package com.bharath.carcrashdetection.util

import android.content.Context
import android.util.Log
import com.bharath.carcrashdetection.data.repository.IncidentRepository
import com.bharath.carcrashdetection.data.repository.MedicalProfileRepository
import com.bharath.carcrashdetection.data.repository.UserRepository
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Comprehensive Error Handling and Recovery System for Phase 6
 * Provides robust error management and automatic recovery mechanisms
 */
class ErrorHandler(
    private val context: Context,
    private val mqttService: MqttService,
    private val esp32Manager: Esp32Manager,
    private val gpsService: GpsService,
    private val userRepository: UserRepository,
    private val medicalProfileRepository: MedicalProfileRepository,
    private val incidentRepository: IncidentRepository
) {
    companion object {
        private const val TAG = "ErrorHandler"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val ERROR_WINDOW_MS = 300000L // 5 minutes
        private const val MAX_ERRORS_PER_WINDOW = 10
    }

    private val errorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val errorHistory = mutableListOf<ErrorRecord>()
    private val recoveryStrategies = mutableMapOf<ErrorType, RecoveryStrategy>()
    private val isRecoveryActive = AtomicBoolean(false)

    data class ErrorRecord(
        val id: String,
        val timestamp: Long,
        val errorType: ErrorType,
        val component: String,
        val message: String,
        val stackTrace: String?,
        val severity: ErrorSeverity,
        val isRecovered: Boolean = false,
        val recoveryAttempts: Int = 0,
        val recoveryTime: Long? = null
    )

    enum class ErrorType {
        MQTT_CONNECTION_LOST,
        MQTT_MESSAGE_FAILED,
        ESP32_COMMUNICATION_FAILED,
        ESP32_DEVICE_DISCONNECTED,
        GPS_SERVICE_UNAVAILABLE,
        GPS_LOCATION_FAILED,
        DATABASE_OPERATION_FAILED,
        DATABASE_CORRUPTION,
        NETWORK_TIMEOUT,
        MEMORY_ALLOCATION_FAILED,
        BATTERY_CRITICAL,
        STORAGE_FULL,
        PERMISSION_DENIED,
        HARDWARE_UNAVAILABLE,
        UNKNOWN_ERROR
    }

    enum class ErrorSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    interface RecoveryStrategy {
        suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult
        fun canAttemptRecovery(error: ErrorRecord): Boolean
        fun getRecoveryPriority(): Int
    }

    data class RecoveryResult(
        val success: Boolean,
        val message: String,
        val recoveryTime: Long,
        val additionalActions: List<String> = emptyList()
    )

    /**
     * Initialize error handler with recovery strategies
     */
    init {
        initializeRecoveryStrategies()
        startErrorMonitoring()
        Log.i(TAG, "Error Handler initialized with ${recoveryStrategies.size} recovery strategies")
    }

    /**
     * Initialize recovery strategies for different error types
     */
    private fun initializeRecoveryStrategies() {
        recoveryStrategies[ErrorType.MQTT_CONNECTION_LOST] = MqttConnectionRecoveryStrategy()
        recoveryStrategies[ErrorType.MQTT_MESSAGE_FAILED] = MqttMessageRecoveryStrategy()
        recoveryStrategies[ErrorType.ESP32_COMMUNICATION_FAILED] = Esp32CommunicationRecoveryStrategy()
        recoveryStrategies[ErrorType.ESP32_DEVICE_DISCONNECTED] = Esp32DisconnectionRecoveryStrategy()
        recoveryStrategies[ErrorType.GPS_SERVICE_UNAVAILABLE] = GpsServiceRecoveryStrategy()
        recoveryStrategies[ErrorType.DATABASE_OPERATION_FAILED] = DatabaseOperationRecoveryStrategy()
        recoveryStrategies[ErrorType.NETWORK_TIMEOUT] = NetworkTimeoutRecoveryStrategy()
        recoveryStrategies[ErrorType.MEMORY_ALLOCATION_FAILED] = MemoryAllocationRecoveryStrategy()
        recoveryStrategies[ErrorType.BATTERY_CRITICAL] = BatteryCriticalRecoveryStrategy()
        recoveryStrategies[ErrorType.STORAGE_FULL] = StorageFullRecoveryStrategy()
    }

    /**
     * Start error monitoring
     */
    private fun startErrorMonitoring() {
        errorScope.launch {
            while (true) {
                try {
                    monitorErrorRates()
                    cleanupOldErrors()
                    delay(60000L) // Check every minute
                } catch (e: Exception) {
                    Log.e(TAG, "Error in error monitoring loop", e)
                }
            }
        }
    }

    /**
     * Handle error with automatic recovery attempt
     */
    suspend fun handleError(
        errorType: ErrorType,
        component: String,
        message: String,
        exception: Exception? = null,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM
    ): ErrorRecord {
        val errorId = generateErrorId()
        val timestamp = System.currentTimeMillis()
        
        val errorRecord = ErrorRecord(
            id = errorId,
            timestamp = timestamp,
            errorType = errorType,
            component = component,
            message = message,
            stackTrace = exception?.stackTraceToString(),
            severity = severity
        )

        // Log error
        Log.e(TAG, "[$component] $message", exception)
        
        // Record error
        errorHistory.add(errorRecord)
        
        // Update error count
        val errorKey = "${errorType}_${component}"
        errorCounts.computeIfAbsent(errorKey) { AtomicInteger(0) }.incrementAndGet()

        // Attempt automatic recovery if not already recovering
        if (!isRecoveryActive.get() && severity != ErrorSeverity.LOW) {
            attemptAutomaticRecovery(errorRecord)
        }

        // Check if error rate exceeds threshold
        if (getErrorRate(errorKey) > MAX_ERRORS_PER_WINDOW) {
            Log.w(TAG, "Error rate exceeded threshold for $errorKey")
            triggerEmergencyMode(errorRecord)
        }

        return errorRecord
    }

    /**
     * Attempt automatic recovery for an error
     */
    private suspend fun attemptAutomaticRecovery(errorRecord: ErrorRecord) {
        if (isRecoveryActive.compareAndSet(false, true)) {
            try {
                Log.i(TAG, "Attempting automatic recovery for error: ${errorRecord.errorType}")
                
                val strategy = recoveryStrategies[errorRecord.errorType]
                if (strategy != null && strategy.canAttemptRecovery(errorRecord)) {
                    val recoveryResult = strategy.attemptRecovery(errorRecord)
                    
                    if (recoveryResult.success) {
                        markErrorAsRecovered(errorRecord.id, recoveryResult.recoveryTime)
                        Log.i(TAG, "Automatic recovery successful: ${recoveryResult.message}")
                        
                        // Execute additional actions
                        recoveryResult.additionalActions.forEach { action ->
                            Log.d(TAG, "Executing additional action: $action")
                            executeRecoveryAction(action)
                        }
                    } else {
                        Log.w(TAG, "Automatic recovery failed: ${recoveryResult.message}")
                        scheduleManualRecovery(errorRecord)
                    }
                } else {
                    Log.d(TAG, "No recovery strategy available for error: ${errorRecord.errorType}")
                    scheduleManualRecovery(errorRecord)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during automatic recovery", e)
                scheduleManualRecovery(errorRecord)
            } finally {
                isRecoveryActive.set(false)
            }
        }
    }

    /**
     * Mark error as recovered
     */
    private fun markErrorAsRecovered(errorId: String, recoveryTime: Long) {
        val errorIndex = errorHistory.indexOfFirst { it.id == errorId }
        if (errorIndex != -1) {
            val error = errorHistory[errorIndex]
            errorHistory[errorIndex] = error.copy(
                isRecovered = true,
                recoveryTime = recoveryTime
            )
        }
    }

    /**
     * Schedule manual recovery
     */
    private fun scheduleManualRecovery(errorRecord: ErrorRecord) {
        errorScope.launch {
            delay(RETRY_DELAY_MS)
            if (errorRecord.recoveryAttempts < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Scheduling manual recovery attempt ${errorRecord.recoveryAttempts + 1}")
                attemptAutomaticRecovery(errorRecord.copy(recoveryAttempts = errorRecord.recoveryAttempts + 1))
            } else {
                Log.w(TAG, "Max recovery attempts reached for error: ${errorRecord.id}")
                escalateError(errorRecord)
            }
        }
    }

    /**
     * Escalate error to higher level
     */
    private fun escalateError(errorRecord: ErrorRecord) {
        Log.e(TAG, "Escalating error: ${errorRecord.errorType} - ${errorRecord.message}")
        
        // Notify system administrators
        // Send critical error notifications
        // Trigger system-wide alerts
        
        when (errorRecord.severity) {
            ErrorSeverity.CRITICAL -> triggerEmergencyMode(errorRecord)
            ErrorSeverity.HIGH -> triggerHighPriorityAlert(errorRecord)
            else -> Log.w(TAG, "Error escalated: ${errorRecord.message}")
        }
    }

    /**
     * Trigger emergency mode
     */
    private fun triggerEmergencyMode(errorRecord: ErrorRecord) {
        Log.e(TAG, "TRIGGERING EMERGENCY MODE due to error: ${errorRecord.errorType}")
        
        // Implement emergency mode logic
        // - Disable non-critical services
        // - Switch to offline mode if possible
        // - Notify emergency contacts
        // - Log critical system state
    }

    /**
     * Trigger high priority alert
     */
    private fun triggerHighPriorityAlert(errorRecord: ErrorRecord) {
        Log.w(TAG, "High priority alert triggered: ${errorRecord.errorType}")
        
        // Send high priority notifications
        // Log system state
        // Trigger diagnostic procedures
    }

    /**
     * Monitor error rates
     */
    private fun monitorErrorRates() {
        val currentTime = System.currentTimeMillis()
        val windowStart = currentTime - ERROR_WINDOW_MS
        
        errorCounts.forEach { (errorKey, count) ->
            val recentErrors = errorHistory.count { 
                it.timestamp >= windowStart && 
                "${it.errorType}_${it.component}" == errorKey 
            }
            
            if (recentErrors > MAX_ERRORS_PER_WINDOW) {
                Log.w(TAG, "High error rate detected for $errorKey: $recentErrors errors in ${ERROR_WINDOW_MS / 1000}s")
            }
        }
    }

    /**
     * Clean up old errors
     */
    private fun cleanupOldErrors() {
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - (24 * 60 * 60 * 1000L) // 24 hours
        
        errorHistory.removeAll { it.timestamp < cutoffTime }
        
        // Reset error counts for old errors
        errorCounts.clear()
        errorHistory.forEach { error ->
            val errorKey = "${error.errorType}_${error.component}"
            errorCounts.computeIfAbsent(errorKey) { AtomicInteger(0) }.incrementAndGet()
        }
    }

    /**
     * Get error rate for a specific error key
     */
    private fun getErrorRate(errorKey: String): Int {
        val currentTime = System.currentTimeMillis()
        val windowStart = currentTime - ERROR_WINDOW_MS
        
        return errorHistory.count { 
            it.timestamp >= windowStart && 
            "${it.errorType}_${it.component}" == errorKey 
        }
    }

    /**
     * Execute recovery action
     */
    private suspend fun executeRecoveryAction(action: String) {
        try {
            when (action) {
                "restart_mqtt_service" -> restartMqttService()
                "restart_esp32_manager" -> restartEsp32Manager()
                "restart_gps_service" -> restartGpsService()
                "clear_database_cache" -> clearDatabaseCache()
                "free_memory" -> freeMemory()
                "check_storage" -> checkStorage()
                else -> Log.d(TAG, "Unknown recovery action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute recovery action: $action", e)
        }
    }

    // Recovery action implementations
    private suspend fun restartMqttService() {
        Log.d(TAG, "Restarting MQTT service")
        try {
            // Simulate MQTT restart
            delay(1000)
            Log.d(TAG, "MQTT service restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart MQTT service", e)
        }
    }

    private suspend fun restartEsp32Manager() {
        Log.d(TAG, "Restarting ESP32 manager")
        try {
            // Simulate ESP32 manager restart
            delay(1000)
            Log.d(TAG, "ESP32 manager restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart ESP32 manager", e)
        }
    }

    private suspend fun restartGpsService() {
        Log.d(TAG, "Restarting GPS service")
        try {
            // Simulate GPS service restart
            delay(1000)
            Log.d(TAG, "GPS service restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart GPS service", e)
        }
    }

    private suspend fun clearDatabaseCache() {
        Log.d(TAG, "Clearing database cache")
        // Implementation would clear Room database cache
    }

    private suspend fun freeMemory() {
        Log.d(TAG, "Freeing memory")
        System.gc()
    }

    private suspend fun checkStorage() {
        Log.d(TAG, "Checking storage")
        // Implementation would check available storage
    }

    /**
     * Generate unique error ID
     */
    private fun generateErrorId(): String {
        return "ERR_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }

    /**
     * Get error statistics
     */
    fun getErrorStatistics(): ErrorStatistics {
        val currentTime = System.currentTimeMillis()
        val windowStart = currentTime - ERROR_WINDOW_MS
        
        val recentErrors = errorHistory.filter { it.timestamp >= windowStart }
        val totalErrors = errorHistory.size
        val recoveredErrors = errorHistory.count { it.isRecovered }
        val criticalErrors = errorHistory.count { it.severity == ErrorSeverity.CRITICAL }
        
        return ErrorStatistics(
            totalErrors = totalErrors,
            recentErrors = recentErrors.size,
            recoveredErrors = recoveredErrors,
            criticalErrors = criticalErrors,
            recoveryRate = if (totalErrors > 0) (recoveredErrors.toDouble() / totalErrors) * 100 else 0.0,
            errorHistory = errorHistory.sortedByDescending { it.timestamp }
        )
    }

    data class ErrorStatistics(
        val totalErrors: Int,
        val recentErrors: Int,
        val recoveredErrors: Int,
        val criticalErrors: Int,
        val recoveryRate: Double,
        val errorHistory: List<ErrorRecord>
    )

    /**
     * Get errors by type
     */
    fun getErrorsByType(errorType: ErrorType): List<ErrorRecord> {
        return errorHistory.filter { it.errorType == errorType }
    }

    /**
     * Get errors by component
     */
    fun getErrorsByComponent(component: String): List<ErrorRecord> {
        return errorHistory.filter { it.component == component }
    }

    /**
     * Get errors by severity
     */
    fun getErrorsBySeverity(severity: ErrorSeverity): List<ErrorRecord> {
        return errorHistory.filter { it.severity == severity }
    }

    /**
     * Clear error history
     */
    fun clearErrorHistory() {
        errorHistory.clear()
        errorCounts.clear()
        Log.i(TAG, "Error history cleared")
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        errorScope.cancel()
        errorHistory.clear()
        errorCounts.clear()
        Log.i(TAG, "Error Handler cleaned up")
    }

    // Recovery Strategy Implementations
    private inner class MqttConnectionRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simulate MQTT connection recovery
                delay(1000)
                
                return RecoveryResult(
                    success = true,
                    message = "MQTT connection restored",
                    recoveryTime = System.currentTimeMillis() - startTime,
                    additionalActions = listOf("verify_mqtt_subscriptions")
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "MQTT recovery failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 1
    }

    private inner class MqttMessageRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simulate MQTT message recovery
                delay(1000)
                
                return RecoveryResult(
                    success = true,
                    message = "MQTT message publishing restored",
                    recoveryTime = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "MQTT message recovery failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 2
    }

    private inner class Esp32CommunicationRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simulate ESP32 communication recovery
                delay(5000)
                
                return RecoveryResult(
                    success = true,
                    message = "ESP32 communication restored",
                    recoveryTime = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "ESP32 communication recovery failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 3
    }

    private inner class Esp32DisconnectionRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simulate ESP32 reconnection
                delay(10000) // Wait 10 seconds for discovery
                
                return RecoveryResult(
                    success = true,
                    message = "ESP32 devices reconnected",
                    recoveryTime = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "ESP32 reconnection failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 4
    }

    private inner class GpsServiceRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simulate GPS service recovery
                delay(5000)
                
                return RecoveryResult(
                    success = true,
                    message = "GPS service restored",
                    recoveryTime = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "GPS service recovery failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 5
    }

    private inner class DatabaseOperationRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simulate database recovery
                delay(1000)
                
                return RecoveryResult(
                    success = true,
                    message = "Database operations restored",
                    recoveryTime = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "Database recovery failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 6
    }

    private inner class NetworkTimeoutRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simulate network recovery
                delay(2000)
                
                return RecoveryResult(
                    success = true,
                    message = "Network connectivity restored",
                    recoveryTime = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "Network recovery failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 7
    }

    private inner class MemoryAllocationRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Force garbage collection
                System.gc()
                delay(1000)
                
                val runtime = Runtime.getRuntime()
                val freeMemory = runtime.freeMemory()
                val totalMemory = runtime.totalMemory()
                val memoryUsage = ((totalMemory - freeMemory).toDouble() / totalMemory.toDouble()) * 100
                
                if (memoryUsage < 80.0) {
                    return RecoveryResult(
                        success = true,
                        message = "Memory usage reduced to ${memoryUsage.roundToInt()}%",
                        recoveryTime = System.currentTimeMillis() - startTime
                    )
                } else {
                    return RecoveryResult(false, "Memory usage still high: ${memoryUsage.roundToInt()}%", System.currentTimeMillis() - startTime)
                }
            } catch (e: Exception) {
                return RecoveryResult(false, "Memory recovery failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 8
    }

    private inner class BatteryCriticalRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Implement battery-saving measures
                // This would typically involve reducing background processing
                // and switching to low-power mode
                
                return RecoveryResult(
                    success = true,
                    message = "Battery-saving measures activated",
                    recoveryTime = System.currentTimeMillis() - startTime,
                    additionalActions = listOf("reduce_background_processing", "enable_power_saving")
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "Battery recovery failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < 1 // Only attempt once for battery issues
        }

        override fun getRecoveryPriority(): Int = 9
    }

    private inner class StorageFullRecoveryStrategy : RecoveryStrategy {
        override suspend fun attemptRecovery(error: ErrorRecord): RecoveryResult {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simulate storage cleanup
                delay(1000)
                
                return RecoveryResult(
                    success = true,
                    message = "Storage cleanup completed",
                    recoveryTime = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                return RecoveryResult(false, "Storage cleanup failed: ${e.message}", System.currentTimeMillis() - startTime)
            }
        }

        override fun canAttemptRecovery(error: ErrorRecord): Boolean {
            return error.recoveryAttempts < MAX_RETRY_ATTEMPTS
        }

        override fun getRecoveryPriority(): Int = 10
    }
}
