package com.insuranceclaimsmapping.activities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.ui.components.PremiumCard
import com.insuranceclaimsmapping.ui.theme.getRoleColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimHistoryScreen(
    viewModel: ClaimHistoryViewModel,
    onNavigateBack: () -> Unit,
    onClaimClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val roleColor = getRoleColor(viewModel.userRole)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var expandedSortMenu by remember { mutableStateOf(false) }
    var sortIndex by remember { mutableStateOf(0) }
    val sortOptions = listOf("Newest First", "Oldest First", "Amount (High to Low)", "Amount (Low to High)", "Hospital A-Z")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claim History", fontWeight = FontWeight.Bold) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617))))
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        viewModel.updateSearchQuery(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by ID, Patient, or Hospital") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = roleColor,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Filters and Sort
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL", "PENDING", "APPROVED", "REJECTED").forEach { status ->
                            FilterChip(
                                selected = selectedFilter == status,
                                onClick = {
                                    selectedFilter = status
                                    viewModel.updateStatusFilter(status)
                                },
                                label = { Text(status, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = roleColor,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    
                    Box {
                        TextButton(onClick = { expandedSortMenu = true }) {
                            Text("Sort", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = expandedSortMenu,
                            onDismissRequest = { expandedSortMenu = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            sortOptions.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = Color.White) },
                                    onClick = {
                                        sortIndex = index
                                        viewModel.updateSortIndex(index)
                                        expandedSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                when (val state = uiState) {
                    is ClaimHistoryUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = roleColor)
                        }
                    }
                    is ClaimHistoryUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.message, color = Color.Red)
                        }
                    }
                    is ClaimHistoryUiState.Success -> {
                        if (state.claims.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No claims found.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(items = state.claims, key = { it.id }) { claim ->
                                    val dismissState = rememberSwipeToDismissBoxState()
                                    
                                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                        LaunchedEffect(Unit) {
                                            viewModel.archiveClaim(claim.id)
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Claim archived",
                                                actionLabel = "UNDO",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.unarchiveClaim(claim.id)
                                                dismissState.reset()
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = !archivedIdsContains(viewModel, claim.id),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        SwipeToDismissBox(
                                            state = dismissState,
                                            backgroundContent = {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Red, RoundedCornerShape(16.dp))
                                                        .padding(horizontal = 20.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Archive", tint = Color.White)
                                                }
                                            },
                                            content = {
                                                ClaimItemCard(claim = claim, roleColor = roleColor, onClick = { onClaimClick(claim.id) })
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun archivedIdsContains(viewModel: ClaimHistoryViewModel, claimId: String): Boolean {
    // A quick hack for the animated visibility exit transition to trigger 
    // before the item is fully removed from the viewmodel list
    return false 
}

@Composable
fun ClaimItemCard(claim: Claim, roleColor: Color, onClick: () -> Unit) {
    PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(claim.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                val statusColor = when (claim.status.uppercase()) {
                    "APPROVED" -> Color(0xFF10B981)
                    "REJECTED" -> Color(0xFFEF4444)
                    else -> Color(0xFFF59E0B)
                }
                Text(claim.status.uppercase(), color = statusColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(claim.hospital, color = Color.LightGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("₹${claim.amount}", color = roleColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val date = claim.timestamp?.toDate()
                if (date != null) {
                    Text(formatter.format(date), color = Color.Gray, fontSize = 12.sp)
                } else {
                    Text("Unknown Date", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
