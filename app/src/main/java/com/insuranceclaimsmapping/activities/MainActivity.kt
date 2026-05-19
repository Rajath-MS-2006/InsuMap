package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.GridLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.adapters.DashboardAdapter
import com.insuranceclaimsmapping.databinding.ActivityMainBinding
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.models.DashboardItem
import com.insuranceclaimsmapping.utils.PrefManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefManager = PrefManager(this)
        val isDark = prefManager.getDarkModeEnabled()
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show onboarding for first-time users
        if (!prefManager.isOnboardingShown()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        val role = prefManager.getRole() ?: "PATIENT"
        var customId = prefManager.getCustomId() ?: "N/A"

        binding.tvUserRoleHeader.text = role
        binding.tvUserIdHeader.text = "User ID: $customId"

        if (customId == "N/A" || customId.isEmpty()) {
            val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
            val user = firebaseHelper.getCurrentUser()
            if (user != null) {
                firebaseHelper.getUserProfile(user.uid, { profile ->
                    if (isFinishing || isDestroyed) return@getUserProfile
                    if (profile != null && profile.customId.isNotEmpty()) {
                        prefManager.setCustomId(profile.customId)
                        prefManager.setRole(profile.role)
                        binding.tvUserIdHeader.text = "User ID: ${profile.customId}"
                        binding.tvUserRoleHeader.text = profile.role
                    } else {
                        Toast.makeText(this, "Profile not found. Please re-select your role.", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this, SelectRoleActivity::class.java).apply {
                            putExtra("email", prefManager.getEmail())
                        })
                        finish()
                    }
                }, {})
            }
        }

        if (role == "PATIENT" || role == "HOSPITAL") {
            binding.fabQuickAction.visibility = View.VISIBLE
            binding.fabQuickAction.setOnClickListener { startActivity(Intent(this, AddClaimActivity::class.java)) }
        } else {
            binding.fabQuickAction.hide()
        }

        if (role == "INSURER") {
            binding.cvAnalytics.visibility = View.VISIBLE
            loadInsurerAnalytics()
        }

        val dashboardItems = mutableListOf<DashboardItem>()
        val historyItem = DashboardItem(4, getString(R.string.history), R.drawable.ic_history, "ALL")
        val profileItem = DashboardItem(5, getString(R.string.profile), R.drawable.ic_profile, "ALL")

        when (role) {
            "PATIENT" -> {
                dashboardItems.add(DashboardItem(0, "Expected Invoice", R.drawable.ic_medical_claim, "PATIENT"))
                dashboardItems.add(DashboardItem(6, "Expense Tracker", R.drawable.ic_history, "PATIENT"))
                dashboardItems.add(DashboardItem(8, "Insurance Card", R.drawable.ic_profile, "PATIENT"))
            }
            "HOSPITAL" -> dashboardItems.add(DashboardItem(1, "Upload Bill", R.drawable.ic_add_claim, "HOSPITAL"))
            "INSURER" -> {
                dashboardItems.add(DashboardItem(2, "Upload Policy", R.drawable.ic_profile, "INSURER"))
                dashboardItems.add(DashboardItem(3, "Adjudicate Bills", R.drawable.ic_history, "INSURER"))
                dashboardItems.add(DashboardItem(7, "Fraud Dashboard", R.drawable.ic_search, "INSURER"))
            }
        }
        dashboardItems.add(historyItem)
        dashboardItems.add(profileItem)

        setSupportActionBar(binding.toolbar)

        val adapter = DashboardAdapter(dashboardItems) { item ->
            when (item.id) {
                0 -> if (role == "PATIENT") viewLatestExpectedInvoice()
                     else startActivity(Intent(this, AddClaimActivity::class.java).apply { putExtra("isPredictionOnly", true) })
                1 -> startActivity(Intent(this, AddClaimActivity::class.java))
                2 -> startActivity(Intent(this, UploadPolicyActivity::class.java))
                3 -> batchProcessPendingClaims()
                4 -> startActivity(Intent(this, ClaimHistoryActivity::class.java))
                5 -> startActivity(Intent(this, ProfileActivity::class.java))
                6 -> startActivity(Intent(this, ExpenseTrackerActivity::class.java))
                7 -> startActivity(Intent(this, FraudDashboardActivity::class.java))
                8 -> startActivity(Intent(this, InsuranceCardActivity::class.java))
            }
        }

        binding.rvDashboard.layoutManager = GridLayoutManager(this, 2)
        binding.rvDashboard.adapter = adapter
        applyRoleStyling(role)
    }

    private fun loadInsurerAnalytics() {
        val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
        firebaseHelper.getClaimsByRole("INSURER", "", { claims ->
            if (isFinishing || isDestroyed) return@getClaimsByRole
            val total = claims.size
            val approved = claims.count { it.status == "APPROVED" }
            val rejected = claims.count { it.status == "REJECTED" }

            binding.tvStatTotal.text = total.toString()
            binding.tvStatApproved.text = approved.toString()
            binding.tvStatRejected.text = rejected.toString()

            setupBarChart(claims)
        }, {
            android.util.Log.w("MainActivity", "Failed to load analytics: ${it.message}")
        })
    }

    private fun setupBarChart(claims: List<Claim>) {
        val pending = claims.count { it.status == "PENDING" }.toFloat()
        val approved = claims.count { it.status == "APPROVED" }.toFloat()
        val rejected = claims.count { it.status == "REJECTED" }.toFloat()

        val entries = listOf(BarEntry(0f, pending), BarEntry(1f, approved), BarEntry(2f, rejected))
        val dataSet = BarDataSet(entries, "Claims by Status").apply {
            colors = listOf(
                getColor(R.color.status_pending),
                getColor(R.color.status_approved),
                getColor(R.color.status_rejected)
            )
            valueTextSize = 12f
            valueTextColor = getColor(R.color.dark_gray)
        }

        binding.barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            setDrawGridBackground(false)
            axisRight.isEnabled = false
            axisLeft.apply { granularity = 1f; axisMinimum = 0f; textColor = getColor(R.color.dark_gray) }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                valueFormatter = IndexAxisValueFormatter(listOf("Pending", "Approved", "Rejected"))
                textColor = getColor(R.color.dark_gray)
            }
            animateY(600)
            invalidate()
        }
    }

    private fun applyRoleStyling(role: String) {
        val (bg, toolbarColorRes, statusBarColorRes) = when (role) {
            "HOSPITAL" -> Triple(R.color.green_light, R.color.hospital_primary, R.color.hospital_dark)
            "INSURER"  -> Triple(R.color.blue_light,  R.color.insurer_primary,  R.color.insurer_dark)
            "PATIENT"  -> Triple(R.color.yellow_light, R.color.patient_primary, R.color.patient_dark)
            else       -> Triple(R.color.gray,         R.color.default_primary, R.color.default_dark)
        }

        binding.mainRoot.setBackgroundResource(bg)
        binding.llHeader.setBackgroundColor(getColor(toolbarColorRes))
        binding.toolbar.setBackgroundColor(getColor(toolbarColorRes))
        window.statusBarColor = getColor(statusBarColorRes)
    }

    private fun viewLatestExpectedInvoice() {
        val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show()
            return
        }
        firebaseHelper.getClaimsByRole("PATIENT", userId, { claims ->
            if (isFinishing || isDestroyed) return@getClaimsByRole
            if (claims.isNotEmpty()) {
                startActivity(Intent(this, ClaimDetailActivity::class.java).apply {
                    putExtra("claimId", claims[0].id)
                })
            } else {
                Toast.makeText(this, "No active invoices found.", Toast.LENGTH_LONG).show()
            }
        }, {
            if (isFinishing || isDestroyed) return@getClaimsByRole
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun batchProcessPendingClaims() {
        val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
        val insurerId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show()
            return
        }
        firebaseHelper.getPolicy(insurerId, { policy ->
            if (isFinishing || isDestroyed) return@getPolicy
            val policyRules = policy?.coverageDetails ?: "Standard Policy: 20% Copay applies to all items."
            firebaseHelper.getClaimsByRole("INSURER", "", { claims ->
                if (isFinishing || isDestroyed) return@getClaimsByRole
                val pending = claims.filter { it.status == "PENDING" }
                if (pending.isEmpty()) {
                    Toast.makeText(this, "No pending claims to process", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this, AdjudicationActivity::class.java).apply {
                        putParcelableArrayListExtra("claims", ArrayList(pending))
                        putExtra("policyRules", policyRules)
                    })
                }
            }, {
                if (isFinishing || isDestroyed) return@getClaimsByRole
                Toast.makeText(this, "Error fetching claims: ${it.message}", Toast.LENGTH_SHORT).show()
            })
        }, {
            if (isFinishing || isDestroyed) return@getPolicy
            Toast.makeText(this, "Error fetching policy: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }
}
