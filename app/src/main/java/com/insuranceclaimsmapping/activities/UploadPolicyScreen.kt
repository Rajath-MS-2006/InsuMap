package com.insuranceclaimsmapping.activities

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.insuranceclaimsmapping.models.Policy
import com.insuranceclaimsmapping.ui.components.AnimatedButton
import com.insuranceclaimsmapping.ui.components.GlassSurface
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPolicyScreen(
    viewModel: UploadPolicyViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showHistoryDialog by remember { mutableStateOf(false) }
    var selectedPolicyDetails by remember { mutableStateOf<Pair<String, String>?>(null) }
    
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.processPolicyPdf(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Policy Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is UploadPolicyUiState.LoadingInitial -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                }
                is UploadPolicyUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadInitialData() }) {
                            Text("Retry")
                        }
                    }
                }
                is UploadPolicyUiState.Processing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3B82F6), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = state.statusMessage,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                is UploadPolicyUiState.Success -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Policy, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = state.message,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                is UploadPolicyUiState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    ) {
                        // Upload Section
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.UploadFile, 
                                    contentDescription = null, 
                                    tint = Color(0xFF3B82F6), 
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Upload New Policy",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Select a PDF document containing the master policy rules, coverage limits, and copay requirements.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                AnimatedButton(
                                    text = "Select PDF File",
                                    onClick = { pdfLauncher.launch("application/pdf") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Active Policy Section
                        if (state.activePolicy != null) {
                            Text(
                                text = "Current Active Policy",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedPolicyDetails = Pair("Version ${state.activePolicy.version}", state.activePolicy.coverageDetails)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(40.dp).background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Policy, contentDescription = null, tint = Color(0xFF10B981))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Active Policy (v${state.activePolicy.version})", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Tap to view coverage rules", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // History Section
                        if (!state.policyHistory.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Policy History",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { showHistoryDialog = true }) {
                                    Text("View All", color = Color(0xFF3B82F6))
                                }
                            }
                            
                            val latestHistory = state.policyHistory.take(2)
                            latestHistory.forEach { policy ->
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable {
                                        selectedPolicyDetails = Pair("Version ${policy.version}", policy.coverageDetails)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text("Version ${policy.version}", color = Color.White, fontWeight = FontWeight.Bold)
                                            val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                            Text("Uploaded: ${fmt.format(Date(policy.uploadedAt))}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Dialog for Policy Details
        selectedPolicyDetails?.let { (title, details) ->
            AlertDialog(
                onDismissRequest = { selectedPolicyDetails = null },
                title = { Text(title, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn {
                        item {
                            Text(details, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedPolicyDetails = null }) {
                        Text("Close")
                    }
                }
            )
        }
        
        // Dialog for Policy History List
        if (showHistoryDialog) {
            val idleState = uiState as? UploadPolicyUiState.Idle
            if (idleState?.policyHistory != null) {
                AlertDialog(
                    onDismissRequest = { showHistoryDialog = false },
                    title = { Text("Version History", fontWeight = FontWeight.Bold) },
                    text = {
                        LazyColumn {
                            items(idleState.policyHistory) { policy ->
                                val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        showHistoryDialog = false
                                        selectedPolicyDetails = Pair("Version ${policy.version}", policy.coverageDetails)
                                    }.padding(vertical = 12.dp)
                                ) {
                                    Column {
                                        Text("Version ${policy.version}", fontWeight = FontWeight.Bold)
                                        Text(fmt.format(Date(policy.uploadedAt)), fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                                Divider()
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showHistoryDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}
