package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.insuranceclaimsmapping.models.BillItem
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.ui.components.AnimatedButton
import com.insuranceclaimsmapping.ui.components.GlassSurface
import com.insuranceclaimsmapping.ui.components.PremiumCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimDetailScreen(
    viewModel: ClaimDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState is ClaimDetailUiState.Success) {
            val state = uiState as ClaimDetailUiState.Success
            state.message?.let { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claim Details", fontWeight = FontWeight.Bold) },
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
                is ClaimDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF3B82F6))
                }
                is ClaimDetailUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
                is ClaimDetailUiState.Success -> {
                    val claim = state.claim
                    val role = state.role

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { ClaimHeader(claim) }
                        
                        item { StatusTracker(claim.status) }

                        val hasItemFraud = claim.items.any { it.fraudWarning }
                        if (claim.fraudWarning || hasItemFraud) {
                            item { FraudWarningCard(claim, hasItemFraud) }
                        }

                        if (claim.status == "ADJUDICATED") {
                            item { AdjudicationSummary(claim, role, viewModel) }
                        }

                        item { DocumentStatusCard(claim) }

                        if (claim.items.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Itemized Bill",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(claim.items) { item ->
                                BillItemRow(item)
                            }
                        }

                        if (role == "HOSPITAL" && claim.status == "PENDING") {
                            item { PatientLinkCard(viewModel) }
                        }

                        item {
                            ActionButtons(claim, role, state.isPredicting, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClaimHeader(claim: Claim) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = claim.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${claim.id.take(8).uppercase()}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3B82F6).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "₹${claim.amount}",
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(claim.hospital, color = Color.Gray, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(claim.description.ifEmpty { "No description provided." }, color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun StatusTracker(status: String) {
    val doneColor = Color(0xFF10B981)
    val activeColor = Color(0xFF3B82F6)
    val pendingColor = Color.Gray.copy(alpha = 0.3f)
    val rejectedColor = Color(0xFFEF4444)
    val appealColor = Color(0xFFF59E0B)

    var color2 = activeColor
    var color3 = pendingColor
    var text3 = "Adjudicated"

    when (status) {
        "PENDING" -> { color2 = activeColor; color3 = pendingColor; text3 = "Adjudicated" }
        "ADJUDICATED" -> { color2 = doneColor; color3 = doneColor; text3 = "Adjudicated ✅" }
        "REJECTED" -> { color2 = doneColor; color3 = rejectedColor; text3 = "Rejected ❌" }
        "APPEAL_PENDING" -> { color2 = doneColor; color3 = appealColor; text3 = "Appeal Pending ⏳" }
    }

    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusStep("Intake", doneColor)
            Box(modifier = Modifier.weight(1f).height(2.dp).background(color2))
            StatusStep("Processing", color2)
            Box(modifier = Modifier.weight(1f).height(2.dp).background(color3))
            StatusStep(text3, color3)
        }
    }
}

@Composable
fun StatusStep(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(16.dp).background(color, RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun FraudWarningCard(claim: Claim, hasItemFraud: Boolean) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Anomaly Detected", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                val reason = when {
                    claim.fraudReasoning.isNotEmpty() -> claim.fraudReasoning
                    hasItemFraud -> "One or more billed items flagged for extreme price anomalies."
                    else -> "AI Flagged this claim for manual review."
                }
                Text(reason, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AdjudicationSummary(claim: Claim, role: String, viewModel: ClaimDetailViewModel) {
    val context = LocalContext.current
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                if (role == "PATIENT") "FINAL PAYMENT SUMMARY" else "ADJUDICATION SUMMARY",
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Billed", color = Color.White.copy(alpha = 0.7f))
                Text("₹%.2f".format(claim.amount.toDoubleOrNull() ?: 0.0), color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Covered by Insurance", color = Color.White.copy(alpha = 0.7f))
                Text("₹%.2f".format(claim.coveredAmount), color = Color(0xFF10B981))
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (role == "PATIENT") "You need to pay" else "Patient Owes", color = Color.White, fontWeight = FontWeight.Bold)
                Text("₹%.2f".format(claim.patientLiability), color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    if (viewModel.exportClaimSummaryToPdf()) {
                        Toast.makeText(context, "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export PDF Summary")
            }
        }
    }
}

@Composable
fun DocumentStatusCard(claim: Claim) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (claim.isBillLoaded) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (claim.isBillLoaded) Color(0xFF10B981) else Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Hospital Bill", color = Color.White, fontSize = 12.sp, modifier = Modifier.alpha(if (claim.isBillLoaded) 1f else 0.5f))
            }
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.1f)))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (claim.isPolicyLoaded) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (claim.isPolicyLoaded) Color(0xFF10B981) else Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Policy Rules", color = Color.White, fontSize = 12.sp, modifier = Modifier.alpha(if (claim.isPolicyLoaded) 1f else 0.5f))
            }
        }
    }
}

@Composable
fun BillItemRow(item: BillItem) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.description, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("₹${item.amount}", color = Color.White)
            }
            if (item.coveredAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Covered", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("₹${item.coveredAmount}", color = Color(0xFF10B981), fontSize = 12.sp)
                }
            }
            if (item.fraudWarning) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Price Anomaly Flagged", color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientLinkCard(viewModel: ClaimDetailViewModel) {
    var patientId by remember { mutableStateOf("") }
    
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Link to Patient", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = patientId,
                onValueChange = { patientId = it },
                label = { Text("Patient ID", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.linkClaimToPatient(patientId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify & Link")
            }
        }
    }
}

@Composable
fun ActionButtons(claim: Claim, role: String, isPredicting: Boolean, viewModel: ClaimDetailViewModel) {
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        if (claim.status == "PENDING" && (role == "INSURER" || role == "PATIENT")) {
            val btnText = if (role == "INSURER") "Calculate Adjudication" else "Predict Out-of-Pocket Cost"
            
            AnimatedButton(
                text = if (isPredicting) "Evaluating..." else btnText,
                onClick = { if (!isPredicting) viewModel.startFinancialPredictor() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPredicting
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (role == "PATIENT" && claim.status == "REJECTED") {
            AnimatedButton(
                text = "File an Appeal",
                onClick = {
                    val intent = Intent(context, AppealClaimActivity::class.java).apply {
                        putExtra("claimId", claim.id)
                        putExtra("claimDesc", claim.description)
                        putExtra("claimAmount", claim.amount)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
