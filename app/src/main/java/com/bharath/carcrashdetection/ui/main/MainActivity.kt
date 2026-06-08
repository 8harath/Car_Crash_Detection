package com.bharath.carcrashdetection.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bharath.carcrashdetection.R
import com.bharath.carcrashdetection.data.model.UserRole
import com.bharath.carcrashdetection.ui.base.BaseActivity
import com.bharath.carcrashdetection.ui.publisher.PublisherActivity
import com.bharath.carcrashdetection.ui.subscriber.SubscriberActivity
import com.bharath.carcrashdetection.util.CrashHandler
import com.bharath.carcrashdetection.util.DatabaseTest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import android.view.View
import android.util.Log
import android.widget.Toast
import com.bharath.carcrashdetection.databinding.ActivityMainBinding
import com.bharath.carcrashdetection.databinding.*

class MainActivity : BaseActivity<ActivityMainBinding>() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private val viewModel: MainViewModel by viewModels()
    
    override fun getViewBinding(): ActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            // Initialize crash handler
            CrashHandler.getInstance().init(this)
            Log.d(TAG, "Crash handler initialized")
            
            super.onCreate(savedInstanceState)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            // Fallback to prevent crashes
            super.onCreate(savedInstanceState)
            setContentView(android.R.layout.simple_list_item_1)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun setupViews() {
        try {
            Log.d(TAG, "Setting up views...")
            setupRoleSelection()
            setupContinueButton()
            loadCurrentUser()
            
            // Temporarily disable database test to prevent crashes
            // DatabaseTest.testDatabase(this)
            Log.d(TAG, "Views setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up views: ${e.message}", e)
            showToast("Error initializing app: ${e.message}")
        }
    }
    
    override fun setupObservers() {
        try {
            Log.d(TAG, "Setting up observers...")
            
            lifecycleScope.launch {
                viewModel.selectedRole.collect { role ->
                    try {
                        binding.btnContinue.isEnabled = role != null
                        updateCardSelection(role)
                        Log.d(TAG, "Role selection updated: $role")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating continue button: ${e.message}", e)
                    }
                }
            }
            
            lifecycleScope.launch {
                viewModel.currentUser.collect { user ->
                    try {
                        user?.let {
                            Log.d(TAG, "Current user loaded: ${it.name} (${it.role})")
                            navigateToRoleSpecificActivity(it.role)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling current user: ${e.message}", e)
                        showToast("Error loading user: ${e.message}")
                    }
                }
            }
            
            lifecycleScope.launch {
                viewModel.isLoading.collect { isLoading ->
                    try {
                        val selectedRole = viewModel.selectedRole.value
                        binding.btnContinue.isEnabled = !isLoading && selectedRole != null
                        Log.d(TAG, "Loading state updated: $isLoading")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating loading state: ${e.message}", e)
                    }
                }
            }
            
            lifecycleScope.launch {
                viewModel.errorMessage.collect { error ->
                    try {
                        if (error != null) {
                            Log.e(TAG, "Error message received: $error")
                            showToast(error)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing error message: ${e.message}", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up observers: ${e.message}", e)
            showToast("Error setting up app: ${e.message}")
        }
    }
    
    private fun setupRoleSelection() {
        try {
            Log.d(TAG, "Setting up role selection...")
            
            binding.cardPublisher.setOnClickListener {
                try {
                    Log.d(TAG, "Publisher card clicked")
                    viewModel.selectRole(UserRole.PUBLISHER)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling publisher card click: ${e.message}", e)
                    showToast("Error selecting role: ${e.message}")
                }
            }
            
            binding.cardSubscriber.setOnClickListener {
                try {
                    Log.d(TAG, "Subscriber card clicked")
                    viewModel.selectRole(UserRole.SUBSCRIBER)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling subscriber card click: ${e.message}", e)
                    showToast("Error selecting role: ${e.message}")
                }
            }
            
            Log.d(TAG, "Role selection setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up role selection: ${e.message}", e)
            showToast("Error setting up role selection: ${e.message}")
        }
    }
    
    private fun setupContinueButton() {
        try {
            Log.d(TAG, "Setting up continue button...")
            
            binding.btnContinue.setOnClickListener {
                try {
                    Log.d(TAG, "Continue button clicked")
                    val selectedRole = viewModel.selectedRole.value
                    
                    if (selectedRole != null) {
                        showNameInputDialog(selectedRole)
                    } else {
                        Log.w(TAG, "Continue button clicked but no role selected")
                        showToast("Please select a role first")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling continue button click: ${e.message}", e)
                    showToast("Error processing request: ${e.message}")
                }
            }
            
            Log.d(TAG, "Continue button setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up continue button: ${e.message}", e)
            showToast("Error setting up continue button: ${e.message}")
        }
    }
    
    private fun updateCardSelection(selectedRole: UserRole?) {
        try {
            Log.d(TAG, "Updating card selection for role: $selectedRole")
            
            // Reset card styles
            binding.cardPublisher.strokeWidth = 0
            binding.cardSubscriber.strokeWidth = 0
            
            // Apply selection style
            when (selectedRole) {
                UserRole.PUBLISHER -> {
                    binding.cardPublisher.apply {
                        strokeWidth = 4
                        strokeColor = getColor(R.color.publisher_primary)
                    }
                }
                UserRole.SUBSCRIBER -> {
                    binding.cardSubscriber.apply {
                        strokeWidth = 4
                        strokeColor = getColor(R.color.subscriber_primary)
                    }
                }
                null -> { 
                    Log.d(TAG, "No role selected")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating card selection: ${e.message}", e)
        }
    }
    
    private fun showNameInputDialog(role: UserRole) {
        try {
            Log.d(TAG, "Showing name input dialog for role: $role")
            
            val dialogView = layoutInflater.inflate(R.layout.dialog_name_input, null)
            val nameEditText = dialogView.findViewById<android.widget.EditText>(R.id.etName)
            
            MaterialAlertDialogBuilder(this)
                .setTitle("Enter Your Name")
                .setView(dialogView)
                .setPositiveButton("Continue") { _, _ ->
                    try {
                        val name = nameEditText.text?.toString()?.trim() ?: ""
                        Log.d(TAG, "Name entered: '$name'")
                        
                        if (name.isNotEmpty()) {
                            viewModel.createUser(name, role)
                        } else {
                            Log.w(TAG, "Empty name entered")
                            showToast("Please enter your name")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling name input: ${e.message}", e)
                        showToast("Error processing name: ${e.message}")
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Log.d(TAG, "Name input dialog cancelled")
                }
                .setOnDismissListener {
                    Log.d(TAG, "Name input dialog dismissed")
                }
                .show()
                
        } catch (e: Exception) {
            Log.e(TAG, "Error showing name input dialog: ${e.message}", e)
            showToast("Error showing dialog: ${e.message}")
        }
    }
    
    private fun navigateToRoleSpecificActivity(role: UserRole) {
        try {
            Log.d(TAG, "Navigating to role-specific activity for: $role")
            
            val intent = when (role) {
                UserRole.PUBLISHER -> Intent(this, PublisherActivity::class.java)
                UserRole.SUBSCRIBER -> Intent(this, SubscriberActivity::class.java)
            }
            
            Log.d(TAG, "Starting activity: ${intent.component?.className}")
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to role-specific activity: ${e.message}", e)
            showToast("Error navigating to activity: ${e.message}")
        }
    }
    
    private fun loadCurrentUser() {
        try {
            Log.d(TAG, "Loading current user...")
            viewModel.loadCurrentUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading current user: ${e.message}", e)
            showToast("Error loading user: ${e.message}")
        }
    }
} 