package com.bharath.carcrashdetection.data.dao

import androidx.room.*
import com.bharath.carcrashdetection.data.model.Incident
import com.bharath.carcrashdetection.data.model.IncidentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<Incident>>
    
    @Query("SELECT * FROM incidents WHERE status = :status ORDER BY timestamp DESC")
    fun getIncidentsByStatus(status: IncidentStatus): Flow<List<Incident>>
    
    @Query("SELECT * FROM incidents WHERE victimId = :victimId ORDER BY timestamp DESC")
    fun getIncidentsByVictimId(victimId: Long): Flow<List<Incident>>
    
    @Query("SELECT * FROM incidents WHERE incidentId = :incidentId")
    suspend fun getIncidentByIncidentId(incidentId: String): Incident?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: Incident): Long
    
    @Update
    suspend fun updateIncident(incident: Incident)
    
    @Delete
    suspend fun deleteIncident(incident: Incident)
    
    @Query("DELETE FROM incidents")
    suspend fun deleteAllIncidents()
} 