package com.team.riskauditor.security.rules

import com.team.riskauditor.model.AppRole
import com.team.riskauditor.model.AppSnapshot
import com.team.riskauditor.model.RemediationAction
import com.team.riskauditor.security.ScoringConfig
import kotlinx.serialization.Serializable

interface Rule {
    val id: String
    val title: String
    fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? // null = not fired
}

@Serializable
data class RuleFinding(
    val ruleId: String,
    val title: String,
    val evidence: List<String>,
    val points: Int,
    val impact: String,
    val action: RemediationAction,
    val heuristic: Boolean = false,
)

/** Context the snapshot alone cannot give: device roles and the previous baseline for this package. */
data class TrustContext(
    val roles: Set<AppRole> = emptySet(),
    /** Permissions declared at the last stored baseline; null = no baseline yet (first scan). */
    val baselinePermissions: Set<String>? = null,
    val baselineVersionName: String? = null,
) {
    companion object {
        val NONE = TrustContext()
    }
}
