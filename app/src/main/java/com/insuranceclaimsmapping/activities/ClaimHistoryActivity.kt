package com.insuranceclaimsmapping.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.adapters.ClaimAdapter
import com.insuranceclaimsmapping.databinding.ActivityClaimHistoryBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim

class ClaimHistoryActivity : AppCompatActivity() {
    private val firebaseHelper by lazy { FirebaseHelper() }
    private lateinit var adapter: ClaimAdapter
    private lateinit var binding: ActivityClaimHistoryBinding
    private var allClaimsList = listOf<Claim>()
    private var archivedIds = mutableSetOf<String>()

    private var currentStatusFilter = "ALL"
    private var currentSortIndex = 0
    private var currentSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClaimHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarHistory)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Claim History"
        binding.toolbarHistory.setNavigationOnClickListener { finish() }

        binding.rvClaims.layoutManager = LinearLayoutManager(this)
        adapter = ClaimAdapter(emptyList())
        binding.rvClaims.adapter = adapter

        // --- Search ---
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // --- Filter Chips ---
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentStatusFilter = when {
                checkedIds.contains(R.id.chipPending)  -> "PENDING"
                checkedIds.contains(R.id.chipApproved) -> "APPROVED"
                checkedIds.contains(R.id.chipRejected) -> "REJECTED"
                else -> "ALL"
            }
            applyFilters()
        }

        // --- Sort Spinner ---
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSortIndex = position
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- Swipe to Archive ---
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val currentList = adapter.getCurrentList()
                if (position >= currentList.size) return
                val archivedClaim = currentList[position]
                archivedIds.add(archivedClaim.id)
                applyFilters()
                Snackbar.make(binding.rvClaims, "Claim archived", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        archivedIds.remove(archivedClaim.id)
                        applyFilters()
                    }.show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvClaims)

        fetchClaims()
        setupBranding()
    }

    private fun applyFilters() {
        var filtered = allClaimsList.filter { it.id !in archivedIds }

        // Status filter
        if (currentStatusFilter != "ALL") {
            filtered = filtered.filter { it.status.equals(currentStatusFilter, ignoreCase = true) }
        }

        // Search filter
        if (currentSearchQuery.isNotEmpty()) {
            filtered = filtered.filter { claim ->
                claim.customPatientId.contains(currentSearchQuery, ignoreCase = true) ||
                claim.name.contains(currentSearchQuery, ignoreCase = true) ||
                claim.hospital.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        // Sort
        filtered = when (currentSortIndex) {
            0 -> filtered.sortedByDescending { it.timestamp }
            1 -> filtered.sortedBy { it.timestamp }
            2 -> filtered.sortedByDescending { it.amount.toDoubleOrNull() ?: 0.0 }
            3 -> filtered.sortedBy { it.amount.toDoubleOrNull() ?: 0.0 }
            4 -> filtered.sortedBy { it.hospital }
            else -> filtered
        }

        adapter.updateData(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupBranding() {
        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"

        val (bg, primaryColorRes, statusBarColorRes) = when (role) {
            "HOSPITAL" -> Triple(R.color.green_light, R.color.hospital_primary, R.color.hospital_dark)
            "INSURER"  -> Triple(R.color.blue_light,  R.color.insurer_primary,  R.color.insurer_dark)
            "PATIENT"  -> Triple(R.color.yellow_light, R.color.patient_primary, R.color.patient_dark)
            else       -> Triple(R.color.gray,         R.color.default_primary, R.color.default_dark)
        }

        binding.root.setBackgroundResource(bg)
        binding.toolbarHistory.setBackgroundColor(getColor(primaryColorRes))
        window.statusBarColor = getColor(statusBarColorRes)
    }

    private fun fetchClaims() {
        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        firebaseHelper.getClaimsByRole(role, uid, { claims ->
            if (isFinishing || isDestroyed) return@getClaimsByRole
            allClaimsList = claims
            applyFilters()
        }, {
            if (isFinishing || isDestroyed) return@getClaimsByRole
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }
}
