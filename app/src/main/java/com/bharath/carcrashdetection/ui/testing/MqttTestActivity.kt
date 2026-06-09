package com.bharath.carcrashdetection.ui.testing

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bharath.carcrashdetection.databinding.ActivityMqttTestBinding
import com.bharath.carcrashdetection.databinding.*
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.MqttTopics
import com.bharath.carcrashdetection.util.NetworkHelper
import com.bharath.carcrashdetection.util.MqttConfig
import kotlinx.coroutines.launch
import android.content.Intent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.bharath.carcrashdetection.util.EmergencyAlertMessage
import java.util.*

class MqttTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMqttTestBinding
    private val TAG = "MqttTestActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMqttTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkNetworkStatus()
    }
    
    private fun setupUI() {
        // Test Publisher Mode
        binding.btnTestPublisher.setOnClickListener {
            testPublisherMode()
        }
        
        // Test Subscriber Mode
        binding.btnTestSubscriber.setOnClickListener {
            testSubscriberMode()
        }
        
        // Test Connection
        binding.btnTestConnection.setOnClickListener {
            testMqttConnection()
        }
        
        // Send Test Alert
        binding.btnSendTestAlert.setOnClickListener {
            sendTestEmergencyAlert()
        }
        
        // Check Network
        binding.btnCheckNetwork.setOnClickListener {
            checkNetworkStatus()
        }
        
        // Clear Logs
        binding.btnClearLogs.setOnClickListener {
            binding.tvLogs.text = ""
        }
    }
    
    private fun testPublisherMode() {
        logMessage("Testing Publisher Mode...")
        
        try {
            val intent = Intent(this, MqttService::class.java).apply {
                action = MqttService.ACTION_ENABLE
                putExtra("role", "PUBLISHER")
            }
            startService(intent)
            
            logMessage("✅ Publisher service started successfully")
            logMessage("Publisher should now be able to send emergency alerts")
            
        } catch (e: Exception) {
            logMessage("❌ Error starting publisher service: ${e.message}")
            Log.e(TAG, "Error starting publisher service", e)
        }
    }
    
    private fun testSubscriberMode() {
        logMessage("Testing Subscriber Mode...")
        
        try {
            val intent = Intent(this, MqttService::class.java).apply {
                action = MqttService.ACTION_ENABLE
                putExtra("role", "SUBSCRIBER")
            }
            startService(intent)
            
            logMessage("✅ Subscriber service started successfully")
            logMessage("Subscriber should now be listening for emergency alerts")
            
        } catch (e: Exception) {
            logMessage("❌ Error starting subscriber service: ${e.message}")
            Log.e(TAG, "Error starting subscriber service", e)
        }
    }
    
    private fun testMqttConnection() {
        logMessage("Testing MQTT Connection...")
        
        lifecycleScope.launch {
            try {
                val brokerUrl = MqttConfig.getBrokerUrl()
                logMessage("Broker URL: $brokerUrl")
                
                // Extract host and port from URL
                val urlParts = brokerUrl.replace("tcp://", "").split(":")
                val host = urlParts[0]
                val port = urlParts[1].toInt()
                
                logMessage("Testing connectivity to $host:$port...")
                
                val result = NetworkHelper.testBrokerConnectivity(host, port)
                
                if (result.isSuccess) {
                    logMessage("✅ MQTT broker is accessible!")
                    logMessage("You can now test publishing and subscribing")
                } else {
                    logMessage("❌ MQTT broker is not accessible")
                    logMessage("Please check:")
                    logMessage("1. Mosquitto is running on your laptop")
                    logMessage("2. Firewall allows port 1883")
                    logMessage("3. IP address is correct")
                    logMessage("4. Try using localhost instead of IP address")
                }
                
            } catch (e: Exception) {
                logMessage("❌ Error testing MQTT connection: ${e.message}")
                Log.e(TAG, "Error testing MQTT connection", e)
            }
        }
    }
    
    private fun sendTestEmergencyAlert() {
        logMessage("Sending Test Emergency Alert...")
        
        try {
            // Create a test emergency alert
            val testAlert = EmergencyAlertMessage(
                incidentId = "test_incident_${System.currentTimeMillis()}",
                victimId = "test_user_001",
                victimName = "Test User",
                location = EmergencyAlertMessage.Location(
                    latitude = 40.7128,
                    longitude = -74.0060
                ),
                timestamp = System.currentTimeMillis(),
                severity = "HIGH",
                medicalInfo = EmergencyAlertMessage.MedicalInfo(
                    bloodType = "O+",
                    allergies = listOf("None"),
                    medications = listOf("None"),
                    conditions = listOf("None")
                )
            )
            
            val json = Json.encodeToString(testAlert)
            val topic = MqttTopics.alertIncident(testAlert.incidentId)
            
            logMessage("Test Alert Details:")
            logMessage("Topic: $topic")
            logMessage("Victim: ${testAlert.victimName}")
            logMessage("Severity: ${testAlert.severity}")
            logMessage("Location: ${testAlert.location.latitude}, ${testAlert.location.longitude}")
            
            // Send via MQTT service
            val intent = Intent(this, MqttService::class.java).apply {
                action = MqttService.ACTION_PUBLISH
                putExtra(MqttService.EXTRA_TOPIC, topic)
                putExtra(MqttService.EXTRA_PAYLOAD, json)
                putExtra(MqttService.EXTRA_QOS, 1)
                putExtra(MqttService.EXTRA_RETAINED, false)
            }
            startService(intent)
            
            logMessage("✅ Test emergency alert sent!")
            logMessage("Check subscriber devices for the alert")
            
        } catch (e: Exception) {
            logMessage("❌ Error sending test alert: ${e.message}")
            Log.e(TAG, "Error sending test alert", e)
        }
    }
    
    private fun checkNetworkStatus() {
        logMessage("Checking Network Status...")
        
        try {
            val networkInfo = NetworkHelper.getNetworkInfo(this)
            
            logMessage("Network Type: ${networkInfo["type"]}")
            logMessage("Network Quality: ${networkInfo["quality"]}")
            logMessage("Local IP: ${networkInfo["local_ip"]}")
            logMessage("Recommended Broker: ${networkInfo["recommended_broker"]}")
            
            // Check if on WiFi
            if (NetworkHelper.isOnWifiNetwork(this)) {
                logMessage("✅ Device is on WiFi network")
                logMessage("This is ideal for local MQTT communication")
            } else {
                logMessage("⚠️ Device is not on WiFi network")
                logMessage("Local MQTT may not work properly")
            }
            
        } catch (e: Exception) {
            logMessage("❌ Error checking network status: ${e.message}")
            Log.e(TAG, "Error checking network status", e)
        }
    }
    
    private fun logMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message\n"
        
        binding.tvLogs.append(logEntry)
        
        // Auto-scroll to bottom
        binding.scrollView.post {
            binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
        
        Log.d(TAG, message)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up if needed
    }
}
