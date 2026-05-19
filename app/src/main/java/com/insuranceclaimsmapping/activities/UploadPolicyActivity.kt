package com.insuranceclaimsmapping.activities

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.ai.OfflineInferenceHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Policy
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UploadPolicyActivity : AppCompatActivity() {
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var offlineInferenceHelper: OfflineInferenceHelper
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_policy)

        firebaseHelper = FirebaseHelper()
        offlineInferenceHelper = OfflineInferenceHelper(this)
        auth = FirebaseAuth.getInstance()

        setupUI()
    }

    private fun setupUI() {
        val rootLayout = findViewById<View>(R.id.rootLayoutPolicy)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarPolicy)
        val btnViewPrevious = findViewById<android.widget.Button>(R.id.btnViewPreviousPolicy)
        val btnViewHistory = findViewById<android.widget.Button>(R.id.btnViewPolicyHistory)
        val llSelect = findViewById<LinearLayout>(R.id.llSelectPolicy)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        auth.currentUser?.uid?.let { uid ->
            firebaseHelper.getUserProfile(uid, { user ->
                if (isFinishing || isDestroyed) return@getUserProfile
                user?.let { applyRoleStyling(it.role, rootLayout, toolbar) }
            }, { e -> 
                if (isFinishing || isDestroyed) return@getUserProfile
                android.util.Log.w("UploadPolicy", "Failed to load role: ${e.message}") 
            })
        }

        llSelect.setOnClickListener { pdfLauncher.launch("application/pdf") }

        val insurerId = auth.currentUser?.uid ?: ""
        firebaseHelper.getPolicy(insurerId, { policy ->
            if (isFinishing || isDestroyed) return@getPolicy
            if (policy != null && policy.coverageDetails.isNotEmpty()) {
                btnViewPrevious.apply {
                    visibility = View.VISIBLE
                    text = "View Active Policy (v${policy.version})"
                    setOnClickListener { showPolicyDialog("Version ${policy.version}", policy.coverageDetails) }
                }
                btnViewHistory.visibility = View.VISIBLE
                btnViewHistory.setOnClickListener { showPolicyHistory(insurerId) }
            }
        }, { e -> 
            if (isFinishing || isDestroyed) return@getPolicy
            android.util.Log.w("UploadPolicy", "Failed to load policy: ${e.message}") 
        })
    }

    private fun showPolicyHistory(insurerId: String) {
        firebaseHelper.getPolicyHistory(insurerId, { historyList: List<Policy> ->
            if (isFinishing || isDestroyed) return@getPolicyHistory
            if (historyList.isEmpty()) {
                Toast.makeText(this, "No previous versions found.", Toast.LENGTH_SHORT).show()
                return@getPolicyHistory
            }
            val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val items = historyList.map { p ->
                "Version ${p.version} — ${fmt.format(Date(p.uploadedAt))}"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Policy Version History")
                .setItems(items as Array<CharSequence>) { _, idx ->
                    showPolicyDialog("Version ${historyList[idx].version}", historyList[idx].coverageDetails)
                }
                .setNegativeButton("Close", null)
                .show()
        }, { e: Exception ->
            if (isFinishing || isDestroyed) return@getPolicyHistory
            Toast.makeText(this, "Failed to load history: ${e.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun applyRoleStyling(role: String, root: View, toolbar: androidx.appcompat.widget.Toolbar) {
        val (bg, colorRes) = when (role) {
            "HOSPITAL" -> R.color.green_light to R.color.hospital_primary
            "INSURER"  -> R.color.blue_light  to R.color.insurer_primary
            "PATIENT"  -> R.color.yellow_light to R.color.patient_primary
            else       -> R.color.gray         to R.color.default_primary
        }
        root.setBackgroundResource(bg)
        toolbar.setBackgroundColor(getColor(colorRes))
    }

    private fun showPolicyDialog(title: String, details: String) {
        if (isFinishing || isDestroyed) return
        val rulesView = TextView(this).apply {
            text = details
            setPadding(60, 40, 60, 40)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(android.graphics.Color.DKGRAY)
            movementMethod = android.text.method.ScrollingMovementMethod()
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(rulesView)
            .setPositiveButton("Close", null)
            .show()
    }

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processPolicy(it) }
    }

    private fun processPolicy(uri: Uri) {
        val progressSection = findViewById<LinearLayout>(R.id.llProgressSection)
        val tvStatus = findViewById<TextView>(R.id.tvPolicyStatus)

        progressSection.visibility = View.VISIBLE

        lifecycleScope.launch {
            tvStatus.text = "Initializing logical scan of PDF document..."
            kotlinx.coroutines.delay(1000)
            tvStatus.text = "Analyzing coverage rules and benefit limits..."
            kotlinx.coroutines.delay(1500)
            tvStatus.text = "Sanitizing extracted clinical data..."

            val extractionResult = offlineInferenceHelper.extractPolicyDetails(uri)

            if (extractionResult != null) {
                tvStatus.text = "Extraction complete. Registering policy..."
                kotlinx.coroutines.delay(800)

                val insurerId = auth.currentUser?.uid ?: run {
                    progressSection.visibility = View.GONE
                    Toast.makeText(this@UploadPolicyActivity, "Not logged in.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val policy = Policy(
                    insurerId = insurerId,
                    name = "Active Policy",
                    pdfUrl = "LOCAL",
                    copayPercentage = 20.0,
                    deductibleLimit = 500.0,
                    coverageDetails = extractionResult
                )

                // Use savePolicyWithHistory to version the policy
                firebaseHelper.savePolicyWithHistory(policy, {
                    if (isFinishing || isDestroyed) return@savePolicyWithHistory
                    progressSection.visibility = View.GONE
                    Toast.makeText(this@UploadPolicyActivity, "Policy Updated Successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }, { e: Exception ->
                    if (isFinishing || isDestroyed) return@savePolicyWithHistory
                    progressSection.visibility = View.GONE
                    Toast.makeText(this@UploadPolicyActivity, "Save Failed: ${e.message}", Toast.LENGTH_LONG).show()
                })
            } else {
                tvStatus.text = "Extraction failed. Please verify PDF format."
                kotlinx.coroutines.delay(2000)
                progressSection.visibility = View.GONE
            }
        }
    }
}
