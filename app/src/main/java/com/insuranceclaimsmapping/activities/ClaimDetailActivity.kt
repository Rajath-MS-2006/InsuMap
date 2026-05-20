package com.insuranceclaimsmapping.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.insuranceclaimsmapping.ui.theme.InsuMapTheme

class ClaimDetailActivity : ComponentActivity() {
    private val viewModel: ClaimDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val claimId = intent.getStringExtra("claimId")
        if (claimId != null) {
            viewModel.loadClaim(claimId)
        }
        
        setContent {
            InsuMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClaimDetailScreen(
                        viewModel = viewModel,
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}
