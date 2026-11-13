package com.safetrack.contacts

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.ValueEventListener
import com.safetrack.SafeTrackApp
import com.safetrack.data.model.ConnectionRequest
import com.safetrack.data.model.TrustedContact
import com.safetrack.databinding.ActivityConnectionRequestsBinding
import com.safetrack.firebase.FirebaseRepository
import kotlinx.coroutines.launch

class ConnectionRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectionRequestsBinding
    private val firebaseRepo = FirebaseRepository()

    private lateinit var incomingAdapter: ConnectionRequestsAdapter
    private lateinit var outgoingAdapter: OutgoingRequestsAdapter
    private lateinit var approvedAdapter: TrustedContactsAdapter

    private var currentUid: String = ""
    private var incomingListener: ValueEventListener? = null
    private var outgoingListener: ValueEventListener? = null
    private var contactsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectionRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionManager = (application as SafeTrackApp).sessionManager
        currentUid = sessionManager.uid ?: ""

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerViews()
        setupTabLayout()
        setupListeners()
        loadAllData()
    }

    private fun setupRecyclerViews() {
        // 1. Incoming
        incomingAdapter = ConnectionRequestsAdapter(
            onApproveClick = { request -> approveRequest(request) },
            onRejectClick = { request -> rejectRequest(request) }
        )
        binding.rvIncomingRequests.layoutManager = LinearLayoutManager(this)
        binding.rvIncomingRequests.adapter = incomingAdapter

        // 2. Sent
        outgoingAdapter = OutgoingRequestsAdapter(
            onCancelClick = { request -> cancelSentRequest(request) }
        )
        binding.rvSentRequests.layoutManager = LinearLayoutManager(this)
        binding.rvSentRequests.adapter = outgoingAdapter

        // 3. Approved Contacts
        approvedAdapter = TrustedContactsAdapter { contact ->
            confirmRevokeContact(contact)
        }
        binding.rvApprovedContacts.layoutManager = LinearLayoutManager(this)
        binding.rvApprovedContacts.adapter = approvedAdapter
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutIncoming.visibility = View.VISIBLE
                        binding.layoutSent.visibility = View.GONE
                        binding.layoutApproved.visibility = View.GONE
                    }
                    1 -> {
                        binding.layoutIncoming.visibility = View.GONE
                        binding.layoutSent.visibility = View.VISIBLE
                        binding.layoutApproved.visibility = View.GONE
                    }
                    2 -> {
                        binding.layoutIncoming.visibility = View.GONE
                        binding.layoutSent.visibility = View.GONE
                        binding.layoutApproved.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupListeners() {
        binding.fabAddContact.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }
    }

    private fun loadAllData() {
        if (currentUid.isBlank()) return

        // 1. Incoming Requests
        incomingListener = firebaseRepo.listenToIncomingRequests(currentUid) { incoming ->
            binding.tabLayout.getTabAt(0)?.text = "Incoming (${incoming.size})"
            if (incoming.isEmpty()) {
                binding.tvEmptyIncoming.visibility = View.VISIBLE
                binding.rvIncomingRequests.visibility = View.GONE
            } else {
                binding.tvEmptyIncoming.visibility = View.GONE
                binding.rvIncomingRequests.visibility = View.VISIBLE
                incomingAdapter.submitList(incoming)
            }
        }

        // 2. Outgoing Requests
        outgoingListener = firebaseRepo.listenToOutgoingRequests(currentUid) { outgoing ->
            binding.tabLayout.getTabAt(1)?.text = "Sent (${outgoing.size})"
            if (outgoing.isEmpty()) {
                binding.tvEmptySent.visibility = View.VISIBLE
                binding.rvSentRequests.visibility = View.GONE
            } else {
                binding.tvEmptySent.visibility = View.GONE
                binding.rvSentRequests.visibility = View.VISIBLE
                outgoingAdapter.submitList(outgoing)
            }
        }

        // 3. Approved Contacts
        contactsListener = firebaseRepo.listenToApprovedContacts(currentUid) { contacts ->
            binding.tabLayout.getTabAt(2)?.text = "Contacts (${contacts.size})"
            if (contacts.isEmpty()) {
                binding.tvEmptyApproved.visibility = View.VISIBLE
                binding.rvApprovedContacts.visibility = View.GONE
            } else {
                binding.tvEmptyApproved.visibility = View.GONE
                binding.rvApprovedContacts.visibility = View.VISIBLE
                approvedAdapter.submitList(contacts)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentUid.isNotBlank()) {
            incomingListener?.let { firebaseRepo.removeListener("incomingRequests/$currentUid", it) }
            outgoingListener?.let { firebaseRepo.removeListener("outgoingRequests/$currentUid", it) }
            contactsListener?.let { firebaseRepo.removeListener("connections/$currentUid", it) }
        }
    }

    private fun approveRequest(request: ConnectionRequest) {
        lifecycleScope.launch {
            val result = firebaseRepo.acceptConnectionRequest(request, currentUid)
            if (result.isSuccess) {
                Toast.makeText(this@ConnectionRequestsActivity, "Connection accepted with ${request.resolvedSenderName}!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ConnectionRequestsActivity, "Error: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun rejectRequest(request: ConnectionRequest) {
        lifecycleScope.launch {
            val result = firebaseRepo.rejectConnectionRequest(request, currentUid)
            if (result.isSuccess) {
                Toast.makeText(this@ConnectionRequestsActivity, "Request rejected.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ConnectionRequestsActivity, "Error: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cancelSentRequest(request: ConnectionRequest) {
        lifecycleScope.launch {
            val result = firebaseRepo.cancelOutgoingRequest(request, currentUid)
            if (result.isSuccess) {
                Toast.makeText(this@ConnectionRequestsActivity, "Request cancelled.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ConnectionRequestsActivity, "Error: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmRevokeContact(contact: TrustedContact) {
        AlertDialog.Builder(this)
            .setTitle("Revoke Access")
            .setMessage("Do you want to disconnect from ${contact.name}? They will immediately lose authorization to read your location.")
            .setPositiveButton("Revoke") { _, _ ->
                lifecycleScope.launch {
                    val result = firebaseRepo.revokeContact(currentUid, contact.contactUserId)
                    if (result.isSuccess) {
                        Toast.makeText(this@ConnectionRequestsActivity, "Access revoked.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ConnectionRequestsActivity, "Error: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
