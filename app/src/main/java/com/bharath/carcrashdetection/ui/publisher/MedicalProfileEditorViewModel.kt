package com.bharath.carcrashdetection.ui.publisher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bharath.carcrashdetection.data.model.EmergencyContact
import com.bharath.carcrashdetection.data.model.MedicalProfile
import com.bharath.carcrashdetection.data.repository.MedicalProfileRepository
import com.bharath.carcrashdetection.CarCrashDetectionApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MedicalProfileEditorViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = MedicalProfileRepository(CarCrashDetectionApp.instance.database.medicalProfileDao())
    
    private val _profile = MutableStateFlow<MedicalProfile?>(null)
    val profile: StateFlow<MedicalProfile?> = _profile
    
    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage
    
    fun loadProfile(profileId: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val profile = repository.getMedicalProfileById(profileId)
                _profile.value = profile
                
                // Load emergency contacts from JSON
                profile?.emergencyContacts?.let { contactsJson ->
                    try {
                        val contacts = Json.decodeFromString<List<EmergencyContact>>(contactsJson)
                        _emergencyContacts.value = contacts
                    } catch (e: Exception) {
                        _emergencyContacts.value = emptyList()
                    }
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun addEmergencyContact(contact: EmergencyContact) {
        val currentContacts = _emergencyContacts.value.toMutableList()
        
        // If this is marked as primary, unmark others
        if (contact.isPrimary) {
            currentContacts.forEachIndexed { index, existingContact ->
                currentContacts[index] = existingContact.copy(isPrimary = false)
            }
        }
        
        currentContacts.add(contact)
        _emergencyContacts.value = currentContacts
    }
    
    fun updateEmergencyContact(contact: EmergencyContact) {
        val currentContacts = _emergencyContacts.value.toMutableList()
        val index = currentContacts.indexOfFirst { 
            it.name == contact.name && it.phoneNumber == contact.phoneNumber 
        }
        
        if (index != -1) {
            // If this is marked as primary, unmark others
            if (contact.isPrimary) {
                currentContacts.forEachIndexed { i, existingContact ->
                    if (i != index) {
                        currentContacts[i] = existingContact.copy(isPrimary = false)
                    }
                }
            }
            
            currentContacts[index] = contact
            _emergencyContacts.value = currentContacts
        }
    }
    
    fun removeEmergencyContact(contact: EmergencyContact) {
        val currentContacts = _emergencyContacts.value.toMutableList()
        currentContacts.remove(contact)
        _emergencyContacts.value = currentContacts
    }
    
    fun saveProfile(profile: MedicalProfile) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                // Serialize emergency contacts to JSON
                val contactsJson = Json.encodeToString(_emergencyContacts.value)
                val profileWithContacts = profile.copy(emergencyContacts = contactsJson)
                
                // Save to database
                if (profile.id == 0L) {
                    val profileId = repository.insertMedicalProfile(profileWithContacts)
                    _profile.value = profileWithContacts.copy(id = profileId)
                } else {
                    repository.updateMedicalProfile(profileWithContacts)
                    _profile.value = profileWithContacts
                }
                _successMessage.value = "Medical profile saved successfully!"
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
