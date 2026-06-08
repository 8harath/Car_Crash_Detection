package com.bharath.carcrashdetection.util

import android.util.Log
import android.os.Build

object LogConfig {
    
    const val TAG_PREFIX = "CC_"
    
    // Enable debug logging for development
    private const val DEBUG_MODE = true
    
    // Log levels
    private const val VERBOSE = 0
    private const val DEBUG = 1
    private const val INFO = 2
    private const val WARN = 3
    private const val ERROR = 4
    
    // Current log level (set to DEBUG for development)
    private const val CURRENT_LOG_LEVEL = DEBUG
    
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE && CURRENT_LOG_LEVEL <= VERBOSE) {
            Log.v("$TAG_PREFIX$tag", message, throwable)
        }
    }
    
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE && CURRENT_LOG_LEVEL <= DEBUG) {
            Log.d("$TAG_PREFIX$tag", message, throwable)
        }
    }
    
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE && CURRENT_LOG_LEVEL <= INFO) {
            Log.i("$TAG_PREFIX$tag", message, throwable)
        }
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE && CURRENT_LOG_LEVEL <= WARN) {
            Log.w("$TAG_PREFIX$tag", message, throwable)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE && CURRENT_LOG_LEVEL <= ERROR) {
            Log.e("$TAG_PREFIX$tag", message, throwable)
        }
    }
    
    fun logSystemInfo() {
        i("System", "Android Version: ${Build.VERSION.RELEASE}")
        i("System", "SDK Level: ${Build.VERSION.SDK_INT}")
        i("System", "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        i("System", "Architecture: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
    }
    
    fun logCrashInfo(throwable: Throwable, context: String = "") {
        e("Crash", "Crash detected in context: $context", throwable)
        e("Crash", "Stack trace: ${Log.getStackTraceString(throwable)}")
        logSystemInfo()
    }
}
