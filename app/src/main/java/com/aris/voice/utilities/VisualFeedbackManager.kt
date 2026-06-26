package com.aris.voice.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aris.voice.AudioWaveView
import com.aris.voice.R
import com.aris.voice.ui.ArisCreatureView
import com.aris.voice.utilities.ArisState
import com.aris.voice.utilities.ArisStateManager
import com.aris.voice.utilities.TTSManager
import com.aris.voice.utilities.TtsVisualizer

class VisualFeedbackManager private constructor(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Components ---
    private var audioWaveView: AudioWaveView? = null
    private var ttsVisualizer: TtsVisualizer? = null
    private var transcriptionView: TextView? = null
    private var inputBoxView: View? = null
    private var thinkingIndicatorView: View? = null
    private var smallDeltaGlowView: ArisCreatureView? = null
    private val arisStateManager by lazy { ArisStateManager.getInstance(context) }
    private val stateChangeListener: (ArisState) -> Unit
    private var speakingOverlay: View? = null

    init {
        stateChangeListener = { newState ->
            updateSmallDeltaVisuals(newState)
        }
        arisStateManager.addStateChangeListener(stateChangeListener)

    }

    class OverlayColors(
        val backgroundStart: Int,
        val backgroundEnd: Int,
        val textColor: Int
    ) {
        companion object {
            val DARK = OverlayColors(0xEE121212.toInt(), 0xEE1C1E24.toInt(), 0xFFECEFF1.toInt())
            val LIGHT = OverlayColors(0xEEFFFFFF.toInt(), 0xEEF5F5F5.toInt(), 0xFF1A1A1A.toInt())
        }
    }

    private fun getThemeColors(): OverlayColors {
        val isDarkMode = context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        return if (isDarkMode) OverlayColors.DARK else OverlayColors.LIGHT
    }

    companion object {
        private const val TAG = "VisualFeedbackManager"

        @Volatile private var INSTANCE: VisualFeedbackManager? = null

        fun getInstance(context: Context): VisualFeedbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VisualFeedbackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Check if the app has permission to draw overlays
     */
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    // --- TTS Wave Methods ---

    fun showTtsWave() {
        // Disabled per user request
    }

    fun hideTtsWave() {
        // Disabled per user request
    }

    private fun setupAudioWaveEffect() {
        // Disabled
    }

    fun showSpeakingOverlay() {
        mainHandler.post {
            if (speakingOverlay != null) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show speaking overlay: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            speakingOverlay = View(context).apply {
                // Reduced opacity from 80 (50%) to 40 (25%) for a more subtle overlay
                setBackgroundColor(0x40FFFFFF.toInt())
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(speakingOverlay, params)
                Log.d(TAG, "Speaking overlay added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding speaking overlay", e)
                speakingOverlay = null
            }
        }
    }


    fun showTranscription(initialText: String = "Listening...") {
        hideThinkingIndicator() // Hide thinking to avoid visual chaos
        if (transcriptionView != null) {
            updateTranscription(initialText) // Update text if already shown
            return
        }

        mainHandler.post {
            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show transcription: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            transcriptionView = TextView(context).apply {
                val theme = getThemeColors()
                text = initialText
                contentDescription = "A.R.I.S assistant transcription, currently listening"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.START
                val glassBackground = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(theme.backgroundStart, theme.backgroundEnd)
                ).apply {
                    cornerRadius = 28f
                    setStroke(1, 0x80FFFFFF.toInt())
                }
                background = glassBackground
                setTextColor(theme.textColor)
                textSize = 16f
                setPadding(40, 24, 40, 24)
                typeface = Typeface.MONOSPACE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                y = (context.resources.displayMetrics.heightPixels * 0.15).toInt() // Position it above the wave view
            }

            try {
                windowManager.addView(transcriptionView, params)
                Log.d(TAG, "Transcription view added.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add transcription view.", e)
                transcriptionView = null
            }
        }
    }

    fun updateTranscription(text: String) {
        mainHandler.post {
            transcriptionView?.text = text
        }
    }

    fun hideTranscription() {
        mainHandler.post {
            transcriptionView?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing transcription view.", e)
                    }
                }
            }
            transcriptionView = null
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    fun showInputBox(
        onActivated: () -> Unit,
        onSubmit: (String) -> Unit,
        onOutsideTap: () -> Unit
    ) {
        hideThinkingIndicator()
        hideTranscription()
        // This method creates an overlay input box that appears over other apps
        // Key fix: Proper keyboard positioning using WindowInsetsCompat to prevent
        // the input box from being hidden behind the keyboard when it appears
        mainHandler.post {
            if (inputBoxView?.isAttachedToWindow == true) {
                // If already showing, just ensure focus
                inputBoxView?.findViewById<EditText>(R.id.overlayInputField)?.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputBoxView?.findViewById(R.id.overlayInputField), InputMethodManager.SHOW_IMPLICIT)
                return@post
            }

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show input box: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            if (inputBoxView != null) {
                try { windowManager.removeView(inputBoxView) } catch (e: Exception) {}
            }

            val inflater = LayoutInflater.from(context)
            inputBoxView = inflater.inflate(R.layout.overlay_input_box, null)

            val inputField = inputBoxView?.findViewById<EditText>(R.id.overlayInputField)
            val rootLayout = inputBoxView?.findViewById<View>(R.id.overlayRootLayout)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                // Top margin with sufficient space
                y = (80 * context.resources.displayMetrics.density).toInt() // 80dp top margin
            }

            inputField?.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val inputText = v.text.toString().trim()
                    if (inputText.isNotEmpty()) {
                        onSubmit(inputText)
                        v.text = ""
                    } else {
                        // If empty, just hide the box
                        hideInputBox()
                    }
                    true
                } else {
                    false
                }
            }

            inputField?.setOnTouchListener { _, _ ->
                onActivated()
                false
            }

            rootLayout?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    Log.d(TAG, "Outside touch detected.")
                    onOutsideTap() // Use the new callback
                    return@setOnTouchListener true
                }
                false
            }

            try {
                windowManager.addView(inputBoxView, params)
                Log.d(TAG, "Input box added with initial y position: ${params.y}")
                
                // **IMPROVEMENT**: Explicitly request focus and show the keyboard
                inputField?.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)

            } catch (e: Exception) {
                Log.e("VisualManager", "Error adding input box view", e)
            }
        }
    }
    // --- REPLACE the hideInputBox method with this simplified version ---
    fun hideInputBox() {
        mainHandler.post {
            inputBoxView?.let {
                if (it.isAttachedToWindow) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                    windowManager.removeView(it)
                }
            }
            inputBoxView = null
        }
    }
    fun hideSpeakingOverlay() {
        mainHandler.post {
            speakingOverlay?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
                        Log.d(TAG, "Speaking overlay removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing speaking overlay", e)
                    }
                }
            }
            speakingOverlay = null
        }
    }
    // --- Thinking indicator (replace existing methods with these) ---
    fun showThinkingIndicator(initialText: String = "Thinking...") {
        hideTranscription() // Hide transcription to avoid visual chaos
        if (thinkingIndicatorView != null) {
            updateThinking(initialText)
            return
        }

        mainHandler.post {
            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show thinking indicator: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            // If a previous instance exists on window manager, try to remove it silently
            thinkingIndicatorView?.let {
                try { if (it.isAttachedToWindow) windowManager.removeView(it) } catch (_: Exception) {}
            }

            // Build a TextView similar to the transcription view so it looks consistent
            val textView = TextView(context).apply {
                val theme = getThemeColors()
                text = initialText
                contentDescription = "A.R.I.S assistant thinking indicator"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                val glassBackground = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(theme.backgroundStart, theme.backgroundEnd)
                ).apply {
                    cornerRadius = 28f
                    setStroke(1, 0x80FFFFFF.toInt())
                }
                background = glassBackground
                setTextColor(theme.textColor)
                textSize = 16f
                setPadding(40, 24, 40, 24)
                typeface = Typeface.MONOSPACE
                // Optional: higher elevation on supported API levels
                ViewCompat.setElevation(this, 12f)
            }

            thinkingIndicatorView = textView

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Place it above the wave and near center (adjust y as needed)
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 320 // tweak dp offset by multiplying with density if you want to use dp
                // If you want the same y calculation in dp:
                // y = (320 * context.resources.displayMetrics.density).toInt()
            }

            try {
                windowManager.addView(textView, params)
                Log.d(TAG, "Thinking indicator added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding thinking indicator", e)
                thinkingIndicatorView = null
            }
        }
    }

    fun updateThinking(text: String) {
        mainHandler.post {
            (thinkingIndicatorView as? TextView)?.text = text
        }
    }

    fun hideThinkingIndicator() {
        mainHandler.post {
            thinkingIndicatorView?.let { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeView(view)
                        Log.d(TAG, "Thinking indicator removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing thinking indicator", e)
                    }
                }
            }
            thinkingIndicatorView = null
        }
    }

    fun showSmallDeltaGlow() {
        // Disabled overlay big creature per user request. 
        // Only the small circular floating-button creature on the screen will be shown and animated now.
    }

    fun hideSmallDeltaGlow() {
        // Disabled overlay big creature per user request.
    }

    /**
     * A new private method to update the small delta's appearance based on the app state.
     */
    private fun updateSmallDeltaVisuals(state: ArisState) {
        // Disabled overlay big creature per user request.
    }


}