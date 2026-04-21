package com.insuranceclaimsmapping.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.adapters.BillItemAdapter
import com.insuranceclaimsmapping.ai.GeminiHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.utils.PrefManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class ClaimDetailActivity : AppCompatActivity() {
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var prefManager: PrefManager
    private lateinit var auth: FirebaseAuth
    private var claimId: String? = null
    private var currentClaim: Claim? = null
    private var isAutoPredictStarted = false
    private var currentRole: String = "PATIENT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_claim_detail)

        firebaseHelper = FirebaseHelper()
        geminiHelper = GeminiHelper(this)
        prefManager = PrefManager(this)
        auth = FirebaseAuth.getInstance()
        
        claimId = intent.getStringExtra("claimId")
        currentRole = prefManager.getRole() ?: "PATIENT"
        
        fetchClaimDetails()

        findViewById<Button>(R.id.btnPredictor).setOnClickListener {
            startFinancialPredictor()
        }
        
        setupBranding()

        findViewById<Button>(R.id.btnLinkPatient).setOnClickListener {
            linkClaimToPatient()
        }

        configureRoleRestrictedViews()
    }

    private fun configureRoleRestrictedViews() {
        val btnLinkPatient = findViewById<Button>(R.id.btnLinkPatient)
        val isHospital = currentRole == "HOSPITAL"
        btnLinkPatient.visibility = if (isHospital) View.VISIBLE else View.GONE
    }

    private fun setupBranding() {
        val role = prefManager.getRole() ?: "PATIENT"
        val root = findViewById<View>(R.id.rootLayoutDetail)
        val btnPredictor = findViewById<Button>(R.id.btnPredictor)
        val tvDetailTitle = findViewById<TextView>(R.id.tvDetailTitle)

        var bg: Int
        var primaryColor: Int
        val statusBarColor: Int
        val cardDesignation = findViewById<View>(R.id.cardDesignation)

        when (role) {
            "HOSPITAL" -> {
                bg = R.color.green_light
                primaryColor = android.graphics.Color.parseColor("#2E7D32")
                statusBarColor = android.graphics.Color.parseColor("#1B5E20")
                cardDesignation.visibility = View.VISIBLE
            }
            "INSURER" -> {
                bg = R.color.blue_light
                primaryColor = android.graphics.Color.parseColor("#1565C0")
                statusBarColor = android.graphics.Color.parseColor("#0D47A1")
                cardDesignation.visibility = View.GONE
            }
            else -> {
                bg = R.color.yellow_light
                primaryColor = android.graphics.Color.parseColor("#F57F17")
                statusBarColor = android.graphics.Color.parseColor("#E65100")
                cardDesignation.visibility = View.GONE
            }
        }

        root.setBackgroundResource(bg)
        btnPredictor.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        tvDetailTitle.setTextColor(primaryColor)
        window.statusBarColor = statusBarColor
    }

    private fun fetchClaimDetails() {
        claimId?.let { id ->
            FirebaseFirestore.getInstance().collection("claims").document(id)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val claim = firebaseHelper.safeMapToClaim(snapshot)
                        claim?.let {
                            if (!isClaimAccessible(it)) {
                                Toast.makeText(this, "You do not have access to this claim.", Toast.LENGTH_LONG).show()
                                finish()
                                return@addSnapshotListener
                            }
                            currentClaim = it
                            updateUI(it)
                        }
                    }
                }
        }
    }

    private fun isClaimAccessible(claim: Claim): Boolean {
        val currentUid = auth.currentUser?.uid ?: return false
        return when (currentRole) {
            "PATIENT" -> claim.patientId == currentUid
            "HOSPITAL" -> claim.userId == currentUid
            "INSURER" -> true
            else -> false
        }
    }

    private fun updateUI(claim: Claim) {
        val tvStatCovered = findViewById<TextView>(R.id.tvStatCovered)
        val tvStatLiability = findViewById<TextView>(R.id.tvStatLiability)
        val tvStatTotal = findViewById<TextView>(R.id.tvStatTotal)
        val btnPredictor = findViewById<Button>(R.id.btnPredictor)
        val rvItems = findViewById<RecyclerView>(R.id.rvBillItems)

        tvStatCovered.text = "₹%.2f".format(claim.coveredAmount)
        tvStatLiability.text = "₹%.2f".format(claim.patientLiability)
        tvStatTotal.text = "₹%.2f".format(claim.amount.toDoubleOrNull() ?: 0.0)

        // Update pills
        val tvStatusBill = findViewById<TextView>(R.id.tvStatusBill)
        val tvStatusPolicy = findViewById<TextView>(R.id.tvStatusPolicy)
        
        if (claim.isBillLoaded) {
            tvStatusBill.text = "✅ Hospital Bill Loaded"
            tvStatusBill.alpha = 1.0f
        } else {
            tvStatusBill.text = "○ Bill Pending"
            tvStatusBill.alpha = 0.5f
        }

        if (claim.isPolicyLoaded) {
            tvStatusPolicy.text = "✅ Insurance Policy Loaded"
            tvStatusPolicy.alpha = 1.0f
        } else {
            tvStatusPolicy.text = "○ Policy Pending"
            tvStatusPolicy.alpha = 0.5f
        }

        // --- Final Summary Update ---
        val cardSummary = findViewById<androidx.cardview.widget.CardView>(R.id.cardSummary)
        val tvSummaryAmount = findViewById<TextView>(R.id.tvSummaryAmount)
        val tvSummaryTitle = findViewById<TextView>(R.id.tvSummaryTitle)

        val role = prefManager.getRole() ?: "PATIENT"
        if (claim.status == "ADJUDICATED") {
            cardSummary.visibility = View.VISIBLE
            if (role == "PATIENT") {
                tvSummaryTitle.text = "FINAL PAYMENT SUMMARY"
                tvSummaryAmount.text = "You need to pay ₹%.2f".format(claim.patientLiability)
            } else {
                tvSummaryTitle.text = "CLAIM ADJUDICATION SUMMARY"
                tvSummaryAmount.text = "Patient owes: ₹%.2f".format(claim.patientLiability)
            }
        } else {
            cardSummary.visibility = View.GONE
        }

        // RecyclerView for items
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = BillItemAdapter(claim.items)

        // Show/Hide predictor button based on role and status
        if (role == "INSURER" && claim.status == "PENDING") {
            btnPredictor.visibility = View.VISIBLE
            btnPredictor.text = "Calculate Financial Predictor"
        } else if (role == "PATIENT" && claim.status == "PENDING") {
            btnPredictor.visibility = View.VISIBLE
            btnPredictor.text = "Predict My Out-of-Pocket Cost"
        } else {
            btnPredictor.visibility = View.GONE
        }

        // Auto-trigger predictor for Patients if the claim is still pending
        if (role == "PATIENT" && claim.status == "PENDING" && !isAutoPredictStarted) {
            isAutoPredictStarted = true
            startFinancialPredictor()
        }
    }

    private fun startFinancialPredictor() {
        if (currentRole != "PATIENT" && currentRole != "INSURER") {
            Toast.makeText(this, "Only patients and insurers can run prediction.", Toast.LENGTH_SHORT).show()
            return
        }

        val claim = currentClaim ?: return
        val progressBar = findViewById<ProgressBar>(R.id.progressAdjudication)
        val btnPredictor = findViewById<Button>(R.id.btnPredictor)

        progressBar.visibility = View.VISIBLE
        btnPredictor.isEnabled = false

        lifecycleScope.launch {
            try {
                // Fetch actual policy from the CURRENT USER (Insurer)
                val currentUid = auth.currentUser?.uid ?: ""
                firebaseHelper.getPolicy(currentUid, { policy ->
                    val policyRules = policy?.coverageDetails ?: "Standard Policy: 20% Copay applies to all items."
                    
                    lifecycleScope.launch {
                        try {
                            val adjudicatedItems = geminiHelper.adjudicateItemized(claim.items, policyRules)
                            
                            if (adjudicatedItems.isNotEmpty()) {
                                val totalCovered = adjudicatedItems.sumOf { it.coveredAmount }
                                val totalBill = claim.amount.toDoubleOrNull() ?: 0.0
                                val liability = totalBill - totalCovered
                                
                                saveResults(adjudicatedItems, totalCovered, liability)
                            } else {
                                Toast.makeText(this@ClaimDetailActivity, "AI Evaluation returned empty results.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@ClaimDetailActivity, "Evaluation Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            progressBar.visibility = View.GONE
                            btnPredictor.isEnabled = true
                        }
                    }
                }, {
                    progressBar.visibility = View.GONE
                    btnPredictor.isEnabled = true
                    Toast.makeText(this@ClaimDetailActivity, "Could not fetch policy rules.", Toast.LENGTH_SHORT).show()
                })
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnPredictor.isEnabled = true
                Toast.makeText(this@ClaimDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                )).addOnSuccessListener {
                    Toast.makeText(this, "Success! Adjudication Complete", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun linkClaimToPatient() {
        if (currentRole != "HOSPITAL") {
            Toast.makeText(this, "Only hospitals can link a claim to a patient.", Toast.LENGTH_SHORT).show()
            return
        }

        val etTargetId = findViewById<android.widget.EditText>(R.id.etTargetPatientId)
        val targetId = etTargetId.text.toString().trim()
        val cid = claimId ?: return

        if (targetId.isEmpty()) {
            Toast.makeText(this, "Please enter a Patient ID", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Verifying Patient ID...", Toast.LENGTH_SHORT).show()
        
        firebaseHelper.getUserIdByCustomId(targetId, { patientUid ->
            if (patientUid != null) {
                firebaseHelper.updateClaimLinkage(cid, patientUid, targetId, {
                    Toast.makeText(this, "Bill Successfully Linked to $targetId", Toast.LENGTH_LONG).show()
                    etTargetId.text.clear()
                    fetchClaimDetails() // Refresh UI
                }, {
                    Toast.makeText(this, "Link Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                })
            } else {
                Toast.makeText(this, "Patient ID not found. Verify the ID and try again.", Toast.LENGTH_LONG).show()
            }
        }, {
            Toast.makeText(this, "Lookup Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }
}
