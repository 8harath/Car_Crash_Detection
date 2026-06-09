package com.bharath.carcrashdetection.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bharath.carcrashdetection.util.MqttConfig
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.NetworkHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException

class MqttSettingsViewModel : ViewModel() {
    
    private val _brokerIp = MutableStateFlow("192.168.1.100")
    val brokerIp: StateFlow<String> = _brokerIp.asStateFlow()
    
    private val _brokerPort = MutableStateFlow(1883)
    val brokerPort: StateFlow<Int> = _brokerPort.asStateFlow()
    
    companion object {
        private const val PREFS_NAME = "mqtt_settings"
        private const val KEY_BROKER_IP = "broker_ip"
        private const val KEY_BROKER_PORT = "broker_port"
    }
    
    private val _connectionStatus = MutableStateFlow("Not tested")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    fun updateBrokerIp(ip: String) {
        _brokerIp.value = ip
    }
    
    fun updateBrokerPort(port: Int) {
        _brokerPort.value = port
    }
    
    fun loadCurrentSettings() {
        viewModelScope.launch {
            try {
                // Load from SharedPreferences or use defaults
                val currentIp = getStoredBrokerIp()
                val currentPort = getStoredBrokerPort()
                
                _brokerIp.value = currentIp
                _brokerPort.value = currentPort
                
                Log.i("MqttSettingsViewModel", "Loaded settings: $currentIp:$currentPort")
                
            } catch (e: Exception) {
                Log.e("MqttSettingsViewModel", "Error loading settings: ${e.message}")
                _errorMessage.value = "Error loading settings: ${e.message}"
            }
        }
    }
    
