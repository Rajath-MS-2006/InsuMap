package com.insuranceclaimsmapping.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class AppealClaimActivity : ComponentActivity() {

    private val viewModel: AppealClaimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val claimId = intent.getStringExtra("claimId") ?: run { finish(); return }
        val claimDesc = intent.getStringExtra("claimDesc") ?: ""
        val claimAmount = intent.getStringExtra("claimAmount") ?: ""

        setContent {
            AppealClaimScreen(
                viewModel = viewModel,
                claimId = claimId,
                claimDesc = claimDesc,
                claimAmount = claimAmount,
                onNavigateBack = { finish() },
                onAppealSuccess = { finish() }
            )
        }
    }
}
