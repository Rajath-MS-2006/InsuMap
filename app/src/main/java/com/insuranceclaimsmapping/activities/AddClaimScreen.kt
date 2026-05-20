package com.insuranceclaimsmapping.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.models.Claim
import kotlinx.coroutines.launch
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@Composable
fun AddClaimScreen(
    viewModel: AddClaimViewModel,
    onNavigateBack: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val duplicateDialogState by viewModel.duplicateDialogState.collectAsState()
    val context = LocalContext.current
    val role = viewModel.userRole

    val themeColor = when (role) {
        "HOSPITAL" -> Color(0xFF10B981)
        "INSURER" -> Color(0xFF3B82F6)
        "PATIENT" -> Color(0xFFF59E0B)
        else -> Color(0xFF64748B)
    }

    // Launchers
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.processPdf(it) }
    }
    
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                viewModel.processImage(uri)
            }
        }
    }
    
    val openScanner = {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
            
        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(context as Activity).addOnSuccessListener { intentSender ->
            scannerLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
        }.addOnFailureListener {
            Toast.makeText(context, "Scanner Failed to open", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617))))
        )

        when (val state = uiState) {
            is AddClaimUiState.Idle -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Submit New Claim", color = themeColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { openScanner() },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Scan Medical Bill (AI)", color = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { pdfLauncher.launch("application/pdf") },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Upload PDF Bill", color = themeColor)
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    TextButton(onClick = { /* manual entry could be triggered here */ }) {
                        Text("Enter Manually", color = Color.Gray)
                    }
                }
            }
            
            is AddClaimUiState.Processing -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = themeColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(state.message, color = Color.White)
                }
            }
            
            is AddClaimUiState.Reviewing -> {
                ReviewForm(
                    state = state, 
                    role = role,
                    themeColor = themeColor,
                    onSubmit = { claim -> viewModel.submitClaim(claim) },
                    onCancel = { onNavigateBack() }
                )
            }
            
            is AddClaimUiState.Success -> {
                LaunchedEffect(Unit) { onSuccess("CLAIM_SUBMITTED") }
            }
            
            is AddClaimUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: ${state.message}", color = Color.Red, modifier = Modifier.padding(16.dp))
                    Button(onClick = { onNavigateBack() }) {
                        Text("Go Back")
                    }
                }
            }
        }

        // Duplicate Dialog
        if (duplicateDialogState != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDuplicateDialog() },
                title = { Text("Possible Duplicate") },
                text = { Text("A claim with the same patient, hospital, and amount already exists. Submit anyway?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDuplicateSubmission() }) {
                        Text("Submit Anyway", color = themeColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDuplicateDialog() }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun ReviewForm(
    state: AddClaimUiState.Reviewing,
    role: String,
    themeColor: Color,
    onSubmit: (Claim) -> Unit,
    onCancel: () -> Unit
) {
    var patientName by remember { mutableStateOf(state.patientName) }
    var hospitalName by remember { mutableStateOf(state.hospitalName) }
    var amount by remember { mutableStateOf(state.amount) }
    var description by remember { mutableStateOf(state.description) }
    var patientId by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                description += " " + results[0]
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("Review Claim", color = themeColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = patientName,
            onValueChange = { patientName = it },
            label = { Text("Patient Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        if (role == "HOSPITAL") {
            OutlinedTextField(
                value = patientId,
                onValueChange = { patientId = it },
                label = { Text("Patient Custom ID") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = hospitalName,
            onValueChange = { hospitalName = it },
            label = { Text("Hospital") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Total Amount") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    }
                    speechLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Speech not supported", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("🎤")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                val claim = Claim(
                    name = patientName,
                    hospital = hospitalName,
                    amount = amount,
                    description = description,
                    userId = if (role == "PATIENT") "" else (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
                    patientId = patientId, // Simplified for brevity in this UI component
                    billUrl = state.uri?.toString() ?: "",
                    items = state.items,
                    isBillLoaded = true,
                    isPolicyLoaded = true,
                    timestamp = Timestamp.now()
                )
                onSubmit(claim)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColor)
        ) {
            Text("Submit Claim", color = Color.White)
        }
    }
}
