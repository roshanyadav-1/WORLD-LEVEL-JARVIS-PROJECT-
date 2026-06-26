package com.aris.voice.brain.intent

class IntentTokenizer : IntentPipelineStage {
    override fun process(context: IntentContext) {
        context.tokens.addAll(context.normalizedInput.split(Regex("\\s+")))
    }
}
