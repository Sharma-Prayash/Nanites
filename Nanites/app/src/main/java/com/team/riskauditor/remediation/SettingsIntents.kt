package com.team.riskauditor.remediation

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.team.riskauditor.model.RemediationAction
import com.team.riskauditor.model.SpecialAccessType
import com.team.riskauditor.scan.AppResult

/**
 * Targeted Settings destinations (E09). Returns candidates from most to least specific;
 * the launcher tries each until one resolves, ending with the app's own details page.
 */
object SettingsIntents {

    fun appDetails(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))

    fun uninstall(pkg: String) = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))

    fun deviceAdmin() = Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun candidates(action: RemediationAction, app: AppResult): List<Intent> {
        val pkg = app.packageName
        val pkgUri = Uri.fromParts("package", pkg, null)
        val specific: List<Intent> = when (action) {
            RemediationAction.APP_SETTINGS, RemediationAction.NONE, RemediationAction.UNINSTALL -> emptyList()
            RemediationAction.ACCESSIBILITY_SETTINGS -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            RemediationAction.DEVICE_ADMIN_SETTINGS -> listOf(deviceAdmin())
            RemediationAction.NOTIFICATION_LISTENER_SETTINGS -> buildList {
                val component = listenerComponent(app)
                if (component != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()),
                    )
                }
                add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            RemediationAction.OVERLAY_SETTINGS -> listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            )
            RemediationAction.INSTALL_SOURCES_SETTINGS -> listOf(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri),
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            )
            RemediationAction.USAGE_ACCESS_SETTINGS -> listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            RemediationAction.ALL_FILES_SETTINGS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    listOf(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri),
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    )
                } else emptyList()
            RemediationAction.BATTERY_SETTINGS -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        return specific + appDetails(pkg)
    }

    /** Settings label for the button: the specific page when there is one. */
    fun label(action: RemediationAction): String = when (action) {
        RemediationAction.NONE, RemediationAction.UNINSTALL -> RemediationAction.APP_SETTINGS.label
        else -> action.label
    }

    private fun listenerComponent(app: AppResult): ComponentName? =
        app.snapshot.access(SpecialAccessType.NOTIFICATION_LISTENER)?.components?.firstOrNull()
            ?.let { ComponentName(app.packageName, it) }
}
