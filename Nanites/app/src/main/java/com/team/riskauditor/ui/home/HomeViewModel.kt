package com.team.riskauditor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.riskauditor.data.ScanRepository
import com.team.riskauditor.data.ScanStatus
import com.team.riskauditor.model.PermissionCategory
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.scan.AppResult
import com.team.riskauditor.scan.DeviceCheckSummary
import com.team.riskauditor.scan.ScanResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeSummary(
    val completedAt: Long,
    val durationMs: Long,
    val inspected: Int,
    val includeSystem: Boolean,
    val bandCounts: Map<RiskBand, Int>,
    val specialAccessApps: Int,
    val topApps: List<AppResult>,
    val categoryCounts: Map<PermissionCategory, Int>,
    val failures: Int,
    val failedChecks: List<DeviceCheckSummary>,
) {
    val attention: Int get() = (bandCounts[RiskBand.HIGH] ?: 0) + (bandCounts[RiskBand.CRITICAL] ?: 0)
    val worst: RiskBand get() = RiskBand.entries.last { (bandCounts[it] ?: 0) > 0 || it == RiskBand.LOW }
}

data class HomeUiState(val status: ScanStatus, val summary: HomeSummary?)

class HomeViewModel(private val repository: ScanRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(repository.status, repository.latest) { status, latest ->
        HomeUiState(status, latest?.toSummary())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(repository.status.value, repository.latest.value?.toSummary()))

    fun scan() = repository.startScan()
    fun cancel() = repository.cancelScan()

    private fun ScanResult.toSummary() = HomeSummary(
        completedAt = completedAt,
        durationMs = completedAt - startedAt,
        inspected = apps.size,
        includeSystem = includeSystem,
        bandCounts = bandCounts,
        specialAccessApps = apps.count { it.enabledSpecialAccess.isNotEmpty() },
        topApps = apps.filter { it.assessment.score > 0 }.take(5),
        categoryCounts = PermissionCategory.NINE.associateWith { c -> apps.count { c in it.categories } },
        failures = failures.size,
        failedChecks = deviceChecks.filterNot { it.ok },
    )
}
