package com.insuranceclaimsmapping.activities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val needsRoleSelection: Boolean = false,
    val successEmail: String? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    val auth = FirebaseAuth.getInstance()
    val firebaseHelper = FirebaseHelper()
    val prefManager = PrefManager(application)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun loginWithEmail(email: String, pass: String) {
        if (email.isEmpty() || pass.isEmpty()) {
            _uiState.update { it.copy(error = "Fields cannot be empty") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Invalid email") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        
        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    checkUserProfile(uid, email)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Authentication error. Please try again.") }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Login Failed: ${task.exception?.localizedMessage}") }
            }
        }
    }

    fun handleFirebaseGoogleAuth(idToken: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user != null) {
                    checkUserProfile(user.uid, user.email ?: "")
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Authentication error. Please try again.") }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Google Sign-In Failed") }
            }
        }
    }
    
    fun onBiometricSuccess() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            checkUserProfile(uid, prefManager.getEmail() ?: "")
        } else {
            _uiState.update { it.copy(error = "Session expired. Please log in with password.") }
        }
    }

    private fun checkUserProfile(uid: String, email: String) {
        firebaseHelper.getUserProfile(uid, { user ->
            if (user != null) {
                prefManager.setLogin(true)
                prefManager.setEmail(email)
                prefManager.setRole(user.role)
                prefManager.setCustomId(user.customId)
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, needsRoleSelection = true, successEmail = email) }
            }
        }, { exception ->
            _uiState.update { it.copy(isLoading = false, error = "Failed to fetch profile: ${exception.message}") }
        })
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
