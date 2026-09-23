package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ActivitySelectRoleBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager
import com.google.firebase.auth.FirebaseAuth

class SelectRoleActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var prefManager: PrefManager
    private lateinit var binding: ActivitySelectRoleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectRoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firebaseHelper = FirebaseHelper()
        prefManager = PrefManager(this)

        val email = intent.getStringExtra("email") ?: ""

        binding.btnConfirmRole.setOnClickListener {
            val selectedRoleId = binding.rgSelectRole.checkedRadioButtonId
            val role = when (selectedRoleId) {
                R.id.rbSelectPatient -> "PATIENT"
                R.id.rbSelectHospital -> "HOSPITAL"
                R.id.rbSelectInsurer -> "INSURER"
                else -> "PATIENT"
            }

            binding.progressSelectRole.visibility = View.VISIBLE
            binding.btnConfirmRole.isEnabled = false

            // Fetch count to generate sequential ID
            firebaseHelper.generateNextCustomId(role) { customId ->
                if (isFinishing || isDestroyed) return@generateNextCustomId
                val currentUid = auth.currentUser?.uid ?: run {
                    binding.progressSelectRole.visibility = View.GONE
                    binding.btnConfirmRole.isEnabled = true
                    Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                    return@generateNextCustomId
                }
                val user = User(
                    uid = currentUid,
                    customId = customId,
                    email = email,
                    role = role
                )

                firebaseHelper.saveUserProfile(user, {
                    if (isFinishing || isDestroyed) return@saveUserProfile
                    binding.progressSelectRole.visibility = View.GONE
                    prefManager.setLogin(true)
                    prefManager.setEmail(email)
                    prefManager.setRole(role)
                    prefManager.setCustomId(customId)
                    Toast.makeText(this, "Profile Setup Complete. Your ID is $customId", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }, {
                    if (isFinishing || isDestroyed) return@saveUserProfile
                    binding.progressSelectRole.visibility = View.GONE
                    binding.btnConfirmRole.isEnabled = true
                    Toast.makeText(this, "Failed to save profile: ${it.message}", Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}
