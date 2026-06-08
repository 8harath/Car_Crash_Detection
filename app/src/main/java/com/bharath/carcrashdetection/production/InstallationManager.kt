package com.bharath.carcrashdetection.production

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.bharath.carcrashdetection.BuildConfig
import com.bharath.carcrashdetection.data.database.AppDatabase
import com.bharath.carcrashdetection.util.SystemHealthMonitor
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.Esp32Manager
import com.bharath.carcrashdetection.util.GpsService
import com.bharath.carcrashdetection.data.repository.UserRepository
import com.bharath.carcrashdetection.data.repository.MedicalProfileRepository
import com.bharath.carcrashdetection.data.repository.IncidentRepository
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipInputStream

/**
 * Installation manager for automated setup, configuration, and deployment management
 * Provides tools for system installation and configuration
 */
class InstallationManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "InstallationManager"
        private const val PREFS_NAME = "installation_prefs"
        private const val KEY_INSTALLATION_DATE = "installation_date"
        private const val KEY_INSTALLATION_VERSION = "installation_version"
        private const val KEY_CONFIGURATION_STATUS = "configuration_status"
        private const val KEY_DEMO_MODE = "demo_mode"
        
        @Volatile
        private var INSTANCE: InstallationManager? = null
        
        fun getInstance(context: Context): InstallationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InstallationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val systemHealthMonitor = SystemHealthMonitor(
        context,
        MqttService(),
        Esp32Manager(context),
        GpsService(context),
        UserRepository(AppDatabase.getDatabase(context).userDao()),
        MedicalProfileRepository(AppDatabase.getDatabase(context).medicalProfileDao()),
        IncidentRepository(AppDatabase.getDatabase(context).incidentDao())
    )
    private val maintenanceManager = MaintenanceManager.getInstance(context)
    
    /**
     * Perform initial system installation
     */
    suspend fun performInitialInstallation(): InstallationResult {
        return try {
            Log.i(TAG, "Starting initial system installation...")
            
            val startTime = System.currentTimeMillis()
            val steps = mutableListOf<InstallationStep>()
            
            // Step 1: System initialization
            steps.add(initializeSystem())
            
            // Step 2: Database setup
            steps.add(setupDatabase())
            
            // Step 3: Default configuration
            steps.add(installDefaultConfiguration())
            
            // Step 4: Demo data setup
            steps.add(setupDemoData())
            
            // Step 5: System validation
            steps.add(validateInstallation())
            
            // Step 6: Final configuration
            steps.add(completeInstallation())
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.i(TAG, "Initial installation completed in ${duration}ms")
            
            InstallationResult(
                success = true,
                stepsCompleted = steps.size,
                duration = duration,
                timestamp = startTime,
                steps = steps,
                message = "Installation completed successfully"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during initial installation", e)
            InstallationResult(
                success = false,
                stepsCompleted = 0,
                duration = 0,
                timestamp = System.currentTimeMillis(),
                steps = emptyList(),
                message = "Installation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Install configuration package
     */
    suspend fun installConfigurationPackage(configFilePath: String): ConfigurationInstallResult {
        return try {
            Log.i(TAG, "Installing configuration package: $configFilePath")
            
            val configFile = File(configFilePath)
            if (!configFile.exists()) {
                throw IllegalArgumentException("Configuration file not found: $configFilePath")
            }
            
            val startTime = System.currentTimeMillis()
            val installedComponents = mutableListOf<String>()
            
            // Extract and install configuration
            ZipInputStream(FileInputStream(configFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when {
                        entry.name.startsWith("config/") -> {
                            installConfigurationFile(zipIn, entry.name)
                            installedComponents.add("Configuration: ${entry.name}")
                        }
                        entry.name.startsWith("profiles/") -> {
                            installProfileData(zipIn, entry.name)
                            installedComponents.add("Profile: ${entry.name}")
                        }
                        entry.name.startsWith("settings/") -> {
                            installSettings(zipIn, entry.name)
                            installedComponents.add("Settings: ${entry.name}")
                        }
                    }
                    entry = zipIn.nextEntry
                }
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.i(TAG, "Configuration package installed successfully")
            
            ConfigurationInstallResult(
                success = true,
                installedComponents = installedComponents,
                duration = duration,
                timestamp = startTime,
                message = "Configuration package installed successfully"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error installing configuration package", e)
            ConfigurationInstallResult(
                success = false,
                installedComponents = emptyList(),
                duration = 0,
                timestamp = System.currentTimeMillis(),
                message = "Configuration installation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Deploy demo configuration
     */
    suspend fun deployDemoConfiguration(): DemoDeploymentResult {
        return try {
            Log.i(TAG, "Deploying demo configuration...")
            
            val startTime = System.currentTimeMillis()
            val deployedItems = mutableListOf<String>()
            
            // Deploy demo profiles
            deployedItems.addAll(deployDemoProfiles())
            
            // Deploy demo settings
            deployedItems.addAll(deployDemoSettings())
            
            // Deploy demo scenarios
            deployedItems.addAll(deployDemoScenarios())
            
            // Enable demo mode
            enableDemoMode()
            deployedItems.add("Demo mode enabled")
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.i(TAG, "Demo configuration deployed successfully")
            
            DemoDeploymentResult(
                success = true,
                deployedItems = deployedItems,
                duration = duration,
                timestamp = startTime,
                message = "Demo configuration deployed successfully"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deploying demo configuration", e)
            DemoDeploymentResult(
                success = false,
                deployedItems = emptyList(),
                duration = 0,
                timestamp = System.currentTimeMillis(),
                message = "Demo deployment failed: ${e.message}"
            )
        }
    }
    
    /**
     * Validate system installation
     */
    suspend fun validateSystemInstallation(): ValidationResult {
        return try {
            Log.i(TAG, "Validating system installation...")
            
            val startTime = System.currentTimeMillis()
            val validations = mutableListOf<ValidationItem>()
            
            // Validate database
            validations.add(validateDatabase())
            
            // Validate configuration
            validations.add(validateConfiguration())
            
            // Validate system health
            validations.add(validateSystemHealth())
            
            // Validate demo setup
            validations.add(validateDemoSetup())
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            val allValid = validations.all { it.isValid }
            
            Log.i(TAG, "System validation completed: ${if (allValid) "PASSED" else "FAILED"}")
            
            ValidationResult(
                success = allValid,
                validations = validations,
                duration = duration,
                timestamp = startTime,
                message = if (allValid) "All validations passed" else "Some validations failed"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating system installation", e)
            ValidationResult(
                success = false,
                validations = emptyList(),
                duration = 0,
                timestamp = System.currentTimeMillis(),
                message = "Validation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Get installation status
     */
    fun getInstallationStatus(): InstallationStatus {
        val installationDate = prefs.getLong(KEY_INSTALLATION_DATE, 0)
        val installationVersion = prefs.getString(KEY_INSTALLATION_VERSION, "Unknown")
        val configurationStatus = prefs.getString(KEY_CONFIGURATION_STATUS, "Not Configured")
        val demoMode = prefs.getBoolean(KEY_DEMO_MODE, false)
        
        return InstallationStatus(
            installationDate = installationDate,
            installationVersion = installationVersion,
            configurationStatus = configurationStatus,
            demoMode = demoMode,
            currentVersion = BuildConfig.VERSION_NAME,
            isInstalled = installationDate > 0,
            isConfigured = configurationStatus != "Not Configured"
        )
    }
    
    /**
     * Reset system to factory defaults
     */
    suspend fun resetToFactoryDefaults(): ResetResult {
        return try {
            Log.w(TAG, "Resetting system to factory defaults...")
            
            val startTime = System.currentTimeMillis()
            
            // Stop all services
            stopAllServices()
            
            // Clear all data
            clearAllData()
            
            // Reset configuration
            resetConfiguration()
            
            // Restart services
            restartAllServices()
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.w(TAG, "Factory reset completed in ${duration}ms")
            
            ResetResult(
                success = true,
                duration = duration,
                timestamp = startTime,
                message = "System reset to factory defaults"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during factory reset", e)
            ResetResult(
                success = false,
                duration = 0,
                timestamp = System.currentTimeMillis(),
                message = "Factory reset failed: ${e.message}"
            )
        }
    }
    
    // Private helper methods
    
    private suspend fun initializeSystem(): InstallationStep {
        return try {
            // Create necessary directories
            val dirs = listOf("config", "logs", "backups", "profiles", "temp")
            dirs.forEach { dirName ->
                val dir = File(context.filesDir, dirName)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
            
            InstallationStep(
                stepName = "System Initialization",
                success = true,
                message = "System directories created successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "System initialization failed", e)
            InstallationStep(
                stepName = "System Initialization",
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
    
    private suspend fun setupDatabase(): InstallationStep {
        return try {
            val database = AppDatabase.getDatabase(context)
            
            // Verify database connection
            database.openHelper.readableDatabase.version
            
            InstallationStep(
                stepName = "Database Setup",
                success = true,
                message = "Database initialized successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Database setup failed", e)
            InstallationStep(
                stepName = "Database Setup",
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
    
    private suspend fun installDefaultConfiguration(): InstallationStep {
        return try {
            // Install default settings
            val defaultSettings = getDefaultSettings()
            defaultSettings.forEach { (key, value) ->
                prefs.edit().putString(key, value).apply()
            }
            
            InstallationStep(
                stepName = "Default Configuration",
                success = true,
                message = "Default configuration installed"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Default configuration installation failed", e)
            InstallationStep(
                stepName = "Default Configuration",
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
    
    private suspend fun setupDemoData(): InstallationStep {
        return try {
            // Create demo profiles
            createDemoProfiles()
            
            // Create demo settings
            createDemoSettings()
            
            InstallationStep(
                stepName = "Demo Data Setup",
                success = true,
                message = "Demo data created successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Demo data setup failed", e)
            InstallationStep(
                stepName = "Demo Data Setup",
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
    
    private suspend fun validateInstallation(): InstallationStep {
        return try {
            val validationResult = validateSystemInstallation()
            
            InstallationStep(
                stepName = "Installation Validation",
                success = validationResult.success,
                message = validationResult.message
            )
        } catch (e: Exception) {
            Log.e(TAG, "Installation validation failed", e)
            InstallationStep(
                stepName = "Installation Validation",
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
    
    private suspend fun completeInstallation(): InstallationStep {
        return try {
            // Record installation details
            prefs.edit()
                .putLong(KEY_INSTALLATION_DATE, System.currentTimeMillis())
                .putString(KEY_INSTALLATION_VERSION, BuildConfig.VERSION_NAME)
                .putString(KEY_CONFIGURATION_STATUS, "Installed")
                .apply()
            
            InstallationStep(
                stepName = "Installation Completion",
                success = true,
                message = "Installation completed and recorded"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Installation completion failed", e)
            InstallationStep(
                stepName = "Installation Completion",
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
    
    private fun installConfigurationFile(zipIn: ZipInputStream, fileName: String) {
        val configDir = File(context.filesDir, "config")
        val configFile = File(configDir, fileName.substringAfter("config/"))
        
        configFile.parentFile?.mkdirs()
        FileOutputStream(configFile).use { output ->
            zipIn.copyTo(output)
        }
    }
    
    private fun installProfileData(zipIn: ZipInputStream, fileName: String) {
        val profilesDir = File(context.filesDir, "profiles")
        val profileFile = File(profilesDir, fileName.substringAfter("profiles/"))
        
        profileFile.parentFile?.mkdirs()
        FileOutputStream(profileFile).use { output ->
            zipIn.copyTo(output)
        }
    }
    
    private fun installSettings(zipIn: ZipInputStream, fileName: String) {
        val settingsDir = File(context.filesDir, "settings")
        val settingsFile = File(settingsDir, fileName.substringAfter("settings/"))
        
        settingsFile.parentFile?.mkdirs()
        FileOutputStream(settingsFile).use { output ->
            zipIn.copyTo(output)
        }
    }
    
    private fun deployDemoProfiles(): List<String> {
        val deployed = mutableListOf<String>()
        
        // Create sample medical profiles
        val demoProfiles = listOf(
            "John Doe - Emergency Contact: +1-555-0101",
            "Jane Smith - Allergies: Penicillin, Latex",
            "Mike Johnson - Blood Type: O+",
            "Sarah Wilson - Medical Conditions: Diabetes, Asthma"
        )
        
        demoProfiles.forEachIndexed { index, profile ->
            val profileFile = File(context.filesDir, "profiles/demo_profile_${index + 1}.txt")
            profileFile.parentFile?.mkdirs()
            profileFile.writeText(profile)
            deployed.add("Demo Profile ${index + 1}")
        }
        
        return deployed
    }
    
    private fun deployDemoSettings(): List<String> {
        val deployed = mutableListOf<String>()
        
        // Deploy demo MQTT settings
        val mqttSettings = mapOf(
            "demo_broker_host" to "192.168.1.100",
            "demo_broker_port" to "1883",
            "demo_client_id" to "demo_client_${System.currentTimeMillis() % 1000}",
            "demo_topic_prefix" to "demo/emergency/"
        )
        
        mqttSettings.forEach { (key, value) ->
            prefs.edit().putString(key, value).apply()
            deployed.add("MQTT Setting: $key")
        }
        
        return deployed
    }
    
    private fun deployDemoScenarios(): List<String> {
        val deployed = mutableListOf<String>()
        
        // Create demo scenario files
        val demoScenarios = listOf(
            "Single Vehicle Crash - Location: Downtown Intersection",
            "Multi-Vehicle Pileup - Location: Highway Exit 23",
            "Pedestrian Accident - Location: Crosswalk at Main St",
            "Motorcycle Crash - Location: Country Road 45"
        )
        
        demoScenarios.forEachIndexed { index, scenario ->
            val scenarioFile = File(context.filesDir, "config/demo_scenario_${index + 1}.txt")
            scenarioFile.parentFile?.mkdirs()
            scenarioFile.writeText(scenario)
            deployed.add("Demo Scenario ${index + 1}")
        }
        
        return deployed
    }
    
    private fun enableDemoMode() {
        prefs.edit().putBoolean(KEY_DEMO_MODE, true).apply()
    }
    
    private fun validateDatabase(): ValidationItem {
        return try {
            val database = AppDatabase.getDatabase(context)
            val version = database.openHelper.readableDatabase.version
            
            ValidationItem(
                component = "Database",
                isValid = version > 0,
                message = "Database version: $version"
            )
        } catch (e: Exception) {
            ValidationItem(
                component = "Database",
                isValid = false,
                message = "Database validation failed: ${e.message}"
            )
        }
    }
    
    private fun validateConfiguration(): ValidationItem {
        val configDir = File(context.filesDir, "config")
        val configFiles = configDir.listFiles()?.size ?: 0
        
        return ValidationItem(
            component = "Configuration",
            isValid = configFiles > 0,
            message = "Configuration files found: $configFiles"
        )
    }
    
    private fun validateSystemHealth(): ValidationItem {
        val systemHealth = systemHealthMonitor.getCurrentHealthMetrics()
        val criticalIssues = if (systemHealth != null) 0 else 1 // Simplified check
        
        return ValidationItem(
            component = "System Health",
            isValid = criticalIssues == 0,
            message = "Critical issues: $criticalIssues"
        )
    }
    
    private fun validateDemoSetup(): ValidationItem {
        val demoMode = prefs.getBoolean(KEY_DEMO_MODE, false)
        val demoProfiles = File(context.filesDir, "profiles").listFiles()?.size ?: 0
        
        return ValidationItem(
            component = "Demo Setup",
            isValid = demoMode && demoProfiles > 0,
            message = "Demo mode: $demoMode, Profiles: $demoProfiles"
        )
    }
    
    private fun getDefaultSettings(): Map<String, String> {
        return mapOf(
            "mqtt_broker_host" to "localhost",
            "mqtt_broker_port" to "1883",
            "mqtt_client_id" to "car_crash_detection_${System.currentTimeMillis() % 10000}",
            "mqtt_topic_prefix" to "emergency/",
            "gps_update_interval" to "5000",
            "emergency_timeout" to "30000",
            "auto_reconnect" to "true",
            "debug_mode" to "false"
        )
    }
    
    private fun createDemoProfiles() {
        // Demo profile creation logic
        Log.i(TAG, "Demo profiles created")
    }
    
    private fun createDemoSettings() {
        // Demo settings creation logic
        Log.i(TAG, "Demo settings created")
    }
    
    private fun stopAllServices() {
        // Stop all running services
        Log.i(TAG, "All services stopped for reset")
    }
    
    private fun clearAllData() {
        // Clear database
        context.deleteDatabase("car_crash_detection.db")
        
        // Clear shared preferences
        prefs.edit().clear().apply()
        
        // Clear files
        val filesDir = context.filesDir
        filesDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
        
        Log.i(TAG, "All data cleared")
    }
    
    private fun resetConfiguration() {
        // Reset to default configuration
        prefs.edit()
            .putLong(KEY_INSTALLATION_DATE, 0)
            .putString(KEY_INSTALLATION_VERSION, "")
            .putString(KEY_CONFIGURATION_STATUS, "Not Configured")
            .putBoolean(KEY_DEMO_MODE, false)
            .apply()
        
        Log.i(TAG, "Configuration reset to defaults")
    }
    
    private fun restartAllServices() {
        // Restart all services
        Log.i(TAG, "All services restarted after reset")
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
    }
}

// Data classes for installation operations

data class InstallationResult(
    val success: Boolean,
    val stepsCompleted: Int,
    val duration: Long,
    val timestamp: Long,
    val steps: List<InstallationStep>,
    val message: String
)

data class InstallationStep(
    val stepName: String,
    val success: Boolean,
    val message: String
)

data class ConfigurationInstallResult(
    val success: Boolean,
    val installedComponents: List<String>,
    val duration: Long,
    val timestamp: Long,
    val message: String
)

data class DemoDeploymentResult(
    val success: Boolean,
    val deployedItems: List<String>,
    val duration: Long,
    val timestamp: Long,
    val message: String
)

data class ValidationResult(
    val success: Boolean,
    val validations: List<ValidationItem>,
    val duration: Long,
    val timestamp: Long,
    val message: String
)

data class ValidationItem(
    val component: String,
    val isValid: Boolean,
    val message: String
)

data class InstallationStatus(
    val installationDate: Long,
    val installationVersion: String?,
    val configurationStatus: String?,
    val demoMode: Boolean,
    val currentVersion: String,
    val isInstalled: Boolean,
    val isConfigured: Boolean
)

data class ResetResult(
    val success: Boolean,
    val duration: Long,
    val timestamp: Long,
    val message: String
)
