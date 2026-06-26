package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec
import androidx.core.net.toUri

class EmailComposeIntent : AppIntent {
    override val name: String = "EmailCompose"

    override fun description(): String =
        "Compose a new email using the default email application. Supports specifying recipients, subject, and body."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec("to", "string", false, "Comma-separated email recipients."),
        ParameterSpec("subject", "string", false, "Email subject."),
        ParameterSpec("body", "string", false, "Email body text.")
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val to = params["to"]?.toString()?.trim().orEmpty()
            val recipients = if (to.isNotBlank()) {
                to.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
            } else {
                emptyArray()
            }
            return Intent(Intent.ACTION_SENDTO).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("mailto:")
                if (recipients.isNotEmpty()) {
                    putExtra(Intent.EXTRA_EMAIL, recipients)
                }
                params["subject"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                    putExtra(Intent.EXTRA_SUBJECT, it)
                }
                params["body"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                    putExtra(Intent.EXTRA_TEXT, it)
                }
            }
        } catch (e: Exception) {
            return null
        }
    }

    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
