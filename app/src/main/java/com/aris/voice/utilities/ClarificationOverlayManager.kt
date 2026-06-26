package com.aris.voice.utilities

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.toColorInt

class ClarificationOverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeViews = mutableListOf<View>()

    fun displayClarificationQuestions(questions: List<String>) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("ClarificationOverlay", "SYSTEM_ALERT_WINDOW permission not granted.")
            return
        }

        clearQuestions()

        val topMargin = 100
        val verticalSpacing = 20
        var accumulatedHeight = 0

        questions.forEachIndexed { index, questionText ->
            val textView = TextView(context).apply {
                text = questionText
                val glowEffect = GradientDrawable(
                    GradientDrawable.Orientation.BL_TR,
                    intArrayOf("#BE63F3".toColorInt(), "#5880F7".toColorInt())
                ).apply { cornerRadius = 32f }

                val glassBackground = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xEE0D0D2E.toInt(), 0xEE2A0D45.toInt())
                ).apply {
                    cornerRadius = 28f
                    setStroke(1, 0x80FFFFFF.toInt())
                }

                val layerDrawable = LayerDrawable(arrayOf(glowEffect, glassBackground)).apply {
                    setLayerInset(1, 4, 4, 4, 4)
                }
                background = layerDrawable
                setTextColor(0xFFE0E0E0.toInt())
                textSize = 15f
                setPadding(40, 24, 40, 24)
                typeface = Typeface.MONOSPACE
            }

            textView.measure(
                View.MeasureSpec.makeMeasureSpec((context.resources.displayMetrics.widthPixels * 0.9).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val viewHeight = textView.measuredHeight
            val finalYPosition = topMargin + accumulatedHeight
            accumulatedHeight += viewHeight + verticalSpacing

            val params = WindowManager.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = -viewHeight
                alpha = 0f
            }

            try {
                windowManager.addView(textView, params)
                activeViews.add(textView)

                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 500L
                    startDelay = (index * 150).toLong()
                    addUpdateListener { animation ->
                        val progress = animation.animatedValue as Float
                        params.y = (finalYPosition * progress - viewHeight * (1 - progress)).toInt()
                        params.alpha = progress
                        windowManager.updateViewLayout(textView, params)
                    }
                    start()
                }
            } catch (e: Exception) {
                Log.e("ClarificationOverlay", "Error adding overlay view: ${e.message}")
            }
        }
    }

    fun clearQuestions() {
        val viewsToRemove = activeViews.toList()
        activeViews.clear()

        viewsToRemove.forEachIndexed { index, view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            val startY = params.y

            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 300L
                startDelay = (index * 100).toLong()
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    params.y = (startY - (1 - progress) * 100).toInt()
                    params.alpha = progress
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        // ignore 
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        try {
                            windowManager.removeView(view)
                        } catch (e: Exception) {
                            Log.e("ClarificationOverlay", "Error removing overlay view: ${e.message}")
                        }
                    }
                })
                start()
            }
        }
    }
}
