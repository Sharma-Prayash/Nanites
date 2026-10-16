package com.team.riskauditor.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.team.riskauditor.data.ScanRepository
import com.team.riskauditor.data.Verification
import com.team.riskauditor.scan.AppResult
import com.team.riskauditor.ui.DetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val repository: ScanRepository, handle: SavedStateHandle) : ViewModel() {

    val packageName: String = handle.toRoute<DetailRoute>().packageName

    val app: StateFlow<AppResult?> = repository.latest
        .map { latest -> latest?.apps?.firstOrNull { it.packageName == packageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.app(packageName))

    private val _verification = MutableStateFlow<Verification?>(null)
    val verification: StateFlow<Verification?> = _verification.asStateFlow()

    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying.asStateFlow()

    /** Called whenever the user comes back from Settings or the uninstall dialog (E03). */
    fun verifyAfterReturn() {
        viewModelScope.launch {
            _verifying.value = true
            _verification.value = repository.verify(packageName)
            _verifying.value = false
        }
    }

    fun dismissVerification() {
        _verification.value = null
    }
}
