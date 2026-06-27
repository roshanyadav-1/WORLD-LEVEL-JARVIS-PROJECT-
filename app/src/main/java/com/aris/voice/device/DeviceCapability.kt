package com.aris.voice.device

import android.content.Context
import com.aris.voice.utilities.CommandResult

/**
 * Represents a discrete deterministic capability of the device.
 */
interface DeviceCapability {
    val name: String
    val patterns: List<Regex>
    
    /**
     * Executes the capability deterministically.
     */
    fun execute(context: Context, command: String, match: MatchResult): CommandResult
}
