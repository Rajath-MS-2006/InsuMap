package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.ui.theme.InsuMapTheme
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()
    private val KEY_NAME = "InsuMapBiometricKey"
    
    private val googleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, gso)
    }
    
    private val googleLoginLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isFinishing || isDestroyed) return@registerForActivityResult
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                account?.idToken?.let { idToken ->
                    viewModel.handleFirebaseGoogleAuth(idToken)
                } ?: run {
                    Toast.makeText(this, "Google Sign-In Failed: Missing Token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val canUseBiometric = viewModel.prefManager.isLoggedIn() &&
            BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        setContent {
            InsuMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        viewModel = viewModel,
                        canUseBiometric = canUseBiometric,
                        onSuccess = {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                        onNeedsRoleSelection = { email ->
                            startActivity(Intent(this, SelectRoleActivity::class.java).apply {
                                putExtra("email", email)
                            })
                            finish()
                        },
                        onNavigateToSignup = {
                            startActivity(Intent(this, SignupActivity::class.java))
                        },
                        onGoogleLoginClick = {
                            googleLoginLauncher.launch(googleSignInClient.signInIntent)
                        },
                        onBiometricLoginClick = {
                            showBiometricPrompt()
                        }
                    )
                }
            }
        }
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
                try {
                    result.cryptoObject?.cipher?.doFinal("AuthTest".toByteArray())
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Crypto validation failed. Session invalid.", Toast.LENGTH_LONG).show()
                    return
                }
                viewModel.onBiometricSuccess()
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
}
