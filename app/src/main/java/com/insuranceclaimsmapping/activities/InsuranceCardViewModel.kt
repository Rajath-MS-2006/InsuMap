package com.insuranceclaimsmapping.activities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Policy
import com.insuranceclaimsmapping.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface InsuranceCardUiState {
    object Loading : InsuranceCardUiState
    data class Success(
        val patient: User,
        val providerName: String?,
        val policy: Policy?
    ) : InsuranceCardUiState
    data class Error(val message: String) : InsuranceCardUiState
}

class InsuranceCardViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseHelper = FirebaseHelper()

    private val _uiState = MutableStateFlow<InsuranceCardUiState>(InsuranceCardUiState.Loading)
    val uiState: StateFlow<InsuranceCardUiState> = _uiState.asStateFlow()

    init {
        loadCardData()
    }

    fun loadCardData() {
        _uiState.value = InsuranceCardUiState.Loading
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            _uiState.value = InsuranceCardUiState.Error("Not logged in")
            return
        }

        firebaseHelper.getUserProfile(uid, { user ->
            if (user != null) {
                val providerId = user.insuranceProviderId
                if (providerId.isNotEmpty()) {
                    firebaseHelper.getUserIdByCustomId(providerId, { insurerUid ->
                        if (insurerUid != null) {
                            var pName: String? = null
                            var pPolicy: Policy? = null

                            // Need to fetch both provider profile and policy
                            firebaseHelper.getUserProfile(insurerUid, { insurer ->
                                pName = insurer?.displayName
                                checkAndEmit(user, pName, pPolicy, true)
                            }, {
                                checkAndEmit(user, pName, pPolicy, true)
                            })

                            firebaseHelper.getPolicy(insurerUid, { policy ->
                                pPolicy = policy
                                checkAndEmit(user, pName, pPolicy, false)
                            }, {
                                checkAndEmit(user, pName, pPolicy, false)
                            })
                        } else {
                            _uiState.value = InsuranceCardUiState.Success(user, null, null)
                        }
                    }, { e ->
                        _uiState.value = InsuranceCardUiState.Success(user, null, null)
                    })
                } else {
                    _uiState.value = InsuranceCardUiState.Success(user, null, null)
                }
            } else {
                _uiState.value = InsuranceCardUiState.Error("Failed to load profile")
            }
        }, { e ->
            _uiState.value = InsuranceCardUiState.Error(e.message ?: "Unknown error")
        })
    }

    private var profileFetched = false
    private var policyFetched = false

    private fun checkAndEmit(user: User, providerName: String?, policy: Policy?, isProfileCb: Boolean) {
        if (isProfileCb) profileFetched = true else policyFetched = true
        if (profileFetched && policyFetched) {
            _uiState.value = InsuranceCardUiState.Success(user, providerName, policy)
        }
    }
}
