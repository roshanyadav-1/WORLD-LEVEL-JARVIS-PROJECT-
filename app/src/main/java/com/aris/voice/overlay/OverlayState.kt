package com.aris.voice.overlay

/**
 * Defines the priority level of an overlay. Higher numbers indicate higher priority.
 */
enum class OverlayPriority(val level: Int) {
    CAPTION(1),
    TASKS(1),
}

/**
 * Defines the position of the overlay on the screen.
 */
enum class OverlayPosition {
    TOP,
    BOTTOM
}

/**
 * Data model representing the content to be displayed in an overlay.
 */
data class OverlayContent(
    val id: String,
    val text: String,
    val priority: OverlayPriority,
    val duration: Long = 0L,
    val position: OverlayPosition = OverlayPosition.BOTTOM
)