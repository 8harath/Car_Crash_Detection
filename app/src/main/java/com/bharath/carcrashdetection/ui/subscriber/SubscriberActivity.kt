package com.bharath.carcrashdetection.ui.subscriber

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bharath.carcrashdetection.ui.base.BaseActivity
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.MqttConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.view.View
import android.util.Log
import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.appcompat.app.AlertDialog
import com.bharath.carcrashdetection.databinding.ActivitySubscriberBinding
import com.bharath.carcrashdetection.databinding.*
import com.google.android.material.snackbar.Snackbar

class SubscriberActivity : BaseActivity<ActivitySubscriberBinding>() {
    
    companion object {
        private const val TAG = "SubscriberActivity"
    }
    
    private val viewModel: SubscriberViewModel by viewModels()
    private lateinit var alertAdapter: AlertHistoryAdapter
    private lateinit var messageReceiver: BroadcastReceiver
    private lateinit var connectionStatusReceiver: BroadcastReceiver
    
    override fun getViewBinding(): ActivitySubscriberBinding = ActivitySubscriberBinding.inflate(layoutInflater)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Initialize MqttConfig
            MqttConfig.init(this)
            
            // Initialize MQTT service for subscriber
            Log.i(TAG, "Initializing MQTT service for subscriber")
            val serviceIntent = Intent(this, MqttService::class.java).apply {
                action = MqttService.ACTION_ENABLE
                putExtra("role", "SUBSCRIBER")
            }
            startService(serviceIntent)
            
