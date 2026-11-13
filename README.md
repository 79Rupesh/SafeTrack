# SafeTrack – Women Safety & Background Location Sharing Android App

SafeTrack is a modern, consent-based Women Safety and Live Location Sharing native Android application built with Kotlin, Firebase Realtime Database, Google Play Services Location API, Google Maps SDK, Room Database for offline queueing, and Android Foreground Services.

---

## 1. Key Architectural Principles

- **Explicit Consent**: Zero secret tracking. Users must generate or enter an invite code and approve the request before location is ever visible to another person.
- **Persistent Awareness**: When location sharing is enabled, a persistent Android Foreground Service notification is visible at all times with 1-tap **Pause** and **Stop** actions.
- **Mutual Contact Pairing & Security**:
  - Time-limited invitation codes (48-hour expiration).
  - Code regeneration support (instantly revoking previous/expired codes).
  - Public profile preview (Name, Role, Code status) before sending requests.
  - Reciprocal authorization: revoking a contact immediately revokes location access on the server via Firebase Security Rules.
  - Protection against self-connection and duplicate connection requests.
  - Search by verified mobile number with restricted indexing (`phoneIndex`).
- **Full Android 8 to 14/15 Compatibility**:
  - `FOREGROUND_SERVICE` (API 28+)
  - `FOREGROUND_SERVICE_LOCATION` (API 34 / Android 14)
  - `ACCESS_BACKGROUND_LOCATION` (API 29 / Android 10)
  - `POST_NOTIFICATIONS` (API 33 / Android 13)
  - Battery Optimization Exemption handling
- **3-State Location Handling**:
  1. *Online State*: Live coordinates synced in real-time to Firebase Realtime Database and shown immediately to approved viewers.
  2. *Offline State*: Coordinates logged into local SQLite via Room (`LocationRecord`), marked `isSynced = false`. When connectivity returns, `LocationRepository` and `WorkManager` automatically sync queued points.
  3. *Device Off / Disconnected*: Viewer map displays the exact last known location and timestamp, explicitly badged as `OFFLINE`. Old locations are never faked as live.
- **Emergency SOS Distress System**:
  - Intentional press-and-hold (2 seconds) to avoid accidental triggers.
  - Broadcasts SOS event with exact coordinates to Firebase Realtime Database and FCM.
  - 1-tap emergency dialer for India's National Emergency Helpline (**112**).

---

