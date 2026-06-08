package com.bharath.carcrashdetection.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Service for handling ESP32 communication via Bluetooth Classic and BLE
 * Supports both connection types for maximum compatibility
 */
class Esp32BluetoothService(private val context: Context) {
    
    companion object {
        private const val TAG = "Esp32BluetoothService"
        private const val IMPACT_THRESHOLD = 5.0f // Adjust based on ESP32 calibration
        
        // ESP32 Service UUIDs - MUST MATCH ESP32 CODE
        private val ESP32_SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val ESP32_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        
        // Bluetooth Classic UUID for ESP32
        private val ESP32_CLASSIC_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
    
    // Connection states
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED_CLASSIC,
        CONNECTED_BLE,
        ERROR
    }
    
    // Sensor data from ESP32
    data class SensorData(
        val accelerometerX: Float,
        val accelerometerY: Float,
        val accelerometerZ: Float,
        val impactForce: Float,
        val latitude: Double?,
        val longitude: Double?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // State flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _sensorData = MutableStateFlow<SensorData?>(null)
    val sensorData: StateFlow<SensorData?> = _sensorData
    
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices
    
    // Bluetooth components
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    // Thread pool for background operations
    private val executor = Executors.newCachedThreadPool()

    // BroadcastReceiver for device discovery and bonding events.
    // Declared before init {} so it is initialized when registerBluetoothReceiver() runs.
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        Log.d(TAG, "Found device: ${it.name ?: "Unknown"} (${it.address})")
                        val currentDevices = _discoveredDevices.value.toMutableList()
                        if (!currentDevices.contains(it)) {
                            currentDevices.add(it)
                            _discoveredDevices.value = currentDevices
                            Log.i(TAG, "Added device to discovered list: ${it.name}")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.i(TAG, "Bluetooth discovery started")
                    _discoveredDevices.value = emptyList() // Clear previous discoveries
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.i(TAG, "Bluetooth discovery finished. Found ${_discoveredDevices.value.size} devices")
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                    Log.d(TAG, "Bond state changed for ${device?.name}: $bondState")
                }
            }
        }
    }

    init {
        initializeBluetooth()
        registerBluetoothReceiver()
    }

    private fun initializeBluetooth() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(bluetoothReceiver, filter)
        }
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Start device discovery
     */
    fun startDiscovery() {
        Log.d(TAG, "=== BLUETOOTH DISCOVERY DEBUG ===")
        Log.d(TAG, "Bluetooth enabled: ${isBluetoothEnabled()}")
        Log.d(TAG, "Bluetooth adapter: ${bluetoothAdapter}")
        
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not enabled")
            return
        }
        
        // Check all required permissions
        val scanPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
        val connectPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        val locationPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        
        Log.d(TAG, "BLUETOOTH_SCAN permission: ${if (scanPermission == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        Log.d(TAG, "BLUETOOTH_CONNECT permission: ${if (connectPermission == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        Log.d(TAG, "ACCESS_FINE_LOCATION permission: ${if (locationPermission == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        
        if (scanPermission != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Bluetooth scan permission not granted")
            return
        }

        if (locationPermission != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted - required for Bluetooth scanning")
            return
        }

        // Clear previous discoveries
        _discoveredDevices.value = emptyList()
        Log.i(TAG, "Cleared previous device discoveries")

        executor.execute {
            try {
                Log.d(TAG, "Starting Bluetooth discovery...")
                bluetoothAdapter?.startDiscovery()
                Log.i(TAG, "Started Bluetooth device discovery successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start discovery: ${e.message}", e)
            }
        }
    }
    
    /**
     * Stop device discovery
     */
    fun stopDiscovery() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        try {
            bluetoothAdapter?.cancelDiscovery()
            Log.i(TAG, "Stopped Bluetooth device discovery")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery: ${e.message}")
        }
    }
    
    /**
     * Connect to ESP32 device using Bluetooth Classic
     */
    fun connectClassic(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connecting to a device")
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        
        executor.execute {
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Bluetooth connect permission not granted")
                    _connectionState.value = ConnectionState.ERROR
                    return@execute
                }
                
                bluetoothSocket = device.createRfcommSocketToServiceRecord(ESP32_CLASSIC_UUID)
                bluetoothSocket?.connect()
                
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                
                _connectionState.value = ConnectionState.CONNECTED_CLASSIC
                Log.i(TAG, "Connected to ESP32 via Bluetooth Classic")
                
                // Start reading sensor data
                startReadingSensorData()
                
            } catch (e: IOException) {
                Log.e(TAG, "Failed to connect via Bluetooth Classic: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
                disconnect()
            }
        }
    }
    
    /**
     * Connect to ESP32 device using BLE
     */
    fun connectBLE(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connecting to a device")
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        
        executor.execute {
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Bluetooth connect permission not granted")
                    _connectionState.value = ConnectionState.ERROR
                    return@execute
                }
                
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect via BLE: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
                disconnect()
            }
        }
    }
    
    /**
     * BLE GATT callback
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.i(TAG, "BLE connected to ESP32")
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt?.discoverServices()
                    }
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.i(TAG, "BLE disconnected from ESP32")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(ESP32_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(ESP32_CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.setCharacteristicNotification(characteristic, true)
                        _connectionState.value = ConnectionState.CONNECTED_BLE
                        Log.i(TAG, "BLE services discovered and notifications enabled")
                    }
                }
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let { char ->
                if (char.uuid == ESP32_CHARACTERISTIC_UUID) {
                    val data = char.value
                    parseSensorData(data)
                }
            }
        }
    }
    
    /**
     * Start reading sensor data from Bluetooth Classic connection
     */
    private fun startReadingSensorData() {
        executor.execute {
            val buffer = ByteArray(1024)
            while (_connectionState.value == ConnectionState.CONNECTED_CLASSIC) {
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
     * Parse sensor data from ESP32
     * Expected format: "ACC:x,y,z|IMPACT:force|GPS:lat,lon"
     */
    private fun parseSensorData(data: ByteArray) {
        try {
            val message = String(data)
            Log.d(TAG, "Received sensor data: $message")
            
            val sensorData = parseSensorMessage(message)
            _sensorData.value = sensorData
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sensor data: ${e.message}")
        }
    }
    
    /**
     * Parse sensor message from ESP32
     */
    private fun parseSensorMessage(message: String): SensorData {
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
        
        return SensorData(
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
                when (_connectionState.value) {
                    ConnectionState.CONNECTED_CLASSIC -> {
                        outputStream?.write(command.toByteArray())
                        outputStream?.flush()
                    }
                    ConnectionState.CONNECTED_BLE -> {
                        val service = bluetoothGatt?.getService(ESP32_SERVICE_UUID)
                        val characteristic = service?.getCharacteristic(ESP32_CHARACTERISTIC_UUID)
                        
                        if (characteristic != null && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            characteristic.value = command.toByteArray()
                            bluetoothGatt?.writeCharacteristic(characteristic)
                        }
                    }
                    else -> {
                        Log.w(TAG, "Not connected to ESP32")
                    }
                }
                Log.d(TAG, "Sent command to ESP32: $command")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command: ${e.message}")
            }
        }
    }
    
    /**
     * Disconnect from ESP32
     */
    fun disconnect() {
        try {
            bluetoothSocket?.close()
            bluetoothGatt?.close()
            inputStream?.close()
            outputStream?.close()
            
            bluetoothSocket = null
            bluetoothGatt = null
            inputStream = null
            outputStream = null
            
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.i(TAG, "Disconnected from ESP32")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect: ${e.message}")
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            disconnect()
            context.unregisterReceiver(bluetoothReceiver)
            executor.shutdown()
            Log.i(TAG, "Bluetooth service cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
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
    fun getCurrentSensorData(): SensorData? {
        return _sensorData.value
    }
    

}
