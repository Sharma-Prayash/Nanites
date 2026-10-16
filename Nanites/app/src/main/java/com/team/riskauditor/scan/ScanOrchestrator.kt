package com.team.riskauditor.scan

import android.content.Context
import com.team.riskauditor.model.AppSnapshot
import com.team.riskauditor.security.RuleEngine
import com.team.riskauditor.security.rules.TrustContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Previously stored state for one package, used for permission-change detection (R008). */
data class Baseline(val permissions: Set<String>, val versionName: String?)

/** Scan → normalise → detect → score. Cancelling the collector cancels the scan. */
class ScanOrchestrator(context: Context, private val engine: RuleEngine) {

    private val scanner = PackageScanner(context)
    private val roles = RoleResolver(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(4)

    fun scan(includeSystem: Boolean, baselines: Map<String, Baseline>): Flow<ScanProgress> = channelFlow {
        val startedAt = System.currentTimeMillis()
        send(ScanProgress.Preparing)

        val (device, deviceRoles, packages) = withContext(Dispatchers.IO) {
            Triple(scanner.special.readDeviceState(), roles.read(), scanner.listPackages(includeSystem))
        }
        val total = packages.size
        send(ScanProgress.Inspecting(0, total, null))

        val done = AtomicInteger()
        val outcomes = packages.map { info ->
            async(io) {
                val label = scanner.labelOf(info)
                val outcome = try {
                    val snap = scanner.snapshot(info.packageName, device)
                    if (snap == null) Outcome.Gone(info.packageName)
                    else Outcome.Ok(assess(snap, deviceRoles, baselines[snap.packageName]))
                } catch (e: RuntimeException) {
                    Outcome.Failed(ScanFailure(info.packageName, e.javaClass.simpleName))
                }
                send(ScanProgress.Inspecting(done.incrementAndGet(), total, label))
                outcome
            }
        }.awaitAll()

        val result = ScanResult(
            startedAt = startedAt,
            completedAt = System.currentTimeMillis(),
            includeSystem = includeSystem,
            ruleVersion = engine.ruleVersion,
            apps = outcomes.filterIsInstance<Outcome.Ok>().map { it.result }
                .sortedWith(compareByDescending<AppResult> { it.assessment.score }.thenBy { it.snapshot.label.lowercase() }),
            failures = outcomes.filterIsInstance<Outcome.Failed>().map { it.failure },
            deviceChecks = device.summary().map { (name, check) ->
                when (check) {
                    is DeviceCheck.Ok -> DeviceCheckSummary(name, true, "Read via ${check.source}")
                    is DeviceCheck.Failed -> DeviceCheckSummary(name, false, check.reason)
                }
            },
        )
        send(ScanProgress.Finished(result))
    }

    /** Re-reads one package after the user returns from Settings or the uninstall dialog (E03). Null = gone. */
    suspend fun rescan(packageName: String, baseline: Baseline?): AppResult? = withContext(Dispatchers.IO) {
        val device = scanner.special.readDeviceState()
        val deviceRoles = roles.read()
        scanner.snapshot(packageName, device)?.let { assess(it, deviceRoles, baseline) }
    }

    private suspend fun assess(snap: AppSnapshot, deviceRoles: RoleResolver.Roles, baseline: Baseline?): AppResult {
        currentCoroutineContext().ensureActive()
        val appRoles = roles.rolesFor(snap.packageName, deviceRoles)
        val trust = TrustContext(appRoles, baseline?.permissions, baseline?.versionName)
        val assessment = withContext(Dispatchers.Default) { engine.assess(snap, trust) }
        return AppResult(snap, assessment, appRoles, System.currentTimeMillis())
    }

    private sealed interface Outcome {
        data class Ok(val result: AppResult) : Outcome
        data class Gone(val packageName: String) : Outcome
        data class Failed(val failure: ScanFailure) : Outcome
    }
}
