package com.aris.voice.brain.intent

interface IntentPipelineStage {
    fun process(context: IntentContext)
}
