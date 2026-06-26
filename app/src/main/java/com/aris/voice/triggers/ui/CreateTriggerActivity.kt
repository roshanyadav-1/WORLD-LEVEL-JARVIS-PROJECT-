package com.aris.voice.triggers.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aris.voice.R
import com.aris.voice.triggers.PermissionUtils
import com.aris.voice.triggers.Trigger
import com.aris.voice.triggers.TriggerManager
import com.aris.voice.triggers.TriggerType
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateTriggerActivity : AppCompatActivity() {

    private lateinit var triggerManager: TriggerManager
    private lateinit var instructionEditText: EditText
    private lateinit var searchEditText: EditText
    private lateinit var scheduledTimeOptions: LinearLayout
    private lateinit var notificationOptions: LinearLayout
    private lateinit var chargingStateOptions: LinearLayout
    private lateinit var timePicker: TimePicker
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var dayOfWeekChipGroup: com.google.android.material.chip.ChipGroup
    private lateinit var appAdapter: AppAdapter
    private lateinit var scrollView: ScrollView
    private lateinit var selectAllAppsCheckbox: CheckBox
    private lateinit var notificationPermissionWarning: LinearLayout
    private lateinit var grantNotificationPermissionButton: TextView
    private lateinit var alarmPermissionWarning: LinearLayout
    private lateinit var grantAlarmPermissionButton: TextView

    private var selectedTriggerType = TriggerType.SCHEDULED_TIME
    private var selectedApps = listOf<AppInfo>()
    private var existingTrigger: Trigger? = null

    private lateinit var triggerTypeSpinner: android.widget.Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trigger)
        com.aris.voice.utilities.ThemeManager.applyTheme(this)

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener {
            onBackPressed()
        }

        triggerManager = TriggerManager.getInstance(this)
        triggerTypeSpinner = findViewById(R.id.triggerTypeSpinner)
        instructionEditText = findViewById(R.id.instructionEditText)
        searchEditText = findViewById(R.id.searchEditText)
        scheduledTimeOptions = findViewById(R.id.scheduledTimeOptions)
        notificationOptions = findViewById(R.id.notificationOptions)
        chargingStateOptions = findViewById(R.id.chargingStateOptions)
        timePicker = findViewById(R.id.timePicker)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        dayOfWeekChipGroup = findViewById(R.id.dayOfWeekChipGroup)
        notificationPermissionWarning = findViewById(R.id.notificationPermissionWarning)
        grantNotificationPermissionButton = findViewById(R.id.grantNotificationPermissionButton)
        alarmPermissionWarning = findViewById(R.id.alarmPermissionWarning)
        grantAlarmPermissionButton = findViewById(R.id.grantAlarmPermissionButton)

        val saveButton = findViewById<TextView>(R.id.saveTriggerButton)

        // Setup the Spinner
        val triggerTypes = TriggerType.values()
        val triggerTypeNames = triggerTypes.map { it.name.replace("_", " ").lowercase().replaceFirstChar { char -> char.uppercase() } }
        val spinnerAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, triggerTypeNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        triggerTypeSpinner.adapter = spinnerAdapter
        
        triggerTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTriggerType = triggerTypes[position]
                setupInitialView()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val triggerId = intent.getStringExtra("EXTRA_TRIGGER_ID")
        if (triggerId != null) {
            // Edit mode
            lifecycleScope.launch {
                existingTrigger = withContext(Dispatchers.IO) {
                    triggerManager.getTriggers().find { it.id == triggerId }
                }
                if (existingTrigger != null) {
                    selectedTriggerType = existingTrigger!!.type
                    triggerTypeSpinner.setSelection(triggerTypes.indexOf(selectedTriggerType))
                    populateUiWithTriggerData(existingTrigger!!)
                    saveButton.text = "Update Trigger"
                } else {
                    Toast.makeText(this@CreateTriggerActivity, "Trigger not found.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } else {
            // Create mode
            selectedTriggerType = (intent.getSerializableExtra("EXTRA_TRIGGER_TYPE") as? TriggerType) ?: TriggerType.SCHEDULED_TIME
            triggerTypeSpinner.setSelection(triggerTypes.indexOf(selectedTriggerType))
            for (i in 0 until dayOfWeekChipGroup.childCount) {
                (dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip).isChecked = true
            }
        }

        setupInitialView()

        setupRecyclerView()
        loadApps()

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter.filter(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        selectAllAppsCheckbox = findViewById(R.id.selectAllAppsCheckbox)
        selectAllAppsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            appsRecyclerView.isEnabled = !isChecked
            appsRecyclerView.alpha = if (isChecked) 0.5f else 1.0f
            if (isChecked) {
                appAdapter.setSelectedApps(emptyList())
                selectedApps = emptyList()
            }
        }

        saveButton.setOnClickListener {
            saveTrigger()
        }

        val testButton = findViewById<TextView>(R.id.testTriggerButton)
        testButton.setOnClickListener {
            testTrigger()
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
        checkAlarmPermission()
    }

    private fun checkNotificationPermission() {
        if (selectedTriggerType == TriggerType.NOTIFICATION) {
            if (PermissionUtils.isNotificationListenerEnabled(this)) {
                notificationPermissionWarning.visibility = View.GONE
            } else {
                notificationPermissionWarning.visibility = View.VISIBLE
                grantNotificationPermissionButton.setOnClickListener {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    startActivity(intent)
                }
            }
        }
    }

    private fun checkAlarmPermission() {
        if (selectedTriggerType == TriggerType.SCHEDULED_TIME) {
            if (PermissionUtils.canScheduleExactAlarms(this)) {
                alarmPermissionWarning.visibility = View.GONE
            } else {
                alarmPermissionWarning.visibility = View.VISIBLE
                grantAlarmPermissionButton.setOnClickListener {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                }
            }
        }
    }

    private fun testTrigger() {
        val instruction = instructionEditText.text.toString()
        if (instruction.isBlank()) {
            Toast.makeText(this, "Instruction cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        triggerManager.executeInstruction(instruction)
        Toast.makeText(this, "Test trigger fired!", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun populateUiWithTriggerData(trigger: Trigger) {
        instructionEditText.setText(trigger.instruction)

        when (trigger.type) {
            TriggerType.SCHEDULED_TIME -> {
                timePicker.hour = trigger.hour ?: 0
                timePicker.minute = trigger.minute ?: 0
                // Clear all chips first
                for (i in 0 until dayOfWeekChipGroup.childCount) {
                    (dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip).isChecked = false
                }
                // Then check the ones from the trigger
                trigger.daysOfWeek.forEach { day ->
                    (dayOfWeekChipGroup.getChildAt(day - 1) as com.google.android.material.chip.Chip).isChecked = true
                }
            }
            TriggerType.NOTIFICATION -> {
                if (trigger.packageName == "*") {
                    selectAllAppsCheckbox.isChecked = true
                } else {
                    // This will be handled in loadApps now
                }
            }
            TriggerType.CHARGING_STATE -> {
                val radioGroup = findViewById<RadioGroup>(R.id.chargingStatusRadioGroup)
                if (trigger.chargingStatus == "Connected") {
                    radioGroup.check(R.id.radioConnected)
                } else {
                    radioGroup.check(R.id.radioDisconnected)
                }
            }
            else -> {
                // Reserved for advanced triggers
            }
        }
    }

    private fun setupInitialView() {
        when (selectedTriggerType) {
            TriggerType.SCHEDULED_TIME -> {
                scheduledTimeOptions.visibility = View.VISIBLE
                notificationOptions.visibility = View.GONE
                chargingStateOptions.visibility = View.GONE
                // Hide other permission warnings
                notificationPermissionWarning.visibility = View.GONE
            }
            TriggerType.NOTIFICATION -> {
                scheduledTimeOptions.visibility = View.GONE
                notificationOptions.visibility = View.VISIBLE
                chargingStateOptions.visibility = View.GONE
                // Hide other permission warnings
                alarmPermissionWarning.visibility = View.GONE
            }
            TriggerType.CHARGING_STATE -> {
                scheduledTimeOptions.visibility = View.GONE
                notificationOptions.visibility = View.GONE
                chargingStateOptions.visibility = View.VISIBLE
                // Hide other permission warnings
                notificationPermissionWarning.visibility = View.GONE
                alarmPermissionWarning.visibility = View.GONE
            }
            TriggerType.APP_OPENED,
            TriggerType.LOCATION_BASED,
            TriggerType.BATTERY_LEVEL,
            TriggerType.CALENDAR_EVENT,
            TriggerType.CONTACT_CALL,
            TriggerType.SCREEN_ON_OFF,
            TriggerType.COMPOUND -> {
                scheduledTimeOptions.visibility = View.GONE
                notificationOptions.visibility = View.GONE
                chargingStateOptions.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        appsRecyclerView.layoutManager = LinearLayoutManager(this)
        appAdapter = AppAdapter(emptyList()) { apps ->
            selectedApps = apps
        }
        appsRecyclerView.adapter = appAdapter
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map {
                    AppInfo(
                        appName = it.loadLabel(pm).toString(),
                        packageName = it.packageName,
                        icon = it.loadIcon(pm)
                    )
                }
                .sortedBy { it.appName }

            withContext(Dispatchers.Main) {
                appAdapter.updateApps(apps)
                if (existingTrigger != null && existingTrigger!!.type == TriggerType.NOTIFICATION) {
                    val selectedPackageNames = existingTrigger!!.packageName?.split(",") ?: emptyList()
                    val preSelectedApps = apps.filter { it.packageName in selectedPackageNames }
                    appAdapter.setSelectedApps(preSelectedApps)
                    selectedApps = preSelectedApps
                }
            }
        }
    }

    private fun saveTrigger() {
        val instruction = instructionEditText.text.toString()
        if (instruction.isBlank()) {
            Toast.makeText(this, "Instruction cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val trigger: Trigger
        when (selectedTriggerType) {
            TriggerType.SCHEDULED_TIME -> {
                if (!com.aris.voice.triggers.PermissionUtils.canScheduleExactAlarms(this)) {
                    showExactAlarmPermissionDialog()
                    return
                }
                val selectedDays = getSelectedDays()
                if (selectedDays.isEmpty()) {
                    Toast.makeText(this, "Please select at least one day", Toast.LENGTH_SHORT).show()
                    return
                }
                trigger = Trigger(
                    id = existingTrigger?.id ?: UUID.randomUUID().toString(),
                    type = TriggerType.SCHEDULED_TIME,
                    hour = timePicker.hour,
                    minute = timePicker.minute,
                    instruction = instruction,
                    daysOfWeek = selectedDays,
                    isEnabled = existingTrigger?.isEnabled ?: true
                )
            }
            TriggerType.NOTIFICATION -> {
                if (!PermissionUtils.isNotificationListenerEnabled(this)) {
                    Toast.makeText(this, "Notification listener permission is required.", Toast.LENGTH_SHORT).show()
                    checkNotificationPermission()
                    return
                }

                val packageName: String
                val appName: String
                if (selectAllAppsCheckbox.isChecked) {
                    packageName = "*"
                    appName = "All Applications"
                } else {
                    if (selectedApps.isEmpty()) {
                        Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
                        return
                    }
                    packageName = selectedApps.joinToString(",") { it.packageName }
                    appName = selectedApps.joinToString(",") { it.appName }
                }

                trigger = Trigger(
                    id = existingTrigger?.id ?: UUID.randomUUID().toString(),
                    type = TriggerType.NOTIFICATION,
                    packageName = packageName,
                    appName = appName,
                    instruction = instruction,
                    isEnabled = existingTrigger?.isEnabled ?: true
                )
            }
            TriggerType.CHARGING_STATE -> {
                val radioGroup = findViewById<RadioGroup>(R.id.chargingStatusRadioGroup)
                val selectedStatus = if (radioGroup.checkedRadioButtonId == R.id.radioConnected) {
                    "Connected"
                } else {
                    "Disconnected"
                }
                trigger = Trigger(
                    id = existingTrigger?.id ?: UUID.randomUUID().toString(),
                    type = TriggerType.CHARGING_STATE,
                    chargingStatus = selectedStatus,
                    instruction = instruction,
                    isEnabled = existingTrigger?.isEnabled ?: true
                )
            }
            else -> {
                trigger = Trigger(
                    id = existingTrigger?.id ?: UUID.randomUUID().toString(),
                    type = selectedTriggerType,
                    instruction = instruction,
                    isEnabled = existingTrigger?.isEnabled ?: true
                )
            }
        }

        if (triggerManager.hasConflict(trigger)) {
            Toast.makeText(this, "A similar trigger already exists at this time or for this app.", Toast.LENGTH_LONG).show()
            return
        }

        if (existingTrigger != null) {
            triggerManager.updateTrigger(trigger)
            Toast.makeText(this, "Trigger updated!", Toast.LENGTH_SHORT).show()
        } else {
            triggerManager.addTrigger(trigger)
            Toast.makeText(this, "Trigger saved!", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun getSelectedDays(): Set<Int> {
        val selectedDays = mutableSetOf<Int>()
        for (i in 0 until dayOfWeekChipGroup.childCount) {
            val chip = dayOfWeekChipGroup.getChildAt(i) as com.google.android.material.chip.Chip
            if (chip.isChecked) {
                // Mapping index to Calendar.DAY_OF_WEEK constants (Sunday=1, Monday=2, etc.)
                selectedDays.add(i + 1)
            }
        }
        return selectedDays
    }

    private fun showExactAlarmPermissionDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To schedule tasks at a precise time, Aris needs the 'Alarms & Reminders' permission. Please grant this in the next screen.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.white))
    }
}
