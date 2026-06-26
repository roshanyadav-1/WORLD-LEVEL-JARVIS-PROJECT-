package com.aris.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import java.io.File
import android.util.Log
import androidx.annotation.RequiresApi
import com.aris.voice.RawScreenData
import com.aris.voice.ScreenInteractionService

class Eyes(context: Context) {

    private val screenshotDir = File(context.filesDir, "screenshots").also { it.mkdirs() }

    private fun compressBitmapForAPI(bitmap: Bitmap): Bitmap {
        val maxDimension = 1024
        val scale = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height,
            1f
        )
        if (scale >= 1f) return bitmap // Optimization: do not create scaled bitmap if not needed

        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Takes a screenshot.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun openEyes(): Bitmap? {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return null
        }
        val bitmap = service.captureScreenshot()
        return bitmap?.let { compressBitmapForAPI(it) }
    }

    fun isTypingAvailable(): Boolean {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return false
        }
        return service.isTypingAvailable()
    }

    /**
     * Gets all raw screen data (XML, scroll info) in a single, efficient call.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getRawScreenData(): RawScreenData? {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return RawScreenData(null, 0,0, 0, 0)
        }
        return service.getScreenAnalysisData()
    }


    /**
     * Gets the package name of the current foreground activity.
     */
    fun getCurrentActivityName(): String {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return "Unknown"
        }
        return service.getCurrentActivityName()
    }
}