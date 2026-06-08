package com.bharath.carcrashdetection.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyContact(
    val name: String,
    val relationship: String,
    val phoneNumber: String,
    val email: String? = null,
    val isPrimary: Boolean = false
)
