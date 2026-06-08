package com.bharath.carcrashdetection.data.repository

import com.bharath.carcrashdetection.data.dao.IncidentDao
import com.bharath.carcrashdetection.data.model.Incident
import com.bharath.carcrashdetection.data.model.IncidentStatus
import kotlinx.coroutines.flow.Flow
class IncidentRepository(
    private val incidentDao: IncidentDao
) {
    fun getAllIncidents(): Flow<List<Incident>> = incidentDao.getAllIncidents()
    
    fun getIncidentsByStatus(status: IncidentStatus): Flow<List<Incident>> = 
        incidentDao.getIncidentsByStatus(status)
    
    fun getIncidentsByVictimId(victimId: Long): Flow<List<Incident>> = 
        incidentDao.getIncidentsByVictimId(victimId)
    
    suspend fun getIncidentByIncidentId(incidentId: String): Incident? = 
        incidentDao.getIncidentByIncidentId(incidentId)
    
    suspend fun insertIncident(incident: Incident): Long = incidentDao.insertIncident(incident)
    
    suspend fun updateIncident(incident: Incident) = incidentDao.updateIncident(incident)
    
    suspend fun deleteIncident(incident: Incident) = incidentDao.deleteIncident(incident)
    
    suspend fun deleteAllIncidents() = incidentDao.deleteAllIncidents()
} 