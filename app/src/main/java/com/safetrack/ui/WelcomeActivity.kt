package com.safetrack.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.auth.AuthActivity
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private var selectedRole = User.ROLE_FRIEND

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateSelectionUI()

        binding.cardFriendMode.setOnClickListener {
            selectedRole = User.ROLE_FRIEND
            updateSelectionUI()
        }

        binding.cardViewerMode.setOnClickListener {
            selectedRole = User.ROLE_VIEWER
            updateSelectionUI()
        }

        binding.btnContinue.setOnClickListener {
            val sessionManager = (application as SafeTrackApp).sessionManager
            sessionManager.userRole = selectedRole

            val intent = Intent(this, AuthActivity::class.java).apply {
                putExtra("SELECTED_ROLE", selectedRole)
            }
            startActivity(intent)
        }
    }

    private fun updateSelectionUI() {
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val dividerColor = ContextCompat.getColor(this, R.color.divider_color)

        if (selectedRole == User.ROLE_FRIEND) {
            binding.cardFriendMode.strokeColor = primaryColor
            binding.cardFriendMode.strokeWidth = 4
            binding.cardViewerMode.strokeColor = dividerColor
            binding.cardViewerMode.strokeWidth = 2
        } else {
            binding.cardFriendMode.strokeColor = dividerColor
            binding.cardFriendMode.strokeWidth = 2
            binding.cardViewerMode.strokeColor = primaryColor
            binding.cardViewerMode.strokeWidth = 4
        }
    }
}
