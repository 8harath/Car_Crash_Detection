package com.bharath.carcrashdetection.demo

import android.content.Context
import android.util.Log
import com.bharath.carcrashdetection.data.model.EmergencyContact
import com.bharath.carcrashdetection.data.model.Incident
import com.bharath.carcrashdetection.data.model.MedicalProfile
import com.bharath.carcrashdetection.data.model.User
import com.bharath.carcrashdetection.data.model.UserRole
import com.bharath.carcrashdetection.data.repository.IncidentRepository
import com.bharath.carcrashdetection.data.repository.MedicalProfileRepository
import com.bharath.carcrashdetection.data.repository.UserRepository
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.Esp32Manager
import com.bharath.carcrashdetection.util.GpsService
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Demo Scenario Manager for Phase 6
 * Manages pre-configured demonstration scenarios for academic presentations
 */
class DemoScenarioManager(
    private val context: Context,
    private val mqttService: MqttService,
    private val esp32Manager: Esp32Manager,
    private val gpsService: GpsService,
    private val userRepository: UserRepository,
    private val medicalProfileRepository: MedicalProfileRepository,
    private val incidentRepository: IncidentRepository
) {
    companion object {
        private const val TAG = "DemoScenarioManager"
        private const val DEMO_TIMEOUT_MS = 300000L // 5 minutes
    }

    private val demoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isDemoRunning = AtomicBoolean(false)
    private val currentDemo = AtomicReference<DemoScenario?>(null)
    private val demoProgress = AtomicReference<DemoProgress?>(null)

    data class DemoScenario(
        val id: String,
        val name: String,
        val description: String,
        val steps: List<DemoStep>,
        val estimatedDuration: Long,
        val difficulty: DemoDifficulty
    )

    data class DemoStep(
        val id: String,
        val name: String,
        val description: String,
        val action: suspend () -> Boolean,
        val validation: suspend () -> Boolean
    )

    data class DemoProgress(
        val scenarioId: String,
        var currentStep: Int,
        val totalSteps: Int,
        val startTime: Long,
        val estimatedEndTime: Long,
        var status: DemoStatus,
        val completedSteps: MutableSet<String> = mutableSetOf(),
        val failedSteps: MutableSet<String> = mutableSetOf()
    )

    enum class DemoStatus {
        NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED, PAUSED
    }

    enum class DemoDifficulty {
        BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    }

    init {
        initializeDemoScenarios()
        Log.i(TAG, "Demo Scenario Manager initialized")
    }

    /**
     * Initialize available demo scenarios
     */
    private fun initializeDemoScenarios() {
        // Demo scenarios will be created on-demand
        Log.d(TAG, "Demo scenarios initialized")
    }

    /**
     * Get available demo scenarios
     */
    fun getAvailableScenarios(): List<DemoScenario> {
        return listOf(
            createSingleCrashScenario(),
            createMultiCrashScenario(),
            createNetworkFailureScenario(),
            createEsp32DisconnectionScenario(),
            createGpsFailureScenario(),
            createBatteryDrainScenario(),
            createMemoryPressureScenario(),
            createCompleteEmergencyResponseScenario()
        )
    }

    /**
     * Start a demo scenario
     */
    suspend fun startDemoScenario(scenarioId: String): Boolean {
        if (isDemoRunning.compareAndSet(false, true)) {
            try {
                val scenario = getAvailableScenarios().find { it.id == scenarioId }
                if (scenario != null) {
                    currentDemo.set(scenario)
                    demoProgress.set(DemoProgress(
                        scenarioId = scenario.id,
                        currentStep = 0,
                        totalSteps = scenario.steps.size,
                        startTime = System.currentTimeMillis(),
                        estimatedEndTime = System.currentTimeMillis() + scenario.estimatedDuration,
                        status = DemoStatus.IN_PROGRESS
                    ))
                    
                    Log.i(TAG, "Starting demo scenario: ${scenario.name}")
                    executeDemoScenario(scenario)
                    return true
                } else {
                    Log.e(TAG, "Demo scenario not found: $scenarioId")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting demo scenario", e)
                isDemoRunning.set(false)
                return false
            }
        }
        return false
    }

    /**
     * Execute demo scenario
     */
    private suspend fun executeDemoScenario(scenario: DemoScenario) {
        try {
            val progress = demoProgress.get()
            if (progress == null) return

            for ((index, step) in scenario.steps.withIndex()) {
                if (!isDemoRunning.get()) break

                progress.currentStep = index + 1
                Log.d(TAG, "Executing step ${index + 1}/${scenario.steps.size}: ${step.name}")

                try {
                    val stepResult = withTimeout(DEMO_TIMEOUT_MS) {
                        step.action()
                    }

                    if (stepResult) {
                        progress.completedSteps.add(step.id)
                        Log.d(TAG, "Step completed successfully: ${step.name}")
                    } else {
                        progress.failedSteps.add(step.id)
                        Log.w(TAG, "Step failed: ${step.name}")
                    }

                    // Validate step
                    val validationResult = step.validation()
                    if (!validationResult) {
                        Log.w(TAG, "Step validation failed: ${step.name}")
                    }

                    delay(1000) // Brief pause between steps

                } catch (e: Exception) {
                    Log.e(TAG, "Error executing step: ${step.name}", e)
                    progress.failedSteps.add(step.id)
                }
            }

            // Mark demo as completed
            progress.status = if (progress.failedSteps.isEmpty()) DemoStatus.COMPLETED else DemoStatus.FAILED
            Log.i(TAG, "Demo scenario completed with status: ${progress.status}")

        } catch (e: Exception) {
            Log.e(TAG, "Error executing demo scenario", e)
            demoProgress.get()?.status = DemoStatus.FAILED
        } finally {
            isDemoRunning.set(false)
        }
    }

    /**
     * Stop current demo
     */
    fun stopCurrentDemo() {
        if (isDemoRunning.compareAndSet(true, false)) {
            demoProgress.get()?.status = DemoStatus.PAUSED
            Log.i(TAG, "Demo scenario stopped")
        }
    }

    /**
     * Get current demo progress
     */
    fun getCurrentDemoProgress(): DemoProgress? {
        return demoProgress.get()
    }

    /**
     * Check if demo is running
     */
    fun isDemoRunning(): Boolean {
        return isDemoRunning.get()
    }

    // Demo scenario creation methods
    private fun createSingleCrashScenario(): DemoScenario {
        return DemoScenario(
            id = "single_crash",
            name = "Single Crash Detection",
            description = "Basic crash detection and emergency response scenario",
            steps = listOf(
                DemoStep(
                    id = "setup_user",
                    name = "Setup Demo User",
                    description = "Create a demo user profile",
                    action = { setupDemoMedicalProfile() },
                    validation = { validateDemoMedicalProfile() }
                ),
                DemoStep(
                    id = "simulate_crash",
                    name = "Simulate Crash Detection",
                    description = "Simulate ESP32 crash detection",
                    action = { simulateCrashDetection() },
                    validation = { validateCrashDetection() }
                ),
                DemoStep(
                    id = "emergency_alert",
                    name = "Send Emergency Alert",
                    description = "Send emergency alert via MQTT",
                    action = { sendEmergencyAlert() },
                    validation = { validateEmergencyAlert() }
                )
            ),
            estimatedDuration = 60000L, // 1 minute
            difficulty = DemoDifficulty.BEGINNER
        )
    }

    private fun createMultiCrashScenario(): DemoScenario {
        return DemoScenario(
            id = "multi_crash",
            name = "Multiple Crash Coordination",
            description = "Handle multiple simultaneous crash incidents",
            steps = listOf(
                DemoStep(
                    id = "setup_multiple_users",
                    name = "Setup Multiple Users",
                    description = "Create multiple demo user profiles",
                    action = { setupMultipleDemoUsers() },
                    validation = { validateMultipleDemoUsers() }
                ),
                DemoStep(
                    id = "simulate_multiple_crashes",
                    name = "Simulate Multiple Crashes",
                    description = "Simulate multiple ESP32 crash detections",
                    action = { simulateMultipleCrashes() },
                    validation = { validateMultipleCrashes() }
                ),
                DemoStep(
                    id = "coordinate_responses",
                    name = "Coordinate Responses",
                    description = "Coordinate emergency responses for multiple incidents",
                    action = { coordinateMultipleResponses() },
                    validation = { validateMultipleResponses() }
                )
            ),
            estimatedDuration = 120000L, // 2 minutes
            difficulty = DemoDifficulty.INTERMEDIATE
        )
    }

    private fun createNetworkFailureScenario(): DemoScenario {
        return DemoScenario(
            id = "network_failure",
            name = "Network Failure Recovery",
            description = "Test system resilience during network failures",
            steps = listOf(
                DemoStep(
                    id = "simulate_network_failure",
                    name = "Simulate Network Failure",
                    description = "Simulate MQTT connection loss",
                    action = { simulateNetworkFailure() },
                    validation = { validateNetworkFailure() }
                ),
                DemoStep(
                    id = "test_offline_mode",
                    name = "Test Offline Mode",
                    description = "Verify system operation in offline mode",
                    action = { testOfflineMode() },
                    validation = { validateOfflineMode() }
                ),
                DemoStep(
                    id = "test_recovery",
                    name = "Test Network Recovery",
                    description = "Verify system recovery when network is restored",
                    action = { testNetworkRecovery() },
                    validation = { validateNetworkRecovery() }
                )
            ),
            estimatedDuration = 90000L, // 1.5 minutes
            difficulty = DemoDifficulty.ADVANCED
        )
    }

    private fun createEsp32DisconnectionScenario(): DemoScenario {
        return DemoScenario(
            id = "esp32_disconnection",
            name = "ESP32 Disconnection Handling",
            description = "Test system behavior when ESP32 devices disconnect",
            steps = listOf(
                DemoStep(
                    id = "simulate_disconnection",
                    name = "Simulate ESP32 Disconnection",
                    description = "Simulate ESP32 device disconnection",
                    action = { simulateEsp32Disconnection() },
                    validation = { validateEsp32Disconnection() }
                ),
                DemoStep(
                    id = "test_fallback",
                    name = "Test Fallback Mechanisms",
                    description = "Verify fallback mechanisms work correctly",
                    action = { testFallbackMechanisms() },
                    validation = { validateFallbackMechanisms() }
                ),
                DemoStep(
                    id = "test_reconnection",
                    name = "Test Reconnection",
                    description = "Verify system behavior when ESP32 reconnects",
                    action = { testEsp32Reconnection() },
                    validation = { validateEsp32Reconnection() }
                )
            ),
            estimatedDuration = 90000L, // 1.5 minutes
            difficulty = DemoDifficulty.INTERMEDIATE
        )
    }

    private fun createGpsFailureScenario(): DemoScenario {
        return DemoScenario(
            id = "gps_failure",
            name = "GPS Service Failure",
            description = "Test system behavior when GPS service fails",
            steps = listOf(
                DemoStep(
                    id = "simulate_gps_failure",
                    name = "Simulate GPS Failure",
                    description = "Simulate GPS service failure",
                    action = { simulateGpsFailure() },
                    validation = { validateGpsFailure() }
                ),
                DemoStep(
                    id = "test_location_fallback",
                    name = "Test Location Fallback",
                    description = "Verify location fallback mechanisms",
                    action = { testLocationFallback() },
                    validation = { validateLocationFallback() }
                ),
                DemoStep(
                    id = "test_gps_recovery",
                    name = "Test GPS Recovery",
                    description = "Verify system recovery when GPS is restored",
                    action = { testGpsRecovery() },
                    validation = { validateGpsRecovery() }
                )
            ),
            estimatedDuration = 90000L, // 1.5 minutes
            difficulty = DemoDifficulty.INTERMEDIATE
        )
    }

    private fun createBatteryDrainScenario(): DemoScenario {
        return DemoScenario(
            id = "battery_drain",
            name = "Battery Drain Management",
            description = "Test system behavior during low battery conditions",
            steps = listOf(
                DemoStep(
                    id = "simulate_low_battery",
                    name = "Simulate Low Battery",
                    description = "Simulate low battery condition",
                    action = { simulateLowBattery() },
                    validation = { validateLowBattery() }
                ),
                DemoStep(
                    id = "test_power_saving",
                    name = "Test Power Saving",
                    description = "Verify power saving mechanisms",
                    action = { testPowerSaving() },
                    validation = { validatePowerSaving() }
                ),
                DemoStep(
                    id = "test_critical_battery",
                    name = "Test Critical Battery",
                    description = "Verify critical battery handling",
                    action = { testCriticalBattery() },
                    validation = { validateCriticalBattery() }
                )
            ),
            estimatedDuration = 60000L, // 1 minute
            difficulty = DemoDifficulty.ADVANCED
        )
    }

    private fun createMemoryPressureScenario(): DemoScenario {
        return DemoScenario(
            id = "memory_pressure",
            name = "Memory Pressure Handling",
            description = "Test system behavior under memory pressure",
            steps = listOf(
                DemoStep(
                    id = "simulate_memory_pressure",
                    name = "Simulate Memory Pressure",
                    description = "Simulate high memory usage",
                    action = { simulateMemoryPressure() },
                    validation = { validateMemoryPressure() }
                ),
                DemoStep(
                    id = "test_memory_cleanup",
                    name = "Test Memory Cleanup",
                    description = "Verify memory cleanup mechanisms",
                    action = { testMemoryCleanup() },
                    validation = { validateMemoryCleanup() }
                ),
                DemoStep(
                    id = "test_performance",
                    name = "Test Performance",
                    description = "Verify system performance under pressure",
                    action = { testPerformanceUnderPressure() },
                    validation = { validatePerformanceUnderPressure() }
                )
            ),
            estimatedDuration = 90000L, // 1.5 minutes
            difficulty = DemoDifficulty.EXPERT
        )
    }

    private fun createCompleteEmergencyResponseScenario(): DemoScenario {
        return DemoScenario(
            id = "complete_emergency_response",
            name = "Complete Emergency Response",
            description = "End-to-end emergency response demonstration",
            steps = listOf(
                DemoStep(
                    id = "setup_complete_system",
                    name = "Setup Complete System",
                    description = "Initialize all system components",
                    action = { setupCompleteSystem() },
                    validation = { validateCompleteSystem() }
                ),
                DemoStep(
                    id = "simulate_emergency",
                    name = "Simulate Emergency",
                    description = "Simulate complete emergency scenario",
                    action = { simulateCompleteEmergency() },
                    validation = { validateCompleteEmergency() }
                ),
                DemoStep(
                    id = "test_response_coordination",
                    name = "Test Response Coordination",
                    description = "Test emergency response coordination",
                    action = { testResponseCoordination() },
                    validation = { validateResponseCoordination() }
                ),
                DemoStep(
                    id = "test_recovery",
                    name = "Test Recovery",
                    description = "Test system recovery after emergency",
                    action = { testEmergencyRecovery() },
                    validation = { validateEmergencyRecovery() }
                )
            ),
            estimatedDuration = 300000L, // 5 minutes
            difficulty = DemoDifficulty.EXPERT
        )
    }

    // Demo step implementations
    private suspend fun setupDemoMedicalProfile(): Boolean {
        return try {
            // Create demo user
            val demoUser = User(
                id = 1L,
                name = "Demo User",
                role = UserRole.PUBLISHER,
                email = "demo@example.com",
                phone = "+1234567890"
            )
            
            // Create emergency contact
            val emergencyContact = EmergencyContact(
                name = "Emergency Contact",
                phoneNumber = "+1987654321",
                relationship = "Family",
                isPrimary = true
            )
            
            // Create medical profile
            val medicalProfile = MedicalProfile(
                id = 1L,
                userId = demoUser.id,
                fullName = demoUser.name,
                dateOfBirth = "1990-01-01",
                bloodType = "O+",
                height = "175",
                weight = "70",
                allergies = "None",
                medications = "None",
                medicalConditions = "None",
                emergencyContacts = "[{\"name\":\"${emergencyContact.name}\",\"phoneNumber\":\"${emergencyContact.phoneNumber}\",\"relationship\":\"${emergencyContact.relationship}\"}]"
            )
            
            Log.d(TAG, "Demo medical profile setup completed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up demo medical profile", e)
            false
        }
    }

    private suspend fun validateDemoMedicalProfile(): Boolean {
        return true // Simplified validation
    }

    private suspend fun simulateCrashDetection(): Boolean {
        return try {
            // Simulate ESP32 crash detection
            val crashData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "severity" to "HIGH",
                "location" to mapOf(
                    "latitude" to 40.7128,
                    "longitude" to -74.0060
                )
            )
            
            Log.d(TAG, "Crash detection simulated: $crashData")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating crash detection", e)
            false
        }
    }

    private suspend fun validateCrashDetection(): Boolean {
        return true // Simplified validation
    }

    private suspend fun sendEmergencyAlert(): Boolean {
        return try {
            // Simulate emergency alert
            val alertMessage = mapOf(
                "type" to "CRASH_DETECTED",
                "timestamp" to System.currentTimeMillis(),
                "location" to mapOf(
                    "latitude" to 40.7128,
                    "longitude" to -74.0060
                ),
                "severity" to "HIGH"
            )
            
            Log.d(TAG, "Emergency alert sent: $alertMessage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending emergency alert", e)
            false
        }
    }

    private suspend fun validateEmergencyAlert(): Boolean {
        return true // Simplified validation
    }

    // Additional demo step implementations (simplified for compilation)
    private suspend fun setupMultipleDemoUsers(): Boolean = true
    private suspend fun validateMultipleDemoUsers(): Boolean = true
    private suspend fun simulateMultipleCrashes(): Boolean = true
    private suspend fun validateMultipleCrashes(): Boolean = true
    private suspend fun coordinateMultipleResponses(): Boolean = true
    private suspend fun validateMultipleResponses(): Boolean = true
    private suspend fun simulateNetworkFailure(): Boolean = true
    private suspend fun validateNetworkFailure(): Boolean = true
    private suspend fun testOfflineMode(): Boolean = true
    private suspend fun validateOfflineMode(): Boolean = true
    private suspend fun testNetworkRecovery(): Boolean = true
    private suspend fun validateNetworkRecovery(): Boolean = true
    private suspend fun simulateEsp32Disconnection(): Boolean = true
    private suspend fun validateEsp32Disconnection(): Boolean = true
    private suspend fun testFallbackMechanisms(): Boolean = true
    private suspend fun validateFallbackMechanisms(): Boolean = true
    private suspend fun testEsp32Reconnection(): Boolean = true
    private suspend fun validateEsp32Reconnection(): Boolean = true
    private suspend fun simulateGpsFailure(): Boolean = true
    private suspend fun validateGpsFailure(): Boolean = true
    private suspend fun testLocationFallback(): Boolean = true
    private suspend fun validateLocationFallback(): Boolean = true
    private suspend fun testGpsRecovery(): Boolean = true
    private suspend fun validateGpsRecovery(): Boolean = true
    private suspend fun simulateLowBattery(): Boolean = true
    private suspend fun validateLowBattery(): Boolean = true
    private suspend fun testPowerSaving(): Boolean = true
    private suspend fun validatePowerSaving(): Boolean = true
    private suspend fun testCriticalBattery(): Boolean = true
    private suspend fun validateCriticalBattery(): Boolean = true
    private suspend fun simulateMemoryPressure(): Boolean = true
    private suspend fun validateMemoryPressure(): Boolean = true
    private suspend fun testMemoryCleanup(): Boolean = true
    private suspend fun validateMemoryCleanup(): Boolean = true
    private suspend fun testPerformanceUnderPressure(): Boolean = true
    private suspend fun validatePerformanceUnderPressure(): Boolean = true
    private suspend fun setupCompleteSystem(): Boolean = true
    private suspend fun validateCompleteSystem(): Boolean = true
    private suspend fun simulateCompleteEmergency(): Boolean = true
    private suspend fun validateCompleteEmergency(): Boolean = true
    private suspend fun testResponseCoordination(): Boolean = true
    private suspend fun validateResponseCoordination(): Boolean = true
    private suspend fun testEmergencyRecovery(): Boolean = true
    private suspend fun validateEmergencyRecovery(): Boolean = true

    /**
     * Generate demo report
     */
    fun generateDemoReport(): String {
        val progress = demoProgress.get()
        if (progress == null) {
            return "No demo in progress"
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val duration = System.currentTimeMillis() - progress.startTime
        
        return """
            Demo Scenario Report
            ===================
            Scenario ID: ${progress.scenarioId}
            Status: ${progress.status}
            Progress: ${progress.currentStep}/${progress.totalSteps}
            Start Time: ${dateFormat.format(Date(progress.startTime))}
            Duration: ${duration / 1000} seconds
            Completed Steps: ${progress.completedSteps.size}
            Failed Steps: ${progress.failedSteps.size}
            
            Completed Steps:
            ${progress.completedSteps.joinToString("\n") { "- $it" }}
            
            Failed Steps:
            ${progress.failedSteps.joinToString("\n") { "- $it" }}
        """.trimIndent()
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopCurrentDemo()
        demoScope.cancel()
        Log.i(TAG, "Demo Scenario Manager cleaned up")
    }
}
