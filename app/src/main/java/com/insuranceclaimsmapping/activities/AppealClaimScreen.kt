package com.insuranceclaimsmapping.activities

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.insuranceclaimsmapping.ui.components.AnimatedButton
import com.insuranceclaimsmapping.ui.components.PremiumCard
import com.insuranceclaimsmapping.ui.theme.InsuMapTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppealClaimScreen(
    viewModel: AppealClaimViewModel,
    claimId: String,
    claimDesc: String,
    claimAmount: String,
    onNavigateBack: () -> Unit,
    onAppealSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var appealNote by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            Toast.makeText(context, "Appeal submitted successfully. Your insurer will review it.", Toast.LENGTH_LONG).show()
            onAppealSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    InsuMapTheme(darkTheme = false) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Appeal Claim", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF59E0B)) // patient_primary
                )
            },
            containerColor = Color(0xFFFEF3C7) // patient light bg
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Info Card
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFFF59E0B))
                            Text("Rejected Claim Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                        
                        Text("Claim: $claimDesc", style = MaterialTheme.typography.bodyLarge)
                        Text("Amount: ₹$claimAmount", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("Status: REJECTED", style = MaterialTheme.typography.bodyLarge, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }

                // Input Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Why are you appealing this rejection?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = appealNote,
                        onValueChange = { appealNote = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = { Text("Provide detailed reasoning and any additional context here...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (uiState.isSubmitting) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFF59E0B))
                    }
                } else {
                    AnimatedButton(
                        text = "Submit Appeal",
                        onClick = { viewModel.submitAppeal(claimId, appealNote) },
                        containerColor = Color(0xFFF59E0B),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
