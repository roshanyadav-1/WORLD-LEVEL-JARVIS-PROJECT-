package com.aris.voice.brain.intent

class IntentNormalizer : IntentPipelineStage {
    override fun process(context: IntentContext) {
        context.normalizedInput = context.rawInput.lowercase().trim()
    }
}
