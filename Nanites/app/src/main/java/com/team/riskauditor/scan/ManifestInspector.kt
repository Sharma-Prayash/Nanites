package com.team.riskauditor.scan

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.team.riskauditor.model.ManifestFacts

/** Static checks (F12) from data PackageManager already parsed. No APK parsing. */
class ManifestInspector {

    fun inspect(info: PackageInfo, componentsLoaded: Boolean): ManifestFacts {
        val app = info.applicationInfo ?: return ManifestFacts()
        val exported: List<String>? = if (componentsLoaded) {
            val services = info.services.orEmpty().filter { it.exported && it.permission == null }.map { it.name }
            val receivers = info.receivers.orEmpty().filter { it.exported && it.permission == null }.map { it.name }
            val providers = info.providers.orEmpty()
                .filter { it.exported && it.readPermission == null && it.writePermission == null }
                .map { it.name }
            services + receivers + providers
        } else null
        return ManifestFacts(
            debuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            cleartextTraffic = app.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0,
            exportedUnprotected = exported,
        )
    }
}
