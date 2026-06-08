package com.bharath.carcrashdetection.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        
        // Required permissions for ESP32 communication
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        
        // Additional permissions for camera and storage
        private val CAMERA_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        fun hasRequiredPermissions(context: Context): Boolean {
            return REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        fun hasCameraPermissions(context: Context): Boolean {
            return CAMERA_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        fun requestRequiredPermissions(activity: Activity) {
            val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(activity, permissionsToRequest, PERMISSION_REQUEST_CODE)
            }
        }
        
        fun requestRequiredPermissions(fragment: Fragment) {
            val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(fragment.requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                fragment.requestPermissions(permissionsToRequest, PERMISSION_REQUEST_CODE)
            }
        }
        
        fun requestCameraPermissions(activity: Activity) {
            val permissionsToRequest = CAMERA_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(activity, permissionsToRequest, PERMISSION_REQUEST_CODE + 1)
            }
        }
        
        fun requestCameraPermissions(fragment: Fragment) {
            val permissionsToRequest = CAMERA_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(fragment.requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                fragment.requestPermissions(permissionsToRequest, PERMISSION_REQUEST_CODE + 1)
            }
        }
        
        fun shouldShowPermissionRationale(activity: Activity, permission: String): Boolean {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        
        fun shouldShowPermissionRationale(fragment: Fragment, permission: String): Boolean {
            return fragment.shouldShowRequestPermissionRationale(permission)
        }
        
        fun getPermissionRequestCode(): Int = PERMISSION_REQUEST_CODE
    }
}
