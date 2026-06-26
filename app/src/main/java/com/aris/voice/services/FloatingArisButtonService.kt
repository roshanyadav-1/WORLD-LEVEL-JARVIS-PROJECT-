package com.aris.voice.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ValueAnimator
import android.animation.AnimatorSet
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import androidx.core.content.ContextCompat
import com.aris.voice.ConversationalAgentService
import com.aris.voice.R
import com.aris.voice.ui.ArisCreatureView
import com.aris.voice.utilities.ArisState
import com.aris.voice.utilities.ArisStateManager

class FloatingArisButtonService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private lateinit var arisStateManager: ArisStateManager
    
    private val stateChangeListener: (ArisState) -> Unit = { state ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            (floatingButton as? ArisCreatureView)?.setState(state)
        }
    }

    companion object {
        private const val TAG = "FloatingArisButton"
        var isRunning = false
        private const val NOTIFICATION_ID = 25
        
        @Volatile
        var instance: FloatingArisButtonService? = null
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        Log.d(TAG, "Floating Axel Button Service created")
        arisStateManager = ArisStateManager.getInstance(this)
        arisStateManager.addStateChangeListener(stateChangeListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Floating Axel Button Service starting...")

        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start FloatingArisButtonService in foreground: ${e.message}", e)
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show floating button: 'Draw over other apps' permission not granted.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            showFloatingButton()
            if (floatingButton == null) {
                Log.w(TAG, "Failed to show floating button, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating button", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "floating_aris_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "A.R.I.S Assistant Button",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, com.aris.voice.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("A.R.I.S Assistant")
            .setContentText("A.R.I.S button is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure to use a valid icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingButton() {
        if (floatingButton != null) {
            Log.d(TAG, "Floating button already showing")
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            // Create the programmatic ArisCreatureView
            val widget = ArisCreatureView(this).apply {
                // Sync with original state immediately
                setState(arisStateManager.getCurrentState())
            }
            floatingButton = widget

            val displayMetrics = resources.displayMetrics
            val sizePx = (68 * displayMetrics.density).toInt() // 68dp clean floating interactive bubble!

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = displayMetrics.widthPixels - sizePx - (16 * displayMetrics.density).toInt()
                y = displayMetrics.heightPixels - sizePx - (100 * displayMetrics.density).toInt()
            }

            // Drag, Tap, Double-Tap, and Long Press Gestures
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDrag = false
            var lastUpTime = 0L
            var isLongPressTriggered = false
            val touchHandler = android.os.Handler(android.os.Looper.getMainLooper())
            
            val longPressRunnable = Runnable {
                if (!isDrag) {
                    isLongPressTriggered = true
                    // Long Press: activate voice immediately!
                    val forceListenIntent = Intent(this, ConversationalAgentService::class.java).apply {
                        action = ConversationalAgentService.ACTION_FORCE_LISTEN
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(forceListenIntent)
                        } else {
                            startService(forceListenIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start ConversationalAgentService for forceListen", e)
                    }
                    
                    // Physical tactile rumble
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(100)
                    }
                    Toast.makeText(this, "A.R.I.S is listening...", Toast.LENGTH_SHORT).show()
                }
            }

            @Suppress("ClickableViewAccessibility")
            widget.setOnTouchListener { view, event ->
                val dm = resources.displayMetrics
                val screenW = dm.widthPixels
                val screenH = dm.heightPixels
                val buttonWidth = view.width.takeIf { it > 0 } ?: sizePx
                val buttonHeight = view.height.takeIf { it > 0 } ?: sizePx

                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDrag = false
                        isLongPressTriggered = false
                        touchHandler.postDelayed(longPressRunnable, 500)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        
                        if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
                            isDrag = true
                            touchHandler.removeCallbacks(longPressRunnable)
                            params.x = (initialX + dx).coerceIn(0, screenW - buttonWidth)
                            params.y = (initialY + dy).coerceIn(0, screenH - buttonHeight)
                            try {
                                if (view.isAttachedToWindow) {
                                    windowManager?.updateViewLayout(view, params)
                                }
                            } catch (e: Exception) {}
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        touchHandler.removeCallbacks(longPressRunnable)
                        if (!isDrag && !isLongPressTriggered) {
                            val upTime = System.currentTimeMillis()
                            if (upTime - lastUpTime < 350) {
                                // Double-tap: trigger dizzy knock down of Axel!
                                val dizzyIntent = Intent(this, ConversationalAgentService::class.java).apply {
                                    action = ConversationalAgentService.ACTION_TRIGGER_DIZZY_SHUTDOWN
                                }
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(dizzyIntent)
                                    } else {
                                        startService(dizzyIntent)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to start ConversationalAgentService for dizzy shutdown", e)
                                }
                            } else {
                                // Single tap: wake up if dormant, or cycle accessory if live!
                                val currentState = arisStateManager.getCurrentState()
                                if (currentState == ArisState.IDLE || currentState == ArisState.ERROR) {
                                    triggerArisActivation()
                                } else {
                                    (widget as? ArisCreatureView)?.cycleAccessory()
                                }
                            }
                            lastUpTime = upTime
                        } else if (isDrag) {
                            // SMOOTH MAGNETIC EDGE SNAP: Pulls fluidly to nearest edge with a soft bounce
                            val centerX = params.x + buttonWidth / 2
                            val padding = (16 * dm.density).toInt()
                            val destX = if (centerX < screenW / 2) {
                                padding
                            } else {
                                screenW - buttonWidth - padding
                            }
                            
                            val snapAnim = ValueAnimator.ofInt(params.x, destX).apply {
                                duration = 350
                                interpolator = android.view.animation.BounceInterpolator()
                                addUpdateListener { anim ->
                                    params.x = anim.animatedValue as Int
                                    try {
                                        if (view.isAttachedToWindow) {
                                            windowManager?.updateViewLayout(view, params)
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                            snapAnim.start()
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        touchHandler.removeCallbacks(longPressRunnable)
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(floatingButton, params)
            Log.d(TAG, "Floating Axel ArisCreatureView added successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating button", e)
            floatingButton = null
        }
    }

    private fun triggerArisActivation() {
        try {
            if (!ConversationalAgentService.isRunning) {
                Log.d(TAG, "Starting ConversationalAgentService from floating button")
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                Log.d(TAG, "ConversationalAgentService is already running, performing standard wake action.")
                // If already running but idle, start interaction
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ConversationalAgentService", e)
        }
    }

    fun animateToPointAndTap(targetX: Float, targetY: Float, onPositionReached: () -> Unit) {
        val button = floatingButton ?: run {
            onPositionReached()
            return
        }
        val wm = windowManager ?: run {
            onPositionReached()
            return
        }
        val layoutParams = button.layoutParams as? WindowManager.LayoutParams ?: run {
            onPositionReached()
            return
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val buttonWidth = button.width.takeIf { it > 0 } ?: (68 * displayMetrics.density).toInt()
        val buttonHeight = button.height.takeIf { it > 0 } ?: (68 * displayMetrics.density).toInt()

        // Absolute screen coords (always Gravity.TOP or Gravity.START)
        layoutParams.gravity = Gravity.TOP or Gravity.START
        val currentAbsoluteX = layoutParams.x
        val currentAbsoluteY = layoutParams.y

        // Compute direct bounding coordinates
        val destX = (targetX - buttonWidth / 2).toInt().coerceIn(0, screenWidth - buttonWidth)
        val destY = (targetY - buttonHeight / 2).toInt().coerceIn(0, screenHeight - buttonHeight)

        // Avoid double animation if already there
        val distanceX = Math.abs(currentAbsoluteX - destX)
        val distanceY = Math.abs(currentAbsoluteY - destY)
        if (distanceX < 15 && distanceY < 15) {
            onPositionReached()
            return
        }

        // Set state to PROCESSING (High-tech spinning) while moving
        (button as? ArisCreatureView)?.setState(ArisState.PROCESSING)

        val animX = ValueAnimator.ofInt(currentAbsoluteX, destX)
        val animY = ValueAnimator.ofInt(currentAbsoluteY, destY)

        val updateListener = ValueAnimator.AnimatorUpdateListener { _ ->
            layoutParams.x = animX.animatedValue as Int
            layoutParams.y = animY.animatedValue as Int
            try {
                if (button.isAttachedToWindow) {
                    wm.updateViewLayout(button, layoutParams)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating window layout", e)
            }
        }

        animX.addUpdateListener(updateListener)
        animY.addUpdateListener(updateListener)

        val animatorSet = AnimatorSet().apply {
            playTogether(animX, animY)
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
        }

        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Short visual pulse
                (button as? ArisCreatureView)?.setState(ArisState.LISTENING)

                onPositionReached()

                // Stay on spot briefly, then return home automatically to the nearest sidebar side!
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val padding = (16 * displayMetrics.density).toInt()
                    // Snaps to the nearest edge matching its layout X coordinate
                    val homeX = if (destX < screenWidth / 2) padding else screenWidth - buttonWidth - padding
                    val homeY = destY.coerceIn((100 * displayMetrics.density).toInt(), screenHeight - buttonHeight - (100 * displayMetrics.density).toInt())

                    val backX = ValueAnimator.ofInt(destX, homeX)
                    val backY = ValueAnimator.ofInt(destY, homeY)

                    val backUpdate = ValueAnimator.AnimatorUpdateListener { _ ->
                        layoutParams.x = backX.animatedValue as Int
                        layoutParams.y = backY.animatedValue as Int
                        try {
                            if (button.isAttachedToWindow) {
                                wm.updateViewLayout(button, layoutParams)
                            }
                        } catch (e: Exception) {}
                    }
                    backX.addUpdateListener(backUpdate)
                    backY.addUpdateListener(backUpdate)

                    val backSet = AnimatorSet().apply {
                        playTogether(backX, backY)
                        duration = 500
                        interpolator = AccelerateDecelerateInterpolator()
                    }

                    backSet.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            (button as? ArisCreatureView)?.setState(ArisState.IDLE)
                        }
                    })
                    backSet.start()
                }, 300)
            }
        })
        animatorSet.start()
    }

    private fun hideFloatingButton() {
        floatingButton?.let { button ->
            try {
                if (button.isAttachedToWindow) {
                    windowManager?.removeView(button)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating button", e)
            }
        }
        floatingButton = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Floating Axel Button Service destroying...")
        arisStateManager.removeStateChangeListener(stateChangeListener)
        hideFloatingButton()
        isRunning = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
