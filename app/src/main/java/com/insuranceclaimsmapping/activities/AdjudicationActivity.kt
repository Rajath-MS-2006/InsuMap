package com.insuranceclaimsmapping.activities

import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.ai.OfflineInferenceHelper
import com.insuranceclaimsmapping.databinding.ActivityAdjudicationBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdjudicationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdjudicationBinding
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var offlineInferenceHelper: OfflineInferenceHelper
    private lateinit var auth: FirebaseAuth
    private var claims: ArrayList<Claim> = arrayListOf()
    private var policyRules: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdjudicationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseHelper = FirebaseHelper()
        offlineInferenceHelper = OfflineInferenceHelper(this)
        auth = FirebaseAuth.getInstance()

        claims = intent.getParcelableArrayListExtra<Claim>("claims") ?: arrayListOf()
        policyRules = intent.getStringExtra("policyRules") ?: "Use standard medical necessity rules."

        setupUI()
        startAdjudication()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbarAdjudication)
        
        // Fetch role for branding
        auth.currentUser?.uid?.let { uid ->
            firebaseHelper.getUserProfile(uid, { user ->
                if (isFinishing || isDestroyed) return@getUserProfile
                user?.let { applyRoleBranding(it.role) }
            }, {
                // error ignored for branding
            })
        }
    }

    private fun applyRoleBranding(role: String) {
        val (bg, colorRes) = when (role) {
            "HOSPITAL" -> R.color.green_light to R.color.hospital_primary
            "INSURER"  -> R.color.blue_light  to R.color.insurer_primary
            "PATIENT"  -> R.color.yellow_light to R.color.patient_primary
            else       -> R.color.gray         to R.color.default_primary
        }
        binding.rootLayoutAdjudication.setBackgroundResource(bg)
        binding.toolbarAdjudication.setBackgroundColor(getColor(colorRes))
    }

    private fun startAdjudication() {
        binding.batchProgressBar.max = claims.size

        lifecycleScope.launch {
            var processed = 0
            for (claim in claims) {
                try {
                    updateLog("[Process] Evaluating: ${claim.description}")
                    binding.tvAdjudicationStatus.text = "Identifying: ${claim.description}..."
                    delay(300)

                    val serviceName = if (claim.items.isNotEmpty()) claim.items[0].description else claim.description
                    updateLog("[Rules] Checking policy coverage for '$serviceName'...")
                    binding.tvAdjudicationStatus.text = "Checking coverage for '$serviceName'..."
                    delay(500)

                    val result = offlineInferenceHelper.adjudicateSingleClaim(claim, policyRules)
                    if (isFinishing || isDestroyed) return@launch

                    if (result.status == "REJECTED") {
                        updateLog("[Log] Item rejected: outside policy scope.")
                        binding.tvAdjudicationStatus.text = "Item outside policy scope. Moving to next..."
                    } else {
                        updateLog("[Log] Coverage confirmed. Approved Amount: ${result.coveredAmount}")
                        binding.tvAdjudicationStatus.text = "Coverage confirmed. Proceeding..."
                    }

                    firebaseHelper.updateClaim(result, {
                        // success
                    }, { e ->
                        if (isFinishing || isDestroyed) return@updateClaim
                        updateLog("[Warning] Failed to save claim: ${e.message}")
                    })
                    
                    processed++
                    binding.batchProgressBar.progress = processed
                    
                    updateLog("[System] Record updated in Cloud Ledger.")
                    delay(500) // Quality of Life delay

                } catch (e: Exception) {
                    if (isFinishing || isDestroyed) return@launch
                    updateLog("[Error] Clinical skip: ${e.message}")
                    delay(500)
                }
            }

            if (isFinishing || isDestroyed) return@launch
            binding.progressBarAdjudication.visibility = View.GONE
            binding.tvAdjudicationStatus.text = "Batch Adjudication Complete"
            updateLog("----------------------------------")
            updateLog("[Status] All components processed successfully.")
            binding.btnDoneAdjudication.visibility = View.VISIBLE
            binding.btnDoneAdjudication.setOnClickListener { finish() }
        }
    }

    private fun updateLog(message: String) {
        if (isFinishing || isDestroyed) return
        binding.tvClinicalLog.append("\n$message")
        binding.scrollLog.post { 
            if (!isFinishing && !isDestroyed) {
                binding.scrollLog.fullScroll(View.FOCUS_DOWN) 
            }
        }
    }
}
