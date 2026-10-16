package com.team.riskauditor.security

import com.team.riskauditor.model.AppSnapshot
import com.team.riskauditor.model.GrantState
import com.team.riskauditor.model.InstallerSource
import com.team.riskauditor.model.ProtectionLevel
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.security.rules.CombinationRules
import com.team.riskauditor.security.rules.Rule
import com.team.riskauditor.security.rules.RuleFinding
import com.team.riskauditor.security.rules.SingleRules
import com.team.riskauditor.security.rules.TrustContext
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.roundToInt

@Serializable
data class SuppressedFinding(val finding: RuleFinding, val reason: String)

@Serializable
data class AppAssessment(
    val packageName: String,
    val score: Int,
    val band: RiskBand,
    val ruleVersion: String,
    /** Scored findings, highest contribution first. Points are already capped. */
    val findings: List<RuleFinding>,
    val suppressed: List<SuppressedFinding>,
    val rawTotal: Int,
    val installerMultiplier: Double,
    val systemMultiplier: Double,
    /** Checks that could not be observed. They added 0 points. */
    val completeness: List<String>,
)

/** Runs every rule, applies role suppression, and hands the result to [RiskScorer]. Deterministic. */
class RuleEngine(
    private val config: ScoringConfig,
    private val rules: List<Rule> = SingleRules.all + CombinationRules.all,
) {
    val ruleVersion: String get() = config.ruleVersion

    fun assess(app: AppSnapshot, trust: TrustContext = TrustContext.NONE): AppAssessment {
        val fired = rules.mapNotNull { it.evaluate(app, trust, config) }.filter { it.points > 0 }

        val suppressedIds = trust.roles.flatMap { role ->
            config.modifiers.roleSuppression[role.name].orEmpty().map { it to role }
        }.toMap()
        val (suppressed, kept) = fired.partition { it.ruleId in suppressedIds }

        return RiskScorer.score(
            app = app,
            findings = kept,
            suppressed = suppressed.map { f ->
                val role = suppressedIds.getValue(f.ruleId)
                SuppressedFinding(f, "Expected because it is your ${role.label.lowercase()}")
            },
            completeness = completenessNotes(app),
            config = config,
        )
    }

    private fun completenessNotes(app: AppSnapshot): List<String> = buildList {
        app.specialAccess.filter { it.declared && !it.state.isObserved }.forEach {
            add("${it.type.title}: ${it.state.label.lowercase()} — scored as declared only")
        }
        val unknownGrants = app.permissions.count { it.grant == GrantState.UNKNOWN && it.protection != ProtectionLevel.SPECIAL }
        if (unknownGrants > 0) add("$unknownGrants permission grant state(s) unknown")
        if (app.manifest.exportedUnprotected == null) add("Exported components: not checked")
        if (app.installer.source == InstallerSource.UNKNOWN && !app.isSystemApp) add("Installer source not recorded by Android")
    }
}

object RiskScorer {
    /** score = min(100, round(Σ min(points_i, cap) × installerMultiplier × systemMultiplier)) */
    fun score(
        app: AppSnapshot,
        findings: List<RuleFinding>,
        suppressed: List<SuppressedFinding>,
        completeness: List<String>,
        config: ScoringConfig,
    ): AppAssessment {
        val capped = findings
            .map { it.copy(points = min(it.points, config.findingCap)) }
            .sortedWith(compareByDescending<RuleFinding> { it.points }.thenBy { it.ruleId })
        val raw = capped.sumOf { it.points }
        val installer = config.installerMultiplier(app.installer.source)
        val system = if (app.isSystemApp) config.modifiers.systemApp else 1.0
        val score = min(100, (raw * installer * system).roundToInt()).coerceAtLeast(0)
        return AppAssessment(
            packageName = app.packageName,
            score = score,
            band = config.bandFor(score),
            ruleVersion = config.ruleVersion,
            findings = capped,
            suppressed = suppressed,
            rawTotal = raw,
            installerMultiplier = installer,
            systemMultiplier = system,
            completeness = completeness,
        )
    }
}

/** Turns an assessment into plain-language text. Never invents reasons: everything comes from findings. */
object Explainer {
    fun headline(a: AppAssessment): String = when (a.band) {
        RiskBand.CRITICAL -> "Could do serious harm if misused"
        RiskBand.HIGH -> "Has risky access worth reviewing"
        RiskBand.MEDIUM -> "Has some sensitive access"
        RiskBand.LOW -> if (a.findings.isEmpty()) "No risky access found by this scan" else "Low risk from what this scan sees"
    }

    fun primaryReason(a: AppAssessment): String? = a.findings.firstOrNull()?.title

    fun formula(a: AppAssessment): String {
        val parts = buildList {
            add("${a.rawTotal} points")
            if (a.installerMultiplier != 1.0) add("× ${fmt(a.installerMultiplier)} (install source)")
            if (a.systemMultiplier != 1.0) add("× ${fmt(a.systemMultiplier)} (system app)")
        }
        val capped = if (a.score == 100 && a.rawTotal * a.installerMultiplier * a.systemMultiplier > 100) " (capped)" else ""
        return parts.joinToString(" ") + " = ${a.score}$capped"
    }

    /** True when a High/Critical result is backed by at least three independent findings. */
    fun meetsReasonRequirement(a: AppAssessment): Boolean =
        a.band < RiskBand.HIGH || a.findings.size >= 3

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
}
