package com.bharath.carcrashdetection.di

import com.bharath.carcrashdetection.CarCrashDetectionApp
import com.bharath.carcrashdetection.data.dao.IncidentDao
import com.bharath.carcrashdetection.data.dao.MedicalProfileDao
import com.bharath.carcrashdetection.data.dao.UserDao
import com.bharath.carcrashdetection.data.database.AppDatabase
import com.bharath.carcrashdetection.data.repository.IncidentRepository
import com.bharath.carcrashdetection.data.repository.MedicalProfileRepository
import com.bharath.carcrashdetection.data.repository.UserRepository
import com.bharath.carcrashdetection.util.MqttService
import com.bharath.carcrashdetection.util.Esp32Manager
import com.bharath.carcrashdetection.util.GpsService
import com.bharath.carcrashdetection.testing.IntegrationTestSuite
import com.bharath.carcrashdetection.util.SystemHealthMonitor
import com.bharath.carcrashdetection.demo.DemoScenarioManager
import com.bharath.carcrashdetection.util.ErrorHandler
import com.bharath.carcrashdetection.production.ProductionMonitor
import com.bharath.carcrashdetection.production.MaintenanceManager
import com.bharath.carcrashdetection.production.InstallationManager

object AppModule {
    
    private val database: AppDatabase by lazy {
        try {
            AppDatabase.getDatabase(CarCrashDetectionApp.instance)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize database: ${e.message}")
        }
    }
    
    val userDao: UserDao by lazy { database.userDao() }
    val medicalProfileDao: MedicalProfileDao by lazy { database.medicalProfileDao() }
    val incidentDao: IncidentDao by lazy { database.incidentDao() }
    
    val userRepository: UserRepository by lazy { UserRepository(userDao) }
    val medicalProfileRepository: MedicalProfileRepository by lazy { MedicalProfileRepository(medicalProfileDao) }
    val incidentRepository: IncidentRepository by lazy { IncidentRepository(incidentDao) }
    
    // Phase 6 Components
    val mqttService: MqttService by lazy { MqttService() }
    val esp32Manager: Esp32Manager by lazy { Esp32Manager(CarCrashDetectionApp.instance) }
    val gpsService: GpsService by lazy { GpsService(CarCrashDetectionApp.instance) }
    
    val integrationTestSuite: IntegrationTestSuite by lazy { 
        IntegrationTestSuite(
            CarCrashDetectionApp.instance,
            mqttService,
            esp32Manager,
            gpsService,
            userRepository,
            medicalProfileRepository,
            incidentRepository
        )
    }
    
    val systemHealthMonitor: SystemHealthMonitor by lazy {
        SystemHealthMonitor(
            CarCrashDetectionApp.instance,
            mqttService,
            esp32Manager,
            gpsService,
            userRepository,
            medicalProfileRepository,
            incidentRepository
        )
    }
    
    val demoScenarioManager: DemoScenarioManager by lazy {
        DemoScenarioManager(
            CarCrashDetectionApp.instance,
            mqttService,
            esp32Manager,
            gpsService,
            userRepository,
            medicalProfileRepository,
            incidentRepository
        )
    }
    
    val errorHandler: ErrorHandler by lazy {
        ErrorHandler(
            CarCrashDetectionApp.instance,
            mqttService,
            esp32Manager,
            gpsService,
            userRepository,
            medicalProfileRepository,
            incidentRepository
        )
    }
    
    // Phase 7 Components - Production and Deployment
    val productionMonitor: ProductionMonitor by lazy {
        ProductionMonitor.getInstance(CarCrashDetectionApp.instance)
    }
    
    val maintenanceManager: MaintenanceManager by lazy {
        MaintenanceManager.getInstance(CarCrashDetectionApp.instance)
    }
    
    val installationManager: InstallationManager by lazy {
        InstallationManager.getInstance(CarCrashDetectionApp.instance)
    }
} 