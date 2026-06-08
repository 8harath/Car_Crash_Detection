package com.bharath.carcrashdetection.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val role: UserRole,
    val email: String? = null,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class UserRole {
    PUBLISHER,  // Crash victim
    SUBSCRIBER  // Emergency responder
} 