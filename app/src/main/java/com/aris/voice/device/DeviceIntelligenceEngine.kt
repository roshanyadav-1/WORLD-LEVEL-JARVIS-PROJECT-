package com.aris.voice.device

import android.content.Context
import com.aris.voice.utilities.CommandResult
import com.aris.voice.device.capabilities.*

/**
 * Universal Deterministic Device Intelligence Engine.
 * Replaces the legacy, monolithic OfflineCommandProcessor with a scalable, regex-based capability architecture.
 */
class DeviceIntelligenceEngine(private val context: Context) {

    private val capabilities: List<DeviceCapability> = listOf(
        MathCapability(),
        SystemSettingsCapability(),
        TimeAndAlarmCapability(),
        MediaControlCapability(),
        DiagnosticsCapability(),
        CommunicationCapability(),
        NavigationCapability(),
        AppLaunchCapability()
    )

    fun processCommand(command: String): CommandResult {
        val lowerCommand = command.lowercase(java.util.Locale.getDefault()).trim()

        for (capability in capabilities) {
            for (pattern in capability.patterns) {
                val match = pattern.find(lowerCommand)
                if (match != null) {
                    return try {
                        val result = capability.execute(context, lowerCommand, match)
                        if (result.isHandled) return result
                        else continue // Try next pattern if not fully handled
                    } catch (e: Exception) {
                        android.util.Log.e("DeviceIntelligence", "Capability ${capability.name} failed", e)
                        CommandResult(true, "I encountered an issue executing that command.")
                    }
                }
            }
        }
        
        return CommandResult(false)
    }
}
