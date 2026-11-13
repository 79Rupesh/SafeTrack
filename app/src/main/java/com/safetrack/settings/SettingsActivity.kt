package com.safetrack.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.auth.EditProfileActivity
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivitySettingsBinding
import com.safetrack.tracking.LocationTrackingService
import com.safetrack.ui.BatteryOptimizationActivity
import com.safetrack.ui.PrivacyPolicyActivity
import com.safetrack.ui.WelcomeActivity
import com.safetrack.utils.SessionManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = (application as SafeTrackApp).sessionManager
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    private fun loadSettings() {
        binding.tvSettingsUserName.text = sessionManager.userName ?: "SafeTrack User"
        binding.tvSettingsUserPhone.text = sessionManager.phoneNumber ?: ""
        binding.tvSettingsUserRole.text = "Mode: ${User.getRoleDisplayName(sessionManager.userRole)}"

        when (sessionManager.updateIntervalMs) {
            15_000L -> binding.rb15s.isChecked = true
            30_000L -> binding.rb30s.isChecked = true
            60_000L -> binding.rb60s.isChecked = true
            300_000L -> binding.rb5m.isChecked = true
            else -> binding.rb30s.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        binding.rgInterval.setOnCheckedChangeListener { _, checkedId ->
            val intervalMs = when (checkedId) {
                R.id.rb15s -> 15_000L
                R.id.rb30s -> 30_000L
                R.id.rb60s -> 60_000L
                R.id.rb5m -> 300_000L
                else -> 30_000L
            }
            sessionManager.updateIntervalMs = intervalMs
            Toast.makeText(this, "Updated tracking interval", Toast.LENGTH_SHORT).show()
        }

        binding.btnOpenBatterySettings.setOnClickListener {
            startActivity(Intent(this, BatteryOptimizationActivity::class.java))
        }

        binding.btnOpenPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            confirmLogout()
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e2: Exception) {
                    Toast.makeText(this, "Please open device Settings -> Battery -> Battery Optimization.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out? Location sharing will be stopped immediately.")
            .setPositiveButton("Sign Out") { _, _ ->
                LocationTrackingService.stopService(this)
                try {
                    FirebaseAuth.getInstance().signOut()
                } catch (e: Exception) {
                    // Ignore
                }
                sessionManager.clear()
                val intent = Intent(this, WelcomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
