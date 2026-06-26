package com.aris.voice.brain.world.providers

import com.aris.voice.domain.CapabilityState

interface ICapabilityStateProvider {
    fun getCapabilityState(): CapabilityState
}
