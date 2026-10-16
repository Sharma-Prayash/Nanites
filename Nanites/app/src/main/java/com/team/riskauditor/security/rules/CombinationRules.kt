package com.team.riskauditor.security.rules

import com.team.riskauditor.model.ActiveState
import com.team.riskauditor.model.AppSnapshot
import com.team.riskauditor.model.InstallerSource
import com.team.riskauditor.model.RemediationAction
import com.team.riskauditor.model.SpecialAccessType
import com.team.riskauditor.security.ScoringConfig
import com.team.riskauditor.security.catalog.PermissionCatalog

/**
 * Combination rules R001–R008. Each is a separate bonus finding on top of the single rows
 * it builds on; it never re-scores that evidence.
 */
private fun AppSnapshot.declaredIn(findingKey: String): List<String> {
    val names = PermissionCatalog.permissionsFor(findingKey)
    return permissions.filter { it.name in names }.map { it.shortName }.sorted()
}

private fun AppSnapshot.hasInternet() = declares(PermissionCatalog.INTERNET)

private fun AppSnapshot.isOn(type: SpecialAccessType) = access(type)?.state == ActiveState.ENABLED

private fun AppSnapshot.hasAccess(type: SpecialAccessType): Boolean =
    access(type)?.let { it.declared || it.state == ActiveState.ENABLED } ?: false

private fun AppSnapshot.isOutsideStore() =
    !isSystemApp && (installer.source == InstallerSource.SIDELOADED || installer.source == InstallerSource.UNKNOWN)

private fun AppSnapshot.persistenceEvidence(): List<String> = buildList {
    if (declares(PermissionCatalog.RECEIVE_BOOT_COMPLETED)) add("RECEIVE_BOOT_COMPLETED")
    if (permissions.any { it.name.startsWith(PermissionCatalog.FOREGROUND_SERVICE) }) add("FOREGROUND_SERVICE")
    if (isOn(SpecialAccessType.BATTERY_EXEMPTION)) add("unrestricted battery")
}

private fun stateWord(app: AppSnapshot, type: SpecialAccessType): String =
    if (app.isOn(type)) "ON" else "requested"

class SmsNetworkPersistenceRule : Rule {
    override val id = "R001"
    override val title = "SMS + internet + auto-start"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val sms = app.declaredIn("SMS")
        val persistence = app.persistenceEvidence()
        if (sms.isEmpty() || !app.hasInternet() || persistence.isEmpty()) return null
        val c = config.combo(id)
        val outside = app.isOutsideStore()
        val points = if (outside) c.sideloadedPoints ?: c.points ?: 0 else c.points ?: 0
        val evidence = buildList {
            add("SMS: ${sms.joinToString()}")
            add("Network: INTERNET")
            add("Persistence: ${persistence.joinToString()}")
            if (outside) add("Source: outside a known app store")
        }
        return RuleFinding(
            id, if (outside) "$title, from outside an app store" else title, evidence, points,
            "Together these may let it catch OTP texts and send them out over the internet, starting on its own.",
            RemediationAction.APP_SETTINGS,
        )
    }
}

class AccessibilityOverlayRule : Rule {
    override val id = "R002"
    override val title = "Accessibility + display over other apps"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        if (!app.hasAccess(SpecialAccessType.ACCESSIBILITY) || !app.hasAccess(SpecialAccessType.OVERLAY)) return null
        val c = config.combo(id)
        val enabled = app.isOn(SpecialAccessType.ACCESSIBILITY)
        val points = if (enabled) c.enabledPoints ?: c.points ?: 0 else c.points ?: 0
        return RuleFinding(
            id, title,
            listOf(
                "Accessibility service: ${stateWord(app, SpecialAccessType.ACCESSIBILITY)}",
                "Overlay (SYSTEM_ALERT_WINDOW): ${stateWord(app, SpecialAccessType.OVERLAY)}",
            ),
            points,
            "This pairing is common in banking trojans: read the screen, then cover it with a fake login.",
            RemediationAction.ACCESSIBILITY_SETTINGS,
        )
    }
}

class NotificationNetworkRule : Rule {
    override val id = "R003"
    override val title = "Notification access + internet"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        if (!app.hasAccess(SpecialAccessType.NOTIFICATION_LISTENER) || !app.hasInternet()) return null
        val c = config.combo(id)
        val enabled = app.isOn(SpecialAccessType.NOTIFICATION_LISTENER)
        val points = if (enabled) c.enabledPoints ?: c.points ?: 0 else c.points ?: 0
        return RuleFinding(
            id, title,
            listOf("Notification listener: ${stateWord(app, SpecialAccessType.NOTIFICATION_LISTENER)}", "Network: INTERNET"),
            points,
            "May forward the contents of your notifications, such as OTPs, over the internet.",
            RemediationAction.NOTIFICATION_LISTENER_SETTINGS,
        )
    }
}

