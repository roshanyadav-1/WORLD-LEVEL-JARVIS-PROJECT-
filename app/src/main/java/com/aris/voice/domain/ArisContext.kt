package com.aris.voice.domain

/**
 * Structured snapshot of all information required for reasoning.
 */
data class ArisContext(
    val userIntent: UserIntent,
    val deviceState: DeviceState,
    val worldModelData: WorldModelData?,
    val memoryData: MemoryData?,
    val environment: EnvironmentData
)
