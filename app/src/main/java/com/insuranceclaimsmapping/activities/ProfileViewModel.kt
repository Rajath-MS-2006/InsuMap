package com.insuranceclaimsmapping.activities

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    object Loading : ProfileUiState
    data class Success(val user: User, val notificationsEnabled: Boolean, val darkModeEnabled: Boolean) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseHelper = FirebaseHelper()
    private val prefManager = PrefManager(application)
    
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    val userRole: String
        get() = prefManager.getRole() ?: "PATIENT"

    init {
        loadProfile()
    }

    fun loadProfile() {
        _uiState.value = ProfileUiState.Loading
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val notifications = prefManager.getNotificationsEnabled()
        val darkMode = prefManager.getDarkModeEnabled()

        if (uid != null) {
            firebaseHelper.getUserProfile(uid, { user ->
                if (user != null) {
                    _uiState.value = ProfileUiState.Success(user, notifications, darkMode)
                } else {
                    _uiState.value = ProfileUiState.Error("User data not found")
                }
            }, { e ->
                _uiState.value = ProfileUiState.Error(e.message ?: "Failed to load profile")
            })
        } else {
            _uiState.value = ProfileUiState.Error("User not logged in")
        }
    }

    fun updateSettings(notificationsEnabled: Boolean, darkModeEnabled: Boolean) {
        prefManager.setNotificationsEnabled(notificationsEnabled)
        prefManager.setDarkModeEnabled(darkModeEnabled)
        
        val currentState = _uiState.value
        if (currentState is ProfileUiState.Success) {
            _uiState.value = currentState.copy(
                notificationsEnabled = notificationsEnabled,
                darkModeEnabled = darkModeEnabled
            )
        }
    }

    fun updateProfile(newName: String, newPhone: String) {
        val currentState = _uiState.value
        if (currentState is ProfileUiState.Success) {
            val updatedUser = currentState.user.copy(displayName = newName, phoneNumber = newPhone)
            firebaseHelper.updateUserProfile(updatedUser, {
                _uiState.value = currentState.copy(user = updatedUser)
                _toastMessage.value = "Profile updated"
            }, { e ->
                _toastMessage.value = "Update failed: ${e.message}"
            })
        }
    }

    fun updateInsuranceProvider(providerId: String) {
        val currentState = _uiState.value
        if (currentState is ProfileUiState.Success) {
            val updatedUser = currentState.user.copy(insuranceProviderId = providerId)
            firebaseHelper.updateUserProfile(updatedUser, {
                _uiState.value = currentState.copy(user = updatedUser)
                _toastMessage.value = "Insurance Provider Saved"
            }, { e ->
                _toastMessage.value = "Update failed: ${e.message}"
            })
        }
    }

    fun uploadProfilePicture(uri: Uri) {
        val currentState = _uiState.value
        if (currentState is ProfileUiState.Success) {
            _toastMessage.value = "Uploading..."
            val user = currentState.user
            firebaseHelper.uploadProfilePicture(uri, user.uid, { downloadUrl ->
                // Atomically write only the profilePictureUrl field — never overwrites other fields
                firebaseHelper.updateProfilePictureUrl(user.uid, downloadUrl, {
                    val updatedUser = user.copy(profilePictureUrl = downloadUrl)
                    _uiState.value = currentState.copy(user = updatedUser)
                    _toastMessage.value = "Profile picture updated"
                }, { e ->
                    _toastMessage.value = "Failed to save picture URL: ${e.message}"
                })
            }, { e ->
                _toastMessage.value = "Upload failed: ${e.message}"
            })
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun logout() {
        firebaseHelper.logout()
        prefManager.logout()
    }
}
