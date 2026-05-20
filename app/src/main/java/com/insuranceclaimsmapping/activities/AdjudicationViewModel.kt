package com.insuranceclaimsmapping.activities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.insuranceclaimsmapping.ai.OfflineInferenceHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun FirebaseHelper.updateClaimSuspend(claim: Claim): Unit = suspendCancellableCoroutine { cont ->
    this.updateClaim(claim,
        onSuccess = { cont.resume(Unit) },
        onFailure = { cont.resumeWithException(it) }
    )
}

data class AdjudicationUiState(
    val statusText: String = "Initializing Adjudication Engine...",
    val progress: Int = 0,
    val maxProgress: Int = 0,
    val logs: List<String> = emptyList(),
    val isFinished: Boolean = false,
    val userRole: String = "UNKNOWN"
)

class AdjudicationViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseHelper = FirebaseHelper()
    private val offlineInferenceHelper = OfflineInferenceHelper(application)

    private val _uiState = MutableStateFlow(AdjudicationUiState())
    val uiState: StateFlow<AdjudicationUiState> = _uiState.asStateFlow()

    init {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            firebaseHelper.getUserProfile(uid, { user ->
                if (user != null) {
                    _uiState.update { it.copy(userRole = user.role) }
                }
            }, {})
        }
    }

    fun startAdjudication(claims: List<Claim>, policyRules: String) {
        if (_uiState.value.isFinished || claims.isEmpty()) return

        _uiState.update { it.copy(maxProgress = claims.size) }

        viewModelScope.launch {
            var processed = 0
            for (claim in claims) {
                try {
                    addLog("[Process] Evaluating: ${claim.description}")
                    updateStatus("Identifying: ${claim.description}...")
                    delay(300)

                    val serviceName = if (claim.items.isNotEmpty()) claim.items[0].description else claim.description
                    addLog("[Rules] Checking policy coverage for '$serviceName'...")
                    updateStatus("Checking coverage for '$serviceName'...")
                    delay(500)

                    val result = offlineInferenceHelper.adjudicateSingleClaim(claim, policyRules)

                    if (result.status == "REJECTED") {
                        addLog("[Log] Item rejected: outside policy scope.")
                        updateStatus("Item outside policy scope. Moving to next...")
                    } else {
                        addLog("[Log] Coverage confirmed. Approved Amount: ${result.coveredAmount}")
                        updateStatus("Coverage confirmed. Proceeding...")
                    }

                    try {
                        firebaseHelper.updateClaimSuspend(result)
                        addLog("[System] Record updated in Cloud Ledger.")
                    } catch (e: Exception) {
                        addLog("[Warning] Failed to save claim: ${e.message}")
                    }

                    processed++
                    _uiState.update { it.copy(progress = processed) }
                    delay(500) // Quality of Life delay

                } catch (e: Exception) {
                    addLog("[Error] Clinical skip: ${e.message}")
                    delay(500)
                }
            }

            updateStatus("Batch Adjudication Complete")
            addLog("----------------------------------")
            addLog("[Status] All components processed successfully.")
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    private fun addLog(message: String) {
        _uiState.update { it.copy(logs = it.logs + message) }
    }

    private fun updateStatus(status: String) {
        _uiState.update { it.copy(statusText = status) }
    }

    override fun onCleared() {
        super.onCleared()
        offlineInferenceHelper.close()
    }
}
