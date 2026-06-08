package com.bharath.carcrashdetection.util

import android.content.Context
import android.util.Log
import com.bharath.carcrashdetection.data.model.User
import com.bharath.carcrashdetection.data.model.UserRole
import com.bharath.carcrashdetection.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseTest {
    
    private const val TAG = "DatabaseTest"
    
    fun testDatabase(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting database test...")
                
                // Test user insertion
                val testUser = User(
                    name = "Test User",
                    role = UserRole.PUBLISHER
                )
                
                val userId = AppModule.userRepository.insertUser(testUser)
                Log.d(TAG, "User inserted with ID: $userId")
                
                // Test user retrieval
                val retrievedUser = AppModule.userRepository.getUserById(userId)
                Log.d(TAG, "Retrieved user: ${retrievedUser?.name}")
                
                // Test user update
                val updatedUser = retrievedUser?.copy(name = "Updated Test User")
                if (updatedUser != null) {
                    AppModule.userRepository.updateUser(updatedUser)
                    Log.d(TAG, "User updated successfully")
                }
                
                // Test user deletion
                if (retrievedUser != null) {
                    AppModule.userRepository.deleteUser(retrievedUser)
                    Log.d(TAG, "User deleted successfully")
                }
                
                Log.d(TAG, "Database test completed successfully!")
                
            } catch (e: Exception) {
                Log.e(TAG, "Database test failed: ${e.message}", e)
            }
        }
    }
} 