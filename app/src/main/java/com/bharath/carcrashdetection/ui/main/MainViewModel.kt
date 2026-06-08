package com.bharath.carcrashdetection.ui.main

import androidx.lifecycle.viewModelScope
import com.bharath.carcrashdetection.data.model.User
import com.bharath.carcrashdetection.data.model.UserRole
import com.bharath.carcrashdetection.data.repository.UserRepository
import com.bharath.carcrashdetection.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.bharath.carcrashdetection.di.AppModule
import android.util.Log

class MainViewModel : BaseViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    private val userRepository: UserRepository? by lazy { 
        try {
            Log.d(TAG, "Initializing UserRepository")
            AppModule.userRepository
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UserRepository: ${e.message}", e)
            null
        }
    }
    
    private val _selectedRole = MutableStateFlow<UserRole?>(null)
    val selectedRole: StateFlow<UserRole?> = _selectedRole
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser
    
    fun selectRole(role: UserRole) {
        try {
            Log.d(TAG, "Selecting role: $role")
            _selectedRole.value = role
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting role: ${e.message}", e)
            showError("Failed to select role: ${e.message}")
        }
    }
    
    fun createUser(name: String, role: UserRole) {
        Log.d(TAG, "Creating user: name=$name, role=$role")
        
        // Validate input
        if (name.isBlank()) {
            showError("Name cannot be empty")
            return
        }
        
        launchWithLoading {
            try {
                Log.d(TAG, "Creating user in database...")
                
                val user = User(
                    name = name.trim(),
                    role = role
                )
                
                // Try to save to database if available, otherwise use temporary storage
                val repository = userRepository
                val createdUser = if (repository != null) {
                    try {
                        Log.d(TAG, "Saving user to database...")
                        val userId = repository.insertUser(user)
                        user.copy(id = userId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Database save failed, using temporary storage: ${e.message}")
                        user.copy(id = System.currentTimeMillis()) // Use timestamp as temporary ID
                    }
                } else {
                    Log.w(TAG, "UserRepository not available, using temporary storage")
                    user.copy(id = System.currentTimeMillis()) // Use timestamp as temporary ID
                }
                
                Log.d(TAG, "User created successfully: ${createdUser.id}")
                _currentUser.value = createdUser
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create user: ${e.message}", e)
                showError("Failed to create user: ${e.message}")
                throw e // Re-throw to trigger error handling in launchWithLoading
            }
        }
    }
    
    fun loadCurrentUser() {
        Log.d(TAG, "Loading current user...")
        
        launchWithLoading {
            try {
                val repository = userRepository
                if (repository != null) {
                    Log.d(TAG, "Loading user from database...")
                    repository.getAllUsers().collect { userList ->
                        if (userList.isNotEmpty()) {
                            Log.d(TAG, "Found existing user: ${userList.first()}")
                            _currentUser.value = userList.first()
                            _selectedRole.value = userList.first().role
                        } else {
                            Log.d(TAG, "No existing user found")
                        }
                    }
                } else {
                    Log.w(TAG, "UserRepository not available, skipping user load")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load current user: ${e.message}", e)
                showError("Failed to load user: ${e.message}")
            }
        }
    }
} 