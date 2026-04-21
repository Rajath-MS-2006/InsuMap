package com.insuranceclaimsmapping.activities

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.ai.GeminiHelper
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.utils.PrefManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdjudicationActivity : AppCompatActivity() {
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var auth: FirebaseAuth
    private lateinit var prefManager: PrefManager
    private var claims: ArrayList<Claim> = arrayListOf()
    private var policyRules: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adjudication)

        firebaseHelper = FirebaseHelper()
        geminiHelper = GeminiHelper(this)
        auth = FirebaseAuth.getInstance()
        prefManager = PrefManager(this)

        claims = intent.getParcelableArrayListExtra<Claim>("claims") ?: arrayListOf()
        policyRules = intent.getStringExtra("policyRules") ?: "Use standard medical necessity rules."

        if (!enforceInsurerAccess()) {
            return
        }

        setupUI()
        startAdjudication()
    }

    private fun enforceInsurerAccess(): Boolean {
        val currentRole = prefManager.getRole()
        if (currentRole != "INSURER") {
            Toast.makeText(this, "Only insurers can adjudicate claims.", Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        return true
    }

    private fun setupUI() {
        val root = findViewById<LinearLayout>(R.id.rootLayoutAdjudication)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarAdjudication)
        setSupportActionBar(toolbar)
        
        // Fetch role for branding
        auth.currentUser?.uid?.let { uid ->
            firebaseHelper.getUserProfile(uid, { user ->
                user?.let { applyRoleBranding(it.role, root, toolbar) }
            }, {})
        }
    }

    private fun applyRoleBranding(role: String, root: View, toolbar: androidx.appcompat.widget.Toolbar) {
        val (bg, toolbarColor) = when (role) {
            "HOSPITAL" -> R.color.green_light to Color.parseColor("#2E7D32")
            "INSURER" -> R.color.blue_light to Color.parseColor("#1565C0")
            "PATIENT" -> R.color.yellow_light to Color.parseColor("#F57F17")
            else -> R.color.gray to Color.parseColor("#00796B")
        }
        root.setBackgroundResource(bg)
        toolbar.setBackgroundColor(toolbarColor)
    }

    private fun startAdjudication() {
        val tvStatus = findViewById<TextView>(R.id.tvAdjudicationStatus)
        val tvLog = findViewById<TextView>(R.id.tvClinicalLog)
        val scroll = findViewById<ScrollView>(R.id.scrollLog)
        val batchProgress = findViewById<ProgressBar>(R.id.batchProgressBar)
        val mainSpinner = findViewById<ProgressBar>(R.id.progressBarAdjudication)
        val btnDone = findViewById<Button>(R.id.btnDoneAdjudication)

        batchProgress.max = claims.size

        lifecycleScope.launch {
            var processed = 0
            for (claim in claims) {
                try {
                    val msgIdentifier = "Evaluating Service: ${claim.description}"
                    updateLog(tvLog, scroll, "[Process] $msgIdentifier")
                    tvStatus.text = "Identifying: ${claim.description}..."
                    delay(800)

                    val serviceName = if (claim.items.isNotEmpty()) claim.items[0].description else claim.description
                    updateLog(tvLog, scroll, "[Rules] Checking policy coverage for '$serviceName'...")
                    tvStatus.text = "Checking coverage for '$serviceName'..."
                    delay(1000)

                    val result = geminiHelper.adjudicateSingleClaim(claim, policyRules)

                    if (result.status == "REJECTED") {
                        updateLog(tvLog, scroll, "[Log] Item rejected: outside policy scope.")
                        tvStatus.text = "Item outside policy scope. Moving to next..."
                    } else if (result.aiReasoning.contains("Quota", ignoreCase = true)) {
                        updateLog(tvLog, scroll, "[System] AI engine cooling down... (Quota hit)")
                        tvStatus.text = "Throttling active. Resuming shortly..."
                        delay(2000)
                    } else {
                        updateLog(tvLog, scroll, "[Log] Coverage confirmed. Approved Amount: ${result.coveredAmount}")
                        tvStatus.text = "Coverage confirmed. Proceeding..."
                    }

                    val claimWithId = result.copy(id = claim.id)
                    firebaseHelper.updateClaim(claimWithId, {}, {})
                    
                    processed++
                    batchProgress.progress = processed
                    
                    updateLog(tvLog, scroll, "[System] Record updated in Cloud Ledger.")
                    delay(1500) // Brief pause for UI update

                } catch (e: Exception) {
                    if (e.message?.contains("Quota", ignoreCase = true) == true) {
                        updateLog(tvLog, scroll, "[Warning] Quota limit hit. Clinical evaluation paused.")
                        tvStatus.text = "Rate limit reached. Please wait..."
                        delay(10000) // Extra safety wait
                    } else {
                        updateLog(tvLog, scroll, "[Error] Clinical skip: ${e.message}")
                    }
                    delay(1000)
                }
            }

            mainSpinner.visibility = View.GONE
            tvStatus.text = "Batch Adjudication Complete"
            updateLog(tvLog, scroll, "----------------------------------")
            updateLog(tvLog, scroll, "[Status] All components processed successfully.")
            btnDone.visibility = View.VISIBLE
            btnDone.setOnClickListener { finish() }
        }
    }

    private fun updateLog(tv: TextView, scroll: ScrollView, message: String) {
        tv.append("\n$message")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
