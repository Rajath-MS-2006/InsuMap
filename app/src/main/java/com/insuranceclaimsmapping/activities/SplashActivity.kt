package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.utils.PrefManager

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefManager = PrefManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null && prefManager.isLoggedIn()) {
                val role = prefManager.getRole()
                if (role == null) {
                    // Profile safety: Fetch role if missing from prefs
                    com.insuranceclaimsmapping.firebase.FirebaseHelper().getUserProfile(currentUser.uid, { user ->
                        if (user != null) {
                            prefManager.setRole(user.role)
                            startActivity(Intent(this, MainActivity::class.java))
                        } else {
                            startActivity(Intent(this, SelectRoleActivity::class.java))
                        }
                        finish()
                    }, {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    })
                } else {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }, 2000)
    }
}
