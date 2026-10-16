package com.team.riskauditor.scan

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.team.riskauditor.model.ActiveState
import com.team.riskauditor.model.AppSnapshot
import com.team.riskauditor.model.GrantState
import com.team.riskauditor.model.SpecialAccessType

/** Inventory (F01) and per-app snapshot assembly. All calls are blocking: run on Dispatchers.IO. */
class PackageScanner(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val installers = InstallerClassifier(pm)
    private val permissions = PermissionAnalyzer(pm)
    private val manifest = ManifestInspector()
    val special = SpecialAccessAnalyzer(context)

    /** Visible packages, excluding the auditor itself. */
    fun listPackages(includeSystem: Boolean): List<PackageInfo> =
        getInstalled(0).filter { info ->
            info.packageName != context.packageName && (includeSystem || !isSystem(info.applicationInfo))
        }

    /** Null when the package is gone (uninstalled between listing and inspection). */
    fun snapshot(packageName: String, device: DeviceAccessState): AppSnapshot? {
        val (info, componentsLoaded) = loadDetailed(packageName) ?: return null
        val app = info.applicationInfo ?: return null
        val system = isSystem(app)
        val perms = permissions.analyze(info)
        val access = special.analyze(info, device)
        // Resolve app-op permission grants from the live AppOps state so the permission list stays honest.
        val opGrant = access.associate { it.type to it.state }
        val resolvedPerms = perms.map { p ->
            val type = SPECIAL_PERMISSION_TYPES[p.name] ?: return@map p
            val state = opGrant[type] ?: return@map p
            p.copy(
                grant = when (state) {
                    ActiveState.ENABLED -> GrantState.GRANTED
                    ActiveState.NOT_ENABLED -> GrantState.NOT_GRANTED
                    else -> GrantState.UNKNOWN
                },
            )
        }
        return AppSnapshot(
            packageName = info.packageName,
            label = safeLabel(app),
            versionName = info.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            firstInstallTime = info.firstInstallTime.takeIf { it > 0 },
            lastUpdateTime = info.lastUpdateTime.takeIf { it > 0 },
            installer = installers.classify(info.packageName, system),
            targetSdk = app.targetSdkVersion,
            isSystemApp = system,
            permissions = resolvedPerms,
            specialAccess = access,
            manifest = manifest.inspect(info, componentsLoaded),
        )
    }

    fun labelOf(info: PackageInfo): String = info.applicationInfo?.let { safeLabel(it) } ?: info.packageName

    private fun safeLabel(app: ApplicationInfo): String = try {
        app.loadLabel(pm).toString().ifBlank { app.packageName }
    } catch (_: RuntimeException) {
        app.packageName
    }

    /**
     * Full component lists can exceed the binder limit for very large apps, so step down.
     * Returns whether service/receiver/provider lists were loaded.
     */
    private fun loadDetailed(packageName: String): Pair<PackageInfo, Boolean>? {
        val attempts = listOf(
            FULL to true,
            (PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS) to false,
            PackageManager.GET_PERMISSIONS to false,
        )
        for ((flags, complete) in attempts) {
            try {
                return getInfo(packageName, flags) to complete
            } catch (_: PackageManager.NameNotFoundException) {
                return null
            } catch (_: RuntimeException) {
                // TransactionTooLargeException surfaces as a RuntimeException here; try fewer flags.
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun getInfo(packageName: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }

    @Suppress("DEPRECATION")
    private fun getInstalled(flags: Int): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }

    private companion object {
        val FULL = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS

        val SPECIAL_PERMISSION_TYPES = mapOf(
            "android.permission.SYSTEM_ALERT_WINDOW" to SpecialAccessType.OVERLAY,
            "android.permission.REQUEST_INSTALL_PACKAGES" to SpecialAccessType.INSTALL_PACKAGES,
            "android.permission.PACKAGE_USAGE_STATS" to SpecialAccessType.USAGE_STATS,
            "android.permission.MANAGE_EXTERNAL_STORAGE" to SpecialAccessType.ALL_FILES_ACCESS,
        )

        fun isSystem(app: ApplicationInfo?): Boolean =
            app != null && app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
}
