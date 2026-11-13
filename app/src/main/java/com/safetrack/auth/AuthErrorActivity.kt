package com.safetrack.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.safetrack.databinding.ActivityAuthErrorBinding

class AuthErrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
            ?: "An unexpected authentication error occurred."
        val errorTitle = intent.getStringExtra(EXTRA_ERROR_TITLE)
            ?: "Authentication Failed"

        binding.tvErrorTitle.text = errorTitle
        binding.tvErrorMessage.text = errorMessage

        binding.btnRetryAuth.setOnClickListener {
            finish()
        }

        binding.btnBackToLogin.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    companion object {
        const val EXTRA_ERROR_TITLE = "extra_error_title"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
    }
}
