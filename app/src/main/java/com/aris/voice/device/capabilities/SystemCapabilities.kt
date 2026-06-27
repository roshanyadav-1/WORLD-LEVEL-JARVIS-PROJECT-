package com.aris.voice.device.capabilities

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.Settings
import com.aris.voice.device.DeviceCapability
import com.aris.voice.utilities.CommandResult

class SystemSettingsCapability : DeviceCapability {
    override val name = "SystemSettings"
    
    // Covers flashlight, wifi, bluetooth, airplane mode, location, dnd
    override val patterns = listOf(
        Regex("(?i).*(torch|flashlight|light|bulb|flash).*"),
        Regex("(?i).*(wi-fi|wifi).*"),
        Regex("(?i).*(bluetooth).*"),
        Regex("(?i).*(airplane|flight mode).*"),
        Regex("(?i).*(location|gps).*"),
        Regex("(?i).*(do not disturb|dnd).*")
    )
    
    override fun execute(context: Context, command: String, match: MatchResult): CommandResult {
        val lower = command.lowercase()
        val turnOn = lower.contains("turn on") || lower.contains("enable") || lower.contains("jalo") || lower.contains("on") || lower.contains("chalu")
        
        // Flashlight direct hardware control
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("light") || lower.contains("bulb") || lower.contains("flash")) {
            return try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return CommandResult(false)
                cameraManager.setTorchMode(cameraId, turnOn)
                CommandResult(true, if (turnOn) "Flashlight turned on" else "Flashlight turned off")
            } catch (e: Exception) {
                CommandResult(true, "Flashlight control is unavailable on this device.")
            }
        }
        
        // Settings panels
        val (action, speech) = when {
            lower.contains("wi-fi") || lower.contains("wifi") -> Settings.Panel.ACTION_WIFI to "Wi-Fi configuration"
            lower.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth settings"
            lower.contains("airplane") || lower.contains("flight mode") -> Settings.ACTION_AIRPLANE_MODE_SETTINGS to "Airplane mode panel"
            lower.contains("location") || lower.contains("gps") -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Location settings"
            lower.contains("disturb") || lower.contains("dnd") -> Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS to "Do Not Disturb panel"
            else -> return CommandResult(false)
        }
        
        val isExplicitStateChange = turnOn || lower.contains("turn off") || lower.contains("disable")
        
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            CommandResult(true, "Opening $speech.", isGoalCompleted = !isExplicitStateChange)
        } catch (e: Exception) {
            CommandResult(true, "Unable to open $speech.")
        }
    }
}
