package com.aris.voice.triggers

import java.util.UUID

enum class TriggerType {
    SCHEDULED_TIME,
    NOTIFICATION,
    CHARGING_STATE,
    APP_OPENED,
    LOCATION_BASED,
    BATTERY_LEVEL,
    CALENDAR_EVENT,
    CONTACT_CALL,
    SCREEN_ON_OFF,
    COMPOUND
}

enum class CompoundLogic {
    AND,
    OR,
    NONE
}

data class Trigger(
    val id: String = UUID.randomUUID().toString(),
    val type: TriggerType,
    val instruction: String,
    var isEnabled: Boolean = true,
    
    // For SCHEDULED_TIME triggers
    val hour: Int? = null,
    val minute: Int? = null,
    
    // For NOTIFICATION and general app triggers
    val packageName: String? = null,
    val appName: String? = null, // For display purposes
    
    // For SCHEDULED_TIME triggers
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // Default to all days
    
    // For CHARGING_STATE triggers
    val chargingStatus: String? = null, // e.g., "Connected", "Disconnected"

    // Advanced Trigger Parameters
    val label: String? = null,
    val oneShotDisable: Boolean = false,
    
    // APP_OPENED Trigger parameters
    val targetAppPackage: String? = null,
    val targetAppName: String? = null,
    
    // LOCATION_BASED parameters
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationRadiusMeters: Float? = null,
    
    // BATTERY_LEVEL parameters
    val batteryThresholdPct: Int? = null,
    
    // CALENDAR_EVENT parameters
    val calendarEventTitle: String? = null,
    
    // CONTACT_CALL parameters
    val contactNameOrNumber: String? = null,
    
    // SCREEN_ON_OFF parameters
    val isScreenOnTrigger: Boolean? = null,
    
    // COMPOUND triggers parameters
    val compoundLogic: CompoundLogic = CompoundLogic.NONE,
    val subTriggerIds: List<String> = emptyList()
)
