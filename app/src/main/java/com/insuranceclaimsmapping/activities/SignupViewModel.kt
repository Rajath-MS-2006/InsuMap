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

data class SignupUiState(
    val isLoading: Boolean = false,
    val selectedRole: String = "PATIENT",
    val error: String? = null,
    val isSuccess: Boolean = false,
    val successMessage: String? = null
)

class SignupViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firebaseHelper = FirebaseHelper()
    private val prefManager = PrefManager(application)

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun selectRole(role: String) {
        _uiState.update { it.copy(selectedRole = role) }
    }

    fun signup(name: String, email: String, pass: String) {
        val role = _uiState.value.selectedRole
        
        if (email.isEmpty() || pass.isEmpty() || name.isEmpty()) {
            _uiState.update { it.copy(error = "All fields are required") }
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Invalid email format") }
            return
        }
        
        if (pass.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    firebaseHelper.generateNextCustomId(role) { customId ->
                        val currentUid = auth.currentUser?.uid
                        if (currentUid == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Session expired. Please try again.") }
                            return@generateNextCustomId
                        }
                        val user = User(
                            uid = currentUid,
                            customId = customId,
                            email = email,
                            role = role,
                            displayName = name
                        )
                        firebaseHelper.saveUserProfile(user, {
                            prefManager.setLogin(true)
                            prefManager.setEmail(email)
                            prefManager.setRole(role)
                            prefManager.setCustomId(customId)
                            _uiState.update { it.copy(
                                isLoading = false,
                                isSuccess = true,
                                successMessage = "Signup Successful. Your ID is $customId"
                            )}
                        }, { exception ->
                            _uiState.update { it.copy(isLoading = false, error = "Profile Creation Failed: ${exception.message}") }
                        })
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Signup Failed: ${task.exception?.localizedMessage}") }
                }
            }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
