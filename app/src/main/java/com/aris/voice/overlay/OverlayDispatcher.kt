package com.aris.voice.overlay

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object OverlayDispatcher {

    private const val TAG = "OverlayDispatcher"

    private val _activeContent = MutableStateFlow<Map<OverlayPosition, OverlayContent>>(emptyMap())
    val activeContent: StateFlow<Map<OverlayPosition, OverlayContent>> = _activeContent.asStateFlow()

    /**
     * Request to show an overlay.
     * Returns a unique ID that you must use to remove it later (if indefinite).
     */
    fun show(text: String, priority: OverlayPriority, duration: Long = 0L, position: OverlayPosition = OverlayPosition.BOTTOM): String {
        try {
            Log.d(TAG, "show: $text, $priority, $duration, $position")
            val id = UUID.randomUUID().toString()
            val newContent = OverlayContent(id, text, priority, duration, position)
            
            val currentMap = _activeContent.value.toMutableMap()
            currentMap[position] = newContent
            _activeContent.value = currentMap
            
            return id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            return ""
        }
    }

    /**
     * Remove an overlay. You must pass the ID you got when you created it.
     * This prevents accidentally removing a different component's overlay.
     */
    fun dismiss(id: String) {
        if (id.isBlank()) return
        try {
            val currentMap = _activeContent.value.toMutableMap()
            val iterator = currentMap.iterator()
            var changed = false
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.id == id) {
                    iterator.remove()
                    changed = true
                }
            }
            if (changed) {
                _activeContent.value = currentMap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss overlay with id: $id", e)
        }
    }

    fun clearAll() {
        _activeContent.value = emptyMap()
    }
}