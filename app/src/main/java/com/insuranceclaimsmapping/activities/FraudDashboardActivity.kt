package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.adapters.ClaimAdapter
import com.insuranceclaimsmapping.databinding.ActivityFraudDashboardBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim

class FraudDashboardActivity : AppCompatActivity() {

    private val firebaseHelper = FirebaseHelper()
    private lateinit var binding: ActivityFraudDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFraudDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarFraud)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Fraud Dashboard"
        binding.toolbarFraud.setNavigationOnClickListener { finish() }
        binding.toolbarFraud.setBackgroundColor(getColor(R.color.status_rejected))
        window.statusBarColor = getColor(R.color.fraud_title)

        binding.rvFlaggedClaims.layoutManager = LinearLayoutManager(this)

        firebaseHelper.getFlaggedClaims({ claims: List<Claim> ->
            if (isFinishing || isDestroyed) return@getFlaggedClaims
            binding.tvFraudCount.text = "${claims.size} flagged claim(s) detected"
            if (claims.isEmpty()) {
                binding.tvFraudEmpty.visibility = View.VISIBLE
                binding.rvFlaggedClaims.visibility = View.GONE
            } else {
                binding.tvFraudEmpty.visibility = View.GONE
                binding.rvFlaggedClaims.visibility = View.VISIBLE
                val adapter = ClaimAdapter(claims)
                adapter.setOnItemClickListener { claim ->
                    startActivity(Intent(this, ClaimDetailActivity::class.java).apply {
                        putExtra("claimId", claim.id)
                    })
                }
                binding.rvFlaggedClaims.adapter = adapter
            }
        }, { e: Exception ->
            if (isFinishing || isDestroyed) return@getFlaggedClaims
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        })
    }
}
