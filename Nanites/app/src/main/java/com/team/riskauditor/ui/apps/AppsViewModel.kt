package com.team.riskauditor.ui.apps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.team.riskauditor.data.ScanRepository
import com.team.riskauditor.model.PermissionCategory
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.scan.AppResult
import com.team.riskauditor.ui.AppsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class SortOrder(val label: String) { RISK("Highest risk"), NAME("Name"), RECENT("Recently installed") }

data class AppsFilter(
    val band: RiskBand? = null,
    val category: PermissionCategory? = null,
    val specialOnly: Boolean = false,
    val sort: SortOrder = SortOrder.RISK,
)

data class AppsUiState(
    val hasScan: Boolean,
    val scanning: Boolean,
    val includeSystem: Boolean,
    val total: Int,
    val filter: AppsFilter,
    val apps: List<AppResult>,
)

class AppsViewModel(private val repository: ScanRepository, handle: SavedStateHandle) : ViewModel() {

    private val route = handle.toRoute<AppsRoute>()
    private val filter = MutableStateFlow(
        AppsFilter(
            band = route.band?.let { runCatching { RiskBand.valueOf(it) }.getOrNull() },
            category = route.category?.let { runCatching { PermissionCategory.valueOf(it) }.getOrNull() },
            specialOnly = route.specialOnly,
        ),
    )

    /** Held in Compose state, not a StateFlow, so fast typing never loses characters. */
    var query by mutableStateOf("")
        private set

    val state: StateFlow<AppsUiState> = combine(repository.latest, repository.status, filter, snapshotFlow { query }) { latest, status, f, q ->
        val all = latest?.apps.orEmpty()
        AppsUiState(
            hasScan = latest != null,
            scanning = status.running,
            includeSystem = latest?.includeSystem ?: false,
            total = all.size,
            filter = f,
            apps = all.filter { it.matches(f, q) }.sortedWith(f.sort.comparator()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState(false, false, false, 0, filter.value, emptyList()))

    fun onQueryChange(q: String) {
        query = q
    }
    fun setBand(b: RiskBand?) = filter.update { it.copy(band = b) }
    fun setCategory(c: PermissionCategory?) = filter.update { it.copy(category = c) }
    fun toggleSpecial() = filter.update { it.copy(specialOnly = !it.specialOnly) }
    fun setSort(s: SortOrder) = filter.update { it.copy(sort = s) }
    fun scan() = repository.startScan()

    private fun AppResult.matches(f: AppsFilter, query: String): Boolean {
        val q = query.trim()
        if (q.isNotEmpty() && !snapshot.label.contains(q, true) && !packageName.contains(q, true)) return false
        if (f.band != null && assessment.band != f.band) return false
        if (f.category != null && f.category !in categories) return false
        if (f.specialOnly && enabledSpecialAccess.isEmpty()) return false
        return true
    }

    private fun SortOrder.comparator(): Comparator<AppResult> = when (this) {
        SortOrder.RISK -> compareByDescending<AppResult> { it.assessment.score }.thenBy { it.snapshot.label.lowercase() }
        SortOrder.NAME -> compareBy { it.snapshot.label.lowercase() }
        SortOrder.RECENT -> compareByDescending<AppResult> { it.snapshot.firstInstallTime ?: Long.MIN_VALUE }
    }
}
