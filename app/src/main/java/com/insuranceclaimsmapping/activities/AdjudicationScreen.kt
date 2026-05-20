package com.insuranceclaimsmapping.activities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.ui.components.AnimatedButton
import com.insuranceclaimsmapping.ui.components.PremiumCard
import com.insuranceclaimsmapping.ui.theme.InsuMapTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjudicationScreen(
    viewModel: AdjudicationViewModel,
    claims: List<Claim>,
    policyRules: String,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.startAdjudication(claims, policyRules)
    }

    // Auto-scroll to bottom of logs
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.logs.size - 1)
            }
        }
    }

    val primaryColor = when (uiState.userRole) {
        "HOSPITAL" -> Color(0xFF10B981) // hospital_primary
        "INSURER" -> Color(0xFF3B82F6)  // insurer_primary
        "PATIENT" -> Color(0xFFF59E0B)  // patient_primary
        else -> Color(0xFF3B82F6)
    }

    val bgColor = when (uiState.userRole) {
        "HOSPITAL" -> Color(0xFFECFDF5) // green_light
        "INSURER" -> Color(0xFFEFF6FF)  // blue_light
        "PATIENT" -> Color(0xFFFFFBEB)  // yellow_light
        else -> Color(0xFFF3F4F6)       // gray
    }

    InsuMapTheme(darkTheme = false) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI Adjudication Engine", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryColor)
                )
            },
            containerColor = bgColor
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Card
                PremiumCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.isFinished) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF10B981))
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = primaryColor,
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(
                                text = uiState.statusText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (uiState.maxProgress > 0) {
                            val progressFloat = uiState.progress.toFloat() / uiState.maxProgress.toFloat()
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = primaryColor,
                                trackColor = primaryColor.copy(alpha = 0.2f),
                            )
                            Text(
                                text = "${uiState.progress} of ${uiState.maxProgress} components processed",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Log Window
                PremiumCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.logs) { logMsg ->
                            val color = when {
                                logMsg.startsWith("[Process]") -> Color(0xFF60A5FA)
                                logMsg.startsWith("[Rules]") -> Color(0xFFFBBF24)
                                logMsg.startsWith("[Warning]") -> Color(0xFFF87171)
                                logMsg.startsWith("[Error]") -> Color(0xFFEF4444)
                                logMsg.startsWith("[System]") -> Color(0xFF34D399)
                                else -> Color(0xFFE2E8F0)
                            }
                            Text(
                                text = logMsg,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = color
                            )
                        }
                    }
                }

                // Done Button
                AnimatedVisibility(
                    visible = uiState.isFinished,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    AnimatedButton(
                        text = "Complete",
                        onClick = onDone,
                        containerColor = primaryColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