## 2. Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/safetrack/
│   │   ├── SafeTrackApp.kt              # Application initialization, Notification Channels
│   │   ├── auth/
│   │   │   ├── AuthActivity.kt          # Phone number entry & Firebase PhoneAuth dispatch
│   │   │   ├── OtpVerificationActivity.kt# 6-digit OTP verification, cooldown timer & auto check
│   │   │   ├── RoleSelectionActivity.kt # Role selection (Friend, Viewer, Both)
│   │   │   ├── ProfileSetupActivity.kt  # User profile creation (Name, verified phone, role)
│   │   │   ├── EditProfileActivity.kt   # Profile editing (Name & Role updates)
│   │   │   ├── AuthLoadingActivity.kt   # Auth session loading & profile validation state
│   │   │   ├── AuthErrorActivity.kt     # Dedicated authentication error display
│   │   │   └── PhoneAuthTokenHolder.kt  # Resend token holder across activities
│   │   ├── contacts/
│   │   │   ├── AddContactActivity.kt    # Connect by Code / Phone, Preview & Share Invitation
│   │   │   ├── ConnectionRequestsActivity.kt # 3-Tab Manager: Incoming, Sent, Approved Contacts
│   │   │   ├── ConnectionRequestsAdapter.kt  # Incoming requests (Approve / Reject)
│   │   │   ├── OutgoingRequestsAdapter.kt    # Sent requests (Cancel Request)
│   │   │   └── TrustedContactsAdapter.kt     # Approved contacts (Revoke Access)
│   │   ├── dashboard/
│   │   │   ├── FriendDashboardActivity.kt# Location Sharer Dashboard (Minimal, non-map)
│   │   │   ├── ViewerDashboardActivity.kt# Guardian / Contact Dashboard & Live SOS Banner
│   │   │   └── ViewerFriendsAdapter.kt
│   │   ├── data/model/
│   │   │   ├── User.kt                  # User model (uid, name, phoneNumber, role, inviteCode, timestamps)
│   │   │   ├── Invitation.kt            # Invitation model (code, ownerUid, expiresAt, status)
│   │   │   ├── ConnectionRequest.kt     # Request model (sender, receiver, status, timestamps)
│   │   │   ├── TrustedContact.kt        # Contact model (status, approvedAt, sharingEnabled)
│   │   │   ├── LocationData.kt
│   │   │   └── SosAlert.kt
│   │   ├── database/
│   │   │   ├── SafeTrackDatabase.kt     # Room Database singleton
│   │   │   ├── dao/LocationDao.kt       # Local offline SQLite DAO
│   │   │   └── entity/LocationRecord.kt # Offline location entity
│   │   ├── firebase/
│   │   │   └── FirebaseRepository.kt    # Realtime DB operations, invitations, requests & sync
│   │   ├── location/
│   │   │   ├── DeviceLocationManager.kt # FusedLocationProviderClient GPS manager & jitter filter
│   │   │   ├── LocationHistoryActivity.kt # Offline queue & history screen
│   │   │   ├── LocationHistoryAdapter.kt
│   │   │   ├── LocationPermissionManager.kt # Fine/Coarse/Background permission & GPS state helper
│   │   │   ├── LocationRepository.kt    # Hybrid Room + Firebase syncing
│   │   │   ├── LocationStatusManager.kt # 4-State evaluator (LIVE, LAST KNOWN, OFFLINE, NO LOCATION)
│   │   │   └── LocationSyncWorker.kt    # WorkManager background reconnect sync
│   │   ├── maps/
│   │   │   └── GoogleMapsActivity.kt    # Live Map, Markers, Circle, Directions
│   │   ├── notifications/
│   │   │   └── SafeTrackMessagingService.kt # FCM SOS & Connection notification receiver
│   │   ├── settings/
│   │   │   └── SettingsActivity.kt      # Update intervals, profile edit link, battery settings, logout
│   │   ├── sos/
│   │   │   └── SosActivity.kt           # Press-and-hold SOS & 112 dialing
│   │   ├── tracking/
│   │   │   ├── BootReceiver.kt          # Restore sharing on reboot
│   │   │   ├── LocationTrackingService.kt# Android Foreground Service (API 34)
│   │   │   └── ServiceStopActionReceiver.kt # Notification stop/pause action receiver
│   │   ├── ui/
│   │   │   ├── BatteryOptimizationActivity.kt # Battery saver & OEM guidance screen
│   │   │   ├── SplashActivity.kt        # Startup persistent session check & routing
│   │   │   ├── WelcomeActivity.kt
│   │   │   ├── PermissionSetupActivity.kt
│   │   │   └── PrivacyPolicyActivity.kt
│   │   └── utils/
│   │       ├── NetworkUtils.kt
│   │       └── SessionManager.kt        # Persistent session preferences
│   └── res/                             # Layouts, vector drawables, menus, colors, themes
├── build.gradle.kts
├── database.rules.json                  # Firebase Realtime Database security rules
└── google-services.json
```

---

## 3. Firebase Database Structure (Phases 1, 2 & 3)

```
users/
  {uid}/
    uid: "string"           # Firebase UID
    userId: "string"        # Backward compatible UID mirror
    name: "string"          # Full name
    phoneNumber: "string"   # Verified E.164 phone number (+91XXXXXXXXXX)
    role: "string"          # "FRIEND", "VIEWER", or "BOTH"
    inviteCode: "string"    # Active code e.g. "ST-123456"
    createdAt: 1715000000000
    updatedAt: 1715000000000
    accountStatus: "ACTIVE"

invitations/
  {code}/
    code: "ST-123456"
    ownerUid: "string"
    ownerName: "string"
    ownerRole: "string"
    createdAt: 1715000000000
    expiresAt: 1715172800000 # 48 hours validity
    status: "ACTIVE"         # "ACTIVE", "EXPIRED", "REVOKED"

userInvitations/
  {uid}: "ST-123456"        # Quick pointer to user's active invitation

