package com.safetrack.maps

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.google.firebase.database.ValueEventListener
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.data.model.LocationData
import com.safetrack.data.model.TrustedContact
import com.safetrack.databinding.ActivityGoogleMapsBinding
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.location.LocationStatusManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoogleMapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityGoogleMapsBinding
    private var googleMap: GoogleMap? = null
    private val firebaseRepo = FirebaseRepository()

    private var activeFriendUid: String = ""
    private var activeFriendName: String = ""
    private var activeFriendPhone: String = ""
    private var currentlyListeningUid: String? = null
    private var isSos: Boolean = false

    private var currentMarker: Marker? = null
    private var accuracyCircle: Circle? = null
    private var lastLocation: LocationData? = null

    private var locationListener: ValueEventListener? = null
    private var contactsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoogleMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        activeFriendUid = intent.getStringExtra(EXTRA_FRIEND_USER_ID) ?: ""
        activeFriendName = intent.getStringExtra(EXTRA_FRIEND_NAME) ?: "Friend"
        activeFriendPhone = intent.getStringExtra(EXTRA_FRIEND_PHONE) ?: ""
        isSos = intent.getBooleanExtra(EXTRA_IS_SOS, false)

        binding.tvFriendName.text = activeFriendName
        binding.toolbar.title = if (isSos) "DISTRESS: $activeFriendName" else "$activeFriendName's Location"
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (isSos) {
            binding.layoutSosCallActions.visibility = View.VISIBLE
            binding.cardFriendDetails.strokeColor = ContextCompat.getColor(this, R.color.safety_red)
            binding.cardFriendDetails.strokeWidth = 4
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        setupButtons()
        checkMapsApiKey()
        loadApprovedFriendsChips()
    }

    private fun checkMapsApiKey() {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            if (apiKey.isNullOrBlank() || apiKey.contains("YOUR_GOOGLE_MAPS_API_KEY_HERE")) {
                binding.cardApiKeyWarning.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun setupButtons() {
        binding.btnRefreshLocation.setOnClickListener {
            switchActiveFriend(activeFriendUid, activeFriendName, activeFriendPhone)
            Toast.makeText(this, "Refreshed live coordinates for $activeFriendName", Toast.LENGTH_SHORT).show()
        }

        binding.btnOpenDirections.setOnClickListener {
            val loc = lastLocation
            if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                val gmmIntentUri = Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${loc.latitude},${loc.longitude}")
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                }
            } else {
                Toast.makeText(this, "Location coordinate is not yet available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCallFriendMap.setOnClickListener {
            if (activeFriendPhone.isNotBlank()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$activeFriendPhone")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Friend's phone number is unavailable.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCallPoliceMap.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:112")
            }
            startActivity(intent)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        googleMap?.uiSettings?.isCompassEnabled = true
        googleMap?.uiSettings?.isMapToolbarEnabled = true

        if (activeFriendUid.isNotBlank()) {
            listenForFriendLocation(activeFriendUid)
        }
    }

    private fun loadApprovedFriendsChips() {
        val sessionManager = (application as SafeTrackApp).sessionManager
        val myUid = sessionManager.uid ?: return

        contactsListener = firebaseRepo.listenToApprovedContacts(myUid) { contacts ->
            binding.chipGroupMapContacts.removeAllViews()

            if (contacts.size > 1) {
                binding.scrollMapContacts.visibility = View.VISIBLE
                for (contact in contacts) {
                    val chip = Chip(this).apply {
                        text = contact.name
                        isCheckable = true
                        isChecked = contact.contactUserId == activeFriendUid
                        setOnClickListener {
                            switchActiveFriend(contact.contactUserId, contact.name, contact.phone)
                        }
                    }
                    binding.chipGroupMapContacts.addView(chip)
                }
            } else {
                binding.scrollMapContacts.visibility = View.GONE
            }

            // Check if active friend has been revoked
            if (activeFriendUid.isNotBlank() && contacts.none { it.contactUserId == activeFriendUid }) {
                handleFriendRevoked()
            }
        }
    }

    private fun switchActiveFriend(friendUid: String, friendName: String, friendPhone: String) {
        currentlyListeningUid?.let { oldUid ->
            locationListener?.let { l ->
                firebaseRepo.removeListener("locations/$oldUid", l)
            }
        }
        activeFriendUid = friendUid
        activeFriendName = friendName
        activeFriendPhone = friendPhone
        currentlyListeningUid = friendUid

        binding.tvFriendName.text = friendName
        binding.toolbar.title = if (isSos) "DISTRESS: $friendName" else "$friendName's Location"

        // Remove old marker & circle
        currentMarker?.remove()
        currentMarker = null
        accuracyCircle?.remove()
        accuracyCircle = null

        listenForFriendLocation(friendUid)
    }

    private fun listenForFriendLocation(friendUid: String) {
        // Remove previous listener on old friend
        currentlyListeningUid?.let { oldUid ->
            if (oldUid != friendUid) {
                locationListener?.let { l ->
                    firebaseRepo.removeListener("locations/$oldUid", l)
                }
            }
        }
        currentlyListeningUid = friendUid

        locationListener = firebaseRepo.listenToUserLocation(friendUid) { location ->
            if (location != null && (location.latitude != 0.0 || location.longitude != 0.0)) {
                lastLocation = location
                updateMapAndUI(location)
            } else {
                binding.tvStatusBadge.text = "● NO LOCATION"
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
                binding.tvLastUpdated.text = "No location has ever been uploaded by this contact."
                binding.tvAccuracyAndBattery.text = "Waiting for initial GPS coordinates..."
            }
        }
    }

    private fun updateMapAndUI(location: LocationData) {
        val latLng = LatLng(location.latitude, location.longitude)
        val map = googleMap ?: return

        // 1. In-place Marker Position Update (Do not recreate on every packet)
        if (currentMarker == null) {
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(activeFriendName)
                .snippet("SafeTrack Live Location")
                .icon(
                    if (isSos) BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    else BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                )
            currentMarker = map.addMarker(markerOptions)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        } else {
            currentMarker?.position = latLng
            map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }

        // 2. Accuracy circle update
        accuracyCircle?.remove()
        if (location.accuracy > 0) {
            val circleColor = if (isSos) Color.argb(40, 211, 47, 47) else Color.argb(40, 98, 0, 238)
            val strokeColor = if (isSos) Color.argb(120, 211, 47, 47) else Color.argb(120, 98, 0, 238)
            accuracyCircle = map.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(location.accuracy.toDouble())
                    .fillColor(circleColor)
                    .strokeColor(strokeColor)
                    .strokeWidth(2f)
            )
        }

        // 3. Status Evaluation (LIVE / LAST KNOWN / OFFLINE)
        if (isSos) {
            binding.tvStatusBadge.text = "● SOS DISTRESS"
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.safety_red))
        } else {
            val state = LocationStatusManager.evaluateState(location)
            binding.tvStatusBadge.text = LocationStatusManager.formatStatusLabel(state)
            val colorRes = when (state) {
                LocationStatusManager.LocationState.LIVE -> R.color.safety_green
                LocationStatusManager.LocationState.LAST_KNOWN -> R.color.safety_amber
                LocationStatusManager.LocationState.OFFLINE -> R.color.text_secondary
                LocationStatusManager.LocationState.NO_LOCATION -> R.color.text_hint
            }
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, colorRes))
        }

        // 4. Relative & Absolute Time Formatting
        val relative = LocationStatusManager.formatRelativeTime(location.timestamp)
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
        binding.tvLastUpdated.text = "Updated: $relative (${sdf.format(Date(location.timestamp))})"

        // 5. Accuracy, Altitude, Speed & Battery Details
        val speedStr = if (location.speed > 0f) String.format(Locale.US, " • Speed: %.1f km/h", location.speed * 3.6f) else ""
        val altitudeStr = if (location.altitude != 0.0) String.format(Locale.US, " • Alt: %.0fm", location.altitude) else ""
        val batteryStr = if (location.batteryPct >= 0) " • Battery: ${location.batteryPct}%" else ""
        val approxNotice = if (location.isApproximate) " (Approximate)" else ""

        binding.tvAccuracyAndBattery.text = String.format(
            Locale.US,
            "Accuracy: ±%.1fm%s%s%s%s",
            location.accuracy,
            approxNotice,
            speedStr,
            altitudeStr,
            batteryStr
        )
    }

    private fun handleFriendRevoked() {
        AlertDialog.Builder(this)
            .setTitle("Access Revoked")
            .setMessage("Location sharing authorization from $activeFriendName has ended or was revoked.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentlyListeningUid?.let { uid ->
            locationListener?.let { l ->
                firebaseRepo.removeListener("locations/$uid", l)
            }
        }
        val myUid = (application as SafeTrackApp).sessionManager.uid
        if (!myUid.isNullOrBlank()) {
            contactsListener?.let { l ->
                firebaseRepo.removeListener("connections/$myUid", l)
            }
        }
    }

    companion object {
        const val EXTRA_FRIEND_USER_ID = "extra_friend_user_id"
        const val EXTRA_FRIEND_NAME = "extra_friend_name"
        const val EXTRA_FRIEND_PHONE = "extra_friend_phone"
        const val EXTRA_IS_SOS = "extra_is_sos"
    }
}
