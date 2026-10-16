package com.team.riskauditor.scan

import com.team.riskauditor.model.ActiveState
import com.team.riskauditor.model.AppRole
import com.team.riskauditor.model.AppSnapshot
import com.team.riskauditor.model.PermissionCategory
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.model.SpecialAccessType
import com.team.riskauditor.security.AppAssessment

data class AppResult(
    val snapshot: AppSnapshot,
    val assessment: AppAssessment,
    val roles: Set<AppRole>,
    val scannedAt: Long,
) {
    val packageName: String get() = snapshot.packageName

    /** Special accesses currently switched on (battery exemption is common and not counted here). */
    val enabledSpecialAccess: List<SpecialAccessType>
        get() = snapshot.specialAccess
            .filter { it.state == ActiveState.ENABLED && it.type != SpecialAccessType.BATTERY_EXEMPTION }
            .map { it.type }

    /** Categories this app touches (any declared permission or declared/enabled special access). */
    val categories: Set<PermissionCategory>
        get() = buildSet {
            snapshot.permissions.mapTo(this) { it.category }
            if (snapshot.specialAccess.any {
                    it.type != SpecialAccessType.BATTERY_EXEMPTION && (it.declared || it.state == ActiveState.ENABLED)
                }
            ) add(PermissionCategory.SPECIAL_ACCESS)
            remove(PermissionCategory.OTHER)
        }
}

data class DeviceCheckSummary(val name: String, val ok: Boolean, val detail: String)

data class ScanFailure(val packageName: String, val reason: String)

data class ScanResult(
    val startedAt: Long,
    val completedAt: Long,
    val includeSystem: Boolean,
    val ruleVersion: String,
    val apps: List<AppResult>,
    val failures: List<ScanFailure>,
    val deviceChecks: List<DeviceCheckSummary>,
) {
    val bandCounts: Map<RiskBand, Int> by lazy {
        RiskBand.entries.associateWith { band -> apps.count { it.assessment.band == band } }
    }
    val isPartial: Boolean get() = failures.isNotEmpty() || deviceChecks.any { !it.ok }
}

sealed interface ScanProgress {
    data object Preparing : ScanProgress
    data class Inspecting(val done: Int, val total: Int, val currentLabel: String?) : ScanProgress
    data class Finished(val result: ScanResult) : ScanProgress
}
