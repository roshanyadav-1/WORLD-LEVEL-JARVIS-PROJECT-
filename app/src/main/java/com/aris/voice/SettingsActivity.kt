package com.aris.voice

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.aris.voice.api.GoogleTts
import com.aris.voice.api.PicovoiceKeyManager
import com.aris.voice.api.TTSVoice
import com.aris.voice.utilities.SpeechCoordinator
import com.aris.voice.utilities.VoicePreferenceManager
import com.aris.voice.utilities.UserProfileManager
import com.aris.voice.utilities.WakeWordManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import android.widget.SeekBar

class SettingsActivity : BaseNavigationActivity() {

    private lateinit var ttsVoicePicker: NumberPicker
    private lateinit var switchShowThoughts: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var permissionsInfoButton: TextView
    private lateinit var batteryOptimizationHelpButton: TextView
    private lateinit var appVersionText: TextView
    private lateinit var editUserName: android.widget.EditText
    private lateinit var editUserEmail: android.widget.EditText
    private lateinit var editWakeWordKey: android.widget.EditText
    private lateinit var editGeminiApiKeys: android.widget.EditText
    private lateinit var buttonSaveGeminiKeys: TextView
    private lateinit var textGetPicovoiceKeyLink: TextView
    private lateinit var wakeWordButton: TextView
    private lateinit var buttonSignOut: TextView
    private lateinit var wakeWordManager: WakeWordManager
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    
    private lateinit var seekBarSensitivity: SeekBar
    private lateinit var textSensitivityLabel: TextView
    private lateinit var btnThemeDefault: TextView
    private lateinit var btnThemeDark: TextView
    private lateinit var btnThemeLight: TextView


    private lateinit var sc: SpeechCoordinator
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var availableVoices: List<TTSVoice>
    private var voiceTestJob: Job? = null
    
    private lateinit var textKeyHealthMonitorTitle: TextView
    private lateinit var layoutKeyHealthMonitor: android.widget.LinearLayout
    private var healthMonitorJob: Job? = null

    companion object {
        private const val PREFS_NAME = "ArisSettings"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val TEST_TEXT = "Hello, I'm Aris, and this is a test of the selected voice."
        private val DEFAULT_VOICE = TTSVoice.CHIRP_PUCK
        const val KEY_SHOW_THOUGHTS = "show_thoughts"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize permission launcher first
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                // The manager will handle the service start after permission is granted.
                wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
                updateWakeWordButtonState()
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

        initialize()
        setupUI()
        loadAllSettings()
        setupAutoSavingListeners()
        cacheVoiceSamples()
    }

    override fun onStop() {
        super.onStop()
        // Stop any lingering voice tests when the user leaves the screen
        sc.stop()
        voiceTestJob?.cancel()
        healthMonitorJob?.cancel()
    }

