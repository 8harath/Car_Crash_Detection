package com.bharath.carcrashdetection.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "medical_profiles",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MedicalProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val fullName: String? = null,
    val dateOfBirth: String? = null,
    val bloodType: String? = null,
    val height: String? = null, // in cm
    val weight: String? = null, // in kg
    val allergies: String? = null,
    val medications: String? = null,
    val medicalConditions: String? = null,
    val emergencyContacts: String? = null, // JSON string of contacts
    val insuranceInfo: String? = null,
    val organDonor: Boolean = false,
    val photoPath: String? = null, // Path to profile photo
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) 