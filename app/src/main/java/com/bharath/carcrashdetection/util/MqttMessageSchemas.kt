package com.bharath.carcrashdetection.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmergencyAlertMessage(
    val type: String = "emergency_alert",
    val incidentId: String,
    val victimId: String,
    val victimName: String,
    val location: Location,
    val timestamp: Long,
    val severity: String,
    val medicalInfo: MedicalInfo
) {
    @Serializable
data class Location(
        val latitude: Double,
        val longitude: Double
    )
    @Serializable
data class MedicalInfo(
        val bloodType: String,
        val allergies: List<String> = emptyList(),
        val medications: List<String> = emptyList(),
        val conditions: List<String> = emptyList()
    )
}

@Serializable
data class ResponseAckMessage(
    val type: String = "response_ack",
    val incidentId: String,
    val responderId: String,
    val responderName: String,
    val status: String,
    val eta: Int, // seconds
    val timestamp: Long
)