package com.aris.voice.brain.intent

class CapabilityDetector : IntentPipelineStage {
    override fun process(context: IntentContext) {
        when (context.primaryIntent) {
            "COMMUNICATION" -> {
                if (context.action == "MAKE_CALL") {
                    context.requiredCapabilities.add("TELEPHONY")
                } else if (context.action == "SEND_MESSAGE") {
                    if (context.targetApplication == "whatsapp") {
                        context.requiredCapabilities.add("WHATSAPP_INSTALLED")
                    } else {
                        context.requiredCapabilities.add("SMS")
                    }
                }
            }
            "UI_INTERACTION" -> {
                context.requiredCapabilities.add("ACCESSIBILITY")
            }
        }
    }
}
