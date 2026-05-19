package com.insuranceclaimsmapping.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.adapters.ClaimAdapter
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.Claim

class ClaimHistoryActivity : AppCompatActivity() {
    private val firebaseHelper by lazy { FirebaseHelper() }
    private lateinit var adapter: ClaimAdapter
    private var allClaimsList = listOf<Claim>()
    private var archivedIds = mutableSetOf<String>()

    // Current filter and sort state
    private var currentStatusFilter = "ALL"
    private var currentSortIndex = 0
    private var currentSearchQuery = ""

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
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFilter)
        val spinnerSort = findViewById<Spinner>(R.id.spinnerSort)

        rvClaims.layoutManager = LinearLayoutManager(this)
        adapter = ClaimAdapter(emptyList())
        rvClaims.adapter = adapter

        // --- Search ---
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                applyFilters(tvEmpty)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // --- Filter Chips ---
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentStatusFilter = when {
                checkedIds.contains(R.id.chipPending)  -> "PENDING"
                checkedIds.contains(R.id.chipApproved) -> "APPROVED"
                checkedIds.contains(R.id.chipRejected) -> "REJECTED"
                else -> "ALL"
            }
            applyFilters(tvEmpty)
        }

        // --- Sort Spinner ---
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSortIndex = position
                applyFilters(tvEmpty)
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
                applyFilters(tvEmpty)
                Snackbar.make(rvClaims, "Claim archived", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        archivedIds.remove(archivedClaim.id)
                        applyFilters(tvEmpty)
                    }.show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvClaims)

        fetchClaims(tvEmpty)
        setupBranding(toolbar)
    }

    private fun applyFilters(tvEmpty: TextView) {
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
            2 -> filtered.sortedByDescending { it.amount }
            3 -> filtered.sortedBy { it.amount }
            4 -> filtered.sortedBy { it.hospital }
            else -> filtered
        }

        adapter.updateData(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupBranding(toolbar: androidx.appcompat.widget.Toolbar) {
        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        val root = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)

        val (bg, primaryColor, statusBarColor) = when (role) {
            "HOSPITAL" -> Triple(R.color.green_light, android.graphics.Color.parseColor("#2E7D32"), android.graphics.Color.parseColor("#1B5E20"))
            "INSURER"  -> Triple(R.color.blue_light,  android.graphics.Color.parseColor("#1565C0"), android.graphics.Color.parseColor("#0D47A1"))
            "PATIENT"  -> Triple(R.color.yellow_light, android.graphics.Color.parseColor("#F57F17"), android.graphics.Color.parseColor("#E65100"))
            else       -> Triple(R.color.gray,         android.graphics.Color.parseColor("#00796B"), android.graphics.Color.parseColor("#004D40"))
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
            applyFilters(tvEmpty)
        }, {
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }
}

