package com.aris.voice.triggers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aris.voice.MainActivity
import com.aris.voice.R
import kotlinx.coroutines.*

class TriggerMonitoringService : Service() {

    private val TAG = "TriggerMonitoringSvc"
    private val chargingStateReceiver = ChargingStateReceiver()
    private var locationJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "TriggerMonitoringServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        registerReceivers()
        startLocationMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("A.R.I.S Automation Service")
            .setContentText("Monitoring location and time automations in background.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1339,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1339, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TriggerMonitoringService in foreground: ${e.message}", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        try {
            unregisterReceiver(chargingStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
        locationJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Trigger Monitoring Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun registerReceivers() {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(chargingStateReceiver, intentFilter)
        Log.d(TAG, "ChargingStateReceiver registered")
    }

    private fun startLocationMonitoring() {
        locationJob = serviceScope.launch {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val triggerManager = TriggerManager.getInstance(this@TriggerMonitoringService)
            
            while (isActive) {
                try {
                    val locationTriggers = triggerManager.getEnabledTriggers().filter { it.type == TriggerType.LOCATION_BASED }
                    if (locationTriggers.isNotEmpty()) {
                        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                            this@TriggerMonitoringService,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        
                        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                            this@TriggerMonitoringService,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        
                        if (hasFine || hasCoarse) {
                            val providers = locationManager.getProviders(true)
                            var bestLocation: android.location.Location? = null
                            for (provider in providers) {
                                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                                    bestLocation = loc
                                }
                            }
                            
                            if (bestLocation != null) {
                                val lat = bestLocation.latitude
                                val lng = bestLocation.longitude
                                Log.d(TAG, "Current Location: lat=$lat, lng=$lng, accuracy=${bestLocation.accuracy}")
                                
                                for (trigger in locationTriggers) {
                                    val tLat = trigger.locationLatitude ?: continue
                                    val tLng = trigger.locationLongitude ?: continue
                                    val radius = trigger.locationRadiusMeters ?: 200f
                                    
                                    val results = FloatArray(1)
                                    android.location.Location.distanceBetween(lat, lng, tLat, tLng, results)
                                    val distance = results[0]
                                    
                                    if (distance <= radius) {
                                        Log.i(TAG, "Location Trigger Fired! Within ${distance}m of targeted ${trigger.label ?: trigger.instruction}")
                                        // Execute trigger instruction and record execution
                                        triggerManager.executeInstruction(trigger.instruction)
                                        triggerManager.recordExecution(trigger)
                                    }
                                }
                            }
                        } else {
                            Log.w(TAG, "Location permission missing for active location-based triggers.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in location monitoring loop", e)
                }
                
                // Check location every 30 seconds
                delay(30000L)
            }
        }
    }
}
