package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.utils.PrefManager
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.User
import com.google.firebase.auth.FirebaseAuth

class SignupActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var prefManager: PrefManager
    private lateinit var firebaseHelper: FirebaseHelper
    private val strongPasswordRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()
        prefManager = PrefManager(this)
        firebaseHelper = FirebaseHelper()

        val etSignupName = findViewById<EditText>(R.id.etSignupName)
        val etSignupEmail = findViewById<EditText>(R.id.etSignupEmail)
        val etSignupPassword = findViewById<EditText>(R.id.etSignupPassword)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        val rgRole = findViewById<RadioGroup>(R.id.rgRole)
        val progressBar = findViewById<ProgressBar>(R.id.progressSignup)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnSignup.setOnClickListener {
            val name = etSignupName.text.toString().trim()
            val email = etSignupEmail.text.toString().trim()
            val password = etSignupPassword.text.toString().trim()
            
            val selectedRoleId = rgRole.checkedRadioButtonId
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

            if (!strongPasswordRegex.matches(password)) {
                Toast.makeText(
                    this,
                    "Use 8+ chars with upper, lower, number, and special symbol.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            progressBar.visibility = android.view.View.VISIBLE
            btnSignup.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        firebaseHelper.generateNextCustomId(role) { customId ->
                            val user = com.insuranceclaimsmapping.models.User(
                                uid = auth.currentUser!!.uid,
                                customId = customId,
                                email = email,
                                role = role,
                                displayName = name
                            )
                            firebaseHelper.saveUserProfile(user, {
                                progressBar.visibility = android.view.View.GONE
                                btnSignup.isEnabled = true
                                prefManager.setLogin(true)
                                prefManager.setEmail(email)
                                prefManager.setRole(role)
                                prefManager.setCustomId(customId)
                                Toast.makeText(this, "Signup Successful. Your ID is $customId", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }, {
                                progressBar.visibility = android.view.View.GONE
                                btnSignup.isEnabled = true
                                Toast.makeText(this, "Profile Creation Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            })
                        }
                    } else {
                        progressBar.visibility = android.view.View.GONE
                        btnSignup.isEnabled = true
                        Toast.makeText(this, "Signup Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }
}
