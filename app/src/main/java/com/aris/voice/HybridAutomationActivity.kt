package com.aris.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.aris.voice.api.Finger
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.IntentRegistry
import com.aris.voice.intents.ParameterSpec
import java.text.SimpleDateFormat
import java.util.*

class HybridAutomationActivity : BaseNavigationActivity() {

    private lateinit var spinnerIntents: Spinner
    private lateinit var textIntentDescription: TextView
    private lateinit var containerIntentParameters: LinearLayout
    private lateinit var btnLaunchHae: View
    private lateinit var textHaeTerminalLogs: TextView

    private lateinit var finger: Finger
    private var selectedIntent: AppIntent? = null
    private val paramEditFields = mutableMapOf<String, EditText>()

    override fun getContentLayoutId(): Int = R.layout.activity_hybrid_automation

    override fun getCurrentNavItem(): NavItem = NavItem.SETTINGS // Part of the settings context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hybrid_automation)

        finger = Finger(this)

        initViews()
        setupSpinner()
    }

    private fun initViews() {
        spinnerIntents = findViewById(R.id.spinner_intents)
        textIntentDescription = findViewById(R.id.text_intent_description)
        containerIntentParameters = findViewById(R.id.container_intent_parameters)
        btnLaunchHae = findViewById(R.id.btn_launch_hae)
        textHaeTerminalLogs = findViewById(R.id.text_hae_terminal_logs)

        textHaeTerminalLogs.movementMethod = ScrollingMovementMethod()

        btnLaunchHae.setOnClickListener {
            triggerSelectedIntent()
        }
    }

    private fun logToTerminal(message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(Date())
        val currentLog = textHaeTerminalLogs.text.toString()
        val newLog = "$currentLog\n[$timeStr] $message"
        textHaeTerminalLogs.text = newLog

        // Auto Scroll to Bottom of Terminal
        val scrollAmount = textHaeTerminalLogs.layout?.let {
            it.lineCount * textHaeTerminalLogs.lineHeight - textHaeTerminalLogs.height
        } ?: 0
        if (scrollAmount > 0) {
            textHaeTerminalLogs.scrollTo(0, scrollAmount)
        }
    }

    private fun setupSpinner() {
        val intents = IntentRegistry.listIntents(this)
        val intentNames = intents.map { it.name }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intentNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerIntents.adapter = adapter

        spinnerIntents.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val intentName = intentNames[position]
                selectedIntent = IntentRegistry.findByName(this@HybridAutomationActivity, intentName)
                updateIntentDetails()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedIntent = null
                updateIntentDetails()
            }
        }
    }

    private fun updateIntentDetails() {
        containerIntentParameters.removeAllViews()
        paramEditFields.clear()

        val intent = selectedIntent
        if (intent == null) {
            textIntentDescription.text = "No AppIntent selected."
            return
        }

        textIntentDescription.text = intent.description()
        logToTerminal("Inspecting Intent: ${intent.name}")

        val paramsSpec = intent.parametersSpec()
        if (paramsSpec.isEmpty()) {
            val emptyInfo = TextView(this).apply {
                text = "This intent takes no parameters."
                setTextColor(ContextCompat.getColor(this@HybridAutomationActivity, R.color.white))
                textSize = 13f
                setPadding(0, 10, 0, 10)
            }
            containerIntentParameters.addView(emptyInfo)
            return
        }

        val density = resources.displayMetrics.density
        for (spec in paramsSpec) {
            // Label
            val labelView = TextView(this).apply {
                text = spec.name.uppercase(Locale.US) + (if (spec.required) " (Required)" else " (Optional)")
                setTextColor(ContextCompat.getColor(this@HybridAutomationActivity, R.color.text_secondary))
                textSize = 10f
                setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
                try {
                    val font = androidx.core.content.res.ResourcesCompat.getFont(this@HybridAutomationActivity, R.font.oxanium)
                    typeface = font
                } catch (e: Exception) {
                    // Ignore
                }
            }
            containerIntentParameters.addView(labelView)

            // EditText Input
            val editView = EditText(this).apply {
                background = ContextCompat.getDrawable(this@HybridAutomationActivity, R.drawable.edit_text_border)
                setTextColor(ContextCompat.getColor(this@HybridAutomationActivity, R.color.white))
                textSize = 13f
                hint = spec.description
                setHintTextColor(0x55FFFFFF)
                setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (45 * density).toInt()
                ).apply {
                    bottomMargin = (8 * density).toInt()
                }
                try {
                    val font = androidx.core.content.res.ResourcesCompat.getFont(this@HybridAutomationActivity, R.font.oxanium)
                    typeface = font
                } catch (e: Exception) {
                    // Ignore
                }
            }
            containerIntentParameters.addView(editView)
            paramEditFields[spec.name] = editView
        }
    }

    private fun triggerSelectedIntent() {
        val intentSpec = selectedIntent
        if (intentSpec == null) {
            Toast.makeText(this, "Select a valid intent first", Toast.LENGTH_SHORT).show()
            return
        }

        logToTerminal("Starting HAE Direct Bypass Simulation...")
        logToTerminal("Mapping values for intent parameters:")

        val params = mutableMapOf<String, String>()
        for ((name, editText) in paramEditFields) {
            val valStr = editText.text.toString().trim()
            logToTerminal(" - $name -> '$valStr'")
            if (valStr.isNotEmpty()) {
                params[name] = valStr
            }
        }

        try {
            logToTerminal("Building Intent with the compiled parameters Map...")
            val builtIntent = intentSpec.buildIntent(this, params)
            if (builtIntent == null) {
                logToTerminal("Error: buildIntent() returned null (Validation / params format error).")
                Toast.makeText(this, "Validation Failed: Parameter(s) missing or malformed.", Toast.LENGTH_SHORT).show()
                return
            }

            logToTerminal("Intent successfully built. Resolving Uri/Action: ${builtIntent.action} / ${builtIntent.data}")
            logToTerminal("Bypassing UI click layers. Invoking Tier 1 / Tier 2 direct launching pipeline...")
            
            val launched = finger.launchIntent(builtIntent)
            if (launched) {
                logToTerminal("System bypass triggered successfully. Intent dispatched directly to Android OS!")
                Toast.makeText(this, "Direct hybrid bypass executed!", Toast.LENGTH_SHORT).show()
            } else {
                logToTerminal("Error: OS failed to start intent. Missing application or handler.")
                Toast.makeText(this, "Intent failed to start on OS level.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            logToTerminal("Execution crashed: ${e.message}")
            Toast.makeText(this, "HAE trigger exception: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
