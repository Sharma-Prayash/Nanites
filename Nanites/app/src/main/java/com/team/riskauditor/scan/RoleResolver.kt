package com.team.riskauditor.scan

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.team.riskauditor.model.AppRole

/** Device roles (E02). A role explains access that would otherwise look risky, e.g. SMS for the SMS app. */
class RoleResolver(private val context: Context) {

    data class Roles(
        val sms: String?,
        val dialer: String?,
        val launcher: String?,
        val keyboard: String?,
    )

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)

    fun read(): Roles = Roles(
        sms = safe { Telephony.Sms.getDefaultSmsPackage(context) },
        dialer = safe { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage },
        launcher = safe {
            context.packageManager
                .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" } // "android" = chooser, no default set
        },
        keyboard = safe {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.let { ComponentName.unflattenFromString(it)?.packageName }
        },
    )

    fun rolesFor(packageName: String, roles: Roles): Set<AppRole> = buildSet {
        if (packageName == roles.sms) add(AppRole.DEFAULT_SMS)
        if (packageName == roles.dialer) add(AppRole.DEFAULT_DIALER)
        if (packageName == roles.launcher) add(AppRole.DEFAULT_LAUNCHER)
        if (packageName == roles.keyboard) add(AppRole.DEFAULT_KEYBOARD)
        val owner = safe { dpm?.isDeviceOwnerApp(packageName) == true || dpm?.isProfileOwnerApp(packageName) == true }
        if (owner == true) add(AppRole.SECURITY_OR_MDM)
    }

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: RuntimeException) {
        null
    }
}
