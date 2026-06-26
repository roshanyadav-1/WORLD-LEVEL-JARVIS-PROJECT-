package com.aris.voice.brain.world.providers

import com.aris.voice.domain.TaskState

interface ITaskStateProvider {
    fun getTaskState(): TaskState
}
