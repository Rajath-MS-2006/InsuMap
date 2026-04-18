package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager
import com.google.firebase.auth.FirebaseAuth

class SelectRoleActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var prefManager: PrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_role)

        auth = FirebaseAuth.getInstance()
        firebaseHelper = FirebaseHelper()
        prefManager = PrefManager(this)

        val rgRole = findViewById<RadioGroup>(R.id.rgSelectRole)
        val btnConfirm = findViewById<Button>(R.id.btnConfirmRole)
        val progressBar = findViewById<ProgressBar>(R.id.progressSelectRole)

        val email = intent.getStringExtra("email") ?: ""

        btnConfirm.setOnClickListener {
            val selectedRoleId = rgRole.checkedRadioButtonId
            val role = when (selectedRoleId) {
                R.id.rbSelectPatient -> "PATIENT"
                R.id.rbSelectHospital -> "HOSPITAL"
                R.id.rbSelectInsurer -> "INSURER"
                else -> "PATIENT"
            }

            progressBar.visibility = android.view.View.VISIBLE
            btnConfirm.isEnabled = false

            // Fetch count to generate sequential ID
            firebaseHelper.generateNextCustomId(role) { customId ->
                val user = User(
                    uid = auth.currentUser!!.uid,
                    customId = customId,
                    email = email,
                    role = role
                )

                firebaseHelper.saveUserProfile(user, {
                    progressBar.visibility = android.view.View.GONE
                    prefManager.setLogin(true)
                    prefManager.setEmail(email)
                    prefManager.setRole(role)
                    prefManager.setCustomId(customId)
                    Toast.makeText(this, "Profile Setup Complete. Your ID is $customId", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }, {
                    progressBar.visibility = android.view.View.GONE
                    btnConfirm.isEnabled = true
                    Toast.makeText(this, "Failed to save profile: ${it.message}", Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}
