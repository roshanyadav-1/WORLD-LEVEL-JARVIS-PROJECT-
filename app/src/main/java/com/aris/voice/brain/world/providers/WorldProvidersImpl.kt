package com.aris.voice.brain.world.providers

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.content.res.Configuration
import android.view.accessibility.AccessibilityManager
import com.aris.voice.domain.*
import com.aris.voice.perception.IDeviceStateMonitor
import com.aris.voice.ScreenInteractionService
import com.aris.voice.di.ArisServiceRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class DeviceStateProviderImpl(private val context: Context) : IDeviceStateProvider {
    override fun getDeviceState(): DeviceState {
        val deviceStateMonitor = try {
            ArisServiceRegistry.get(IDeviceStateMonitor::class.java)
        } catch (e: Exception) { null }
        
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val isCharging = batteryManager.isCharging
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val networkState = if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) "CONNECTED" else "DISCONNECTED"

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            else -> "NORMAL"
        }

        val orientation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"

        val activeApp = ScreenInteractionService.instance?.getForegroundAppPackageName()
        val foregroundActivity = ScreenInteractionService.instance?.getCurrentActivityName()

        return DeviceState(
            activeApplication = activeApp,
            foregroundActivity = foregroundActivity,
            isScreenOn = true,
            deviceOrientation = orientation,
            batteryPercentage = deviceStateMonitor?.getBatteryLevel() ?: batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            isBatteryCharging = isCharging,
            networkState = networkState,
            audioMode = audioMode
        )
    }
}

class UiStateProviderImpl(private val context: Context) : IUiStateProvider {
    override fun getUiState(): UiState {
        val hash = ScreenInteractionService.instance?.getWindowHierarchySignature() ?: "hash_default"
        
        return UiState(
            visibleHierarchy = emptyList(),
            clickableElements = emptyList(),
            editableFields = emptyList(),
            scrollableContainers = emptyList(),
            selectedElements = emptyList(),
            focusedElement = null
        )
    }
}

class EnvironmentProviderImpl(private val context: Context) : IEnvironmentProvider {
    override fun getEnvironmentData(): EnvironmentData {
        val deviceStateMonitor = try {
            ArisServiceRegistry.get(IDeviceStateMonitor::class.java)
        } catch (e: Exception) { null }
        
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = Date()

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val networkType = if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) "WIFI" else if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) "CELLULAR" else "UNKNOWN"

        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) permissions.add("RECORD_AUDIO")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) permissions.add("CAMERA")

        return EnvironmentData(
            time = timeFormat.format(now),
            date = dateFormat.format(now),
            isNetworkAvailable = isNetworkAvailable,
            networkType = networkType,
            clipboardText = deviceStateMonitor?.getLastClipboardEntry(),
            activeNotifications = deviceStateMonitor?.getActiveNotifications() ?: emptyList(),
            grantedPermissions = permissions,
            runningServices = listOf("ConversationalAgentService", "ScreenInteractionService")
        )
    }
}

class TaskStateProviderImpl : ITaskStateProvider {
    override fun getTaskState(): TaskState {
        val sessionManager = try {
            ArisServiceRegistry.get(com.aris.voice.runtime.IVoiceSessionManager::class.java)
        } catch (e: Exception) { null }
        
        val state = sessionManager?.currentSession?.state
        return when (state) {
            com.aris.voice.domain.VoiceSessionState.PROCESSING -> TaskState.THINKING
            com.aris.voice.domain.VoiceSessionState.ACTIVATED -> TaskState.LISTENING
            else -> TaskState.IDLE
        }
    }
}

class CapabilityStateProviderImpl(private val context: Context) : ICapabilityStateProvider {
    override fun getCapabilityState(): CapabilityState {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val accessibilityReady = am.isEnabled

        val microphoneReady = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val internetAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        return CapabilityState(
            accessibilityReady = accessibilityReady,
            cameraReady = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            microphoneReady = microphoneReady,
            internetAvailable = internetAvailable,
            bluetoothAvailable = false,
            locationAvailable = false,
            localLlmAvailable = false,
            cloudLlmAvailable = internetAvailable
        )
    }
}
