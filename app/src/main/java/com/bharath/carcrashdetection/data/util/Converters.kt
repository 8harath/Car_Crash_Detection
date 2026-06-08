package com.bharath.carcrashdetection.data.util

import androidx.room.TypeConverter
import com.bharath.carcrashdetection.data.model.IncidentSeverity
import com.bharath.carcrashdetection.data.model.IncidentStatus
import com.bharath.carcrashdetection.data.model.UserRole

class Converters {
    @TypeConverter
    fun fromUserRole(role: UserRole): String {
        return role.name
    }
    
    @TypeConverter
    fun toUserRole(role: String): UserRole {
        return UserRole.valueOf(role)
    }
    
    @TypeConverter
    fun fromIncidentStatus(status: IncidentStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toIncidentStatus(status: String): IncidentStatus {
        return IncidentStatus.valueOf(status)
    }
    
    @TypeConverter
    fun fromIncidentSeverity(severity: IncidentSeverity): String {
        return severity.name
    }
    
    @TypeConverter
    fun toIncidentSeverity(severity: String): IncidentSeverity {
        return IncidentSeverity.valueOf(severity)
    }
} 