package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class SendSMSIntent : AppIntent {
    override val name: String = "SendSMS"

    override fun description(): String =
        "Open the SMS app to send a message to a specific phone number."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "phone",
            type = "string",
            required = true,
            description = "The phone number to send the SMS to."
        ),
        ParameterSpec(
            name = "message",
            type = "string",
            required = true,
            description = "The SMS message body."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val phone = params["phone"]?.toString()?.trim() ?: return null
            val message = params["message"]?.toString()?.trim() ?: return null
            
            val sanitizedPhone = phone.replace(Regex("[^0-9+\\-*#]"), "")
            if (sanitizedPhone.isEmpty()) return null
            
            return Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$sanitizedPhone")).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:123"))
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
