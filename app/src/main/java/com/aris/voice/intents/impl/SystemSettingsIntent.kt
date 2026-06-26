package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class SystemSettingsIntent : AppIntent {
    override val name: String = "SystemSettings"

    override fun description(): String =
        "Directly open specific system settings pages, bypassing multi-step UI navigations."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "screen",
            type = "string",
            required = true,
            description = "The settings screen to open. Supported values: 'wifi', 'bluetooth', 'battery', 'display', 'location', 'accessibility', 'main'."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val screen = params["screen"]?.toString()?.lowercase()?.trim() ?: "main"
            val action = when (screen) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            return Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }

    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Settings.ACTION_SETTINGS)
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
