package com.insuranceclaimsmapping.activities

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.adapters.ClaimAdapter
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim
import android.text.Editable
import android.text.TextWatcher

class ClaimHistoryActivity : AppCompatActivity() {
    private val firebaseHelper by lazy { FirebaseHelper() }
    private lateinit var adapter: ClaimAdapter
    private var allClaimsList = listOf<Claim>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_claim_history)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarHistory)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Claim History"
        toolbar.setNavigationOnClickListener { finish() }

        val rvClaims = findViewById<RecyclerView>(R.id.rvClaims)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        rvClaims.layoutManager = LinearLayoutManager(this)
        adapter = ClaimAdapter(emptyList())
        rvClaims.adapter = adapter

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterClaims(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fetchClaims(tvEmpty)
        setupBranding(toolbar)
    }

    private fun setupBranding(toolbar: androidx.appcompat.widget.Toolbar) {
        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        val root = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)

        val (bg, primaryColor, statusBarColor) = when (role) {
            "HOSPITAL" -> Triple(R.color.green_light, android.graphics.Color.parseColor("#2E7D32"), android.graphics.Color.parseColor("#1B5E20"))
            "INSURER" -> Triple(R.color.blue_light, android.graphics.Color.parseColor("#1565C0"), android.graphics.Color.parseColor("#0D47A1"))
            "PATIENT" -> Triple(R.color.yellow_light, android.graphics.Color.parseColor("#F57F17"), android.graphics.Color.parseColor("#E65100"))
            else -> Triple(R.color.gray, android.graphics.Color.parseColor("#00796B"), android.graphics.Color.parseColor("#004D40"))
        }

        root?.setBackgroundResource(bg)
        toolbar.setBackgroundColor(primaryColor)
        window.statusBarColor = statusBarColor
    }

    private fun fetchClaims(tvEmpty: TextView) {
        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        firebaseHelper.getClaimsByRole(role, uid, { claims ->
            allClaimsList = claims
            if (claims.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                tvEmpty.visibility = View.GONE
                adapter.updateData(claims)
            }
        }, {
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun filterClaims(query: String) {
        val filteredList = if (query.isEmpty()) {
            allClaimsList
        } else {
            allClaimsList.filter { claim ->
                claim.customPatientId.contains(query, ignoreCase = true) ||
                claim.name.contains(query, ignoreCase = true) ||
                claim.hospital.contains(query, ignoreCase = true)
            }
        }
        adapter.updateData(filteredList)
        findViewById<TextView>(R.id.tvEmpty).visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }
}
