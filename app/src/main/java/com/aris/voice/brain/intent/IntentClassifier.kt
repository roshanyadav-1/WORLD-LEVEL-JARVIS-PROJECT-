package com.aris.voice.brain.intent

class IntentClassifier : IntentPipelineStage {
    override fun process(context: IntentContext) {
        val lowerInput = context.normalizedInput
        
        when {
            lowerInput.contains("open ") || lowerInput.contains("launch ") || lowerInput.contains("kholo") || lowerInput.contains("chalu karo") -> {
                context.primaryIntent = "LAUNCH_APP"
                context.action = "OPEN_APPLICATION"
            }
            lowerInput.contains("call ") || lowerInput.contains("dial ") || lowerInput.contains("phone ") -> {
                context.primaryIntent = "COMMUNICATION"
                context.action = "MAKE_CALL"
            }
            lowerInput.contains("message ") || lowerInput.contains("sms ") || lowerInput.contains("whatsapp ") -> {
                context.primaryIntent = "COMMUNICATION"
                context.action = "SEND_MESSAGE"
            }
            lowerInput.matches(Regex(".*\\d+\\s*[+\\-*/]\\s*\\d+.*")) -> {
                context.primaryIntent = "CALCULATION"
                context.action = "MATH_EVAL"
            }
            lowerInput.contains("torch") || lowerInput.contains("flashlight") || lowerInput.contains("wi-fi") || lowerInput.contains("wifi") || lowerInput.contains("bluetooth") -> {
                context.primaryIntent = "SYSTEM_CONTROL"
                val turnOn = lowerInput.contains("on") || lowerInput.contains("enable") || lowerInput.contains("jalo") || lowerInput.contains("chalu")
                context.action = if (turnOn) "ENABLE" else "DISABLE"
            }
            lowerInput.contains("click") || lowerInput.contains("tap") || lowerInput.contains("press") -> {
                context.primaryIntent = "UI_INTERACTION"
                context.action = "CLICK"
            }
            lowerInput.contains("type") || lowerInput.contains("write") || lowerInput.contains("enter") -> {
                context.primaryIntent = "UI_INTERACTION"
                context.action = "TYPE"
            }
            lowerInput.contains("swipe") || lowerInput.contains("scroll") -> {
                context.primaryIntent = "UI_INTERACTION"
                context.action = "SCROLL"
            }
            lowerInput.contains("alarm") || lowerInput.contains("wake me up") -> {
                context.primaryIntent = "ALARM"
                context.action = "SET_ALARM"
            }
            else -> {
                context.primaryIntent = "UNKNOWN"
            }
        }
    }
}