    private fun initialize() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sc = SpeechCoordinator.getInstance(this)
        availableVoices = GoogleTts.getAvailableVoices()
        // Initialize wake word manager
        wakeWordManager = WakeWordManager(this, requestPermissionLauncher)
    }

    private fun setupUI() {
        ttsVoicePicker = findViewById(R.id.ttsVoicePicker)
        switchShowThoughts = findViewById(R.id.switchShowThoughts)
        permissionsInfoButton = findViewById(R.id.permissionsInfoButton)
        appVersionText = findViewById(R.id.appVersionText)
        batteryOptimizationHelpButton = findViewById(R.id.batteryOptimizationHelpButton)
      
        editWakeWordKey = findViewById(R.id.editWakeWordKey)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        editGeminiApiKeys = findViewById(R.id.editGeminiApiKeys)
        buttonSaveGeminiKeys = findViewById(R.id.buttonSaveGeminiKeys)
        
        textKeyHealthMonitorTitle = findViewById(R.id.textKeyHealthMonitorTitle)
        layoutKeyHealthMonitor = findViewById(R.id.layoutKeyHealthMonitor)

        buttonSignOut = findViewById(R.id.buttonSignOut)

        editUserName = findViewById(R.id.editUserName)
        editUserEmail = findViewById(R.id.editUserEmail)
        textGetPicovoiceKeyLink = findViewById(R.id.textGetPicovoiceKeyLink)

        seekBarSensitivity = findViewById(R.id.seekBarSensitivity)
        textSensitivityLabel = findViewById(R.id.textSensitivityLabel)
        
        btnThemeDefault = findViewById(R.id.btn_theme_default)
        btnThemeDark = findViewById(R.id.btn_theme_dark)
        btnThemeLight = findViewById(R.id.btn_theme_light)

        setupClickListeners()
        setupVoicePicker()
        setupSensitivitySeekBar()
        setupThemeSelection()

        // Prefill profile fields from saved values
        kotlin.runCatching {
            val pm = UserProfileManager(this)
            editUserName.setText(pm.getName() ?: "")
            editUserEmail.setText(pm.getEmail() ?: "")
        }

        editUserName.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                UserProfileManager(this@SettingsActivity).setName(s?.toString() ?: "")
            }
        })
        editUserEmail.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                UserProfileManager(this@SettingsActivity).setEmail(s?.toString() ?: "")
            }
        })

        // Show app version
        val versionName = BuildConfig.VERSION_NAME
        appVersionText.text = "Version $versionName"
    }

    private fun setupVoicePicker() {
        val voiceDisplayNames = availableVoices.map { it.displayName }.toTypedArray()
        ttsVoicePicker.minValue = 0
        ttsVoicePicker.maxValue = voiceDisplayNames.size - 1
        ttsVoicePicker.displayedValues = voiceDisplayNames
        ttsVoicePicker.wrapSelectorWheel = false
    }

    private fun setupSensitivitySeekBar() {
        val sensitivity = sharedPreferences.getFloat("wake_word_sensitivity", 0.5f)
        val progressVal = (sensitivity * 10).toInt().coerceIn(0, 10)
        seekBarSensitivity.progress = progressVal
        textSensitivityLabel.text = "Sensitivity: $sensitivity"

        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progressValue: Int, fromUser: Boolean) {
                val floatValue = progressValue / 10.0f
                textSensitivityLabel.text = "Sensitivity: $floatValue"
                sharedPreferences.edit().putFloat("wake_word_sensitivity", floatValue).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupClickListeners() {
        permissionsInfoButton.setOnClickListener {
            val intent = Intent(this, PermissionsActivity::class.java)
            startActivity(intent)
        }
        buttonSaveGeminiKeys.setOnClickListener {
            val keys = editGeminiApiKeys.text.toString().trim()
            if (keys.isNotBlank() && !keys.split(",").all { it.trim().startsWith("AIza") }) {
                Toast.makeText(this, "All keys must start with AIza", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            com.aris.voice.utilities.ApiKeyManager.saveKeys(this, keys)
            Toast.makeText(this, "Gemini API Keys saved.", Toast.LENGTH_SHORT).show()
            updateKeyHealthUI()
        }
        batteryOptimizationHelpButton.setOnClickListener {
            showBatteryOptimizationDialog()
        }
        wakeWordButton.setOnClickListener {
            val keyManager = PicovoiceKeyManager(this)
            
            // Step 1: Save key if provided in the EditText
            val userKey = editWakeWordKey.text.toString().trim()
            if (userKey.isNotEmpty()) {
                keyManager.saveUserProvidedKey(userKey)
                Toast.makeText(this, "Wake word key saved.", Toast.LENGTH_SHORT).show()
            }
            
            // Step 2: Check if we have a key (either just saved or previously saved)
            val hasKey = !keyManager.getUserProvidedKey().isNullOrBlank()
            
            if (!hasKey) {
                showPicovoiceKeyRequiredDialog()
                return@setOnClickListener
            }
            
            // Step 3: Enable the wake word
            wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
            // Give the service a moment to update its state before refreshing the UI
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ updateWakeWordButtonState() }, 500)
        }
        textGetPicovoiceKeyLink.setOnClickListener {
            val url = "https://console.picovoice.ai/login"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // This might happen if the device has no web browser
                Toast.makeText(this, "Could not open link. No browser found.", Toast.LENGTH_SHORT).show()
                Log.e("SettingsActivity", "Failed to open Picovoice link", e)
            }
        }


        buttonSignOut.setOnClickListener {
            showSignOutConfirmationDialog()
        }

        findViewById<TextView>(R.id.viewTaskLogsButton).setOnClickListener {
            startActivity(Intent(this, TaskLogsListActivity::class.java))
        }

        findViewById<TextView>(R.id.viewDiagnosticsButton).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        findViewById<TextView>(R.id.viewHybridAutomationButton).setOnClickListener {
            startActivity(Intent(this, HybridAutomationActivity::class.java))
        }

        findViewById<TextView>(R.id.viewModelManagerButton).setOnClickListener {
            startActivity(Intent(this, com.aris.voice.v2.llm.ModelManagerDashboardActivity::class.java))
        }
    }

    private fun setupAutoSavingListeners() {
        var isInitialLoad = true

        ttsVoicePicker.setOnValueChangedListener { _, _, newVal ->
            val selectedVoice = availableVoices[newVal]
            saveSelectedVoice(selectedVoice)

            if (!isInitialLoad) {
                voiceTestJob?.cancel()
                voiceTestJob = lifecycleScope.launch {
                    delay(400L)
                    // First, stop any currently playing voice
                    sc.stop()
                    // Then, play the new sample
                    playVoiceSample(selectedVoice)
                }
            }
        }

        ttsVoicePicker.post {
            isInitialLoad = false
        }

        switchShowThoughts.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_SHOW_THOUGHTS, isChecked).apply()
        }
    }

    private fun playVoiceSample(voice: TTSVoice) {
        lifecycleScope.launch {
            val cacheDir = File(cacheDir, "voice_samples")
            val voiceFile = File(cacheDir, "${voice.name}.wav")

            try {
                if (voiceFile.exists()) {
                    val audioData = voiceFile.readBytes()
                    sc.playAudioData(audioData)
                    Log.d("SettingsActivity", "Playing cached sample for ${voice.displayName}")
                } else {
                    sc.testVoice(TEST_TEXT, voice)
                    Log.d("SettingsActivity", "Synthesizing test for ${voice.displayName}")
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("SettingsActivity", "Error playing voice sample", e)
                    Toast.makeText(this@SettingsActivity, "Error playing voice", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cacheVoiceSamples() {
        // No-op: Done locally with built-in TTS engine, conserving online API hits/traffic
    }

    private fun loadAllSettings() {

        // Inside loadAllSettings()
        val keyManager = PicovoiceKeyManager(this)
        editWakeWordKey.setText(keyManager.getUserProvidedKey() ?: "") // You will create this method next
        editGeminiApiKeys.setText(com.aris.voice.utilities.ApiKeyManager.getSavedKeysString(this))
        val savedVoice = VoicePreferenceManager.getSelectedVoice(this)
        ttsVoicePicker.value = availableVoices.indexOf(savedVoice).coerceAtLeast(0)
        
        // Update wake word button state
        updateWakeWordButtonState()

        switchShowThoughts.isChecked = sharedPreferences.getBoolean(KEY_SHOW_THOUGHTS, false)
    }

    override fun onResume() {
        super.onResume()
        startKeyHealthMonitor()
    }

    private fun startKeyHealthMonitor() {
        healthMonitorJob?.cancel()
        healthMonitorJob = lifecycleScope.launch {
            while (true) {
                updateKeyHealthUI()
                delay(1000L)
            }
        }
    }

    private fun updateKeyHealthUI() {
        val healthList = com.aris.voice.utilities.ApiKeyManager.getKeyHealthList()
        if (healthList.isEmpty()) {
            textKeyHealthMonitorTitle.visibility = android.view.View.GONE
            layoutKeyHealthMonitor.visibility = android.view.View.GONE
            return
        }

        textKeyHealthMonitorTitle.visibility = android.view.View.VISIBLE
        layoutKeyHealthMonitor.visibility = android.view.View.VISIBLE

        layoutKeyHealthMonitor.removeAllViews()

        for (info in healthList) {
            val textView = TextView(this).apply {
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.white))
                textSize = 13f
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 6, 0, 6)
                }
                setPadding(paddingLeft, 4, paddingRight, 4)
                
                try {
                    val font = resources.getFont(R.font.oxanium)
                    typeface = font
                } catch (e: Exception) {
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                
                val statusText = when (info.status) {
                    com.aris.voice.utilities.KeyStatus.ACTIVE -> {
                        "🟢 ${info.maskedKey} (Active - Ready to use)"
                    }
                    com.aris.voice.utilities.KeyStatus.EXHAUSTED -> {
                        "🔴 ${info.maskedKey} (Exhausted ❌ — 429 Rate Limit Hit)"
                    }
                    com.aris.voice.utilities.KeyStatus.COOLDOWN -> {
                        "🟡 ${info.maskedKey} (Cooldown — Pending recovery: ${info.remainingSeconds}s)"
                    }
                }
                text = statusText
            }
            layoutKeyHealthMonitor.addView(textView)
        }
    }

    private fun saveSelectedVoice(voice: TTSVoice) {
        VoicePreferenceManager.saveSelectedVoice(this, voice)
        Log.d("SettingsActivity", "Saved voice: ${voice.displayName}")
    }

    private fun showPicovoiceKeyRequiredDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Picovoice Key Required")
            .setMessage("To enable wake word functionality, you need a Picovoice AccessKey. You can get a free key from the Picovoice Console. Note: The Picovoice dashboard might not be available on mobile browsers sometimes - you may need to use a desktop browser.")
            .setPositiveButton("Get Key") { _, _ ->
                // Try to open Picovoice console
                val url = "https://console.picovoice.ai/login"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open link. No browser found or link unavailable on mobile. Please use a desktop browser.", Toast.LENGTH_LONG).show()
                    Log.e("SettingsActivity", "Failed to open Picovoice link", e)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        
        // Set button text colors to white
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )
    }

    private fun updateWakeWordButtonState() {
        wakeWordManager.updateButtonState(wakeWordButton)
    }

    private fun showBatteryOptimizationDialog() {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isWhitelisted = pm.isIgnoringBatteryOptimizations(packageName)
        val statusMsg = if (isWhitelisted) "✅ Battery optimization disabled (good!)" else "⚠️ Battery optimization enabled (may affect wake word)"
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.battery_optimization_title))
            .setMessage("$statusMsg\n\n${getString(R.string.battery_optimization_message)}")
            .setPositiveButton(getString(R.string.learn_how)) { _, _ ->
                // Open the Tasker FAQ URL
                val url = "https://tasker.joaoapps.com/userguide/en/faqs/faq-problem.html#00"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open link. No browser found.", Toast.LENGTH_LONG).show()
                    Log.e("SettingsActivity", "Failed to open battery optimization link", e)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        
        // Set button text colors to white
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )
    }

    private fun showSignOutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out? This will clear all your settings and data.")
            .setPositiveButton("Sign Out") { _, _ ->
                signOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signOut() {
        // Stop all running services first
        stopService(Intent(this, ConversationalAgentService::class.java))
        stopService(Intent(this, com.aris.voice.services.EnhancedWakeWordService::class.java))
        stopService(Intent(this, com.aris.voice.v2.AgentService::class.java))

        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Firebase sign out error", e)
        }

        // Clear User Profile
        val userProfileManager = UserProfileManager(this)
        userProfileManager.clearProfile()

        // Clear all shared preferences for this app
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()


        // Restart the app by navigating to the onboarding screen
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    
    private fun setupThemeSelection() {
        fun updateThemeHighlighting() {
            val currentTheme = com.aris.voice.utilities.ThemeManager.getSelectedTheme(this)
            val accentColor = com.aris.voice.utilities.ThemeManager.getThemeColors(currentTheme).accentColor
            val density = resources.displayMetrics.density
            
            val defaultDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(if (currentTheme == com.aris.voice.utilities.ThemeManager.THEME_DEFAULT) accentColor else android.graphics.Color.parseColor("#151525"))
            }
            val darkDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(if (currentTheme == com.aris.voice.utilities.ThemeManager.THEME_DARK) accentColor else android.graphics.Color.parseColor("#151525"))
            }
            val lightDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(if (currentTheme == com.aris.voice.utilities.ThemeManager.THEME_LIGHT) accentColor else android.graphics.Color.parseColor("#151525"))
            }
            
            btnThemeDefault.background = defaultDrawable
            btnThemeDark.background = darkDrawable
            btnThemeLight.background = lightDrawable
            
            btnThemeDefault.setTextColor(if (currentTheme == com.aris.voice.utilities.ThemeManager.THEME_DEFAULT) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            btnThemeDark.setTextColor(if (currentTheme == com.aris.voice.utilities.ThemeManager.THEME_DARK) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            btnThemeLight.setTextColor(if (currentTheme == com.aris.voice.utilities.ThemeManager.THEME_LIGHT) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        
        btnThemeDefault.setOnClickListener {
            com.aris.voice.utilities.ThemeManager.setSelectedTheme(this, com.aris.voice.utilities.ThemeManager.THEME_DEFAULT)
            updateThemeHighlighting()
            com.aris.voice.utilities.ThemeManager.applyTheme(this)
            android.widget.Toast.makeText(this, "Silver Dark Theme Activated", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        btnThemeDark.setOnClickListener {
            com.aris.voice.utilities.ThemeManager.setSelectedTheme(this, com.aris.voice.utilities.ThemeManager.THEME_DARK)
            updateThemeHighlighting()
            com.aris.voice.utilities.ThemeManager.applyTheme(this)
            android.widget.Toast.makeText(this, "Carbon Obsidian Theme Activated", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        btnThemeLight.setOnClickListener {
            com.aris.voice.utilities.ThemeManager.setSelectedTheme(this, com.aris.voice.utilities.ThemeManager.THEME_LIGHT)
            updateThemeHighlighting()
            com.aris.voice.utilities.ThemeManager.applyTheme(this)
            android.widget.Toast.makeText(this, "Cyber Titanium Theme Activated", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        updateThemeHighlighting()
    }

    override fun getContentLayoutId(): Int = R.layout.activity_settings
    
    override fun getCurrentNavItem(): BaseNavigationActivity.NavItem = BaseNavigationActivity.NavItem.SETTINGS
}