class UnusualDeviceAdminRule : Rule {
    override val id = "R004"
    override val title = "Active device admin that isn't a device manager"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        if (!app.isOn(SpecialAccessType.DEVICE_ADMIN)) return null
        return RuleFinding(
            id, title,
            listOf("Device admin: active", "Not the device owner or work-profile manager"),
            config.combo(id).points ?: 0,
            "Device admin is unusual for a regular app and makes it harder to remove.",
            RemediationAction.DEVICE_ADMIN_SETTINGS,
        )
    }
}

class PurposeMismatchRule : Rule {
    override val id = "R005"
    override val title = "Access doesn't fit what the app seems to be"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val c = config.combo(id)
        val tokens = (app.label + " " + app.packageName).lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val keyword = c.keywords.firstOrNull { kw ->
            tokens.any { t -> if (kw.length <= 3) t == kw else t.contains(kw) }
        } ?: return null
        val sensitive = app.declaredIn("SMS") + app.declaredIn("CONTACTS_CALL_LOGS")
        if (sensitive.isEmpty()) return null
        return RuleFinding(
            id, title,
            listOf("Name \"${app.label}\" suggests a simple utility (keyword: $keyword)", "Requests: ${sensitive.joinToString()}"),
            c.points ?: 0,
            "A simple utility should not need your messages or contacts. This is a name-based heuristic.",
            RemediationAction.UNINSTALL,
            heuristic = true,
        )
    }
}

class StorageNetworkRule : Rule {
    override val id = "R006"
    override val title = "Files or media access + internet"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val storage = app.declaredIn("MEDIA_STORAGE").toMutableList()
        if (app.hasAccess(SpecialAccessType.ALL_FILES_ACCESS)) storage += "MANAGE_EXTERNAL_STORAGE"
        if (storage.isEmpty() || !app.hasInternet()) return null
        return RuleFinding(
            id, title,
            listOf("Storage: ${storage.joinToString()}", "Network: INTERNET"),
            config.combo(id).points ?: 0,
            "May read your files or media and has a way to send data over the network.",
            RemediationAction.APP_SETTINGS,
        )
    }
}

class MultipleSpecialAccessRule : Rule {
    override val id = "R007"
    override val title = "Several powerful accesses switched on"

    private val highImpact = listOf(
        SpecialAccessType.ACCESSIBILITY, SpecialAccessType.DEVICE_ADMIN, SpecialAccessType.NOTIFICATION_LISTENER,
        SpecialAccessType.OVERLAY, SpecialAccessType.INSTALL_PACKAGES, SpecialAccessType.USAGE_STATS,
    )

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val c = config.combo(id)
        val on = highImpact.filter { app.isOn(it) }
        if (on.size < (c.minEnabled ?: 2)) return null
        return RuleFinding(
            id, title, on.map { "${it.title}: ON" }, c.points ?: 0,
            "Holding several powerful accesses at once increases the harm if the app is misused.",
            RemediationAction.APP_SETTINGS,
        )
    }
}

class NewSensitivePermissionRule : Rule {
    override val id = "R008"
    override val title = "Update added sensitive access"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val baseline = trust.baselinePermissions ?: return null
        val added = app.permissions.map { it.name }
            .filter { it !in baseline && PermissionCatalog.isSensitive(it) }
            .sorted()
        if (added.isEmpty()) return null
        val since = trust.baselineVersionName?.let { " since version $it" } ?: " since the last scan"
        return RuleFinding(
            id, title, added.map { "New$since: ${it.substringAfterLast('.')}" },
            config.combo(id).points ?: 0,
            "The app gained new sensitive capabilities in an update. Check that you expected this.",
            RemediationAction.APP_SETTINGS,
        )
    }
}

object CombinationRules {
    val all: List<Rule> = listOf(
        SmsNetworkPersistenceRule(),
        AccessibilityOverlayRule(),
        NotificationNetworkRule(),
        UnusualDeviceAdminRule(),
        PurposeMismatchRule(),
        StorageNetworkRule(),
        MultipleSpecialAccessRule(),
        NewSensitivePermissionRule(),
    )
}
