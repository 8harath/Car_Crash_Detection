package com.bharath.carcrashdetection.util

object MqttTopics {
    // Base topics
    const val EMERGENCY_ALERTS = "emergency/alerts"
    const val EMERGENCY_STATUS = "emergency/status"
    const val EMERGENCY_RESPONSE = "emergency/response"
    const val RESPONSE_ACK = "emergency/response/ack"

    // Subtopics
    fun alertIncident(incidentId: String) = "emergency/alerts/$incidentId"
    const val ALERT_BROADCAST = "emergency/alerts/broadcast"
    fun statusIncident(incidentId: String) = "emergency/status/$incidentId"
    const val STATUS_SYSTEM = "emergency/status/system"
    fun responseIncident(incidentId: String) = "emergency/response/$incidentId"
    const val RESPONSE_BROADCAST = "emergency/response/broadcast"

    // Subscription helpers
    fun subscribeTopicsForRole(role: String, incidentId: String? = null): List<String> = when (role) {
        "PUBLISHER" -> listOfNotNull(
            incidentId?.let { alertIncident(it) },
            STATUS_SYSTEM,
            RESPONSE_BROADCAST
        )
        "SUBSCRIBER" -> listOf(
            ALERT_BROADCAST,
            EMERGENCY_ALERTS + "/+",
            EMERGENCY_STATUS + "/+",
            EMERGENCY_RESPONSE + "/+",
            RESPONSE_ACK + "/+"
        )
        else -> emptyList()
    }
}