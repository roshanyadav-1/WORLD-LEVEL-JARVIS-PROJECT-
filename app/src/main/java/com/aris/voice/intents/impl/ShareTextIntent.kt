package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class ShareTextIntent : AppIntent {
    override val name: String = "ShareText"

    override fun description(): String =
        "Open the system share sheet to send text. Use this when you want to share text to another app."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "text",
            type = "string",
            required = true,
            description = "The text to share."
        ),
        ParameterSpec(
            name = "chooser_title",
            type = "string",
            required = false,
            description = "Optional chooser title shown on the share sheet."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val text = params["text"]?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return null
            val base = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val title = params["chooser_title"]?.toString()?.takeIf { it.isNotBlank() } ?: "Share via"
            return Intent.createChooser(base, title).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
