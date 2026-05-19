package com.insuranceclaimsmapping.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ActivityAppealClaimBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper

class AppealClaimActivity : AppCompatActivity() {

    private val firebaseHelper = FirebaseHelper()
    private lateinit var binding: ActivityAppealClaimBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppealClaimBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarAppeal)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Appeal Claim"
        binding.toolbarAppeal.setBackgroundColor(getColor(R.color.patient_primary))
        window.statusBarColor = getColor(R.color.patient_dark)
        binding.toolbarAppeal.setNavigationOnClickListener { finish() }

        val claimId = intent.getStringExtra("claimId") ?: run { finish(); return }
        val claimDesc = intent.getStringExtra("claimDesc") ?: ""
        val claimAmount = intent.getStringExtra("claimAmount") ?: ""

        binding.tvAppealClaimInfo.text = "Claim: $claimDesc\nAmount: ₹$claimAmount\nStatus: REJECTED"

        binding.btnSubmitAppeal.setOnClickListener {
            val note = binding.etAppealNote.text.toString().trim()
            if (note.isEmpty()) {
                Toast.makeText(this, "Please provide a reason for your appeal", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnSubmitAppeal.isEnabled = false
            firebaseHelper.addClaimAppeal(claimId, note, {
                if (isFinishing || isDestroyed) return@addClaimAppeal
                Toast.makeText(this, "Appeal submitted successfully. Your insurer will review it.", Toast.LENGTH_LONG).show()
                finish()
            }, {
                if (isFinishing || isDestroyed) return@addClaimAppeal
                binding.btnSubmitAppeal.isEnabled = true
                Toast.makeText(this, "Failed to submit appeal: ${it.message}", Toast.LENGTH_SHORT).show()
            })
        }
    }
}
