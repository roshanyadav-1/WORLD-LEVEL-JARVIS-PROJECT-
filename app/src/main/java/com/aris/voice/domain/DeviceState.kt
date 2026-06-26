package com.aris.voice.domain

data class DeviceState(
    val activeApplication: String? = null,
    val foregroundActivity: String? = null,
    val isScreenOn: Boolean = true,
    val deviceOrientation: String = "PORTRAIT",
    val batteryPercentage: Int = 100,
    val isBatteryCharging: Boolean = false,
    val networkState: String = "DISCONNECTED",
    val audioMode: String = "NORMAL"
)
