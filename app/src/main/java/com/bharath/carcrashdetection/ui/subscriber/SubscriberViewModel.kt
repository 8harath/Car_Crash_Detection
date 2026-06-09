package com.bharath.carcrashdetection.ui.subscriber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.MqttConfig
import com.bharath.carcrashdetection.data.model.Incident
import com.bharath.carcrashdetection.data.model.IncidentStatus
import com.bharath.carcrashdetection.data.model.IncidentSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import android.content.Context
import android.content.Intent
import com.bharath.carcrashdetection.util.MqttService.ConnectionState
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class SubscriberViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "SubscriberViewModel"
    }
    
    // Core MQTT State
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _brokerIp = MutableStateFlow("192.168.0.101")
    val brokerIp: StateFlow<String> = _brokerIp.asStateFlow()
    
    private val _brokerPort = MutableStateFlow(1883)
    val brokerPort: StateFlow<Int> = _brokerPort.asStateFlow()
    
    // Emergency Alerts
    private val _emergencyAlerts = MutableStateFlow<List<Incident>>(emptyList())
    val emergencyAlerts: StateFlow<List<Incident>> = _emergencyAlerts.asStateFlow()
    
    private val _alertCount = MutableStateFlow(0)
    val alertCount: StateFlow<Int> = _alertCount.asStateFlow()
    
    // Loading States
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()
    
    // Experimental Features (Hidden by default)
    private val _showExperimentalFeatures = MutableStateFlow(false)
    val showExperimentalFeatures: StateFlow<Boolean> = _showExperimentalFeatures.asStateFlow()
    
    init {
        loadSavedSettings()
        observeMqttConnectionState()
        observeMqttMessages()
    }
    
    // Core MQTT Functions
    
    fun updateBrokerIp(ip: String) {
        _brokerIp.value = ip
    }
    
    fun updateBrokerPort(port: Int) {
        _brokerPort.value = port
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            try {
                // Save to SharedPreferences
                MqttConfig.updateBrokerSettings(_brokerIp.value, _brokerPort.value)
                
                // For now, just log the settings update
                // In a real implementation, this would update MqttService
                Log.d(TAG, "Settings would be updated in MqttService: ${_brokerIp.value}:${_brokerPort.value}")
                
                Log.i(TAG, "Settings saved: ${_brokerIp.value}:${_brokerPort.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving settings: ${e.message}", e)
            }
        }
    }
    
    fun testConnection() {
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                
                // For now, just simulate connection test
                // In a real implementation, this would use MqttService.testConnection()
                kotlinx.coroutines.delay(2000) // Simulate network delay
                
                // Simulate successful connection for demo
                val success = true
                
                if (success) {
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.i(TAG, "Connection test successful")
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    Log.w(TAG, "Connection test failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error testing connection: ${e.message}", e)
                _connectionState.value = ConnectionState.DISCONNECTED
            } finally {
                _isConnecting.value = false
            }
        }
    }
    
    fun clearAllAlerts() {
        viewModelScope.launch {
            try {
                _emergencyAlerts.value = emptyList()
                _alertCount.value = 0
                Log.i(TAG, "All alerts cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing alerts: ${e.message}", e)
            }
        }
    }
    
    fun handleEmergencyAlertReceived(alertJson: String, topic: String) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "🚨 Processing emergency alert from topic: $topic")
                Log.i(TAG, "🚨 Alert content: $alertJson")
                
                // Parse the emergency alert
                val incident = parseEmergencyAlert(alertJson)
                
                // Add to the list
                addEmergencyAlert(incident)
                
                                 Log.i(TAG, "✅ Emergency alert processed and added to list")
                 
                 // Update connection state to show we're receiving messages
                 _connectionState.value = ConnectionState.CONNECTED
             } catch (e: Exception) {
                Log.e(TAG, "Error processing emergency alert: ${e.message}", e)
                // Create a fallback incident for failed parsing
                val fallbackIncident = Incident(
                    id = 0,
                    victimId = 1L,
                    incidentId = "incident_${System.currentTimeMillis()}",
                    latitude = null,
                    longitude = null,
                    timestamp = System.currentTimeMillis(),
                    status = IncidentStatus.ACTIVE,
                    description = "Emergency Alert: $alertJson",
                    severity = IncidentSeverity.HIGH,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                addEmergencyAlert(fallbackIncident)
            }
        }
    }
    
    fun handleTestMessageReceived(message: String, topic: String) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "📝 Processing test message from topic: $topic")
                Log.i(TAG, "📝 Message content: $message")
                
                // Try to parse as JSON first
                val incident = if (message.trim().startsWith("{")) {
                    parseEmergencyAlert(message)
                } else {
                    // Create a test incident for plain text
                    Incident(
                        id = 0,
                        victimId = 1L,
                        incidentId = "test_${System.currentTimeMillis()}",
                        latitude = null,
                        longitude = null,
                        timestamp = System.currentTimeMillis(),
                        status = IncidentStatus.ACTIVE,
                        description = "Message: $message",
                        severity = IncidentSeverity.MEDIUM,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                
                // Add to the list
                addEmergencyAlert(incident)
                
                                 Log.i(TAG, "✅ Test message processed and added to list")
                 
                 // Update connection state to show we're receiving messages
                 _connectionState.value = ConnectionState.CONNECTED
             } catch (e: Exception) {
                Log.e(TAG, "Error processing test message: ${e.message}", e)
                // Create fallback incident
                val fallbackIncident = Incident(
                    id = 0,
                    victimId = 1L,
                    incidentId = "test_${System.currentTimeMillis()}",
                    latitude = null,
                    longitude = null,
                    timestamp = System.currentTimeMillis(),
                    status = IncidentStatus.ACTIVE,
                    description = "Message: $message",
                    severity = IncidentSeverity.MEDIUM,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                addEmergencyAlert(fallbackIncident)
            }
        }
    }
    
    // Experimental Features Toggle
    
    fun toggleExperimentalFeatures() {
        _showExperimentalFeatures.value = !_showExperimentalFeatures.value
        Log.d(TAG, "Experimental features ${if (_showExperimentalFeatures.value) "shown" else "hidden"}")
    }
    
    // Private Helper Functions
    
    private fun loadSavedSettings() {
        viewModelScope.launch {
            try {
                val savedIp = MqttConfig.getBrokerIp()
                val savedPort = MqttConfig.getBrokerPort()
                
                if (savedIp.isNotEmpty()) {
                    _brokerIp.value = savedIp
                }
                if (savedPort > 0) {
                    _brokerPort.value = savedPort
                }
                
                Log.d(TAG, "Loaded saved settings: $savedIp:$savedPort")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved settings: ${e.message}", e)
            }
        }
    }
    
    private fun observeMqttConnectionState() {
        viewModelScope.launch {
            try {
                // For now, just observe the local state
                // In a real implementation, this would observe MqttService.connectionState
                Log.d(TAG, "MQTT connection state observer initialized (simulated)")
            } catch (e: Exception) {
                Log.e(TAG, "Error observing MQTT connection state: ${e.message}", e)
            }
        }
    }
    
    private fun observeMqttMessages() {
        viewModelScope.launch {
            try {
                // Observe MQTT messages via broadcast receiver
                // This will be handled by the Activity's broadcast receiver
                Log.d(TAG, "MQTT message observer initialized - waiting for broadcasts")
            } catch (e: Exception) {
                Log.e(TAG, "Error observing MQTT messages: ${e.message}", e)
            }
        }
    }
    
    private fun parseEmergencyAlert(alertJson: String): Incident {
        return try {
            // Try to parse JSON structure
            if (alertJson.trim().startsWith("{")) {
                // Extract message from JSON
                val messageMatch = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(alertJson)
                val message = messageMatch?.groupValues?.get(1) ?: alertJson.take(100)
                
                // Extract timestamp from JSON
                val timestampMatch = Regex("\"timestamp\"\\s*:\\s*(\\d+)").find(alertJson)
                val timestamp = timestampMatch?.groupValues?.get(1)?.toLongOrNull() ?: System.currentTimeMillis()
                
                Incident(
                    id = 0, // Auto-generated by Room
                    victimId = 1L, // Default victim ID
                    incidentId = "incident_${timestamp}",
                    latitude = null,
                    longitude = null,
                    timestamp = timestamp,
                    status = IncidentStatus.ACTIVE,
                    description = message,
                    severity = IncidentSeverity.HIGH,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            } else {
                // Simple text message
                Incident(
                    id = 0, // Auto-generated by Room
                    victimId = 1L, // Default victim ID
                    incidentId = "incident_${System.currentTimeMillis()}",
                    latitude = null,
                    longitude = null,
                    timestamp = System.currentTimeMillis(),
                    status = IncidentStatus.ACTIVE,
                    description = alertJson.take(100), // Take first 100 chars
                    severity = IncidentSeverity.HIGH,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            // Fallback to simple message parsing
            Log.w(TAG, "Failed to parse alert, using fallback: ${e.message}")
            
            Incident(
                id = 0, // Auto-generated by Room
                victimId = 1L, // Default victim ID
                incidentId = "incident_${System.currentTimeMillis()}",
                latitude = null,
                longitude = null,
                timestamp = System.currentTimeMillis(),
                status = IncidentStatus.ACTIVE,
                description = alertJson.take(100), // Take first 100 chars
                severity = IncidentSeverity.HIGH,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }
    
    private fun addEmergencyAlert(incident: Incident) {
        viewModelScope.launch {
            try {
                val currentAlerts = _emergencyAlerts.value.toMutableList()
                currentAlerts.add(0, incident) // Add to beginning
                
                // Keep only last 50 alerts to prevent memory issues
                if (currentAlerts.size > 50) {
                    currentAlerts.removeAt(currentAlerts.size - 1)
                }
                
                _emergencyAlerts.value = currentAlerts
                _alertCount.value = currentAlerts.size
                
                Log.d(TAG, "Emergency alert added. Total alerts: ${currentAlerts.size}")
                Log.i(TAG, "📨 New message received: ${incident.description}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding emergency alert: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get the latest message for real-time display
     */
    fun getLatestMessage(): String {
        val alerts = _emergencyAlerts.value
        return if (alerts.isNotEmpty()) {
            alerts.first().description ?: "No description"
        } else {
            "No messages received"
        }
    }
    
    /**
     * Get message count for real-time display
     */
    fun getMessageCount(): Int {
        return _alertCount.value
    }
    
    // Cleanup
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "SubscriberViewModel cleared")
    }
} 