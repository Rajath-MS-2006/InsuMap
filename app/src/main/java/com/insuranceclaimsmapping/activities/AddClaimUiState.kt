package com.insuranceclaimsmapping.activities

import android.net.Uri
import com.insuranceclaimsmapping.models.BillItem
import com.insuranceclaimsmapping.models.User

sealed interface AddClaimUiState {
    object Idle : AddClaimUiState
    data class Processing(val message: String) : AddClaimUiState
    data class Reviewing(
        val uri: Uri?,
        val patientName: String,
        val hospitalName: String,
        val amount: String,
        val description: String,
        val items: List<BillItem>,
        val patientsList: List<User> = emptyList(),
        val selectedPatientId: String = ""
    ) : AddClaimUiState
    object Success : AddClaimUiState
    data class Error(val message: String) : AddClaimUiState
}
