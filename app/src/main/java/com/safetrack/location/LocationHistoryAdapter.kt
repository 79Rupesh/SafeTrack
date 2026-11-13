package com.safetrack.location

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.safetrack.R
import com.safetrack.database.entity.LocationRecord
import com.safetrack.databinding.ItemLocationHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationHistoryAdapter :
    RecyclerView.Adapter<LocationHistoryAdapter.HistoryViewHolder>() {

    private val records = mutableListOf<LocationRecord>()

    fun submitList(newRecords: List<LocationRecord>) {
        records.clear()
        records.addAll(newRecords)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemLocationHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    inner class HistoryViewHolder(private val binding: ItemLocationHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: LocationRecord) {
            val latStr = String.format(Locale.US, "%.5f° N", record.latitude)
            val lngStr = String.format(Locale.US, "%.5f° E", record.longitude)
            binding.tvHistoryCoordinates.text = "$latStr, $lngStr"

            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
            binding.tvHistoryTimestamp.text = sdf.format(Date(record.timestamp))
            binding.tvHistoryAccuracy.text = "±${record.accuracy.toInt()}m"

            if (record.isSynced) {
                binding.tvHistorySyncBadge.text = "Synced"
                binding.tvHistorySyncBadge.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.safety_green)
                )
            } else {
                binding.tvHistorySyncBadge.text = "Pending"
                binding.tvHistorySyncBadge.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.safety_amber)
                )
            }
        }
    }
}
