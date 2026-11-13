package com.safetrack.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.safetrack.data.model.ConnectionRequest
import com.safetrack.data.model.Invitation
import com.safetrack.data.model.LocationData
import com.safetrack.data.model.SosAlert
import com.safetrack.data.model.TrustedContact
import com.safetrack.data.model.User
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val usersRef: DatabaseReference = db.getReference("users")
    private val invitationsRef: DatabaseReference = db.getReference("invitations")
    private val userInvitationsRef: DatabaseReference = db.getReference("userInvitations")
    private val phoneIndexRef: DatabaseReference = db.getReference("phoneIndex")
    private val connectionsRef: DatabaseReference = db.getReference("connections")
    private val requestsRef: DatabaseReference = db.getReference("connectionRequests")
    private val incomingRequestsRef: DatabaseReference = db.getReference("incomingRequests")
    private val outgoingRequestsRef: DatabaseReference = db.getReference("outgoingRequests")
    private val locationsRef: DatabaseReference = db.getReference("locations")
    private val historyRef: DatabaseReference = db.getReference("locationHistory")
    private val sosRef: DatabaseReference = db.getReference("sosAlerts")
    private val activeSosRef: DatabaseReference = db.getReference("activeSos")

    // --- User Profile ---

    suspend fun saveUser(user: User): Result<Unit> {
        return try {
            val resolvedId = user.uid.ifBlank { user.userId }
            val normalizedUser = user.copy(
                uid = resolvedId,
                userId = resolvedId,
                updatedAt = System.currentTimeMillis()
            )
            usersRef.child(resolvedId).setValue(normalizedUser).await()

            // Index verified mobile number (normalized E.164 without spaces)
            if (user.phoneNumber.isNotBlank()) {
                val cleanPhone = user.phoneNumber.replace("\\s".toRegex(), "")
                phoneIndexRef.child(cleanPhone).setValue(resolvedId).await()
            }

            // Create or preserve active invitation
            if (user.inviteCode.isNotBlank()) {
                createOrSaveInvitation(
                    Invitation(
                        code = user.inviteCode,
                        ownerUid = resolvedId,
                        ownerName = user.name,
                        ownerRole = user.role,
                        createdAt = System.currentTimeMillis(),
                        expiresAt = System.currentTimeMillis() + Invitation.EXPIRY_DURATION_MS,
                        status = Invitation.STATUS_ACTIVE
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(uid: String, name: String, role: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "name" to name,
                "role" to role,
                "updatedAt" to System.currentTimeMillis()
            )
            usersRef.child(uid).updateChildren(updates).await()

            // Also update active invitation if present
            val activeCodeSnapshot = userInvitationsRef.child(uid).get().await()
            val activeCode = activeCodeSnapshot.getValue(String::class.java)
            if (!activeCode.isNullOrBlank()) {
                invitationsRef.child(activeCode).child("ownerName").setValue(name).await()
                invitationsRef.child(activeCode).child("ownerRole").setValue(role).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(userId: String): User? {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            snapshot.getValue(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserIdByPhoneNumber(phoneNumber: String): String? {
        return try {
            val cleanPhone = phoneNumber.replace("\\s".toRegex(), "")
            val snapshot = phoneIndexRef.child(cleanPhone).get().await()
            snapshot.getValue(String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // --- Invitation Generation & Verification ---

    suspend fun createOrSaveInvitation(invitation: Invitation): Result<Unit> {
        return try {
            invitationsRef.child(invitation.code).setValue(invitation).await()
            userInvitationsRef.child(invitation.ownerUid).setValue(invitation.code).await()
            usersRef.child(invitation.ownerUid).child("inviteCode").setValue(invitation.code).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun regenerateInvitation(user: User): Result<Invitation> {
        return try {
            val resolvedUid = user.uid.ifBlank { user.userId }

            // Expire previous invitation if any
            val oldCodeSnapshot = userInvitationsRef.child(resolvedUid).get().await()
            val oldCode = oldCodeSnapshot.getValue(String::class.java)
            if (!oldCode.isNullOrBlank()) {
                invitationsRef.child(oldCode).child("status").setValue(Invitation.STATUS_EXPIRED).await()
            }

            // Generate new code: ST-XXXXXX
            val randomDigits = (100000..999999).random()
            val newCode = "ST-$randomDigits"

            val newInvitation = Invitation(
                code = newCode,
                ownerUid = resolvedUid,
                ownerName = user.name,
                ownerRole = user.role,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + Invitation.EXPIRY_DURATION_MS,
                status = Invitation.STATUS_ACTIVE
            )

            createOrSaveInvitation(newInvitation).getOrThrow()
            Result.success(newInvitation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInvitation(inviteCode: String): Invitation? {
        return try {
            val snapshot = invitationsRef.child(inviteCode.trim().uppercase()).get().await()
            if (!snapshot.exists()) return null

            // Support both structured Invitation and legacy string pointer
            val invitation = snapshot.getValue(Invitation::class.java)
            if (invitation != null && invitation.ownerUid.isNotBlank()) {
                invitation
            } else {
                val ownerUid = snapshot.getValue(String::class.java)
                if (!ownerUid.isNullOrBlank()) {
                    val owner = getUser(ownerUid)
                    Invitation(
                        code = inviteCode,
                        ownerUid = ownerUid,
                        ownerName = owner?.name ?: "SafeTrack Contact",
                        ownerRole = owner?.role ?: User.ROLE_FRIEND,
                        createdAt = System.currentTimeMillis(),
                        expiresAt = System.currentTimeMillis() + Invitation.EXPIRY_DURATION_MS,
                        status = Invitation.STATUS_ACTIVE
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserActiveInvitation(uid: String): Invitation? {
        return try {
            val codeSnapshot = userInvitationsRef.child(uid).get().await()
            val code = codeSnapshot.getValue(String::class.java) ?: return null
            getInvitation(code)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserIdByInviteCode(inviteCode: String): String? {
        val inv = getInvitation(inviteCode)
        return inv?.ownerUid
    }

    // --- Connection Requests ---

    suspend fun sendConnectionRequest(
        fromUser: User,
        toUser: User,
        inviteCode: String = ""
    ): Result<String> {
        val senderUid = fromUser.uid.ifBlank { fromUser.userId }
        val receiverUid = toUser.uid.ifBlank { toUser.userId }

        if (senderUid == receiverUid) {
            return Result.failure(Exception("You cannot connect to yourself."))
        }

        return try {
            // Check if already approved contacts
            val existingConn = connectionsRef.child(senderUid).child(receiverUid).get().await()
            if (existingConn.exists()) {
                val contact = existingConn.getValue(TrustedContact::class.java)
                if (contact != null && contact.status == TrustedContact.STATUS_APPROVED) {
                    return Result.failure(Exception("You are already connected with ${toUser.name}."))
                }
            }

            // Check if a pending request already exists from this sender to receiver
            val pendingQuery = incomingRequestsRef.child(receiverUid).get().await()
            for (child in pendingQuery.children) {
                val reqId = child.key ?: continue
                val reqSnapshot = requestsRef.child(reqId).get().await()
                val req = reqSnapshot.getValue(ConnectionRequest::class.java)
                if (req != null && req.resolvedSenderUid == senderUid && req.status == ConnectionRequest.STATUS_PENDING) {
                    return Result.failure(Exception("A pending connection request has already been sent to ${toUser.name}."))
                }
            }

            val requestId = requestsRef.push().key ?: return Result.failure(
                Exception("Failed to generate request ID")
            )

            val now = System.currentTimeMillis()
            val request = ConnectionRequest(
                requestId = requestId,
                senderUid = senderUid,
                fromUserId = senderUid,
                senderName = fromUser.name,
                fromUserName = fromUser.name,
                senderPhone = fromUser.phoneNumber,
                fromUserPhone = fromUser.phoneNumber,
                receiverUid = receiverUid,
                toUserId = receiverUid,
                receiverName = toUser.name,
                receiverPhone = toUser.phoneNumber,
                inviteCode = inviteCode,
                status = ConnectionRequest.STATUS_PENDING,
                createdAt = now,
                timestamp = now,
                updatedAt = now
            )

            // Save in main connectionRequests node
            requestsRef.child(requestId).setValue(request).await()

            // Save indexed references for fast receiver and sender queries
            incomingRequestsRef.child(receiverUid).child(requestId).setValue(true).await()
            outgoingRequestsRef.child(senderUid).child(requestId).setValue(true).await()

            Result.success(requestId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToIncomingRequests(
        receiverUid: String,
        onUpdate: (List<ConnectionRequest>) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requests = mutableListOf<ConnectionRequest>()
                val count = snapshot.childrenCount
                if (count == 0L) {
                    onUpdate(emptyList())
                    return
                }

                var fetched = 0
                for (child in snapshot.children) {
                    val reqId = child.key ?: continue
                    requestsRef.child(reqId).get().addOnSuccessListener { reqSnap ->
                        val req = reqSnap.getValue(ConnectionRequest::class.java)
                        if (req != null && req.status == ConnectionRequest.STATUS_PENDING) {
                            requests.add(req)
                        }
                        fetched++
                        if (fetched == count.toInt()) {
                            onUpdate(requests.sortedByDescending { it.createdAt })
                        }
                    }.addOnFailureListener {
                        fetched++
                        if (fetched == count.toInt()) {
                            onUpdate(requests.sortedByDescending { it.createdAt })
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(emptyList())
            }
        }
        incomingRequestsRef.child(receiverUid).addValueEventListener(listener)
        return listener
    }

    fun listenToOutgoingRequests(
        senderUid: String,
        onUpdate: (List<ConnectionRequest>) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requests = mutableListOf<ConnectionRequest>()
                val count = snapshot.childrenCount
                if (count == 0L) {
                    onUpdate(emptyList())
                    return
                }

                var fetched = 0
                for (child in snapshot.children) {
                    val reqId = child.key ?: continue
                    requestsRef.child(reqId).get().addOnSuccessListener { reqSnap ->
                        val req = reqSnap.getValue(ConnectionRequest::class.java)
                        if (req != null && req.status == ConnectionRequest.STATUS_PENDING) {
                            requests.add(req)
                        }
                        fetched++
                        if (fetched == count.toInt()) {
                            onUpdate(requests.sortedByDescending { it.createdAt })
                        }
                    }.addOnFailureListener {
                        fetched++
                        if (fetched == count.toInt()) {
                            onUpdate(requests.sortedByDescending { it.createdAt })
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(emptyList())
            }
        }
        outgoingRequestsRef.child(senderUid).addValueEventListener(listener)
        return listener
    }

    suspend fun acceptConnectionRequest(
        request: ConnectionRequest,
        currentUid: String
    ): Result<Unit> {
        val receiverUid = request.resolvedReceiverUid
        val senderUid = request.resolvedSenderUid

        if (currentUid != receiverUid) {
            return Result.failure(Exception("Only the recipient can accept this connection request."))
        }

        return try {
            val now = System.currentTimeMillis()

            // 1. Update request status to accepted
            val updates = mapOf(
                "status" to ConnectionRequest.STATUS_ACCEPTED,
                "updatedAt" to now
            )
            requestsRef.child(request.requestId).updateChildren(updates).await()

            // 2. Add reciprocal trusted contacts
            val receiverContact = TrustedContact(
                contactUserId = senderUid,
                name = request.resolvedSenderName,
                phone = request.resolvedSenderPhone,
                status = TrustedContact.STATUS_APPROVED,
                createdAt = request.createdAt,
                approvedAt = now,
                sharingEnabled = true
            )
            connectionsRef.child(receiverUid).child(senderUid).setValue(receiverContact).await()

            val senderContact = TrustedContact(
                contactUserId = receiverUid,
                name = request.receiverName.ifBlank { "SafeTrack Contact" },
                phone = request.receiverPhone,
                status = TrustedContact.STATUS_APPROVED,
                createdAt = request.createdAt,
                approvedAt = now,
                sharingEnabled = true
            )
            connectionsRef.child(senderUid).child(receiverUid).setValue(senderContact).await()

            // 3. Remove pointers from pending incoming/outgoing lists
            incomingRequestsRef.child(receiverUid).child(request.requestId).removeValue().await()
            outgoingRequestsRef.child(senderUid).child(request.requestId).removeValue().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectConnectionRequest(
        request: ConnectionRequest,
        currentUid: String
    ): Result<Unit> {
        val receiverUid = request.resolvedReceiverUid
        if (currentUid != receiverUid) {
            return Result.failure(Exception("Only the recipient can reject this connection request."))
        }

        return try {
            val now = System.currentTimeMillis()
            requestsRef.child(request.requestId).updateChildren(
                mapOf(
                    "status" to ConnectionRequest.STATUS_REJECTED,
                    "updatedAt" to now
                )
            ).await()

            incomingRequestsRef.child(receiverUid).child(request.requestId).removeValue().await()
            outgoingRequestsRef.child(request.resolvedSenderUid).child(request.requestId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelOutgoingRequest(
        request: ConnectionRequest,
        currentUid: String
    ): Result<Unit> {
        val senderUid = request.resolvedSenderUid
        if (currentUid != senderUid) {
            return Result.failure(Exception("Only the sender can cancel this connection request."))
        }

        return try {
            val now = System.currentTimeMillis()
            requestsRef.child(request.requestId).updateChildren(
                mapOf(
                    "status" to ConnectionRequest.STATUS_CANCELLED,
                    "updatedAt" to now
                )
            ).await()

            incomingRequestsRef.child(request.resolvedReceiverUid).child(request.requestId).removeValue().await()
            outgoingRequestsRef.child(senderUid).child(request.requestId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Approved Contacts & Revocation ---

    fun listenToApprovedContacts(
        userId: String,
        onUpdate: (List<TrustedContact>) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val contacts = mutableListOf<TrustedContact>()
                for (child in snapshot.children) {
                    val contact = child.getValue(TrustedContact::class.java)
                    if (contact != null && contact.status == TrustedContact.STATUS_APPROVED) {
                        contacts.add(contact)
                    }
                }
                onUpdate(contacts.sortedBy { it.name })
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(emptyList())
            }
        }
        connectionsRef.child(userId).addValueEventListener(listener)
        return listener
    }

    suspend fun revokeContact(currentUid: String, contactUserId: String): Result<Unit> {
        return try {
            // Remove authorization on both sides
            connectionsRef.child(currentUid).child(contactUserId).removeValue().await()
            connectionsRef.child(contactUserId).child(currentUid).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Location Tracking & Streaming ---

    suspend fun uploadLocation(location: LocationData): Result<Unit> {
        return try {
            val userLocRef = locationsRef.child(location.userId)
            val currentSnap = userLocRef.child("timestamp").get().await()
            val existingTimestamp = currentSnap.getValue(Long::class.java) ?: 0L

            if (location.timestamp >= existingTimestamp) {
                userLocRef.setValue(location).await()
            }
            // Append to authorized location history
            historyRef.child(location.userId).push().setValue(location).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearRemoteHistory(userId: String): Result<Unit> {
        return try {
            historyRef.child(userId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSharingStatus(userId: String, status: String): Result<Unit> {
        return try {
            locationsRef.child(userId).child("sharingStatus").setValue(status).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToUserLocation(
        targetUserId: String,
        onUpdate: (LocationData?) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(LocationData::class.java)
                onUpdate(location)
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(null)
            }
        }
        locationsRef.child(targetUserId).addValueEventListener(listener)
        return listener
    }

    fun removeListener(referencePath: String, listener: ValueEventListener) {
        db.getReference(referencePath).removeEventListener(listener)
    }

    // --- SOS Emergency System ---

    suspend fun triggerSos(alert: SosAlert): Result<String> {
        return try {
            val alertId = sosRef.push().key ?: return Result.failure(Exception("Cannot generate SOS key"))
            val alertWithId = alert.copy(alertId = alertId)
            
            // 1. Save detailed historical alert
            sosRef.child(alertId).setValue(alertWithId).await()
            // 2. Set active distress pointer for real-time monitoring
            activeSosRef.child(alert.senderId).setValue(alertWithId).await()
            
            Result.success(alertId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveSos(alertId: String, senderId: String, notes: String = ""): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val updates = mapOf(
                "status" to SosAlert.STATUS_RESOLVED,
                "resolvedAt" to now,
                "resolutionNotes" to notes
            )
            // 1. Update historical record
            sosRef.child(alertId).updateChildren(updates).await()
            // 2. Remove from active SOS pointer
            activeSosRef.child(senderId).removeValue().await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToActiveSosAlerts(
        senderIds: Set<String>,
        onUpdate: (List<SosAlert>) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = mutableListOf<SosAlert>()
                for (child in snapshot.children) {
                    val alert = child.getValue(SosAlert::class.java)
                    if (alert != null &&
                        alert.status == SosAlert.STATUS_ACTIVE &&
                        senderIds.contains(alert.senderId)
                    ) {
                        alerts.add(alert)
                    }
                }
                onUpdate(alerts)
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(emptyList())
            }
        }
        activeSosRef.addValueEventListener(listener)
        return listener
    }

    fun listenToActiveSosBySender(
        senderId: String,
        onUpdate: (SosAlert?) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alert = snapshot.getValue(SosAlert::class.java)
                onUpdate(alert)
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(null)
            }
        }
        activeSosRef.child(senderId).addValueEventListener(listener)
        return listener
    }
}
