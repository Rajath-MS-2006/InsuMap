package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.google.android.material.switchmaterial.SwitchMaterial
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager

class ProfileActivity : AppCompatActivity() {
    private val firebaseHelper = FirebaseHelper()
    private lateinit var prefManager: PrefManager
    private var currentUserData: User? = null

    private lateinit var ivProfilePicture: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvPhone: TextView

    private val imagePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadProfilePicture(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        prefManager = PrefManager(this)

        ivProfilePicture = findViewById(R.id.ivProfilePicture)
        tvName = findViewById(R.id.tvProfileName)
        val tvId = findViewById<TextView>(R.id.tvProfileId)
        val tvRole = findViewById<TextView>(R.id.tvProfileRole)
        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        tvPhone = findViewById(R.id.tvProfilePhone)

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnEditProfile = findViewById<Button>(R.id.btnEditProfile)
        val swNotifications = findViewById<SwitchMaterial>(R.id.swNotifications)
        val swDarkMode = findViewById<SwitchMaterial>(R.id.swDarkMode)
        val cvInsuranceProvider = findViewById<androidx.cardview.widget.CardView>(R.id.cvInsuranceProvider)
        val etInsuranceProviderId = findViewById<EditText>(R.id.etInsuranceProviderId)
        val btnSaveInsurance = findViewById<Button>(R.id.btnSaveInsurance)

        val role = prefManager.getRole() ?: "PATIENT"
        applyRoleBranding(role)

        if (role == "PATIENT") {
            cvInsuranceProvider.visibility = android.view.View.VISIBLE
        }

        // Initialize Switches
        swNotifications.isChecked = prefManager.getNotificationsEnabled()
        swNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setNotificationsEnabled(isChecked)
        }

        swDarkMode.isChecked = prefManager.getDarkModeEnabled()
        swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setDarkModeEnabled(isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        val currentUser = firebaseHelper.getCurrentUser()
        if (currentUser != null) {
            firebaseHelper.getUserProfile(currentUser.uid, { user ->
                if (user != null) {
                    currentUserData = user
                    tvName.text = user.displayName.ifEmpty { "User" }
                    tvId.text = "User ID: ${user.customId}"
                    tvRole.text = "Account Type: ${user.role}"
                    tvEmail.text = user.email
                    tvPhone.text = if (user.phoneNumber.isNotEmpty()) user.phoneNumber else "No phone added"
                    
                    if (user.insuranceProviderId.isNotEmpty()) {
                        etInsuranceProviderId.setText(user.insuranceProviderId)
                    }

                    if (user.profilePictureUrl.isNotEmpty()) {
                        Glide.with(this).load(user.profilePictureUrl).into(ivProfilePicture)
                    }
                }
            }, {
                tvEmail.text = prefManager.getEmail() ?: "User Email"
                tvId.text = "User ID: ${prefManager.getCustomId() ?: "N/A"}"
            })
        }

        btnEditProfile.setOnClickListener { showEditProfileDialog() }
        ivProfilePicture.setOnClickListener { imagePickerLauncher.launch("image/*") }
        
        btnSaveInsurance.setOnClickListener {
            val providerId = etInsuranceProviderId.text.toString().trim()
            if (providerId.isEmpty()) {
                Toast.makeText(this, "Please enter a valid Provider ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentUserData?.let {
                val updatedUser = it.copy(insuranceProviderId = providerId)
                firebaseHelper.updateUserProfile(updatedUser, {
                    currentUserData = updatedUser
                    Toast.makeText(this, "Insurance Provider Saved", Toast.LENGTH_SHORT).show()
                }, { e ->
                    Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                })
            }
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

    private fun showEditProfileDialog() {
        val user = currentUserData ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etEditName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)
        
        etName.setText(user.displayName)
        etPhone.setText(user.phoneNumber)

        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()
                
                val updatedUser = user.copy(displayName = newName, phoneNumber = newPhone)
                firebaseHelper.updateUserProfile(updatedUser, {
                    currentUserData = updatedUser
                    tvName.text = newName.ifEmpty { "User" }
                    tvPhone.text = newPhone.ifEmpty { "No phone added" }
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                }, {
                    Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uploadProfilePicture(uri: Uri) {
        val user = currentUserData ?: return
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()
        firebaseHelper.uploadProfilePicture(uri, user.uid, { downloadUrl ->
            val updatedUser = user.copy(profilePictureUrl = downloadUrl)
            firebaseHelper.updateUserProfile(updatedUser, {
                currentUserData = updatedUser
                Glide.with(this).load(downloadUrl).into(ivProfilePicture)
                Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
            }, {
                Toast.makeText(this, "Failed to save picture URL", Toast.LENGTH_SHORT).show()
            })
        }, {
            Toast.makeText(this, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun applyRoleBranding(role: String) {
        val root = findViewById<android.view.View>(R.id.rootProfile)
        val nameLabel = findViewById<TextView>(R.id.tvProfileName)
        
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
