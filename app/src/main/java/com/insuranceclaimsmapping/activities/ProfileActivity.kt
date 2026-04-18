package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.utils.PrefManager

class ProfileActivity : AppCompatActivity() {
    private val firebaseHelper = FirebaseHelper()
    private lateinit var prefManager: PrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        prefManager = PrefManager(this)

        val tvName = findViewById<TextView>(R.id.tvProfileName)
        val tvId = findViewById<TextView>(R.id.tvProfileId)
        val tvRole = findViewById<TextView>(R.id.tvProfileRole)
        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        // Apply dynamic role-based branding
        val role = prefManager.getRole() ?: "PATIENT"
        applyRoleBranding(role)

        val currentUser = firebaseHelper.getCurrentUser()
        if (currentUser != null) {
            firebaseHelper.getUserProfile(currentUser.uid, { user ->
                if (user != null) {
                    tvName.text = user.displayName.ifEmpty { "User" }
                    tvId.text = "User ID: ${user.customId}"
                    tvRole.text = "Account Type: ${user.role}"
                    tvEmail.text = user.email
                }
            }, {
                tvEmail.text = prefManager.getEmail() ?: "User Email"
                tvId.text = "User ID: ${prefManager.getCustomId() ?: "N/A"}"
            })
        }

        btnLogout.setOnClickListener {
            firebaseHelper.logout()
            prefManager.logout()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    private fun applyRoleBranding(role: String) {
        val root = findViewById<android.view.View>(R.id.rootProfile)
        val nameLabel = findViewById<android.widget.TextView>(R.id.tvProfileName)
        
        val (bg, color) = when (role) {
            "HOSPITAL" -> R.color.green_light to android.graphics.Color.parseColor("#2E7D32")
            "INSURER" -> R.color.blue_light to android.graphics.Color.parseColor("#1565C0")
            "PATIENT" -> R.color.yellow_light to android.graphics.Color.parseColor("#F57F17")
            else -> R.color.gray to android.graphics.Color.parseColor("#00796B")
        }
        
        root?.setBackgroundResource(bg)
        nameLabel?.setTextColor(color)
        window.statusBarColor = color
    }
}
