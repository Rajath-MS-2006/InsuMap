package com.insuranceclaimsmapping.activities

import com.insuranceclaimsmapping.models.Claim

sealed interface MainUiState {
    object Loading : MainUiState
    data class Success(
        val role: String,
        val customId: String,
        val totalClaims: Int = 0,
        val approvedClaims: Int = 0,
        val rejectedClaims: Int = 0,
        val claims: List<Claim> = emptyList()
    ) : MainUiState
    data class Error(val message: String) : MainUiState
}
