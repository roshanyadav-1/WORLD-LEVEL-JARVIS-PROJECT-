package com.aris.voice.brain.intent

class ClarificationChecker : IntentPipelineStage {
    override fun process(context: IntentContext) {
        if (context.primaryIntent == "UNKNOWN" || context.confidenceScore < 0.5f) {
            context.isClarificationRequired = true
            context.clarificationReason = "I'm not exactly sure what action to take for this request."
        }
    }
}
