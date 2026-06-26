package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddCalendarEventIntent : AppIntent {
    override val name: String = "AddCalendarEvent"

    override fun description(): String =
        "Directly add an event or meeting scheduled on calendar with description, location, or dynamic times."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "title",
            type = "string",
            required = true,
            description = "The title or name of the calendar event/meeting (e.g., 'Doctor appointment', 'Sync session')."
        ),
        ParameterSpec(
            name = "description",
            type = "string",
            required = false,
            description = "Optional detailed description or meeting details."
        ),
        ParameterSpec(
            name = "location",
            type = "string",
            required = false,
            description = "Optional physical location or meeting link."
        ),
        ParameterSpec(
            name = "begin_time",
            type = "string",
            required = false,
            description = "Start date/time of the event. Can be in ISO format 'YYYY-MM-DD HH:MM' or Unix MS timestamp. Defaults to current time."
        ),
        ParameterSpec(
            name = "end_time",
            type = "string",
            required = false,
            description = "End date/time of the event. Can be in ISO format 'YYYY-MM-DD HH:MM' or Unix MS timestamp. Defaults to 1 hour after begin_time."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val title = params["title"]?.toString()?.trim().orEmpty()
            if (title.isEmpty()) return null

            val description = params["description"]?.toString()?.trim().orEmpty()
            val location = params["location"]?.toString()?.trim().orEmpty()
            val beginTimeStr = params["begin_time"]?.toString()?.trim().orEmpty()
            val endTimeStr = params["end_time"]?.toString()?.trim().orEmpty()

            val defaultBeginTime = System.currentTimeMillis()
            var beginTime = defaultBeginTime
            if (beginTimeStr.isNotEmpty()) {
                beginTime = beginTimeStr.toLongOrNull() ?: parseDateTime(beginTimeStr) ?: defaultBeginTime
            }

            var endTime = beginTime + 60 * 60 * 1000L // 1 hour duration default
            if (endTimeStr.isNotEmpty()) {
                endTime = endTimeStr.toLongOrNull() ?: parseDateTime(endTimeStr) ?: (beginTime + 60 * 60 * 1000L)
            }

            return Intent(Intent.ACTION_INSERT).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                if (description.isNotEmpty()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, description)
                }
                if (location.isNotEmpty()) {
                    putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                }
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            }
        } catch (e: Exception) {
            return null
        }
    }

    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_INSERT).apply { data = CalendarContract.Events.CONTENT_URI }
        return dummyIntent.resolveActivity(context.packageManager) != null
    }

    private fun parseDateTime(dateTimeStr: String): Long? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault()),
            SimpleDateFormat("HH:mm", Locale.getDefault())
        )
        for (format in formats) {
            try {
                val date = format.parse(dateTimeStr) ?: continue
                // If only HH:mm is given, prefix with today's date
                if (format.toPattern() == "HH:mm") {
                    val today = Date()
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)
                    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse("$todayStr $dateTimeStr")?.time
                }
                return date.time
            } catch (e: Exception) {
                // Keep trying other formats
            }
        }
        return null
    }
}
