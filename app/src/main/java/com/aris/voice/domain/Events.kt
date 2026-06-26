package com.aris.voice.domain

import com.aris.voice.core.ArisEvent

// --- SYSTEM EVENTS DECLARED IN DOMAIN ---

data class UserIntentDetected(
    val input: String,
    val goal: UserIntent,
    override val timestamp: Long = System.currentTimeMillis(),
    override val origin: String = "Brain.IntentAnalyzer"
) : ArisEvent

data class ActionStarted(
    val planId: String,
    val step: PlanStep,
    override val timestamp: Long = System.currentTimeMillis(),
    override val origin: String = "Actions.Executor"
) : ArisEvent

data class ActionCompleted(
    val planId: String,
    val step: PlanStep,
    val result: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val origin: String = "Actions.Executor"
) : ArisEvent

data class ErrorOccurred(
    val code: String,
    val errorMessage: String,
    val isFatal: Boolean,
    override val timestamp: Long = System.currentTimeMillis(),
    override val origin: String
) : ArisEvent
