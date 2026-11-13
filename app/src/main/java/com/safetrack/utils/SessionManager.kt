package com.safetrack.utils

import android.content.Context
import android.content.SharedPreferences
import com.safetrack.data.model.User

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "safetrack_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_UID = "uid"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE = "phone"
        private const val KEY_NAME = "name"
        private const val KEY_ROLE = "role"
        private const val KEY_INVITE_CODE = "invite_code"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_ACCOUNT_STATUS = "account_status"

        private const val KEY_SHARING_ACTIVE = "sharing_active"
        private const val KEY_SHARING_PAUSED = "sharing_paused"
        private const val KEY_LAST_LATITUDE = "last_latitude"
        private const val KEY_LAST_LONGITUDE = "last_longitude"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        private const val KEY_LAST_ACCURACY = "last_accuracy"
        private const val KEY_UPDATE_INTERVAL_MS = "update_interval_ms"
        private const val KEY_MIN_UPDATE_INTERVAL_MS = "min_update_interval_ms"
        private const val KEY_MIN_DISTANCE_METERS = "min_distance_meters"
        private const val KEY_LAST_UPLOAD_TIME = "last_upload_time"
        private const val KEY_BATTERY_ACKNOWLEDGED = "battery_acknowledged"
        private const val KEY_EMERGENCY_PHONE = "primary_emergency_phone"
        private const val KEY_ACTIVE_SOS_ID = "active_sos_alert_id"
        private const val KEY_IS_SOS_ACTIVE = "is_sos_active"
    }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var uid: String?
        get() = prefs.getString(KEY_UID, null) ?: prefs.getString(KEY_USER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_UID, value).putString(KEY_USER_ID, value).apply()
        }

    var userId: String?
        get() = uid
        set(value) {
            uid = value
        }

    var phoneNumber: String?
        get() = prefs.getString(KEY_PHONE, "")
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_NAME, "")
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var userRole: String
        get() = prefs.getString(KEY_ROLE, User.ROLE_FRIEND) ?: User.ROLE_FRIEND
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    var inviteCode: String
        get() {
            var code = prefs.getString(KEY_INVITE_CODE, null)
            if (code.isNullOrBlank()) {
                code = generateInviteCode()
                prefs.edit().putString(KEY_INVITE_CODE, code).apply()
            }
            return code
        }
        set(value) = prefs.edit().putString(KEY_INVITE_CODE, value).apply()

    var createdAt: Long
        get() = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())
        set(value) = prefs.edit().putLong(KEY_CREATED_AT, value).apply()

    var updatedAt: Long
        get() = prefs.getLong(KEY_UPDATED_AT, System.currentTimeMillis())
        set(value) = prefs.edit().putLong(KEY_UPDATED_AT, value).apply()

    var accountStatus: String
        get() = prefs.getString(KEY_ACCOUNT_STATUS, User.STATUS_ACTIVE) ?: User.STATUS_ACTIVE
        set(value) = prefs.edit().putString(KEY_ACCOUNT_STATUS, value).apply()

    var isSharingActive: Boolean
        get() = prefs.getBoolean(KEY_SHARING_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHARING_ACTIVE, value).apply()

    var isSharingPaused: Boolean
        get() = prefs.getBoolean(KEY_SHARING_PAUSED, false)
        set(value) = prefs.edit().putBoolean(KEY_SHARING_PAUSED, value).apply()

    var lastLatitude: Float
        get() = prefs.getFloat(KEY_LAST_LATITUDE, 0.0f)
        set(value) = prefs.edit().putFloat(KEY_LAST_LATITUDE, value).apply()

    var lastLongitude: Float
        get() = prefs.getFloat(KEY_LAST_LONGITUDE, 0.0f)
        set(value) = prefs.edit().putFloat(KEY_LAST_LONGITUDE, value).apply()

    var lastUpdateTime: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_TIME, value).apply()

    var lastAccuracy: Float
        get() = prefs.getFloat(KEY_LAST_ACCURACY, 0.0f)
        set(value) = prefs.edit().putFloat(KEY_LAST_ACCURACY, value).apply()

    var updateIntervalMs: Long
        get() = prefs.getLong(KEY_UPDATE_INTERVAL_MS, 30_000L) // Default 30 seconds
        set(value) = prefs.edit().putLong(KEY_UPDATE_INTERVAL_MS, value).apply()

    var minUpdateIntervalMs: Long
        get() = prefs.getLong(KEY_MIN_UPDATE_INTERVAL_MS, 15_000L) // Default 15 seconds
        set(value) = prefs.edit().putLong(KEY_MIN_UPDATE_INTERVAL_MS, value).apply()

    var minDistanceChangeMeters: Float
        get() = prefs.getFloat(KEY_MIN_DISTANCE_METERS, 20.0f) // Default 20 meters
        set(value) = prefs.edit().putFloat(KEY_MIN_DISTANCE_METERS, value).apply()

    var lastUploadTime: Long
        get() = prefs.getLong(KEY_LAST_UPLOAD_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, value).apply()

    var batteryOptimizationAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_ACKNOWLEDGED, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_ACKNOWLEDGED, value).apply()

    var primaryEmergencyPhone: String
        get() = prefs.getString(KEY_EMERGENCY_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMERGENCY_PHONE, value).apply()

    var activeSosAlertId: String?
        get() = prefs.getString(KEY_ACTIVE_SOS_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_SOS_ID, value).apply()

    var isSosActive: Boolean
        get() = prefs.getBoolean(KEY_IS_SOS_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_SOS_ACTIVE, value).apply()

    fun saveUser(user: User) {
        isLoggedIn = true
        val resolvedUid = user.uid.ifBlank { user.userId }
        uid = resolvedUid
        phoneNumber = user.phoneNumber
        userName = user.name
        userRole = user.role
        inviteCode = user.inviteCode
        primaryEmergencyPhone = user.emergencyContactPhone
        createdAt = user.createdAt
        updatedAt = user.updatedAt
        accountStatus = user.accountStatus
    }

    fun toUser(): User? {
        val currentUid = uid ?: return null
        return User(
            uid = currentUid,
            userId = currentUid,
            name = userName ?: "",
            phoneNumber = phoneNumber ?: "",
            role = userRole,
            inviteCode = inviteCode,
            emergencyContactPhone = primaryEmergencyPhone,
            createdAt = createdAt,
            updatedAt = updatedAt,
            accountStatus = accountStatus
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun generateInviteCode(): String {
        val randomNum = (100000..999999).random()
        return "ST-$randomNum"
    }
}
