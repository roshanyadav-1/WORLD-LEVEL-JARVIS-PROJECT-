package com.aris.voice.brain.world.providers

import com.aris.voice.domain.UiState

interface IUiStateProvider {
    fun getUiState(): UiState
}
