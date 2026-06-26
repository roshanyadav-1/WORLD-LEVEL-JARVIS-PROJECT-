package com.aris.voice.adapters

import com.aris.voice.core.ArisResult

interface IVoiceAdapter {
    suspend fun speak(text: String): ArisResult<Unit>
    suspend fun listen(): ArisResult<String>
    fun stopSpeaking()
}

interface IAccessibilityAdapter {
    suspend fun getScreenElements(): ArisResult<List<String>>
    suspend fun clickElement(elementId: String): ArisResult<Unit>
    suspend fun scroll(direction: String): ArisResult<Unit>
    suspend fun goBack(): ArisResult<Unit>
    suspend fun goHome(): ArisResult<Unit>
}

interface IActionAdapter {
    suspend fun executeAction(action: String, params: Map<String, Any>): ArisResult<Any>
}

interface ITriggerAdapter {
    suspend fun registerTrigger(triggerId: String, condition: String): ArisResult<Unit>
    suspend fun unregisterTrigger(triggerId: String): ArisResult<Unit>
}

interface IOverlayAdapter {
    suspend fun showOverlay(content: String): ArisResult<Unit>
    suspend fun hideOverlay(): ArisResult<Unit>
}

interface IMemoryStorageAdapter {
    suspend fun save(key: String, value: String): ArisResult<Unit>
    suspend fun load(key: String): ArisResult<String?>
    suspend fun delete(key: String): ArisResult<Unit>
}
