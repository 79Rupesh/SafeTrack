package com.safetrack.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.safetrack.R
import com.safetrack.data.model.LocationData
import com.safetrack.data.model.TrustedContact
import com.safetrack.databinding.ItemFriendViewerBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewerFriendsAdapter(
    private val onViewMapClick: (TrustedContact, LocationData?) -> Unit,
    private val onRemoveClick: (TrustedContact) -> Unit
) : RecyclerView.Adapter<ViewerFriendsAdapter.FriendViewHolder>() {

    private val contacts = mutableListOf<TrustedContact>()
    private val locationMap = mutableMapOf<String, LocationData>()

    fun submitData(newContacts: List<TrustedContact>, newLocations: Map<String, LocationData>) {
        contacts.clear()
        contacts.addAll(newContacts)
        locationMap.clear()
        locationMap.putAll(newLocations)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendViewerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val contact = contacts[position]
        val location = locationMap[contact.contactUserId]
        holder.bind(contact, location)
    }

    override fun getItemCount(): Int = contacts.size

    inner class FriendViewHolder(private val binding: ItemFriendViewerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: TrustedContact, location: LocationData?) {
            binding.tvViewerFriendName.text = contact.name.ifBlank { "Friend" }
            binding.tvViewerFriendPhone.text = contact.phone

            if (location != null) {
                val now = System.currentTimeMillis()
                val diffMinutes = (now - location.timestamp) / (1000 * 60)

                if (location.sharingStatus == LocationData.STATUS_PAUSED) {
                    binding.tvViewerFriendStatusBadge.text = "PAUSED"
                    binding.tvViewerFriendStatusBadge.setBackgroundResource(R.drawable.bg_pill_paused)
                    binding.tvViewerFriendStatusBadge.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.pill_paused_text)
                    )
                } else if (diffMinutes > 15) {
                    binding.tvViewerFriendStatusBadge.text = "OFFLINE"
                    binding.tvViewerFriendStatusBadge.setBackgroundResource(R.drawable.bg_stat_card)
                    binding.tvViewerFriendStatusBadge.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.text_secondary)
                    )
                } else {
                    binding.tvViewerFriendStatusBadge.text = "LIVE"
                    binding.tvViewerFriendStatusBadge.setBackgroundResource(R.drawable.bg_pill_live)
                    binding.tvViewerFriendStatusBadge.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.pill_active_text)
                    )
                }

                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                val timeStr = sdf.format(Date(location.timestamp))
                val batteryStr = if (location.batteryPct >= 0) " • ⚡ ${location.batteryPct}%" else ""
                val speedStr = if (location.speed > 0f) String.format(Locale.US, " • 🚗 %.1f km/h", location.speed * 3.6f) else " • 🚗 Stationary"
                val accStr = if (location.accuracy > 0) String.format(Locale.US, " • 📍 ±%.1fm", location.accuracy) else ""
                binding.tvViewerLastUpdate.text = "$timeStr$batteryStr$speedStr$accStr"
            } else {
                binding.tvViewerFriendStatusBadge.text = "WAITING"
                binding.tvViewerFriendStatusBadge.setBackgroundResource(R.drawable.bg_stat_card)
                binding.tvViewerFriendStatusBadge.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_hint)
                )
                binding.tvViewerLastUpdate.text = "Waiting for initial location update..."
            }

            binding.btnViewOnMap.setOnClickListener {
                onViewMapClick(contact, location)
            }

            binding.btnRemoveFriend.setOnClickListener {
                onRemoveClick(contact)
            }
        }
    }
}
