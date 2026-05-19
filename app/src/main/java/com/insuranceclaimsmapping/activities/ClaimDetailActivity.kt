package com.insuranceclaimsmapping.activities

import android.content.ContentValues
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.adapters.BillItemAdapter
import com.insuranceclaimsmapping.ai.OfflineInferenceHelper
import com.insuranceclaimsmapping.databinding.ActivityClaimDetailBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.utils.PrefManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClaimDetailActivity : AppCompatActivity() {
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var offlineInferenceHelper: OfflineInferenceHelper
    private lateinit var prefManager: PrefManager
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityClaimDetailBinding
    private var claimId: String? = null
    private var currentClaim: Claim? = null
    private var isAutoPredictStarted = false
    private var snapshotListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClaimDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseHelper = FirebaseHelper()
        offlineInferenceHelper = OfflineInferenceHelper(this)
        prefManager = PrefManager(this)
        auth = FirebaseAuth.getInstance()

        claimId = intent.getStringExtra("claimId")

        fetchClaimDetails()
        setupBranding()

        binding.btnPredictor.setOnClickListener { startFinancialPredictor() }
        binding.btnLinkPatient.setOnClickListener { linkClaimToPatient() }
        binding.btnAppeal.setOnClickListener {
            val claim = currentClaim ?: return@setOnClickListener
            startActivity(Intent(this, AppealClaimActivity::class.java).apply {
                putExtra("claimId", claim.id)
                putExtra("claimDesc", claim.description)
                putExtra("claimAmount", claim.amount)
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
        snapshotListener = null
    }

    private fun setupBranding() {
        val role = prefManager.getRole() ?: "PATIENT"

        val bg: Int
        val primaryColor: Int
        val statusBarColor: Int

        when (role) {
            "HOSPITAL" -> {
                bg = R.color.green_light
                primaryColor = getColor(R.color.hospital_primary)
                statusBarColor = getColor(R.color.hospital_dark)
                binding.cardDesignation.visibility = View.VISIBLE
            }
            "INSURER" -> {
                bg = R.color.blue_light
                primaryColor = getColor(R.color.insurer_primary)
                statusBarColor = getColor(R.color.insurer_dark)
                binding.cardDesignation.visibility = View.GONE
            }
            else -> {
                bg = R.color.yellow_light
                primaryColor = getColor(R.color.patient_primary)
                statusBarColor = getColor(R.color.patient_dark)
                binding.cardDesignation.visibility = View.GONE
            }
        }

        binding.rootLayoutDetail.setBackgroundResource(bg)
        binding.btnPredictor.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        binding.tvDetailTitle.setTextColor(primaryColor)
        window.statusBarColor = statusBarColor
    }

    private fun fetchClaimDetails() {
        val id = claimId ?: return
        snapshotListener = FirebaseFirestore.getInstance().collection("claims").document(id)
            .addSnapshotListener { snapshot, e ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val claim = firebaseHelper.safeMapToClaim(snapshot)
                    claim?.let {
                        currentClaim = it
                        updateUI(it)
                    }
                }
            }
    }

    private fun updateUI(claim: Claim) {
        binding.tvStatCovered.text = "₹%.2f".format(claim.coveredAmount)
        binding.tvStatLiability.text = "₹%.2f".format(claim.patientLiability)
        binding.tvStatTotal.text = "₹%.2f".format(claim.amount.toDoubleOrNull() ?: 0.0)

        binding.tvStatusBill.text = if (claim.isBillLoaded) "✅ Hospital Bill Loaded" else "○ Bill Pending"
        binding.tvStatusBill.alpha = if (claim.isBillLoaded) 1.0f else 0.5f
        binding.tvStatusPolicy.text = if (claim.isPolicyLoaded) "✅ Insurance Policy Loaded" else "○ Policy Pending"
        binding.tvStatusPolicy.alpha = if (claim.isPolicyLoaded) 1.0f else 0.5f

        val role = prefManager.getRole() ?: "PATIENT"

        if (claim.status == "ADJUDICATED") {
            binding.cardSummary.visibility = View.VISIBLE
            if (role == "PATIENT") {
                binding.tvSummaryTitle.text = "FINAL PAYMENT SUMMARY"
                binding.tvSummaryAmount.text = "You need to pay ₹%.2f".format(claim.patientLiability)
            } else {
                binding.tvSummaryTitle.text = "CLAIM ADJUDICATION SUMMARY"
                binding.tvSummaryAmount.text = "Patient owes: ₹%.2f".format(claim.patientLiability)
            }
            binding.btnExportPdf.setOnClickListener { exportClaimSummaryToPdf(claim) }
        } else {
            binding.cardSummary.visibility = View.GONE
        }

        val hasItemFraud = claim.items.any { it.fraudWarning }
        val finalFraudWarning = claim.fraudWarning || hasItemFraud
        if (finalFraudWarning) {
            binding.cardFraudWarning.visibility = View.VISIBLE
            binding.tvFraudReasoning.text = when {
                claim.fraudReasoning.isNotEmpty() -> claim.fraudReasoning
                hasItemFraud -> "One or more billed items have been flagged for extreme price anomalies."
                else -> "AI Flagged this claim for manual review due to anomalies."
            }
        } else {
            binding.cardFraudWarning.visibility = View.GONE
        }

        binding.rvBillItems.layoutManager = LinearLayoutManager(this)
        binding.rvBillItems.adapter = BillItemAdapter(claim.items)

        when {
            role == "INSURER" && claim.status == "PENDING" -> { binding.btnPredictor.visibility = View.VISIBLE; binding.btnPredictor.text = "Calculate Financial Predictor" }
            role == "PATIENT" && claim.status == "PENDING" -> { binding.btnPredictor.visibility = View.VISIBLE; binding.btnPredictor.text = "Predict My Out-of-Pocket Cost" }
            else -> binding.btnPredictor.visibility = View.GONE
        }

        binding.btnAppeal.visibility = if (role == "PATIENT" && claim.status == "REJECTED") View.VISIBLE else View.GONE

        updateTimeline(claim.status)

        if (role == "PATIENT" && claim.status == "PENDING" && !isAutoPredictStarted) {
            isAutoPredictStarted = true
            startFinancialPredictor()
        }
    }

    private fun updateTimeline(status: String) {
        val activeColor = getColor(R.color.insurer_primary)
        val doneColor = getColor(R.color.hospital_primary)
        val pendingColor = getColor(R.color.gray)
        val rejectedColor = getColor(R.color.status_rejected)
        val appealColor = getColor(R.color.status_appeal)

        binding.tvTimelineStep1.setBackgroundColor(doneColor)

        when (status) {
            "PENDING" -> { binding.tvTimelineStep2.setBackgroundColor(activeColor); binding.tvTimelineStep3.setBackgroundColor(pendingColor); binding.tvTimelineStep3Label.text = "Adjudicated" }
            "ADJUDICATED" -> { binding.tvTimelineStep2.setBackgroundColor(doneColor); binding.tvTimelineStep3.setBackgroundColor(doneColor); binding.tvTimelineStep3Label.text = "Adjudicated ✅" }
            "REJECTED" -> { binding.tvTimelineStep2.setBackgroundColor(doneColor); binding.tvTimelineStep3.setBackgroundColor(rejectedColor); binding.tvTimelineStep3Label.text = "Rejected ❌" }
            "APPEAL_PENDING" -> { binding.tvTimelineStep2.setBackgroundColor(doneColor); binding.tvTimelineStep3.setBackgroundColor(appealColor); binding.tvTimelineStep3Label.text = "Appeal Pending ⏳" }
            else -> { binding.tvTimelineStep2.setBackgroundColor(activeColor); binding.tvTimelineStep3.setBackgroundColor(pendingColor) }
        }
    }

    private fun startFinancialPredictor() {
        val claim = currentClaim ?: return

        binding.progressAdjudication.visibility = View.VISIBLE
        binding.btnPredictor.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUid = auth.currentUser?.uid ?: run {
                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        binding.progressAdjudication.visibility = View.GONE
                        binding.btnPredictor.isEnabled = true
                        Toast.makeText(this@ClaimDetailActivity, "Not logged in.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                var policyRules = "Standard Policy: 20% Copay applies to all items."
                val policyFetch = kotlinx.coroutines.CompletableDeferred<String>()
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) {
                        policyFetch.complete(policyRules)
                        return@withContext
                    }
                    firebaseHelper.getPolicy(currentUid, { policy ->
                        policyFetch.complete(policy?.coverageDetails?.takeIf { it.isNotEmpty() } ?: policyRules)
                    }, {
                        policyFetch.complete(policyRules)
                    })
                }
                policyRules = policyFetch.await()

                val adjudicatedItems = offlineInferenceHelper.adjudicateItemized(claim.items, policyRules)

                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    if (adjudicatedItems.isNotEmpty()) {
                        val totalCovered = adjudicatedItems.sumOf { it.coveredAmount }
                        val totalBill = claim.amount.toDoubleOrNull() ?: 0.0
                        saveResults(adjudicatedItems, totalCovered, totalBill - totalCovered)
                    } else {
                        Toast.makeText(this@ClaimDetailActivity, "AI Evaluation returned empty results.", Toast.LENGTH_SHORT).show()
                    }
                    binding.progressAdjudication.visibility = View.GONE
                    binding.btnPredictor.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    binding.progressAdjudication.visibility = View.GONE
                    binding.btnPredictor.isEnabled = true
                    Toast.makeText(this@ClaimDetailActivity, "Evaluation Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveResults(items: List<com.insuranceclaimsmapping.models.BillItem>, covered: Double, liability: Double) {
        claimId?.let { id ->
            FirebaseFirestore.getInstance().collection("claims").document(id)
                .update(mapOf(
                    "status" to "ADJUDICATED",
                    "items" to items,
                    "coveredAmount" to covered,
                    "patientLiability" to liability,
                    "aiReasoning" to "Optimized via Explainable AI Predictor"
                ))
                .addOnSuccessListener {
                    if (isFinishing || isDestroyed) return@addOnSuccessListener
                    Toast.makeText(this, "Success! Adjudication Complete", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    if (isFinishing || isDestroyed) return@addOnFailureListener
                    Toast.makeText(this, "Failed to save results: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun linkClaimToPatient() {
        val targetId = binding.etTargetPatientId.text.toString().trim()
        val cid = claimId ?: return

        if (targetId.isEmpty()) {
            Toast.makeText(this, "Please enter a Patient ID", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Verifying Patient ID...", Toast.LENGTH_SHORT).show()
        firebaseHelper.getUserIdByCustomId(targetId, { patientUid ->
            if (isFinishing || isDestroyed) return@getUserIdByCustomId
            if (patientUid != null) {
                firebaseHelper.updateClaimLinkage(cid, patientUid, targetId, {
                    if (isFinishing || isDestroyed) return@updateClaimLinkage
                    Toast.makeText(this, "Bill Successfully Linked to $targetId", Toast.LENGTH_LONG).show()
                    binding.etTargetPatientId.text.clear()
                }, {
                    if (isFinishing || isDestroyed) return@updateClaimLinkage
                    Toast.makeText(this, "Link Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                })
            } else {
                Toast.makeText(this, "Patient ID not found.", Toast.LENGTH_LONG).show()
            }
        }, {
            if (isFinishing || isDestroyed) return@getUserIdByCustomId
            Toast.makeText(this, "Lookup Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun exportClaimSummaryToPdf(claim: Claim) {
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
                // simple pagination bypass / clip warning protection (this avoids breaking standard pdf flow)
                return@forEach
            }
        }

        pdfDocument.finishPage(page)
        val filename = "InsuMap_Claim_${claim.customPatientId.ifEmpty { claim.id }}.pdf"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                uri?.let { contentResolver.openOutputStream(it)?.use { os -> pdfDocument.writeTo(os) } }
                    ?: Toast.makeText(this, "Failed to create PDF file", Toast.LENGTH_SHORT).show()
                Toast.makeText(this, "PDF saved to Downloads", Toast.LENGTH_LONG).show()
            } else {
                val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
                java.io.FileOutputStream(file).use { pdfDocument.writeTo(it) }
                Toast.makeText(this, "PDF saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }
}
