package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.insuranceclaimsmapping.R
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import kotlinx.coroutines.launch

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.insuranceclaimsmapping.adapters.DashboardAdapter
import com.insuranceclaimsmapping.models.DashboardItem

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefManager = com.insuranceclaimsmapping.utils.PrefManager(this)
        val role = prefManager.getRole() ?: "PATIENT"
        var customId = prefManager.getCustomId() ?: "N/A"
        
        val rvDashboard = findViewById<RecyclerView>(R.id.rvDashboard)
        val tvRoleHeader = findViewById<android.widget.TextView>(R.id.tvUserRoleHeader)
        val tvIdHeader = findViewById<android.widget.TextView>(R.id.tvUserIdHeader)

        tvRoleHeader.text = role
        tvIdHeader.text = "User ID: $customId"

        // Fix N/A issue by fetching from Firestore. If profile is missing (deleted DB), redirect to selection.
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
                        // Profile is missing in cloud (DB was emptied)
                        Toast.makeText(this, "Profile not found. Please re-select your role.", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this, SelectRoleActivity::class.java).apply {
                            putExtra("email", prefManager.getEmail())
                        })
                        finish()
                    }
                }, {
                    // Network error or other failure
                })
            }
        }

        val dashboardItems = mutableListOf<DashboardItem>()

        // Common items (always at the bottom for better layout)
        val historyItem = DashboardItem(4, getString(R.string.history), R.drawable.ic_history, "ALL")
        val profileItem = DashboardItem(5, getString(R.string.profile), R.drawable.ic_profile, "ALL")

        // Role-specific items
        when (role) {
            "PATIENT" -> {
                dashboardItems.add(DashboardItem(0, "Expected Invoice", R.drawable.ic_medical_claim, "PATIENT"))
            }
            "HOSPITAL" -> {
                dashboardItems.add(DashboardItem(1, "Upload Bill", R.drawable.ic_add_claim, "HOSPITAL"))
            }
            "INSURER" -> {
                dashboardItems.add(DashboardItem(2, "Upload Policy", R.drawable.ic_profile, "INSURER"))
                dashboardItems.add(DashboardItem(3, "Adjudicate Bills", R.drawable.ic_history, "INSURER"))
            }
        }
        
        // Add common items
        dashboardItems.add(historyItem)
        dashboardItems.add(profileItem)

        if (dashboardItems.isEmpty()) {
            Toast.makeText(this, "Welcome! No items available for your role.", Toast.LENGTH_SHORT).show()
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val adapter = DashboardAdapter(dashboardItems) { item ->
            when (item.id) {
                0 -> {
                    if (role == "PATIENT") {
                        viewLatestExpectedInvoice()
                    } else {
                        startActivity(Intent(this, AddClaimActivity::class.java).apply {
                            putExtra("isPredictionOnly", true)
                        })
                    }
                }
                1 -> startActivity(Intent(this, AddClaimActivity::class.java))
                2 -> startActivity(Intent(this, UploadPolicyActivity::class.java))
                3 -> {
                    batchProcessPendingClaims()
                }
                4 -> startActivity(Intent(this, ClaimHistoryActivity::class.java))
                5 -> startActivity(Intent(this, ProfileActivity::class.java))
            }
        }

        rvDashboard.layoutManager = GridLayoutManager(this, 2)
        rvDashboard.adapter = adapter

        // Set dynamic background color based on role
        applyRoleStyling(role, toolbar)
    }

    private fun applyRoleStyling(role: String, toolbar: androidx.appcompat.widget.Toolbar) {
        val rootLayout = findViewById<android.widget.LinearLayout>(R.id.mainRoot)
        val headerLayout = findViewById<android.widget.LinearLayout>(R.id.llHeader)

        val (bg, toolbarColor, statusBarColor) = when (role) {
            "HOSPITAL" -> Triple(R.color.green_light, Color.parseColor("#2E7D32"), Color.parseColor("#1B5E20"))
            "INSURER" -> Triple(R.color.blue_light, Color.parseColor("#1565C0"), Color.parseColor("#0D47A1"))
            "PATIENT" -> Triple(R.color.yellow_light, Color.parseColor("#F57F17"), Color.parseColor("#E65100"))
            else -> Triple(R.color.gray, Color.parseColor("#00796B"), Color.parseColor("#004D40"))
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
                val latestClaim = claims[0]
                val intent = Intent(this, ClaimDetailActivity::class.java).apply {
                    putExtra("claimId", latestClaim.id)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "No active invoices found. Please check with the hospital billing desk.", Toast.LENGTH_LONG).show()
            }
        }, {
            Toast.makeText(this, "Error checking record: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun batchProcessPendingClaims() {
        val firebaseHelper = com.insuranceclaimsmapping.firebase.FirebaseHelper()
        val insurerId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        firebaseHelper.getPolicy(insurerId, { policy ->
            val policyRules = policy?.coverageDetails ?: "Standard Policy: 20% Copay applies to all items."
            
            firebaseHelper.getClaimsByRole("INSURER", "", { claims ->
                val pendingClaims = claims.filter { it.status == "PENDING" }
                if (pendingClaims.isEmpty()) {
                    Toast.makeText(this, "No pending claims to process", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(this, AdjudicationActivity::class.java).apply {
                        putParcelableArrayListExtra("claims", ArrayList(pendingClaims))
                        putExtra("policyRules", policyRules)
                    }
                    startActivity(intent)
                }
            }, {
                Toast.makeText(this, "Error fetching claims: ${it.message}", Toast.LENGTH_SHORT).show()
            })
        }, {
            Toast.makeText(this, "Failed to fetch policy rules. Using defaults.", Toast.LENGTH_SHORT).show()
        })
    }
}
