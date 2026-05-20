package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val prefManager by lazy { PrefManager(this) }
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(prefManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show onboarding for first-time users
        if (!prefManager.isOnboardingShown()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            
            MainScreen(
                uiState = uiState,
                onNavigateAction = { actionId, role ->
                    handleNavigation(actionId, role)
                }
            )
        }
    }

    private fun handleNavigation(actionId: Int, role: String) {
        when (actionId) {
            0 -> {
                if (role == "PATIENT") {
                    lifecycleScope.launch {
                        try {
                            val claimId = viewModel.fetchLatestInvoiceForPatient()
                            if (claimId != null) {
                                startActivity(Intent(this@MainActivity, ClaimDetailActivity::class.java).apply {
                                    putExtra("claimId", claimId)
                                })
                            } else {
                                Toast.makeText(this@MainActivity, "No active invoices found.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Error fetching invoice: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    startActivity(Intent(this, AddClaimActivity::class.java).apply { putExtra("isPredictionOnly", true) })
                }
            }
            1 -> startActivity(Intent(this, AddClaimActivity::class.java))
            2 -> startActivity(Intent(this, UploadPolicyActivity::class.java))
            3 -> {
                lifecycleScope.launch {
                    try {
                        val (pendingClaims, rules) = viewModel.fetchPendingClaimsForInsurer()
                        if (pendingClaims.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No pending claims to process", Toast.LENGTH_SHORT).show()
                        } else {
                            startActivity(Intent(this@MainActivity, AdjudicationActivity::class.java).apply {
                                putParcelableArrayListExtra("claims", ArrayList(pendingClaims))
                                putExtra("policyRules", rules)
                            })
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error fetching claims: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            4 -> startActivity(Intent(this, ClaimHistoryActivity::class.java))
            5 -> startActivity(Intent(this, ProfileActivity::class.java))
            6 -> startActivity(Intent(this, ExpenseTrackerActivity::class.java))
            7 -> startActivity(Intent(this, FraudDashboardActivity::class.java))
            8 -> startActivity(Intent(this, InsuranceCardActivity::class.java))
        }
    }
}
