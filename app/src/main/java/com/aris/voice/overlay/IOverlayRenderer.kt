package com.aris.voice.overlay

import com.aris.voice.core.ArisResult

/**
 * Visual overlay and subtitle presentation contract for ARIS visual indicators.
 */
interface IOverlayRenderer {
    /**
     * Renders a custom status or text message onto the screen overlay.
     */
    fun showFeedback(text: String, durationMs: Long): ArisResult<Unit>

    /**
     * Updates active loading, speech recognition, or task execution progress spinners.
     */
    fun updateStatusIndicator(statusText: String, progress: Float?): ArisResult<Unit>

    /**
     * Dismisses the active floating overlay panel.
     */
    fun dismissOverlay(): ArisResult<Unit>
}
