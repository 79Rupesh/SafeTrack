package com.safetrack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class ConnectionRequest(
    val requestId: String = "",
    val senderUid: String = "",
    val fromUserId: String = "",
    val senderName: String = "",
    val fromUserName: String = "",
    val senderPhone: String = "",
    val fromUserPhone: String = "",
    val receiverUid: String = "",
    val toUserId: String = "",
    val receiverName: String = "",
    val receiverPhone: String = "",
    val inviteCode: String = "",
    val status: String = STATUS_PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_REVOKED = "revoked"
        const val STATUS_EXPIRED = "expired"
    }

    val resolvedSenderUid: String
        get() = senderUid.ifBlank { fromUserId }

    val resolvedSenderName: String
        get() = senderName.ifBlank { fromUserName }

    val resolvedSenderPhone: String
        get() = senderPhone.ifBlank { fromUserPhone }

    val resolvedReceiverUid: String
        get() = receiverUid.ifBlank { toUserId }
}
