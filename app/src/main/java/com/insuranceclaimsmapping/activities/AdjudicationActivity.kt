package com.insuranceclaimsmapping.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.ui.theme.InsuMapTheme

class AdjudicationActivity : ComponentActivity() {
    
    private val viewModel: AdjudicationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val claims = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("claims", Claim::class.java) ?: arrayListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("claims") ?: arrayListOf()
        }
        val policyRules = intent.getStringExtra("policyRules") ?: "Use standard medical necessity rules."

        setContent {
            InsuMapTheme {
                AdjudicationScreen(
                    viewModel = viewModel,
                    claims = claims,
                    policyRules = policyRules,
                    onNavigateBack = { finish() },
                    onDone = { finish() }
                )
            }
        }
    }
}
