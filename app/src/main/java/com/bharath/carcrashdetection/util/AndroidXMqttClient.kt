package com.bharath.carcrashdetection.util

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * AndroidX-compatible MQTT client wrapper that avoids the LocalBroadcastManager compatibility issues
 * by using the core Paho library directly instead of the Android service wrapper.
 */
class AndroidXMqttClient(
    private val context: Context,
    private val serverUri: String,
    private val clientId: String
) {
    
    companion object {
        private const val TAG = "AndroidXMqttClient"
    }
    
    private var mqttClient: MqttClient? = null
    private var isConnected = false
    private var callback: MqttCallback? = null
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    
    init {
        try {
            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())
            Log.d(TAG, "MQTT client initialized with ID: $clientId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MQTT client: ${e.message}")
        }
    }
    
    /**
     * Set the callback for MQTT events
     */
    fun setCallback(callback: MqttCallback) {
        this.callback = callback
        mqttClient?.setCallback(callback)
    }
    
    /**
     * Connect to the MQTT broker
     */
    fun connect(options: MqttConnectOptions, userContext: Any?, actionListener: IMqttActionListener?) {
        if (mqttClient == null) {
            actionListener?.onFailure(null, Exception("MQTT client not initialized"))
            return
        }
        
        executor.submit {
            try {
                Log.d(TAG, "Connecting to MQTT broker: $serverUri")
                mqttClient?.connect(options)
                isConnected = true
                Log.i(TAG, "Successfully connected to MQTT broker!")
                actionListener?.onSuccess(null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to MQTT broker: ${e.message}")
                isConnected = false
                actionListener?.onFailure(null, e)
            }
        }
    }
    
    /**
     * Disconnect from the MQTT broker
     */
    fun disconnect() {
        try {
            mqttClient?.disconnect()
            isConnected = false
            Log.i(TAG, "Disconnected from MQTT broker")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }
    
    /**
     * Check if the client is connected
     */
    fun isConnected(): Boolean {
        return isConnected && mqttClient?.isConnected == true
    }
    
    /**
     * Subscribe to a topic
     */
    fun subscribe(topic: String, qos: Int, userContext: Any?, actionListener: IMqttActionListener?) {
        if (!isConnected()) {
            actionListener?.onFailure(null, Exception("MQTT client not connected"))
            return
        }
        
        executor.submit {
            try {
                Log.d(TAG, "Subscribing to topic: $topic")
                mqttClient?.subscribe(topic, qos)
                Log.i(TAG, "Successfully subscribed to $topic")
                actionListener?.onSuccess(null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to $topic: ${e.message}")
                actionListener?.onFailure(null, e)
            }
        }
    }
    
    /**
     * Publish a message
     */
    fun publish(topic: String, message: MqttMessage, userContext: Any?, actionListener: IMqttActionListener?) {
        if (!isConnected()) {
            actionListener?.onFailure(null, Exception("MQTT client not connected"))
            return
        }
        
        executor.submit {
            try {
                Log.d(TAG, "Publishing message to $topic")
                mqttClient?.publish(topic, message)
                Log.i(TAG, "Message published successfully to $topic")
                actionListener?.onSuccess(null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish message: ${e.message}")
                actionListener?.onFailure(null, e)
            }
        }
    }
    
    /**
     * Close the client and release resources
     */
    fun close() {
        try {
            disconnect()
            mqttClient?.close()
            executor.shutdown()
            Log.d(TAG, "MQTT client closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing MQTT client: ${e.message}")
        }
    }
}
