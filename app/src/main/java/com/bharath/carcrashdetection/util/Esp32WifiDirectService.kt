package com.bharath.carcrashdetection.util

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * WiFi Direct service for ESP32 communication as fallback
 * Provides peer-to-peer WiFi communication when Bluetooth is unavailable
 */
class Esp32WifiDirectService(private val context: Context) {
    
    companion object {
        private const val TAG = "Esp32WifiDirectService"
        private const val PORT = 8888
        private const val IMPACT_THRESHOLD = 5.0f // Same as Bluetooth service
    }
    
    // Connection states
    enum class ConnectionState {
        DISCONNECTED,
        DISCOVERING,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    // WiFi P2P components
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    // State flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _discoveredDevices = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<WifiP2pDevice>> = _discoveredDevices
    
    private val _sensorData = MutableStateFlow<Esp32BluetoothService.SensorData?>(null)
    val sensorData: StateFlow<Esp32BluetoothService.SensorData?> = _sensorData
    
    // Thread pool for background operations
    private val executor = Executors.newCachedThreadPool()
    
    // Broadcast receiver for WiFi P2P events
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Log.i(TAG, "WiFi P2P is enabled")
                    } else {
                        Log.w(TAG, "WiFi P2P is disabled")
                        _connectionState.value = ConnectionState.ERROR
                    }
                }
                
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    context?.let { ctx ->
                        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            Log.w(TAG, "Location permission not granted for WiFi P2P")
                            return
                        }
                        
                        wifiP2pManager?.requestPeers(wifiP2pChannel, object : WifiP2pManager.PeerListListener {
                            override fun onPeersAvailable(peers: WifiP2pDeviceList?) {
                                val deviceList = peers?.deviceList?.toList() ?: emptyList()
                                _discoveredDevices.value = deviceList
                                Log.i(TAG, "Discovered ${deviceList.size} WiFi P2P devices")
                            }
                        })
                    }
                }
                
                "android.net.wifi.p2p.CONNECTION_STATE_CHANGE" -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        Log.i(TAG, "WiFi P2P connected")
                        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
                    } else {
                        Log.i(TAG, "WiFi P2P disconnected")
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        }
    }
    
    // Connection info listener
    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info: WifiP2pInfo ->
        if (info.groupFormed) {
            if (info.isGroupOwner) {
                // This device is the group owner (server)
                startServer()
            } else {
                // This device is the client
                connectToServer(info.groupOwnerAddress.hostAddress)
            }
        }
    }
    
    init {
        initializeWifiP2P()
    }
    
    private fun initializeWifiP2P() {
        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiP2pChannel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        
        if (wifiP2pManager == null || wifiP2pChannel == null) {
            Log.e(TAG, "WiFi P2P not supported on this device")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        
        // Register broadcast receiver
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction("android.net.wifi.p2p.CONNECTION_STATE_CHANGE")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
    }
    
    /**
     * Check if WiFi P2P is supported and enabled
     */
    fun isWifiP2PEnabled(): Boolean {
        return wifiP2pManager != null && wifiP2pChannel != null
    }
    
    /**
     * Start device discovery
     */
    fun startDiscovery() {
        if (!isWifiP2PEnabled()) {
            Log.w(TAG, "WiFi P2P not available")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted for WiFi P2P discovery")
            return
        }
        
        _connectionState.value = ConnectionState.DISCOVERING
        
        wifiP2pManager?.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Started WiFi P2P device discovery")
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to start WiFi P2P discovery: $reason")
                _connectionState.value = ConnectionState.ERROR
            }
        })
    }
    
    /**
     * Stop device discovery
     */
    fun stopDiscovery() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        wifiP2pManager?.stopPeerDiscovery(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Stopped WiFi P2P device discovery")
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to stop WiFi P2P discovery: $reason")
            }
        })
    }
    
    /**
     * Connect to ESP32 device
     */
    fun connectToDevice(device: WifiP2pDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connecting to a device")
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        
        wifiP2pManager?.connect(wifiP2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Initiating connection to ESP32 via WiFi P2P")
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to connect to ESP32 via WiFi P2P: $reason")
                _connectionState.value = ConnectionState.ERROR
            }
        })
    }
    
    /**
     * Start server (when this device is group owner)
     */
    private fun startServer() {
        executor.execute {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "WiFi P2P server started on port $PORT")
                
                val client = serverSocket?.accept()
                if (client != null) {
                    clientSocket = client
                    inputStream = client.inputStream
                    outputStream = client.outputStream
                    
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.i(TAG, "WiFi P2P client connected")
                    
                    // Start reading sensor data
                    startReadingSensorData()
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start WiFi P2P server: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }
    
    /**
     * Connect to server (when ESP32 is group owner)
     */
    private fun connectToServer(serverAddress: String?) {
        if (serverAddress == null) {
            Log.e(TAG, "Server address is null")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        
        executor.execute {
            try {
                clientSocket = Socket()
                clientSocket?.connect(InetSocketAddress(serverAddress, PORT), 5000)
                
                inputStream = clientSocket?.inputStream
                outputStream = clientSocket?.outputStream
                
                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "Connected to WiFi P2P server at $serverAddress")
                
                // Start reading sensor data
                startReadingSensorData()
                
            } catch (e: IOException) {
                Log.e(TAG, "Failed to connect to WiFi P2P server: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }
    
    /**
     * Start reading sensor data from WiFi connection
     */
    private fun startReadingSensorData() {
        executor.execute {
            val buffer = ByteArray(1024)
            while (_connectionState.value == ConnectionState.CONNECTED) {
                try {
                    val bytes = inputStream?.read(buffer)
                    if (bytes != null && bytes > 0) {
                        val data = buffer.copyOf(bytes)
                        parseSensorData(data)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading sensor data: ${e.message}")
                    break
                }
            }
        }
    }
    
    /**
     * Parse sensor data from ESP32 (same format as Bluetooth)
     */
    private fun parseSensorData(data: ByteArray) {
        try {
            val message = String(data)
            Log.d(TAG, "Received sensor data via WiFi: $message")
            
            val sensorData = parseSensorMessage(message)
            _sensorData.value = sensorData
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sensor data: ${e.message}")
        }
    }
    
    /**
     * Parse sensor message from ESP32 (same as Bluetooth service)
     */
    private fun parseSensorMessage(message: String): Esp32BluetoothService.SensorData {
        var accX = 0f
        var accY = 0f
        var accZ = 0f
        var impactForce = 0f
        var latitude: Double? = null
        var longitude: Double? = null
        
        val parts = message.split("|")
        for (part in parts) {
            when {
                part.startsWith("ACC:") -> {
                    val accValues = part.substring(4).split(",")
                    if (accValues.size >= 3) {
                        accX = accValues[0].toFloatOrNull() ?: 0f
                        accY = accValues[1].toFloatOrNull() ?: 0f
                        accZ = accValues[2].toFloatOrNull() ?: 0f
                    }
                }
                part.startsWith("IMPACT:") -> {
                    impactForce = part.substring(7).toFloatOrNull() ?: 0f
                }
                part.startsWith("GPS:") -> {
                    val gpsValues = part.substring(4).split(",")
                    if (gpsValues.size >= 2) {
                        latitude = gpsValues[0].toDoubleOrNull()
                        longitude = gpsValues[1].toDoubleOrNull()
                    }
                }
            }
        }
        
        return Esp32BluetoothService.SensorData(
            accelerometerX = accX,
            accelerometerY = accY,
            accelerometerZ = accZ,
            impactForce = impactForce,
            latitude = latitude,
            longitude = longitude
        )
    }
    
    /**
     * Send command to ESP32
     */
    fun sendCommand(command: String) {
        executor.execute {
            try {
                outputStream?.write(command.toByteArray())
                outputStream?.flush()
                Log.d(TAG, "Sent command to ESP32 via WiFi: $command")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command via WiFi: ${e.message}")
            }
        }
    }
    
    /**
     * Disconnect from ESP32
     */
    fun disconnect() {
        try {
            serverSocket?.close()
            clientSocket?.close()
            inputStream?.close()
            outputStream?.close()
            
            serverSocket = null
            clientSocket = null
            inputStream = null
            outputStream = null
            
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.i(TAG, "Disconnected from ESP32 via WiFi P2P")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during WiFi P2P disconnect: ${e.message}")
        }
    }
    
    /**
     * Check if impact detected (crash detection)
     */
    fun isImpactDetected(): Boolean {
        val data = _sensorData.value
        return data?.impactForce ?: 0f > IMPACT_THRESHOLD
    }
    
    /**
     * Get current sensor data
     */
    fun getCurrentSensorData(): Esp32BluetoothService.SensorData? {
        return _sensorData.value
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        disconnect()
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
    

}
