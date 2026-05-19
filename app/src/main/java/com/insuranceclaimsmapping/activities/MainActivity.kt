package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.adapters.DashboardAdapter
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.models.DashboardItem
import com.insuranceclaimsmapping.utils.PrefManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply stored dark mode preference before setContentView
        val prefManager = PrefManager(this)
        val isDark = prefManager.getDarkModeEnabled()
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_main)

        val role = prefManager.getRole() ?: "PATIENT"
        var customId = prefManager.getCustomId() ?: "N/A"

        val rvDashboard = findViewById<RecyclerView>(R.id.rvDashboard)
        val tvRoleHeader = findViewById<TextView>(R.id.tvUserRoleHeader)
        val tvIdHeader = findViewById<TextView>(R.id.tvUserIdHeader)
        val fabQuickAction = findViewById<FloatingActionButton>(R.id.fabQuickAction)
        val cvAnalytics = findViewById<androidx.cardview.widget.CardView>(R.id.cvAnalytics)

        tvRoleHeader.text = role
        tvIdHeader.text = "User ID: $customId"

        // Sync profile from Firestore if custom ID is missing
        if (customId == "N/A" || customId.isEmpty()) {
            val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
            val user = firebaseHelper.getCurrentUser()
            if (user != null) {
                firebaseHelper.getUserProfile(user.uid, { profile ->
                    if (profile != null && profile.customId.isNotEmpty()) {
                        prefManager.setCustomId(profile.customId)
                        prefManager.setRole(profile.role)
                        tvIdHeader.text = "User ID: ${profile.customId}"
                        tvRoleHeader.text = profile.role
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

        // --- FAB ---
        // Show FAB only for roles that can add claims
        if (role == "PATIENT" || role == "HOSPITAL") {
            fabQuickAction.visibility = View.VISIBLE
            fabQuickAction.setOnClickListener {
                startActivity(Intent(this, AddClaimActivity::class.java))
            }
        } else {
            fabQuickAction.hide()
        }

        // --- Analytics (Insurers only) ---
        if (role == "INSURER") {
            cvAnalytics.visibility = View.VISIBLE
            loadInsurerAnalytics()
        }

        // --- Dashboard Items ---
        val dashboardItems = mutableListOf<DashboardItem>()
        val historyItem = DashboardItem(4, getString(R.string.history), R.drawable.ic_history, "ALL")
        val profileItem = DashboardItem(5, getString(R.string.profile), R.drawable.ic_profile, "ALL")

        when (role) {
            "PATIENT" -> dashboardItems.add(DashboardItem(0, "Expected Invoice", R.drawable.ic_medical_claim, "PATIENT"))
            "HOSPITAL" -> dashboardItems.add(DashboardItem(1, "Upload Bill", R.drawable.ic_add_claim, "HOSPITAL"))
            "INSURER" -> {
                dashboardItems.add(DashboardItem(2, "Upload Policy", R.drawable.ic_profile, "INSURER"))
                dashboardItems.add(DashboardItem(3, "Adjudicate Bills", R.drawable.ic_history, "INSURER"))
            }
        }
        dashboardItems.add(historyItem)
        dashboardItems.add(profileItem)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val adapter = DashboardAdapter(dashboardItems) { item ->
            when (item.id) {
                0 -> if (role == "PATIENT") viewLatestExpectedInvoice()
                     else startActivity(Intent(this, AddClaimActivity::class.java).apply { putExtra("isPredictionOnly", true) })
                1 -> startActivity(Intent(this, AddClaimActivity::class.java))
                2 -> startActivity(Intent(this, UploadPolicyActivity::class.java))
                3 -> batchProcessPendingClaims()
                4 -> startActivity(Intent(this, ClaimHistoryActivity::class.java))
                5 -> startActivity(Intent(this, ProfileActivity::class.java))
            }
        }

        rvDashboard.layoutManager = GridLayoutManager(this, 2)
        rvDashboard.adapter = adapter
        applyRoleStyling(role, toolbar)
    }

    private fun loadInsurerAnalytics() {
        val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
        firebaseHelper.getClaimsByRole("INSURER", "", { claims ->
            val total = claims.size
            val approved = claims.count { it.status == "APPROVED" }
            val rejected = claims.count { it.status == "REJECTED" }

            findViewById<TextView>(R.id.tvStatTotal).text = total.toString()
            findViewById<TextView>(R.id.tvStatApproved).text = approved.toString()
            findViewById<TextView>(R.id.tvStatRejected).text = rejected.toString()

            setupBarChart(claims)
        }, {})
    }

    private fun setupBarChart(claims: List<Claim>) {
        val barChart = findViewById<BarChart>(R.id.barChart)

        val pending = claims.count { it.status == "PENDING" }.toFloat()
        val approved = claims.count { it.status == "APPROVED" }.toFloat()
        val rejected = claims.count { it.status == "REJECTED" }.toFloat()

        val entries = listOf(
            BarEntry(0f, pending),
            BarEntry(1f, approved),
            BarEntry(2f, rejected)
        )

        val dataSet = BarDataSet(entries, "Claims by Status").apply {
            colors = listOf(
                Color.parseColor("#FF8F00"), // amber – pending
                Color.parseColor("#2E7D32"), // green  – approved
                Color.parseColor("#C62828")  // red    – rejected
            )
            valueTextSize = 12f
            valueTextColor = Color.DKGRAY
        }

        barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            setDrawGridBackground(false)
            axisRight.isEnabled = false
            axisLeft.apply {
                granularity = 1f
                axisMinimum = 0f
                textColor = Color.DKGRAY
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                valueFormatter = IndexAxisValueFormatter(listOf("Pending", "Approved", "Rejected"))
                textColor = Color.DKGRAY
            }
            animateY(600)
            invalidate()
        }
    }

    private fun applyRoleStyling(role: String, toolbar: androidx.appcompat.widget.Toolbar) {
        val rootLayout = findViewById<android.widget.LinearLayout>(R.id.mainRoot)
        val headerLayout = findViewById<android.widget.LinearLayout>(R.id.llHeader)

        val (bg, toolbarColor, statusBarColor) = when (role) {
            "HOSPITAL" -> Triple(R.color.green_light, Color.parseColor("#2E7D32"), Color.parseColor("#1B5E20"))
            "INSURER"  -> Triple(R.color.blue_light,  Color.parseColor("#1565C0"), Color.parseColor("#0D47A1"))
            "PATIENT"  -> Triple(R.color.yellow_light, Color.parseColor("#F57F17"), Color.parseColor("#E65100"))
            else       -> Triple(R.color.gray,         Color.parseColor("#00796B"), Color.parseColor("#004D40"))
        }

        rootLayout?.setBackgroundResource(bg)
        headerLayout?.setBackgroundColor(toolbarColor)
        toolbar.setBackgroundColor(toolbarColor)
        window.statusBarColor = statusBarColor
    }

    private fun viewLatestExpectedInvoice() {
        val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        firebaseHelper.getClaimsByRole("PATIENT", userId, { claims ->
            if (claims.isNotEmpty()) {
                startActivity(Intent(this, ClaimDetailActivity::class.java).apply {
                    putExtra("claimId", claims[0].id)
                })
            } else {
                Toast.makeText(this, "No active invoices found.", Toast.LENGTH_LONG).show()
            }
        }, {
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun batchProcessPendingClaims() {
        val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
        val insurerId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        firebaseHelper.getPolicy(insurerId, { policy ->
            val policyRules = policy?.coverageDetails ?: "Standard Policy: 20% Copay applies to all items."
            firebaseHelper.getClaimsByRole("INSURER", "", { claims ->
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
                Toast.makeText(this, "Error fetching claims: ${it.message}", Toast.LENGTH_SHORT).show()
            })
        })
    }
}

