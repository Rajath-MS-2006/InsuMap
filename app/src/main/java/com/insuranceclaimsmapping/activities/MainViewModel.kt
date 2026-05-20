package com.insuranceclaimsmapping.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.models.Policy
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainViewModel(
    private val prefManager: PrefManager,
    private val firebaseHelper: FirebaseHelper = FirebaseHelper(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update { MainUiState.Error(throwable.message ?: "Unknown error") }
    }

    init {
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.value = MainUiState.Loading
            
            var role = prefManager.getRole() ?: "PATIENT"
            var customId = prefManager.getCustomId() ?: "N/A"

            if (customId == "N/A" || customId.isEmpty()) {
                val authUser = auth.currentUser
                if (authUser != null) {
                    val profile = fetchUserProfile(authUser.uid)
                    if (profile != null && profile.customId.isNotEmpty()) {
                        prefManager.setCustomId(profile.customId)
                        prefManager.setRole(profile.role)
                        customId = profile.customId
                        role = profile.role
                    } else {
                        _uiState.value = MainUiState.Error("ProfileNotFound")
                        return@launch
                    }
                }
            }

            if (role == "INSURER") {
                val claims = fetchClaims("INSURER", "")
                val total = claims.size
                val approved = claims.count { it.status == "APPROVED" }
                val rejected = claims.count { it.status == "REJECTED" }
                _uiState.value = MainUiState.Success(
                    role = role, 
                    customId = customId,
                    totalClaims = total,
                    approvedClaims = approved,
                    rejectedClaims = rejected,
                    claims = claims
                )
            } else {
                _uiState.value = MainUiState.Success(role = role, customId = customId)
            }
        }
    }

    private suspend fun fetchUserProfile(uid: String): User? = suspendCancellableCoroutine { continuation ->
        firebaseHelper.getUserProfile(uid, { profile ->
            if (continuation.isActive) continuation.resume(profile)
        }, { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        })
    }

    private suspend fun fetchClaims(role: String, userId: String): List<Claim> = suspendCancellableCoroutine { continuation ->
        firebaseHelper.getClaimsByRole(role, userId, { claims ->
            if (continuation.isActive) continuation.resume(claims)
        }, { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        })
    }

    suspend fun fetchLatestInvoiceForPatient(): String? {
        val userId = auth.currentUser?.uid ?: return null
        val claims = fetchClaims("PATIENT", userId)
        return if (claims.isNotEmpty()) claims[0].id else null
    }

    suspend fun fetchPendingClaimsForInsurer(): Pair<List<Claim>, String> {
        val insurerId = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val policy = suspendCancellableCoroutine<Policy?> { continuation ->
            firebaseHelper.getPolicy(insurerId, {
                if (continuation.isActive) continuation.resume(it)
            }, {
                if (continuation.isActive) continuation.resumeWithException(it)
            })
        }
        val policyRules = policy?.coverageDetails ?: "Standard Policy: 20% Copay applies to all items."
        val claims = fetchClaims("INSURER", "")
        return Pair(claims.filter { it.status == "PENDING" }, policyRules)
    }
}
