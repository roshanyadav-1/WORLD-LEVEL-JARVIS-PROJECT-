package com.aris.voice.device.capabilities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.MediaStore
import com.aris.voice.device.DeviceCapability
import com.aris.voice.utilities.CommandResult

class NavigationCapability : DeviceCapability {
    override val name = "Navigation"
    override val patterns = listOf(
        Regex("(?i).*(navigate|location|maps|rasta|map kholo).*")
    )
    
    override fun execute(context: Context, command: String, match: MatchResult): CommandResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=navigation"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return CommandResult(true, "Opening Maps")
    }
}

class DiagnosticsCapability : DeviceCapability {
    override val name = "Diagnostics"
    override val patterns = listOf(
        Regex("(?i).*(battery|charging).*"),
        Regex("(?i).*(camera|photo|screenshot).*")
    )
    
    override fun execute(context: Context, command: String, match: MatchResult): CommandResult {
        val lower = command.lowercase()
        
        if (lower.contains("battery") || lower.contains("charging")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return CommandResult(true, "Your battery is currently at $batLevel percent.")
        }
        
        if (lower.contains("camera") || lower.contains("photo")) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                CommandResult(true, "Opening camera viewfinder.")
            } catch (e: Exception) {
                CommandResult(true, "Failed to open camera.")
            }
        }
        
        return CommandResult(false)
    }
}
