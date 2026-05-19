package com.insuranceclaimsmapping.activities

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.ai.OfflineInferenceHelper
import com.insuranceclaimsmapping.databinding.ActivityAddClaimBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddClaimActivity : AppCompatActivity() {
    private var photoUri: Uri? = null
    private var billFileUri: Uri? = null
    private val firebaseHelper = FirebaseHelper()
    private val offlineInferenceHelper by lazy { OfflineInferenceHelper(this) }
    private var resolvedPatientUid: String? = null
    private lateinit var binding: ActivityAddClaimBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) openCamera()
        else Toast.makeText(this, "Camera Permission is required to scan bills", Toast.LENGTH_SHORT).show()
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scanResult = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                photoUri = uri
                processImageWithAI(uri)
            }
        }
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val current = binding.etDescription.text.toString()
                binding.etDescription.setText(if (current.isEmpty()) results[0] else "$current ${results[0]}")
                binding.etDescription.setSelection(binding.etDescription.text.length)
            }
        }
    }

    private val pdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { billFileUri = it; processPdfWithAI(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddClaimBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefManager = PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        applyRoleBranding(role)

        if (role == "HOSPITAL") {
            binding.etPatientId.visibility = View.VISIBLE
            setupPatientSearch()
        }

        binding.btnScan.setOnClickListener {
            binding.layoutManualInput.visibility = View.GONE
            openScanner()
        }
        binding.btnUploadPdf.setOnClickListener {
            binding.layoutManualInput.visibility = View.GONE
            pdfLauncher.launch("application/pdf")
        }
        binding.btnManual.setOnClickListener { binding.layoutManualInput.visibility = View.VISIBLE }

        binding.btnMic.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak the diagnosis or surgery details...")
            }
            try { speechLauncher.launch(intent) }
            catch (e: Exception) { Toast.makeText(this, "Speech recognition not supported.", Toast.LENGTH_SHORT).show() }
        }

        binding.btnSubmitClaim.setOnClickListener {
            val patientIdInput = binding.etPatientId.text.toString().trim()
            val name = binding.etPatientName.text.toString().trim()
            val hospital = binding.etHospitalName.text.toString().trim()
            val amount = binding.etAmount.text.toString().trim()
            val description = binding.etDescription.text.toString().trim()

            if (name.isEmpty() || hospital.isEmpty() || amount.isEmpty()) {
                Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUid == null) {
                Toast.makeText(this, "You must be logged in to submit a claim.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val patientIdForDupe = if (role == "PATIENT") currentUid else (resolvedPatientUid ?: "")
            if (patientIdForDupe.isNotEmpty()) {
                firebaseHelper.getDuplicateClaims(patientIdForDupe, hospital, amount) { isDuplicate: Boolean ->
                    if (isFinishing || isDestroyed) return@getDuplicateClaims
                    if (isDuplicate) {
                        AlertDialog.Builder(this)
                            .setTitle("Possible Duplicate")
                            .setMessage("A claim with the same patient, hospital, and amount already exists. Submit anyway?")
                            .setPositiveButton("Submit Anyway") { _, _ -> proceedWithSubmit(role, patientIdInput, name, hospital, amount, description) }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        proceedWithSubmit(role, patientIdInput, name, hospital, amount, description)
                    }
                }
            } else {
                proceedWithSubmit(role, patientIdInput, name, hospital, amount, description)
            }
        }
    }

    private fun setupPatientSearch() {
        binding.etPatientId.setOnEditorActionListener { _, _, _ ->
            val query = binding.etPatientId.text.toString().trim()
            if (query.length >= 2) {
                firebaseHelper.getUsersByName(query, { users: List<User> ->
                    if (isFinishing || isDestroyed) return@getUsersByName
                    if (users.isNotEmpty()) {
                        val names = users.map { "${it.displayName} (${it.customId})" }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Select Patient")
                            .setItems(names as Array<out CharSequence>) { _: DialogInterface, idx: Int ->
                                val selected = users[idx]
                                binding.etPatientId.setText(selected.customId)
                                resolvedPatientUid = selected.uid
                                binding.etPatientName.setText(selected.displayName)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        Toast.makeText(this, "No patients found matching '$query'", Toast.LENGTH_SHORT).show()
                    }
                }, { e: Exception ->
                    if (isFinishing || isDestroyed) return@getUsersByName
                    Toast.makeText(this, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
                })
            }
            false
        }
    }

    private fun proceedWithSubmit(role: String, patientIdInput: String, name: String, hospital: String, amount: String, description: String) {
        if (role == "HOSPITAL" && patientIdInput.isNotEmpty() && resolvedPatientUid == null) {
            firebaseHelper.getUserIdByCustomId(patientIdInput, { patientUid: String? ->
                if (isFinishing || isDestroyed) return@getUserIdByCustomId
                submitClaim(null, name, hospital, amount, description, emptyList(), patientUid)
            }, { e: Exception ->
                if (isFinishing || isDestroyed) return@getUserIdByCustomId
                Toast.makeText(this, "Error finding Patient ID: ${e.message}", Toast.LENGTH_SHORT).show()
            })
        } else {
            submitClaim(null, name, hospital, amount, description, emptyList(), if (role == "PATIENT") FirebaseAuth.getInstance().currentUser?.uid else resolvedPatientUid)
        }
    }

    private fun openCamera() {
        val file = java.io.File.createTempFile("bill_", ".jpg", getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES))
        photoUri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        cameraLauncher.launch(photoUri)
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) photoUri?.let { processImageWithAI(it) }
    }

    private fun processImageWithAI(uri: Uri) {
        binding.layoutProcessing.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                val result = offlineInferenceHelper.extractItemizedBill(bitmap)
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    if (result.items.isNotEmpty() && result.hospitalName.isNotEmpty()) {
                        submitClaim(uri.toString(), result.patientName, result.hospitalName,
                            result.items.sumOf { it.amount }.toString(),
                            result.items.joinToString(", ") { it.description }, result.items, null)
                    } else {
                        showManualFallback("AI could not extract information. Enter manually.")
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiError", "AI Error", e)
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    showManualFallback("AI Error: ${e.message}") 
                }
            } finally {
                withContext(Dispatchers.Main) { 
                    if (!isFinishing && !isDestroyed) {
                        binding.layoutProcessing.visibility = View.GONE 
                    }
                }
            }
        }
    }

    private fun processPdfWithAI(uri: Uri) {
        binding.layoutProcessing.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fd = contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                fd.use { fileDescriptor ->
                    val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
                    renderer.use {
                        if (it.pageCount > 0) {
                            val page = it.openPage(0)
                            val bitmap = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                            page.use { p -> p.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                            val result = offlineInferenceHelper.extractItemizedBill(bitmap)
                            withContext(Dispatchers.Main) {
                                if (isFinishing || isDestroyed) return@withContext
                                if (result.items.isNotEmpty()) {
                                    submitClaim(uri.toString(), result.patientName, result.hospitalName, result.items.sumOf { it.amount }.toString(), result.items.joinToString { it.description }, result.items, null)
                                } else showManualFallback("AI could not extract PDF data.")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    if (isFinishing || isDestroyed) return@withContext
                    showManualFallback("PDF AI Error: ${e.message}") 
                }
            } finally {
                withContext(Dispatchers.Main) { 
                    if (!isFinishing && !isDestroyed) {
                        binding.layoutProcessing.visibility = View.GONE 
                    }
                }
            }
        }
    }

    private fun showManualFallback(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.layoutManualInput.visibility = View.VISIBLE
    }

    private fun submitClaim(billUrl: String?, name: String, hospital: String, amount: String, description: String, items: List<com.insuranceclaimsmapping.models.BillItem>, patientId: String?) {
        val role = PrefManager(this).getRole() ?: "PATIENT"
        val currUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val claim = Claim(
            name = name, hospital = hospital, amount = amount, description = description,
            userId = if (role == "PATIENT") "" else currUserId,
            patientId = if (role == "PATIENT") currUserId else (patientId ?: ""),
            billUrl = billUrl ?: "", items = items,
            isBillLoaded = true, isPolicyLoaded = true, timestamp = Timestamp.now()
        )
        firebaseHelper.addClaim(claim, { docId: String ->
            if (isFinishing || isDestroyed) return@addClaim
            Toast.makeText(this, "Claim Submitted", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ClaimDetailActivity::class.java).apply { putExtra("claimId", docId) })
            finish()
        }, { e: Exception -> 
            if (isFinishing || isDestroyed) return@addClaim
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() 
        })
    }

    private fun applyRoleBranding(role: String) {
        val (bg, colorRes) = when (role) {
            "HOSPITAL" -> R.color.green_light to R.color.hospital_primary
            "INSURER"  -> R.color.blue_light  to R.color.insurer_primary
            "PATIENT"  -> R.color.yellow_light to R.color.patient_primary
            else       -> R.color.gray         to R.color.default_primary
        }
        binding.rootAddClaim.setBackgroundResource(bg)
        binding.tvAddClaimHeader.setTextColor(getColor(colorRes))
        window.statusBarColor = getColor(colorRes)
    }

    private fun openScanner() {
        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setResultFormats(RESULT_FORMAT_JPEG).setScannerMode(SCANNER_MODE_FULL).build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this).addOnSuccessListener { intentSender ->
            scannerLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
        }.addOnFailureListener { e ->
            if (isFinishing || isDestroyed) return@addOnFailureListener
            Toast.makeText(this, "Scanner Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        offlineInferenceHelper.close()
    }
}
