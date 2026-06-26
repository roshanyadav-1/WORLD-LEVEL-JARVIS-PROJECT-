package com.aris.voice.brain.intent

class ConfidenceCalculator : IntentPipelineStage {
    override fun process(context: IntentContext) {
        if (context.primaryIntent == "UNKNOWN") {
            context.confidenceScore = 0.3f
        } else {
            context.confidenceScore = 1.0f
        }
    }
}