                         // Setup message receiver
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
            setupAlertsList()
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
                viewModel.emergencyAlerts.collect { alerts ->
                    updateAlertsList(alerts)
                }
            }
            
            lifecycleScope.launch {
                viewModel.alertCount.collect { count ->
                    binding.tvAlertCount.text = "$count alerts received"
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
    
    private fun setupAlertsList() {
        binding.btnClearAlerts.setOnClickListener {
            viewModel.clearAllAlerts()
            Snackbar.make(binding.root, "All alerts cleared", Snackbar.LENGTH_SHORT).show()
        }
        
        // Setup RecyclerView
        binding.rvAlerts.layoutManager = LinearLayoutManager(this)
        alertAdapter = AlertHistoryAdapter { incident ->
            // Handle incident click - could open detail view
            Snackbar.make(binding.root, "Alert: ${incident.description ?: "Emergency Alert"}", Snackbar.LENGTH_SHORT).show()
        }
        binding.rvAlerts.adapter = alertAdapter
    }
    
    private fun setupExperimentalFeatures() {
        binding.btnToggleExperimental.setOnClickListener {
            viewModel.toggleExperimentalFeatures()
        }
        
        // Setup experimental feature buttons if they exist
        binding.btnTestMqttConnection?.setOnClickListener {
            runComprehensiveMqttTests()
        }
        
        binding.btnSendTestMessage?.setOnClickListener {
            sendTestMessage()
        }
        
        binding.btnMqttSettings?.setOnClickListener {
            showMqttSettings()
        }
    }
    
    private fun runComprehensiveMqttTests() {
        try {
            // Send intent to MQTT service to run tests
            val serviceIntent = Intent(this, MqttService::class.java).apply {
                action = "com.bharath.carcrashdetection.RUN_TESTS"
            }
            startService(serviceIntent)
            
            Snackbar.make(binding.root, "Running MQTT tests... Check logs for results", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Error running tests: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun sendTestMessage() {
        try {
            // Send a test message via MQTT service
            val testTopic = "emergency/test/message"
            val testPayload = "Test message from SubscriberActivity - ${System.currentTimeMillis()}"
            
            val serviceIntent = Intent(this, MqttService::class.java).apply {
                action = MqttService.ACTION_PUBLISH
                putExtra(MqttService.EXTRA_TOPIC, testTopic)
                putExtra(MqttService.EXTRA_PAYLOAD, testPayload)
                putExtra(MqttService.EXTRA_QOS, 1)
                putExtra(MqttService.EXTRA_RETAINED, false)
            }
            startService(serviceIntent)
            
            Snackbar.make(binding.root, "Test message sent to $testTopic", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Error sending test message: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun showMqttSettings() {
        try {
            // Send intent to MQTT service to get settings info
            val serviceIntent = Intent(this, MqttService::class.java).apply {
                action = "com.bharath.carcrashdetection.GET_SETTINGS"
            }
            startService(serviceIntent)
            
            Snackbar.make(binding.root, "Getting MQTT settings... Check logs for results", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Error getting MQTT settings: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun showTestResultsDialog(content: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("MQTT Test Results")
            .setMessage(content)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Copy to Clipboard") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("MQTT Test Results", content)
                clipboard.setPrimaryClip(clip)
                Snackbar.make(binding.root, "Results copied to clipboard", Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .create()
        
        dialog.show()
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
                    "com.bharath.carcrashdetection.EMERGENCY_ALERT_RECEIVED" -> {
                        val alertJson = intent.getStringExtra("alert_json") ?: ""
                        val topic = intent.getStringExtra("topic") ?: ""
                        Log.i(TAG, "🚨 Received emergency alert broadcast: $topic")
                        viewModel.handleEmergencyAlertReceived(alertJson, topic)
                        
                        // Show real-time notification
                        showNewMessageNotification("🚨 Emergency Alert", alertJson.take(50))
                    }
                    "com.bharath.carcrashdetection.SIMPLE_MESSAGE_RECEIVED" -> {
                        val message = intent.getStringExtra("message") ?: ""
                        val topic = intent.getStringExtra("topic") ?: ""
                        Log.i(TAG, "📝 Received test message broadcast: $topic")
                        viewModel.handleTestMessageReceived(message, topic)
                        
                        // Show real-time notification
                        showNewMessageNotification("📝 New Message", message.take(50))
                    }
                    "com.bharath.carcrashdetection.CUSTOM_MESSAGE_RECEIVED" -> {
                        val message = intent.getStringExtra("message") ?: ""
                        val topic = intent.getStringExtra("topic") ?: ""
                        Log.i(TAG, "💬 Received custom message broadcast: $topic")
                        viewModel.handleTestMessageReceived(message, topic)
                        
                        // Show real-time notification
                        showNewMessageNotification("💬 Custom Message", message.take(50))
                    }
                    "com.bharath.carcrashdetection.GENERAL_MESSAGE_RECEIVED" -> {
                        val message = intent.getStringExtra("message") ?: ""
                        val topic = intent.getStringExtra("topic") ?: ""
                        Log.i(TAG, "📨 Received general message broadcast: $topic")
                        viewModel.handleTestMessageReceived(message, topic)
                        
                        // Show real-time notification
                        showNewMessageNotification("📨 New Message", message.take(50))
                    }
                }
            }
        }
        
        // Register the receiver
        val filter = IntentFilter().apply {
            addAction("com.bharath.carcrashdetection.EMERGENCY_ALERT_RECEIVED")
            addAction("com.bharath.carcrashdetection.SIMPLE_MESSAGE_RECEIVED")
            addAction("com.bharath.carcrashdetection.CUSTOM_MESSAGE_RECEIVED")
            addAction("com.bharath.carcrashdetection.GENERAL_MESSAGE_RECEIVED")
        }
        registerReceiver(messageReceiver, filter)
        
                 Log.i(TAG, "📡 Message receiver registered for MQTT broadcasts")
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
     

    
    private fun updateAlertsList(alerts: List<com.bharath.carcrashdetection.data.model.Incident>) {
        if (alerts.isEmpty()) {
            binding.tvNoAlerts.visibility = View.VISIBLE
            binding.rvAlerts.visibility = View.GONE
        } else {
            binding.tvNoAlerts.visibility = View.GONE
            binding.rvAlerts.visibility = View.VISIBLE
            alertAdapter.submitList(alerts)
        }
    }
    
    private fun showNewMessageNotification(title: String, message: String) {
        try {
            // Show a snackbar notification
            val snackbar = Snackbar.make(
                binding.root,
                "$title: $message",
                Snackbar.LENGTH_LONG
            )
            
            // Add action to view the message
            snackbar.setAction("View") {
                // Scroll to the top of the list to show the latest message
                binding.rvAlerts.smoothScrollToPosition(0)
            }
            
            snackbar.show()
            
            // Also update the alert count text
            binding.tvAlertCount.text = "${viewModel.alertCount.value} alerts received"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing message notification: ${e.message}", e)
        }
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