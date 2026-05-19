package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.utils.PrefManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private val firebaseHelper by lazy { com.insuranceclaimsmapping.firebase.FirebaseHelper() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefManager = PrefManager(this)

        lifecycleScope.launch {
            delay(2000)
            if (isFinishing || isDestroyed) return@launch

            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null && prefManager.isLoggedIn()) {
                val role = prefManager.getRole()
                if (role == null) {
                    firebaseHelper.getUserProfile(currentUser.uid, onSuccess = { user ->
                        if (isFinishing || isDestroyed) return@getUserProfile
                        if (user != null) {
                            prefManager.setRole(user.role)
                            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        } else {
                            startActivity(Intent(this@SplashActivity, SelectRoleActivity::class.java))
                        }
                        finish()
                    }, onFailure = { _ ->
                        if (isFinishing || isDestroyed) return@getUserProfile
                        startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                        finish()
                    })
                } else {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            } else {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                finish()
            }
        }
    }
}
