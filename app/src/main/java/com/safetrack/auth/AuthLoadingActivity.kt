package com.safetrack.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.safetrack.SafeTrackApp
import com.safetrack.dashboard.FriendDashboardActivity
import com.safetrack.dashboard.ViewerDashboardActivity
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivityAuthLoadingBinding
import com.safetrack.firebase.FirebaseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthLoadingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthLoadingBinding
    private val firebaseRepo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            val user = try {
                firebaseRepo.getUser(currentUser.uid)
            } catch (e: Exception) {
                null
            }

            val sessionManager = (application as SafeTrackApp).sessionManager

            if (user != null && user.name.isNotBlank()) {
                sessionManager.saveUser(user)
                val destination = if (user.role == User.ROLE_VIEWER) {
                    ViewerDashboardActivity::class.java
                } else {
                    FriendDashboardActivity::class.java
                }
                startActivity(Intent(this@AuthLoadingActivity, destination))
            } else {
                val intent = Intent(this@AuthLoadingActivity, ProfileSetupActivity::class.java).apply {
                    putExtra(ProfileSetupActivity.EXTRA_UID, currentUser.uid)
                    putExtra(ProfileSetupActivity.EXTRA_PHONE, currentUser.phoneNumber ?: "")
                    putExtra(ProfileSetupActivity.EXTRA_ROLE, sessionManager.userRole)
                }
                startActivity(intent)
            }
            finish()
        }
    }
}
