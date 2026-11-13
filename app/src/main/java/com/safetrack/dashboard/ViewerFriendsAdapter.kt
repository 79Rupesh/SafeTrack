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
                    binding.tvViewerFriendStatusBadge.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.safety_amber)
                    )
                } else if (diffMinutes > 15) {
                    binding.tvViewerFriendStatusBadge.text = "OFFLINE"
                    binding.tvViewerFriendStatusBadge.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.text_hint)
                    )
                } else {
                    binding.tvViewerFriendStatusBadge.text = "LIVE"
                    binding.tvViewerFriendStatusBadge.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.safety_green)
                    )
                }

                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                val timeStr = sdf.format(Date(location.timestamp))
                val batteryStr = if (location.batteryPct >= 0) " • Battery: ${location.batteryPct}%" else ""
                binding.tvViewerLastUpdate.text = "Last updated: $timeStr$batteryStr"
            } else {
                binding.tvViewerFriendStatusBadge.text = "WAITING"
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
