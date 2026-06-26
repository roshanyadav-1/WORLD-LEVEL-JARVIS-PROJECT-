package com.aris.voice.brain.world.providers

import com.aris.voice.domain.DeviceState

interface IDeviceStateProvider {
    fun getDeviceState(): DeviceState
}
