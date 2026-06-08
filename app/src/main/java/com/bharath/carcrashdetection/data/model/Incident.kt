package com.bharath.carcrashdetection.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "incidents",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["victimId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Incident(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val victimId: Long,
    val incidentId: String, // Unique MQTT incident identifier
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: IncidentStatus = IncidentStatus.ACTIVE,
    val description: String? = null,
    val severity: IncidentSeverity = IncidentSeverity.MEDIUM,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class IncidentStatus {
    ACTIVE,
    RESPONDING,
    RESOLVED,
    CANCELLED
}

enum class IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
} 