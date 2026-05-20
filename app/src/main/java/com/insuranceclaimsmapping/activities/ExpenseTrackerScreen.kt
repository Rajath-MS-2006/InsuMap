package com.insuranceclaimsmapping.activities

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.insuranceclaimsmapping.ui.components.PremiumCard
import com.insuranceclaimsmapping.ui.theme.InsuMapTheme
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerScreen(
    viewModel: ExpenseTrackerViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    InsuMapTheme(darkTheme = false) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Expense Intelligence", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                )
            },
            containerColor = Color(0xFFF1F5F9)
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                }
            } else if (uiState.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${uiState.error}", color = Color.Red)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Summary Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SummaryCard(
                            title = "Total Billed",
                            amount = formatter.format(uiState.summary.totalBilled),
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCard(
                            title = "Out of Pocket",
                            amount = formatter.format(uiState.summary.outOfPocket),
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Monthly Trend Chart
                    PremiumCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Monthly Spend Trend",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.trends.isEmpty()) {
                                Text("No data available", color = Color.Gray)
                            } else {
                                LineChartCanvas(
                                    data = uiState.trends,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            }
                        }
                    }

                    // Hospital Breakdown
                    PremiumCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Spend by Hospital",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.hospitalSpend.isEmpty()) {
                                Text("No data available", color = Color.Gray)
                            } else {
                                BarChartCanvas(
                                    data = uiState.hospitalSpend,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, amount: String, color: Color, modifier: Modifier = Modifier) {
    PremiumCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun LineChartCanvas(data: List<TrendData>, modifier: Modifier = Modifier) {
    val maxVal = data.maxOfOrNull { it.value }?.toFloat()?.coerceAtLeast(1f) ?: 1f
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 16.dp.toPx()
        
        val graphWidth = width - padding * 2
        val graphHeight = height - padding * 2
        
        val stepX = if (data.size > 1) graphWidth / (data.size - 1) else graphWidth

        val path = Path()
        
        data.forEachIndexed { index, trend ->
            val x = padding + index * stepX
            val y = height - padding - (trend.value.toFloat() / maxVal * graphHeight)
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            
            // Draw points
            drawCircle(
                color = Color(0xFF3B82F6),
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }
        
        // Draw line
        drawPath(
            path = path,
            color = Color(0xFF3B82F6),
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Fill area
        val fillPath = Path().apply {
            addPath(path)
            lineTo(padding + (data.size - 1) * stepX, height - padding)
            lineTo(padding, height - padding)
            close()
        }
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.3f), Color.Transparent),
                startY = 0f,
                endY = height - padding
            )
        )
    }
}

@Composable
fun BarChartCanvas(data: List<TrendData>, modifier: Modifier = Modifier) {
    val maxVal = data.maxOfOrNull { it.value }?.toFloat()?.coerceAtLeast(1f) ?: 1f
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 16.dp.toPx()
        
        val graphWidth = width - padding * 2
        val graphHeight = height - padding * 2
        
        val barWidth = (graphWidth / data.size) * 0.6f
        val stepX = graphWidth / data.size
        
        val colors = listOf(
            Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), 
            Color(0xFF8B5CF6), Color(0xFFEC4899)
        )
        
        data.forEachIndexed { index, trend ->
            val barHeight = (trend.value.toFloat() / maxVal) * graphHeight
            val x = padding + index * stepX + (stepX - barWidth) / 2
            val y = height - padding - barHeight
            
            drawRoundRect(
                color = colors[index % colors.size],
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}
