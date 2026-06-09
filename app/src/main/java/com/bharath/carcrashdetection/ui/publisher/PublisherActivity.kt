package com.bharath.carcrashdetection.ui.publisher

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bharath.carcrashdetection.ui.base.BaseActivity
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.MqttConfig
import kotlinx.coroutines.launch
import android.view.View
import android.util.Log
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import com.bharath.carcrashdetection.databinding.ActivityPublisherBinding
import com.bharath.carcrashdetection.databinding.*
import com.google.android.material.snackbar.Snackbar

class PublisherActivity : BaseActivity<ActivityPublisherBinding>() {
    
    companion object {
        private const val TAG = "PublisherActivity"
    }
    
    private val viewModel: PublisherViewModel by viewModels()
    private lateinit var messageReceiver: BroadcastReceiver
    private lateinit var connectionStatusReceiver: BroadcastReceiver
    
    override fun getViewBinding(): ActivityPublisherBinding = ActivityPublisherBinding.inflate(layoutInflater)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Initialize MqttConfig
            MqttConfig.init(this)
            
            // Initialize MQTT service for publisher
            Log.i(TAG, "Initializing MQTT service for publisher")
            val serviceIntent = Intent(this, MqttService::class.java).apply {
                action = MqttService.ACTION_ENABLE
                putExtra("role", "PUBLISHER")
            }
            startService(serviceIntent)
            
            // Setup message receiver for real-time status updates
            setupMessageReceiver()
            
