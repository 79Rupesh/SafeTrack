package com.safetrack.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.safetrack.SafeTrackApp
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivityProfileSetupBinding
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.ui.PermissionSetupActivity
import com.safetrack.utils.NetworkUtils
import kotlinx.coroutines.launch

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private val firebaseRepo = FirebaseRepository()

    private var uid: String = ""
    private var phoneNumber: String = ""
    private var role: String = User.ROLE_FRIEND

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionManager = (application as SafeTrackApp).sessionManager
        uid = intent.getStringExtra(EXTRA_UID) ?: sessionManager.uid ?: ""
        phoneNumber = intent.getStringExtra(EXTRA_PHONE) ?: sessionManager.phoneNumber ?: ""
        role = intent.getStringExtra(EXTRA_ROLE) ?: sessionManager.userRole

        binding.tvVerifiedPhoneNumber.text = phoneNumber.ifBlank { "Verified Number" }
        binding.tvDisplayRole.text = User.getRoleDisplayName(role)

        binding.btnChangeRole.setOnClickListener {
            val intent = Intent(this, RoleSelectionActivity::class.java).apply {
                putExtra(RoleSelectionActivity.EXTRA_UID, uid)
                putExtra(RoleSelectionActivity.EXTRA_PHONE, phoneNumber)
                putExtra(RoleSelectionActivity.EXTRA_INITIAL_ROLE, role)
            }
            startActivity(intent)
            finish()
        }

        binding.btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }
    }

    private fun saveUserProfile() {
        val name = binding.etFullName.text?.toString()?.trim() ?: ""
        val emergencyPhone = binding.etEmergencyPhone.text?.toString()?.trim() ?: ""
        if (name.length < 2) {
            binding.tilFullName.error = "Please enter your full name (at least 2 letters)"
            return
        }
        binding.tilFullName.error = null

        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No network connection. Please check your internet connection.", Toast.LENGTH_SHORT).show()
            return
        }

        setSavingState(true)

        val sessionManager = (application as SafeTrackApp).sessionManager
        val inviteCode = sessionManager.inviteCode
        val now = System.currentTimeMillis()

        val user = User(
            uid = uid,
            userId = uid,
            name = name,
            phoneNumber = phoneNumber,
            role = role,
            inviteCode = inviteCode,
            emergencyContactPhone = emergencyPhone,
            createdAt = now,
            updatedAt = now,
            accountStatus = User.STATUS_ACTIVE
        )

        lifecycleScope.launch {
            val result = firebaseRepo.saveUser(user)
            setSavingState(false)

            if (result.isSuccess) {
                sessionManager.saveUser(user)
                sessionManager.primaryEmergencyPhone = emergencyPhone
                Toast.makeText(this@ProfileSetupActivity, "Profile successfully created!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this@ProfileSetupActivity, PermissionSetupActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(
                    this@ProfileSetupActivity,
                    "Failed to save profile: ${result.exceptionOrNull()?.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setSavingState(saving: Boolean) {
        binding.btnSaveProfile.isEnabled = !saving
        binding.etFullName.isEnabled = !saving
        binding.btnChangeRole.isEnabled = !saving
        binding.layoutSaving.visibility = if (saving) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_UID = "extra_uid"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_ROLE = "extra_role"
    }
}
