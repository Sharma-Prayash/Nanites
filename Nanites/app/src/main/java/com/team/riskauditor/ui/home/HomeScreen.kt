package com.team.riskauditor.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team.riskauditor.AppContainer
import com.team.riskauditor.data.ScanStatus
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.scan.AppResult
import com.team.riskauditor.security.Explainer
import com.team.riskauditor.ui.AppsRoute
import com.team.riskauditor.ui.components.AppIcon
import com.team.riskauditor.ui.components.IconBubble
import com.team.riskauditor.ui.components.ScoreBadge
import com.team.riskauditor.ui.components.SectionCard
import com.team.riskauditor.ui.theme.colors

@Composable
fun HomeScreen(container: AppContainer, onOpenApps: (AppsRoute) -> Unit, onOpenApp: (String) -> Unit) {
    val vm: HomeViewModel = viewModel { HomeViewModel(container.scanRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val summary = state.summary
    val listState = rememberLazyListState()
    var shownResultAt by rememberSaveable { mutableLongStateOf(summary?.completedAt ?: 0L) }
    LaunchedEffect(summary?.completedAt) {
        val at = summary?.completedAt ?: return@LaunchedEffect
        if (at != shownResultAt) {
            shownResultAt = at
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header() }
        item {
            when {
                state.status.running -> ScanningCard(state.status, onCancel = vm::cancel)
                summary == null -> WelcomeCard(state.status, onScan = vm::scan)
                else -> ResultHero(summary, onScan = vm::scan)
            }
        }
        if (summary != null) {
            item { BandTiles(summary, onOpenApps) }
            item {
                SpecialAccessTile(summary.specialAccessApps) { onOpenApps(AppsRoute(specialOnly = true)) }
            }
            if (summary.topApps.isNotEmpty()) {
                item {
                    SectionCard(title = "Highest risk apps", subtitle = "Tap an app to see why it was flagged") {
                        summary.topApps.forEachIndexed { i, app ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TopAppRow(app) { onOpenApp(app.packageName) }
                        }
                        TextButton(onClick = { onOpenApps(AppsRoute()) }, modifier = Modifier.align(Alignment.End)) {
                            Text("See all ${summary.inspected} apps")
                        }
                    }
                }
            }
            item { CategoryCard(summary, onOpenApps) }
            if (summary.failures > 0 || summary.failedChecks.isNotEmpty()) {
                item { LimitationsCard(summary) }
            }
            item {
                Text(
                    "Scores are rule-based estimates of what an app could do with its access. They are not proof that an app is harmful.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        IconBubble(Icons.Filled.Shield, MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.primary, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Nanites", style = MaterialTheme.typography.titleLarge)
            Text("App risk auditor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeCard(status: ScanStatus, onScan: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Which of your apps could do the most harm?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Nanites checks every installed app's permissions and special access, scores the risk from 0 to 100, and explains each reason in plain language.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Promise(Icons.Filled.PhoneAndroid, "Runs entirely on this phone")
            Promise(Icons.Filled.CloudOff, "No internet permission — nothing is uploaded")
            Promise(Icons.Filled.Visibility, "Only reads — never changes your settings")
            if (status.cancelled) Text("Last scan was cancelled.", style = MaterialTheme.typography.bodySmall)
            status.error?.let { Text("Scan failed: $it", color = MaterialTheme.colorScheme.error) }
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Scan installed apps")
            }
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ScanningCard(status: ScanStatus, onCancel: () -> Unit) {
    SectionCard {
        Text("Scanning your apps…", style = MaterialTheme.typography.titleMedium)
        if (status.total > 0) {
            LinearProgressIndicator(
                progress = { status.done.toFloat() / status.total },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Text(
                "Inspected ${status.done} of ${status.total}" + (status.currentLabel?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
            Text("Reading the list of installed apps and special-access settings", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Checking permissions, accessibility, device admin, notification access and overlays.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
    }
}

@Composable
private fun ResultHero(summary: HomeSummary, onScan: () -> Unit) {
    val attention = summary.attention
    val band = if (attention > 0) summary.worst else RiskBand.LOW
    val c = band.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.onContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (attention > 0) Icons.Filled.GppMaybe else Icons.Filled.GppGood, null,
                    tint = c.accent, modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (attention > 0) "$attention ${if (attention == 1) "app needs" else "apps need"} your attention"
                    else "No High-risk findings detected by this scan",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(
                if (attention > 0) "These apps have High or Critical risk. Open each one to see why and what you can do."
                else "This scan found no app with High or Critical risk. Keep reviewing apps after installs and updates.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${summary.inspected} apps inspected · ${relative(summary.completedAt)} · " +
                    if (summary.includeSystem) "including system apps" else "user-installed apps",
                style = MaterialTheme.typography.labelMedium,
            )
            FilledTonalButton(onClick = onScan) { Text("Scan again") }
        }
    }
}

@Composable
private fun BandTiles(summary: HomeSummary, onOpenApps: (AppsRoute) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(RiskBand.CRITICAL, RiskBand.HIGH, RiskBand.MEDIUM, RiskBand.LOW).forEach { band ->
            val c = band.colors
            Card(
                onClick = { onOpenApps(AppsRoute(band = band.name)) },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(vertical = 12.dp, horizontal = 10.dp)) {
                    Text("${summary.bandCounts[band] ?: 0}", style = MaterialTheme.typography.headlineSmall, color = c.accent, fontWeight = FontWeight.Bold)
                    Text(band.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SpecialAccessTile(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Outlined.AdminPanelSettings, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 0) "No app has special access switched on" else "$count ${if (count == 1) "app has" else "apps have"} special access on",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Accessibility, device admin, notification access, overlays, usage or install access",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun TopAppRow(app: AppResult, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.snapshot.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Explainer.primaryReason(app.assessment)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        ScoreBadge(app.assessment.score, app.assessment.band)
    }
}

@Composable
private fun CategoryCard(summary: HomeSummary, onOpenApps: (AppsRoute) -> Unit) {
    SectionCard(title = "Access across your apps", subtitle = "How many apps request each kind of access") {
        summary.categoryCounts.entries.forEach { (category, count) ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = count > 0) { onOpenApps(AppsRoute(category = category.name)) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(category.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(category.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "$count",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LimitationsCard(summary: HomeSummary) {
    SectionCard(title = "Scan limitations") {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                summary.failedChecks.forEach { Text("${it.name}: ${it.detail}. Affected apps are scored on declarations only.", style = MaterialTheme.typography.bodySmall) }
                if (summary.failures > 0) Text("${summary.failures} app(s) could not be inspected.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun relative(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
