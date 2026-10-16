package com.team.riskauditor.security.rules

import com.team.riskauditor.model.ActiveState
import com.team.riskauditor.model.AppSnapshot
import com.team.riskauditor.model.GrantState
import com.team.riskauditor.model.InstallerSource
import com.team.riskauditor.model.RemediationAction
import com.team.riskauditor.model.SpecialAccessType
import com.team.riskauditor.security.ScoringConfig
import com.team.riskauditor.security.catalog.PermissionCatalog

internal fun grantLabel(state: GrantState): String = when (state) {
    GrantState.GRANTED -> "granted"
    GrantState.NOT_GRANTED -> "declared, not granted"
    GrantState.UNKNOWN -> "declared, grant state unknown"
}

/**
 * One scoring row backed by ordinary permissions (SMS, contacts, camera/mic …).
 * Fires once per row at its highest value: any granted permission ⇒ granted points, else declared points.
 */
class PermissionRowRule(
    override val id: String,
    override val title: String,
    private val impact: String,
    private val action: RemediationAction = RemediationAction.APP_SETTINGS,
    private val bonus: ((AppSnapshot, ScoringConfig) -> Pair<Int, String>?)? = null,
) : Rule {
    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val row = config.single(id)
        val names = PermissionCatalog.permissionsFor(id)
        val present = app.permissions.filter { it.name in names }.sortedBy { it.name }
        if (present.isEmpty()) return null
        val granted = present.any { it.grant == GrantState.GRANTED }
        var points = if (granted) row.grantedPoints else row.declaredPoints
        val evidence = present.map { "${it.shortName} — ${grantLabel(it.grant)}" }.toMutableList()
        bonus?.invoke(app, config)?.let { (extra, line) ->
            points += extra
            evidence += line
        }
        val qualifier = if (granted) "" else " (requested, not granted yet)"
        return RuleFinding(id, title + qualifier, evidence, points, impact, action)
    }
}

/** One scoring row backed by a special-access observation. */
class SpecialAccessRule(
    override val id: String,
    private val type: SpecialAccessType,
    override val title: String,
    private val impact: String,
    private val action: RemediationAction,
) : Rule {
    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val obs = app.access(type) ?: return null
        val enabled = obs.state == ActiveState.ENABLED
        if (!obs.declared && !enabled) return null
        val row = config.single(id)
        val points = if (enabled) row.grantedPoints else row.declaredPoints
        val status = when (obs.state) {
            ActiveState.ENABLED -> "Status: ON"
            ActiveState.NOT_ENABLED -> "Status: declared, currently off"
            else -> "Status: ${obs.state.label.lowercase()} — scored as declared only"
        }
        val suffix = when (obs.state) {
            ActiveState.ENABLED -> " is ON"
            ActiveState.NOT_ENABLED -> " requested (off)"
            else -> " requested (status unknown)"
        }
        return RuleFinding(id, title + suffix, obs.evidence + status, points, impact, action)
    }
}

class BootPersistenceRule : Rule {
    override val id = "BOOT_PERSISTENCE"
    override val title = "Starts automatically after restart"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val row = config.single(id)
        val boot = app.declares(PermissionCatalog.RECEIVE_BOOT_COMPLETED)
        val exempt = app.access(SpecialAccessType.BATTERY_EXEMPTION)?.state == ActiveState.ENABLED
        if (!boot && !exempt) return null
        val evidence = buildList {
            if (boot) add("RECEIVE_BOOT_COMPLETED — declared (normal permission, granted at install)")
            if (exempt) add("Battery optimisation: unrestricted (exempt from background limits)")
        }
        val points = (if (boot) row.grantedPoints else 0) + (if (exempt) row.batteryExemptionBonus ?: 0 else 0)
        val impact = "Starts by itself after the phone restarts and may keep running in the background."
        val action = if (exempt) RemediationAction.BATTERY_SETTINGS else RemediationAction.APP_SETTINGS
        return RuleFinding(id, if (boot) title else "Exempt from battery limits", evidence, points, impact, action)
    }
}

class OutsideStoreRule : Rule {
    override val id = "SIDELOADED"
    override val title = "Installed outside a known app store"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        if (app.isSystemApp) return null
        val evidence = when (app.installer.source) {
            InstallerSource.SIDELOADED -> "Installer: ${app.installer.installerPackage ?: "unknown"} (not an app store)"
            InstallerSource.UNKNOWN -> "Installer: not recorded by Android (typical for ADB or file installs)"
            else -> return null
        }
        return RuleFinding(
            id, title, listOf(evidence), config.single(id).points ?: 0,
            "Apps from outside app stores skip store security review.", RemediationAction.UNINSTALL,
        )
    }
}

class LegacyTargetRule : Rule {
    override val id = "LEGACY_TARGET_SDK"
    override val title = "Built for a very old Android version"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val row = config.single(id)
        val below = row.belowSdk ?: return null
        if (app.targetSdk <= 0 || app.targetSdk >= below) return null
        return RuleFinding(
            id, title, listOf("targetSdkVersion ${app.targetSdk} (below $below, before runtime permission prompts)"),
            row.points ?: 0,
            "Old apps may receive sensitive permissions at install without asking you.", RemediationAction.UNINSTALL,
        )
    }
}

