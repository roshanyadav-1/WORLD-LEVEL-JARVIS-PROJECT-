package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class OpenMapsIntent : AppIntent {
    override val name: String = "OpenMaps"

    override fun description(): String =
        "Open Maps app to search for a location or navigate to an address."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "location",
            type = "string",
            required = true,
            description = "The location, address, or coordinate to search for."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val location = params["location"]?.toString()?.trim() ?: return null
            if (location.isEmpty()) return null
            
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(location)}")
            return Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }

    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=London"))
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
