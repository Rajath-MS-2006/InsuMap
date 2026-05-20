package com.insuranceclaimsmapping.activities

import android.app.Application
import android.content.ContentValues
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.insuranceclaimsmapping.ai.OfflineInferenceHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.BillItem
import com.insuranceclaimsmapping.models.Claim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface ClaimDetailUiState {
    object Loading : ClaimDetailUiState
    data class Success(
        val role: String,
        val claim: Claim,
        val isPredicting: Boolean = false,
        val message: String? = null
    ) : ClaimDetailUiState
    data class Error(val message: String) : ClaimDetailUiState
}

class ClaimDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseHelper = FirebaseHelper()
    private val offlineInferenceHelper = OfflineInferenceHelper(application)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow<ClaimDetailUiState>(ClaimDetailUiState.Loading)
    val uiState: StateFlow<ClaimDetailUiState> = _uiState.asStateFlow()

    private var claimId: String? = null
    private var snapshotListener: ListenerRegistration? = null
    private var currentUserRole: String = "PATIENT"
    private var autoPredictStarted = false

    fun loadClaim(id: String) {
        if (claimId == id) return
        claimId = id
        _uiState.value = ClaimDetailUiState.Loading

        val uid = auth.currentUser?.uid
        if (uid == null) {
            _uiState.value = ClaimDetailUiState.Error("Not logged in.")
            return
        }

        firebaseHelper.getUserProfile(uid, { user ->
            if (user != null) {
                currentUserRole = user.role
                startListening(id)
            } else {
                _uiState.value = ClaimDetailUiState.Error("Failed to load user profile.")
            }
        }, { e ->
            _uiState.value = ClaimDetailUiState.Error(e.message ?: "Unknown error")
        })
    }

    private fun startListening(id: String) {
        snapshotListener?.remove()
        snapshotListener = firestore.collection("claims").document(id)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.value = ClaimDetailUiState.Error("Listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val claim = firebaseHelper.safeMapToClaim(snapshot)
                    if (claim != null) {
                        handleClaimUpdate(claim)
                    } else {
                        _uiState.value = ClaimDetailUiState.Error("Invalid claim data.")
                    }
                } else {
                    _uiState.value = ClaimDetailUiState.Error("Claim not found.")
                }
            }
    }

    private fun handleClaimUpdate(claim: Claim) {
        val currentState = _uiState.value
        val isPredicting = if (currentState is ClaimDetailUiState.Success) currentState.isPredicting else false
        
        _uiState.value = ClaimDetailUiState.Success(
            role = currentUserRole,
            claim = claim,
            isPredicting = isPredicting,
            message = null
        )

        // Auto predict for PATIENT if status is PENDING
        if (currentUserRole == "PATIENT" && claim.status == "PENDING" && !autoPredictStarted) {
            autoPredictStarted = true
            startFinancialPredictor()
        }
    }

    fun startFinancialPredictor() {
        val currentState = _uiState.value as? ClaimDetailUiState.Success ?: return
        val claim = currentState.claim

        _uiState.value = currentState.copy(isPredicting = true, message = "Running AI prediction...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUid = auth.currentUser?.uid ?: throw Exception("Not logged in.")

                var policyRules = "Standard Policy: 20% Copay applies to all items."
                try {
                    val policySnapshot = firestore.collection("policies").document(currentUid).get().await()
                    if (policySnapshot.exists()) {
                        val details = policySnapshot.getString("coverageDetails")
                        if (!details.isNullOrEmpty()) policyRules = details
                    }
                } catch (e: Exception) {
                    // Ignore, use fallback
                }

                val adjudicatedItems = offlineInferenceHelper.adjudicateItemized(claim.items, policyRules)

                if (adjudicatedItems.isNotEmpty()) {
                    val totalCovered = adjudicatedItems.sumOf { it.coveredAmount }
                    val totalBill = claim.amount.toDoubleOrNull() ?: 0.0
                    val liability = totalBill - totalCovered

                    firestore.collection("claims").document(claim.id)
                        .update(mapOf(
                            "status" to "ADJUDICATED",
                            "items" to adjudicatedItems,
                            "coveredAmount" to totalCovered,
                            "patientLiability" to liability,
                            "aiReasoning" to "Optimized via Explainable AI Predictor"
                        )).await()
                        
                    withContext(Dispatchers.Main) {
                        // The snapshot listener will automatically update the UI state
                        // Just need to clear the predicting flag
                        val latestState = _uiState.value as? ClaimDetailUiState.Success
                        if (latestState != null) {
                            _uiState.value = latestState.copy(isPredicting = false, message = "Success! Adjudication Complete")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.value = currentState.copy(isPredicting = false, message = "AI Evaluation returned empty results.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = currentState.copy(isPredicting = false, message = "Evaluation Error: ${e.message}")
                }
            }
        }
    }

    fun linkClaimToPatient(targetId: String) {
        if (targetId.isBlank()) {
            val currentState = _uiState.value as? ClaimDetailUiState.Success
            if (currentState != null) {
                _uiState.value = currentState.copy(message = "Please enter a Patient ID")
            }
            return
        }

        val currentState = _uiState.value as? ClaimDetailUiState.Success ?: return
        val claimId = currentState.claim.id

        _uiState.value = currentState.copy(message = "Verifying Patient ID...")

        firebaseHelper.getUserIdByCustomId(targetId, { patientUid ->
            if (patientUid != null) {
                firebaseHelper.updateClaimLinkage(claimId, patientUid, targetId, {
                    val latestState = _uiState.value as? ClaimDetailUiState.Success
                    if (latestState != null) {
                        _uiState.value = latestState.copy(message = "Bill Successfully Linked to $targetId")
                    }
                }, { e ->
                    val latestState = _uiState.value as? ClaimDetailUiState.Success
                    if (latestState != null) {
                        _uiState.value = latestState.copy(message = "Link Failed: ${e.message}")
                    }
                })
            } else {
                val latestState = _uiState.value as? ClaimDetailUiState.Success
                if (latestState != null) {
                    _uiState.value = latestState.copy(message = "Patient ID not found.")
                }
            }
        }, { e ->
            val latestState = _uiState.value as? ClaimDetailUiState.Success
            if (latestState != null) {
                _uiState.value = latestState.copy(message = "Lookup Error: ${e.message}")
            }
        })
    }

    fun clearMessage() {
        val currentState = _uiState.value as? ClaimDetailUiState.Success
        if (currentState != null) {
            _uiState.value = currentState.copy(message = null)
        }
    }

    fun exportClaimSummaryToPdf(): Boolean {
        val currentState = _uiState.value as? ClaimDetailUiState.Success ?: return false
        val claim = currentState.claim

        val pdfDocument = PdfDocument()
        val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas: Canvas = page.canvas
        val paint = Paint()

        paint.textSize = 20f; paint.isFakeBoldText = true; paint.color = Color.BLACK
        canvas.drawText("InsuMap Claim Adjudication Summary", 40f, 60f, paint)
        paint.strokeWidth = 2f; canvas.drawLine(40f, 80f, 555f, 80f, paint)
        paint.isFakeBoldText = false; paint.textSize = 14f

        var y = 120f
        canvas.drawText("Claim ID: ${claim.id}", 40f, y, paint); y += 30f
        canvas.drawText("Patient Name: ${claim.name}", 40f, y, paint); y += 30f
        canvas.drawText("Hospital: ${claim.hospital}", 40f, y, paint); y += 30f
        canvas.drawText("Total Invoice Amount: ₹${claim.amount}", 40f, y, paint); y += 30f
        canvas.drawText("Insurance Covered Amount: ₹%.2f".format(claim.coveredAmount), 40f, y, paint); y += 30f
        canvas.drawText("Patient Liability (Out of Pocket): ₹%.2f".format(claim.patientLiability), 40f, y, paint); y += 40f

        paint.isFakeBoldText = true; canvas.drawText("Itemized Billing Breakdown:", 40f, y, paint); y += 30f
        paint.isFakeBoldText = false
        claim.items.forEach { item ->
            canvas.drawText("${item.description} - Billed: ₹%.2f | Covered: ₹%.2f".format(item.amount, item.coveredAmount), 50f, y, paint)
            y += 24f
            if (y > 800f) {
                return@forEach
            }
        }

        pdfDocument.finishPage(page)
        val filename = "InsuMap_Claim_${claim.customPatientId.ifEmpty { claim.id }}.pdf"

        return try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> pdfDocument.writeTo(os) } }
            } else {
                val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
                java.io.FileOutputStream(file).use { pdfDocument.writeTo(it) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pdfDocument.close()
        }
    }

    override fun onCleared() {
        super.onCleared()
        snapshotListener?.remove()
        offlineInferenceHelper.close()
    }
}
