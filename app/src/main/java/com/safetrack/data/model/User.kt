package com.safetrack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val userId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val role: String = ROLE_FRIEND, // FRIEND, VIEWER, BOTH
    val inviteCode: String = "",
    val emergencyContactPhone: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val accountStatus: String = STATUS_ACTIVE
) {
    companion object {
        const val ROLE_FRIEND = "FRIEND"
        const val ROLE_VIEWER = "VIEWER"
        const val ROLE_BOTH = "BOTH"

        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_SUSPENDED = "SUSPENDED"

        fun getRoleDisplayName(role: String): String {
            return when (role) {
                ROLE_FRIEND -> "Location Sharing User"
                ROLE_VIEWER -> "Trusted Contact User"
                ROLE_BOTH -> "Both (Share & View)"
                else -> "Location Sharing User"
            }
        }
    }
}
