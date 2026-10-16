package com.team.riskauditor.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.team.riskauditor.AppContainer
import com.team.riskauditor.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, onOpenRubric: () -> Unit) {
    val includeSystem by container.prefs.includeSystemApps.collectAsStateWithLifecycle(initialValue = false)
    val latest by container.scanRepository.latest.collectAsStateWithLifecycle()
    val status by container.scanRepository.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val stale = latest != null && latest?.includeSystem != includeSystem

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item {
            SectionCard(title = "Scan") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include system apps", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Pre-installed apps are scored at half weight. Off by default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = includeSystem, onCheckedChange = { v -> scope.launch { container.prefs.setIncludeSystemApps(v) } })
                }
                if (stale) {
                    Text("The last scan used the other setting.", style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = container.scanRepository::startScan, enabled = !status.running) { Text("Scan again now") }
                }
            }
        }
        item {
            SectionCard(title = "Privacy") {
                Fact(Icons.Filled.PhoneAndroid, "All analysis happens on this phone.")
                Fact(Icons.Filled.CloudOff, "Nanites has no internet permission, so it cannot send your app list anywhere.")
                Fact(Icons.Filled.NoAccounts, "No account, no analytics, no ads, no crash reporting.")
                Fact(Icons.Filled.Lock, "Results are kept in memory only and excluded from backups.")
            }
        }
        item {
            SectionCard(title = "About the score") {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenRubric),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Scoring rubric", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Rules ${container.scoringConfig.ruleVersion}: weights, combinations and bands",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
                Text(
                    "A high score means an app could do more harm if misused. It does not mean the app is malware, and a low score does not mean your phone is safe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
