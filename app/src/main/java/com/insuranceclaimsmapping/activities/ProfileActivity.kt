package com.insuranceclaimsmapping.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.databinding.ActivityProfileBinding
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.utils.PrefManager

class ProfileActivity : AppCompatActivity() {
    private val firebaseHelper = FirebaseHelper()
    private lateinit var prefManager: PrefManager
    private var currentUserData: User? = null
    private lateinit var binding: ActivityProfileBinding

    private val imagePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (isFinishing || isDestroyed) return@registerForActivityResult
        uri?.let { uploadProfilePicture(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PrefManager(this)

        val role = prefManager.getRole() ?: "PATIENT"
        applyRoleBranding(role)

        if (role == "PATIENT") {
            binding.cvInsuranceProvider.visibility = View.VISIBLE
        }

        // Initialize Switches
        binding.swNotifications.isChecked = prefManager.getNotificationsEnabled()
        binding.swNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setNotificationsEnabled(isChecked)
        }

        binding.swDarkMode.isChecked = prefManager.getDarkModeEnabled()
        binding.swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setDarkModeEnabled(isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        val currentUser = firebaseHelper.getCurrentUser()
        if (currentUser != null) {
            firebaseHelper.getUserProfile(currentUser.uid, { user: User? ->
                if (isFinishing || isDestroyed) return@getUserProfile
                if (user != null) {
                    currentUserData = user
                    binding.tvProfileName.text = user.displayName.ifEmpty { "User" }
                    binding.tvProfileId.text = "User ID: ${user.customId}"
                    binding.tvProfileRole.text = "Account Type: ${user.role}"
                    binding.tvProfileEmail.text = user.email
                    binding.tvProfilePhone.text = if (user.phoneNumber.isNotEmpty()) user.phoneNumber else "No phone added"
                    
                    if (user.insuranceProviderId.isNotEmpty()) {
                        binding.etInsuranceProviderId.setText(user.insuranceProviderId)
                    }

                    if (user.profilePictureUrl.isNotEmpty()) {
                        Glide.with(this).load(user.profilePictureUrl).into(binding.ivProfilePicture)
                    }
                }
            }, {
                if (isFinishing || isDestroyed) return@getUserProfile
                binding.tvProfileEmail.text = prefManager.getEmail() ?: "User Email"
                binding.tvProfileId.text = "User ID: ${prefManager.getCustomId() ?: "N/A"}"
            })
        }

        binding.btnEditProfile.setOnClickListener { showEditProfileDialog() }
        binding.ivProfilePicture.setOnClickListener { imagePickerLauncher.launch("image/*") }
        
        binding.btnSaveInsurance.setOnClickListener {
            val providerId = binding.etInsuranceProviderId.text.toString().trim()
            if (providerId.isEmpty()) {
                Toast.makeText(this, "Please enter a valid Provider ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentUserData?.let {
                val updatedUser = it.copy(insuranceProviderId = providerId)
                firebaseHelper.updateUserProfile(updatedUser, {
                    if (isFinishing || isDestroyed) return@updateUserProfile
                    currentUserData = updatedUser
                    Toast.makeText(this, "Insurance Provider Saved", Toast.LENGTH_SHORT).show()
                }, { e: Exception ->
                    if (isFinishing || isDestroyed) return@updateUserProfile
                    Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                })
            }
        }

        binding.btnLogout.setOnClickListener {
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
                    if (isFinishing || isDestroyed) return@updateUserProfile
                    currentUserData = updatedUser
                    binding.tvProfileName.text = newName.ifEmpty { "User" }
                    binding.tvProfilePhone.text = newPhone.ifEmpty { "No phone added" }
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                }, { e: Exception ->
                    if (isFinishing || isDestroyed) return@updateUserProfile
                    Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uploadProfilePicture(uri: Uri) {
        val user = currentUserData ?: return
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()
        firebaseHelper.uploadProfilePicture(uri, user.uid, { downloadUrl: String ->
            if (isFinishing || isDestroyed) return@uploadProfilePicture
            val updatedUser = user.copy(profilePictureUrl = downloadUrl)
            firebaseHelper.updateUserProfile(updatedUser, {
                if (isFinishing || isDestroyed) return@updateUserProfile
                currentUserData = updatedUser
                Glide.with(this).load(downloadUrl).into(binding.ivProfilePicture)
                Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
            }, { e: Exception ->
                if (isFinishing || isDestroyed) return@updateUserProfile
                Toast.makeText(this, "Failed to save picture URL: ${e.message}", Toast.LENGTH_SHORT).show()
            })
        }, { e: Exception ->
            if (isFinishing || isDestroyed) return@uploadProfilePicture
            Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
        })
    }

    private fun applyRoleBranding(role: String) {
        val (bg, colorRes, statusColorRes) = when (role) {
            "HOSPITAL" -> Triple(R.color.green_light, R.color.hospital_primary, R.color.hospital_dark)
            "INSURER"  -> Triple(R.color.blue_light,  R.color.insurer_primary,  R.color.insurer_dark)
            "PATIENT"  -> Triple(R.color.yellow_light, R.color.patient_primary, R.color.patient_dark)
            else       -> Triple(R.color.gray,         R.color.default_primary, R.color.default_dark)
        }

        binding.rootProfile.setBackgroundResource(bg)
        binding.tvProfileName.setTextColor(getColor(colorRes))
        window.statusBarColor = getColor(statusColorRes)
    }
}
