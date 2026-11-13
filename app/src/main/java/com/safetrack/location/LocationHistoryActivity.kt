package com.safetrack.location

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.safetrack.SafeTrackApp
import com.safetrack.databinding.ActivityLocationHistoryBinding
import com.safetrack.firebase.FirebaseRepository
import kotlinx.coroutines.launch

class LocationHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationHistoryBinding
    private lateinit var locationRepository: LocationRepository
    private lateinit var adapter: LocationHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as SafeTrackApp
        locationRepository = LocationRepository(app.database.locationDao(), FirebaseRepository())

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        observeHistory()
        setupActions()
    }

    private fun setupRecyclerView() {
        adapter = LocationHistoryAdapter()
        binding.rvLocationHistory.layoutManager = LinearLayoutManager(this)
        binding.rvLocationHistory.adapter = adapter
    }

    private fun observeHistory() {
        val sessionManager = (application as SafeTrackApp).sessionManager
        val userId = sessionManager.userId ?: return

        locationRepository.getLocationHistory(userId).observe(this) { records ->
            if (records.isNullOrEmpty()) {
                binding.tvEmptyHistory.visibility = View.VISIBLE
                binding.rvLocationHistory.visibility = View.GONE
                binding.tvSyncStatusSummary.text = "Offline Queue: Empty"
            } else {
                binding.tvEmptyHistory.visibility = View.GONE
                binding.rvLocationHistory.visibility = View.VISIBLE
                adapter.submitList(records)

                val unsyncedCount = records.count { !it.isSynced }
                if (unsyncedCount > 0) {
                    binding.tvSyncStatusSummary.text = "Offline Queue: $unsyncedCount pending upload"
                } else {
                    binding.tvSyncStatusSummary.text = "Offline Queue: All ${records.size} points synced"
                }
            }
        }
    }

    private fun setupActions() {
        binding.btnForceSync.setOnClickListener {
            lifecycleScope.launch {
                val syncedCount = locationRepository.syncPendingLocations(applicationContext)
                Toast.makeText(
                    this@LocationHistoryActivity,
                    "Synced $syncedCount pending record(s) to cloud.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to delete all stored location points from your device?")
                .setPositiveButton("Clear") { _, _ ->
                    val sessionManager = (application as SafeTrackApp).sessionManager
                    val userId = sessionManager.userId ?: return@setPositiveButton
                    lifecycleScope.launch {
                        locationRepository.clearHistory(userId)
                        Toast.makeText(this@LocationHistoryActivity, "Location history cleared.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