phoneIndex/
  {cleanPhone}: "uid"       # Normalized lookup (+91XXXXXXXXXX)

connectionRequests/
  {requestId}/
    requestId: "string"
    senderUid: "string"
    senderName: "string"
    senderPhone: "string"
    receiverUid: "string"
    receiverName: "string"
    receiverPhone: "string"
    inviteCode: "string"
    status: "pending"       # "pending", "accepted", "rejected", "cancelled", "revoked", "expired"
    createdAt: 1715000000000
    updatedAt: 1715000000000

incomingRequests/
  {receiverUid}/
    {requestId}: true       # Index of pending requests for the receiver

outgoingRequests/
  {senderUid}/
    {requestId}: true       # Index of pending requests sent by the sender

connections/
  {ownerUid}/
    {trustedContactUid}/
      contactUserId: "string"
      name: "string"
      phone: "string"
      status: "APPROVED"    # "APPROVED" or "REVOKED"
      approvedAt: 1715000000000
      sharingEnabled: true

locations/
  {userId}/
    latitude: 28.613939     # High precision decimal degrees
    longitude: 77.209021
    accuracy: 12.5          # Horizontal accuracy in meters
    altitude: 216.0         # Altitude in meters (when available)
    speed: 0.0              # Speed in m/s (when available)
    timestamp: 1715000000000# Timestamp of GPS fix
    updatedAt: 1715000000000# Server upload timestamp
    isLive: true            # Active transmission flag
    isApproximate: false    # Precision flag (false = FINE, true = COARSE)
    provider: "fused"       # FusedLocationProviderClient