class DebuggableRule : Rule {
    override val id = "DEBUGGABLE"
    override val title = "Debug build installed"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        if (app.manifest.debuggable != true) return null
        return RuleFinding(
            id, title, listOf("android:debuggable=\"true\" (ApplicationInfo.FLAG_DEBUGGABLE)"),
            config.single(id).points ?: 0,
            "Debug builds may let other tools inspect or change the app's data.", RemediationAction.NONE,
        )
    }
}

class CleartextRule : Rule {
    override val id = "CLEARTEXT"
    override val title = "Allows unencrypted network traffic"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        if (app.manifest.cleartextTraffic != true) return null
        if (!app.declares(PermissionCatalog.INTERNET)) return null
        return RuleFinding(
            id, title,
            listOf("usesCleartextTraffic allowed (FLAG_USES_CLEARTEXT_TRAFFIC); a network security config may still limit it"),
            config.single(id).points ?: 0,
            "Data sent without encryption may be read by others on the same network.", RemediationAction.NONE,
        )
    }
}

class ExportedComponentsRule : Rule {
    override val id = "EXPORTED_UNPROTECTED"
    override val title = "Components open to any other app"

    override fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding? {
        val row = config.single(id)
        val exported = app.manifest.exportedUnprotected ?: return null
        if (exported.size < (row.minCount ?: 1)) return null
        val shown = exported.take(4).map { it.substringAfterLast('.') }
        val more = if (exported.size > shown.size) " and ${exported.size - shown.size} more" else ""
        return RuleFinding(
            id, title, listOf("${exported.size} exported services/receivers/providers without a permission: ${shown.joinToString()}$more"),
            row.points ?: 0,
            "Other apps may trigger these parts of the app without any permission.", RemediationAction.NONE,
        )
    }
}

object SingleRules {
    val all: List<Rule> = listOf(
        SpecialAccessRule(
            "ACCESSIBILITY", SpecialAccessType.ACCESSIBILITY, "Accessibility service",
            "May read everything on your screen and tap buttons for you, including in banking apps.",
            RemediationAction.ACCESSIBILITY_SETTINGS,
        ),
        SpecialAccessRule(
            "DEVICE_ADMIN", SpecialAccessType.DEVICE_ADMIN, "Device admin",
            "May lock or wipe the phone, and blocks uninstalling until it is deactivated.",
            RemediationAction.DEVICE_ADMIN_SETTINGS,
        ),
        SpecialAccessRule(
            "NOTIFICATION_LISTENER", SpecialAccessType.NOTIFICATION_LISTENER, "Notification access",
            "May read all your notifications, including one-time passwords and private messages.",
            RemediationAction.NOTIFICATION_LISTENER_SETTINGS,
        ),
        SpecialAccessRule(
            "OVERLAY", SpecialAccessType.OVERLAY, "Display over other apps",
            "May draw on top of other apps, which can be used to show fake login screens.",
            RemediationAction.OVERLAY_SETTINGS,
        ),
        SpecialAccessRule(
            "INSTALL_PACKAGES", SpecialAccessType.INSTALL_PACKAGES, "Install other apps",
            "May ask to install more apps, which could bring in unwanted software.",
            RemediationAction.INSTALL_SOURCES_SETTINGS,
        ),
        SpecialAccessRule(
            "USAGE_STATS", SpecialAccessType.USAGE_STATS, "Usage access",
            "May see which apps you use and for how long.",
            RemediationAction.USAGE_ACCESS_SETTINGS,
        ),
        SpecialAccessRule(
            "ALL_FILES_ACCESS", SpecialAccessType.ALL_FILES_ACCESS, "All files access",
            "May read and change every file in shared storage, including documents and downloads.",
            RemediationAction.ALL_FILES_SETTINGS,
        ),
        PermissionRowRule(
            "SMS", "SMS access",
            "May read your text messages, including OTP codes, or send SMS that can cost money.",
        ),
        PermissionRowRule(
            "CONTACTS_CALL_LOGS", "Contacts or call history access",
            "May copy your contacts and call history, which can be used for scams or extortion.",
        ),
        PermissionRowRule(
            "LOCATION", "Location access",
            "May track where you are.",
            bonus = { app, config ->
                val bg = app.permission(PermissionCatalog.ACCESS_BACKGROUND_LOCATION)
                val extra = config.single("LOCATION").backgroundBonus ?: 0
                if (bg?.grant == GrantState.GRANTED && extra > 0) extra to "Background location granted — can track even when not in use"
                else null
            },
        ),
        PermissionRowRule(
            "CAMERA_MIC", "Camera or microphone access",
            "May take photos or record audio.",
        ),
        PermissionRowRule(
            "PHONE_IDENTITY", "Phone identity or calling access",
            "May read phone identifiers or place calls, which can be used for tracking or call fraud.",
        ),
        PermissionRowRule(
            "MEDIA_STORAGE", "Photos and media access",
            "May view your photos, videos or audio files.",
        ),
        BootPersistenceRule(),
        OutsideStoreRule(),
        LegacyTargetRule(),
        DebuggableRule(),
        CleartextRule(),
        ExportedComponentsRule(),
    )
}
