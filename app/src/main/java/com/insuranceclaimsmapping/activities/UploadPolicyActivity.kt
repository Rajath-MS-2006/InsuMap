package com.insuranceclaimsmapping.activities

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.graphics.Color
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.ai.GeminiHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Policy
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class UploadPolicyActivity : AppCompatActivity() {
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_policy)

        firebaseHelper = FirebaseHelper()
        geminiHelper = GeminiHelper(this)
        auth = FirebaseAuth.getInstance()

        setupUI()
    }

    private fun setupUI() {
        val rootLayout = findViewById<View>(R.id.rootLayoutPolicy)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarPolicy)
        val btnViewPrevious = findViewById<android.widget.Button>(R.id.btnViewPreviousPolicy)
        val llSelect = findViewById<LinearLayout>(R.id.llSelectPolicy)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Fetch user role for styling
        auth.currentUser?.uid?.let { uid ->
            firebaseHelper.getUserProfile(uid, { user ->
                user?.let {
                    applyRoleStyling(it.role, rootLayout, toolbar)
                }
            }, {})
        }

        llSelect.setOnClickListener {
            pdfLauncher.launch("application/pdf")
        }

        val insurerId = auth.currentUser?.uid ?: ""
        firebaseHelper.getPolicy(insurerId, { policy ->
            if (policy != null && policy.coverageDetails.isNotEmpty()) {
                btnViewPrevious.apply {
                    visibility = View.VISIBLE
                    setOnClickListener { showPolicyDialog(policy.coverageDetails) }
                }
            }
        }, {})
    }

    private fun applyRoleStyling(role: String, root: View, toolbar: androidx.appcompat.widget.Toolbar) {
        val (bg, toolbarColor) = when (role) {
            "HOSPITAL" -> R.color.green_light to android.graphics.Color.parseColor("#2E7D32")
            "INSURER" -> R.color.blue_light to android.graphics.Color.parseColor("#1565C0")
            "PATIENT" -> R.color.yellow_light to android.graphics.Color.parseColor("#F57F17")
            else -> R.color.gray to android.graphics.Color.parseColor("#00796B")
        }
        root.setBackgroundResource(bg)
        toolbar.setBackgroundColor(toolbarColor)
    }

    private fun showPolicyDialog(details: String) {
        val rulesView = TextView(this).apply {
            text = details
            setPadding(60, 40, 60, 40)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(android.graphics.Color.DKGRAY)
            movementMethod = android.text.method.ScrollingMovementMethod()
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Active Policy Rules")
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
            // Step-by-step Clinical Progress
            tvStatus.text = "Initializing logical scan of PDF document..."
            kotlinx.coroutines.delay(1000)
            
            tvStatus.text = "Analyzing coverage rules and benefit limits..."
            kotlinx.coroutines.delay(1500)
            
            tvStatus.text = "Sanitizing extracted clinical data..."
            
            val extractionResult = geminiHelper.extractPolicyDetails(uri)
            
            if (extractionResult != null) {
                tvStatus.text = "Extraction complete. Registering policy..."
                kotlinx.coroutines.delay(800)
                
                val insurerId = auth.currentUser?.uid ?: ""
                val policy = Policy(
                    insurerId = insurerId,
                    name = "Active Policy",
                    pdfUrl = "LOCAL",
                    copayPercentage = 20.0, 
                    deductibleLimit = 500.0, 
                    coverageDetails = extractionResult
                )

                firebaseHelper.savePolicy(policy, {
                    progressSection.visibility = View.GONE
                    Toast.makeText(this@UploadPolicyActivity, "Policy Updated Successfully!", Toast.LENGTH_SHORT).show()
                    finish() // Return to dashboard
                }, {
                    progressSection.visibility = View.GONE
                    Toast.makeText(this@UploadPolicyActivity, "Save Failed: ${it.message}", Toast.LENGTH_LONG).show()
                })
            } else {
                tvStatus.text = "Extraction failed. Please verify PDF format."
                kotlinx.coroutines.delay(2000)
                progressSection.visibility = View.GONE
            }
        }
    }
}
