package com.insuranceclaimsmapping.activities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SelectRoleUiState(
    val isLoading: Boolean = false,
    val selectedRole: String = "PATIENT",
    val error: String? = null,
    val isSuccess: Boolean = false,
    val successMessage: String? = null
)

class SelectRoleViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firebaseHelper = FirebaseHelper()
    private val prefManager = PrefManager(application)

    private val _uiState = MutableStateFlow(SelectRoleUiState())
    val uiState: StateFlow<SelectRoleUiState> = _uiState.asStateFlow()

    fun selectRole(role: String) {
        _uiState.update { it.copy(selectedRole = role) }
    }

    fun confirmRole(email: String) {
        val role = _uiState.value.selectedRole
        _uiState.update { it.copy(isLoading = true, error = null) }

        firebaseHelper.generateNextCustomId(role) { customId ->
            val currentUid = auth.currentUser?.uid
            if (currentUid == null) {
                _uiState.update { it.copy(isLoading = false, error = "Session expired. Please log in again.") }
                return@generateNextCustomId
            }
            val user = User(
                uid = currentUid,
                customId = customId,
                email = email,
                role = role
            )

            firebaseHelper.saveUserProfile(user, {
                prefManager.setLogin(true)
                prefManager.setEmail(email)
                prefManager.setRole(role)
                prefManager.setCustomId(customId)
                _uiState.update { it.copy(
                    isLoading = false, 
                    isSuccess = true, 
                    successMessage = "Profile Setup Complete. Your ID is $customId"
                )}
            }, { exception ->
                _uiState.update { it.copy(isLoading = false, error = "Failed to save profile: ${exception.message}") }
            })
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
