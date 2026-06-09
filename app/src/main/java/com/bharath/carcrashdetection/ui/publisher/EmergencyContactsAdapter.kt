package com.bharath.carcrashdetection.ui.publisher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bharath.carcrashdetection.R
import com.bharath.carcrashdetection.data.model.EmergencyContact

class EmergencyContactsAdapter(
    private val contacts: MutableList<EmergencyContact> = mutableListOf(),
    private val onContactClick: (EmergencyContact) -> Unit,
    private val onContactDelete: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<EmergencyContactsAdapter.ContactViewHolder>() {

    class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvContactName)
        val tvRelationship: TextView = view.findViewById(R.id.tvContactRelationship)
        val tvPhone: TextView = view.findViewById(R.id.tvContactPhone)
        val tvEmail: TextView = view.findViewById(R.id.tvContactEmail)
        val tvPrimary: TextView = view.findViewById(R.id.tvContactPrimary)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        
        holder.tvName.text = contact.name
        holder.tvRelationship.text = contact.relationship
        holder.tvPhone.text = contact.phoneNumber
        holder.tvEmail.text = contact.email ?: "No email"
        holder.tvPrimary.visibility = if (contact.isPrimary) View.VISIBLE else View.GONE
        
        holder.itemView.setOnClickListener {
            onContactClick(contact)
        }
        
        holder.btnDelete.setOnClickListener {
            onContactDelete(contact)
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<EmergencyContact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }

    fun addContact(contact: EmergencyContact) {
        contacts.add(contact)
        notifyItemInserted(contacts.size - 1)
    }

    fun removeContact(contact: EmergencyContact) {
        val index = contacts.indexOf(contact)
        if (index != -1) {
            contacts.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updateContact(contact: EmergencyContact) {
        val index = contacts.indexOfFirst { it.name == contact.name && it.phoneNumber == contact.phoneNumber }
        if (index != -1) {
            contacts[index] = contact
            notifyItemChanged(index)
        }
    }
}
