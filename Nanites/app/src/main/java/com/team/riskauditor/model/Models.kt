package com.team.riskauditor.model

import kotlinx.serialization.Serializable

/** The nine PS-01 categories, plus OTHER for permissions that are recorded but not scored. */
@Serializable
enum class PermissionCategory(val title: String, val blurb: String) {
    SPECIAL_ACCESS("Special access", "Screen reading, overlays, device admin, notification access"),
    SMS("SMS", "Read, receive or send text messages"),
    CONTACTS_CALL_LOGS("Contacts & call logs", "Your contacts and call history"),
    CAMERA_MIC("Camera & microphone", "Take photos, record video or audio"),
    LOCATION("Location", "Where your phone is"),
    PHONE_IDENTITY("Phone & identity", "Phone number, device IDs, place calls"),
    STORAGE_MEDIA("Storage & media", "Photos, videos, files"),
    BACKGROUND_PERSISTENCE("Background & persistence", "Start at boot, keep running"),
    NETWORK("Network", "Internet access"),
    OTHER("Other", "Not used for scoring");

    companion object {
        val NINE = entries.filter { it != OTHER }
    }
}

@Serializable
enum class ProtectionLevel(val label: String) {
    NORMAL("Normal"), DANGEROUS("Dangerous"), SIGNATURE("Signature"), SPECIAL("Special"), UNKNOWN("Unknown")
}

/** Grant state of a regular permission. UNKNOWN is never treated as "not granted". */
@Serializable
enum class GrantState { GRANTED, NOT_GRANTED, UNKNOWN }

@Serializable
data class PermissionObservation(
    val name: String,
    val protection: ProtectionLevel,
    val grant: GrantState,
    val category: PermissionCategory,
) {
    val shortName: String get() = name.substringAfterLast('.')
}

@Serializable
enum class SpecialAccessType(val title: String) {
    ACCESSIBILITY("Accessibility service"),
    DEVICE_ADMIN("Device admin"),
    NOTIFICATION_LISTENER("Notification access"),
    OVERLAY("Display over other apps"),
    INSTALL_PACKAGES("Install unknown apps"),
    USAGE_STATS("Usage access"),
    ALL_FILES_ACCESS("All files access"),
    BATTERY_EXEMPTION("Unrestricted battery"),
}

/**
 * Whether a special access is currently switched on. Every value is distinct on purpose:
 * only ENABLED and NOT_ENABLED are observations; the rest say why we could not observe.
 */
@Serializable
enum class ActiveState(val label: String) {
    ENABLED("On"),
    NOT_ENABLED("Off"),
    UNKNOWN("Unknown"),
    UNSUPPORTED("Not supported on this Android version"),
    NOT_CHECKED("Not checked"),
    CHECK_FAILED("Check failed"),
    ;

    val isObserved: Boolean get() = this == ENABLED || this == NOT_ENABLED
}

@Serializable
data class SpecialAccessObservation(
    val type: SpecialAccessType,
    /** App declares the permission / component needed for this access. */
    val declared: Boolean,
    val state: ActiveState,
    /** Human-readable source of the observation, e.g. component name or API used. */
    val evidence: List<String> = emptyList(),
    /** Fully-qualified component class names behind this access (services / receivers). */
    val components: List<String> = emptyList(),
)

@Serializable
enum class InstallerSource(val label: String) {
    PLAY_STORE("Google Play Store"),
    OEM_STORE("Device-maker or other app store"),
    SIDELOADED("Installed outside an app store"),
    PRELOADED("Pre-installed"),
    UNKNOWN("Unknown — not recorded by Android"),
}

@Serializable
data class InstallerInfo(val source: InstallerSource, val installerPackage: String?)

@Serializable
enum class AppRole(val label: String) {
    DEFAULT_SMS("Default SMS app"),
    DEFAULT_DIALER("Default phone app"),
    DEFAULT_LAUNCHER("Default home app"),
    DEFAULT_KEYBOARD("Default keyboard"),
    SECURITY_OR_MDM("Device owner / work-profile manager"),
}

/** Static manifest facts (F12). Null = not checked. */
@Serializable
data class ManifestFacts(
    val debuggable: Boolean? = null,
    val cleartextTraffic: Boolean? = null,
    val exportedUnprotected: List<String>? = null,
)

/** Everything observed about one app in one scan. Pure data: the engine never touches Android. */
@Serializable
data class AppSnapshot(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val firstInstallTime: Long?,
    val lastUpdateTime: Long?,
    val installer: InstallerInfo,
    val targetSdk: Int,
    val isSystemApp: Boolean,
    val permissions: List<PermissionObservation>,
    val specialAccess: List<SpecialAccessObservation>,
    val manifest: ManifestFacts = ManifestFacts(),
) {
    fun permission(name: String): PermissionObservation? = permissions.firstOrNull { it.name == name }
    fun declares(name: String): Boolean = permissions.any { it.name == name }
    fun access(type: SpecialAccessType): SpecialAccessObservation? = specialAccess.firstOrNull { it.type == type }
}

/** Where a finding sends the user. Resolved into an Intent by remediation/SettingsIntents. */
@Serializable
enum class RemediationAction(val label: String) {
    APP_SETTINGS("Open app permissions"),
    ACCESSIBILITY_SETTINGS("Open accessibility settings"),
    DEVICE_ADMIN_SETTINGS("Open device admin settings"),
    NOTIFICATION_LISTENER_SETTINGS("Open notification access"),
    OVERLAY_SETTINGS("Open \"display over other apps\""),
    INSTALL_SOURCES_SETTINGS("Open \"install unknown apps\""),
    USAGE_ACCESS_SETTINGS("Open usage access"),
    ALL_FILES_SETTINGS("Open all files access"),
    BATTERY_SETTINGS("Open battery optimisation"),
    UNINSTALL("Uninstall if you don't recognise it"),
    NONE("No action needed"),
}

@Serializable
enum class RiskBand(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High"), CRITICAL("Critical") }