            // Setup connection status receiver
            setupConnectionStatusReceiver()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }
    
    override fun setupViews() {
        try {
            setupToolbar()
            setupConnectionStatus()
            setupBrokerSettings()
            setupEmergencyButton()
            setupExperimentalFeatures()
            
            Log.i(TAG, "Views setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up views: ${e.message}", e)
            showToast("Error initializing app: ${e.message}")
        }
    }
    
    override fun setupObservers() {
        try {
            lifecycleScope.launch {
                viewModel.connectionState.collect { state ->
                    updateConnectionStatus(state)
                }
            }
            
            lifecycleScope.launch {
                viewModel.brokerIp.collect { ip ->
                    binding.etBrokerIp.setText(ip)
                }
            }
            
            lifecycleScope.launch {
                viewModel.brokerPort.collect { port ->
                    binding.etBrokerPort.setText(port.toString())
                }
            }
            
            lifecycleScope.launch {
                viewModel.customMessage.collect { message ->
                    binding.etCustomMessage.setText(message)
                }
            }
            
            lifecycleScope.launch {
                viewModel.messageStatus.collect { status ->
                    if (status.isNotEmpty()) {
                        binding.tvMessageStatus.text = status
                        binding.cardMessageStatus.visibility = View.VISIBLE
                    }
                }
            }
            
            lifecycleScope.launch {
                viewModel.showMessageStatus.collect { show ->
                    binding.cardMessageStatus.visibility = if (show) View.VISIBLE else View.GONE
                }
            }
            
            lifecycleScope.launch {
                viewModel.showExperimentalFeatures.collect { show ->
                    binding.llExperimentalFeatures.visibility = if (show) View.VISIBLE else View.GONE
                    binding.btnToggleExperimental.text = if (show) "Hide Advanced Features" else "Show Advanced Features"
                }
            }
            
            lifecycleScope.launch {
                viewModel.isConnecting.collect { connecting ->
                    binding.btnTestConnection.isEnabled = !connecting
                    binding.btnTestConnection.text = if (connecting) "Testing..." else "Test Connection"
                }
            }
            
            lifecycleScope.launch {
                viewModel.isSending.collect { sending ->
                    binding.btnSendEmergency.isEnabled = !sending
                    binding.btnSendEmergency.text = if (sending) "Sending..." else "Send Emergency Alert"
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up observers: ${e.message}", e)
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupConnectionStatus() {
        binding.btnTestConnection.setOnClickListener {
            viewModel.testConnection()
        }
    }
    
    private fun setupBrokerSettings() {
        binding.etBrokerIp.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateBrokerIp(binding.etBrokerIp.text.toString())
            }
        }
        
        binding.etBrokerPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                try {
                    val port = binding.etBrokerPort.text.toString().toInt()
                    viewModel.updateBrokerPort(port)
                } catch (e: NumberFormatException) {
                    binding.etBrokerPort.setText("1883")
                    viewModel.updateBrokerPort(1883)
                }
            }
        }
        
        binding.btnSaveSettings.setOnClickListener {
            viewModel.saveSettings()
            Snackbar.make(binding.root, "Settings saved successfully", Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun setupEmergencyButton() {
        binding.etCustomMessage.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateCustomMessage(binding.etCustomMessage.text.toString())
            }
        }
        
        binding.btnSendEmergency.setOnClickListener {
            // First update the message in ViewModel
            viewModel.updateCustomMessage(binding.etCustomMessage.text.toString())
            
            // Then send the emergency alert
            viewModel.sendEmergencyAlert()
            
            // Send the actual MQTT message via the service
            sendEmergencyAlertViaMqtt()
        }
    }
    
    private fun sendEmergencyAlertViaMqtt() {
        try {
            val customMessage = binding.etCustomMessage.text.toString().trim()
            val emergencyMessage = if (customMessage.isNotEmpty()) {
                """
                {
                    "type": "emergency_alert",
                    "timestamp": ${System.currentTimeMillis()},
                    "message": "$customMessage",
                    "location": "auto-detected",
                    "device_id": "android_device"
                }
                """.trimIndent()
            } else {
                """
                {
                    "type": "emergency_alert",
                    "timestamp": ${System.currentTimeMillis()},
                    "message": "Emergency assistance needed",
                    "location": "auto-detected",
                    "device_id": "android_device"
                }
                """.trimIndent()
            }
            
            val topic = "emergency/alerts/alert"
            Log.i(TAG, "📤 Sending emergency alert via MQTT service: $topic")
            
            // Send via MQTT service using Intent
            val serviceIntent = Intent(this, MqttService::class.java).apply {
                action = MqttService.ACTION_PUBLISH
                putExtra(MqttService.EXTRA_TOPIC, topic)
                putExtra(MqttService.EXTRA_PAYLOAD, emergencyMessage)
                putExtra(MqttService.EXTRA_QOS, 1)
                putExtra(MqttService.EXTRA_RETAINED, false)
            }
            startService(serviceIntent)
            
            Log.i(TAG, "📤 Emergency alert intent sent to MQTT service")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending emergency alert via MQTT: ${e.message}", e)
        }
    }
    
    private fun setupExperimentalFeatures() {
        binding.btnToggleExperimental.setOnClickListener {
            viewModel.toggleExperimentalFeatures()
        }
        
        // Setup experimental feature buttons if they exist
        binding.btnConnectEsp32?.setOnClickListener {
            Snackbar.make(binding.root, "ESP32 connection feature coming soon", Snackbar.LENGTH_SHORT).show()
        }
        
        binding.btnTestBluetooth?.setOnClickListener {
            Snackbar.make(binding.root, "Bluetooth testing feature coming soon", Snackbar.LENGTH_SHORT).show()
        }
        
        binding.btnMedicalProfile?.setOnClickListener {
            Snackbar.make(binding.root, "Medical profile feature coming soon", Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun updateConnectionStatus(state: MqttService.ConnectionState) {
        val (color, text) = when (state) {
            MqttService.ConnectionState.CONNECTED -> {
                android.graphics.Color.GREEN to "Connected"
            }
            MqttService.ConnectionState.CONNECTING -> {
                android.graphics.Color.YELLOW to "Connecting..."
            }
            MqttService.ConnectionState.DISCONNECTED -> {
                android.graphics.Color.RED to "Disconnected"
            }
        }
        
        binding.connectionIndicator.setBackgroundColor(color)
        binding.tvConnectionStatus.text = text
    }
    
    private fun setupMessageReceiver() {
        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.bharath.carcrashdetection.MESSAGE_PUBLISHED" -> {
                        val topic = intent.getStringExtra("topic") ?: ""
                        val success = intent.getBooleanExtra("success", false)
                        val payload = intent.getStringExtra("payload") ?: ""
                        val error = intent.getStringExtra("error") ?: ""
                        
                        if (success) {
                            Log.i(TAG, "✅ Message published successfully to $topic")
                            Snackbar.make(binding.root, "✅ Message sent successfully!", Snackbar.LENGTH_SHORT).show()
                        } else {
                            Log.e(TAG, "❌ Message publish failed: $error")
                            Snackbar.make(binding.root, "❌ Failed to send message: $error", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        
        // Register the receiver
        val filter = IntentFilter().apply {
            addAction("com.bharath.carcrashdetection.MESSAGE_PUBLISHED")
        }
        registerReceiver(messageReceiver, filter)
        
                 Log.i(TAG, "📡 Message receiver registered for publish status updates")
     }
     
     private fun setupConnectionStatusReceiver() {
         connectionStatusReceiver = object : BroadcastReceiver() {
             override fun onReceive(context: Context?, intent: Intent?) {
                 when (intent?.action) {
                     "com.bharath.carcrashdetection.CONNECTION_STATUS" -> {
                         val status = intent.getStringExtra("status") ?: "DISCONNECTED"
                         val error = intent.getStringExtra("error")
                         
                         Log.i(TAG, "📡 Connection status update: $status ${error ?: ""}")
                         
                         when (status) {
                             "CONNECTED" -> {
                                 binding.connectionIndicator.setBackgroundColor(android.graphics.Color.GREEN)
                                 binding.tvConnectionStatus.text = "Connected"
                                 Snackbar.make(binding.root, "✅ Connected to MQTT broker", Snackbar.LENGTH_SHORT).show()
                             }
                             "DISCONNECTED" -> {
                                 binding.connectionIndicator.setBackgroundColor(android.graphics.Color.RED)
                                 binding.tvConnectionStatus.text = "Disconnected"
                                 if (error != null) {
                                     Snackbar.make(binding.root, "❌ Connection failed: $error", Snackbar.LENGTH_LONG).show()
                                 }
                             }
                             "CONNECTING" -> {
                                 binding.connectionIndicator.setBackgroundColor(android.graphics.Color.YELLOW)
                                 binding.tvConnectionStatus.text = "Connecting..."
                             }
                         }
                     }
                 }
             }
         }
         
         // Register the receiver
         val filter = IntentFilter().apply {
             addAction("com.bharath.carcrashdetection.CONNECTION_STATUS")
         }
         registerReceiver(connectionStatusReceiver, filter)
         
         Log.i(TAG, "📡 Connection status receiver registered")
     }
    
         override fun onDestroy() {
         super.onDestroy()
         try {
             // Unregister the message receiver
             if (::messageReceiver.isInitialized) {
                 unregisterReceiver(messageReceiver)
                 Log.i(TAG, "📡 Message receiver unregistered")
             }
             
             // Unregister the connection status receiver
             if (::connectionStatusReceiver.isInitialized) {
                 unregisterReceiver(connectionStatusReceiver)
                 Log.i(TAG, "📡 Connection status receiver unregistered")
             }
         } catch (e: Exception) {
             Log.e(TAG, "Error unregistering receivers: ${e.message}", e)
         }
     }
} 