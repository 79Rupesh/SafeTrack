package com.safetrack.contacts

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.safetrack.SafeTrackApp
import com.safetrack.data.model.Invitation
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivityAddContactBinding
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.utils.NetworkUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddContactBinding
    private val firebaseRepo = FirebaseRepository()

    private var currentUid: String = ""
    private var currentUser: User? = null
    private var targetUserPreview: User? = null
    private var targetInviteCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionManager = (application as SafeTrackApp).sessionManager
        currentUid = sessionManager.uid ?: ""
        currentUser = sessionManager.toUser()

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupTabs()
        setupListeners()
        loadMyInvitation()
    }

    private fun setupTabs() {
        binding.addContactTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.cardTargetPreview.visibility = View.GONE
                when (tab?.position) {
                    0 -> {
                        binding.sectionInviteCode.visibility = View.VISIBLE
                        binding.sectionMobileNumber.visibility = View.GONE
                        binding.sectionMyCode.visibility = View.GONE
                    }
                    1 -> {
                        binding.sectionInviteCode.visibility = View.GONE
                        binding.sectionMobileNumber.visibility = View.VISIBLE
                        binding.sectionMyCode.visibility = View.GONE
                    }
                    2 -> {
                        binding.sectionInviteCode.visibility = View.GONE
                        binding.sectionMobileNumber.visibility = View.GONE
                        binding.sectionMyCode.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupListeners() {
        binding.btnLookupCode.setOnClickListener {
            val code = binding.etCodeInput.text?.toString()?.trim()?.uppercase() ?: ""
            if (code.length < 5) {
                binding.tilCodeInput.error = "Please enter a valid code (e.g. ST-123456)"
                return@setOnClickListener
            }
            binding.tilCodeInput.error = null
            lookupInvitationCode(code)
        }

        binding.btnLookupPhone.setOnClickListener {
            val phone = binding.etPhoneInput.text?.toString()?.trim() ?: ""
            val digits = phone.replace("[^0-9]".toRegex(), "")
            if (digits.length != 10) {
                binding.tilPhoneInput.error = "Please enter a valid 10-digit mobile number"
                return@setOnClickListener
            }
            binding.tilPhoneInput.error = null
            lookupMobileNumber("+91$digits")
        }

        binding.btnConfirmSendRequest.setOnClickListener {
            val target = targetUserPreview
            if (target != null) {
                sendRequestToTarget(target, targetInviteCode)
            }
        }

        binding.btnRegenerateCode.setOnClickListener {
            regenerateMyCode()
        }

        binding.btnShareSheet.setOnClickListener {
            shareMyCode()
        }
    }

    private fun lookupInvitationCode(code: String) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection.", Toast.LENGTH_SHORT).show()
            return
        }

        setLookupState(true)

        lifecycleScope.launch {
            val invitation = firebaseRepo.getInvitation(code)
            setLookupState(false)

            if (invitation == null) {
                binding.tilCodeInput.error = "Invitation code not found. Please verify with your contact."
                binding.cardTargetPreview.visibility = View.GONE
                return@launch
            }

            if (invitation.ownerUid == currentUid) {
                binding.tilCodeInput.error = "You cannot connect to your own invitation code."
                binding.cardTargetPreview.visibility = View.GONE
                return@launch
            }

            if (invitation.isExpired) {
                binding.tilCodeInput.error = "This invitation code has expired. Please ask the sender to regenerate it."
                binding.cardTargetPreview.visibility = View.GONE
                return@launch
            }

            // Fetch owner profile details
            val owner = firebaseRepo.getUser(invitation.ownerUid)
            val resolvedUser = owner ?: User(
                uid = invitation.ownerUid,
                userId = invitation.ownerUid,
                name = invitation.ownerName,
                role = invitation.ownerRole
            )

            targetUserPreview = resolvedUser
            targetInviteCode = code

            displayTargetPreview(resolvedUser, "Invitation Status: Active (Expires soon)")
        }
    }

    private fun lookupMobileNumber(fullPhoneNumber: String) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection.", Toast.LENGTH_SHORT).show()
            return
        }

        setLookupState(true)

        lifecycleScope.launch {
            val targetUid = firebaseRepo.getUserIdByPhoneNumber(fullPhoneNumber)
            if (targetUid == null) {
                setLookupState(false)
                binding.tilPhoneInput.error = "No registered SafeTrack user found with this mobile number."
                binding.cardTargetPreview.visibility = View.GONE
                return@launch
            }

            if (targetUid == currentUid) {
                setLookupState(false)
                binding.tilPhoneInput.error = "You cannot connect to your own phone number."
                binding.cardTargetPreview.visibility = View.GONE
                return@launch
            }

            val targetUser = firebaseRepo.getUser(targetUid)
            setLookupState(false)

            if (targetUser == null) {
                binding.tilPhoneInput.error = "User profile could not be loaded."
                binding.cardTargetPreview.visibility = View.GONE
                return@launch
            }

            targetUserPreview = targetUser
            targetInviteCode = targetUser.inviteCode

            displayTargetPreview(targetUser, "Verified Registered Number")
        }
    }

    private fun displayTargetPreview(user: User, statusLabel: String) {
        binding.tvPreviewName.text = user.name.ifBlank { "SafeTrack User" }
        binding.tvPreviewRole.text = "Role: ${User.getRoleDisplayName(user.role)}"
        binding.tvPreviewExpiry.text = statusLabel
        binding.cardTargetPreview.visibility = View.VISIBLE
    }

    private fun sendRequestToTarget(target: User, inviteCode: String) {
        val sessionManager = (application as SafeTrackApp).sessionManager
        val sender = currentUser ?: sessionManager.toUser() ?: return

        setLookupState(true)

        lifecycleScope.launch {
            val result = firebaseRepo.sendConnectionRequest(sender, target, inviteCode)
            setLookupState(false)

            if (result.isSuccess) {
                Toast.makeText(
                    this@AddContactActivity,
                    "Connection request sent to ${target.name}! Waiting for their approval.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } else {
                Toast.makeText(
                    this@AddContactActivity,
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to send connection request.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadMyInvitation() {
        val sessionManager = (application as SafeTrackApp).sessionManager
        binding.tvMyShareCode.text = sessionManager.inviteCode

        lifecycleScope.launch {
            val invitation = firebaseRepo.getUserActiveInvitation(currentUid)
            if (invitation != null) {
                binding.tvMyShareCode.text = invitation.code
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                binding.tvCodeExpiresAt.text = "Valid until: ${sdf.format(Date(invitation.expiresAt))}"
            } else {
                binding.tvCodeExpiresAt.text = "Valid for 48 hours"
            }
        }
    }

    private fun regenerateMyCode() {
        val sessionManager = (application as SafeTrackApp).sessionManager
        val user = currentUser ?: sessionManager.toUser() ?: return

        setLookupState(true)

        lifecycleScope.launch {
            val result = firebaseRepo.regenerateInvitation(user)
            setLookupState(false)

            if (result.isSuccess) {
                val newInv = result.getOrNull()
                if (newInv != null) {
                    sessionManager.inviteCode = newInv.code
                    binding.tvMyShareCode.text = newInv.code
                    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    binding.tvCodeExpiresAt.text = "Valid until: ${sdf.format(Date(newInv.expiresAt))}"
                    Toast.makeText(this@AddContactActivity, "New invitation code generated: ${newInv.code}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@AddContactActivity, "Failed to regenerate code: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareMyCode() {
        val code = binding.tvMyShareCode.text.toString()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Connect with me on SafeTrack for mutual safety and live location sharing! My invitation code is: $code (valid for 48 hours)."
            )
        }
        startActivity(Intent.createChooser(shareIntent, "Share SafeTrack Invitation Code"))
    }

    private fun setLookupState(loading: Boolean) {
        binding.lookupProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLookupCode.isEnabled = !loading
        binding.btnLookupPhone.isEnabled = !loading
        binding.btnConfirmSendRequest.isEnabled = !loading
        binding.btnRegenerateCode.isEnabled = !loading
    }
}
