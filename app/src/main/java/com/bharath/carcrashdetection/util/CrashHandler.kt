package com.bharath.carcrashdetection.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced crash handler to prevent app crashes and provide better error reporting
 */
class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {
    
    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_LOG_FILE = "crash_log.txt"
        
        @Volatile
        private var instance: CrashHandler? = null
        
        fun getInstance(): CrashHandler {
            return instance ?: synchronized(this) {
                instance ?: CrashHandler().also { instance = it }
            }
        }
    }
    
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var context: Context? = null
    
    fun init(context: Context) {
        this.context = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        Log.d(TAG, "Crash handler initialized")
    }
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "Uncaught exception in thread: ${thread.name}", throwable)
            
            // Log crash details
            logCrash(thread, throwable)
            
            // Show user-friendly error message
            showUserFriendlyError(throwable)
            
            // Give user time to see the message
            Thread.sleep(2000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler: ${e.message}", e)
        } finally {
            // Call default handler if available
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    private fun logCrash(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val crashInfo = buildString {
                appendLine("=== CRASH REPORT ===")
                appendLine("Timestamp: $timestamp")
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: ${throwable.javaClass.simpleName}")
                appendLine("Message: ${throwable.message}")
                appendLine("Stack trace:")
                appendLine(getStackTraceString(throwable))
                appendLine("=== END CRASH REPORT ===")
            }
            
            Log.e(TAG, crashInfo)
            
            // Save to file if context is available
            context?.let { ctx ->
                try {
                    val file = File(ctx.filesDir, CRASH_LOG_FILE)
                    FileWriter(file, true).use { writer ->
                        PrintWriter(writer).use { printer ->
                            printer.println(crashInfo)
                            printer.println()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save crash log to file: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging crash: ${e.message}")
        }
    }
    
    private fun getStackTraceString(throwable: Throwable): String {
        return try {
            val sw = java.io.StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            sw.toString()
        } catch (e: Exception) {
            "Failed to get stack trace: ${e.message}"
        }
    }
    
    private fun showUserFriendlyError(throwable: Throwable) {
        try {
            context?.let { ctx ->
                val errorMessage = when {
                    throwable is OutOfMemoryError -> "The app ran out of memory. Please restart the app."
                    throwable is SecurityException -> "A security error occurred. Please check app permissions."
                    throwable.message?.contains("database", ignoreCase = true) == true -> "A database error occurred. Please restart the app."
                    throwable.message?.contains("network", ignoreCase = true) == true -> "A network error occurred. Please check your connection."
                    else -> "An unexpected error occurred. Please restart the app."
                }
                
                // Show toast on main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        Toast.makeText(ctx, errorMessage, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to show error toast: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing user-friendly error: ${e.message}")
        }
    }
    
    /**
     * Check if crash log file exists and return its contents
     */
    fun getCrashLog(): String? {
        return try {
            context?.let { ctx ->
                val file = File(ctx.filesDir, CRASH_LOG_FILE)
                if (file.exists()) {
                    file.readText()
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading crash log: ${e.message}")
            null
        }
    }
    
    /**
     * Clear crash log file
     */
    fun clearCrashLog() {
        try {
            context?.let { ctx ->
                val file = File(ctx.filesDir, CRASH_LOG_FILE)
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Crash log cleared")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing crash log: ${e.message}")
        }
    }
}
