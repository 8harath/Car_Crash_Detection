package com.bharath.carcrashdetection.ui.publisher

import com.bharath.carcrashdetection.util.Esp32Manager

data class Device(
    val name: String,
    val address: String,
    val deviceType: Esp32Manager.ConnectionType,
    val signalStrength: Int = 0,
    val isConnected: Boolean = false
)
