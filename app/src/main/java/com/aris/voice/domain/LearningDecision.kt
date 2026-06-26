package com.aris.voice.domain

import java.util.UUID

data class LearningDecision(
    val decisionId: String = "ldec_${UUID.randomUUID()}",
    val timestamp: Long = System.currentTimeMillis(),
    val proposal: LearningProposal,
    val isApproved: Boolean,
    val reason: String
)
