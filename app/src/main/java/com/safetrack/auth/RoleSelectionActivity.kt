package com.safetrack.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivityRoleSelectionBinding

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding
    private var selectedRole = User.ROLE_FRIEND
    private var uid: String = ""
    private var phoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionManager = (application as SafeTrackApp).sessionManager
        uid = intent.getStringExtra(EXTRA_UID) ?: sessionManager.uid ?: ""
        phoneNumber = intent.getStringExtra(EXTRA_PHONE) ?: sessionManager.phoneNumber ?: ""
        selectedRole = intent.getStringExtra(EXTRA_INITIAL_ROLE) ?: sessionManager.userRole

        updateSelectionUI()

        binding.cardRoleFriend.setOnClickListener {
            selectedRole = User.ROLE_FRIEND
            updateSelectionUI()
        }

        binding.cardRoleViewer.setOnClickListener {
            selectedRole = User.ROLE_VIEWER
            updateSelectionUI()
        }

        binding.cardRoleBoth.setOnClickListener {
            selectedRole = User.ROLE_BOTH
            updateSelectionUI()
        }

        binding.btnContinueWithRole.setOnClickListener {
            sessionManager.userRole = selectedRole

            val intent = Intent(this, ProfileSetupActivity::class.java).apply {
                putExtra(ProfileSetupActivity.EXTRA_UID, uid)
                putExtra(ProfileSetupActivity.EXTRA_PHONE, phoneNumber)
                putExtra(ProfileSetupActivity.EXTRA_ROLE, selectedRole)
            }
            startActivity(intent)
        }
    }

    private fun updateSelectionUI() {
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val dividerColor = ContextCompat.getColor(this, R.color.divider_color)

        // Reset all
        binding.cardRoleFriend.strokeColor = dividerColor
        binding.cardRoleFriend.strokeWidth = 2
        binding.cardRoleViewer.strokeColor = dividerColor
        binding.cardRoleViewer.strokeWidth = 2
        binding.cardRoleBoth.strokeColor = dividerColor
        binding.cardRoleBoth.strokeWidth = 2

        when (selectedRole) {
            User.ROLE_FRIEND -> {
                binding.cardRoleFriend.strokeColor = primaryColor
                binding.cardRoleFriend.strokeWidth = 5
            }
            User.ROLE_VIEWER -> {
                binding.cardRoleViewer.strokeColor = primaryColor
                binding.cardRoleViewer.strokeWidth = 5
            }
            User.ROLE_BOTH -> {
                binding.cardRoleBoth.strokeColor = primaryColor
                binding.cardRoleBoth.strokeWidth = 5
            }
        }
    }

    companion object {
        const val EXTRA_UID = "extra_uid"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_INITIAL_ROLE = "extra_initial_role"
    }
}
