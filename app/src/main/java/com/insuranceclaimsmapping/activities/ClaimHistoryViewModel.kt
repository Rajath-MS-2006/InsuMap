package com.insuranceclaimsmapping.activities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ClaimHistoryUiState {
    object Loading : ClaimHistoryUiState
    data class Success(val claims: List<Claim>) : ClaimHistoryUiState
    data class Error(val message: String) : ClaimHistoryUiState
}

class ClaimHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseHelper = FirebaseHelper()
    private val prefManager = PrefManager(application)
    
    private val _uiState = MutableStateFlow<ClaimHistoryUiState>(ClaimHistoryUiState.Loading)
    val uiState: StateFlow<ClaimHistoryUiState> = _uiState.asStateFlow()

    private var allClaimsList = listOf<Claim>()
    private var archivedIds = mutableSetOf<String>()

    private var currentStatusFilter = "ALL"
    private var currentSortIndex = 0
    private var currentSearchQuery = ""

    val userRole: String
        get() = prefManager.getRole() ?: "PATIENT"

    init {
        fetchClaims()
    }

    fun fetchClaims() {
        _uiState.value = ClaimHistoryUiState.Loading
        val role = userRole
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid.isNullOrEmpty()) {
            _uiState.value = ClaimHistoryUiState.Error("Session expired. Please log in again.")
            return
        }

        firebaseHelper.getClaimsByRole(role, uid, { claims ->
            allClaimsList = claims
            applyFilters()
        }, { error ->
            _uiState.value = ClaimHistoryUiState.Error(error.message ?: "Failed to fetch claims")
        })
    }

    fun updateSearchQuery(query: String) {
        currentSearchQuery = query
        applyFilters()
    }

    fun updateStatusFilter(status: String) {
        currentStatusFilter = status
        applyFilters()
    }

    fun updateSortIndex(index: Int) {
        currentSortIndex = index
        applyFilters()
    }

    fun archiveClaim(claimId: String) {
        archivedIds.add(claimId)
        applyFilters()
    }

    fun unarchiveClaim(claimId: String) {
        archivedIds.remove(claimId)
        applyFilters()
    }

    private fun applyFilters() {
        var filtered = allClaimsList.filter { it.id !in archivedIds }

        // Status filter
        if (currentStatusFilter != "ALL") {
            filtered = filtered.filter { it.status.equals(currentStatusFilter, ignoreCase = true) }
        }

        // Search filter
        if (currentSearchQuery.isNotEmpty()) {
            filtered = filtered.filter { claim ->
                claim.customPatientId.contains(currentSearchQuery, ignoreCase = true) ||
                claim.name.contains(currentSearchQuery, ignoreCase = true) ||
                claim.hospital.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        // Sort
        filtered = when (currentSortIndex) {
            0 -> filtered.sortedByDescending { it.timestamp }
            1 -> filtered.sortedBy { it.timestamp }
            2 -> filtered.sortedByDescending { it.amount.toDoubleOrNull() ?: 0.0 }
            3 -> filtered.sortedBy { it.amount.toDoubleOrNull() ?: 0.0 }
            4 -> filtered.sortedBy { it.hospital }
            else -> filtered
        }

        _uiState.value = ClaimHistoryUiState.Success(filtered)
    }
}
