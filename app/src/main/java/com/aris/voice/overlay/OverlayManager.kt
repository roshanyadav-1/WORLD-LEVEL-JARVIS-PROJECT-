package com.aris.voice.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class OverlayManager private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val windowManager by lazy { applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val clientCount = AtomicInteger(0)
    
    private var bottomOverlayView: View? = null
    private var topOverlayView: View? = null
    private var observeJob: Job? = null
    
    // Manage auto-dismiss runnables by overlay ID to prevent leaks and overlapping cancels
    private val autoDismissRunnables = ConcurrentHashMap<String, Runnable>()

    companion object {
        private const val TAG = "OverlayManager"
        @Volatile private var INSTANCE: OverlayManager? = null
        
        fun getInstance(context: Context): OverlayManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OverlayManager(context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Called by anyone who wants the overlay to be alive.
     * Increments the user count.
     */
    @Synchronized // prevent race conditions
    fun startObserving() {
        val currentCount = clientCount.incrementAndGet()
        Log.d(TAG, "Client added. Total clients: $currentCount")

        // Only start the job if it's not already running
        if (observeJob?.isActive == true) return

        observeJob = scope.launch {
            try {
                OverlayDispatcher.activeContent.collect { contentMap ->
                    try {
                        // Update or remove views based on the map content
                        for (position in OverlayPosition.values()) {
                            val content = contentMap[position]
                            if (content != null) {
                                updateOverlayView(content)
                            } else {
                                removeOverlayView(position)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "UI Update Error", e)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Observer cancelled normally.")
            } catch (e: Exception) {
                Log.e(TAG, "Fatal Observer Error", e)
            }
        }
    }

    /**
     * Called when a component is done with the overlay.
     * Decrements the user count. Only kills the overlay if count reaches 0.
     */
    @Synchronized
    fun stopObserving() {
        var remainingClients = clientCount.decrementAndGet()
        if (remainingClients < 0) {
            clientCount.set(0)
            remainingClients = 0
        }
        Log.d(TAG, "Client removed. Remaining clients: $remainingClients")

        if (remainingClients <= 0) {
            // Only actually stop if NOBODY needs it anymore
            Log.d(TAG, "No clients left. Stopping observer.")
            clientCount.set(0) // Safety reset
            observeJob?.cancel()
            observeJob = null // Clear the reference so it can restart later
            removeOverlayInternal()
            
            // Clear all pending dismiss runnables
            autoDismissRunnables.values.forEach { mainHandler.removeCallbacks(it) }
            autoDismissRunnables.clear()
        } else {
            Log.d(TAG, "Observer kept alive for other clients.")
        }
    }

    private fun updateOverlayView(content: OverlayContent) {
        if (!Settings.canDrawOverlays(applicationContext)) {
            Log.w(TAG, "Cannot draw overlays: Permission denied.")
            return
        }

        Log.d(TAG, "Updating overlay: ${content.text} at ${content.position}")

        // Cancel existing runnable for this position if it exists, to avoid premature dismissal
        // of a new overlay that replaced an old one.
        // For simplicity, we can just track by ID. If an old ID is no longer present, its dismiss will harmlessly do nothing.
        
        if (content.position == OverlayPosition.TOP) {
            if (topOverlayView == null) createView(OverlayPosition.TOP)
            (topOverlayView as? TextView)?.text = content.text
        } else {
            if (bottomOverlayView == null) createView(OverlayPosition.BOTTOM)
            (bottomOverlayView as? TextView)?.text = content.text
        }

        // Handle Auto-dismiss (e.g., for system info toasts)
        if (content.duration > 0 && !autoDismissRunnables.containsKey(content.id)) {
            val runnable = Runnable {
                OverlayDispatcher.dismiss(content.id)
                autoDismissRunnables.remove(content.id)
            }
            autoDismissRunnables[content.id] = runnable
            mainHandler.postDelayed(runnable, content.duration)
        }
    }

    private fun removeOverlayView(position: OverlayPosition) {
        if (position == OverlayPosition.TOP) {
            removeView(topOverlayView)
            topOverlayView = null
        } else {
            removeView(bottomOverlayView)
            bottomOverlayView = null
        }
    }

    private fun createView(position: OverlayPosition) {
        if (!Settings.canDrawOverlays(applicationContext)) return
        
        val textView = TextView(applicationContext).apply {
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt())
                cornerRadius = 24f
            }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(24, 16, 24, 16)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        val gravity = if (position == OverlayPosition.TOP) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        val yPos = if (position == OverlayPosition.TOP) 150 else 250

        val layoutFlag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            y = yPos
        }

        try {
            windowManager.addView(textView, params)
            if (position == OverlayPosition.TOP) {
                topOverlayView = textView
            } else {
                bottomOverlayView = textView
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun removeOverlayInternal() {
        removeView(bottomOverlayView)
        bottomOverlayView = null
        removeView(topOverlayView)
        topOverlayView = null
    }

    private fun removeView(view: View?) {
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay view (already removed?)", e)
            }
        }
    }
}