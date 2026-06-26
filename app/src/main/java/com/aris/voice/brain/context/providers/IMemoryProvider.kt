package com.aris.voice.brain.context.providers

import com.aris.voice.domain.MemoryData
import com.aris.voice.domain.UserIntent

interface IMemoryProvider {
    suspend fun getMemoryData(intent: UserIntent): MemoryData?
}
