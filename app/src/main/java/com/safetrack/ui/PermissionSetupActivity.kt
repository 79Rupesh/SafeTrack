package com.safetrack.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.safetrack.SafeTrackApp
import com.safetrack.dashboard.FriendDashboardActivity
import com.safetrack.dashboard.ViewerDashboardActivity
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivityPermissionSetupBinding
import com.safetrack.location.LocationPermissionManager

class PermissionSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionSetupBinding

    private val requestForegroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineGranted) {
                Toast.makeText(this, "Precise location permission granted.", Toast.LENGTH_SHORT).show()
                checkBackgroundLocationPermission()
            } else if (coarseGranted) {
                Toast.makeText(this, "Approximate location granted. Precise location provides highest safety.", Toast.LENGTH_LONG).show()
                checkBackgroundLocationPermission()
            } else {
                showPermissionDeniedDialog()
            }
        }

    private val requestBackgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Background location access granted.", Toast.LENGTH_SHORT).show()
            }
            checkNotificationPermission()
        }

    private val requestNotificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            checkBatteryOptimization()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener {
            startPermissionFlow()
        }

        binding.btnOpenAppSettings.setOnClickListener {
            LocationPermissionManager.openAppSettings(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (LocationPermissionManager.hasFineLocationPermission(this)) {
            binding.btnGrantPermissions.text = "All Essential Permissions Granted"
            binding.btnOpenAppSettings.visibility = View.GONE
        }
    }

    private fun startPermissionFlow() {
        if (!LocationPermissionManager.hasAnyLocationPermission(this)) {
            requestForegroundLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            checkBackgroundLocationPermission()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!LocationPermissionManager.hasBackgroundLocationPermission(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Background Location Permission")
                    .setMessage("SafeTrack requires 'Allow all the time' location access to share your coordinates in the background when emergency situations arise or when your screen is closed.")
                    .setPositiveButton("Continue to Permission") { _, _ ->
                        requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("Skip for Now") { _, _ ->
                        checkNotificationPermission()
                    }
                    .show()
                return
            }
        }
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkBatteryOptimization()
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        navigateToDashboard()
    }

    private fun showPermissionDeniedDialog() {
        binding.btnOpenAppSettings.visibility = View.VISIBLE
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("SafeTrack cannot transmit or view safety coordinates without location access. Please grant Precise Location in App Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                LocationPermissionManager.openAppSettings(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToDashboard() {
        val sessionManager = (application as SafeTrackApp).sessionManager
        val destination = if (sessionManager.userRole == User.ROLE_VIEWER) {
            ViewerDashboardActivity::class.java
        } else {
            FriendDashboardActivity::class.java
        }

        val intent = Intent(this, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
