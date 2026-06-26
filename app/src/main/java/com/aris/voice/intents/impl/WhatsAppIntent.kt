package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class WhatsAppIntent : AppIntent {
    override val name: String = "WhatsApp"

    override fun description(): String =
        "Directly open WhatsApp to chat with a phone number (with country code, e.g. '919876543210') or prepare a pre-filled chat message."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "phone",
            type = "string",
            required = false,
            description = "The phone number with country code, no symbols (e.g. '919876543210')."
        ),
        ParameterSpec(
            name = "message",
            type = "string",
            required = false,
            description = "The pre-filled text message to auto-populate in the chat window."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val rawPhone = params["phone"]?.toString()?.trim().orEmpty()
            val phone = rawPhone.replace(Regex("[^0-9]"), "")
            val message = params["message"]?.toString()?.trim().orEmpty()

            if (phone.isEmpty()) {
                val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    return intent
                }
                return Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com/")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            val uriString = if (message.isNotEmpty()) {
                "whatsapp://send?phone=$phone&text=${Uri.encode(message)}"
            } else {
                "whatsapp://send?phone=$phone"
            }

            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val pm = context.packageManager
            if (intent.resolveActivity(pm) != null) {
                return intent
            }

            val webUriString = if (message.isNotEmpty()) {
                "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}"
            } else {
                "https://api.whatsapp.com/send?phone=$phone"
            }
            return Intent(Intent.ACTION_VIEW, Uri.parse(webUriString)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=123")).apply { setPackage("com.whatsapp") }
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=123"))
        return dummyIntent.resolveActivity(context.packageManager) != null || 
               fallbackIntent.resolveActivity(context.packageManager) != null
    }
}
