package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ActivitySignupBinding
import com.insuranceclaimsmapping.utils.PrefManager
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.User
import com.google.firebase.auth.FirebaseAuth

class SignupActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var prefManager: PrefManager
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        prefManager = PrefManager(this)
        firebaseHelper = FirebaseHelper()

        binding.btnSignup.setOnClickListener {
            val name = binding.etSignupName.text.toString().trim()
            val email = binding.etSignupEmail.text.toString().trim()
            val password = binding.etSignupPassword.text.toString()
            
            val selectedRoleId = binding.rgRole.checkedRadioButtonId
            val role = when (selectedRoleId) {
                R.id.rbPatient -> "PATIENT"
                R.id.rbHospital -> "HOSPITAL"
                R.id.rbInsurer -> "INSURER"
                else -> "PATIENT"
            }

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressSignup.visibility = View.VISIBLE
            binding.btnSignup.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (isFinishing || isDestroyed) return@addOnCompleteListener
                    if (task.isSuccessful) {
                        firebaseHelper.generateNextCustomId(role) { customId ->
                            if (isFinishing || isDestroyed) return@generateNextCustomId
                            val currentUid = auth.currentUser?.uid ?: run {
                                binding.progressSignup.visibility = View.GONE
                                binding.btnSignup.isEnabled = true
                                Toast.makeText(this, "Session expired. Please try again.", Toast.LENGTH_SHORT).show()
                                return@generateNextCustomId
                            }
                            val user = User(
                                uid = currentUid,
                                customId = customId,
                                email = email,
                                role = role,
                                displayName = name
                            )
                            firebaseHelper.saveUserProfile(user, {
                                if (isFinishing || isDestroyed) return@saveUserProfile
                                binding.progressSignup.visibility = View.GONE
                                binding.btnSignup.isEnabled = true
                                prefManager.setLogin(true)
                                prefManager.setEmail(email)
                                prefManager.setRole(role)
                                prefManager.setCustomId(customId)
                                Toast.makeText(this, "Signup Successful. Your ID is $customId", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }, {
                                if (isFinishing || isDestroyed) return@saveUserProfile
                                binding.progressSignup.visibility = View.GONE
                                binding.btnSignup.isEnabled = true
                                Toast.makeText(this, "Profile Creation Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            })
                        }
                    } else {
                        binding.progressSignup.visibility = View.GONE
                        binding.btnSignup.isEnabled = true
                        Toast.makeText(this, "Signup Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }
}
