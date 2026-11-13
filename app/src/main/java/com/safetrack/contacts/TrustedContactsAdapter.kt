package com.safetrack.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.safetrack.data.model.TrustedContact
import com.safetrack.databinding.ItemTrustedContactBinding

class TrustedContactsAdapter(
    private val onRevokeClick: (TrustedContact) -> Unit
) : RecyclerView.Adapter<TrustedContactsAdapter.ContactViewHolder>() {

    private val items = mutableListOf<TrustedContact>()

    fun submitList(newItems: List<TrustedContact>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemTrustedContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ContactViewHolder(private val binding: ItemTrustedContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: TrustedContact) {
            binding.tvContactName.text = contact.name.ifBlank { "Contact" }
            binding.tvContactPhone.text = contact.phone.ifBlank { "SafeTrack User" }

            binding.btnRevokeContact.setOnClickListener {
                onRevokeClick(contact)
            }
        }
    }
}
