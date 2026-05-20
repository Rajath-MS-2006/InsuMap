package com.insuranceclaimsmapping.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppealClaimUiState(
    val isSubmitting: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
)

class AppealClaimViewModel : ViewModel() {

    private val firebaseHelper = FirebaseHelper()

    private val _uiState = MutableStateFlow(AppealClaimUiState())
    val uiState: StateFlow<AppealClaimUiState> = _uiState.asStateFlow()

    fun submitAppeal(claimId: String, note: String) {
        if (note.isBlank()) {
            _uiState.update { it.copy(error = "Please provide a reason for your appeal") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }

        firebaseHelper.addClaimAppeal(claimId, note, {
            _uiState.update { it.copy(isSubmitting = false, success = true) }
        }, { exception ->
            _uiState.update { it.copy(isSubmitting = false, error = exception.message ?: "Failed to submit appeal") }
        })
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
