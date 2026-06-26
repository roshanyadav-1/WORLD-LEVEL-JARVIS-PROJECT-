package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class SetAlarmIntent : AppIntent {
    override val name: String = "SetAlarm"

    override fun description(): String =
        "Open the Clock app to set a new alarm."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "hour",
            type = "integer",
            required = true,
            description = "The hour for the alarm (0-23)."
        ),
        ParameterSpec(
            name = "minute",
            type = "integer",
            required = true,
            description = "The minute for the alarm (0-59)."
        ),
        ParameterSpec(
            name = "message",
            type = "string",
            required = false,
            description = "Optional label for the alarm."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val hour = (params["hour"] as? Number)?.toInt() ?: params["hour"]?.toString()?.toIntOrNull() ?: return null
            val minute = (params["minute"] as? Number)?.toInt() ?: params["minute"]?.toString()?.toIntOrNull() ?: 0
            val message = params["message"]?.toString()?.trim() ?: ""
            
            return Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                if (message.isNotEmpty()) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                }
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }

    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(AlarmClock.ACTION_SET_ALARM)
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
