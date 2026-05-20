package com.insuranceclaimsmapping.activities

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.ai.OfflineInferenceHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class AddClaimViewModel(application: Application) : AndroidViewModel(application) {
    
    private val firebaseHelper = FirebaseHelper()
    private val auth = FirebaseAuth.getInstance()
    private val prefManager = PrefManager(application)
    private val offlineInferenceHelper = OfflineInferenceHelper(application)
    
    private val _uiState = MutableStateFlow<AddClaimUiState>(AddClaimUiState.Idle)
    val uiState: StateFlow<AddClaimUiState> = _uiState.asStateFlow()

    private var _duplicateDialogState = MutableStateFlow<Claim?>(null)
    val duplicateDialogState: StateFlow<Claim?> = _duplicateDialogState.asStateFlow()

    val userRole: String
        get() = prefManager.getRole() ?: "PATIENT"

    fun processImage(uri: Uri) {
        _uiState.value = AddClaimUiState.Processing("Extracting data via AI...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(getApplication<Application>().contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(getApplication<Application>().contentResolver, uri)
                }
                
                val result = offlineInferenceHelper.extractItemizedBill(bitmap)
                
                withContext(Dispatchers.Main) {
                    if (result.items.isNotEmpty() && result.hospitalName.isNotEmpty()) {
                        _uiState.value = AddClaimUiState.Reviewing(
                            uri = uri,
                            patientName = result.patientName,
                            hospitalName = result.hospitalName,
                            amount = result.items.sumOf { it.amount }.toString(),
                            description = result.items.joinToString(", ") { it.description },
                            items = result.items
                        )
                    } else {
                        _uiState.value = AddClaimUiState.Error("AI could not extract information. Enter manually.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = AddClaimUiState.Error("AI Error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun processPdf(uri: Uri) {
        _uiState.value = AddClaimUiState.Processing("Parsing PDF Document...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fd = getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
                if (fd == null) {
                    withContext(Dispatchers.Main) { _uiState.value = AddClaimUiState.Error("Could not open file.") }
                    return@launch
                }
                
                var extractedBitmap: Bitmap? = null
                fd.use { fileDescriptor ->
                    val renderer = PdfRenderer(fileDescriptor)
                    renderer.use { pdfRenderer ->
                        if (pdfRenderer.pageCount > 0) {
                            pdfRenderer.openPage(0).use { page ->
                                // Safe scaling to prevent OOM
                                val scaleFactor = Math.min(2048f / Math.max(page.width, page.height), 2f)
                                val w = (page.width * scaleFactor).toInt()
                                val h = (page.height * scaleFactor).toInt()
                                
                                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                extractedBitmap = bitmap
                            }
                        }
                    }
                }
                
                extractedBitmap?.let { bitmap ->
                    val result = offlineInferenceHelper.extractItemizedBill(bitmap)
                    withContext(Dispatchers.Main) {
                        if (result.items.isNotEmpty()) {
                            _uiState.value = AddClaimUiState.Reviewing(
                                uri = uri,
                                patientName = result.patientName,
                                hospitalName = result.hospitalName,
                                amount = result.items.sumOf { it.amount }.toString(),
                                description = result.items.joinToString { it.description },
                                items = result.items
                            )
                        } else {
                            _uiState.value = AddClaimUiState.Error("AI could not extract PDF data.")
                        }
                    }
                } ?: run {
                    withContext(Dispatchers.Main) { _uiState.value = AddClaimUiState.Error("Invalid PDF format.") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = AddClaimUiState.Error("PDF Processing Error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun submitClaim(claim: Claim) {
        _uiState.value = AddClaimUiState.Processing("Submitting Claim...")
        viewModelScope.launch {
            try {
                // Check for duplicates first
                val patientIdToCheck = if (userRole == "PATIENT") auth.currentUser?.uid ?: "" else claim.patientId
                val isDuplicate = suspendCancellableCoroutine<Boolean> { cont ->
                    firebaseHelper.getDuplicateClaims(patientIdToCheck, claim.hospital, claim.amount) { isDupe ->
                        cont.resume(isDupe)
                    }
                }

                if (isDuplicate) {
                    _duplicateDialogState.value = claim
                    _uiState.value = AddClaimUiState.Idle // Revert state so dialog can show
                } else {
                    executeSubmit(claim)
                }
            } catch (e: Exception) {
                _uiState.value = AddClaimUiState.Error("Submission Failed: ${e.localizedMessage}")
            }
        }
    }
    
    fun confirmDuplicateSubmission() {
        val claim = _duplicateDialogState.value
        _duplicateDialogState.value = null
        if (claim != null) {
            _uiState.value = AddClaimUiState.Processing("Submitting Claim...")
            viewModelScope.launch {
                executeSubmit(claim)
            }
        }
    }
    
    fun dismissDuplicateDialog() {
        _duplicateDialogState.value = null
    }

    private suspend fun executeSubmit(claim: Claim) {
        try {
            suspendCancellableCoroutine<String> { cont ->
                firebaseHelper.addClaim(claim, { docId ->
                    cont.resume(docId)
                }, { e ->
                    cont.resumeWithException(e)
                })
            }
            _uiState.value = AddClaimUiState.Success
        } catch (e: Exception) {
            _uiState.value = AddClaimUiState.Error("Failed: ${e.localizedMessage}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        offlineInferenceHelper.close()
    }
}