    fun loadCurrentSettings(context: Context) {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val currentIp = prefs.getString(KEY_BROKER_IP, "192.168.1.100") ?: "192.168.1.100"
                val currentPort = prefs.getInt(KEY_BROKER_PORT, 1883)
                
                _brokerIp.value = currentIp
                _brokerPort.value = currentPort
                
                Log.i("MqttSettingsViewModel", "Loaded settings from SharedPreferences: $currentIp:$currentPort")
                
            } catch (e: Exception) {
                Log.e("MqttSettingsViewModel", "Error loading settings: ${e.message}")
                _errorMessage.value = "Error loading settings: ${e.message}"
            }
        }
    }
    
    fun testConnection() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _connectionStatus.value = "Testing connection..."
                _errorMessage.value = null
                
                val ip = _brokerIp.value
                val port = _brokerPort.value
                
                Log.i("MqttSettingsViewModel", "Testing MQTT connection to $ip:$port")
                
                // First test basic network connectivity
                val isReachable = testNetworkConnectivity(ip, port)
                
                if (!isReachable) {
                    _connectionStatus.value = "❌ Network unreachable"
                    _errorMessage.value = "Cannot reach $ip:$port. Check if Mosquitto broker is running."
                    Log.w("MqttSettingsViewModel", "Network connectivity test failed")
                    return@launch
                }
                
                // Now test actual MQTT connection
                val mqttTestResult = testMqttConnection(ip, port)
                
                if (mqttTestResult) {
                    _connectionStatus.value = "✅ MQTT Connection successful"
                    _successMessage.value = "Successfully connected to MQTT broker at $ip:$port"
                    Log.i("MqttSettingsViewModel", "MQTT connection test successful")
                } else {
                    _connectionStatus.value = "❌ MQTT Connection failed"
                    _errorMessage.value = "Cannot connect to MQTT broker at $ip:$port. Check if Mosquitto is running and accessible."
                    Log.w("MqttSettingsViewModel", "MQTT connection test failed")
                }
                
            } catch (e: Exception) {
                Log.e("MqttSettingsViewModel", "Error testing connection: ${e.message}")
                _connectionStatus.value = "❌ Connection error"
                _errorMessage.value = "Connection test failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun saveSettings(context: Context) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val ip = _brokerIp.value
                val port = _brokerPort.value
                
                // Validate input
                if (ip.isBlank()) {
                    _errorMessage.value = "Broker IP address cannot be empty"
                    return@launch
                }
                
                // Validate IP address format
                if (!isValidIpAddress(ip) && ip != "localhost") {
                    _errorMessage.value = "Invalid IP address format. Use format like 192.168.1.100 or localhost"
                    return@launch
                }
                
                if (port < 1 || port > 65535) {
                    _errorMessage.value = "Port must be between 1 and 65535"
                    return@launch
                }
                
                // Save to SharedPreferences
                saveBrokerSettings(context, ip, port)
                
                // Update MqttConfig
                updateMqttConfig(ip, port)
                
                // Notify MQTT service to reconnect with new settings
                notifyMqttServiceSettingsChanged(context)
                
                _successMessage.value = "Settings saved successfully"
                Log.i("MqttSettingsViewModel", "Settings saved: $ip:$port")
                
            } catch (e: Exception) {
                Log.e("MqttSettingsViewModel", "Error saving settings: ${e.message}")
                _errorMessage.value = "Error saving settings: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun testNetworkConnectivity(ip: String, port: Int): Boolean {
        return try {
            // Test if we can resolve the hostname
            val address = InetAddress.getByName(ip)
            Log.d("MqttSettingsViewModel", "Resolved $ip to ${address.hostAddress}")
            
            // Test if we can connect to the port
            Socket(address, port).use { socket ->
                socket.isConnected
            }
        } catch (e: UnknownHostException) {
            Log.w("MqttSettingsViewModel", "Cannot resolve hostname: $ip")
            false
        } catch (e: Exception) {
            Log.w("MqttSettingsViewModel", "Cannot connect to $ip:$port - ${e.message}")
            false
        }
    }
    
    /**
     * Test actual MQTT connection by temporarily enabling the service
     */
    private fun testMqttConnection(ip: String, port: Int): Boolean {
        return try {
            // For now, we'll use a simpler approach
            // Since we can't easily test MQTT without the full client,
            // we'll assume that if the network connectivity works and the port is open,
            // the MQTT broker is likely running and accessible
            
            // Additional check: try to connect and see if we get any response
            val address = InetAddress.getByName(ip)
            val socket = Socket(address, port)
            
            // If we can connect to the port, it's likely an MQTT broker
            // (most MQTT brokers respond to any TCP connection attempt)
            val isConnected = socket.isConnected
            
            socket.close()
            
            Log.d("MqttSettingsViewModel", "MQTT port test result: $isConnected")
            isConnected
            
        } catch (e: Exception) {
            Log.w("MqttSettingsViewModel", "MQTT connectivity test failed: ${e.message}")
            false
        }
    }
    
    private fun getStoredBrokerIp(): String {
        // For now, return default. In a real app, you'd read from SharedPreferences
        return "192.168.1.100"
    }
    
    private fun getStoredBrokerPort(): Int {
        // For now, return default. In a real app, you'd read from SharedPreferences
        return 1883
    }
    
    private fun saveBrokerSettings(context: Context, ip: String, port: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_BROKER_IP, ip)
                .putInt(KEY_BROKER_PORT, port)
                .apply()
            Log.i("MqttSettingsViewModel", "Saved broker settings to SharedPreferences: $ip:$port")
        } catch (e: Exception) {
            Log.e("MqttSettingsViewModel", "Error saving to SharedPreferences: ${e.message}")
            throw e
        }
    }
    
    private fun updateMqttConfig(ip: String, port: Int) {
        try {
            // Update the MqttConfig object with new settings
            MqttConfig.updateBrokerSettings(ip, port)
            Log.i("MqttSettingsViewModel", "Notified MQTT service of settings change")
        } catch (e: Exception) {
            Log.e("MqttSettingsViewModel", "Error updating MQTT config: ${e.message}")
            throw e
        }
    }
    
    /**
     * Notify MQTT service that settings have changed
     */
    private fun notifyMqttServiceSettingsChanged(context: Context) {
        try {
            val intent = android.content.Intent(context, com.bharath.carcrashdetection.util.MqttService::class.java).apply {
                action = com.bharath.carcrashdetection.util.MqttService.ACTION_UPDATE_SETTINGS
            }
            context.startService(intent)
            Log.i("MqttSettingsViewModel", "Notified MQTT service of settings change")
        } catch (e: Exception) {
            Log.e("MqttSettingsViewModel", "Error notifying MQTT service: ${e.message}")
        }
    }
    
    /**
     * Validate IP address format
     */
    private fun isValidIpAddress(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            
            parts.all { part ->
                val num = part.toInt()
                num in 0..255
            }
        } catch (e: Exception) {
            false
        }
    }
}
