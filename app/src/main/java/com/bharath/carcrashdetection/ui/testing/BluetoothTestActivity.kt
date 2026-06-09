package com.bharath.carcrashdetection.ui.testing

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bharath.carcrashdetection.R
import com.bharath.carcrashdetection.databinding.ActivityBluetoothTestBinding
import com.bharath.carcrashdetection.util.Esp32BluetoothService
import com.bharath.carcrashdetection.util.PermissionManager

class BluetoothTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBluetoothTestBinding
    private lateinit var bluetoothService: Esp32BluetoothService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        initializeBluetooth()
    }
    
    private fun setupViews() {
        binding.btnTestDiscovery.setOnClickListener {
            testDeviceDiscovery()
        }
        
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }
        
        binding.btnTestData.setOnClickListener {
            testDataReception()
        }
        
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
        }
    }
    
    private fun initializeBluetooth() {
        bluetoothService = Esp32BluetoothService(this)
        
        // Observe connection state
        bluetoothService.connectionState.collect { state ->
            runOnUiThread {
                updateConnectionStatus(state)
            }
        }
        
        // Observe discovered devices
        bluetoothService.discoveredDevices.collect { devices ->
            runOnUiThread {
                updateDeviceList(devices)
            }
        }
        
        // Observe sensor data
        bluetoothService.sensorData.collect { data ->
            runOnUiThread {
                updateSensorData(data)
            }
        }
    }
    
    private fun testDeviceDiscovery() {
        if (!PermissionManager.hasRequiredPermissions(this)) {
            PermissionManager.requestRequiredPermissions(this)
            return
        }
        
        if (!bluetoothService.isBluetoothEnabled()) {
            showToast("Bluetooth is not enabled")
            return
        }
        
        logMessage("Starting device discovery...")
        bluetoothService.startDiscovery()
        
        // Stop discovery after 10 seconds
        binding.btnTestDiscovery.postDelayed({
            bluetoothService.stopDiscovery()
            logMessage("Device discovery stopped")
        }, 10000)
    }
    
    private fun testConnection() {
        val devices = bluetoothService.discoveredDevices.value
        if (devices.isEmpty()) {
            showToast("No devices discovered. Run discovery first.")
            return
        }
        
        // Try to connect to the first ESP32 device found
        val esp32Device = devices.find { device ->
            device.name?.contains("ESP32") == true || 
            device.name?.contains("CarCrash") == true
        }
        
        if (esp32Device != null) {
            logMessage("Attempting to connect to: ${esp32Device.name}")
            bluetoothService.connectBLE(esp32Device)
        } else {
            showToast("No ESP32 device found. Make sure ESP32 is advertising as 'ESP32_CarCrash'")
        }
    }
    
    private fun testDataReception() {
        val isConnected = bluetoothService.connectionState.value in listOf(
            Esp32BluetoothService.ConnectionState.CONNECTED_BLE,
            Esp32BluetoothService.ConnectionState.CONNECTED_CLASSIC
        )
        
        if (isConnected) {
            logMessage("Testing data reception...")
            logMessage("Current sensor data: ${bluetoothService.getCurrentSensorData()}")
            
            // Send a test command
            bluetoothService.sendCommand("TEST:Hello from Android")
            logMessage("Sent test command to ESP32")
        } else {
            showToast("Not connected to ESP32. Connect first.")
        }
    }
    
    private fun updateConnectionStatus(state: Esp32BluetoothService.ConnectionState) {
        val statusText = when (state) {
            Esp32BluetoothService.ConnectionState.DISCONNECTED -> "Disconnected"
            Esp32BluetoothService.ConnectionState.CONNECTING -> "Connecting..."
            Esp32BluetoothService.ConnectionState.CONNECTED_CLASSIC -> "Connected (Classic)"
            Esp32BluetoothService.ConnectionState.CONNECTED_BLE -> "Connected (BLE)"
            Esp32BluetoothService.ConnectionState.ERROR -> "Error"
        }
        
        binding.tvConnectionStatus.text = "Status: $statusText"
        logMessage("Connection state changed: $statusText")
    }
    
    private fun updateDeviceList(devices: List<android.bluetooth.BluetoothDevice>) {
        val deviceText = if (devices.isEmpty()) {
            "No devices found"
        } else {
            devices.joinToString("\n") { device ->
                "${device.name ?: "Unknown"} (${device.address})"
            }
        }
        
        binding.tvDeviceList.text = deviceText
        logMessage("Discovered ${devices.size} device(s)")
    }
    
    private fun updateSensorData(data: Esp32BluetoothService.SensorData?) {
        if (data != null) {
            val sensorText = "Acc: (${String.format("%.2f", data.accelerometerX)}, " +
                           "${String.format("%.2f", data.accelerometerY)}, " +
                           "${String.format("%.2f", data.accelerometerZ)}) " +
                           "Impact: ${String.format("%.2f", data.impactForce)}g"
            
            binding.tvSensorData.text = sensorText
            logMessage("Received sensor data: $sensorText")
        }
    }
    
    private fun logMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message\n"
        
        binding.tvLog.append(logEntry)
        
        // Auto-scroll to bottom
        val scrollAmount = binding.scrollView.getChildAt(0).height - binding.scrollView.height
        if (scrollAmount > 0) {
            binding.scrollView.smoothScrollTo(0, scrollAmount)
        }
        
        Log.d("BluetoothTest", message)
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        logMessage("Toast: $message")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.cleanup()
    }
}
