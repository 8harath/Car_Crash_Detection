package com.bharath.carcrashdetection.ui.base

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {
    
    protected lateinit var binding: VB
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = getViewBinding()
            setContentView(binding.root)
            
            setupViews()
            setupObservers()
        } catch (e: Exception) {
            // Fallback to prevent crashes
            setContentView(android.R.layout.simple_list_item_1)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    abstract fun getViewBinding(): VB
    
    abstract fun setupViews()
    
    abstract fun setupObservers()
    
    protected fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Fallback if toast fails
            android.util.Log.e("BaseActivity", "Toast failed: ${e.message}")
        }
    }
    
    protected fun showLongToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Fallback if toast fails
            android.util.Log.e("BaseActivity", "Toast failed: ${e.message}")
        }
    }
} 