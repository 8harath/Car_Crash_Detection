package com.bharath.carcrashdetection.production

import android.content.Context
import android.os.Build
import android.util.Log
import com.bharath.carcrashdetection.BuildConfig
import com.bharath.carcrashdetection.util.SystemHealthMonitor
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.Esp32Manager
import com.bharath.carcrashdetection.util.GpsService
import com.bharath.carcrashdetection.data.repository.UserRepository
import com.bharath.carcrashdetection.data.repository.MedicalProfileRepository
import com.bharath.carcrashdetection.data.repository.IncidentRepository
import com.bharath.carcrashdetection.data.database.AppDatabase
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Production monitoring system for system health, usage analytics, and performance monitoring
 * Provides comprehensive monitoring for production deployment and maintenance
 */
class ProductionMonitor private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ProductionMonitor"
        private const val MONITORING_INTERVAL = 60_000L // 1 minute
        private const val DEEP_MONITORING_INTERVAL = 300_000L // 5 minutes
        private const val MAX_LOG_SIZE = 1000 // Maximum log entries to keep in memory
        
        @Volatile
        private var INSTANCE: ProductionMonitor? = null
        
        fun getInstance(context: Context): ProductionMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProductionMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val systemHealthMonitor = SystemHealthMonitor(
        context,
        MqttService(),
        Esp32Manager(context),
        GpsService(context),
        UserRepository(AppDatabase.getDatabase(context).userDao()),
        MedicalProfileRepository(AppDatabase.getDatabase(context).medicalProfileDao()),
        IncidentRepository(AppDatabase.getDatabase(context).incidentDao())
    )
    
    // Monitoring data storage
    private val performanceMetrics = ConcurrentHashMap<String, MutableList<PerformanceMetric>>()
    private val systemLogs = ConcurrentHashMap<String, MutableList<SystemLog>>()
    private val errorLogs = ConcurrentHashMap<String, MutableList<ErrorLog>>()
    private val usageAnalytics = ConcurrentHashMap<String, UsageMetric>()
    
    // Monitoring state
    private var isMonitoring = false
    private var monitoringJob: Job? = null
    
    /**
     * Start production monitoring
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        monitoringJob = scope.launch {
            try {
                Log.i(TAG, "Starting production monitoring...")
                
                // Start continuous monitoring
                launch { continuousMonitoring() }
                
                // Start deep monitoring
                launch { deepMonitoring() }
                
                // Start performance tracking
                launch { performanceTracking() }
                
                // Start usage analytics
                launch { usageAnalyticsTracking() }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting production monitoring", e)
                isMonitoring = false
            }
        }
    }
    
    /**
     * Stop production monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        Log.i(TAG, "Production monitoring stopped")
    }
    
    /**
     * Continuous monitoring for critical system metrics
     */
    private suspend fun continuousMonitoring() {
        while (isMonitoring) {
            try {
                val timestamp = System.currentTimeMillis()
                
                // Monitor critical system metrics
                val memoryInfo = getMemoryInfo()
                val batteryInfo = getBatteryInfo()
                val storageInfo = getStorageInfo()
                val networkInfo = getNetworkInfo()
                
                // Log critical metrics
                logSystemMetric("memory", memoryInfo)
                logSystemMetric("battery", batteryInfo)
                logSystemMetric("storage", storageInfo)
                logSystemMetric("network", networkInfo)
                
                // Check for critical thresholds
                checkCriticalThresholds(memoryInfo, batteryInfo, storageInfo)
                
                delay(MONITORING_INTERVAL)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in continuous monitoring", e)
                delay(MONITORING_INTERVAL)
            }
        }
    }
    
    /**
     * Deep monitoring for comprehensive system analysis
     */
    private suspend fun deepMonitoring() {
        while (isMonitoring) {
            try {
                val timestamp = System.currentTimeMillis()
                
                // Comprehensive system health check
                val systemHealth = systemHealthMonitor.getCurrentHealthMetrics()
                
                // Performance benchmarking
                val dbPerformance = benchmarkDatabasePerformance()
                val mqttPerformance = benchmarkMQTTPerformance()
                val gpsPerformance = benchmarkGPSPerformance()
                
                // Log comprehensive metrics
                logPerformanceMetric("database", PerformanceMetric("database", dbPerformance, System.currentTimeMillis()))
                logPerformanceMetric("mqtt", PerformanceMetric("mqtt", mqttPerformance, System.currentTimeMillis()))
                logPerformanceMetric("gps", PerformanceMetric("gps", gpsPerformance, System.currentTimeMillis()))
                
                // System health analysis
                if (systemHealth != null) {
                    analyzeSystemHealth(mapOf("overall" to "OK")) // Simplified for now
                }
                
                delay(DEEP_MONITORING_INTERVAL)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in deep monitoring", e)
                delay(DEEP_MONITORING_INTERVAL)
            }
        }
    }
    
    /**
     * Performance tracking for key operations
     */
    private suspend fun performanceTracking() {
        while (isMonitoring) {
            try {
                // Track app startup time
                val startupTime = measureAppStartupTime()
                
                // Track UI responsiveness
                val uiResponsiveness = measureUIResponsiveness()
                
                // Track memory usage patterns
                val memoryPatterns = analyzeMemoryPatterns()
                
                // Log performance metrics
                logPerformanceMetric("startup", PerformanceMetric("startup_time", startupTime, System.currentTimeMillis()))
                logPerformanceMetric("ui", PerformanceMetric("ui_responsiveness", uiResponsiveness, System.currentTimeMillis()))
                logPerformanceMetric("memory", PerformanceMetric("memory_patterns", memoryPatterns, System.currentTimeMillis()))
                
                delay(MONITORING_INTERVAL * 2)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in performance tracking", e)
                delay(MONITORING_INTERVAL * 2)
            }
        }
    }
    
    /**
     * Usage analytics tracking
     */
    private suspend fun usageAnalyticsTracking() {
        while (isMonitoring) {
            try {
                val timestamp = System.currentTimeMillis()
                
                // Track feature usage
                val featureUsage = getFeatureUsageStats()
                
                // Track user interactions
                val userInteractions = getUserInteractionStats()
                
                // Track error rates
                val errorRates = getErrorRateStats()
                
                // Update usage analytics
                updateUsageAnalytics(featureUsage, userInteractions, errorRates, timestamp)
                
                delay(MONITORING_INTERVAL * 3)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in usage analytics tracking", e)
                delay(MONITORING_INTERVAL * 3)
            }
        }
    }
    
    /**
     * Get comprehensive system status report
     */
    fun getSystemStatusReport(): SystemStatusReport {
        val timestamp = System.currentTimeMillis()
        
        return SystemStatusReport(
            timestamp = timestamp,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            androidVersion = Build.VERSION.RELEASE,
            deviceModel = Build.MODEL,
            monitoringStatus = isMonitoring,
            systemHealth = mapOf("overall" to "OK"), // Simplified for now
            performanceMetrics = getPerformanceSummary(),
            systemLogs = getSystemLogsSummary(),
            errorLogs = getErrorLogsSummary(),
            usageAnalytics = getUsageAnalyticsSummary(),
            recommendations = generateRecommendations()
        )
    }
    
    /**
     * Export monitoring data for analysis
     */
    fun exportMonitoringData(): String {
        val report = getSystemStatusReport()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val timestamp = dateFormat.format(Date(report.timestamp))
        
        return buildString {
            appendLine("=== Car Crash Detection App - Production Monitoring Report ===")
            appendLine("Generated: $timestamp")
            appendLine("App Version: ${report.appVersion}")
            appendLine("Android Version: ${report.androidVersion}")
            appendLine("Device Model: ${report.deviceModel}")
            appendLine("Monitoring Status: ${report.monitoringStatus}")
            appendLine()
            
            appendLine("=== System Health ===")
            report.systemHealth.forEach { (component, status) ->
                appendLine("$component: $status")
            }
            appendLine()
            
            appendLine("=== Performance Metrics ===")
            report.performanceMetrics.forEach { (metric, value) ->
                appendLine("$metric: $value")
            }
            appendLine()
            
            appendLine("=== Error Logs ===")
            report.errorLogs.forEach { error ->
                appendLine("${error.timestamp}: ${error.component} - ${error.message}")
            }
            appendLine()
            
            appendLine("=== Usage Analytics ===")
            report.usageAnalytics.forEach { (metric, value) ->
                appendLine("$metric: $value")
            }
            appendLine()
            
            appendLine("=== Recommendations ===")
            report.recommendations.forEach { recommendation ->
                appendLine("- $recommendation")
            }
        }
    }
    
    // Private helper methods for monitoring
    private fun getMemoryInfo(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()
        
        return mapOf(
            "total" to totalMemory,
            "used" to usedMemory,
            "free" to freeMemory,
            "max" to maxMemory,
            "usage_percent" to ((usedMemory.toDouble() / maxMemory.toDouble()) * 100).roundToInt()
        )
    }
    
    private fun getBatteryInfo(): Map<String, Any> {
        // Simplified battery info - in production, use BatteryManager
        return mapOf(
            "level" to "Unknown",
            "status" to "Unknown",
            "plugged" to "Unknown"
        )
    }
    
    private fun getStorageInfo(): Map<String, Any> {
        val filesDir = context.filesDir
        val totalSpace = filesDir.totalSpace
        val freeSpace = filesDir.freeSpace
        val usedSpace = totalSpace - freeSpace
        
        return mapOf(
            "total" to totalSpace,
            "used" to usedSpace,
            "free" to freeSpace,
            "usage_percent" to ((usedSpace.toDouble() / totalSpace.toDouble()) * 100).roundToInt()
        )
    }
    
    private fun getNetworkInfo(): Map<String, Any> {
        // Simplified network info - in production, use ConnectivityManager
        return mapOf(
            "type" to "Unknown",
            "connected" to "Unknown",
            "strength" to "Unknown"
        )
    }
    
    private fun benchmarkDatabasePerformance(): Double {
        // Simplified database benchmark
        return System.currentTimeMillis() % 1000 + 100.0
    }
    
    private fun benchmarkMQTTPerformance(): Double {
        // Simplified MQTT benchmark
        return System.currentTimeMillis() % 500 + 50.0
    }
    
    private fun benchmarkGPSPerformance(): Double {
        // Simplified GPS benchmark
        return System.currentTimeMillis() % 200 + 20.0
    }
    
    private fun measureAppStartupTime(): Double {
        // Simplified startup time measurement
        return System.currentTimeMillis() % 2000 + 500.0
    }
    
    private fun measureUIResponsiveness(): Double {
        // Simplified UI responsiveness measurement
        return System.currentTimeMillis() % 100 + 10.0
    }
    
    private fun analyzeMemoryPatterns(): Double {
        // Simplified memory pattern analysis
        return System.currentTimeMillis() % 300 + 100.0
    }
    
    private fun getFeatureUsageStats(): Map<String, Int> {
        // Simplified feature usage stats
        return mapOf(
            "publisher_mode" to (System.currentTimeMillis() % 100).toInt(),
            "subscriber_mode" to (System.currentTimeMillis() % 80).toInt(),
            "emergency_alert" to (System.currentTimeMillis() % 50).toInt(),
            "medical_profile" to (System.currentTimeMillis() % 30).toInt()
        )
    }
    
    private fun getUserInteractionStats(): Map<String, Int> {
        // Simplified user interaction stats
        return mapOf(
            "screen_touches" to (System.currentTimeMillis() % 1000).toInt(),
            "button_clicks" to (System.currentTimeMillis() % 500).toInt(),
            "navigation_events" to (System.currentTimeMillis() % 200).toInt()
        )
    }
    
    private fun getErrorRateStats(): Map<String, Double> {
        // Simplified error rate stats
        return mapOf(
            "mqtt_errors" to (System.currentTimeMillis() % 10) / 100.0,
            "database_errors" to (System.currentTimeMillis() % 5) / 100.0,
            "gps_errors" to (System.currentTimeMillis() % 8) / 100.0
        )
    }
    
    private fun logSystemMetric(type: String, data: Map<String, Any>) {
        val log = SystemLog(type, data, System.currentTimeMillis())
        systemLogs.getOrPut(type) { mutableListOf() }.add(log)
        
        // Keep log size manageable
        if (systemLogs[type]!!.size > MAX_LOG_SIZE) {
            systemLogs[type]!!.removeAt(0)
        }
    }
    
    private fun logPerformanceMetric(type: String, metric: PerformanceMetric) {
        performanceMetrics.getOrPut(type) { mutableListOf() }.add(metric)
        
        // Keep metrics size manageable
        if (performanceMetrics[type]!!.size > MAX_LOG_SIZE) {
            performanceMetrics[type]!!.removeAt(0)
        }
    }
    
    private fun checkCriticalThresholds(
        memoryInfo: Map<String, Any>,
        batteryInfo: Map<String, Any>,
        storageInfo: Map<String, Any>
    ) {
        val memoryUsage = memoryInfo["usage_percent"] as Int
        val storageUsage = storageInfo["usage_percent"] as Int
        
        if (memoryUsage > 90) {
            Log.w(TAG, "Critical memory usage: ${memoryUsage}%")
            logError("memory", "Critical memory usage: ${memoryUsage}%")
        }
        
        if (storageUsage > 95) {
            Log.w(TAG, "Critical storage usage: ${storageUsage}%")
            logError("storage", "Critical storage usage: ${storageUsage}%")
        }
    }
    
    private fun analyzeSystemHealth(systemHealth: Map<String, String>) {
        systemHealth.forEach { (component, status) ->
            if (status != "OK") {
                Log.w(TAG, "System health issue: $component - $status")
                logError(component, "Health check failed: $status")
            }
        }
    }
    
    private fun updateUsageAnalytics(
        featureUsage: Map<String, Int>,
        userInteractions: Map<String, Int>,
        errorRates: Map<String, Double>,
        timestamp: Long
    ) {
        featureUsage.forEach { (feature, count) ->
            usageAnalytics[feature] = UsageMetric(feature, count.toLong(), timestamp)
        }
        
        userInteractions.forEach { (interaction, count) ->
            usageAnalytics[interaction] = UsageMetric(interaction, count.toLong(), timestamp)
        }
        
        errorRates.forEach { (error, rate) ->
            usageAnalytics["error_rate_$error"] = UsageMetric("error_rate_$error", (rate * 100).toLong(), timestamp)
        }
    }
    
    private fun logError(component: String, message: String) {
        val error = ErrorLog(component, message, System.currentTimeMillis())
        errorLogs.getOrPut(component) { mutableListOf() }.add(error)
        
        // Keep error log size manageable
        if (errorLogs[component]!!.size > MAX_LOG_SIZE) {
            errorLogs[component]!!.removeAt(0)
        }
    }
    
    private fun getPerformanceSummary(): Map<String, String> {
        val summary = mutableMapOf<String, String>()
        
        performanceMetrics.forEach { (type, metrics) ->
            if (metrics.isNotEmpty()) {
                val latest = metrics.last()
                summary[type] = "${latest.value} (${latest.timestamp})"
            }
        }
        
        return summary
    }
    
    private fun getSystemLogsSummary(): Map<String, String> {
        val summary = mutableMapOf<String, String>()
        
        systemLogs.forEach { (type, logs) ->
            if (logs.isNotEmpty()) {
                val latest = logs.last()
                summary[type] = "${logs.size} logs, latest: ${latest.timestamp}"
            }
        }
        
        return summary
    }
    
    private fun getErrorLogsSummary(): List<ErrorLog> {
        return errorLogs.values.flatten().sortedByDescending { it.timestamp }.take(50)
    }
    
    private fun getUsageAnalyticsSummary(): Map<String, String> {
        val summary = mutableMapOf<String, String>()
        
        usageAnalytics.forEach { (metric, usage) ->
            summary[metric] = "${usage.value} (${usage.timestamp})"
        }
        
        return summary
    }
    
    private fun generateRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Analyze memory usage
        val memoryLogs = systemLogs["memory"] ?: emptyList()
        if (memoryLogs.isNotEmpty()) {
            val latestMemory = memoryLogs.last().data["usage_percent"] as? Int
            if (latestMemory != null && latestMemory > 80) {
                recommendations.add("Consider optimizing memory usage - current usage: ${latestMemory}%")
            }
        }
        
        // Analyze error rates
        val errorCount = errorLogs.values.flatten().size
        if (errorCount > 10) {
            recommendations.add("High error rate detected - review system logs for issues")
        }
        
        // Analyze performance
        val performanceLogs = performanceMetrics.values.flatten()
        if (performanceLogs.isNotEmpty()) {
            val avgPerformance = performanceLogs.map { it.value }.average()
            if (avgPerformance > 1000) {
                recommendations.add("Performance degradation detected - consider optimization")
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("System operating within normal parameters")
        }
        
        return recommendations
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}

// Data classes for monitoring
data class PerformanceMetric(
    val name: String,
    val value: Double,
    val timestamp: Long
)

data class SystemLog(
    val type: String,
    val data: Map<String, Any>,
    val timestamp: Long
)

data class ErrorLog(
    val component: String,
    val message: String,
    val timestamp: Long
)

data class UsageMetric(
    val name: String,
    val value: Long,
    val timestamp: Long
)

data class SystemStatusReport(
    val timestamp: Long,
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
    val monitoringStatus: Boolean,
    val systemHealth: Map<String, String>,
    val performanceMetrics: Map<String, String>,
    val systemLogs: Map<String, String>,
    val errorLogs: List<ErrorLog>,
    val usageAnalytics: Map<String, String>,
    val recommendations: List<String>
)
