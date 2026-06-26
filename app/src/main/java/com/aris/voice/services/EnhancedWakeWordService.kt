package com.aris.voice.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aris.voice.ConversationalAgentService
import com.aris.voice.MainActivity
import com.aris.voice.R
import com.aris.voice.api.PorcupineWakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class EnhancedWakeWordService : Service() {

    private var porcupineDetector: PorcupineWakeWordDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "EnhancedWakeWordServiceChannel"
        private val _isRunning = AtomicBoolean(false)
        var isRunning: Boolean
            get() = _isRunning.get()
            set(value) = _isRunning.set(value)

        const val ACTION_WAKE_WORD_FAILED = "com.aris.voice.WAKE_WORD_FAILED"
        const val EXTRA_USE_PORCUPINE = "use_porcupine"
    }

    private fun safeStartForeground() {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val initialText = "🎙️ Listening for 'A.R.I.S'..."
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("A.R.I.S Wake Word")
            .setContentText(initialText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val hasMicrophonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasMicrophonePermission) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        startForeground(
                            1338,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } catch (e: SecurityException) {
                        Log.w("EnhancedWakeWordService", "Failed to start with microphone type, falling back to specialUse", e)
                        startForeground(
                            1338,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        startForeground(
                            1338,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } catch (e: Exception) {
                        startForeground(1338, notification)
                    }
                } else {
                    startForeground(1338, notification)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        1338,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(1338, notification)
                }
            }
        } catch (e: Exception) {
            Log.e("EnhancedWakeWordService", "Failed to start foreground safely: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("EnhancedWakeWordService", "Service onCreate() called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("EnhancedWakeWordService", "Service starting...")
        
        safeStartForeground()
        
        // Check if we have the required RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("EnhancedWakeWordService", "RECORD_AUDIO permission not granted. Cannot start foreground service.")
            Toast.makeText(this, "Microphone permission required for wake word", Toast.LENGTH_LONG).show()
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        // Set isRunning = true only after successfully entering foreground (BUG-2)
        isRunning = true

        // Acquire partial wake lock for Doze Mode (BUG-13 & IMP-6)
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Aris:WakeWordLock").apply {
                acquire(10 * 60 * 1000L) // 10 minutes timeout, will release / renew as needed
            }
        } catch (e: Exception) {
            Log.e("EnhancedWakeWordService", "Failed to acquire wake lock: ${e.message}")
        }

        // Start the appropriate wake word detector
        startWakeWordDetection()

        return START_STICKY
    }

    private fun updateNotification(state: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val stateText = when(state) {
            "listening" -> "🎙️ Listening for 'A.R.I.S'..."
            "detected" -> "✅ 'A.R.I.S' detected! Starting..."
            "failed" -> "❌ Wake word failed. Tap to retry."
            "shortcut_only" -> "⚙️ Shortcut Mode: Vol Down + Power active"
            else -> "🔇 Inactive"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("A.R.I.S Wake Word")
            .setContentText(stateText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1338, notification)
    }

    private fun startWakeWordDetection() {
        val onWakeWordDetected: () -> Unit = {
            // Check if the conversational agent isn't already running in thread-safe way (BUG-8)
            if (!ConversationalAgentService.isRunning) {
                updateNotification("detected")
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "A.R.I.S listening...", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("EnhancedWakeWordService", "Conversational agent is already running.")
            }
        }

        val onApiFailure: () -> Unit = {
            Log.e("EnhancedWakeWordService", "Wake Word detection failed! Falling back to Shortcut-Only Mode.")
            updateNotification("shortcut_only")
        }

        serviceScope.launch {
            try {
                val keyManager = com.aris.voice.api.PicovoiceKeyManager(this@EnhancedWakeWordService)
                val accessKey = keyManager.getAccessKey()
                if (accessKey.isNullOrBlank()) {
                    Log.d("EnhancedWakeWordService", "No Picovoice API key found. Running in Shortcut-Only Mode.")
                    updateNotification("shortcut_only")
                } else {
                    Log.d("EnhancedWakeWordService", "Using Porcupine wake word detection")
                    porcupineDetector = PorcupineWakeWordDetector(this@EnhancedWakeWordService, onWakeWordDetected, onApiFailure)
                    porcupineDetector?.start()
                    updateNotification("listening")
                }
            } catch (e: Exception) {
                Log.e("EnhancedWakeWordService", "Error starting wake word detection: ${e.message}")
                onApiFailure()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("EnhancedWakeWordService", "Service onDestroy() called")

        porcupineDetector?.stop()
        porcupineDetector = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("EnhancedWakeWordService", "Failed to release wake lock: ${e.message}")
        }
        wakeLock = null

        serviceScope.cancel()
        
        isRunning = false
        Log.d("EnhancedWakeWordService", "Service destroyed, isRunning set to false")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Enhanced Wake Word Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
