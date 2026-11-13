package com.safetrack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class TrustedContact(
    val contactUserId: String = "",
    val name: String = "",
    val phone: String = "",
    val status: String = STATUS_APPROVED,
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long = System.currentTimeMillis(),
    val sharingEnabled: Boolean = true
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_REVOKED = "REVOKED"
    }
}