```

---

## 4. Phase 3: Invitation & Trusted Contact Connection Features

1. **Invitation Code Generation & Expiry**:
   - Dynamic generation of 6-digit alphanumeric codes (`ST-XXXXXX`).
   - Attached to authenticated Firebase UID with a 48-hour expiration timestamp (`expiresAt`).
   - User can regenerate their code at any time; the previous code is immediately marked as `EXPIRED`.
2. **Preview Before Sending Request**:
   - Entering an invitation code fetches and displays the sender's public profile card:
     - Name
     - Role (Location Sharer / Guardian)
     - Validity status (Active vs. Expired)
   - Checks prevent self-connections and duplicate requests before any network dispatch.
3. **Connect by Mobile Number**:
   - Secondary tab in [AddContactActivity.kt](file:///c:/Users/hp/OneDrive/Desktop/New%20folder/app/src/main/java/com/safetrack/contacts/AddContactActivity.kt) allowing lookup by verified 10-digit phone number.
   - Restricts directory exposure by querying `phoneIndex/{cleanPhone}` directly.
4. **3-Tab Trusted Contacts & Requests Manager**:
   - In [ConnectionRequestsActivity.kt](file:///c:/Users/hp/OneDrive/Desktop/New%20folder/app/src/main/java/com/safetrack/contacts/ConnectionRequestsActivity.kt):
     - **Incoming Tab**: Lists incoming requests with **Approve** and **Reject** buttons and live badge counters.
     - **Sent Tab**: Lists pending outgoing requests with a **Cancel Request** button.
     - **Contacts Tab**: Lists approved contacts with a **Revoke Access** button.
5. **Immediate Location Access Revocation**:
   - Revoking a contact removes mutual authorization entries in `connections/{ownerUid}/{contactUid}`.
   - In `database.rules.json`, rule `.read` on `locations/{userId}` enforces:
     `root.child('connections').child($userId).child(auth.uid).child('status').val() == 'APPROVED'`
     The moment a contact is revoked, Firebase Security Rules terminate their ability to read the friend's location.
6. **Push Notifications**:
   - [SafeTrackMessagingService.kt](file:///c:/Users/hp/OneDrive/Desktop/New%20folder/app/src/main/java/com/safetrack/notifications/SafeTrackMessagingService.kt) handles FCM messages:
     - `CONNECTION_REQUEST`
     - `REQUEST_ACCEPTED`
     - `REQUEST_REJECTED`
     - `CONNECTION_REVOKED`
   - Strictly conforms to privacy guidelines: notifications never contain private GPS coordinates.

---

## 5. Phase 4: GPS Location Integration & Google Maps System

SafeTrack integrates real device GPS hardware with Google Play Services and Google Maps SDK, upholding explicit user control, real-time feedback, and strict privacy boundaries.

### 5.1 Real GPS Hardware Integration (`DeviceLocationManager.kt`)
- **FusedLocationProviderClient**: Combines GPS, Wi-Fi, and cellular towers using Google Play Services with `PRIORITY_HIGH_ACCURACY`.
- **Jitter Rejection Filter**: Drops duplicate and stationary micro-jitter updates (< 2.0 meters change) to preserve battery life and eliminate database thrashing.
- **Hardware Telemetry**: Captures latitude, longitude, horizontal accuracy radius (meters), altitude, speed, and timestamp.
- **Hardware GPS Availability**: Validates whether Location Services (GPS/Network) are active before attempting acquisition, notifying the user immediately if hardware is toggled off.

### 5.2 4-Tier Location Status Evaluation (`LocationStatusManager.kt`)
To guarantee that old coordinates are never misrepresented as live tracking:
1. **LIVE (🟢 Green)**: Active fix received within the last **2 minutes** (`now - timestamp <= 120,000ms`).
2. **LAST KNOWN (🟠 Orange)**: Location reading between **2 and 15 minutes** old, or user has intentionally paused sharing.
3. **OFFLINE (⚪ Gray)**: Fix older than **15 minutes**, user has stopped sharing, or device is powered off.
4. **NO LOCATION (⚪ Gray)**: Location sharing has never been initiated for this account.

Relative time formatting (`"Updated 20s ago"`, `"Updated 5m ago"`, or `"Updated on 12 May, 04:30 PM"`) is calculated on-the-fly and displayed alongside status badges.

### 5.3 Stale Packet & Timestamp Collision Guard (`FirebaseRepository.kt`)
Network latency or reconnected offline batches could deliver delayed packets out of order. Before updating `locations/{userId}`, SafeTrack performs a client-side check verifying `existingTimestamp <= newLocation.timestamp`. Stale packets can never overwrite a newer live position.

### 5.4 Viewer Mode Google Maps Experience (`GoogleMapsActivity.kt`)
- **In-Place Marker Updates**: Reuses marker instances (`currentMarker.position = latLng`) to eliminate visual flickering.
- **Accuracy Confidence Circle**: Renders a dynamic semi-transparent blue accuracy ring representing the GPS fix's horizontal error margin in meters.
- **Multi-Contact Horizontal Selector**: Horizontal chip group allows instant switching between all approved friends sharing location.
- **Real-Time Mutual Revocation Detection**: Attaches a real-time listener to the viewer's permission entry in `connections/{friendUid}/{viewerUid}`. If the location-sharing user revokes access while the viewer has the map open, SafeTrack immediately halts location listening, displays a security alert dialog, and exits back to the dashboard.
- **Turn-by-Turn Directions Launcher**: Launches Google Maps navigation (`google.navigation:q=lat,lng`) or maps web directions fallback with 1 tap.
- **Graceful API Key Handling**: If the Google Maps API key is missing or unconfigured, SafeTrack displays a clear setup instruction card rather than crashing.

### 5.5 Sharer Mode Minimal Dashboard (`FriendDashboardActivity.kt`)
- Displays live GPS availability indicator (active vs. disabled).
- Shows live latitude, longitude, accuracy radius, and last fix timestamp.
- Dedicated 1-tap **Start Sharing**, **Pause Sharing**, and **Stop Sharing** controls.
- When stopped, clears the live database coordinates to prevent persistent tracking records.

---

## 6. Google Maps API Key Setup & Configuration Guide

To enable live Google Maps rendering on Android devices:

### Step 1: Create a Google Cloud Project & Enable Maps SDK
1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project or select your existing SafeTrack Firebase project.
3. Navigate to **APIs & Services > Library**.
4. Search for **Maps SDK for Android** and click **Enable**.

### Step 2: Create & Restrict API Key
1. Go to **APIs & Services > Credentials**.
2. Click **Create Credentials > API Key**.
3. Click **Edit API Key** and set the following restrictions:
   - **Application restrictions**: Select **Android apps**.
   - Click **Add an item**:
     - **Package name**: `com.safetrack` (and `com.safetrack.debug` if testing debug variant).
     - **SHA-1 certificate fingerprint**: Run the Gradle signing report to obtain your debug SHA-1:
       ```powershell
       .\gradlew.bat signingReport
       ```
       Copy the `SHA1` string under `Variant: debugUnitTest / debug`.
   - **API restrictions**: Select **Restrict key** and check **Maps SDK for Android**.
4. Save your restricted API key.

### Step 3: Add Key to `local.properties` (Recommended)
SafeTrack is configured to automatically inject your key into the build without modifying source code or exposing secrets to Git.

Add the following line to `local.properties` in your project root:
```properties
MAPS_API_KEY=AIzaSyYourActualGoogleMapsApiKeyHere
```

Alternatively, you can supply it as an environment variable in your CI/CD pipeline:
```bash
export MAPS_API_KEY="AIzaSyYourActualGoogleMapsApiKeyHere"
```

If neither is provided, the app will compile safely with the placeholder and present an informative setup card in the map view instead of crashing.

---

## 7. Phase 5: Background Location Tracking Foreground Service

SafeTrack implements continuous, consent-based background location sharing through an Android Foreground Service, upholding transparent persistent notification standards and full compliance with Android 8 through 14/15 background execution constraints.

### 7.1 Dedicated Foreground Service (`LocationTrackingService.kt`)
- **Service Declaration**: Configured with `android:foregroundServiceType="location"` to comply with Android 14 (API 34) requirements.
- **Explicit Consent**: Will never track in the background unless the user explicitly starts location sharing.
- **Continuous Background Collection**: Transmits GPS fixes when the app is minimized, screen is locked, switching between other apps, or activity is closed.
- **Resource Management**: Automatically cleans up `LocationCallback`, removes updates from `FusedLocationProviderClient`, cancels coroutine scopes, and safely releases `WakeLock` upon service termination.

### 7.2 Persistent Safety Notification & Actions
- **Ongoing Visibility**: Notification cannot be swiped away while location sharing is active, guaranteeing total user awareness.
- **Notification Channel**: `CHANNEL_LOCATION_ID` (`safetrack_location_channel`) configured with `IMPORTANCE_LOW` to eliminate repetitive audio chimes while maintaining a visible status bar safety shield.
- **Active State**:
  - **Title**: `SafeTrack – Location Sharing Active`
  - **Body**: `Your location is currently being shared with your approved trusted contacts.`
  - **Actions**:
    - **Open SafeTrack**: Tapping notification brings `FriendDashboardActivity` to front.
    - **Pause Sharing**: 1-tap notification button pauses background updates and updates Firebase status to `PAUSED`.
    - **Stop Sharing**: 1-tap notification button stops the foreground service, clears live coordinates from Firebase, and sets status to `STOPPED`.
- **Paused State**:
  - **Title**: `SafeTrack – Location Sharing Paused`
  - **Body**: `Location sharing is paused. Tap resume to resume sharing.`
  - **Actions**: [Resume Sharing] and [Stop Sharing].

### 7.3 Configurable Telemetry & Power Management
- **Desired Update Interval**: Default `30 seconds` (configurable in Settings).
- **Fastest Interval**: `15 seconds`.
- **Displacement Threshold**: `20 meters` (`setMinUpdateDistanceMeters(20f)`).
- **Jitter Rejection Filter**: Drops duplicate and stationary micro-drift updates (< 2.0 meters) to protect user battery and reduce unnecessary network calls.
- **Partial Wake Lock**: Briefly acquired (max 3 seconds) during socket transmission to prevent deep CPU sleep during upload.

### 7.4 Offline Queueing & Periodic Synchronization
- **Local Room Storage**: If network is unavailable, fixes are stored locally in Room database (`location_records`) with `isSynced = false`.
- **Automated Retention Policy**: Automatically prunes synced history older than **48 hours** to protect device storage.
- **Immediate Reconnection Sync**: The moment network connectivity returns, queued records are pushed sequentially in chronological order.
- **WorkManager Fallback**: [LocationSyncWorker.kt](file:///c:/Users/hp/OneDrive/Desktop/New%20folder/app/src/main/java/com/safetrack/location/LocationSyncWorker.kt) runs periodic background jobs with `NetworkType.CONNECTED` constraints to ensure zero lost coordinates.

### 7.5 Device Restart Handling (`BootReceiver.kt`)
- Listens for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- Verifies prior user login, active sharing flag, and permission state before taking any action.
- **Android 12+ (API 31+) Restriction Compliance**: Starting foreground services directly from the background is restricted by Android OS policy. On devices where direct background start is prohibited, SafeTrack gracefully catches `ForegroundServiceStartNotAllowedException` and issues a high-priority prompt notification:
  - **Title**: `SafeTrack – Device Restarted`
  - **Body**: `You were actively sharing your location before restart. Tap to resume location sharing.`
  - Tapping the notification immediately launches `FriendDashboardActivity` to resume sharing with 1 tap.

### 7.6 Battery Optimization Guidance Screen (`BatteryOptimizationActivity.kt`)
- Transparently educates users about:
  1. **Doze Mode & Battery Saver**: Explains how Android power saving delays background location fixes.
  2. **Manufacturer Task Killers (OEMs)**: Specific setup instructions for Samsung (Sleeping Apps), Xiaomi (Autostart & No Restrictions), OnePlus, Oppo, and Vivo.
  3. **Realistic Safety Limitations**: Clearly states that tracking depends on phone battery, power state, and cellular reception; never makes unrealistic promises of infinite tracking.
- Features dynamic status badge (`Unrestricted` vs `Restricted`) and provides direct shortcut to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

## 8. Phase 6: Emergency SOS Distress System & Real-Time Alerting

SafeTrack incorporates an end-to-end Emergency SOS Distress System engineered for life-safety moments, combining anti-accidental triggers, live multi-contact alerting with audio sirens, offline SMS dispatch, and safe cancellation protocols.

### 8.1 2-Second Hold-to-Trigger ([SosActivity.kt](file:///c:/Users/hp/OneDrive/Desktop/New%20folder/app/src/main/java/com/safetrack/sos/SosActivity.kt))
- **Intentional Trigger Design**: Prevents accidental false alarms by requiring an unbroken 2-second touch-and-hold interaction on the SOS button.
- **Haptic & Visual Countdown**: Provides dynamic millisecond countdown feedback with progress bar animation and vibration pulses.
- **Telemetry Gathering**: Immediately acquires live GPS coordinates, accuracy radius, and device battery percentage before dispatching the alert.

### 8.2 Dual-Pointer Firebase Realtime Database Architecture
- **`sosAlerts/{alertId}`**: Permanent historical record containing sender UID, name, phone, timestamp, initial coordinates, status (`ACTIVE` / `RESOLVED`), battery percentage, and resolution notes.
- **`activeSos/{userId}`**: Ultra-fast live active distress pointer monitored by all approved viewer devices with millisecond latency.

### 8.3 Viewer Mode Urgent Alerting & Siren ([ViewerDashboardActivity.kt](file:///c:/Users/hp/OneDrive/Desktop/New%20folder/app/src/main/java/com/safetrack/dashboard/ViewerDashboardActivity.kt))
- **Pulsing Emergency Card**: An active distress banner immediately appears at the top of the viewer's screen.
- **Continuous Audible Alarm / Siren**: Uses `RingtoneManager` with `TYPE_ALARM` to play an audible siren so guardians notice the distress call even across the room.
- **Urgent Vibration Waveform**: Loops an emergency vibration pattern (`0, 600, 200, 600, 200, 1000`).
- **Emergency Action Controls**:
  - **Track on Map**: Instantly opens [GoogleMapsActivity.kt](file:///c:/Users/hp/OneDrive/Desktop/New%20folder/app/src/main/java/com/safetrack/maps/GoogleMapsActivity.kt) focused on the friend's coordinates with a red emergency marker and 17x camera zoom.
  - **Mute Siren**: Silences the alarm audio while keeping the emergency banner active.
  - **Call Friend**: Direct 1-tap phone dialer to the friend's mobile number.
  - **Call 112**: Direct dialer to India's National Emergency Helpline.

### 8.4 Emergency SOS Cancellation ("I Am Safe Now")
- The sender can tap **"I AM SAFE NOW (Cancel SOS Alert)"** upon reaching safety.
- Updates Firebase status to `RESOLVED` and removes the active distress pointer.
- Immediately silences the audible siren and dismisses the red banner on all viewer devices, notifying contacts: *"Emergency resolved. Contact is safe."*

### 8.5 Offline Emergency SMS Fallback
- If mobile data or Wi-Fi is unavailable during an emergency, SafeTrack automatically prepares a pre-filled emergency SMS:
  `"EMERGENCY SOS from [Name] via SafeTrack! I need immediate help. My current location is: https://maps.google.com/?q=[lat],[lng]"`
- Routes directly to the user's primary emergency contact configured in [EditProfileActivity.kt](file:///c:/Users/hp/OneDrive/Desktop/New%20folder/app/src/main/java/com/safetrack/auth/EditProfileActivity.kt).

### 8.6 National Emergency Helpline Integration
- Integrated 1-tap shortcut to **112** (National Emergency Helpline) accessible on both sender and viewer emergency screens.

---

## 9. Build & Testing Execution Guide

### Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```
*Output path*: `app\build\outputs\apk\debug\app-debug.apk`

