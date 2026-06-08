package com.bharath.carcrashdetection.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsService(private val context: Context) {
    
    companion object {
        private const val TAG = "GpsService"
        private const val MIN_TIME_BETWEEN_UPDATES = 1000L // 1 second
        private const val MIN_DISTANCE_CHANGE_FOR_UPDATES = 1f // 1 meter
    }
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()
    
    private val _locationAccuracy = MutableStateFlow(0f)
    val locationAccuracy: StateFlow<Float> = _locationAccuracy.asStateFlow()
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _currentLocation.value = location
            _locationAccuracy.value = location.accuracy
            Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d(TAG, "Location provider status changed: $provider -> $status")
        }
        
        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Location provider enabled: $provider")
            _isGpsEnabled.value = true
        }
        
        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "Location provider disabled: $provider")
            _isGpsEnabled.value = false
        }
    }
    
    /**
     * Check if GPS permissions are granted
     */
    fun hasGpsPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if GPS is enabled
     */
    fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * Start GPS location updates
     */
    fun startLocationUpdates() {
        if (!hasGpsPermissions()) {
            Log.w(TAG, "GPS permissions not granted")
            return
        }
        
        if (!isGpsEnabled()) {
            Log.w(TAG, "GPS is not enabled")
            return
        }
        
        try {
            // Try GPS provider first (more accurate)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES,
                    locationListener
                )
                Log.i(TAG, "Started GPS location updates")
            }
            
            // Also try network provider as fallback
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES,
                    locationListener
                )
                Log.i(TAG, "Started network location updates")
            }
            
            _isGpsEnabled.value = true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when requesting location updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }
    
    /**
     * Stop GPS location updates
     */
    fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
            Log.i(TAG, "Stopped location updates")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }
    
    /**
     * Get last known location
     */
    fun getLastKnownLocation(): Location? {
        if (!hasGpsPermissions()) {
            return null
        }
        
        try {
            // Try GPS provider first
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            
            // If GPS location is not available or too old, try network provider
            if (location == null || isLocationOld(location)) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            
            return location
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when getting last known location", e)
            return null
        }
    }
    
    /**
     * Check if location is too old (older than 5 minutes)
     */
    private fun isLocationOld(location: Location): Boolean {
        val currentTime = System.currentTimeMillis()
        val locationTime = location.time
        val timeDifference = currentTime - locationTime
        return timeDifference > 5 * 60 * 1000 // 5 minutes
    }
    
    /**
     * Get current location as string
     */
    fun getCurrentLocationString(): String {
        val location = _currentLocation.value ?: getLastKnownLocation()
        return if (location != null) {
            "Lat: ${location.latitude}, Lon: ${location.longitude}"
        } else {
            "Location not available"
        }
    }
    
    /**
     * Get location coordinates as Pair
     */
    fun getCurrentCoordinates(): Pair<Double, Double>? {
        val location = _currentLocation.value ?: getLastKnownLocation()
        return if (location != null) {
            Pair(location.latitude, location.longitude)
        } else {
            null
        }
    }
    
    /**
     * Calculate distance between two locations in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopLocationUpdates()
        Log.i(TAG, "GPS Service cleaned up")
    }
}
