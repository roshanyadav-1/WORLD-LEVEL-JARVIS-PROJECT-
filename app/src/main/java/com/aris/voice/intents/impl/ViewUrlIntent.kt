package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec
import androidx.core.net.toUri

class ViewUrlIntent : AppIntent {
    override val name: String = "ViewUrl"

    override fun description(): String =
        "Open a web URL in the default browser."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "url",
            type = "string",
            required = true,
            description = "The HTTP/HTTPS URL to open."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            var url = params["url"]?.toString()?.trim().orEmpty()
            if (url.isEmpty()) return null
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            return Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_VIEW, "https://google.com".toUri())
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
