package com.safetrack.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.safetrack.R
import com.safetrack.data.model.ConnectionRequest
import com.safetrack.databinding.ItemSentRequestBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OutgoingRequestsAdapter(
    private val onCancelClick: (ConnectionRequest) -> Unit
) : RecyclerView.Adapter<OutgoingRequestsAdapter.SentViewHolder>() {

    private val requests = mutableListOf<ConnectionRequest>()

    fun submitList(newRequests: List<ConnectionRequest>) {
        requests.clear()
        requests.addAll(newRequests)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SentViewHolder {
        val binding = ItemSentRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SentViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount(): Int = requests.size

    inner class SentViewHolder(private val binding: ItemSentRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: ConnectionRequest) {
            binding.tvSentReceiverName.text = request.receiverName.ifBlank { "SafeTrack User" }
            binding.tvSentReceiverPhone.text = request.receiverPhone

            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            binding.tvSentRequestTime.text = "Requested: ${sdf.format(Date(request.createdAt))}"

            binding.tvSentStatusBadge.text = request.status.uppercase()
            if (request.status == ConnectionRequest.STATUS_PENDING) {
                binding.tvSentStatusBadge.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.safety_amber)
                )
            } else {
                binding.tvSentStatusBadge.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_secondary)
                )
            }

            binding.btnCancelSentRequest.setOnClickListener {
                onCancelClick(request)
            }
        }
    }
}
