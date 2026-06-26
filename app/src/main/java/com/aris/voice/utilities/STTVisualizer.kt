package com.aris.voice.utilities

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.util.Log
import com.aris.voice.AudioWaveView // CHANGED: Import the new wave view

class STTVisualizer(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    // CHANGED: The view is now an AudioWaveView
    private var visualizerView: AudioWaveView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show() {
        // Feature disabled per user request: no visual wave overlay.
    }

    fun hide() {
        // Nothing to hide as the visual wave is disabled.
    }

    fun onRmsChanged(rmsdB: Float) {
        // Nothing to update.
    }
}