package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.insuranceclaimsmapping.ui.theme.InsuMapTheme

class FraudDashboardActivity : ComponentActivity() {

    private val viewModel: FraudDashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            InsuMapTheme {
                FraudDashboardScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() },
                    onClaimClick = { claimId ->
                        startActivity(Intent(this, ClaimDetailActivity::class.java).apply {
                            putExtra("claimId", claimId)
                        })
                    }
                )
            }
        }
    }
}
