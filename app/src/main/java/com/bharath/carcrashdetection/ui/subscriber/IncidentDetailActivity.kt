package com.bharath.carcrashdetection.ui.subscriber

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bharath.carcrashdetection.ui.base.BaseActivity
import com.bharath.carcrashdetection.data.model.Incident
import com.bharath.carcrashdetection.databinding.ActivityIncidentDetailBinding
import com.bharath.carcrashdetection.databinding.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.util.Log
import android.view.MenuItem
import java.text.SimpleDateFormat
import java.util.*

class IncidentDetailActivity : BaseActivity<ActivityIncidentDetailBinding>() {
    
    companion object {
        private const val TAG = "IncidentDetailActivity"
        const val EXTRA_INCIDENT_ID = "extra_incident_id"
    }
    
    private var currentIncident: Incident? = null
    
    override fun getViewBinding(): ActivityIncidentDetailBinding = ActivityIncidentDetailBinding.inflate(layoutInflater)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setupToolbar()
            setupActionButtons()
            populateIncidentDetails()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }
    
    override fun setupViews() {
        try {
            // Views are already set up in onCreate
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up views: ${e.message}", e)
        }
    }
    
    override fun setupObservers() {
        try {
            // For now, no observers needed
            Log.d(TAG, "No observers needed for simplified IncidentDetailActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up observers: ${e.message}", e)
        }
    }
    
    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.title = "Incident Details"
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up toolbar: ${e.message}", e)
        }
    }
    
    private fun populateIncidentDetails() {
        try {
            // For now, create a sample incident for demonstration
            // In a real implementation, this would load from the database
            currentIncident = Incident(
                id = 1L,
                victimId = 1L,
                incidentId = "incident_${System.currentTimeMillis()}",
                latitude = 40.7128,
                longitude = -74.0060,
                timestamp = System.currentTimeMillis(),
                status = com.bharath.carcrashdetection.data.model.IncidentStatus.ACTIVE,
                description = "Emergency assistance needed - vehicle accident",
                severity = com.bharath.carcrashdetection.data.model.IncidentSeverity.HIGH,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            currentIncident?.let { incident ->
                // Basic incident info
                binding.tvVictimName.text = "Emergency Alert"
                binding.tvIncidentId.text = "ID: ${incident.incidentId}"
                binding.tvSeverity.text = "Severity: ${incident.severity.name}"
                
                // Timestamp
                val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                binding.tvTimestamp.text = "Time: ${sdf.format(Date(incident.timestamp))}"
                
                // Location
                if (incident.latitude != null && incident.longitude != null) {
                    binding.tvLocation.text = 
                        "Location: ${String.format("%.6f, %.6f", incident.latitude, incident.longitude)}"
                } else {
                    binding.tvLocation.text = "Location: Unknown"
                }
                
                // Description
                binding.tvDescription.text = incident.description ?: "No description available"
                
            } ?: run {
                Log.w(TAG, "No incident data to populate")
                showToast("No incident data available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error populating incident details: ${e.message}", e)
            showToast("Error loading incident information")
        }
    }
    
    private fun setupActionButtons() {
        // Respond button
        binding.btnRespond.setOnClickListener {
            showResponseDialog()
        }
        
        // Cancel response button
        binding.btnCancelResponse.setOnClickListener {
            showCancelResponseDialog()
        }
        
        // Navigation buttons
        binding.btnNavigateMaps.setOnClickListener {
            openNavigation()
        }
        
        binding.btnNavigateWaze.setOnClickListener {
            openWazeNavigation()
        }
    }
    
    private fun showResponseDialog() {
        try {
            // For now, just show a simple dialog
            // In a real implementation, this would show a proper response dialog
            showToast("Response functionality coming soon")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing response dialog: ${e.message}", e)
        }
    }
    
    private fun showCancelResponseDialog() {
        try {
            // For now, just show a simple dialog
            // In a real implementation, this would show a proper cancel dialog
            showToast("Cancel response functionality coming soon")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing cancel response dialog: ${e.message}", e)
        }
    }
    
    private fun openNavigation() {
        try {
            // For now, just show a message
            // In a real implementation, this would open Google Maps
            showToast("Navigation functionality coming soon")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening navigation: ${e.message}", e)
        }
    }
    
    private fun openWazeNavigation() {
        try {
            // For now, just show a message
            // In a real implementation, this would open Waze
            showToast("Waze navigation functionality coming soon")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Waze navigation: ${e.message}", e)
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
