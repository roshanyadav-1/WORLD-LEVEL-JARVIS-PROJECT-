package com.aris.voice.domain

data class WorldModelData(
    val deviceState: DeviceState,
    val uiState: UiState,
    val environment: EnvironmentData,
    val taskState: TaskState,
    val capabilityState: CapabilityState
)
