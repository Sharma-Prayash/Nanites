package com.team.riskauditor.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team.riskauditor.AppContainer
import com.team.riskauditor.model.InstallerSource
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.scan.AppResult
import com.team.riskauditor.security.Explainer
import com.team.riskauditor.ui.components.AppIcon
import com.team.riskauditor.ui.components.ScoreBadge
import com.team.riskauditor.ui.components.StatusChip
import com.team.riskauditor.ui.components.Tone
import com.team.riskauditor.ui.theme.colors

@Composable
fun AppsScreen(container: AppContainer, onOpenApp: (String) -> Unit) {
    val vm: AppsViewModel = viewModel { AppsViewModel(container.scanRepository, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val f = state.filter

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Apps", style = MaterialTheme.typography.headlineSmall)
                    if (state.hasScan) {
                        Text(
                            "${state.apps.size} of ${state.total} shown · " + if (state.includeSystem) "including system apps" else "user-installed apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SortMenu(f.sort, vm::setSort)
            }
            Spacer(Modifier.size(10.dp))
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name or package") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, "Clear search") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            f.category?.let { c ->
                InputChip(
                    selected = true, onClick = { vm.setCategory(null) }, label = { Text(c.title) },
                    trailingIcon = { Icon(Icons.Filled.Close, "Remove filter", Modifier.size(16.dp)) },
                )
            }
            FilterChip(
                selected = f.specialOnly, onClick = vm::toggleSpecial, label = { Text("Special access on") },
                leadingIcon = { Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.size(18.dp)) },
            )
            listOf(RiskBand.CRITICAL, RiskBand.HIGH, RiskBand.MEDIUM, RiskBand.LOW).forEach { band ->
                val dot = band.colors.accent
                FilterChip(
                    selected = f.band == band,
                    onClick = { vm.setBand(if (f.band == band) null else band) },
                    label = { Text(band.label) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(dot)) },
                )
            }
        }

        when {
            !state.hasScan -> EmptyState(
                if (state.scanning) "Scan in progress…" else "No scan yet",
                "Run a scan to list your installed apps with their risk scores.",
                if (state.scanning) null else ("Scan installed apps" to vm::scan),
            )
            state.apps.isEmpty() -> EmptyState("No apps match", "Try clearing a filter or the search.", null)
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.apps, key = { it.packageName }) { app -> AppRow(app) { onOpenApp(app.packageName) } }
            }
        }
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(current.label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = { onSelect(s); open = false })
            }
        }
    }
}

@Composable
private fun AppRow(app: AppResult, onClick: () -> Unit) {
    val s = app.snapshot
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    s.packageName + (s.versionName?.let { " · v$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Explainer.primaryReason(app.assessment)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    StatusChip(installerShort(s.installer.source), if (s.installer.source == InstallerSource.SIDELOADED) Tone.Warning else Tone.Neutral)
                    if (app.enabledSpecialAccess.isNotEmpty()) {
                        StatusChip("${app.enabledSpecialAccess.size} special access on", Tone.Danger)
                    }
                    if (s.isSystemApp) StatusChip("System", Tone.Neutral)
                }
            }
            Spacer(Modifier.width(8.dp))
            ScoreBadge(app.assessment.score, app.assessment.band)
        }
    }
}

private fun installerShort(source: InstallerSource) = when (source) {
    InstallerSource.PLAY_STORE -> "Play Store"
    InstallerSource.OEM_STORE -> "Other store"
    InstallerSource.SIDELOADED -> "Outside store"
    InstallerSource.PRELOADED -> "Pre-installed"
    InstallerSource.UNKNOWN -> "Source unknown"
}

@Composable
private fun EmptyState(title: String, body: String, action: Pair<String, () -> Unit>?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(16.dp))
            Button(onClick = action.second) { Text(action.first) }
        }
    }
}
