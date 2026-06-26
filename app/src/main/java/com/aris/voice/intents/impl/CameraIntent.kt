package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class CameraIntent : AppIntent {
    override val name: String = "Camera"

    override fun description(): String =
        "Directly open the system's default camera app to capture a photo or start recording a video."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "mode",
            type = "string",
            required = false,
            description = "The capture mode. Supported: 'photo' (default) or 'video'."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val mode = params["mode"]?.toString()?.lowercase()?.trim() ?: "photo"
            val action = if (mode == "video") {
                MediaStore.INTENT_ACTION_VIDEO_CAMERA
            } else {
                MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            }
            return Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
