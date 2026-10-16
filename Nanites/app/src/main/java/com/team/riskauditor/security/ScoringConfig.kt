package com.team.riskauditor.security

import com.team.riskauditor.model.InstallerSource
import com.team.riskauditor.model.RiskBand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirror of assets/scoring_config.json — the single source of truth for every weight. */
@Serializable
data class ScoringConfig(
    val ruleVersion: String,
    val findingCap: Int,
    val bands: Map<String, List<Int>>,
    val singleFindings: Map<String, SingleFindingConfig>,
    val combinations: Map<String, CombinationConfig>,
    val modifiers: Modifiers,
) {
    fun single(key: String): SingleFindingConfig =
        singleFindings[key] ?: error("scoring_config.json is missing singleFindings.$key")

    fun combo(id: String): CombinationConfig =
        combinations[id] ?: error("scoring_config.json is missing combinations.$id")

    fun bandFor(score: Int): RiskBand =
        RiskBand.entries.firstOrNull { band ->
            val range = bands[band.name] ?: return@firstOrNull false
            score in range[0]..range[1]
        } ?: RiskBand.CRITICAL

    fun installerMultiplier(source: InstallerSource): Double = modifiers.installer[source.name] ?: 1.0

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): ScoringConfig = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class SingleFindingConfig(
    val category: String,
    val granted: Int? = null,
    val declared: Int? = null,
    val points: Int? = null,
    val backgroundBonus: Int? = null,
    val batteryExemptionBonus: Int? = null,
    val belowSdk: Int? = null,
    val minCount: Int? = null,
) {
    val grantedPoints: Int get() = granted ?: points ?: 0
    val declaredPoints: Int get() = declared ?: points ?: 0
}

@Serializable
data class CombinationConfig(
    val name: String,
    val points: Int? = null,
    val sideloadedPoints: Int? = null,
    val enabledPoints: Int? = null,
    val keywords: List<String> = emptyList(),
    val minEnabled: Int? = null,
    val scoreFloor: Int? = null,
)

@Serializable
data class Modifiers(
    val installer: Map<String, Double>,
    val systemApp: Double,
    val roleSuppression: Map<String, List<String>> = emptyMap(),
)
