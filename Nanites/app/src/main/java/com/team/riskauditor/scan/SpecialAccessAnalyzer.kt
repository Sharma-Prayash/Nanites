package com.team.riskauditor.scan

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.core.app.NotificationManagerCompat
import com.team.riskauditor.model.ActiveState
import com.team.riskauditor.model.SpecialAccessObservation
import com.team.riskauditor.model.SpecialAccessType
import com.team.riskauditor.security.catalog.PermissionCatalog

/** Result of a device-wide check: either the set of package names, or the reason we could not read it. */
sealed interface DeviceCheck {
    data class Ok(val packages: Set<String>, val source: String) : DeviceCheck
    data class Failed(val state: ActiveState, val reason: String) : DeviceCheck
}

/** Device-wide special-access lists, read once per scan. */
data class DeviceAccessState(
    val accessibility: DeviceCheck,
    val deviceAdmins: DeviceCheck,
    val notificationListeners: DeviceCheck,
) {
    fun summary(): List<Pair<String, DeviceCheck>> = listOf(
        "Accessibility services" to accessibility,
        "Device admins" to deviceAdmins,
        "Notification listeners" to notificationListeners,
    )
}

/** Special access (F04): declaration from the manifest, live state from the platform. */
class SpecialAccessAnalyzer(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    fun readDeviceState(): DeviceAccessState = DeviceAccessState(
        accessibility = readAccessibility(),
        deviceAdmins = readDeviceAdmins(),
        notificationListeners = readListeners(),
    )

    fun analyze(info: PackageInfo, device: DeviceAccessState): List<SpecialAccessObservation> {
        val pkg = info.packageName
        val uid = info.applicationInfo?.uid ?: -1
        val requested = info.requestedPermissions?.toSet().orEmpty()

        val a11yServices = info.services.orEmpty()
            .filter { it.permission == PermissionCatalog.BIND_ACCESSIBILITY_SERVICE }
            .map { it.name }
        val listenerServices = info.services.orEmpty()
            .filter { it.permission == PermissionCatalog.BIND_NOTIFICATION_LISTENER_SERVICE }
            .map { it.name }
        val adminReceivers = info.receivers.orEmpty()
            .filter { it.permission == PermissionCatalog.BIND_DEVICE_ADMIN }
            .map { it.name }

        return listOf(
            componentAccess(SpecialAccessType.ACCESSIBILITY, pkg, a11yServices, device.accessibility, "service"),
            componentAccess(SpecialAccessType.DEVICE_ADMIN, pkg, adminReceivers, device.deviceAdmins, "receiver"),
            componentAccess(SpecialAccessType.NOTIFICATION_LISTENER, pkg, listenerServices, device.notificationListeners, "service"),
            opAccess(
                SpecialAccessType.OVERLAY, pkg, uid, requested,
                PermissionCatalog.SYSTEM_ALERT_WINDOW, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            ),
            opAccess(
                SpecialAccessType.INSTALL_PACKAGES, pkg, uid, requested,
                PermissionCatalog.REQUEST_INSTALL_PACKAGES, OP_REQUEST_INSTALL_PACKAGES,
            ),
            opAccess(
                SpecialAccessType.USAGE_STATS, pkg, uid, requested,
                PermissionCatalog.PACKAGE_USAGE_STATS, AppOpsManager.OPSTR_GET_USAGE_STATS,
            ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                opAccess(
                    SpecialAccessType.ALL_FILES_ACCESS, pkg, uid, requested,
                    PermissionCatalog.MANAGE_EXTERNAL_STORAGE, OP_MANAGE_EXTERNAL_STORAGE,
                )
            } else {
                SpecialAccessObservation(
                    SpecialAccessType.ALL_FILES_ACCESS, PermissionCatalog.MANAGE_EXTERNAL_STORAGE in requested,
                    ActiveState.UNSUPPORTED, listOf("All files access exists from Android 11"),
                )
            },
            batteryAccess(pkg, requested),
        )
    }

    private fun componentAccess(
        type: SpecialAccessType,
        pkg: String,
        components: List<String>,
        device: DeviceCheck,
        kind: String,
    ): SpecialAccessObservation {
        val evidence = components.take(3).map { "Declares $kind ${it.removePrefix(pkg)}" }
        val state = when (device) {
            is DeviceCheck.Ok -> if (pkg in device.packages) ActiveState.ENABLED else ActiveState.NOT_ENABLED
            is DeviceCheck.Failed -> device.state
        }
        val source = when (device) {
            is DeviceCheck.Ok -> if (state == ActiveState.ENABLED) listOf("Enabled per ${device.source}") else emptyList()
            is DeviceCheck.Failed -> listOf(device.reason)
        }
        return SpecialAccessObservation(type, components.isNotEmpty(), state, evidence + source, components)
    }

    private fun opAccess(
        type: SpecialAccessType,
        pkg: String,
        uid: Int,
        requested: Set<String>,
        permission: String,
        op: String,
    ): SpecialAccessObservation {
        val declared = permission in requested
        if (!declared) return SpecialAccessObservation(type, false, ActiveState.NOT_CHECKED)
        val short = permission.substringAfterLast('.')
        if (uid < 0) return SpecialAccessObservation(type, true, ActiveState.UNKNOWN, listOf("Declares $short"))
        val state = try {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(op, uid, pkg)
            } else {
                appOps.checkOpNoThrow(op, uid, pkg)
            }
            when (mode) {
                AppOpsManager.MODE_ALLOWED, AppOpsManager.MODE_FOREGROUND -> ActiveState.ENABLED
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> ActiveState.NOT_ENABLED
                AppOpsManager.MODE_DEFAULT ->
                    if (pm.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED) ActiveState.ENABLED
                    else ActiveState.NOT_ENABLED
                else -> ActiveState.UNKNOWN
            }
        } catch (_: SecurityException) {
            ActiveState.CHECK_FAILED
        } catch (_: IllegalArgumentException) {
            ActiveState.UNSUPPORTED
        }
        val via = if (state.isObserved) "checked via AppOps" else "AppOps check: ${state.label.lowercase()}"
        return SpecialAccessObservation(type, true, state, listOf("Declares $short", via))
    }

    private fun batteryAccess(pkg: String, requested: Set<String>): SpecialAccessObservation {
        val declared = PermissionCatalog.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in requested
        val state = try {
            if (power.isIgnoringBatteryOptimizations(pkg)) ActiveState.ENABLED else ActiveState.NOT_ENABLED
        } catch (_: SecurityException) {
            ActiveState.CHECK_FAILED
        }
        return SpecialAccessObservation(SpecialAccessType.BATTERY_EXEMPTION, declared, state)
    }

    private fun readAccessibility(): DeviceCheck {
        val packages = mutableSetOf<String>()
        var ok = false
        try {
            val am = context.getSystemService(AccessibilityManager::class.java)
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNullTo(packages) { it.resolveInfo?.serviceInfo?.packageName }
            ok = true
        } catch (_: RuntimeException) {
        }
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')
                ?.mapNotNullTo(packages) { ComponentName.unflattenFromString(it)?.packageName }
            ok = true
        } catch (_: RuntimeException) {
        }
        return if (ok) DeviceCheck.Ok(packages, "AccessibilityManager")
        else DeviceCheck.Failed(ActiveState.CHECK_FAILED, "Could not read enabled accessibility services")
    }

    private fun readDeviceAdmins(): DeviceCheck = try {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admins = dpm.activeAdmins.orEmpty().map { it.packageName }.toSet()
        DeviceCheck.Ok(admins, "DevicePolicyManager")
    } catch (_: RuntimeException) {
        DeviceCheck.Failed(ActiveState.CHECK_FAILED, "Could not read active device admins")
    }

    private fun readListeners(): DeviceCheck = try {
        DeviceCheck.Ok(NotificationManagerCompat.getEnabledListenerPackages(context), "notification access settings")
    } catch (_: RuntimeException) {
        DeviceCheck.Failed(ActiveState.CHECK_FAILED, "Could not read enabled notification listeners")
    }

    private companion object {
        // Not public OPSTR constants in the SDK; an unknown op throws and maps to UNSUPPORTED.
        const val OP_REQUEST_INSTALL_PACKAGES = "android:request_install_packages"
        const val OP_MANAGE_EXTERNAL_STORAGE = "android:manage_external_storage"
    }
}
