package com.aris.voice.perception

import com.aris.voice.core.ArisResult
import com.aris.voice.domain.DeviceContext

/**
 * Perception Engine to parse external screen elements and system contexts into clean representations.
 */
interface IPerceptionEngine {
    /**
     * Captures a full snapshot of the active screen and system state.
     */
    suspend fun observeEnvironment(): ArisResult<DeviceContext>
}

/**
 * Screen analysis interface to detect foreground packages and top level screen layouts.
 */
interface IScreenAnalyzer {
    suspend fun getActiveAppPackage(): String?
    suspend fun getScreenHierarchyHash(): String?
}

/**
 * Optical Character Recognition interface to pull raw strings from visual elements.
 */
interface IOcrEngine {
    suspend fun extractTextFromImage(bytes: ByteArray): ArisResult<String>
}

/**
 * Accessibility tree parser to traverse Android accessibility node streams.
 */
interface IAccessibilityParser {
    suspend fun parseCurrentWindowNodes(): ArisResult<List<String>>
}

/**
 * Monitor live system parameters (battery, connectivity, clipboard, notifications).
 */
interface IDeviceStateMonitor {
    fun getBatteryLevel(): Int
    fun isNetworkConnected(): Boolean
    fun getLastClipboardEntry(): String?
    fun getActiveNotifications(): List<String>
}
