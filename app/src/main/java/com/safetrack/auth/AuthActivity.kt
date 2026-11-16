package com.safetrack.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.safetrack.SafeTrackApp
import com.safetrack.data.model.User
import com.safetrack.databinding.ActivityAuthBinding
import com.safetrack.utils.NetworkUtils
import java.util.concurrent.TimeUnit

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private var isSendingOtp = false
    private var selectedRole = User.ROLE_FRIEND

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        selectedRole = intent.getStringExtra("SELECTED_ROLE") ?: User.ROLE_FRIEND

        binding.btnSendOtp.setOnClickListener {
            if (isSendingOtp) return@setOnClickListener
            initiatePhoneVerification()
        }

        binding.btnQuickTestLogin.setOnClickListener {
            val testPhone = "+919876543210"
            val intent = Intent(this, OtpVerificationActivity::class.java).apply {
                putExtra(OtpVerificationActivity.EXTRA_PHONE_NUMBER, testPhone)
                putExtra(OtpVerificationActivity.EXTRA_VERIFICATION_ID, "demo_test_verification_id")
                putExtra(OtpVerificationActivity.EXTRA_AUTO_CODE, "123456")
                putExtra(OtpVerificationActivity.EXTRA_SELECTED_ROLE, selectedRole)
            }
            startActivity(intent)
        }
    }

    private fun initiatePhoneVerification() {
        val rawInput = binding.etPhone.text?.toString()?.trim() ?: ""

        // Validate clean 10-digit number
        val digitsOnly = rawInput.replace("[^0-9]".toRegex(), "")
        if (digitsOnly.length != 10) {
            binding.tilPhone.error = "Please enter a valid 10-digit mobile number"
            return
        }

        if (!NetworkUtils.isNetworkAvailable(this)) {
            binding.tilPhone.error = "No internet connection. Please verify your data connection."
            return
        }

        binding.tilPhone.error = null
        val fullPhoneNumber = "+91$digitsOnly"

        setLoadingState(true, "Requesting verification code for $fullPhoneNumber...")

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Instant auto-retrieval or test number instant resolution
                setLoadingState(false)
                val smsCode = credential.smsCode
                val intent = Intent(this@AuthActivity, OtpVerificationActivity::class.java).apply {
                    putExtra(OtpVerificationActivity.EXTRA_PHONE_NUMBER, fullPhoneNumber)
                    putExtra(OtpVerificationActivity.EXTRA_AUTO_CODE, smsCode)
                    putExtra(OtpVerificationActivity.EXTRA_SELECTED_ROLE, selectedRole)
                }
                startActivity(intent)
                finish()
            }

            override fun onVerificationFailed(e: FirebaseException) {
                setLoadingState(false)
                handleVerificationError(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                setLoadingState(false)
                PhoneAuthTokenHolder.resendingToken = token

                val intent = Intent(this@AuthActivity, OtpVerificationActivity::class.java).apply {
                    putExtra(OtpVerificationActivity.EXTRA_PHONE_NUMBER, fullPhoneNumber)
                    putExtra(OtpVerificationActivity.EXTRA_VERIFICATION_ID, verificationId)
                    putExtra(OtpVerificationActivity.EXTRA_SELECTED_ROLE, selectedRole)
                }
                startActivity(intent)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(fullPhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun handleVerificationError(e: FirebaseException) {
        val message = when (e) {
            is FirebaseAuthInvalidCredentialsException -> {
                "The phone number format is invalid. Please check and try again."
            }
            is FirebaseTooManyRequestsException -> {
                "Too many SMS requests sent for this number. Please wait a few minutes before trying again."
            }
            else -> {
                e.localizedMessage ?: "Verification failed. Please check your internet connection."
            }
        }
        binding.tilPhone.error = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setLoadingState(loading: Boolean, message: String = "") {
        isSendingOtp = loading
        binding.btnSendOtp.isEnabled = !loading
        binding.etPhone.isEnabled = !loading
        binding.layoutLoading.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.tvLoadingMessage.text = message
        }
    }
}
