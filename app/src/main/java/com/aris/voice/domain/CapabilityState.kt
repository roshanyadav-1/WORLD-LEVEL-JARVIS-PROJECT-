package com.aris.voice.domain

data class CapabilityState(
    val accessibilityReady: Boolean = false,
    val cameraReady: Boolean = false,
    val microphoneReady: Boolean = false,
    val internetAvailable: Boolean = false,
    val bluetoothAvailable: Boolean = false,
    val locationAvailable: Boolean = false,
    val localLlmAvailable: Boolean = false,
    val cloudLlmAvailable: Boolean = false
)
