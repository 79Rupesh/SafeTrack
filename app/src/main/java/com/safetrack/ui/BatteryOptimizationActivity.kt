package com.safetrack.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.databinding.ActivityBatteryOptimizationBinding
import com.safetrack.utils.SessionManager

class BatteryOptimizationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatteryOptimizationBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatteryOptimizationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = (application as SafeTrackApp).sessionManager
        sessionManager.batteryOptimizationAcknowledged = true

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupListeners()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupListeners() {
        binding.btnRequestExemption.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        binding.btnOpenDeviceSettings.setOnClickListener {
            openAppBatterySettings()
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun updateStatus() {
        val isExempt = isIgnoringBatteryOptimizations()

        if (isExempt) {
            binding.cardStatus.strokeColor = ContextCompat.getColor(this, R.color.safety_green)
            binding.ivStatusIcon.setImageResource(R.drawable.ic_check)
            binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.safety_green))
            binding.tvExemptionStatus.text = getString(R.string.battery_optimization_status_exempt)
            binding.tvStatusDesc.text = "SafeTrack is permitted to transmit background location fixes when sharing is active without Doze interruption."
            binding.btnRequestExemption.visibility = View.GONE
        } else {
            binding.cardStatus.strokeColor = ContextCompat.getColor(this, R.color.safety_amber)
            binding.ivStatusIcon.setImageResource(R.drawable.ic_refresh)
            binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.safety_amber))
            binding.tvExemptionStatus.text = getString(R.string.battery_optimization_status_restricted)
            binding.tvStatusDesc.text = "Android may delay background location updates when the screen is turned off or battery saver is active."
            binding.btnRequestExemption.visibility = View.VISIBLE
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general battery settings
                openAppBatterySettings()
            }
        } else {
            Toast.makeText(this, "Battery optimization is not restricted on this Android version.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            val generalIntent = Intent(Settings.ACTION_SETTINGS)
            startActivity(generalIntent)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
