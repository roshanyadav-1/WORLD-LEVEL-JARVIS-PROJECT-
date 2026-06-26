package com.aris.voice.domain

/**
 * Structured snapshot of the device context, used during observation.
 */
data class DeviceContext(
    val currentAppPackage: String?,
    val currentScreenLabel: String?,
    val visibleUiTextElements: List<String> = emptyList(),
    val runningBackgroundTasks: List<String> = emptyList(),
    val hasInternetConnection: Boolean = false,
    val batteryPercentage: Int = 100,
    val activeNotifications: List<String> = emptyList(),
    val clipboardText: String? = null
)
