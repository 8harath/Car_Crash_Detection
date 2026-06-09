package com.bharath.carcrashdetection


import com.bharath.carcrashdetection.util.EmergencyAlertMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MqttMessageSchemasTest {
    @Test
    fun testEmergencyAlertMessageSerialization() {
        val message = EmergencyAlertMessage(
            incidentId = "incident_123",
            victimId = "user_1",
            victimName = "John Doe",
            location = EmergencyAlertMessage.Location(12.34, 56.78),
            timestamp = 1234567890L,
            severity = "HIGH",
            medicalInfo = EmergencyAlertMessage.MedicalInfo(
                bloodType = "O+",
                allergies = listOf("penicillin"),
                medications = listOf("insulin")
            )
        )
        val json = Json.encodeToString(message)
        val decoded = Json.decodeFromString<EmergencyAlertMessage>(json)
        assertEquals(message, decoded)
    }
}