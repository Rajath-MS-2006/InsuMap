package com.insuranceclaimsmapping.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class ExpenseTrackerActivity : ComponentActivity() {

    private val viewModel: ExpenseTrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ExpenseTrackerScreen(
                viewModel = viewModel,
                onNavigateBack = { finish() }
            )
        }
    }
}
