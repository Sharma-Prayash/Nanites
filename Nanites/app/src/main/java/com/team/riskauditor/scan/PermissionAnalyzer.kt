package com.team.riskauditor.scan

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.team.riskauditor.model.GrantState
import com.team.riskauditor.model.PermissionObservation
import com.team.riskauditor.model.ProtectionLevel
import com.team.riskauditor.security.catalog.PermissionCatalog
import java.util.concurrent.ConcurrentHashMap

/** Declared vs granted permissions with protection level (F02). */
class PermissionAnalyzer(private val pm: PackageManager) {

    private val protectionCache = ConcurrentHashMap<String, ProtectionLevel>()

    fun analyze(info: PackageInfo): List<PermissionObservation> {
        val names = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags
        return names.indices.map { i ->
            val name = names[i]
            val protection = protectionOf(name)
            val grant = when {
                // App-op permissions: the flag does not reflect the user's toggle; SpecialAccessAnalyzer resolves it.
                protection == ProtectionLevel.SPECIAL -> GrantState.UNKNOWN
                flags == null || i >= flags.size -> GrantState.UNKNOWN
                flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 -> GrantState.GRANTED
                else -> GrantState.NOT_GRANTED
            }
            PermissionObservation(name, protection, grant, PermissionCatalog.categoryOf(name))
        }.distinctBy { it.name }
    }

    private fun protectionOf(name: String): ProtectionLevel = protectionCache.getOrPut(name) {
        try {
            val p = pm.getPermissionInfo(name, 0)
            val base: Int
            val appOp: Boolean
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                base = p.protection
                appOp = p.protectionFlags and PermissionInfo.PROTECTION_FLAG_APPOP != 0
            } else {
                @Suppress("DEPRECATION") val level = p.protectionLevel
                @Suppress("DEPRECATION") val mask = PermissionInfo.PROTECTION_MASK_BASE
                base = level and mask
                appOp = level and PermissionInfo.PROTECTION_FLAG_APPOP != 0
            }
            when {
                appOp -> ProtectionLevel.SPECIAL
                base == PermissionInfo.PROTECTION_DANGEROUS -> ProtectionLevel.DANGEROUS
                base == PermissionInfo.PROTECTION_NORMAL -> ProtectionLevel.NORMAL
                base == PermissionInfo.PROTECTION_SIGNATURE -> ProtectionLevel.SIGNATURE
                else -> ProtectionLevel.UNKNOWN
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // Custom permission from an app that isn't installed, or removed from this Android version.
            ProtectionLevel.UNKNOWN
        } catch (_: SecurityException) {
            ProtectionLevel.UNKNOWN
        }
    }
}
