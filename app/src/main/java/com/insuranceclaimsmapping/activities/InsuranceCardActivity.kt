package com.insuranceclaimsmapping.activities

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ActivityInsuranceCardBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.google.firebase.auth.FirebaseAuth

class InsuranceCardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInsuranceCardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsuranceCardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarCard)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Insurance Card"
        binding.toolbarCard.setBackgroundColor(getColor(R.color.patient_primary))
        window.statusBarColor = getColor(R.color.patient_dark)
        binding.toolbarCard.setNavigationOnClickListener { finish() }

        val firebaseHelper = FirebaseHelper()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firebaseHelper.getUserProfile(uid, { user ->
            if (isFinishing || isDestroyed) return@getUserProfile
            if (user != null) {
                binding.tvCardName.text = user.displayName.ifEmpty { "Member" }
                binding.tvCardPatientId.text = "Member ID: ${user.customId}"

                val providerId = user.insuranceProviderId
                if (providerId.isNotEmpty()) {
                    binding.tvCardProviderId.text = "Provider ID: $providerId"
                    firebaseHelper.getUserIdByCustomId(providerId, { insurerUid ->
                        if (isFinishing || isDestroyed) return@getUserIdByCustomId
                        if (insurerUid != null) {
                            firebaseHelper.getUserProfile(insurerUid, { insurer ->
                                if (isFinishing || isDestroyed) return@getUserProfile
                                binding.tvCardProvider.text = insurer?.displayName?.ifEmpty { "Insurance Provider" } ?: "Insurance Provider"
                            }, { e -> Log.w("InsuranceCard", "Failed to load insurer profile: ${e.message}") })

                            firebaseHelper.getPolicy(insurerUid, { policy ->
                                if (isFinishing || isDestroyed) return@getPolicy
                                if (policy != null) {
                                    binding.tvCardCoverage.text = "Copay: ${policy.copayPercentage.toInt()}%  |  Deductible: ₹${policy.deductibleLimit.toInt()}"
                                    binding.tvCardStatus.text = "ACTIVE — v${policy.version}"
                                } else {
                                    binding.tvCardCoverage.text = "No policy linked"
                                    binding.tvCardStatus.text = "INACTIVE"
                                }
                            }, { e -> Log.w("InsuranceCard", "Failed to load policy: ${e.message}") })
                        } else {
                            binding.tvCardProvider.text = "Provider not found"
                            binding.tvCardStatus.text = "INACTIVE"
                        }
                    }, {
                        if (isFinishing || isDestroyed) return@getUserIdByCustomId
                        Toast.makeText(this, "Error looking up provider", Toast.LENGTH_SHORT).show()
                    })
                } else {
                    binding.tvCardProvider.text = "No provider linked"
                    binding.tvCardProviderId.text = "Go to Profile → set Insurance Provider ID"
                    binding.tvCardStatus.text = "INACTIVE"
                }
            }
        }, {
            if (isFinishing || isDestroyed) return@getUserProfile
            Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
        })
    }
}
