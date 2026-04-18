package com.insuranceclaimsmapping.activities

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.ai.GeminiHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddClaimActivity : AppCompatActivity() {
    private var photoUri: Uri? = null
    private var billFileUri: Uri? = null
    private val firebaseHelper = FirebaseHelper()
    private val geminiHelper by lazy { GeminiHelper(this) }

    private val permissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera Permission is required to scan bills", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_claim)

        val layoutManualInput = findViewById<android.widget.LinearLayout>(R.id.layoutManualInput)
        val layoutProcessing = findViewById<android.widget.LinearLayout>(R.id.layoutProcessing)
        val tvProcessingStatus = findViewById<android.widget.TextView>(R.id.tvProcessingStatus)

        val etPatientId = findViewById<EditText>(R.id.etPatientId)
        val etPatientName = findViewById<EditText>(R.id.etPatientName)
        val etHospitalName = findViewById<EditText>(R.id.etHospitalName)
        val etAmount = findViewById<EditText>(R.id.etAmount)
        val etDescription = findViewById<EditText>(R.id.etDescription)
        
        val btnScan = findViewById<Button>(R.id.btnScan)
        val btnUploadPdf = findViewById<Button>(R.id.btnUploadPdf)
        val btnManual = findViewById<Button>(R.id.btnManual)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitClaim)

        // Apply Role Styling
        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        applyRoleBranding(role)

        if (role == "HOSPITAL") {
            val etPatientId = findViewById<EditText>(R.id.etPatientId)
            etPatientId.visibility = android.view.View.VISIBLE
        }

        btnScan.setOnClickListener {
            layoutManualInput.visibility = android.view.View.GONE
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                permissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        btnUploadPdf.setOnClickListener {
            layoutManualInput.visibility = android.view.View.GONE
            pdfLauncher.launch("application/pdf")
        }

        btnManual.setOnClickListener {
            layoutManualInput.visibility = android.view.View.VISIBLE
        }

        btnSubmit.setOnClickListener {
            val patientIdInput = etPatientId.text.toString().trim()
            val name = etPatientName.text.toString().trim()
            val hospital = etHospitalName.text.toString().trim()
            val amount = etAmount.text.toString().trim()
            val description = etDescription.text.toString().trim()

            if (name.isEmpty() || hospital.isEmpty() || amount.isEmpty()) {
                Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (role == "HOSPITAL" && patientIdInput.isNotEmpty()) {
                // Link claim to specific patient account
                firebaseHelper.getUserIdByCustomId(patientIdInput, { patientUid ->
                    if (patientUid != null) {
                        submitClaimWithLink(null, name, hospital, amount, description, emptyList(), patientUid)
                    } else {
                        Toast.makeText(this, "Patient ID $patientIdInput not found. Proceeding as unlinked.", Toast.LENGTH_LONG).show()
                        submitClaimWithLink(null, name, hospital, amount, description, emptyList(), null)
                    }
                }, {
                    Toast.makeText(this, "Error finding Patient ID: ${it.message}", Toast.LENGTH_SHORT).show()
                })
            } else {
                submitClaim(null, name, hospital, amount, description, emptyList())
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { processImageWithAI(it) }
        }
    }

    private val pdfLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            billFileUri = it
            processPdfWithAI(it)
        }
    }

    private fun processImageWithAI(uri: Uri) {
        val layoutProcessing = findViewById<android.widget.LinearLayout>(R.id.layoutProcessing)
        layoutProcessing.visibility = android.view.View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // Convert URI to Bitmap
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri))
                } else {
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                
                val result = geminiHelper.extractItemizedBill(bitmap)
                if (result.items.isNotEmpty()) {
                    val totalAmount = result.items.sumOf { it.amount }.toString()
                    val diagnosis = result.items.joinToString(", ") { it.description }
                    submitClaim(uri.toString(), result.patientName, result.hospitalName, totalAmount, diagnosis, result.items)
                } else {
                    showManualFallback("AI couldn't read the bill. Please enter manually.")
                }
            } catch (e: Exception) {
                val errorMsg = "AI Error: ${e.localizedMessage ?: "Unknown Error"}"
                Log.e("GeminiError", errorMsg, e)
                showManualFallback(errorMsg)
            } finally {
                layoutProcessing.visibility = android.view.View.GONE
            }
        }
    }

    private fun processPdfWithAI(uri: Uri) {
        val layoutProcessing = findViewById<android.widget.LinearLayout>(R.id.layoutProcessing)
        layoutProcessing.visibility = android.view.View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                if (fileDescriptor != null) {
                    val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bitmap = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        
                        withContext(Dispatchers.Main) {
                            processRenderedBitmap(bitmap, uri.toString())
                        }
                        page.close()
                    }
                    renderer.close()
                    fileDescriptor.close()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showManualFallback("PDF AI Error: ${e.localizedMessage ?: "Unknown Error"}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    layoutProcessing.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun processRenderedBitmap(bitmap: android.graphics.Bitmap, fileUrl: String) {
        lifecycleScope.launch {
            try {
                val result = geminiHelper.extractItemizedBill(bitmap)
                if (result.items.isNotEmpty()) {
                    val totalAmount = result.items.sumOf { it.amount }.toString()
                    val diagnosis = result.items.joinToString(", ") { it.description }
                    submitClaim(fileUrl, result.patientName, result.hospitalName, totalAmount, diagnosis, result.items)
                } else {
                    showManualFallback("AI couldn't read the PDF content. Please enter manually.")
                }
            } catch (e: Exception) {
                showManualFallback("AI PDF Error: ${e.localizedMessage ?: "Unknown Error"}")
            }
        }
    }

    private fun showManualFallback(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        findViewById<android.widget.LinearLayout>(R.id.layoutManualInput).visibility = android.view.View.VISIBLE
    }

    private fun submitClaim(billUrl: String?, name: String, hospital: String, amount: String, description: String, items: List<com.insuranceclaimsmapping.models.BillItem>) {
        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        val currUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val claim = Claim(
            name = name,
            hospital = hospital,
            amount = amount,
            description = description,
            userId = if (role == "PATIENT") "" else currUserId,
            patientId = if (role == "PATIENT") currUserId else "", 
            billUrl = billUrl ?: "",
            items = items,
            isBillLoaded = true,
            isPolicyLoaded = true,
            timestamp = Timestamp.now()
        )

        firebaseHelper.addClaim(claim, { docId ->
            Toast.makeText(this, "Claim Submitted Successfully", Toast.LENGTH_SHORT).show()
            val intent = android.content.Intent(this, ClaimDetailActivity::class.java).apply {
                putExtra("claimId", docId)
                putExtra("hospital", hospital)
                putExtra("patient", name)
                putExtra("amount", amount)
                putExtra("status", "PENDING")
            }
            startActivity(intent)
            finish()
        }, {
            Toast.makeText(this, "Submission Failed: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun submitClaimWithLink(billUrl: String?, name: String, hospital: String, amount: String, description: String, items: List<com.insuranceclaimsmapping.models.BillItem>, linkedPatientId: String?) {
        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        val currUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val claim = Claim(
            name = name,
            hospital = hospital,
            amount = amount,
            description = description,
            userId = if (role == "PATIENT") "" else currUserId,
            patientId = if (role == "PATIENT") currUserId else (linkedPatientId ?: ""), 
            customPatientId = if (role == "HOSPITAL") findViewById<EditText>(R.id.etPatientId).text.toString() else "",
            billUrl = billUrl ?: "",
            items = items,
            isBillLoaded = true,
            isPolicyLoaded = true,
            timestamp = com.google.firebase.Timestamp.now()
        )

        firebaseHelper.addClaim(claim, { docId ->
            Toast.makeText(this, "Claim Submitted Successfully", Toast.LENGTH_SHORT).show()
            val intent = android.content.Intent(this, ClaimDetailActivity::class.java).apply {
                putExtra("claimId", docId)
                putExtra("hospital", hospital)
                putExtra("patient", name)
                putExtra("amount", amount)
                putExtra("status", "PENDING")
            }
            startActivity(intent)
            finish()
        }, {
            Toast.makeText(this, "Submission Failed: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun applyRoleBranding(role: String) {
        val root = findViewById<android.view.View>(R.id.rootAddClaim)
        val header = findViewById<android.widget.TextView>(R.id.tvAddClaimHeader)
        
        val (bg, color) = when (role) {
            "HOSPITAL" -> R.color.green_light to android.graphics.Color.parseColor("#2E7D32")
            "INSURER" -> R.color.blue_light to android.graphics.Color.parseColor("#1565C0")
            "PATIENT" -> R.color.yellow_light to android.graphics.Color.parseColor("#F57F17")
            else -> R.color.gray to android.graphics.Color.parseColor("#00796B")
        }
        
        root?.setBackgroundResource(bg)
        header?.setTextColor(color)
        window.statusBarColor = color
    }

    private fun openCamera() {
        val fileName = "bill_${System.currentTimeMillis()}.jpg"
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val file = java.io.File.createTempFile(fileName, ".jpg", storageDir)
        photoUri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        cameraLauncher.launch(photoUri)
    }
}
