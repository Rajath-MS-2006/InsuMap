package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class AddClaimActivity : ComponentActivity() {

    private val viewModel: AddClaimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AddClaimScreen(
                        viewModel = viewModel,
                        onNavigateBack = { finish() },
                        onSuccess = { claimId ->
                            // Original logic would navigate to ClaimDetailActivity
                            // but for simplicity here we just finish with success
                            finish() 
                        }
                    )
                }
            }
        }
    }
}
