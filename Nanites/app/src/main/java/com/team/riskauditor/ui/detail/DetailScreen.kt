package com.team.riskauditor.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team.riskauditor.AppContainer
import com.team.riskauditor.data.Verification
import com.team.riskauditor.model.ActiveState
import com.team.riskauditor.model.GrantState
import com.team.riskauditor.model.PermissionCategory
import com.team.riskauditor.model.RemediationAction
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.model.SpecialAccessType
import com.team.riskauditor.remediation.SettingsIntents
import com.team.riskauditor.scan.AppResult
import com.team.riskauditor.security.Explainer
import com.team.riskauditor.security.rules.RuleFinding
import com.team.riskauditor.ui.components.AppIcon
import com.team.riskauditor.ui.components.ScoreGauge
import com.team.riskauditor.ui.components.SectionCard
import com.team.riskauditor.ui.components.StatusChip
import com.team.riskauditor.ui.components.Tone
import com.team.riskauditor.ui.theme.colors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DetailViewModel = viewModel { DetailViewModel(container.scanRepository, createSavedStateHandle()) }
    val app by vm.app.collectAsStateWithLifecycle()
    val verification by vm.verification.collectAsStateWithLifecycle()
    val verifying by vm.verifying.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var adminDialog by remember { mutableStateOf(false) }

    // Every trip to Settings / uninstall comes back here and triggers a fresh read of this package.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.verifyAfterReturn() }
    fun launchFirst(intents: List<Intent>) {
        for (intent in intents) {
            try {
                launcher.launch(intent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        scope.launch { snackbar.showSnackbar("This settings page isn't available on your device") }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(verifying, verification) {
        if (verifying || verification != null) listState.animateScrollToItem(0)
    }

    val current = app
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.snapshot?.label ?: "App details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (current != null) {
                        IconButton(onClick = vm::verifyAfterReturn, enabled = !verifying) { Icon(Icons.Filled.Refresh, "Rescan this app") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (current != null) {
                ActionBar(
                    app = current,
                    onSettings = { launchFirst(listOf(SettingsIntents.appDetails(current.packageName))) },
                    onUninstall = {
                        if (current.snapshot.access(SpecialAccessType.DEVICE_ADMIN)?.state == ActiveState.ENABLED) adminDialog = true
                        else launchFirst(listOf(SettingsIntents.uninstall(current.packageName)))
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (current == null) {
            GoneState(verification, Modifier.padding(padding), onBack)
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeaderCard(current) }
            if (verifying || verification != null) {
                item { VerificationBanner(verification, verifying, vm::dismissVerification) }
            }
            item {
                WhyFlaggedCard(current) { action ->
                    if (action == RemediationAction.UNINSTALL) launchFirst(listOf(SettingsIntents.uninstall(current.packageName)))
                    else launchFirst(SettingsIntents.candidates(action, current))
                }
            }
            item { SpecialAccessCard(current) }
            item { CategoriesCard(current) }
            item { PermissionsCard(current) }
            item { AppInfoCard(current) }
            item { StaticChecksCard(current) }
        }
    }

    if (adminDialog && current != null) {
        AlertDialog(
            onDismissRequest = { adminDialog = false },
            title = { Text("Turn off device admin first") },
            text = {
                Text("${current.snapshot.label} is an active device admin, so Android blocks uninstalling it. Open device admin settings, switch it off, then come back and uninstall.")
            },
            confirmButton = {
                TextButton(onClick = {
                    adminDialog = false
                    launchFirst(listOf(SettingsIntents.deviceAdmin()))
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { adminDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HeaderCard(app: AppResult) {
    val a = app.assessment
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.snapshot.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreGauge(a.score, a.band)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Explainer.headline(a), style = MaterialTheme.typography.titleMedium, color = a.band.colors.accent)
                Text(
                    "${a.findings.size} ${if (a.findings.size == 1) "reason" else "reasons"} found · rules ${a.ruleVersion}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(Explainer.formula(a), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "This score is a rule-based estimate of what the app could do with its access. It is not proof that the app is harmful.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerificationBanner(v: Verification?, verifying: Boolean, onDismiss: () -> Unit) {
    val ok = v is Verification.Rechecked && v.resolved.isNotEmpty() || v is Verification.Removed
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) RiskBand.LOW.colors.container else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            if (verifying) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            else Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Info, null, tint = if (ok) RiskBand.LOW.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    verifying -> Text("Re-checking this app…", style = MaterialTheme.typography.titleSmall)
                    v is Verification.Rechecked -> {
                        Text(
                            if (v.scoreBefore == v.scoreAfter && v.resolved.isEmpty() && v.added.isEmpty()) "Re-checked: no change detected"
                            else "Re-checked: score ${v.scoreBefore} → ${v.scoreAfter}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        v.resolved.forEach { Text("Resolved: ${it.title}", style = MaterialTheme.typography.bodySmall) }
                        v.added.forEach { Text("New: ${it.title}", style = MaterialTheme.typography.bodySmall) }
                        if (v.resolved.isEmpty() && v.added.isEmpty()) {
                            Text("Android still reports the same access for this app.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    v is Verification.Failed -> Text("Could not re-check this app. Try the refresh button.", style = MaterialTheme.typography.titleSmall)
                    else -> {}
                }
            }
            if (!verifying) TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun WhyFlaggedCard(app: AppResult, onAction: (RemediationAction) -> Unit) {
    val a = app.assessment
    SectionCard(
        title = "Why flagged?",
        subtitle = if (a.findings.isEmpty()) null else "Each reason shows what Android reported and how many points it added",
    ) {
        if (a.findings.isEmpty()) {
            Text("This scan found no risky access for this app.", style = MaterialTheme.typography.bodyMedium)
        }
        if (!Explainer.meetsReasonRequirement(a)) {
            Text(
                "Fewer than three independent reasons support this band. Treat it with caution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        a.findings.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FindingItem(f, onAction)
        }
        if (a.suppressed.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Not counted", style = MaterialTheme.typography.labelLarge)
            a.suppressed.forEach {
                Text("${it.finding.title} — ${it.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (a.completeness.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Could not be checked (added 0 points)", style = MaterialTheme.typography.labelLarge)
            a.completeness.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FindingItem(f: RuleFinding, onAction: (RemediationAction) -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WarningAmber, null, tint = severityTone(f.points), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(f.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "+${f.points}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(f.impact, style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            f.evidence.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("Rule ${f.ruleId}", Tone.Neutral)
            if (f.heuristic) StatusChip("Heuristic", Tone.Warning)
            Spacer(Modifier.weight(1f))
            if (f.action != RemediationAction.NONE) {
                TextButton(onClick = { onAction(f.action) }) {
                    Text(if (f.action == RemediationAction.UNINSTALL) "Uninstall" else SettingsIntents.label(f.action))
                }
            }
        }
    }
}

@Composable
private fun severityTone(points: Int) = when {
    points >= 16 -> RiskBand.CRITICAL.colors.accent
    points >= 10 -> RiskBand.HIGH.colors.accent
    points >= 5 -> RiskBand.MEDIUM.colors.accent
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun SpecialAccessCard(app: AppResult) {
    SectionCard(title = "Special access", subtitle = "Requested = declared by the app · On = switched on in Android settings") {
        SpecialAccessType.entries.forEach { type ->
            val obs = app.snapshot.access(type)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(type.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                val (text, tone) = when {
                    obs == null -> "Not checked" to Tone.Neutral
                    obs.state == ActiveState.ENABLED -> "On" to Tone.Danger
                    !obs.declared -> "Not requested" to Tone.Ok
                    obs.state == ActiveState.NOT_ENABLED -> "Requested · off" to Tone.Warning
                    else -> "Requested · ${obs.state.label.lowercase()}" to Tone.Warning
                }
                StatusChip(text, tone)
            }
        }
    }
}

@Composable
private fun CategoriesCard(app: AppResult) {
    SectionCard(title = "Access by category", subtitle = "The nine kinds of access this auditor checks") {
        PermissionCategory.NINE.forEach { category ->
            val perms = app.snapshot.permissions.filter { it.category == category }
            val special = if (category == PermissionCategory.SPECIAL_ACCESS) {
                app.snapshot.specialAccess.filter { it.type != SpecialAccessType.BATTERY_EXEMPTION && (it.declared || it.state == ActiveState.ENABLED) }
            } else emptyList()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(category.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    val detail = when {
                        category == PermissionCategory.SPECIAL_ACCESS && special.isNotEmpty() ->
                            special.joinToString { it.type.title + if (it.state == ActiveState.ENABLED) " (on)" else "" }
                        perms.isNotEmpty() -> perms.joinToString { it.shortName + grantSuffix(it.grant) }
                        else -> "None"
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun grantSuffix(g: GrantState) = when (g) {
    GrantState.GRANTED -> " ✓"
    GrantState.NOT_GRANTED -> " (not granted)"
    GrantState.UNKNOWN -> " (state unknown)"
}

@Composable
private fun PermissionsCard(app: AppResult) {
    var expanded by remember { mutableStateOf(false) }
    val perms = app.snapshot.permissions.sortedWith(compareBy({ it.category.ordinal }, { it.name }))
    val granted = perms.count { it.grant == GrantState.GRANTED }
    val notGranted = perms.count { it.grant == GrantState.NOT_GRANTED }
    val unknown = perms.count { it.grant == GrantState.UNKNOWN }
    SectionCard(
        modifier = Modifier.animateContentSize(),
        title = "All permissions (${perms.size})",
        subtitle = "$granted granted · $notGranted requested but not granted" + if (unknown > 0) " · $unknown unknown" else "",
    ) {
        if (expanded) {
            perms.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.shortName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.protection.label} · ${p.category.title}" +
                                (com.team.riskauditor.security.catalog.PermissionCatalog.lookup(p.name)?.meaning?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    when (p.grant) {
                        GrantState.GRANTED -> StatusChip("Granted", Tone.Warning)
                        GrantState.NOT_GRANTED -> StatusChip("Not granted", Tone.Neutral)
                        GrantState.UNKNOWN -> StatusChip("Unknown", Tone.Neutral)
                    }
                }
            }
        }
        if (perms.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide permissions" else "Show all permissions") }
        } else {
            Text("This app requests no permissions.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppInfoCard(app: AppResult) {
    val s = app.snapshot
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    SectionCard(title = "App info") {
        InfoRow("Version", (s.versionName ?: "Unknown") + " (${s.versionCode})")
        InfoRow("Installed from", s.installer.source.label + (s.installer.installerPackage?.let { "\n$it" } ?: ""))
        InfoRow("Installed", s.firstInstallTime?.let { fmt.format(Date(it)) } ?: "Unknown")
        InfoRow("Last updated", s.lastUpdateTime?.let { fmt.format(Date(it)) } ?: "Unknown")
        InfoRow("Target SDK", "${s.targetSdk}")
        InfoRow("Type", if (s.isSystemApp) "System app" else "User-installed app")
        if (app.roles.isNotEmpty()) InfoRow("Role", app.roles.joinToString { it.label })
    }
}

@Composable
private fun StaticChecksCard(app: AppResult) {
    val m = app.snapshot.manifest
    SectionCard(title = "Static checks", subtitle = "Read from the installed app's manifest flags") {
        InfoRow("Debuggable", when (m.debuggable) { true -> "Yes"; false -> "No"; null -> "Not checked" })
        InfoRow("Cleartext traffic", when (m.cleartextTraffic) { true -> "Allowed"; false -> "Not allowed"; null -> "Not checked" })
        InfoRow(
            "Unprotected exported components",
            m.exportedUnprotected?.size?.toString() ?: "Not checked (app too large to list)",
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

@Composable
private fun ActionBar(app: AppResult, onSettings: () -> Unit, onUninstall: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(onClick = onSettings, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Filled.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("App settings")
            }
            if (!app.snapshot.isSystemApp) {
                OutlinedButton(
                    onClick = onUninstall,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Uninstall")
                }
            }
        }
    }
}

@Composable
private fun GoneState(v: Verification?, modifier: Modifier, onBack: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (v is Verification.Removed) {
            Icon(Icons.Filled.CheckCircle, null, tint = RiskBand.LOW.colors.accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("${v.label} was uninstalled", style = MaterialTheme.typography.titleLarge)
            Text("Verified: Android no longer lists this app.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("This app is not in the latest scan", style = MaterialTheme.typography.titleMedium)
            Text("Run a new scan from Home to inspect it.", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
