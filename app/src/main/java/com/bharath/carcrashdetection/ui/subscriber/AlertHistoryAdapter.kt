package com.bharath.carcrashdetection.ui.subscriber

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bharath.carcrashdetection.data.model.Incident
import com.bharath.carcrashdetection.data.model.IncidentSeverity
import com.bharath.carcrashdetection.databinding.ItemAlertCardBinding
import com.bharath.carcrashdetection.databinding.*
import java.text.SimpleDateFormat
import java.util.*

class AlertHistoryAdapter(
    private val onItemClick: (Incident) -> Unit
) : ListAdapter<Incident, AlertHistoryAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlertViewHolder(
        private val binding: ItemAlertCardBinding,
        private val onItemClick: (Incident) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(incident: Incident) {
            binding.root.setOnClickListener { onItemClick(incident) }
            
            binding.tvVictimName.text = incident.description ?: "Emergency Alert"
            binding.tvLocation.text = if (incident.latitude != null && incident.longitude != null) {
                "Lat: %.4f, Lng: %.4f".format(incident.latitude, incident.longitude)
            } else {
                "Location: Unknown"
            }
            binding.tvTime.text = formatTimestamp(incident.timestamp)
            binding.tvSeverity.text = incident.severity.name
            
            // Set severity color
            val severityColor = when (incident.severity) {
                IncidentSeverity.CRITICAL -> android.graphics.Color.RED
                IncidentSeverity.HIGH -> android.graphics.Color.parseColor("#FF6B35")
                IncidentSeverity.MEDIUM -> android.graphics.Color.parseColor("#FFA500")
                IncidentSeverity.LOW -> android.graphics.Color.parseColor("#4CAF50")
            }
            
            binding.tvSeverity.setTextColor(severityColor)
        }
        
        private fun formatTimestamp(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }

    private class AlertDiffCallback : DiffUtil.ItemCallback<Incident>() {
        override fun areItemsTheSame(oldItem: Incident, newItem: Incident): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Incident, newItem: Incident): Boolean {
            return oldItem == newItem
        }
    }
}
