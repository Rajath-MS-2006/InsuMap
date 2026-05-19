package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ActivityLoginBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.utils.PrefManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import com.google.firebase.auth.FirebaseAuth
import android.view.View

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var prefManager: PrefManager
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var binding: ActivityLoginBinding
    private val KEY_NAME = "InsuMapBiometricKey"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        prefManager = PrefManager(this)
        firebaseHelper = FirebaseHelper()

        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        ).requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build()

        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)

        val canUseBiometric = prefManager.isLoggedIn() &&
            BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        binding.btnBiometricLogin.visibility = if (canUseBiometric) View.VISIBLE else View.GONE
        binding.btnBiometricLogin.setOnClickListener { showBiometricPrompt() }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressLogin.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (isFinishing || isDestroyed) return@addOnCompleteListener
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: run {
                            binding.progressLogin.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            Toast.makeText(this, "Authentication error. Please try again.", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }
                        checkUserProfileAndRedirect(uid, email)
                    } else {
                        binding.progressLogin.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(this, "Login Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        binding.btnGoogleLogin.setOnClickListener { googleLoginLauncher.launch(googleSignInClient.signInIntent) }
        binding.tvSignup.setOnClickListener { startActivity(Intent(this, SignupActivity::class.java)) }
    }

    private fun showBiometricPrompt() {
        val cipher = setupKeyStoreAndCipher()
        if (cipher == null) {
            Toast.makeText(this, "Biometric crypto failed. Please log in with password.", Toast.LENGTH_LONG).show()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (isFinishing || isDestroyed) return
                // Prove successful decryption/encryption
                try {
                    result.cryptoObject?.cipher?.doFinal("AuthTest".toByteArray())
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Crypto validation failed. Session invalid.", Toast.LENGTH_LONG).show()
                    return
                }

                val uid = auth.currentUser?.uid
                if (uid != null) {
                    checkUserProfileAndRedirect(uid, prefManager.getEmail() ?: "")
                } else {
                    Toast.makeText(this@LoginActivity, "Session expired. Please log in with password.", Toast.LENGTH_LONG).show()
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (isFinishing || isDestroyed) return
                Toast.makeText(this@LoginActivity, "Biometric error: $errString", Toast.LENGTH_SHORT).show()
            }
            override fun onAuthenticationFailed() {
                if (isFinishing || isDestroyed) return
                Toast.makeText(this@LoginActivity, "Biometric not recognized", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Login")
            .setSubtitle("Use your fingerprint or face to sign in")
            .setNegativeButtonText("Use Password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    private fun setupKeyStoreAndCipher(): Cipher? {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_NAME)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
                )
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(
                        KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true)
                        .build()
                )
                keyGenerator.generateKey()
            }

            val key = keyStore.getKey(KEY_NAME, null) as SecretKey
            val cipher = Cipher.getInstance(
                "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
            )
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private val googleLoginLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isFinishing || isDestroyed) return@registerForActivityResult
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                account?.idToken?.let { idToken ->
                    firebaseAuthWithGoogle(idToken)
                } ?: run {
                    Toast.makeText(this, "Google Sign-In Failed: Missing Token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                if (task.isSuccessful) {
                    val user = auth.currentUser ?: run {
                        Toast.makeText(this, "Authentication error. Please try again.", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }
                    checkUserProfileAndRedirect(user.uid, user.email ?: "")
                } else {
                    Toast.makeText(this, "Firebase Auth with Google Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkUserProfileAndRedirect(uid: String, email: String) {
        firebaseHelper.getUserProfile(uid, { user ->
            if (isFinishing || isDestroyed) return@getUserProfile
            binding.progressLogin.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            if (user != null) {
                prefManager.setLogin(true)
                prefManager.setEmail(email)
                prefManager.setRole(user.role)
                prefManager.setCustomId(user.customId)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                startActivity(Intent(this, SelectRoleActivity::class.java).apply {
                    putExtra("email", email)
                })
                finish()
            }
        }, {
            if (isFinishing || isDestroyed) return@getUserProfile
            binding.progressLogin.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            Toast.makeText(this, "Failed to fetch profile: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }
}
