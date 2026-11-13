package com.safetrack.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.safetrack.data.model.ConnectionRequest
import com.safetrack.databinding.ItemConnectionRequestBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConnectionRequestsAdapter(
    private val onApproveClick: (ConnectionRequest) -> Unit,
    private val onRejectClick: (ConnectionRequest) -> Unit
) : RecyclerView.Adapter<ConnectionRequestsAdapter.RequestViewHolder>() {

    private val requests = mutableListOf<ConnectionRequest>()

    fun submitList(newRequests: List<ConnectionRequest>) {
        requests.clear()
        requests.addAll(newRequests)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemConnectionRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount(): Int = requests.size

    inner class RequestViewHolder(private val binding: ItemConnectionRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: ConnectionRequest) {
            binding.tvRequesterName.text = request.resolvedSenderName.ifBlank { "SafeTrack User" }
            binding.tvRequesterPhone.text = request.resolvedSenderPhone

            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            binding.tvRequestTime.text = "Requested: ${sdf.format(Date(request.createdAt))}"

            binding.btnApprove.setOnClickListener {
                onApproveClick(request)
            }

            binding.btnReject.setOnClickListener {
                onRejectClick(request)
            }
        }
    }
}
