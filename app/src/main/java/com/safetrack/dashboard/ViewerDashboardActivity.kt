package com.safetrack.dashboard

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.ValueEventListener
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.contacts.AddContactActivity
import com.safetrack.contacts.ConnectionRequestsActivity
import com.safetrack.data.model.LocationData
import com.safetrack.data.model.SosAlert
import com.safetrack.data.model.TrustedContact
import com.safetrack.databinding.ActivityViewerDashboardBinding
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.maps.GoogleMapsActivity
import com.safetrack.settings.SettingsActivity
import com.safetrack.sos.SosActivity
import com.safetrack.utils.SessionManager
import kotlinx.coroutines.launch

class ViewerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerDashboardBinding
    private lateinit var sessionManager: SessionManager
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: ViewerFriendsAdapter

    private val contactsList = mutableListOf<TrustedContact>()
    private val locationsMap = mutableMapOf<String, LocationData>()
    private var activeSosAlert: SosAlert? = null

    private var contactsListener: ValueEventListener? = null
    private var sosListener: ValueEventListener? = null
    private var requestsListener: ValueEventListener? = null
    private val locationListenersMap = mutableMapOf<String, ValueEventListener>()

    private var emergencyRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isAlarmMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = (application as SafeTrackApp).sessionManager
        setSupportActionBar(binding.toolbar)

        initAlarmAndVibrator()
        setupRecyclerView()
        setupListeners()
        loadData()
    }

    private fun initAlarmAndVibrator() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            emergencyRingtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
        } catch (e: Exception) {
            // Audio manager error
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun setupRecyclerView() {
        adapter = ViewerFriendsAdapter(
            onViewMapClick = { contact, location ->
                val intent = Intent(this, GoogleMapsActivity::class.java).apply {
                    putExtra(GoogleMapsActivity.EXTRA_FRIEND_USER_ID, contact.contactUserId)
                    putExtra(GoogleMapsActivity.EXTRA_FRIEND_NAME, contact.name)
                    putExtra(GoogleMapsActivity.EXTRA_FRIEND_PHONE, contact.phone)
                }
                startActivity(intent)
            },
            onRemoveClick = { contact ->
                confirmRemoveFriend(contact)
            }
        )
        binding.rvFriends.layoutManager = LinearLayoutManager(this)
        binding.rvFriends.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.btnAddFriend.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }

        binding.btnReviewRequests.setOnClickListener {
            startActivity(Intent(this, ConnectionRequestsActivity::class.java))
        }

        binding.btnViewSosLocation.setOnClickListener {
            stopEmergencyAlarm()
            activeSosAlert?.let { alert ->
                val intent = Intent(this, GoogleMapsActivity::class.java).apply {
                    putExtra(GoogleMapsActivity.EXTRA_FRIEND_USER_ID, alert.senderId)
                    putExtra(GoogleMapsActivity.EXTRA_FRIEND_NAME, alert.senderName)
                    putExtra(GoogleMapsActivity.EXTRA_FRIEND_PHONE, alert.senderPhone)
                    putExtra(GoogleMapsActivity.EXTRA_IS_SOS, true)
                }
                startActivity(intent)
            }
        }

        binding.btnMuteAlarm.setOnClickListener {
            isAlarmMuted = true
            stopEmergencyAlarm()
            Toast.makeText(this, "Emergency siren muted.", Toast.LENGTH_SHORT).show()
        }

        binding.btnCallSosFriend.setOnClickListener {
            stopEmergencyAlarm()
            val phone = activeSosAlert?.senderPhone
            if (!phone.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Phone number unavailable.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCall112Sos.setOnClickListener {
            stopEmergencyAlarm()
            SosActivity.dialEmergencyNumber(this)
        }
    }

    private fun removeAllFirebaseListeners() {
        val myUid = sessionManager.uid ?: sessionManager.userId
        if (!myUid.isNullOrBlank()) {
            contactsListener?.let {
                firebaseRepo.removeListener("connections/$myUid", it)
            }
            requestsListener?.let {
                firebaseRepo.removeListener("incomingRequests/$myUid", it)
            }
        }
        contactsListener = null
        requestsListener = null

        sosListener?.let {
            firebaseRepo.removeListener("activeSos", it)
        }
        sosListener = null

        locationListenersMap.forEach { (friendUid, listener) ->
            firebaseRepo.removeListener("locations/$friendUid", listener)
        }
        locationListenersMap.clear()
    }

    private fun loadData() {
        val userId = sessionManager.userId ?: return
        removeAllFirebaseListeners()

        // 1. Listen for approved friends
        contactsListener = firebaseRepo.listenToApprovedContacts(userId) { contacts ->
            contactsList.clear()
            contactsList.addAll(contacts)

            if (contacts.isEmpty()) {
                binding.tvEmptyFriends.visibility = View.VISIBLE
                binding.rvFriends.visibility = View.GONE
            } else {
                binding.tvEmptyFriends.visibility = View.GONE
                binding.rvFriends.visibility = View.VISIBLE

                // Stream location updates for each friend
                contacts.forEach { contact ->
                    val listener = firebaseRepo.listenToUserLocation(contact.contactUserId) { location ->
                        if (location != null) {
                            locationsMap[contact.contactUserId] = location
                            adapter.submitData(contactsList, locationsMap)
                        }
                    }
                    locationListenersMap[contact.contactUserId] = listener
                }
                adapter.submitData(contactsList, locationsMap)

                // 2. Stream active SOS alerts for all approved friends
                val friendIds = contacts.map { it.contactUserId }.toSet()
                sosListener?.let { firebaseRepo.removeListener("activeSos", it) }
                sosListener = firebaseRepo.listenToActiveSosAlerts(friendIds) { alerts ->
                    if (alerts.isNotEmpty()) {
                        val newAlert = alerts.first()
                        val isNewDistress = activeSosAlert == null || activeSosAlert?.alertId != newAlert.alertId
                        activeSosAlert = newAlert
                        binding.cardSosBanner.visibility = View.VISIBLE
                        binding.tvSosBannerTitle.text = "🚨 EMERGENCY: ${newAlert.senderName} TRIGGERED SOS!"
                        binding.tvSosBannerDesc.text = newAlert.message

                        if (isNewDistress) {
                            isAlarmMuted = false
                            playEmergencyAlarm()
                        }
                    } else {
                        if (activeSosAlert != null) {
                            Toast.makeText(this@ViewerDashboardActivity, "Emergency resolved. Contact is safe.", Toast.LENGTH_LONG).show()
                        }
                        activeSosAlert = null
                        stopEmergencyAlarm()
                        binding.cardSosBanner.visibility = View.GONE
                    }
                }
            }
        }

        // 3. Listen for incoming pending connection requests
        requestsListener = firebaseRepo.listenToIncomingRequests(userId) { requests ->
            if (requests.isNotEmpty()) {
                binding.cardPendingRequests.visibility = View.VISIBLE
                binding.tvPendingRequestsText.text = "You have ${requests.size} pending connection request(s)"
            } else {
                binding.cardPendingRequests.visibility = View.GONE
            }
        }
    }

    private fun playEmergencyAlarm() {
        if (isAlarmMuted) return
        try {
            emergencyRingtone?.let {
                if (!it.isPlaying) {
                    it.play()
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val pattern = longArrayOf(0, 600, 200, 600, 200, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun stopEmergencyAlarm() {
        try {
            emergencyRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun confirmRemoveFriend(contact: TrustedContact) {
        AlertDialog.Builder(this)
            .setTitle("Remove Contact")
            .setMessage("Do you want to disconnect from ${contact.name}?")
            .setPositiveButton("Remove") { _, _ ->
                val userId = sessionManager.userId ?: return@setPositiveButton
                lifecycleScope.launch {
                    val result = firebaseRepo.revokeContact(userId, contact.contactUserId)
                    if (result.isSuccess) {
                        Toast.makeText(this@ViewerDashboardActivity, "Removed contact.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        stopEmergencyAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEmergencyAlarm()
        removeAllFirebaseListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_viewer_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_requests -> {
                startActivity(Intent(this, ConnectionRequestsActivity::class.java))
                true
            }
            R.id.action_switch_mode -> {
                startActivity(Intent(this, FriendDashboardActivity::class.java))
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
