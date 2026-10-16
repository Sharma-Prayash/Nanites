package com.team.riskauditor.security.catalog

import com.team.riskauditor.model.PermissionCategory
import com.team.riskauditor.model.PermissionCategory.BACKGROUND_PERSISTENCE
import com.team.riskauditor.model.PermissionCategory.CAMERA_MIC
import com.team.riskauditor.model.PermissionCategory.CONTACTS_CALL_LOGS
import com.team.riskauditor.model.PermissionCategory.LOCATION
import com.team.riskauditor.model.PermissionCategory.NETWORK
import com.team.riskauditor.model.PermissionCategory.PHONE_IDENTITY
import com.team.riskauditor.model.PermissionCategory.SMS
import com.team.riskauditor.model.PermissionCategory.SPECIAL_ACCESS
import com.team.riskauditor.model.PermissionCategory.STORAGE_MEDIA

/**
 * Known permissions: which of the nine categories they belong to, which scoring row
 * (key in scoring_config.json singleFindings) they feed, and a plain-language meaning.
 */
data class CatalogEntry(
    val category: PermissionCategory,
    val findingKey: String?,
    val meaning: String,
)

object PermissionCatalog {
    const val INTERNET = "android.permission.INTERNET"
    const val RECEIVE_BOOT_COMPLETED = "android.permission.RECEIVE_BOOT_COMPLETED"
    const val FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
    const val ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
    const val SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW"
    const val REQUEST_INSTALL_PACKAGES = "android.permission.REQUEST_INSTALL_PACKAGES"
    const val PACKAGE_USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS"
    const val MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"
    const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
    const val BIND_ACCESSIBILITY_SERVICE = "android.permission.BIND_ACCESSIBILITY_SERVICE"
    const val BIND_NOTIFICATION_LISTENER_SERVICE = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    const val BIND_DEVICE_ADMIN = "android.permission.BIND_DEVICE_ADMIN"

    private const val P = "android.permission."

