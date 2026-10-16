package com.team.riskauditor.data

import com.team.riskauditor.scan.AppResult
import com.team.riskauditor.scan.Baseline
import com.team.riskauditor.scan.ScanOrchestrator
import com.team.riskauditor.scan.ScanProgress
import com.team.riskauditor.scan.ScanResult
import com.team.riskauditor.security.rules.RuleFinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanStatus(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentLabel: String? = null,
    val cancelled: Boolean = false,
    val error: String? = null,
)

/** Outcome of re-reading one package after the user came back from Settings or uninstall. */
sealed interface Verification {
    val label: String

    data class Removed(override val label: String) : Verification
    data class Rechecked(
        override val label: String,
        val scoreBefore: Int,
        val scoreAfter: Int,
        val resolved: List<RuleFinding>,
        val added: List<RuleFinding>,
    ) : Verification
    data class Failed(override val label: String) : Verification
}

/** Holds the latest scan for every screen and owns the running scan job (survives navigation). */
class ScanRepository(
    private val orchestrator: ScanOrchestrator,
    private val prefs: AppPrefs,
    private val scope: CoroutineScope,
) {
    private val _latest = MutableStateFlow<ScanResult?>(null)
    val latest: StateFlow<ScanResult?> = _latest.asStateFlow()

    private val _status = MutableStateFlow(ScanStatus())
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    private var job: Job? = null

    fun startScan() {
        if (job?.isActive == true) return
        job = scope.launch {
            _status.value = ScanStatus(running = true)
            try {
                val includeSystem = prefs.includeSystemApps.first()
                orchestrator.scan(includeSystem, currentBaselines()).collect { p ->
                    when (p) {
                        ScanProgress.Preparing -> _status.value = ScanStatus(running = true)
                        is ScanProgress.Inspecting ->
                            _status.value = ScanStatus(true, p.done, p.total, p.currentLabel)
                        is ScanProgress.Finished -> {
                            _latest.value = p.result
                            _status.value = ScanStatus(done = p.result.apps.size, total = p.result.apps.size)
                        }
                    }
                }
            } catch (e: CancellationException) {
                _status.value = ScanStatus(cancelled = true)
                throw e
            } catch (e: RuntimeException) {
                _status.value = ScanStatus(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancelScan() {
        job?.cancel()
    }

    fun app(packageName: String): AppResult? = _latest.value?.apps?.firstOrNull { it.packageName == packageName }

    /** Re-read the package and replace (or drop) it in the latest result. Never assumes the fix worked. */
    suspend fun verify(packageName: String): Verification {
        val before = app(packageName)
        val label = before?.snapshot?.label ?: packageName
        val after = try {
            orchestrator.rescan(packageName, before?.snapshot?.let { Baseline(it.permissions.map { p -> p.name }.toSet(), it.versionName) })
        } catch (e: RuntimeException) {
            return Verification.Failed(label)
        }
        _latest.update { result ->
            result?.copy(
                apps = if (after == null) result.apps.filterNot { it.packageName == packageName }
                else result.apps.map { if (it.packageName == packageName) after else it }
                    .sortedWith(compareByDescending<AppResult> { it.assessment.score }.thenBy { it.snapshot.label.lowercase() }),
            )
        }
        if (after == null) return Verification.Removed(label)
        val beforeIds = before?.assessment?.findings.orEmpty().associateBy { it.ruleId }
        val afterIds = after.assessment.findings.associateBy { it.ruleId }
        return Verification.Rechecked(
            label = label,
            scoreBefore = before?.assessment?.score ?: after.assessment.score,
            scoreAfter = after.assessment.score,
            resolved = beforeIds.filterKeys { it !in afterIds }.values.toList(),
            added = afterIds.filterKeys { it !in beforeIds }.values.toList(),
        )
    }

    private fun currentBaselines(): Map<String, Baseline> =
        _latest.value?.apps.orEmpty().associate { r ->
            r.packageName to Baseline(r.snapshot.permissions.map { it.name }.toSet(), r.snapshot.versionName)
        }
}
