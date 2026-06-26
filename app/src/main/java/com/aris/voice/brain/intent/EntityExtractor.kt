package com.aris.voice.brain.intent

class EntityExtractor(private val appAliasProvider: AppAliasProvider) : IntentPipelineStage {
    override fun process(context: IntentContext) {
        val lowerInput = context.normalizedInput
        
        when (context.primaryIntent) {
            "LAUNCH_APP" -> {
                val cleanName = lowerInput.replace("open ", "").replace("launch ", "").replace("kholo", "").replace("chalu karo", "").trim()
                context.targetApplication = appAliasProvider.resolveAppName(cleanName)
            }
            "COMMUNICATION" -> {
                if (context.action == "MAKE_CALL") {
                    context.targetEntity = lowerInput.replace("call ", "").replace("dial ", "").replace("phone ", "").trim()
                } else if (context.action == "SEND_MESSAGE") {
                    if (lowerInput.contains("whatsapp")) {
                        context.targetApplication = "whatsapp"
                    }
                }
            }
            "SYSTEM_CONTROL" -> {
                context.targetEntity = when {
                    lowerInput.contains("torch") || lowerInput.contains("flashlight") -> "FLASHLIGHT"
                    lowerInput.contains("wifi") || lowerInput.contains("wi-fi") -> "WIFI"
                    lowerInput.contains("bluetooth") -> "BLUETOOTH"
                    else -> "UNKNOWN"
                }
            }
            "UI_INTERACTION" -> {
                if (context.action == "CLICK") {
                    context.targetEntity = lowerInput.replace("click", "").replace("tap", "").replace("press", "").trim()
                }
            }
        }
    }
}