### Install on Device / Emulator via ADB
```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 10. Comprehensive Two-Phone Testing Matrix (Phases 3, 4, 5 & 6)

| # | Test Scenario | Steps | Expected Result |
|---|---|---|---|
| **1** | **Invitation Generation** | User A logs in. Opens "Add Contact" -> "My Code" tab (or Dashboard Invite). | Unique code `ST-XXXXXX` is displayed with "Valid for 48 hours" timestamp. |
| **2** | **Code Sharing** | User A taps "Share Code". | Android system share sheet opens with message: *"Connect with me on SafeTrack for mutual safety... My code is: ST-XXXXXX"*. |
| **3** | **Enter Code & Preview Profile** | User B logs in on Phone 2. Opens "Add Contact" -> enters User A's code -> taps "Find Contact". | User A's public profile preview card displays: Name, Role, and Active status. |
| **4** | **Self-Connection Block** | User A tries to enter their own code or phone number. | Input error: *"You cannot connect to your own invitation code."* No request is sent. |
| **5** | **Submit Connection Request** | User B taps "Send Connection Request" on preview card. | Request created in `connectionRequests/{id}`. User B sees it in their "Sent" tab. User B gets toast confirmation. |
| **6** | **Duplicate Request Block** | User B tries to submit the request again to User A. | System prevents duplicate: *"A pending connection request has already been sent to User A."* |
| **7** | **Incoming Request Notification** | User A opens "Connection Requests" (or receives push notification). | Tab shows `Incoming (1)`. Request card displays User B's name, phone, and request timestamp with "Approve" and "Reject" buttons. |
| **8** | **Cancel Outgoing Request** | User B opens "Connection Requests" -> "Sent" tab -> taps "Cancel Request". | Request status becomes `cancelled`. Request immediately vanishes from User A's Incoming list. |
| **9** | **Request Rejection** | User B re-sends request. User A taps "Reject". | Request status becomes `rejected`. Removed from pending lists. User B cannot see User A's location. |
| **10** | **Request Approval & Mutual Pairing** | User B re-sends request. User A taps "Approve". | Request status becomes `accepted`. Both users now appear in each other's "Approved Contacts" list. |
| **11** | **Location Permission Flow** | User A opens Permission Setup screen. | Explicit explanation card shown. User is prompted for Precise / Approximate Foreground location. |
| **12** | **GPS Disabled Prompt** | Turn off GPS in Android status bar on Phone 1. | Dashboard displays *"GPS Disabled"*. Tapping "Enable GPS" opens Android Location System Settings. |
| **13** | **Start Sharing Live Location** | User A taps "Start Sharing" on Friend Dashboard. | Foreground Service starts. Persistent notification appears: *"SafeTrack – Location Sharing Active"*. GPS coordinates acquire and upload. |
| **14** | **App Minimized Background Tracking** | User A presses Home button or opens another application. | Persistent notification remains active. Coordinates continue transmitting to Firebase every 30s. |
| **15** | **Screen Locked Background Tracking** | User A locks the physical screen with power button. | Service continues running. GPS fixes continue collecting and uploading. |
| **16** | **Persistent Notification Pause Action** | User A pulls down notification shade -> taps "Pause Sharing". | Notification changes to *"SafeTrack – Location Sharing Paused"* with [Resume Sharing] action. Viewer sees status change to `PAUSED` / `LAST KNOWN`. |
| **17** | **Persistent Notification Resume Action** | User A taps "Resume Sharing" on the notification. | Location tracking resumes immediately. Status updates back to `LIVE`. |
| **18** | **Persistent Notification Stop Action** | User A taps "Stop Sharing" on the notification. | Service stops, notification is removed from shade, coordinates are cleared from Firebase, and viewer sees `OFFLINE`. |
| **19** | **Live Map Display (Viewer Mode)** | User B opens Viewer Dashboard -> taps "View on Map" for User A. | Google Maps opens. Smooth camera animation centers on User A with marker and accuracy circle. Status badge shows `LIVE`. |
| **20** | **In-Place Marker Update (No Flicker)** | User A moves location while app is in background. | Marker position on User B's map updates smoothly in-place without screen flash or recreating marker objects. |
| **21** | **Offline Queueing to Room** | Enable Airplane Mode on Phone 1 while sharing is active. Move 50 meters. | Fixes are saved locally to SQLite (`LocationRecord`, `isSynced = false`). Dashboard shows *"Internet: Offline (Location queued to Room)"*. |
| **22** | **Network Reconnection Sync** | Disable Airplane Mode on Phone 1. | Network re-establishes. `LocationRepository` immediately syncs pending offline records to Firebase and marks them synced. |
| **23** | **Device Reboot Handling** | Reboot Phone 1 while sharing is active. | After boot, `BootReceiver` checks prior state. On supported versions it restores the service; on Android 12+ it posts a high-priority prompt to tap and resume. |
| **24** | **Battery Optimization Guidance** | User A taps "Battery & Background Optimization Guide" on dashboard. | Guidance screen opens detailing Doze Mode and OEM task killers. User can tap "Allow Unrestricted Background Activity" to grant exemption. |
| **25** | **Mid-Session Revocation Defense** | While User B is actively viewing User A on Map, User A taps "Revoke" on User B in dashboard. | Real-time listener immediately detects revocation. Alert dialog appears: *"Location access revoked"*. Map auto-closes back to dashboard. |
| **26** | **Turn-by-Turn Navigation** | User B taps "Directions" button on Map screen. | Google Maps app launches with navigation route to User A's coordinates. |
| **27** | **Accidental SOS Prevention** | User A taps (quick touch < 2s) the SOS button. | Hold timer cancels immediately without dispatching false emergency alarms. |
| **28** | **Hold 2-Seconds SOS Dispatch** | User A presses and holds SOS button for full 2 seconds. | Device vibrates; SOS alert created with live coordinates in `sosAlerts` and `activeSos`. UI updates to *"🚨 SOS ALERT BROADCASTING!"*. |
| **29** | **Viewer Real-Time Siren & Vibration** | User B has Viewer Dashboard open (or in background). | Red Emergency Card appears immediately. Phone plays loud audible alarm siren and urgent vibration waveform. |
| **30** | **Viewer Siren Mute Action** | User B taps "Mute Siren" on the SOS card. | Siren audio and vibration stop immediately, allowing clear voice calls while distress banner stays visible. |
| **31** | **Track Distress on Google Maps** | User B taps "Track on Map". | Google Maps opens directly with 17x zoom, red SOS marker, and direct "Call Friend" & "Call 112" emergency buttons. |
| **32** | **Sender Cancels SOS ("I Am Safe")** | User A taps "I AM SAFE NOW (Cancel SOS Alert)" and confirms dialog. | Alert resolved in Firebase. User B's dashboard banner auto-dismisses with toast: *"Emergency resolved. Contact is safe."*. |
| **33** | **Offline Emergency SMS Fallback** | User A turns off Wi-Fi & Mobile Data, then triggers SOS. | App detects offline state and automatically opens SMS composer with pre-filled distress message and Google Maps coordinates link. |
"# SafeTrack" 
