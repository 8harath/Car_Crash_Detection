package com.bharath.carcrashdetection.ui.publisher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bharath.carcrashdetection.R
import com.bharath.carcrashdetection.util.Esp32Manager

class DeviceAdapter(
    private val devices: MutableList<Device> = mutableListOf(),
    private val onDeviceClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivDeviceIcon: ImageView = view.findViewById(R.id.ivDeviceIcon)
        val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvDeviceAddress: TextView = view.findViewById(R.id.tvDeviceAddress)
        val tvDeviceType: TextView = view.findViewById(R.id.tvDeviceType)
        val ivSignalStrength: ImageView = view.findViewById(R.id.ivSignalStrength)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        
        holder.tvDeviceName.text = device.name
        holder.tvDeviceAddress.text = device.address
        holder.tvDeviceType.text = when (device.deviceType) {
            Esp32Manager.ConnectionType.BLUETOOTH_CLASSIC -> "Bluetooth Classic"
            Esp32Manager.ConnectionType.BLUETOOTH_BLE -> "Bluetooth BLE"
            Esp32Manager.ConnectionType.WIFI_DIRECT -> "WiFi Direct"
            Esp32Manager.ConnectionType.NONE -> "Unknown"
        }
        
        // Set signal strength icon
        val signalIcon = when {
            device.signalStrength >= 80 -> R.drawable.ic_emergency // Strong signal
            device.signalStrength >= 50 -> R.drawable.ic_emergency // Medium signal
            else -> R.drawable.ic_emergency // Weak signal
        }
        holder.ivSignalStrength.setImageResource(signalIcon)
        
        // Set device icon based on connection type
        val deviceIcon = when (device.deviceType) {
            Esp32Manager.ConnectionType.BLUETOOTH_CLASSIC -> R.drawable.ic_emergency
            Esp32Manager.ConnectionType.BLUETOOTH_BLE -> R.drawable.ic_emergency
            Esp32Manager.ConnectionType.WIFI_DIRECT -> R.drawable.ic_emergency
            Esp32Manager.ConnectionType.NONE -> R.drawable.ic_emergency
        }
        holder.ivDeviceIcon.setImageResource(deviceIcon)
        
        holder.itemView.setOnClickListener {
            onDeviceClick(device)
        }
    }

    override fun getItemCount() = devices.size

    fun updateDevices(newDevices: List<Device>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    fun addDevice(device: Device) {
        if (!devices.any { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }
}
