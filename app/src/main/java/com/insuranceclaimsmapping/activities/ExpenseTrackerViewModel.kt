package com.insuranceclaimsmapping.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExpenseSummary(
    val totalClaims: Int = 0,
    val totalBilled: Double = 0.0,
    val outOfPocket: Double = 0.0
)

data class TrendData(
    val label: String,
    val value: Double
)

data class ExpenseTrackerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val summary: ExpenseSummary = ExpenseSummary(),
    val trends: List<TrendData> = emptyList(),
    val hospitalSpend: List<TrendData> = emptyList()
)

class ExpenseTrackerViewModel : ViewModel() {

    private val firebaseHelper = FirebaseHelper()
    
    private val _uiState = MutableStateFlow(ExpenseTrackerUiState())
    val uiState: StateFlow<ExpenseTrackerUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        firebaseHelper.getClaimsByRole("PATIENT", uid, { claims ->
            viewModelScope.launch {
                val summary = calculateSummary(claims)
                val trends = calculateTrends(claims)
                val hospitalSpend = calculateHospitalSpend(claims)
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        summary = summary,
                        trends = trends,
                        hospitalSpend = hospitalSpend
                    ) 
                }
            }
        }, { exception ->
            _uiState.update { 
                it.copy(isLoading = false, error = exception.message ?: "Unknown error") 
            }
        })
    }

    private fun calculateSummary(claims: List<Claim>): ExpenseSummary {
        val totalBilled = claims.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
        val outOfPocket = claims.filter { it.status == "ADJUDICATED" }.sumOf { it.patientLiability }
        return ExpenseSummary(
            totalClaims = claims.size,
            totalBilled = totalBilled,
            outOfPocket = outOfPocket
        )
    }

    private fun calculateTrends(claims: List<Claim>): List<TrendData> {
        val sorted = claims.sortedBy { it.timestamp?.seconds ?: 0L }
        val monthFormat = SimpleDateFormat("MMM yy", Locale.getDefault())
        val monthMap = LinkedHashMap<String, Double>()
        
        sorted.forEach { claim ->
            val date = claim.timestamp?.toDate() ?: Date(0)
            val key = monthFormat.format(date)
            val amt = claim.amount.toDoubleOrNull() ?: 0.0
            monthMap[key] = (monthMap[key] ?: 0.0) + amt
        }
        
        return monthMap.map { TrendData(it.key, it.value) }
    }

    private fun calculateHospitalSpend(claims: List<Claim>): List<TrendData> {
        val hospitalMap = LinkedHashMap<String, Double>()
        claims.forEach { c ->
            val h = c.hospital.ifEmpty { "Unknown" }
            hospitalMap[h] = (hospitalMap[h] ?: 0.0) + (c.amount.toDoubleOrNull() ?: 0.0)
        }
        return hospitalMap.map { TrendData(it.key, it.value) }.sortedByDescending { it.value }
    }
}
