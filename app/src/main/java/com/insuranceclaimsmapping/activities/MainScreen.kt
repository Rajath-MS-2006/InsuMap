package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.insuranceclaimsmapping.R
import androidx.compose.foundation.border
@Composable
fun MainScreen(
    uiState: MainUiState,
    onNavigateAction: (Int, String) -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Ambient Futuristic Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
        )

        when (uiState) {
            is MainUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            }
            is MainUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "System Failure: ${uiState.message}",
                        color = Color(0xFFEF4444),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            is MainUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                ) {
                    HeaderSection(uiState.role, uiState.customId)
                    
                    if (uiState.role == "INSURER") {
                        Spacer(modifier = Modifier.height(24.dp))
                        AnalyticsSection(uiState.totalClaims, uiState.approvedClaims, uiState.rejectedClaims)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "SYSTEM MODULES",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val items = getDashboardItems(uiState.role)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items) { item ->
                            DashboardCard(
                                title = item.title,
                                iconRes = item.iconRes,
                                onClick = { onNavigateAction(item.id, uiState.role) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(role: String, customId: String) {
    val roleColor = when (role) {
        "HOSPITAL" -> Color(0xFF10B981)
        "INSURER" -> Color(0xFF3B82F6)
        "PATIENT" -> Color(0xFFF59E0B)
        else -> Color(0xFF64748B)
    }

    Column {
        Text(
            text = role,
            color = roleColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "ID: $customId",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
fun AnalyticsSection(total: Int, approved: Int, rejected: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .glassmorphism()
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatItem("Total", total, Color.White)
        StatItem("Approved", approved, Color(0xFF10B981))
        StatItem("Rejected", rejected, Color(0xFFEF4444))
    }
}

@Composable
fun StatItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value.toString(), color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 12.sp)
    }
}

@Composable
fun DashboardCard(title: String, iconRes: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .glassmorphism()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Placeholder for icon, as we don't load drawables directly here for brevity
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Extension to apply adaptive glassmorphism safely without content-blurring side effects
fun Modifier.glassmorphism(): Modifier = this.then(
    Modifier
        .background(Color(0xFF1E293B).copy(alpha = 0.6f))
        .border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(24.dp)
        )
)

data class DashboardItemData(val id: Int, val title: String, val iconRes: Int)

fun getDashboardItems(role: String): List<DashboardItemData> {
    val items = mutableListOf<DashboardItemData>()
    when (role) {
        "PATIENT" -> {
            items.add(DashboardItemData(0, "Expected Invoice", R.drawable.ic_medical_claim))
            items.add(DashboardItemData(6, "Expense Tracker", R.drawable.ic_history))
            items.add(DashboardItemData(8, "Insurance Card", R.drawable.ic_profile))
        }
        "HOSPITAL" -> items.add(DashboardItemData(1, "Upload Bill", R.drawable.ic_add_claim))
        "INSURER" -> {
            items.add(DashboardItemData(2, "Upload Policy", R.drawable.ic_profile))
            items.add(DashboardItemData(3, "Adjudicate Bills", R.drawable.ic_history))
            items.add(DashboardItemData(7, "Fraud Dashboard", R.drawable.ic_search))
        }
    }
    items.add(DashboardItemData(4, "History", R.drawable.ic_history))
    items.add(DashboardItemData(5, "Profile", R.drawable.ic_profile))
    return items
}
