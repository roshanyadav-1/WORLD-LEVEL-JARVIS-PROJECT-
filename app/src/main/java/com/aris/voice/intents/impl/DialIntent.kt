package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec
import androidx.core.net.toUri

/**
 * Opens the default dialer app with the given number filled.
 * Does NOT place the call automatically (uses ACTION_DIAL, no permission required).
 */
class DialIntent : AppIntent {
    override val name: String = "Dial"

    override fun description(): String =
        "Open the phone dialer with the specified phone number prefilled (no call is placed)."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "phone_number",
            type = "string",
            required = true,
            description = "The phone number to dial. Digits only or may include + and spaces."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val raw = params["phone_number"]?.toString()?.trim().orEmpty()
            if (raw.isEmpty()) return null
            
            // Remove spaces but preserve valid phone characters like + and -
            val sanitized = raw.replace(Regex("[^0-9+\\-*#]"), "")
            if (sanitized.isEmpty()) return null
            
            val uri = "tel:$sanitized".toUri()
            return Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_DIAL, "tel:123".toUri())
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}

