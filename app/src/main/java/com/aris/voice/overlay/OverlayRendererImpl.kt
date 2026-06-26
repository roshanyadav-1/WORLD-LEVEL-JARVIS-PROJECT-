package com.aris.voice.overlay

import android.content.Context
import android.widget.Toast
import com.aris.voice.core.ArisResult
import java.io.File

class OverlayRendererImpl(private val context: Context) : IOverlayRenderer {

    override fun showFeedback(text: String, durationMs: Long): ArisResult<Unit> {
        try {
            OverlayDispatcher.show(
                text = text,
                priority = OverlayPriority.TASKS,
                duration = durationMs,
                position = OverlayPosition.TOP
            )
            return ArisResult.Success(Unit)
        } catch (e: Exception) {
            // Fallback to standard Toast
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            return ArisResult.Success(Unit)
        }
    }

    override fun updateStatusIndicator(statusText: String, progress: Float?): ArisResult<Unit> {
        // Feed into active floating overlay panels or show Feedback
        showFeedback("Status: $statusText${if (progress != null) " (${(progress * 100).toInt()}%)" else ""}", 3000L)
        return ArisResult.Success(Unit)
    }

    override fun dismissOverlay(): ArisResult<Unit> {
        OverlayDispatcher.clearAll()
        return ArisResult.Success(Unit)
    }
}
