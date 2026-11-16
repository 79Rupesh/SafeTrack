package com.safetrack.sos

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.safetrack.SafeTrackApp
import com.safetrack.data.model.SosAlert
import com.safetrack.databinding.ActivitySosBinding
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.utils.NetworkUtils
import com.safetrack.utils.SessionManager
import kotlinx.coroutines.launch
import java.util.Locale

class SosActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySosBinding
    private lateinit var sessionManager: SessionManager
    private val firebaseRepo = FirebaseRepository()

    private var holdTimer: CountDownTimer? = null
    private var isHolding = false
    private var toneGenerator: ToneGenerator? = null
    private var isSirenPlaying = false
    private var isTorchOn = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = (application as SafeTrackApp).sessionManager
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupHoldListener()
        setupButtons()
        checkActiveSosState()
    }

    private fun setupButtons() {
        binding.btnDirect112Call.setOnClickListener {
            dialEmergencyNumber(this)
        }

        binding.btnSendOfflineSms.setOnClickListener {
            dispatchOfflineSms()
        }

        binding.btnCancelSos.setOnClickListener {
            confirmCancelSos()
        }

        binding.btnToggleSiren.setOnClickListener {
            toggleSiren()
        }

        binding.btnToggleTorch.setOnClickListener {
            toggleTorch()
        }
    }

    private fun toggleSiren() {
        if (isSirenPlaying) {
            stopSiren()
        } else {
            startSiren()
        }
    }

    private fun startSiren() {
        try {
            isSirenPlaying = true
            binding.btnToggleSiren.text = "Stop Siren"
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 20000)
            Toast.makeText(this, "Emergency siren sounding!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Audio alarm unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSiren() {
        isSirenPlaying = false
        binding.btnToggleSiren.text = "Loud Siren"
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun toggleTorch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                if (cameraId != null) {
                    isTorchOn = !isTorchOn
                    cameraManager.setTorchMode(cameraId, isTorchOn)
                    binding.btnToggleTorch.text = if (isTorchOn) "Torch ON" else "Flashlight"
                    Toast.makeText(this, if (isTorchOn) "Emergency flashlight ON" else "Flashlight OFF", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Flashlight not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSiren()
        if (isTorchOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, false)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun checkActiveSosState() {
        if (sessionManager.isSosActive && !sessionManager.activeSosAlertId.isNullOrBlank()) {
            showSosActiveUI(
                sessionManager.lastLatitude.toDouble(),
                sessionManager.lastLongitude.toDouble()
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHoldListener() {
        binding.btnSosHold.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startHoldTimer()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelHoldTimer()
                    true
                }
                else -> false
            }
        }
    }

    private fun startHoldTimer() {
        isHolding = true
        binding.progressHold.visibility = View.VISIBLE
        binding.progressHold.progress = 0

        val totalDuration = 2000L // 2 seconds
        val interval = 50L

        holdTimer = object : CountDownTimer(totalDuration, interval) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = (((totalDuration - millisUntilFinished).toFloat() / totalDuration) * 100).toInt()
                binding.progressHold.progress = progress
                val secondsLeft = String.format(Locale.US, "%.1f", millisUntilFinished / 1000f)
                binding.tvCountdown.text = "Hold for $secondsLeft more seconds..."
            }

            override fun onFinish() {
                binding.progressHold.progress = 100
                binding.tvCountdown.text = "DISPATCHING SOS..."
                vibrateDevice()
                triggerSosAlert()
            }
        }.start()
    }

    private fun cancelHoldTimer() {
        if (isHolding) {
            holdTimer?.cancel()
            isHolding = false
            binding.progressHold.visibility = View.INVISIBLE
            binding.progressHold.progress = 0
            if (!sessionManager.isSosActive) {
                binding.tvCountdown.text = "Hold button for 2 seconds"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerSosAlert() {
        val userId = sessionManager.userId ?: "USER_SOS"
        val userName = sessionManager.userName ?: "SafeTrack Contact"
        val userPhone = sessionManager.phoneNumber ?: ""

        var lat = sessionManager.lastLatitude.toDouble()
        var lng = sessionManager.lastLongitude.toDouble()
        var acc = sessionManager.lastAccuracy

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    lat = loc.latitude
                    lng = loc.longitude
                    acc = loc.accuracy
                    sessionManager.lastLatitude = lat.toFloat()
                    sessionManager.lastLongitude = lng.toFloat()
                }
                dispatchFirebaseAlert(userId, userName, userPhone, lat, lng, acc)
            }.addOnFailureListener {
                dispatchFirebaseAlert(userId, userName, userPhone, lat, lng, acc)
            }
        } catch (e: Exception) {
            dispatchFirebaseAlert(userId, userName, userPhone, lat, lng, acc)
        }
    }

    private fun dispatchFirebaseAlert(
        userId: String,
        userName: String,
        userPhone: String,
        lat: Double,
        lng: Double,
        acc: Float
    ) {
        val batteryPct = getBatteryPercentage()
        val alert = SosAlert(
            senderId = userId,
            senderName = userName,
            senderPhone = userPhone,
            latitude = lat,
            longitude = lng,
            accuracy = acc,
            timestamp = System.currentTimeMillis(),
            status = SosAlert.STATUS_ACTIVE,
            message = "EMERGENCY: $userName has activated SOS distress at coordinates ($lat, $lng)!",
            batteryPct = batteryPct
        )

        val isOnline = NetworkUtils.isNetworkAvailable(this)

        lifecycleScope.launch {
            if (isOnline) {
                val result = firebaseRepo.triggerSos(alert)
                if (result.isSuccess) {
                    val alertId = result.getOrNull()
                    sessionManager.activeSosAlertId = alertId
                    sessionManager.isSosActive = true
                    showSosActiveUI(lat, lng)
                    Toast.makeText(this@SosActivity, "🚨 SOS Distress Broadcasted to All Contacts!", Toast.LENGTH_LONG).show()
                } else {
                    handleSosDispatchFailure(lat, lng, result.exceptionOrNull()?.localizedMessage)
                }
            } else {
                handleSosDispatchFailure(lat, lng, "No active internet connection")
            }
        }
    }

    private fun handleSosDispatchFailure(lat: Double, lng: Double, reason: String?) {
        sessionManager.isSosActive = true
        showSosActiveUI(lat, lng)
        Toast.makeText(this, "Internet unavailable. Please dispatch offline emergency SMS!", Toast.LENGTH_LONG).show()
        dispatchOfflineSms()
    }

    private fun showSosActiveUI(lat: Double, lng: Double) {
        binding.tvSosStatusHeader.text = "🚨 SOS ALERT BROADCASTING!"
        binding.tvSosStatusDesc.text = String.format(
            Locale.US,
            "Your emergency distress signals are active at coordinates (%.5f, %.5f). Your approved contacts are being alerted.",
            lat,
            lng
        )
        binding.btnCancelSos.visibility = View.VISIBLE
        binding.btnSosHold.visibility = View.GONE
        binding.tvCountdown.text = "EMERGENCY DISTRESS ACTIVE"
    }

    private fun confirmCancelSos() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Emergency SOS")
            .setMessage("Are you safe now? Canceling the alert will notify your trusted contacts that the emergency has been resolved.")
            .setPositiveButton("Yes, I am Safe") { _, _ ->
                cancelActiveSos()
            }
            .setNegativeButton("Keep SOS Active", null)
            .show()
    }

    private fun cancelActiveSos() {
        val alertId = sessionManager.activeSosAlertId
        val userId = sessionManager.userId ?: ""

        lifecycleScope.launch {
            if (!alertId.isNullOrBlank() && userId.isNotBlank()) {
                firebaseRepo.resolveSos(alertId, userId, "User reported safe")
            }
            sessionManager.isSosActive = false
            sessionManager.activeSosAlertId = null

            binding.tvSosStatusHeader.text = "EMERGENCY DISTRESS SYSTEM"
            binding.tvSosStatusDesc.text = "Press and hold the red button below for 2 seconds to dispatch immediate emergency distress signals to your trusted contacts."
            binding.btnCancelSos.visibility = View.GONE
            binding.btnSosHold.visibility = View.VISIBLE
            binding.tvCountdown.text = "Hold button for 2 seconds"
            Toast.makeText(this@SosActivity, "SOS alert canceled. Contacts notified you are safe.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dispatchOfflineSms() {
        val lat = sessionManager.lastLatitude.toDouble()
        val lng = sessionManager.lastLongitude.toDouble()
        val name = sessionManager.userName.takeIf { !it.isNullOrBlank() } ?: "SafeTrack User"
        val mapsUrl = "https://maps.google.com/?q=$lat,$lng"
        val message = "EMERGENCY SOS from $name via SafeTrack! I need immediate help. My current location is: $mapsUrl"

        val emergencyPhone = sessionManager.primaryEmergencyPhone

        val smsUri = if (emergencyPhone.isNotBlank()) {
            Uri.parse("smsto:$emergencyPhone")
        } else {
            Uri.parse("smsto:")
        }

        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", message)
        }

        try {
            startActivity(smsIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "SMS application not available on device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            -1
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateDevice() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(longArrayOf(0, 300, 150, 300, 150, 500), -1)
    }

    companion object {
        fun dialEmergencyNumber(context: Context) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:112")
            }
            context.startActivity(intent)
        }
    }
}
