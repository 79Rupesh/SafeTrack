package com.safetrack.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.ValueEventListener
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.contacts.AddContactActivity
import com.safetrack.contacts.ConnectionRequestsActivity
import com.safetrack.contacts.TrustedContactsAdapter
import com.safetrack.data.model.LocationData
import com.safetrack.data.model.TrustedContact
import com.safetrack.databinding.ActivityFriendDashboardBinding
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.location.DeviceLocationManager
import com.safetrack.location.LocationHistoryActivity
import com.safetrack.location.LocationPermissionManager
import com.safetrack.location.LocationRepository
import com.safetrack.location.LocationStatusManager
import com.safetrack.settings.SettingsActivity
import com.safetrack.sos.SosActivity
import com.safetrack.tracking.LocationTrackingService
import com.safetrack.ui.BatteryOptimizationActivity
import com.safetrack.ui.PermissionSetupActivity
import com.safetrack.utils.NetworkUtils
import com.safetrack.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FriendDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendDashboardBinding
    private lateinit var sessionManager: SessionManager
    private val firebaseRepo = FirebaseRepository()
    private lateinit var contactsAdapter: TrustedContactsAdapter
    private lateinit var deviceLocationManager: DeviceLocationManager
    private lateinit var locationRepository: LocationRepository
    private var contactsListener: ValueEventListener? = null

    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                LocationTrackingService.ACTION_LOCATION_BROADCAST -> {
                    val lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_LATITUDE, 0.0)
                    val lng = intent.getDoubleExtra(LocationTrackingService.EXTRA_LONGITUDE, 0.0)
                    val accuracy = intent.getFloatExtra(LocationTrackingService.EXTRA_ACCURACY, 0f)
                    val timestamp = intent.getLongExtra(LocationTrackingService.EXTRA_TIMESTAMP, System.currentTimeMillis())
                    val uploadSuccess = intent.getBooleanExtra(LocationTrackingService.EXTRA_UPLOAD_SUCCESS, false)
                    val lastUploadTime = intent.getLongExtra(
                        LocationTrackingService.EXTRA_LAST_UPLOAD_TIME,
                        sessionManager.lastUploadTime
                    )
                    val isOnline = intent.getBooleanExtra(
                        LocationTrackingService.EXTRA_IS_ONLINE,
                        NetworkUtils.isNetworkAvailable(context)
                    )

                    updateLocationUI(lat, lng, accuracy, timestamp)
                    updateNetworkAndUploadUI(isOnline, uploadSuccess, lastUploadTime)
                }
                LocationTrackingService.ACTION_STATUS_BROADCAST -> {
                    val status = intent.getStringExtra(LocationTrackingService.EXTRA_SHARING_STATUS)
                    updateStatusUI(status)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as SafeTrackApp
        sessionManager = app.sessionManager
        locationRepository = LocationRepository(app.database.locationDao(), firebaseRepo)
        deviceLocationManager = DeviceLocationManager(this)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupListeners()
        loadInitialState()
        observeContacts()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(LocationTrackingService.ACTION_LOCATION_BROADCAST)
            addAction(LocationTrackingService.ACTION_STATUS_BROADCAST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationUpdateReceiver, filter)
        }

        binding.tvMyInviteCode.text = "Your Invite Code: ${sessionManager.inviteCode}"
        checkGpsStatus()
        updateSharingStateDisplay()
        updateNetworkAndUploadUI(
            NetworkUtils.isNetworkAvailable(this),
            uploadSuccess = false,
            lastUploadTime = sessionManager.lastUploadTime
        )
        if (sessionManager.isSosActive) {
            binding.btnSos.text = "SOS\nACTIVE"
        } else {
            binding.btnSos.text = "SOS"
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(locationUpdateReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    private fun setupRecyclerView() {
        contactsAdapter = TrustedContactsAdapter { contact ->
            confirmRevokeContact(contact)
        }
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = contactsAdapter
    }

    private fun setupListeners() {
        binding.btnStartSharing.setOnClickListener {
            startSharingFlow()
        }

        binding.btnTogglePause.setOnClickListener {
            if (sessionManager.isSharingPaused) {
                resumeSharingFlow()
            } else {
                pauseSharingFlow()
            }
        }

        binding.btnStopSharing.setOnClickListener {
            stopSharingFlow()
        }

        binding.btnSos.setOnClickListener {
            startActivity(Intent(this, SosActivity::class.java))
        }

        binding.btnCall112.setOnClickListener {
            SosActivity.dialEmergencyNumber(this)
        }

        binding.btnShareCode.setOnClickListener {
            val intent = Intent(this, AddContactActivity::class.java)
            startActivity(intent)
        }

        binding.btnBatteryGuide.setOnClickListener {
            startActivity(Intent(this, BatteryOptimizationActivity::class.java))
        }

        binding.tvGpsStatus.setOnClickListener {
            if (!LocationPermissionManager.isLocationServiceEnabled(this)) {
                LocationPermissionManager.openLocationSettings(this)
            }
        }
    }

    private fun checkGpsStatus() {
        val isGpsEnabled = LocationPermissionManager.isLocationServiceEnabled(this)
        val hasPermission = LocationPermissionManager.hasAnyLocationPermission(this)
        val isPrecise = LocationPermissionManager.hasFineLocationPermission(this)

        when {
            !hasPermission -> {
                binding.tvGpsStatus.text = "Location Permission: Not Granted (Tap to grant)"
                binding.tvGpsStatus.setTextColor(ContextCompat.getColor(this, R.color.safety_red))
                binding.tvGpsStatus.setOnClickListener {
                    startActivity(Intent(this, PermissionSetupActivity::class.java))
                }
            }
            !isGpsEnabled -> {
                binding.tvGpsStatus.text = "GPS: Location Services Disabled on device (Tap to enable)"
                binding.tvGpsStatus.setTextColor(ContextCompat.getColor(this, R.color.safety_amber))
                binding.tvGpsStatus.setOnClickListener {
                    LocationPermissionManager.openLocationSettings(this)
                }
            }
            !isPrecise -> {
                binding.tvGpsStatus.text = "GPS: Approximate Location active (Tap to grant precise)"
                binding.tvGpsStatus.setTextColor(ContextCompat.getColor(this, R.color.safety_amber))
                binding.tvGpsStatus.setOnClickListener {
                    startActivity(Intent(this, PermissionSetupActivity::class.java))
                }
            }
            else -> {
                binding.tvGpsStatus.text = "GPS: Hardware Active (High Accuracy)"
                binding.tvGpsStatus.setTextColor(ContextCompat.getColor(this, R.color.safety_green))
                binding.tvGpsStatus.setOnClickListener(null)
            }
        }
    }

    private fun startSharingFlow() {
        if (!LocationPermissionManager.hasAnyLocationPermission(this)) {
            startActivity(Intent(this, PermissionSetupActivity::class.java))
            return
        }

        if (!LocationPermissionManager.isLocationServiceEnabled(this)) {
            Toast.makeText(this, "Please enable device location services (GPS) to share location.", Toast.LENGTH_LONG).show()
            LocationPermissionManager.openLocationSettings(this)
            return
        }

        sessionManager.isSharingActive = true
        sessionManager.isSharingPaused = false
        updateStatusUI(LocationData.STATUS_ACTIVE)

        // Start Foreground Service
        LocationTrackingService.startService(this)

        // Immediate one-shot fix for UI instant responsiveness
        deviceLocationManager.getCurrentLocation(
            onLocationReceived = { locData ->
                updateLocationUI(locData.latitude, locData.longitude, locData.accuracy, locData.timestamp)
            },
            onError = {
                checkGpsStatus()
            }
        )

        val userId = sessionManager.userId ?: return
        lifecycleScope.launch {
            firebaseRepo.updateSharingStatus(userId, LocationData.STATUS_ACTIVE)
        }
    }

    private fun pauseSharingFlow() {
        sessionManager.isSharingPaused = true
        updateStatusUI(LocationData.STATUS_PAUSED)

        LocationTrackingService.pauseService(this)

        val userId = sessionManager.userId ?: return
        lifecycleScope.launch {
            firebaseRepo.updateSharingStatus(userId, LocationData.STATUS_PAUSED)
        }
    }

    private fun resumeSharingFlow() {
        sessionManager.isSharingPaused = false
        updateStatusUI(LocationData.STATUS_ACTIVE)

        LocationTrackingService.resumeService(this)

        val userId = sessionManager.userId ?: return
        lifecycleScope.launch {
            firebaseRepo.updateSharingStatus(userId, LocationData.STATUS_ACTIVE)
        }
    }

    private fun stopSharingFlow() {
        sessionManager.isSharingActive = false
        sessionManager.isSharingPaused = false
        updateStatusUI(LocationData.STATUS_STOPPED)

        LocationTrackingService.stopService(this)

        val userId = sessionManager.userId ?: return
        lifecycleScope.launch {
            firebaseRepo.updateSharingStatus(userId, LocationData.STATUS_STOPPED)
            firebaseRepo.clearRemoteHistory(userId)
        }
    }

    private fun loadInitialState() {
        binding.tvMyInviteCode.text = "Your Invite Code: ${sessionManager.inviteCode}"

        if (sessionManager.isSharingActive && !sessionManager.isSharingPaused) {
            updateStatusUI(LocationData.STATUS_ACTIVE)
        } else if (sessionManager.isSharingPaused) {
            updateStatusUI(LocationData.STATUS_PAUSED)
        } else {
            updateStatusUI(LocationData.STATUS_STOPPED)
        }

        if (sessionManager.lastUpdateTime > 0) {
            updateLocationUI(
                sessionManager.lastLatitude.toDouble(),
                sessionManager.lastLongitude.toDouble(),
                sessionManager.lastAccuracy,
                sessionManager.lastUpdateTime
            )
        }
    }

    private fun observeContacts() {
        val userId = sessionManager.userId ?: return
        contactsListener = firebaseRepo.listenToApprovedContacts(userId) { contacts ->
            binding.tvContactsTitle.text = "Trusted Contacts (${contacts.size})"
            if (contacts.isEmpty()) {
                binding.tvEmptyContacts.visibility = View.VISIBLE
                binding.rvContacts.visibility = View.GONE
            } else {
                binding.tvEmptyContacts.visibility = View.GONE
                binding.rvContacts.visibility = View.VISIBLE
                contactsAdapter.submitList(contacts)
            }
        }
    }

    private fun confirmRevokeContact(contact: TrustedContact) {
        AlertDialog.Builder(this)
            .setTitle("Revoke Access")
            .setMessage("Do you want to stop sharing your location with ${contact.name}? They will no longer be able to view your location.")
            .setPositiveButton("Revoke") { _, _ ->
                val userId = sessionManager.userId ?: return@setPositiveButton
                lifecycleScope.launch {
                    val result = firebaseRepo.revokeContact(userId, contact.contactUserId)
                    if (result.isSuccess) {
                        Toast.makeText(this@FriendDashboardActivity, "Access revoked.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatusUI(status: String?) {
        when (status) {
            LocationData.STATUS_ACTIVE -> {
                binding.tvSharingStatus.text = getString(R.string.sharing_status_active)
                binding.cardSharingStatus.strokeColor = ContextCompat.getColor(this, R.color.safety_green)
                binding.ivStatusDot.setImageResource(R.drawable.ic_check)
                binding.ivStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.safety_green))
                binding.btnTogglePause.text = getString(R.string.btn_pause_sharing)
                binding.btnTogglePause.visibility = View.VISIBLE
                binding.btnStopSharing.visibility = View.VISIBLE
                binding.btnStartSharing.visibility = View.GONE
            }
            LocationData.STATUS_PAUSED -> {
                binding.tvSharingStatus.text = getString(R.string.sharing_status_paused)
                binding.cardSharingStatus.strokeColor = ContextCompat.getColor(this, R.color.safety_amber)
                binding.ivStatusDot.setImageResource(R.drawable.ic_refresh)
                binding.ivStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.safety_amber))
                binding.btnTogglePause.text = getString(R.string.btn_resume_sharing)
                binding.btnTogglePause.visibility = View.VISIBLE
                binding.btnStopSharing.visibility = View.VISIBLE
                binding.btnStartSharing.visibility = View.GONE
            }
            else -> {
                binding.tvSharingStatus.text = getString(R.string.sharing_status_stopped)
                binding.cardSharingStatus.strokeColor = ContextCompat.getColor(this, R.color.safety_red)
                binding.ivStatusDot.setImageResource(R.drawable.ic_close)
                binding.ivStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.safety_red))
                binding.btnTogglePause.visibility = View.GONE
                binding.btnStopSharing.visibility = View.GONE
                binding.btnStartSharing.visibility = View.VISIBLE
            }
        }
    }

    private fun updateLocationUI(lat: Double, lng: Double, accuracy: Float, timestamp: Long) {
        val relative = LocationStatusManager.formatRelativeTime(timestamp)
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
        binding.tvLastLocationUpdate.text = "Last GPS Reading: $relative (${sdf.format(Date(timestamp))})"
        binding.tvLocationAccuracy.text = String.format(
            Locale.US,
            "Accuracy: ±%.1f meters (Lat: %.5f, Lng: %.5f)",
            accuracy,
            lat,
            lng
        )
    }

    private fun updateNetworkAndUploadUI(isOnline: Boolean, uploadSuccess: Boolean, lastUploadTime: Long) {
        if (isOnline) {
            binding.tvInternetStatus.text = "Internet: Connected (Online)"
            binding.tvInternetStatus.setTextColor(ContextCompat.getColor(this, R.color.safety_green))
        } else {
            binding.tvInternetStatus.text = "Internet: Offline (Location queued to Room)"
            binding.tvInternetStatus.setTextColor(ContextCompat.getColor(this, R.color.safety_amber))
        }

        if (uploadSuccess) {
            binding.tvUploadStatus.text = "Firebase Upload: Synced Just now"
            binding.tvUploadStatus.setTextColor(ContextCompat.getColor(this, R.color.safety_green))
        } else if (lastUploadTime > 0) {
            val relTime = LocationStatusManager.formatRelativeTime(lastUploadTime)
            binding.tvUploadStatus.text = "Firebase Upload: Last synced $relTime"
            binding.tvUploadStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            binding.tvUploadStatus.text = "Firebase Upload: Pending upload"
            binding.tvUploadStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun updateSharingStateDisplay() {
        if (sessionManager.isSharingActive) {
            if (sessionManager.isSharingPaused) {
                updateStatusUI(LocationData.STATUS_PAUSED)
            } else {
                updateStatusUI(LocationData.STATUS_ACTIVE)
            }
        } else {
            updateStatusUI(LocationData.STATUS_STOPPED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val userId = sessionManager.userId ?: sessionManager.uid
        if (!userId.isNullOrBlank()) {
            contactsListener?.let {
                firebaseRepo.removeListener("connections/$userId", it)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_friend_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_requests -> {
                startActivity(Intent(this, ConnectionRequestsActivity::class.java))
                true
            }
            R.id.action_history -> {
                startActivity(Intent(this, LocationHistoryActivity::class.java))
                true
            }
            R.id.action_switch_mode -> {
                startActivity(Intent(this, ViewerDashboardActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
