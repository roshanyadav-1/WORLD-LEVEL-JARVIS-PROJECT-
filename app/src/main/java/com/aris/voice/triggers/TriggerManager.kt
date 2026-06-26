package com.aris.voice.triggers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import java.util.Calendar

class TriggerManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    private val gson = Gson()

    fun hasConflict(newTrigger: Trigger): Boolean {
        val existing = loadTriggers()
        return existing.any { existingTrigger ->
            existingTrigger.id != newTrigger.id &&
            existingTrigger.type == newTrigger.type &&
            existingTrigger.hour == newTrigger.hour &&
            existingTrigger.minute == newTrigger.minute &&
            existingTrigger.daysOfWeek.intersect(newTrigger.daysOfWeek).isNotEmpty()
        }
    }

    fun addTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        triggers.add(trigger)
        saveTriggers(triggers)
        if (trigger.isEnabled) {
            scheduleAlarm(trigger)
        }
    }

    fun removeTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        val triggerToRemove = triggers.find { it.id == trigger.id }
        if (triggerToRemove != null) {
            cancelAlarm(triggerToRemove)
            triggers.remove(triggerToRemove)
            saveTriggers(triggers)
        }
    }

    fun getTriggers(): List<Trigger> {
        return loadTriggers()
    }

    fun getEnabledTriggers(): List<Trigger> {
        return loadTriggers().filter { it.isEnabled }
    }

    fun getTriggersByType(type: TriggerType): List<Trigger> {
        return loadTriggers().filter { it.type == type }
    }

    fun rescheduleTrigger(triggerId: String) {
        val triggers = loadTriggers()
        val trigger = triggers.find { it.id == triggerId }
        if (trigger != null && trigger.isEnabled) {
            scheduleAlarm(trigger)
            android.util.Log.d("TriggerManager", "Rescheduled trigger: ${trigger.id}")
        }
    }

    fun updateTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        val index = triggers.indexOfFirst { it.id == trigger.id }
        if (index != -1) {
            triggers[index] = trigger
            saveTriggers(triggers)
            if (trigger.isEnabled) {
                scheduleAlarm(trigger)
            } else {
                cancelAlarm(trigger)
            }
        }
    }

    fun executeInstruction(instruction: String) {
        android.util.Log.d("TriggerManager", "Executing instruction: $instruction")
        val intent = Intent(context, TriggerReceiver::class.java).apply {
            action = TriggerReceiver.ACTION_EXECUTE_TASK
            putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, instruction)
        }
        context.sendBroadcast(intent)
    }

    fun recordExecution(trigger: Trigger) {
        val history = loadExecutionHistory()
        val record = "${trigger.id}:${System.currentTimeMillis()}:${trigger.label ?: "Telemetry Trigger"}"
        history.add(0, record)
        while (history.size > 10) {
            history.removeAt(history.lastIndex)
        }
        saveExecutionHistory(history)

        // Handle one-shot triggers auto-disable
        if (trigger.oneShotDisable) {
            val updated = trigger.copy(isEnabled = false)
            updateTrigger(updated)
            android.util.Log.d("TriggerManager", "One-shot trigger ${trigger.id} disabled after firing.")
        }

        // Propagate / check compound triggers
        checkAndFireCompoundTriggers(trigger.id)
    }

    fun checkAndFireCompoundTriggers(firedTriggerId: String) {
        val triggers = loadTriggers()
        val compoundTriggers = triggers.filter { 
            it.isEnabled && it.type == TriggerType.COMPOUND && it.subTriggerIds.contains(firedTriggerId) 
        }

        for (compound in compoundTriggers) {
            val logic = compound.compoundLogic
            val subIds = compound.subTriggerIds
            val firedIdsInHistory = loadExecutionHistory().map { it.split(":").first() }
            
            val satisfies = when (logic) {
                CompoundLogic.AND -> {
                    // Verify if all required sub triggers have fired/run recently
                    subIds.all { id -> firedIdsInHistory.contains(id) }
                }
                CompoundLogic.OR -> {
                    // Any of them qualifies
                    true
                }
                CompoundLogic.NONE -> false
            }

            if (satisfies) {
                android.util.Log.d("TriggerManager", "Compound trigger conditions met! Dispatching '${compound.label}'")
                executeInstruction(compound.instruction)
                recordExecution(compound)
            }
        }
    }

    private fun scheduleAlarm(trigger: Trigger) {
        if (trigger.type != TriggerType.SCHEDULED_TIME) {
            return
        }

        val intent = Intent(context, TriggerReceiver::class.java).apply {
            action = TriggerReceiver.ACTION_EXECUTE_TASK
            putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, trigger.instruction)
            putExtra(TriggerReceiver.EXTRA_TRIGGER_ID, trigger.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            trigger.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTriggerTime = getNextTriggerTime(trigger.hour!!, trigger.minute!!, trigger.daysOfWeek)
        if (nextTriggerTime == null) {
            android.util.Log.w("TriggerManager", "No valid calendar day computed for trigger: ${trigger.id}")
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime.timeInMillis,
                    pendingIntent
                )
            } else {
                android.util.Log.w("TriggerManager", "Exact alarm permission missing. Scheduling inexact window fallback.")
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime.timeInMillis,
                    15 * 60 * 1000L, // 15-minute tolerance window
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerTime.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun getNextTriggerTime(hour: Int, minute: Int, daysOfWeek: Set<Int>): Calendar? {
        val now = Calendar.getInstance()
        var nextTrigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (i in 0..7) {
            val day = (now.get(Calendar.DAY_OF_WEEK) + i - 1) % 7 + 1
            if (day in daysOfWeek) {
                nextTrigger.add(Calendar.DAY_OF_YEAR, i)
                if (nextTrigger.after(now)) {
                    return nextTrigger
                }
                nextTrigger.add(Calendar.DAY_OF_YEAR, -i)
            }
        }
        return null
    }

    private fun cancelAlarm(trigger: Trigger) {
        if (trigger.type != TriggerType.SCHEDULED_TIME) {
            return
        }
        val intent = Intent(context, TriggerReceiver::class.java).apply {
            action = TriggerReceiver.ACTION_EXECUTE_TASK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            trigger.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private val lock = Any()

    private fun saveTriggers(triggers: List<Trigger>) {
        synchronized(lock) {
            val json = gson.toJson(triggers)
            sharedPreferences.edit().putString(KEY_TRIGGERS, json).apply()
        }
    }

    private fun loadTriggers(): MutableList<Trigger> {
        return synchronized(lock) {
            val json = sharedPreferences.getString(KEY_TRIGGERS, null)
            if (json != null) {
                val type = object : TypeToken<MutableList<Trigger>>() {}.type
                try {
                    gson.fromJson(json, type)
                } catch(e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
        }
    }

    private fun saveExecutionHistory(history: List<String>) {
        synchronized(lock) {
            val json = gson.toJson(history)
            sharedPreferences.edit().putString("trigger_execution_history", json).apply()
        }
    }

    fun loadExecutionHistory(): MutableList<String> {
        return synchronized(lock) {
            val json = sharedPreferences.getString("trigger_execution_history", null)
            if (json != null) {
                val type = object : TypeToken<MutableList<String>>() {}.type
                try {
                    gson.fromJson(json, type)
                } catch(e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
        }
    }

    fun createTriggerFromNaturalLanguage(utterance: String): Trigger? {
        val lower = utterance.lowercase().trim()
        
        // Match expressions signifying automation/triggers/scheduling
        val hasTriggerKeyword = lower.contains("trigger") || lower.contains("schedule") || 
                               lower.contains("automation") || lower.contains("set a routine") ||
                               lower.contains("alarm at") || lower.contains("when i") || lower.contains("at ")
        if (!hasTriggerKeyword) {
            return null
        }

        // Parse Time: e.g. "10:30 am", "5 pm", "17:00", "at 18:30"
        var hour: Int? = null
        var minute: Int? = null
        var isTimeTrigger = false
        
        // Pattern matches format: 10:30 am, 10:30pm, 5 pm, 5pm, 10 am
        val timeRegex = "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?".toRegex()
        val match = timeRegex.find(lower)
        if (match != null) {
            try {
                var h = match.groupValues[1].toInt()
                val m = if (match.groupValues[2].isNotEmpty()) match.groupValues[2].toInt() else 0
                val amPm = match.groupValues[3]
                
                if (amPm == "pm" && h < 12) h += 12
                if (amPm == "am" && h == 12) h = 0
                
                if (h in 0..23 && m in 0..59) {
                    hour = h
                    minute = m
                    isTimeTrigger = true
                }
            } catch (e: Exception) {
                android.util.Log.e("TriggerManager", "Failed parsing time in natural language voice input", e)
            }
        }

        // Parse Location: latitude, longitude patterns
        var lat: Double? = null
        var lng: Double? = null
        val latRegex = "(?:lat|latitude)\\s*(-?\\d+\\.\\d+)".toRegex()
        val lngRegex = "(?:lon|lng|longitude)\\s*(-?\\d+\\.\\d+)".toRegex()
        
        val latMatch = latRegex.find(lower)
        val lngMatch = lngRegex.find(lower)
        if (latMatch != null && lngMatch != null) {
            lat = latMatch.groupValues[1].toDoubleOrNull()
            lng = lngMatch.groupValues[1].toDoubleOrNull()
        }

        // Clean and isolate target action/instruction
        var action = utterance
        val stripPrefixes = listOf(
            "create a trigger to", "create trigger to", "create an automation to", "create automation to",
            "schedule a task to", "schedule to", "schedule", "set a trigger to", "set trigger to", "when i reach", "when i enter"
        )
        for (prefix in stripPrefixes) {
            if (action.lowercase().startsWith(prefix)) {
                action = action.substring(prefix.length).trim()
            }
        }

        // Strip location or time details from trigger action name to prevent redundant descriptions
        action = action.replace(timeRegex, "").trim()
        action = action.replace(latRegex, "").trim()
        action = action.replace(lngRegex, "").trim()
        action = action.replace("at latitude", "", ignoreCase = true)
        action = action.replace("and longitude", "", ignoreCase = true)
        action = action.replace("at", "", ignoreCase = true)
        action = action.replace("to", "", ignoreCase = true)
        action = action.trim()

        if (action.isEmpty()) {
            action = "open maps" // generic fallback
        }

        if (lat != null && lng != null) {
            return Trigger(
                type = TriggerType.LOCATION_BASED,
                instruction = action,
                locationLatitude = lat,
                locationLongitude = lng,
                locationRadiusMeters = 200f,
                label = "Voice Geo-Automation: $action"
            )
        } else if (isTimeTrigger) {
            return Trigger(
                type = TriggerType.SCHEDULED_TIME,
                instruction = action,
                hour = hour,
                minute = minute,
                label = "Voice Scheduled Automation: $action"
            )
        }

        return null
    }

    companion object {
        private const val PREFS_NAME = "com.aris.voice.triggers.prefs"
        private const val KEY_TRIGGERS = "triggers_list"

        @Volatile
        private var INSTANCE: TriggerManager? = null

        fun getInstance(context: Context): TriggerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TriggerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
