package com.safetrack.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.safetrack.SafeTrackApp
import com.safetrack.auth.AuthActivity
import com.safetrack.auth.ProfileSetupActivity
import com.safetrack.dashboard.FriendDashboardActivity
import com.safetrack.dashboard.ViewerDashboardActivity
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivitySplashBinding
import com.safetrack.firebase.FirebaseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val firebaseRepo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            // Display splash for at least 1.2 seconds for brand perception
            delay(1200)
            verifySessionAndNavigate()
        }
    }

    private suspend fun verifySessionAndNavigate() {
        val sessionManager = (application as SafeTrackApp).sessionManager
        val auth = FirebaseAuth.getInstance()
        val firebaseUser = auth.currentUser

        if (firebaseUser == null) {
            // Not authenticated: navigate to Welcome/Login flow
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        val uid = firebaseUser.uid
        val cachedUser = sessionManager.toUser()

        // If local session is already valid, use it; otherwise fetch from Firebase
        val profile = if (cachedUser != null && cachedUser.name.isNotBlank()) {
            cachedUser
        } else {
            try {
                firebaseRepo.getUser(uid)
            } catch (e: Exception) {
                null
            }
        }

        if (profile != null && profile.name.isNotBlank()) {
            // User is authenticated and profile exists
            sessionManager.saveUser(profile)

            val destination = if (profile.role == User.ROLE_VIEWER) {
                ViewerDashboardActivity::class.java
            } else {
                FriendDashboardActivity::class.java
            }
            startActivity(Intent(this, destination))
        } else {
            // Authenticated via Firebase Phone Auth, but profile is not yet completed
            val intent = Intent(this, ProfileSetupActivity::class.java).apply {
                putExtra(ProfileSetupActivity.EXTRA_UID, uid)
                putExtra(ProfileSetupActivity.EXTRA_PHONE, firebaseUser.phoneNumber ?: "")
                putExtra(ProfileSetupActivity.EXTRA_ROLE, sessionManager.userRole)
            }
            startActivity(intent)
        }
        finish()
    }
}
