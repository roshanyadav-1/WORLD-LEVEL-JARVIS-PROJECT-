package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class OpenAppSettingsIntent : AppIntent {
    override val name: String = "OpenAppSettings"

    override fun description(): String =
        "Directly open system Settings page for a specified application package."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "package_name",
            type = "string",
            required = true,
            description = "The package name of the app (e.g., 'com.instagram.android')."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val packageName = params["package_name"]?.toString()?.trim().orEmpty()
            if (packageName.isEmpty()) return null
            return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.android.settings"))
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
