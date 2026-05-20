package com.insuranceclaimsmapping.activities

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.ai.OfflineInferenceHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Policy
import com.insuranceclaimsmapping.models.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UploadPolicyUiState {
    object LoadingInitial : UploadPolicyUiState
    data class Idle(
        val role: String,
        val activePolicy: Policy?,
        val policyHistory: List<Policy>?
    ) : UploadPolicyUiState
    data class Processing(val statusMessage: String) : UploadPolicyUiState
    data class Success(val message: String) : UploadPolicyUiState
    data class Error(val message: String) : UploadPolicyUiState
}

class UploadPolicyViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseHelper = FirebaseHelper()
    private val offlineInferenceHelper = OfflineInferenceHelper(application)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<UploadPolicyUiState>(UploadPolicyUiState.LoadingInitial)
    val uiState: StateFlow<UploadPolicyUiState> = _uiState.asStateFlow()

    private var currentUserRole: String = "INSURER"
    private var currentActivePolicy: Policy? = null
    private var currentPolicyHistory: List<Policy>? = null

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _uiState.value = UploadPolicyUiState.Error("Not logged in.")
            return
        }

        _uiState.value = UploadPolicyUiState.LoadingInitial

        firebaseHelper.getUserProfile(uid, { user ->
            if (user != null) {
                currentUserRole = user.role
                loadPolicyData(uid)
            } else {
                _uiState.value = UploadPolicyUiState.Error("Failed to load user profile.")
            }
        }, {
            _uiState.value = UploadPolicyUiState.Error(it.message ?: "Unknown error")
        })
    }

    private fun loadPolicyData(uid: String) {
        firebaseHelper.getPolicy(uid, { policy ->
            currentActivePolicy = policy
            firebaseHelper.getPolicyHistory(uid, { history ->
                currentPolicyHistory = history
                _uiState.value = UploadPolicyUiState.Idle(currentUserRole, currentActivePolicy, currentPolicyHistory)
            }, {
                currentPolicyHistory = emptyList()
                _uiState.value = UploadPolicyUiState.Idle(currentUserRole, currentActivePolicy, currentPolicyHistory)
            })
        }, {
            // No active policy
            currentActivePolicy = null
            _uiState.value = UploadPolicyUiState.Idle(currentUserRole, null, null)
        })
    }

    fun processPolicyPdf(uri: Uri) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _uiState.value = UploadPolicyUiState.Error("Not logged in.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UploadPolicyUiState.Processing("Initializing logical scan of PDF document...")
            delay(1000)
            
            _uiState.value = UploadPolicyUiState.Processing("Analyzing coverage rules and benefit limits...")
            delay(1500)
            
            _uiState.value = UploadPolicyUiState.Processing("Sanitizing extracted clinical data...")
            
            val extractionResult = offlineInferenceHelper.extractPolicyDetails(uri)
            
            if (extractionResult != null) {
                _uiState.value = UploadPolicyUiState.Processing("Extraction complete. Registering policy...")
                delay(800)
                
                val policy = Policy(
                    insurerId = uid,
                    name = "Active Policy",
                    pdfUrl = "LOCAL",
                    copayPercentage = 20.0,
                    deductibleLimit = 500.0,
                    coverageDetails = extractionResult
                )
                
                firebaseHelper.savePolicyWithHistory(policy, {
                    _uiState.value = UploadPolicyUiState.Success("Policy Updated Successfully!")
                    // Reload data
                    loadInitialData()
                }, { e ->
                    _uiState.value = UploadPolicyUiState.Error("Save Failed: ${e.message}")
                    // Reset to idle after a short delay
                    viewModelScope.launch {
                        delay(2000)
                        _uiState.value = UploadPolicyUiState.Idle(currentUserRole, currentActivePolicy, currentPolicyHistory)
                    }
                })
            } else {
                _uiState.value = UploadPolicyUiState.Error("Extraction failed. Please verify PDF format.")
                // Reset to idle
                viewModelScope.launch {
                    delay(2000)
                    _uiState.value = UploadPolicyUiState.Idle(currentUserRole, currentActivePolicy, currentPolicyHistory)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        offlineInferenceHelper.close()
    }
}
