package com.bharath.carcrashdetection.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * MQTT Configuration Manager
 * Handles broker settings and connection parameters
 */
object MqttConfig {
    
    private const val TAG = "MqttConfig"
    private const val PREFS_NAME = "mqtt_settings"
    private const val KEY_BROKER_IP = "broker_ip"
    private const val KEY_BROKER_PORT = "broker_port"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_KEEP_ALIVE = "keep_alive"
    private const val KEY_CONNECTION_TIMEOUT = "connection_timeout"
    
    // Default values
    private const val DEFAULT_BROKER_IP = "192.168.0.101"
    private const val DEFAULT_BROKER_PORT = 1883
    private const val DEFAULT_KEEP_ALIVE = 60
    private const val DEFAULT_CONNECTION_TIMEOUT = 30
    
    // Constants for MqttService
    const val CLIENT_ID_PREFIX = "android_client_"
    const val CONNECTION_TIMEOUT = 30
    const val KEEP_ALIVE_INTERVAL = 60
    const val MAX_RECONNECT_ATTEMPTS = 5
    const val RECONNECT_DELAY = 5000L
    
    // IP validation constants
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65535
    
    private var prefs: SharedPreferences? = null
    
    /**
     * Initialize the configuration manager
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.d(TAG, "MqttConfig initialized")
        }
    }
    
    /**
     * Get broker IP address
     */
    fun getBrokerIp(): String {
        return prefs?.getString(KEY_BROKER_IP, DEFAULT_BROKER_IP) ?: DEFAULT_BROKER_IP
    }
    
    /**
     * Get broker port
     */
    fun getBrokerPort(): Int {
        return prefs?.getInt(KEY_BROKER_PORT, DEFAULT_BROKER_PORT) ?: DEFAULT_BROKER_PORT
    }
    
    /**
     * Get client ID
     */
    fun getClientId(): String {
        val savedId = prefs?.getString(KEY_CLIENT_ID, null)
        return if (savedId != null) {
            savedId
        } else {
            val newId = "android_client_${System.currentTimeMillis()}"
            prefs?.edit()?.putString(KEY_CLIENT_ID, newId)?.apply()
            newId
        }
    }
    
    /**
     * Get username (if configured)
     */
    fun getUsername(): String? {
        return prefs?.getString(KEY_USERNAME, null)
    }
    
    /**
     * Get password (if configured)
     */
    fun getPassword(): String? {
        return prefs?.getString(KEY_PASSWORD, null)
    }
    
    /**
     * Get keep alive interval in seconds
     */
    fun getKeepAlive(): Int {
        return prefs?.getInt(KEY_KEEP_ALIVE, DEFAULT_KEEP_ALIVE) ?: DEFAULT_KEEP_ALIVE
    }
    
    /**
     * Get connection timeout in seconds
     */
    fun getConnectionTimeout(): Int {
        return prefs?.getInt(KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT) ?: DEFAULT_CONNECTION_TIMEOUT
    }
    
    /**
     * Update broker settings
     */
    fun updateBrokerSettings(ip: String, port: Int) {
        try {
            prefs?.edit()?.apply {
                putString(KEY_BROKER_IP, ip)
                putInt(KEY_BROKER_PORT, port)
                apply()
            }
            Log.i(TAG, "Broker settings updated: $ip:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating broker settings: ${e.message}", e)
        }
    }
    
    /**
     * Update authentication credentials
     */
    fun updateCredentials(username: String?, password: String?) {
        try {
            prefs?.edit()?.apply {
                if (username != null) {
                    putString(KEY_USERNAME, username)
                } else {
                    remove(KEY_USERNAME)
                }
                if (password != null) {
                    putString(KEY_PASSWORD, password)
                } else {
                    remove(KEY_PASSWORD)
                }
                apply()
            }
            Log.i(TAG, "Credentials updated: username=${username != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating credentials: ${e.message}", e)
        }
    }
    
    /**
     * Update connection parameters
     */
    fun updateConnectionParams(keepAlive: Int, timeout: Int) {
        try {
            prefs?.edit()?.apply {
                putInt(KEY_KEEP_ALIVE, keepAlive)
                putInt(KEY_CONNECTION_TIMEOUT, timeout)
                apply()
            }
            Log.i(TAG, "Connection parameters updated: keepAlive=$keepAlive, timeout=$timeout")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating connection parameters: ${e.message}", e)
        }
    }
    
    /**
     * Reset to default settings
     */
    fun resetToDefaults() {
        try {
            prefs?.edit()?.apply {
                putString(KEY_BROKER_IP, DEFAULT_BROKER_IP)
                putInt(KEY_BROKER_PORT, DEFAULT_BROKER_PORT)
                putInt(KEY_KEEP_ALIVE, DEFAULT_KEEP_ALIVE)
                putInt(KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT)
                remove(KEY_USERNAME)
                remove(KEY_PASSWORD)
                apply()
            }
            Log.i(TAG, "Settings reset to defaults")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting settings: ${e.message}", e)
        }
    }
    
    /**
     * Check if broker settings are valid
     */
    fun validateBrokerSettings(): Boolean {
        val ip = getBrokerIp()
        val port = getBrokerPort()
        
        return ip.isNotEmpty() && port > 0 && port <= 65535
    }
    
    /**
     * Validate IP address format
     */
    fun isValidIpAddress(ip: String): Boolean {
        return NetworkHelper.isValidIpAddress(ip)
    }
    
    /**
     * Validate port number
     */
    fun isValidPort(port: Int): Boolean {
        return port in MIN_PORT..MAX_PORT
    }
    
    /**
     * Get broker URL with validation
     */
    fun getBrokerUrl(): String {
        val ip = getBrokerIp()
        val port = getBrokerPort()
        
        if (!isValidIpAddress(ip)) {
            Log.e(TAG, "Invalid broker IP address: $ip")
            throw IllegalArgumentException("Invalid broker IP address: $ip")
        }
        
        if (!isValidPort(port)) {
            Log.e(TAG, "Invalid broker port: $port")
            throw IllegalArgumentException("Invalid broker port: $port")
        }
        
        return "tcp://$ip:$port"
    }
    
    /**
     * Get broker URL safely (returns null if invalid)
     */
    fun getBrokerUrlSafe(): String? {
        return try {
            getBrokerUrl()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting broker URL: ${e.message}")
            null
        }
    }
    
    /**
     * Check if authentication is configured
     */
    fun hasAuthentication(): Boolean {
        return getUsername() != null && getPassword() != null
    }
    
    /**
     * Clear all settings
     */
    fun clearAll() {
        try {
            prefs?.edit()?.clear()?.apply()
            Log.i(TAG, "All settings cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing settings: ${e.message}", e)
        }
    }
}