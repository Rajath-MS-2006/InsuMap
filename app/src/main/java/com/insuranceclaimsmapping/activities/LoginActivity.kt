package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.utils.PrefManager
import com.google.firebase.auth.FirebaseAuth
import android.graphics.Bitmap
import com.insuranceclaimsmapping.models.Policy

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var prefManager: PrefManager
    private lateinit var firebaseHelper: FirebaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        prefManager = PrefManager(this)
        firebaseHelper = FirebaseHelper()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogleLogin = findViewById<com.google.android.gms.common.SignInButton>(R.id.btnGoogleLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressLogin)
        val tvSignup = findViewById<TextView>(R.id.tvSignup)

        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = android.view.View.VISIBLE
            btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        checkUserProfileAndRedirect(auth.currentUser!!.uid, email, progressBar, btnLogin)
                    } else {
                        progressBar.visibility = android.view.View.GONE
                        btnLogin.isEnabled = true
                        Toast.makeText(this, "Login Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleLoginLauncher.launch(signInIntent)
        }

        tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private val googleLoginLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser!!
                    checkUserProfileAndRedirect(user.uid, user.email ?: "", null, null)
                } else {
                    Toast.makeText(this, "Firebase Auth with Google Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkUserProfileAndRedirect(uid: String, email: String, progressBar: android.view.View?, loginButton: android.view.View?) {
        firebaseHelper.getUserProfile(uid, { user ->
            progressBar?.visibility = android.view.View.GONE
            loginButton?.isEnabled = true
            
            if (user != null) {
                prefManager.setLogin(true)
                prefManager.setEmail(email)
                prefManager.setRole(user.role)
                
                // Trigger Gemini (In a real scenario, you'd pass a placeholder bitmap since it's non-null)
                val placeholderBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                val policyRules = Policy()
                // val aiResult = geminiHelper.calculateAdjudication(placeholderBitmap, policyRules) 
                
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                // New Google user, needs to select a role
                val intent = Intent(this, SelectRoleActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
                finish()
            }
        }, {
            progressBar?.visibility = android.view.View.GONE
            loginButton?.isEnabled = true
            Toast.makeText(this, "Failed to fetch profile: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }
}
