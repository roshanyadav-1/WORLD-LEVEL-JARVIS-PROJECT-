package com.aris.voice.brain.world.providers

import com.aris.voice.domain.EnvironmentData

interface IEnvironmentProvider {
    fun getEnvironmentData(): EnvironmentData
}
