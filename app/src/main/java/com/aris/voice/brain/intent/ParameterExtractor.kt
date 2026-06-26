package com.aris.voice.brain.intent

class ParameterExtractor : IntentPipelineStage {
    override fun process(context: IntentContext) {
        val lowerInput = context.normalizedInput
        
        when (context.primaryIntent) {
            "COMMUNICATION" -> {
                if (context.action == "SEND_MESSAGE") {
                    val split = lowerInput.split("saying", "that")
                    if (split.size > 1) {
                        context.parameters["message_body"] = split[1].trim()
                    }
                }
            }
            "CALCULATION" -> {
                context.parameters["expression"] = lowerInput
            }
            "UI_INTERACTION" -> {
                if (context.action == "TYPE") {
                    context.parameters["text"] = lowerInput.replace("type", "").replace("write", "").replace("enter", "").trim()
                } else if (context.action == "SCROLL") {
                    if (lowerInput.contains("up")) context.constraints.add("DIRECTION_UP")
                    if (lowerInput.contains("down")) context.constraints.add("DIRECTION_DOWN")
                }
            }
            "ALARM" -> {
                context.parameters["time"] = lowerInput
            }
        }
    }
}
