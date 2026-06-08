package com.bharath.carcrashdetection.util

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * Unified manager for ESP32 communication
 * Coordinates Bluetooth and WiFi Direct services for optimal connectivity
 */
class Esp32Manager(context: Context) {
    
    companion object {
        private const val TAG = "Esp32Manager"
    }
    
    // Communication services
    private val bluetoothService = Esp32BluetoothService(context)
    private val wifiDirectService = Esp32WifiDirectService(context)
    
    // Connection states
    enum class ConnectionType {
        NONE,
        BLUETOOTH_CLASSIC,
        BLUETOOTH_BLE,
        WIFI_DIRECT
    }
    
    enum class ConnectionState {
        DISCONNECTED,
        DISCOVERING,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    // State flows
    private val _connectionType = MutableStateFlow(ConnectionType.NONE)
    val connectionType: StateFlow<ConnectionType> = _connectionType
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _sensorData = MutableStateFlow<Esp32BluetoothService.SensorData?>(null)
    val sensorData: StateFlow<Esp32BluetoothService.SensorData?> = _sensorData
    
    private val _discoveredDevices = MutableStateFlow<List<Any>>(emptyList())
    val discoveredDevices: StateFlow<List<Any>> = _discoveredDevices
    
    // Combined state from both services
    private val combinedSensorData = combine(
        bluetoothService.sensorData,
        wifiDirectService.sensorData
    ) { bluetoothData, wifiData ->
        bluetoothData ?: wifiData
    }
    
    init {
        // Observe connection states
        observeConnectionStates()
    }
    
    /**
     * Observe connection states from both services
     */
    private fun observeConnectionStates() {
        // This would be implemented with proper coroutine scope in a real implementation
        // For now, we'll handle it through direct method calls
    }
    
    /**
     * Check if any communication method is available
     */
    fun isCommunicationAvailable(): Boolean {
        return bluetoothService.isBluetoothEnabled() || wifiDirectService.isWifiP2PEnabled()
    }
    
    /**
     * Get available communication methods
     */
    fun getAvailableMethods(): List<String> {
        val methods = mutableListOf<String>()
        
        if (bluetoothService.isBluetoothEnabled()) {
            methods.add("Bluetooth Classic")
            methods.add("Bluetooth BLE")
        }
        
        if (wifiDirectService.isWifiP2PEnabled()) {
            methods.add("WiFi Direct")
        }
        
        return methods
    }
    
    /**
     * Start device discovery on all available methods
     */
    fun startDiscovery() {
        _connectionState.value = ConnectionState.DISCOVERING
        
        // Start Bluetooth discovery
        if (bluetoothService.isBluetoothEnabled()) {
            bluetoothService.startDiscovery()
        }
        
        // Start WiFi Direct discovery
        if (wifiDirectService.isWifiP2PEnabled()) {
            wifiDirectService.startDiscovery()
        }
        
        Log.i(TAG, "Started device discovery on all available methods")
    }
    
    /**
     * Stop device discovery
     */
    fun stopDiscovery() {
        bluetoothService.stopDiscovery()
        wifiDirectService.stopDiscovery()
        
        if (_connectionState.value == ConnectionState.DISCOVERING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        
        Log.i(TAG, "Stopped device discovery")
    }
    
    /**
     * Connect to ESP32 device using best available method
     */
    fun connectToDevice(device: Any) {
        _connectionState.value = ConnectionState.CONNECTING
        
        when (device) {
            is BluetoothDevice -> {
                // Try BLE first, then Classic
                try {
                    bluetoothService.connectBLE(device)
                    _connectionType.value = ConnectionType.BLUETOOTH_BLE
                } catch (e: Exception) {
                    Log.w(TAG, "BLE connection failed, trying Classic: ${e.message}")
                    try {
                        bluetoothService.connectClassic(device)
                        _connectionType.value = ConnectionType.BLUETOOTH_CLASSIC
                    } catch (e2: Exception) {
                        Log.e(TAG, "Both Bluetooth methods failed: ${e2.message}")
                        _connectionState.value = ConnectionState.ERROR
                    }
                }
            }
            is WifiP2pDevice -> {
                wifiDirectService.connectToDevice(device)
                _connectionType.value = ConnectionType.WIFI_DIRECT
            }
            else -> {
                Log.e(TAG, "Unknown device type: ${device::class.java.simpleName}")
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }
    
    /**
     * Send command to ESP32
     */
    fun sendCommand(command: String) {
        when (_connectionType.value) {
            ConnectionType.BLUETOOTH_CLASSIC, ConnectionType.BLUETOOTH_BLE -> {
                bluetoothService.sendCommand(command)
            }
            ConnectionType.WIFI_DIRECT -> {
                wifiDirectService.sendCommand(command)
            }
            else -> {
                Log.w(TAG, "Not connected to ESP32")
            }
        }
    }
    
    /**
     * Disconnect from ESP32
     */
    fun disconnect() {
        bluetoothService.disconnect()
        wifiDirectService.disconnect()
        
        _connectionType.value = ConnectionType.NONE
        _connectionState.value = ConnectionState.DISCONNECTED
        
        Log.i(TAG, "Disconnected from ESP32")
    }
    
    /**
     * Check if impact detected (crash detection)
     */
    fun isImpactDetected(): Boolean {
        return bluetoothService.isImpactDetected() || wifiDirectService.isImpactDetected()
    }
    
    /**
     * Get current sensor data
     */
    fun getCurrentSensorData(): Esp32BluetoothService.SensorData? {
        return bluetoothService.getCurrentSensorData() ?: wifiDirectService.getCurrentSensorData()
    }
    
    /**
     * Get connection status string
     */
    fun getConnectionStatus(): String {
        return when (_connectionType.value) {
            ConnectionType.BLUETOOTH_CLASSIC -> "Connected via Bluetooth Classic"
            ConnectionType.BLUETOOTH_BLE -> "Connected via Bluetooth BLE"
            ConnectionType.WIFI_DIRECT -> "Connected via WiFi Direct"
            ConnectionType.NONE -> "Not connected"
        }
    }
    
    /**
     * Get sensor data status
     */
    fun getSensorDataStatus(): String {
        val data = getCurrentSensorData()
        return if (data != null) {
            "Acc: (${String.format("%.1f", data.accelerometerX)}, ${String.format("%.1f", data.accelerometerY)}, ${String.format("%.1f", data.accelerometerZ)}) " +
            "Impact: ${String.format("%.1f", data.impactForce)}g"
        } else {
            "No sensor data"
        }
    }
    
    /**
     * Check if GPS data is available
     */
    fun hasGpsData(): Boolean {
        val data = getCurrentSensorData()
        return data?.latitude != null && data?.longitude != null
    }
    
    /**
     * Get GPS coordinates
     */
    fun getGpsCoordinates(): Pair<Double, Double>? {
        val data = getCurrentSensorData()
        return if (data?.latitude != null && data?.longitude != null) {
            Pair(data.latitude, data.longitude)
        } else {
            null
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        disconnect()
        wifiDirectService.cleanup()
        Log.i(TAG, "ESP32 Manager cleaned up")
    }
    
    /**
     * Get Bluetooth service for direct access if needed
     */
    fun getBluetoothService(): Esp32BluetoothService {
        return bluetoothService
    }
    
    /**
     * Get WiFi Direct service for direct access if needed
     */
    fun getWifiDirectService(): Esp32WifiDirectService {
        return wifiDirectService
    }
}
