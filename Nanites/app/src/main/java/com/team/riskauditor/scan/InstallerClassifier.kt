package com.team.riskauditor.scan

import android.content.pm.PackageManager
import android.os.Build
import com.team.riskauditor.model.InstallerInfo
import com.team.riskauditor.model.InstallerSource

/** Maps the recorded installer package to a source. A missing installer is UNKNOWN, never assumed sideloaded. */
class InstallerClassifier(private val pm: PackageManager) {

    fun classify(packageName: String, isSystemApp: Boolean): InstallerInfo {
        val installer = installerOf(packageName)
        val source = when {
            installer == PLAY_STORE -> InstallerSource.PLAY_STORE
            installer in OTHER_STORES -> InstallerSource.OEM_STORE
            installer == null && isSystemApp -> InstallerSource.PRELOADED
            installer == null -> InstallerSource.UNKNOWN
            isSystemApp && installer == "android" -> InstallerSource.PRELOADED
            else -> InstallerSource.SIDELOADED
        }
        return InstallerInfo(source, installer)
    }

    private fun installerOf(packageName: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = pm.getInstallSourceInfo(packageName)
            info.installingPackageName ?: info.initiatingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val PLAY_STORE = "com.android.vending"
        val OTHER_STORES = setOf(
            "com.sec.android.app.samsungapps", // Galaxy Store
            "com.huawei.appmarket",            // AppGallery
            "com.xiaomi.market", "com.xiaomi.mipicks", // GetApps
            "com.heytap.market", "com.oppo.market", // OPPO / realme
            "com.bbk.appstore",                // vivo
            "com.amazon.venezia",              // Amazon Appstore
            "com.oneplus.market",
            "org.fdroid.fdroid",
        )
    }
}
