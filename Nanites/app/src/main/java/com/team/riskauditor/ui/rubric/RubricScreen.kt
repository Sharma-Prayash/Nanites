package com.team.riskauditor.ui.rubric

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.security.ScoringConfig
import com.team.riskauditor.security.rules.CombinationRules
import com.team.riskauditor.security.rules.SingleRules
import com.team.riskauditor.ui.components.BandPill
import com.team.riskauditor.ui.components.SectionCard

/** The scoring rubric, rendered straight from scoring_config.json so it can never drift from the engine. */
@Composable
fun RubricScreen(config: ScoringConfig) {
    val titles = (SingleRules.all + CombinationRules.all).associate { it.id to it.title }
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("How scoring works", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Rule version ${config.ruleVersion}. Every number below is read from the same file the scanner uses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = "Formula") {
                Text(
                    "score = min(100, round( Σ min(points, ${config.findingCap}) × install source × system app ))",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Bullet("Each finding adds at most ${config.findingCap} points, so High needs at least 3 reasons and Critical at least 4.")
                Bullet("Requested-but-not-granted access scores about half of granted access.")
                Bullet("Checks Android would not answer add 0 points and are listed on the app's page.")
                Bullet("Combinations are bonus findings on top of the single findings they build on.")
            }
        }
        item {
            SectionCard(title = "Risk bands") {
                RiskBand.entries.forEach { band ->
                    val range = config.bands[band.name]
                    Row(Modifier.fillMaxWidth()) {
                        BandPill(band)
                        Text(range?.let { "${it[0]} – ${it[1]}" } ?: "–", Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
            }
        }
        item {
            SectionCard(title = "Single findings", subtitle = "Points when granted / when only requested") {
                config.singleFindings.forEach { (key, row) ->
                    val value = when {
                        row.points != null -> "${row.points}"
                        else -> "${row.granted ?: 0} / ${row.declared ?: 0}"
                    }
                    val extra = listOfNotNull(
                        row.backgroundBonus?.let { "+$it background" },
                        row.batteryExemptionBonus?.let { "+$it battery exempt" },
                        row.belowSdk?.let { "target < $it" },
                        row.minCount?.let { "≥ $it components" },
                    ).joinToString()
                    RubricRow(titles[key] ?: key, key, value, extra)
                }
            }
        }
        item {
            SectionCard(title = "Combination rules") {
                config.combinations.forEach { (id, c) ->
                    val value = listOfNotNull(
                        c.points?.let { "$it" },
                        c.enabledPoints?.let { "$it if on" },
                        c.sideloadedPoints?.let { "$it outside store" },
                        c.scoreFloor?.let { "score ≥ $it" },
                    ).joinToString(" · ")
                    val extra = if (c.keywords.isNotEmpty()) "Keywords: ${c.keywords.joinToString()}" else ""
                    val note = if (id == "R009") " (hash lookup — not enabled in this build)" else ""
                    RubricRow(c.name + note, id, value, extra)
                }
            }
        }
        item {
            SectionCard(title = "Modifiers") {
                config.modifiers.installer.forEach { (source, m) -> RubricRow(source.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, "installer", "× $m", "") }
                RubricRow("System app", "system", "× ${config.modifiers.systemApp}", "")
                config.modifiers.roleSuppression.forEach { (role, ids) ->
                    RubricRow(role.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, "role", "not counted", ids.joinToString())
                }
            }
        }
    }
}

@Composable
private fun RubricRow(title: String, id: String, value: String, extra: String) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                id + if (extra.isNotEmpty()) " · $extra" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodySmall)
}
