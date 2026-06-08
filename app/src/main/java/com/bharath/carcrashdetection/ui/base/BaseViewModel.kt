package com.bharath.carcrashdetection.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log

abstract class BaseViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "BaseViewModel"
    }
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage
    
    private val _successMessage = MutableSharedFlow<String>()
    val successMessage: SharedFlow<String> = _successMessage
    
    protected fun showLoading() {
        try {
            Log.d(TAG, "Showing loading state")
            _isLoading.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error showing loading: ${e.message}", e)
        }
    }
    
    protected fun hideLoading() {
        try {
            Log.d(TAG, "Hiding loading state")
            _isLoading.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding loading: ${e.message}", e)
        }
    }
    
    protected fun showError(message: String?) {
        try {
            val errorMsg = message ?: "An unknown error occurred"
            Log.e(TAG, "Showing error: $errorMsg")
            viewModelScope.launch {
                _errorMessage.emit(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error message: ${e.message}", e)
        }
    }
    
    protected fun showSuccess(message: String?) {
        try {
            val successMsg = message ?: "Operation completed successfully"
            Log.d(TAG, "Showing success: $successMsg")
            viewModelScope.launch {
                _successMessage.emit(successMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing success message: ${e.message}", e)
        }
    }
    
    protected fun launchWithLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting operation with loading state")
                showLoading()
                block()
                Log.d(TAG, "Operation completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Operation failed: ${e.message}", e)
                showError(e.message ?: "An error occurred during operation")
            } finally {
                Log.d(TAG, "Operation finished, hiding loading state")
                hideLoading()
            }
        }
    }
    
    override fun onCleared() {
        try {
            Log.d(TAG, "ViewModel being cleared")
            super.onCleared()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing ViewModel: ${e.message}", e)
        }
    }
} 