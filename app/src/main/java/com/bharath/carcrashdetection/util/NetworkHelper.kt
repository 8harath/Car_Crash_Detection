package com.bharath.carcrashdetection.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.NetworkInterface
import java.net.InetAddress
import java.net.Socket
import java.net.InetSocketAddress
import java.util.regex.Pattern

object NetworkHelper {
    private const val TAG = "NetworkHelper"
    
    // IP address validation patterns
    private val IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )
    private val HOSTNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$"
    )
    
    /**
     * Comprehensive IP address validation
     */
    fun isValidIpAddress(ip: String): Boolean {
        if (ip.isEmpty()) return false
        
        // Check for localhost
        if (ip.equals("localhost", ignoreCase = true) || ip == "127.0.0.1") {
            return true
        }
        
        // Check for valid IPv4 address
        if (IPV4_PATTERN.matcher(ip).matches()) {
            return true
        }
        
        // Check for valid hostname (basic validation)
        if (HOSTNAME_PATTERN.matcher(ip).matches()) {
            return true
        }
        
        return false
    }
    
    /**
     * Get validation error message for IP address
     */
    fun getIpValidationError(ip: String): String? {
        if (ip.isEmpty()) {
            return "IP address cannot be empty"
        }
        
        if (ip.equals("localhost", ignoreCase = true) || ip == "127.0.0.1") {
            return null // Valid
        }
        
        if (IPV4_PATTERN.matcher(ip).matches()) {
            return null // Valid
        }
        
        if (HOSTNAME_PATTERN.matcher(ip).matches()) {
            return null // Valid
        }
        
        return "Invalid IP address format. Please enter a valid IPv4 address (e.g., 192.168.1.100) or hostname"
    }
    
    /**
     * Get the device's local IP address
     */
    fun getLocalIpAddress(): String? {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress.indexOf(':') < 0) {
                        val ip = inetAddress.hostAddress
                        // Prefer WiFi IP addresses (usually 192.168.x.x)
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            Log.d(TAG, "Found local IP: $ip")
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.message}")
        }
        return null
    }
    
    /**
     * Test MQTT broker connectivity with detailed error reporting
     */
    fun testBrokerConnectivity(host: String, port: Int, timeout: Int = 5000): BrokerTestResult {
        return try {
            // First validate the IP address
            if (!isValidIpAddress(host)) {
                val error = getIpValidationError(host)
                return BrokerTestResult(false, "Invalid IP Address", error ?: "Invalid IP address format")
            }
            
            // Validate port
            if (port <= 0 || port > 65535) {
                return BrokerTestResult(false, "Invalid Port", "Port must be between 1 and 65535")
            }
            
            // Test network connectivity
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeout)
            socket.close()
            
            Log.i(TAG, "Broker connectivity test successful: $host:$port")
            BrokerTestResult(true, "Connected", "Successfully connected to $host:$port")
            
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> 
                    "Connection timeout. Please check if the MQTT broker is running on $host:$port"
                e.message?.contains("refused", ignoreCase = true) == true -> 
                    "Connection refused. No MQTT broker listening on $host:$port"
                e.message?.contains("unreachable", ignoreCase = true) == true -> 
                    "Host unreachable. Please check the IP address and network connection"
                e.message?.contains("no route", ignoreCase = true) == true -> 
                    "No route to host. Please check your network configuration"
                else -> "Connection failed: ${e.message}"
            }
            
            Log.w(TAG, "Broker connectivity test failed: $host:$port - ${e.message}")
            BrokerTestResult(false, "Connection Failed", errorMessage)
        }
    }
    
    /**
     * Result class for broker connectivity tests
     */
    data class BrokerTestResult(
        val isSuccess: Boolean,
        val status: String,
        val message: String
    )
    
    /**
     * Get recommended broker URL based on network configuration
     */
    fun getRecommendedBrokerUrl(): String {
        val localIp = getLocalIpAddress()
        return if (localIp != null) {
            "tcp://$localIp:1883"
        } else {
            // Fallback to localhost if local IP not found
            "tcp://localhost:1883"
        }
    }
    
    /**
     * Check if device is on WiFi network
     */
    fun isOnWifiNetwork(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Get network type description
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "No Network"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }
    }
    
    /**
     * Get network quality indicator
     */
    fun getNetworkQuality(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "Poor"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Poor"
        
        return when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "Excellent"
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "Good"
            else -> "Poor"
        }
    }
    
    /**
     * Get network information summary
     */
    fun getNetworkInfo(context: Context): Map<String, String> {
        return mapOf(
            "type" to getNetworkType(context),
            "quality" to getNetworkQuality(context),
            "local_ip" to (getLocalIpAddress() ?: "Unknown"),
            "recommended_broker" to getRecommendedBrokerUrl()
        )
    }
}
