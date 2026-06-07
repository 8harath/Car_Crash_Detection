package com.bharath.carcrashdetection.util

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.MutableLiveData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import java.util.*
import java.util.concurrent.TimeUnit

// Import Eclipse Paho MQTT Client
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttMessage
// Import our custom AndroidX-compatible MQTT client
import com.bharath.carcrashdetection.util.AndroidXMqttClient
import com.bharath.carcrashdetection.util.MqttConfig

class MqttService : Service() {
    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED }
    
    companion object {
        val connectionState = MutableLiveData(ConnectionState.DISCONNECTED)
        const val ACTION_PUBLISH = "com.bharath.carcrashdetection.mqtt.ACTION_PUBLISH"
        const val ACTION_ENABLE = "com.bharath.carcrashdetection.mqtt.ACTION_ENABLE"
        const val ACTION_DISABLE = "com.bharath.carcrashdetection.mqtt.ACTION_DISABLE"
        const val ACTION_UPDATE_SETTINGS = "UPDATE_SETTINGS"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_PAYLOAD = "extra_payload"
        const val EXTRA_QOS = "extra_qos"
        const val EXTRA_RETAINED = "extra_retained"
        
        // Track if MQTT is enabled globally
        private var isMqttEnabledGlobal = false
        
        fun isMqttEnabled(): Boolean = isMqttEnabledGlobal
        
        fun setMqttEnabled(enabled: Boolean) {
            isMqttEnabledGlobal = enabled
        }
        
        /**
         * Check if MQTT service is currently enabled and ready to use
         */
        fun isServiceEnabled(): Boolean {
            return isMqttEnabledGlobal
        }
        
        /**
         * Enable MQTT service from outside - sends intent to service
         */
        fun enableService(context: Context, role: String? = null) {
            val intent = Intent(context, MqttService::class.java).apply {
                action = ACTION_ENABLE
                if (role != null) {
                    putExtra("role", role)
                }
            }
            context.startService(intent)
        }
        
        /**
         * Disable MQTT service from outside - sends intent to service
         */
        fun disableService(context: Context) {
            val intent = Intent(context, MqttService::class.java).apply {
                action = ACTION_DISABLE
            }
            context.startService(intent)
        }
        
        /**
         * Get current connection state
         */
        fun getConnectionState(): ConnectionState? {
            return connectionState.value
        }
        
        /**
         * Check if MQTT service is currently running
         */
        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = manager.getRunningServices(Integer.MAX_VALUE)
            return runningServices.any { it.service.className == MqttService::class.java.name }
        }
        
        /**
         * Check if MQTT is enabled and connected
         */
        fun isConnected(): Boolean {
            return isMqttEnabledGlobal && connectionState.value == ConnectionState.CONNECTED
        }
        
        // MQTT Configuration Constants
        const val CLIENT_ID_PREFIX = "android_client_"
        const val CONNECTION_TIMEOUT = 30
        const val KEEP_ALIVE_INTERVAL = 60
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_DELAY = 5000L
        
        /**
         * Get current MQTT status as a human-readable string
         */
        fun getStatusString(): String {
            return when {
                !isMqttEnabledGlobal -> "MQTT: Disabled"
                connectionState.value == ConnectionState.CONNECTING -> "MQTT: Connecting..."
                connectionState.value == ConnectionState.CONNECTED -> "MQTT: Connected"
                connectionState.value == ConnectionState.DISCONNECTED -> "MQTT: Disconnected"
                else -> "MQTT: Unknown"
            }
        }
        
        /**
         * Get current MQTT status as a human-readable string with role info
         */
        fun getStatusStringWithRole(role: String? = null): String {
            val baseStatus = getStatusString()
            return if (role != null) {
                "$baseStatus ($role)"
            } else {
                baseStatus
            }
        }
    }
    
    private lateinit var mqttClient: AndroidXMqttClient
    private val TAG = "MqttService"
    private var reconnectAttempts = 0
    private var isReconnecting = false
    private var isConnected = false

    private var pendingRole: String? = null
    private var pendingIncidentId: String? = null
    private var isMqttEnabled = false

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isNetworkAvailable()) {
                Log.i(TAG, "Network available, but MQTT will only connect when user enables it")
                // Don't auto-connect - only connect when user explicitly requests
            } else {
                Log.w(TAG, "Network unavailable, MQTT will disconnect if active.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Log.i(TAG, "MQTT service created - initializing MQTT client")
            
            // Generate unique client ID
            val clientId = MqttConfig.CLIENT_ID_PREFIX + System.currentTimeMillis() + "_" + Random().nextInt(1000)
            
            // Get broker URL from configuration with validation
            val brokerUrl = MqttConfig.getBrokerUrlSafe()
            if (brokerUrl == null) {
                Log.e(TAG, "Invalid broker configuration - cannot initialize MQTT client")
                connectionState.postValue(ConnectionState.DISCONNECTED)
                return
            }
            
            Log.i(TAG, "Using broker URL: $brokerUrl")
            
            // Initialize MQTT client
            mqttClient = AndroidXMqttClient(applicationContext, brokerUrl, clientId)
            
            // Register network receiver
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
            }
            
            // Set initial connection state - DISCONNECTED until user explicitly enables
            connectionState.postValue(ConnectionState.DISCONNECTED)
            
            Log.i(TAG, "MQTT client initialized with ID: $clientId - waiting for user to enable")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in MQTT service onCreate: ${e.message}")
            connectionState.postValue(ConnectionState.DISCONNECTED)
        }
    }

    private fun isValidTopic(topic: String): Boolean {
        return topic.startsWith("emergency/")
    }
    
    /**
     * Test broker connectivity before attempting MQTT connection
     */
    private fun testBrokerConnectivity(): Boolean {
        try {
            val brokerUrl = MqttConfig.getBrokerUrlSafe()
            if (brokerUrl == null) {
                Log.e(TAG, "Invalid broker configuration")
                return false
            }
            
            // Extract IP and port from broker URL
            val urlParts = brokerUrl.removePrefix("tcp://").split(":")
            if (urlParts.size != 2) {
                Log.e(TAG, "Invalid broker URL format: $brokerUrl")
                return false
            }
            
            val ip = urlParts[0]
            val port = urlParts[1].toIntOrNull()
            
            if (port == null || !MqttConfig.isValidPort(port)) {
                Log.e(TAG, "Invalid broker port: $port")
                return false
            }
            
            if (!MqttConfig.isValidIpAddress(ip)) {
                Log.e(TAG, "Invalid broker IP address: $ip")
                return false
            }
            
            // Test actual connectivity using socket
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(ip, port), 5000) // 5 second timeout
                socket.close()
                Log.i(TAG, "✅ Broker connectivity test successful: $ip:$port")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Broker connectivity test failed: $ip:$port - ${e.message}")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing broker connectivity: ${e.message}")
            return false
        }
    }
    
    /**
     * Verify that the MQTT connection is actually working
     */
    private fun verifyConnection() {
        try {
            if (!::mqttClient.isInitialized || !mqttClient.isConnected()) {
                Log.w(TAG, "Cannot verify connection - client not connected")
                return
            }
            
            // Send a test message to verify connection is working
            val testTopic = "emergency/test/connection"
            val testPayload = "Connection test - ${System.currentTimeMillis()}"
            
            Log.i(TAG, "🔍 Verifying MQTT connection with test message...")
            
            val message = MqttMessage(testPayload.toByteArray()).apply {
                this.qos = 0 // QoS 0 for test message
                this.isRetained = false
            }
            
            mqttClient.publish(testTopic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "✅ Connection verification successful - test message sent")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.w(TAG, "⚠️ Connection verification failed: ${exception?.message}")
                    // Connection might not be fully working
                    connectionState.postValue(ConnectionState.DISCONNECTED)
                    isConnected = false
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during connection verification: ${e.message}")
        }
    }

    fun publish(topic: String, payload: String, qos: Int = 1, retained: Boolean = false) {
        if (!isValidTopic(topic)) {
            Log.e(TAG, "Invalid topic: $topic")
            // Send broadcast to notify UI of invalid topic
            val intent = Intent("com.bharath.carcrashdetection.MESSAGE_PUBLISHED")
            intent.putExtra("topic", topic)
            intent.putExtra("success", false)
            intent.putExtra("error", "Invalid topic: $topic")
            sendBroadcast(intent)
            return
        }
        
        if (!isMqttEnabled) {
            Log.w(TAG, "MQTT is not enabled by user, cannot publish message to: $topic")
            // Send broadcast to notify UI that MQTT is not enabled
            val intent = Intent("com.bharath.carcrashdetection.MESSAGE_PUBLISHED")
            intent.putExtra("topic", topic)
            intent.putExtra("success", false)
            intent.putExtra("error", "MQTT not enabled")
            sendBroadcast(intent)
            return
        }
        
        val message = MqttMessage(payload.toByteArray()).apply {
            this.qos = qos
            this.isRetained = retained
        }
        
        if (::mqttClient.isInitialized && mqttClient.isConnected()) {
            try {
                Log.i(TAG, "📤 Publishing message to $topic: $payload")
                mqttClient.publish(topic, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "✅ Message published successfully to $topic")
                        // Send broadcast to notify UI of successful publish
                        val intent = Intent("com.bharath.carcrashdetection.MESSAGE_PUBLISHED")
                        intent.putExtra("topic", topic)
                        intent.putExtra("success", true)
                        intent.putExtra("payload", payload)
                        sendBroadcast(intent)
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "❌ Publish failed for $topic: ${exception?.message}")
                        MqttMessageQueue.enqueue(topic, payload, qos, retained)
                        // Send broadcast to notify UI of failed publish
                        val intent = Intent("com.bharath.carcrashdetection.MESSAGE_PUBLISHED")
                        intent.putExtra("topic", topic)
                        intent.putExtra("success", false)
                        intent.putExtra("error", exception?.message ?: "Unknown error")
                        sendBroadcast(intent)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "❌ Publish exception for $topic: ${e.message}")
                MqttMessageQueue.enqueue(topic, payload, qos, retained)
                // Send broadcast to notify UI of failed publish
                val intent = Intent("com.bharath.carcrashdetection.MESSAGE_PUBLISHED")
                intent.putExtra("topic", topic)
                intent.putExtra("success", false)
                intent.putExtra("error", e.message ?: "Unknown exception")
                sendBroadcast(intent)
            }
        } else {
            Log.w(TAG, "❌ Not connected, enqueuing message for $topic")
            MqttMessageQueue.enqueue(topic, payload, qos, retained)
            // Send broadcast to notify UI that message was queued
            val intent = Intent("com.bharath.carcrashdetection.MESSAGE_PUBLISHED")
            intent.putExtra("topic", topic)
            intent.putExtra("success", false)
            intent.putExtra("error", "MQTT not connected - message queued")
            sendBroadcast(intent)
        }
    }

    fun subscribeToTopics(topics: List<String>) {
        if (!isMqttEnabled) {
            Log.w(TAG, "MQTT is not enabled by user, cannot subscribe to topics")
            return
        }
        
        val validTopics = topics.filter { isValidTopic(it) }
        if (::mqttClient.isInitialized && mqttClient.isConnected()) {
            validTopics.forEach { topic ->
                try {
                    Log.d(TAG, "Subscribing to topic: $topic")
                    mqttClient.subscribe(topic, 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i(TAG, "Successfully subscribed to $topic")
                        }
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Failed to subscribe to $topic: ${exception?.message}")
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Subscribe exception for $topic: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "Not connected, cannot subscribe now.")
        }
    }

    // Subscribe for specific role (publisher or subscriber)
    private fun subscribeForRole(role: String, incidentId: String? = null) {
        if (!::mqttClient.isInitialized || !mqttClient.isConnected()) {
            Log.w(TAG, "Cannot subscribe: MQTT client not connected")
            return
        }
        
        try {
            when (role.uppercase()) {
                "PUBLISHER" -> {
                    // Publisher subscribes to response topics
                    val responseTopic = "emergency/response/${incidentId ?: "general"}"
                    mqttClient.subscribe(responseTopic, 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i(TAG, "Subscribed to response topic: $responseTopic")
                        }
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Failed to subscribe to response topic: ${exception?.message}")
                        }
                    })
                    
                    // Also subscribe to system status
                    val systemTopic = "emergency/status/system"
                    mqttClient.subscribe(systemTopic, 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i(TAG, "Subscribed to system status topic: $systemTopic")
                        }
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Failed to subscribe to system status topic: ${exception?.message}")
                        }
                    })
                }
                "SUBSCRIBER" -> {
                    // Subscriber subscribes to emergency alerts
                    val alertTopic = "emergency/alerts/#"
                    mqttClient.subscribe(alertTopic, 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i(TAG, "Subscribed to alert topic: $alertTopic")
                        }
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Failed to subscribe to alert topic: ${exception?.message}")
                        }
                    })
                    
                    // Also subscribe to test messages
                    val testTopic = "emergency/test/#"
                    mqttClient.subscribe(testTopic, 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i(TAG, "Subscribed to test topic: $testTopic")
                        }
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Failed to subscribe to test topic: ${exception?.message}")
                        }
                    })
                    
                    // Subscribe to custom messages
                    val customTopic = "emergency/custom/#"
                    mqttClient.subscribe(customTopic, 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i(TAG, "Subscribed to custom message topic: $customTopic")
                        }
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Failed to subscribe to custom message topic: ${exception?.message}")
                        }
                    })
                    
                    // Subscribe to response acknowledgments
                    val ackTopic = "emergency/response/ack/#"
                    mqttClient.subscribe(ackTopic, 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i(TAG, "Subscribed to response ack topic: $ackTopic")
                        }
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Failed to subscribe to response ack topic: ${exception?.message}")
                        }
                    })
                }
                else -> {
                    Log.w(TAG, "Unknown role: $role")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topics: ${e.message}")
        }
    }

    private fun retryQueuedMessages() {
        if (::mqttClient.isInitialized && mqttClient.isConnected()) {
            Log.d(TAG, "Retrying queued messages...")
            MqttMessageQueue.retryAll { topic, payload, qos, retained ->
                try {
                    publish(topic, payload, qos, retained)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Retry publish failed: ${e.message}")
                    false
                }
            }
        }
    }

    private fun connect() {
        if (!isMqttEnabled) {
            Log.i(TAG, "MQTT is not enabled by user, skipping connection")
            return
        }
        
        if (isReconnecting) {
            Log.d(TAG, "Already attempting to reconnect, skipping...")
            return
        }
        
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Network not available, cannot connect to MQTT")
            connectionState.postValue(ConnectionState.DISCONNECTED)
            // Send broadcast to notify UI of network error
            val intent = Intent("com.bharath.carcrashdetection.CONNECTION_STATUS")
            intent.putExtra("status", "DISCONNECTED")
            intent.putExtra("error", "Network not available")
            sendBroadcast(intent)
            return
        }
        
        // Test broker connectivity before attempting connection
        if (!testBrokerConnectivity()) {
            Log.e(TAG, "Broker connectivity test failed - cannot connect")
            connectionState.postValue(ConnectionState.DISCONNECTED)
            // Send broadcast to notify UI of invalid IP error
            val intent = Intent("com.bharath.carcrashdetection.CONNECTION_STATUS")
            intent.putExtra("status", "DISCONNECTED")
            intent.putExtra("error", "Invalid IP address or port")
            sendBroadcast(intent)
            return
        }
        
        try {
            if (!::mqttClient.isInitialized) {
                Log.e(TAG, "MQTT client not initialized")
                connectionState.postValue(ConnectionState.DISCONNECTED)
                return
            }
            
            // Get broker URL from configuration with validation
            val brokerUrl = MqttConfig.getBrokerUrlSafe()
            if (brokerUrl == null) {
                Log.e(TAG, "Invalid broker configuration - cannot connect")
                connectionState.postValue(ConnectionState.DISCONNECTED)
                return
            }
            Log.i(TAG, "Attempting to connect to MQTT broker: $brokerUrl")
            connectionState.postValue(ConnectionState.CONNECTING)
            isReconnecting = true
            
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = false // We'll handle reconnection manually
                isCleanSession = true
                connectionTimeout = MqttConfig.CONNECTION_TIMEOUT
                keepAliveInterval = MqttConfig.KEEP_ALIVE_INTERVAL
                // Authentication is handled through MqttConfig methods
                val username = MqttConfig.getUsername()
                val password = MqttConfig.getPassword()
                if (!username.isNullOrEmpty()) {
                    userName = username
                    password?.let { pwd ->
                        this.password = pwd.toCharArray()
                    }
                }
            }
            
            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT connection lost: ${cause?.message}")
                    isConnected = false
                    connectionState.postValue(ConnectionState.DISCONNECTED)
                    
                    // Attempt reconnection if not already trying
                    if (!isReconnecting && reconnectAttempts < MqttConfig.MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect()
                    }
                }
                
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    Log.i(TAG, "📨 Message arrived: $topic -> ${String(message?.payload ?: ByteArray(0))}")
                    if (topic != null && message != null) {
                        try {
                            val payload = String(message.payload)
                            Log.i(TAG, "📨 Message payload: $payload")
                            
                            if (topic.startsWith("emergency/alerts/")) {
                                Log.i(TAG, "🚨 Emergency alert received on topic: $topic")
                                val intent = Intent("com.bharath.carcrashdetection.EMERGENCY_ALERT_RECEIVED")
                                intent.putExtra("alert_json", payload)
                                intent.putExtra("topic", topic)
                                sendBroadcast(intent)
                            } else if (topic.startsWith("emergency/test/")) {
                                Log.i(TAG, "📝 Test message received on topic: $topic")
                                // Handle test messages
                                val intent = Intent("com.bharath.carcrashdetection.SIMPLE_MESSAGE_RECEIVED")
                                intent.putExtra("topic", topic)
                                intent.putExtra("message", payload)
                                sendBroadcast(intent)
                            } else if (topic.startsWith("emergency/custom/")) {
                                Log.i(TAG, "💬 Custom message received on topic: $topic")
                                // Handle custom messages
                                val intent = Intent("com.bharath.carcrashdetection.CUSTOM_MESSAGE_RECEIVED")
                                intent.putExtra("topic", topic)
                                intent.putExtra("message", payload)
                                sendBroadcast(intent)
                            } else if (topic.startsWith("emergency/")) {
                                Log.i(TAG, "📨 General emergency message received on topic: $topic")
                                // Handle other emergency messages
                                val intent = Intent("com.bharath.carcrashdetection.GENERAL_MESSAGE_RECEIVED")
                                intent.putExtra("topic", topic)
                                intent.putExtra("message", payload)
                                sendBroadcast(intent)
                            } else {
                                Log.i(TAG, "📨 General message received on topic: $topic")
                                // Handle other messages
                                val intent = Intent("com.bharath.carcrashdetection.GENERAL_MESSAGE_RECEIVED")
                                intent.putExtra("topic", topic)
                                intent.putExtra("message", payload)
                                sendBroadcast(intent)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error processing received message on topic $topic: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Received message with null topic or payload")
                    }
                }
                
                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d(TAG, "Message delivered: ${token?.message}")
                }
            })
            
                                      mqttClient.connect(options, null, object : IMqttActionListener {
                 override fun onSuccess(asyncActionToken: IMqttToken?) {
                     Log.i(TAG, "✅ Successfully connected to MQTT broker!")
                     isConnected = true
                     connectionState.postValue(ConnectionState.CONNECTED)
                     reconnectAttempts = 0
                     isReconnecting = false
                     
                     // Send broadcast to notify UI of successful connection
                     val intent = Intent("com.bharath.carcrashdetection.CONNECTION_STATUS")
                     intent.putExtra("status", "CONNECTED")
                     sendBroadcast(intent)
                     
                     // Verify connection is working
                     verifyConnection()
                     
                     // Retry any queued messages
                     retryQueuedMessages()
                     
                     // Subscribe for pending role if any
                     pendingRole?.let { role ->
                         subscribeForRole(role, pendingIncidentId)
                     }
                 }
                
                                 override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                     Log.e(TAG, "❌ Failed to connect to MQTT broker: ${exception?.message}")
                     isConnected = false
                     connectionState.postValue(ConnectionState.DISCONNECTED)
                     isReconnecting = false
                     
                     // Send broadcast to notify UI of connection failure
                     val intent = Intent("com.bharath.carcrashdetection.CONNECTION_STATUS")
                     intent.putExtra("status", "DISCONNECTED")
                     intent.putExtra("error", "Failed to connect: ${exception?.message ?: "Unknown error"}")
                     sendBroadcast(intent)
                     
                     // Increment reconnect attempts
                     reconnectAttempts++
                     
                     // Try to reconnect if we haven't exceeded max attempts
                     if (reconnectAttempts < MqttConfig.MAX_RECONNECT_ATTEMPTS && isMqttEnabled) {
                         Log.i(TAG, "Reconnect attempt $reconnectAttempts of ${MqttConfig.MAX_RECONNECT_ATTEMPTS}")
                         android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                             connect()
                         }, MqttConfig.RECONNECT_DELAY)
                     } else {
                         Log.w(TAG, "Max reconnect attempts reached or MQTT disabled")
                     }
                 }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during MQTT connection: ${e.message}")
            isConnected = false
            connectionState.postValue(ConnectionState.DISCONNECTED)
            isReconnecting = false
            
            // Schedule reconnection attempt
            if (reconnectAttempts < MqttConfig.MAX_RECONNECT_ATTEMPTS) {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (isReconnecting) return
        
        isReconnecting = true
        reconnectAttempts++
        
        Log.i(TAG, "Scheduling reconnection attempt $reconnectAttempts in ${MqttConfig.RECONNECT_DELAY}ms")
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isReconnecting = false
            if (::mqttClient.isInitialized && !mqttClient.isConnected()) {
                connect()
            }
        }, MqttConfig.RECONNECT_DELAY)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo: NetworkInfo? = cm.activeNetworkInfo
            networkInfo?.isConnectedOrConnecting == true
        }
    }

    // Monitor connection quality
    private fun logConnectionQuality() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = cm.activeNetworkInfo
        val type = networkInfo?.typeName ?: "Unknown"
        Log.d(TAG, "Current network type: $type")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { inIntent ->
            when (inIntent.action) {
                ACTION_ENABLE -> {
                    Log.i(TAG, "User explicitly enabled MQTT service")
                    isMqttEnabled = true
                    setMqttEnabled(true)
                    // Now attempt to connect automatically
                    connect()
                }
                ACTION_DISABLE -> {
                    Log.i(TAG, "User explicitly disabled MQTT service")
                    disableMqtt()
                }
                ACTION_PUBLISH -> {
                    val topic = inIntent.getStringExtra(EXTRA_TOPIC)
                    val payload = inIntent.getStringExtra(EXTRA_PAYLOAD)
                    val qos = inIntent.getIntExtra(EXTRA_QOS, 1)
                    val retained = inIntent.getBooleanExtra(EXTRA_RETAINED, false)
                    if (!topic.isNullOrEmpty() && payload != null) {
                        Log.i(TAG, "📤 Publishing message via service intent: $topic")
                        publish(topic, payload, qos, retained)
                    } else {
                        Log.w(TAG, "Invalid topic or payload for publishing")
                    }
                }
                "com.bharath.carcrashdetection.RUN_TESTS" -> {
                    Log.i(TAG, "🧪 Running comprehensive MQTT tests via service intent")
                    // Run tests in background thread
                    Thread {
                        try {
                            val testReport = runComprehensiveTests()
                            Log.i(TAG, "🧪 Test results: $testReport")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error running tests: ${e.message}", e)
                        }
                    }.start()
                }
                "com.bharath.carcrashdetection.GET_SETTINGS" -> {
                    Log.i(TAG, "📋 Getting MQTT settings via service intent")
                    val brokerConfig = getBrokerConfiguration()
                    val networkTest = testNetworkConnectivity()
                    Log.i(TAG, "📋 Broker config: $brokerConfig")
                    Log.i(TAG, "📋 Network test: $networkTest")
                }
                ACTION_UPDATE_SETTINGS -> {
                    Log.i(TAG, "Settings updated, reconnecting with new broker configuration")
                    if (::mqttClient.isInitialized && mqttClient.isConnected()) {
                        mqttClient.disconnect()
                    } else {
                        // Not connected, proceed with reconnection
                    }
                    // Small delay to ensure disconnect is complete
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        connect()
                    }, 1000)
                }
                else -> {
                    val role = inIntent.getStringExtra("role")
                    val incidentId = inIntent.getStringExtra("incidentId")
                    if (!role.isNullOrEmpty()) {
                        pendingRole = role
                        pendingIncidentId = incidentId
                        
                        // Automatically connect when role is specified
                        if (!isMqttEnabled) {
                            Log.i(TAG, "Auto-enabling MQTT for role: $role")
                            isMqttEnabled = true
                            setMqttEnabled(true)
                        }
                        
                        if (::mqttClient.isInitialized && mqttClient.isConnected()) {
                            Log.i(TAG, "Received start with role=$role, subscribing immediately")
                            subscribeForRole(role, incidentId)
                        } else {
                            Log.i(TAG, "Received start with role=$role, attempting to connect")
                            connect()
                        }
                    } else {
                        Log.w(TAG, "No role provided in intent")
                    }
                }
            }
        }
        // Service will be restarted if killed
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "MQTT service destroyed - cleaning up")
        
        try {
            unregisterReceiver(networkReceiver)
            if (::mqttClient.isInitialized) {
                mqttClient.close()
            } else {
                // MQTT client not initialized, nothing to close
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting MQTT: ${e.message}")
        }
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Not a bound service
        return null
    }
    
    /**
     * Enable MQTT service - this should be called when user explicitly wants MQTT
     */
    fun enableMqtt() {
        Log.i(TAG, "Enabling MQTT service as requested by user")
        isMqttEnabled = true
        setMqttEnabled(true)
        
        // If we have pending role, try to connect now
        if (pendingRole != null) {
            Log.i(TAG, "Connecting with pending role: $pendingRole")
            connect()
        } else {
            // No pending role, just connect normally
            connect()
        }
    }
    
    /**
     * Disable MQTT service - this will disconnect and stop auto-reconnection
     */
    fun disableMqtt() {
        Log.i(TAG, "Disabling MQTT service as requested by user")
        isMqttEnabled = false
        setMqttEnabled(false)
        
        // Disconnect if connected
        if (::mqttClient.isInitialized && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect()
                Log.i(TAG, "MQTT disconnected due to user disabling")
            } catch (j: Exception) {
                Log.e(TAG, "Error disconnecting MQTT: ${j.message}")
            }
        } else {
            // Not connected, just update state
            Log.i(TAG, "MQTT not connected, updating state only")
        }
        
        // Reset connection state
        connectionState.postValue(ConnectionState.DISCONNECTED)
        isReconnecting = false
        reconnectAttempts = 0
        isConnected = false
    }

    /**
     * Get current broker information and connection status
     */
    fun getBrokerInfo(): String {
        return try {
            val brokerUrl = MqttConfig.getBrokerUrlSafe()
            val ip = MqttConfig.getBrokerIp()
            val port = MqttConfig.getBrokerPort()
            val isConnected = if (::mqttClient.isInitialized) mqttClient.isConnected() else false
            
            "Broker: $ip:$port\n" +
            "URL: $brokerUrl\n" +
            "Connected: $isConnected\n" +
            "MQTT Enabled: $isMqttEnabled\n" +
            "Reconnect Attempts: $reconnectAttempts"
        } catch (e: Exception) {
            "Error getting broker info: ${e.message}"
        }
    }
    
    /**
     * Check if we can connect to the current broker configuration
     */
    fun canConnectToBroker(): Boolean {
        return try {
            testBrokerConnectivity() && isNetworkAvailable()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking broker connectivity: ${e.message}")
            false
        }
    }
    
    /**
     * Update broker settings and reconnect
     */
    fun updateBrokerSettings(newIp: String, newPort: Int) {
        try {
            Log.i(TAG, "Updating broker settings to $newIp:$newPort")
            
            // Validate new settings
            if (!MqttConfig.isValidIpAddress(newIp)) {
                Log.e(TAG, "Invalid IP address: $newIp")
                return
            }
            
            if (!MqttConfig.isValidPort(newPort)) {
                Log.e(TAG, "Invalid port: $newPort")
                return
            }
            
            // Update configuration
            MqttConfig.updateBrokerSettings(newIp, newPort)
            
            // Disconnect current connection if any
            if (::mqttClient.isInitialized && mqttClient.isConnected()) {
                Log.i(TAG, "Disconnecting from current broker to apply new settings")
                mqttClient.disconnect()
            }
            
            // Reinitialize client with new settings
            val brokerUrl = MqttConfig.getBrokerUrlSafe()
            if (brokerUrl != null) {
                val clientId = MqttConfig.CLIENT_ID_PREFIX + System.currentTimeMillis() + "_" + Random().nextInt(1000)
                mqttClient = AndroidXMqttClient(applicationContext, brokerUrl, clientId)
                Log.i(TAG, "MQTT client reinitialized with new broker: $brokerUrl")
                
                // Attempt to connect if MQTT is enabled
                if (isMqttEnabled) {
                    connect()
                }
            } else {
                Log.e(TAG, "Failed to get valid broker URL after update")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating broker settings: ${e.message}")
        }
    }
    
    /**
     * Test message sending to verify connection functionality
     */
    fun testMessageSending(): Boolean {
        return try {
            if (!::mqttClient.isInitialized || !mqttClient.isConnected()) {
                Log.w(TAG, "Cannot test message sending - not connected")
                return false
            }
            
            val testTopic = "emergency/test/message"
            val testPayload = "Test message - ${System.currentTimeMillis()}"
            
            Log.i(TAG, "🧪 Testing message sending to topic: $testTopic")
            
            val message = MqttMessage(testPayload.toByteArray()).apply {
                this.qos = 1
                this.isRetained = false
            }
            
            var testResult = false
            
            mqttClient.publish(testTopic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "✅ Message sending test successful")
                    testResult = true
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ Message sending test failed: ${exception?.message}")
                    testResult = false
                }
            })
            
            // Wait a bit for the result
            Thread.sleep(1000)
            return testResult
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during message sending test: ${e.message}")
            return false
        }
    }
    
    /**
     * Get detailed connection diagnostics
     */
    fun getConnectionDiagnostics(): String {
        return try {
            val brokerUrl = MqttConfig.getBrokerUrlSafe()
            val ip = MqttConfig.getBrokerIp()
            val port = MqttConfig.getBrokerPort()
            val networkAvailable = isNetworkAvailable()
            val brokerValid = testBrokerConnectivity()
            val clientInitialized = ::mqttClient.isInitialized
            val clientConnected = if (clientInitialized) mqttClient.isConnected() else false
            val mqttEnabled = isMqttEnabled
            val reconnectAttempts = reconnectAttempts
            val isReconnecting = isReconnecting
            
            """
            🔍 MQTT Connection Diagnostics
            ================================
            Broker IP: $ip
            Broker Port: $port
            Broker URL: $brokerUrl
            Network Available: $networkAvailable
            Broker Configuration Valid: $brokerValid
            Client Initialized: $clientInitialized
            Client Connected: $clientConnected
            MQTT Enabled: $mqttEnabled
            Reconnect Attempts: $reconnectAttempts
            Currently Reconnecting: $isReconnecting
            Connection State: ${connectionState.value}
            ================================
            """.trimIndent()
            
        } catch (e: Exception) {
            "Error getting diagnostics: ${e.message}"
        }
    }
    
    /**
     * Force reconnect with current settings
     */
    fun forceReconnect() {
        try {
            Log.i(TAG, "🔄 Force reconnecting to MQTT broker...")
            
            // Reset reconnection state
            reconnectAttempts = 0
            isReconnecting = false
            
            // Disconnect if currently connected
            if (::mqttClient.isInitialized && mqttClient.isConnected()) {
                Log.i(TAG, "Disconnecting from current connection")
                mqttClient.disconnect()
            }
            
            // Wait a bit for disconnect to complete
            Thread.sleep(1000)
            
            // Attempt to connect
            if (isMqttEnabled) {
                connect()
            } else {
                Log.w(TAG, "MQTT is not enabled, cannot reconnect")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during force reconnect: ${e.message}")
        }
    }
    
    /**
     * Validate and test current broker configuration
     */
    fun validateAndTestBroker(): String {
        return try {
            val diagnostics = getConnectionDiagnostics()
            val brokerValid = testBrokerConnectivity()
            val networkAvailable = isNetworkAvailable()
            
            var result = "🔍 Broker Configuration Validation\n"
            result += "================================\n"
            result += "Network Available: $networkAvailable\n"
            result += "Broker Configuration Valid: $brokerValid\n"
            
            if (!networkAvailable) {
                result += "❌ Network is not available\n"
                return result
            }
            
            if (!brokerValid) {
                result += "❌ Broker configuration is invalid\n"
                return result
            }
            
            // Test connection if not already connected
            if (!::mqttClient.isInitialized || !mqttClient.isConnected()) {
                result += "⚠️ MQTT client not connected, attempting test connection...\n"
                
                // Try to connect temporarily for testing
                val testClient = AndroidXMqttClient(applicationContext, MqttConfig.getBrokerUrlSafe()!!, "test_client_${System.currentTimeMillis()}")
                
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = false
                    isCleanSession = true
                    connectionTimeout = 10 // Short timeout for testing
                    keepAliveInterval = 30
                }
                
                var connectionTestResult = false
                testClient.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        connectionTestResult = true
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        connectionTestResult = false
                    }
                })
                
                // Wait for connection test
                Thread.sleep(2000)
                
                if (connectionTestResult) {
                    result += "✅ Test connection successful - broker is reachable\n"
                    // Disconnect test client
                    testClient.disconnect()
                } else {
                    result += "❌ Test connection failed - broker is not reachable\n"
                }
            } else {
                result += "✅ MQTT client is already connected\n"
                
                // Test message sending
                if (testMessageSending()) {
                    result += "✅ Message sending test successful\n"
                } else {
                    result += "❌ Message sending test failed\n"
                }
            }
            
            result += "\n" + diagnostics
            result
            
        } catch (e: Exception) {
            "Error during broker validation: ${e.message}"
        }
    }
    
    /**
     * Get current broker configuration details
     */
    fun getBrokerConfiguration(): String {
        return try {
            val ip = MqttConfig.getBrokerIp()
            val port = MqttConfig.getBrokerPort()
            val brokerUrl = MqttConfig.getBrokerUrlSafe()
            val ipValid = MqttConfig.isValidIpAddress(ip)
            val portValid = MqttConfig.isValidPort(port)
            
            """
            📋 Broker Configuration Details
            ================================
            IP Address: $ip (${if (ipValid) "✅ Valid" else "❌ Invalid"})
            Port: $port (${if (portValid) "✅ Valid" else "❌ Invalid"})
            Broker URL: $brokerUrl
            Configuration Valid: ${if (ipValid && portValid) "✅ Yes" else "❌ No"}
            ================================
            """.trimIndent()
            
        } catch (e: Exception) {
            "Error getting broker configuration: ${e.message}"
        }
    }
    
    /**
     * Test network connectivity to broker using socket connection
     */
    fun testNetworkConnectivity(): String {
        return try {
            val ip = MqttConfig.getBrokerIp()
            val port = MqttConfig.getBrokerPort()
            
            if (!MqttConfig.isValidIpAddress(ip)) {
                return "❌ Invalid IP address: $ip"
            }
            
            if (!MqttConfig.isValidPort(port)) {
                return "❌ Invalid port: $port"
            }
            
            var result = "🌐 Network Connectivity Test\n"
            result += "============================\n"
            result += "Testing connection to $ip:$port\n"
            
            // Test network connectivity using socket
            val socket = java.net.Socket()
            val timeout = 5000 // 5 seconds timeout
            
            try {
                socket.connect(java.net.InetSocketAddress(ip, port), timeout)
                result += "✅ Network connectivity successful\n"
                result += "✅ Broker is reachable at $ip:$port\n"
                socket.close()
            } catch (e: Exception) {
                result += "❌ Network connectivity failed\n"
                result += "❌ Cannot reach broker at $ip:$port\n"
                result += "Error: ${e.message}\n"
            }
            
            result += "============================\n"
            result
            
        } catch (e: Exception) {
            "Error during network connectivity test: ${e.message}"
        }
    }
    
    /**
     * Run comprehensive MQTT connection tests and provide status report
     */
    fun runComprehensiveTests(): String {
        return try {
            var report = "🔍 MQTT Comprehensive Connection Test Report\n"
            report += "==============================================\n\n"
            
            // 1. Broker Configuration Test
            report += "1. BROKER CONFIGURATION TEST\n"
            report += "-----------------------------\n"
            report += getBrokerConfiguration()
            report += "\n\n"
            
            // 2. Network Connectivity Test
            report += "2. NETWORK CONNECTIVITY TEST\n"
            report += "-----------------------------\n"
            report += testNetworkConnectivity()
            report += "\n\n"
            
            // 3. MQTT Connection Test
            report += "3. MQTT CONNECTION TEST\n"
            report += "-----------------------\n"
            report += validateAndTestBroker()
            report += "\n\n"
            
            // 4. Current Status Summary
            report += "4. CURRENT STATUS SUMMARY\n"
            report += "-------------------------\n"
            val brokerUrl = MqttConfig.getBrokerUrlSafe()
            val ip = MqttConfig.getBrokerIp()
            val port = MqttConfig.getBrokerPort()
            val networkAvailable = isNetworkAvailable()
            val brokerValid = testBrokerConnectivity()
            val clientInitialized = ::mqttClient.isInitialized
            val clientConnected = if (clientInitialized) mqttClient.isConnected() else false
            val mqttEnabled = isMqttEnabled
            
            report += "Broker IP: $ip\n"
            report += "Broker Port: $port\n"
            report += "Broker URL: $brokerUrl\n"
            report += "Network Available: ${if (networkAvailable) "✅ Yes" else "❌ No"}\n"
            report += "Broker Config Valid: ${if (brokerValid) "✅ Yes" else "❌ No"}\n"
            report += "Client Initialized: ${if (clientInitialized) "✅ Yes" else "❌ No"}\n"
            report += "Client Connected: ${if (clientConnected) "✅ Yes" else "❌ No"}\n"
            report += "MQTT Enabled: ${if (mqttEnabled) "✅ Yes" else "❌ No"}\n"
            report += "Connection State: ${connectionState.value}\n"
            
            // 5. Recommendations
            report += "\n5. RECOMMENDATIONS\n"
            report += "------------------\n"
            
            if (!networkAvailable) {
                report += "❌ Check network connection\n"
            }
            
            if (!brokerValid) {
                report += "❌ Verify broker IP and port configuration\n"
            }
            
            if (!clientInitialized) {
                report += "❌ MQTT client not initialized - restart service\n"
            }
            
            if (!clientConnected && networkAvailable && brokerValid) {
                report += "⚠️ Try reconnecting to MQTT broker\n"
            }
            
            if (!mqttEnabled) {
                report += "⚠️ Enable MQTT service to connect\n"
            }
            
            if (clientConnected) {
                report += "✅ MQTT connection is working properly\n"
            }
            
            report += "\n==============================================\n"
            report += "Test completed at: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n"
            
            report
            
        } catch (e: Exception) {
            "Error during comprehensive tests: ${e.message}"
        }
    }
}