package com.safetrack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Invitation(
    val code: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerRole: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + EXPIRY_DURATION_MS,
    val status: String = STATUS_ACTIVE
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_EXPIRED = "EXPIRED"
        const val STATUS_REVOKED = "REVOKED"
        const val EXPIRY_DURATION_MS = 48 * 60 * 60 * 1000L // 48 hours validity
    }

    val isExpired: Boolean
        get() = status == STATUS_EXPIRED || (expiresAt > 0 && System.currentTimeMillis() > expiresAt)
}