    private val entries: Map<String, CatalogEntry> = buildMap {
        fun add(name: String, category: PermissionCategory, key: String?, meaning: String) =
            put(P + name, CatalogEntry(category, key, meaning))

        // Special access (scored through the special-access observations, not the permission row)
        add("SYSTEM_ALERT_WINDOW", SPECIAL_ACCESS, "OVERLAY", "Draw on top of other apps")
        add("REQUEST_INSTALL_PACKAGES", SPECIAL_ACCESS, "INSTALL_PACKAGES", "Ask to install other apps")
        add("PACKAGE_USAGE_STATS", SPECIAL_ACCESS, "USAGE_STATS", "See which apps you use")
        add("BIND_ACCESSIBILITY_SERVICE", SPECIAL_ACCESS, "ACCESSIBILITY", "Act as an accessibility service")
        add("BIND_NOTIFICATION_LISTENER_SERVICE", SPECIAL_ACCESS, "NOTIFICATION_LISTENER", "Read notifications")
        add("BIND_DEVICE_ADMIN", SPECIAL_ACCESS, "DEVICE_ADMIN", "Act as device administrator")

        add("READ_SMS", SMS, "SMS", "Read your text messages")
        add("RECEIVE_SMS", SMS, "SMS", "Receive text messages, including OTPs")
        add("SEND_SMS", SMS, "SMS", "Send text messages (may cost money)")
        add("RECEIVE_MMS", SMS, "SMS", "Receive picture messages")
        add("RECEIVE_WAP_PUSH", SMS, "SMS", "Receive WAP push messages")

        add("READ_CONTACTS", CONTACTS_CALL_LOGS, "CONTACTS_CALL_LOGS", "Read your contacts")
        add("WRITE_CONTACTS", CONTACTS_CALL_LOGS, "CONTACTS_CALL_LOGS", "Change your contacts")
        add("READ_CALL_LOG", CONTACTS_CALL_LOGS, "CONTACTS_CALL_LOGS", "Read your call history")
        add("WRITE_CALL_LOG", CONTACTS_CALL_LOGS, "CONTACTS_CALL_LOGS", "Change your call history")
        add("PROCESS_OUTGOING_CALLS", CONTACTS_CALL_LOGS, "CONTACTS_CALL_LOGS", "See numbers you dial")

        add("CAMERA", CAMERA_MIC, "CAMERA_MIC", "Take photos and videos")
        add("RECORD_AUDIO", CAMERA_MIC, "CAMERA_MIC", "Record audio")

        add("ACCESS_FINE_LOCATION", LOCATION, "LOCATION", "Precise location")
        add("ACCESS_COARSE_LOCATION", LOCATION, "LOCATION", "Approximate location")
        add("ACCESS_BACKGROUND_LOCATION", LOCATION, "LOCATION", "Location while not in use")

        add("READ_PHONE_STATE", PHONE_IDENTITY, "PHONE_IDENTITY", "Read phone status and identity")
        add("READ_PHONE_NUMBERS", PHONE_IDENTITY, "PHONE_IDENTITY", "Read your phone number")
        add("CALL_PHONE", PHONE_IDENTITY, "PHONE_IDENTITY", "Place phone calls")
        add("ANSWER_PHONE_CALLS", PHONE_IDENTITY, "PHONE_IDENTITY", "Answer phone calls")

        add("READ_MEDIA_IMAGES", STORAGE_MEDIA, "MEDIA_STORAGE", "Read your photos")
        add("READ_MEDIA_VIDEO", STORAGE_MEDIA, "MEDIA_STORAGE", "Read your videos")
        add("READ_MEDIA_AUDIO", STORAGE_MEDIA, "MEDIA_STORAGE", "Read your audio files")
        add("READ_MEDIA_VISUAL_USER_SELECTED", STORAGE_MEDIA, "MEDIA_STORAGE", "Read photos you pick")
        add("READ_EXTERNAL_STORAGE", STORAGE_MEDIA, "MEDIA_STORAGE", "Read shared storage")
        add("WRITE_EXTERNAL_STORAGE", STORAGE_MEDIA, "MEDIA_STORAGE", "Change shared storage")
        add("MANAGE_EXTERNAL_STORAGE", STORAGE_MEDIA, "ALL_FILES_ACCESS", "Read and change all files")

        add("RECEIVE_BOOT_COMPLETED", BACKGROUND_PERSISTENCE, "BOOT_PERSISTENCE", "Start when the phone boots")
        add("FOREGROUND_SERVICE", BACKGROUND_PERSISTENCE, null, "Keep running in the background")
        add("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", BACKGROUND_PERSISTENCE, null, "Ask to skip battery limits")

        add("INTERNET", NETWORK, "NETWORK_ONLY", "Full network access")
        add("ACCESS_NETWORK_STATE", NETWORK, null, "View network connections")
        add("ACCESS_WIFI_STATE", NETWORK, null, "View Wi-Fi connections")
        add("CHANGE_NETWORK_STATE", NETWORK, null, "Change network connectivity")
    }

    fun lookup(permission: String): CatalogEntry? =
        entries[permission] ?: if (permission.startsWith(P + "FOREGROUND_SERVICE_")) {
            CatalogEntry(BACKGROUND_PERSISTENCE, null, "Keep running in the background")
        } else null

    fun categoryOf(permission: String): PermissionCategory =
        lookup(permission)?.category ?: PermissionCategory.OTHER

    /** Permissions that feed a given singleFindings row. */
    fun permissionsFor(findingKey: String): Set<String> =
        entries.filterValues { it.findingKey == findingKey }.keys

    /** Categories whose new appearance after an update counts for R008. */
    val sensitiveCategories: Set<PermissionCategory> = setOf(
        SPECIAL_ACCESS, SMS, CONTACTS_CALL_LOGS, CAMERA_MIC, LOCATION, PHONE_IDENTITY, STORAGE_MEDIA,
    )

    fun isSensitive(permission: String): Boolean = categoryOf(permission) in sensitiveCategories
}
