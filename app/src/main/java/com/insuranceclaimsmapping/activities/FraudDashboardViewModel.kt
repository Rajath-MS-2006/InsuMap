package com.insuranceclaimsmapping.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FraudDashboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val flaggedClaims: List<Claim> = emptyList()
)

class FraudDashboardViewModel : ViewModel() {

    private val firebaseHelper = FirebaseHelper()
    
    private val _uiState = MutableStateFlow(FraudDashboardUiState())
    val uiState: StateFlow<FraudDashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        firebaseHelper.getFlaggedClaims({ claims ->
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    flaggedClaims = claims
                ) 
            }
        }, { exception ->
            _uiState.update { 
                it.copy(isLoading = false, error = exception.message ?: "Unknown error") 
            }
        })
    }
}
