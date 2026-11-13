package com.safetrack.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivityEditProfileBinding
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.utils.NetworkUtils
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val firebaseRepo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        loadCurrentProfile()

        binding.btnSaveEditedProfile.setOnClickListener {
            saveUpdatedProfile()
        }
    }

    private fun loadCurrentProfile() {
        val sessionManager = (application as SafeTrackApp).sessionManager
        binding.tvEditPhoneNumber.text = "${sessionManager.phoneNumber} (Verified)"
        binding.etEditName.setText(sessionManager.userName)
        binding.etEditEmergencyPhone.setText(sessionManager.primaryEmergencyPhone)
        binding.tvEditInviteCode.text = sessionManager.inviteCode

        when (sessionManager.userRole) {
            User.ROLE_VIEWER -> binding.rbEditViewer.isChecked = true
            User.ROLE_BOTH -> binding.rbEditBoth.isChecked = true
            else -> binding.rbEditFriend.isChecked = true
        }
    }

    private fun saveUpdatedProfile() {
        val name = binding.etEditName.text?.toString()?.trim() ?: ""
        val emergencyPhone = binding.etEditEmergencyPhone.text?.toString()?.trim() ?: ""
        if (name.length < 2) {
            binding.tilEditName.error = "Name must be at least 2 characters"
            return
        }
        binding.tilEditName.error = null

        val newRole = when (binding.rgEditRole.checkedRadioButtonId) {
            R.id.rbEditViewer -> User.ROLE_VIEWER
            R.id.rbEditBoth -> User.ROLE_BOTH
            else -> User.ROLE_FRIEND
        }

        val sessionManager = (application as SafeTrackApp).sessionManager
        val uid = sessionManager.uid ?: return

        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBarEdit.visibility = View.VISIBLE
        binding.btnSaveEditedProfile.isEnabled = false

        lifecycleScope.launch {
            val result = firebaseRepo.updateUserProfile(uid, name, newRole)
            binding.progressBarEdit.visibility = View.GONE
            binding.btnSaveEditedProfile.isEnabled = true

            if (result.isSuccess) {
                sessionManager.userName = name
                sessionManager.userRole = newRole
                sessionManager.primaryEmergencyPhone = emergencyPhone
                sessionManager.updatedAt = System.currentTimeMillis()

                Toast.makeText(this@EditProfileActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@EditProfileActivity, "Failed to update profile: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
