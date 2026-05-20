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
import com.insuranceclaimsmapping.ui.theme.InsuMapTheme

class ClaimHistoryActivity : ComponentActivity() {
    private val viewModel: ClaimHistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InsuMapTheme(role = viewModel.userRole) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClaimHistoryScreen(
                        viewModel = viewModel,
                        onNavigateBack = { finish() },
                        onClaimClick = { claimId ->
                            val intent = Intent(this, ClaimDetailActivity::class.java)
                            intent.putExtra("CLAIM_ID", claimId)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
