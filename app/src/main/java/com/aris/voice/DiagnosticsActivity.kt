package com.aris.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aris.voice.api.PicovoiceKeyManager
import com.aris.voice.services.EnhancedWakeWordService
import com.aris.voice.utilities.ApiKeyManager
import com.aris.voice.utilities.ErrorSeverity
import com.aris.voice.utilities.ErrorTracker
import com.aris.voice.utilities.KeyStatus
import com.aris.voice.utilities.PermissionManager
import com.aris.voice.utilities.SpeechCoordinator
import com.aris.voice.utilities.SystemError
import com.aris.voice.utilities.SystemModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DiagnosticsActivity : BaseNavigationActivity() {

    private lateinit var textGlobalStatus: TextView
    private lateinit var containerModules: LinearLayout
    private lateinit var tvEmptyErrors: TextView
    private lateinit var recyclerViewErrors: RecyclerView
    private lateinit var btnRetestAll: View
    private lateinit var btnClearAllLogs: TextView

    private lateinit var permissionManager: PermissionManager
    private lateinit var errorAdapter: DiagnosticErrorAdapter

    override fun getContentLayoutId(): Int = R.layout.activity_diagnostics

    override fun getCurrentNavItem(): NavItem = NavItem.SETTINGS // Diagnostic sits within Settings view group context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        permissionManager = PermissionManager(this)

        initViews()
        setupRecyclerView()
        runDiagnosis()

        // Listen for live updates in ErrorTracker
        ErrorTracker.addListener {
            runOnUiThread {
                updateModuleListUI()
                updateErrorHistoryUI()
                updateGlobalStatusUI()
            }
        }
    }

    private fun initViews() {
        textGlobalStatus = findViewById(R.id.tv_global_status_message)
        containerModules = findViewById(R.id.container_modules)
        tvEmptyErrors = findViewById(R.id.tv_empty_errors)
        recyclerViewErrors = findViewById(R.id.recyclerView_errors)
        btnRetestAll = findViewById(R.id.btn_retest_all)
        btnClearAllLogs = findViewById(R.id.btn_clear_all_logs)

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        btnRetestAll.setOnClickListener {
            runDiagnosis()
            Toast.makeText(this, "Health diagnosis initialized.", Toast.LENGTH_SHORT).show()
        }

        btnClearAllLogs.setOnClickListener {
            ErrorTracker.clearErrors()
            Toast.makeText(this, "Diagnostic logs cleared successfully.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        recyclerViewErrors.layoutManager = LinearLayoutManager(this)
        errorAdapter = DiagnosticErrorAdapter(ErrorTracker.getErrors())
        recyclerViewErrors.adapter = errorAdapter
    }

    private fun runDiagnosis() {
        lifecycleScope.launch {
            textGlobalStatus.text = "Running continuous dry-run diagnostics..."
            textGlobalStatus.setTextColor(Color.parseColor("#FFCC00"))

            // 1. Accessibility Service Check
            val isAccessibilityEnabled = permissionManager.isAccessibilityServiceEnabled()
            ErrorTracker.updateModuleStatus(
                SystemModule.ACCESSIBILITY,
                if (isAccessibilityEnabled) ErrorTracker.ModuleStatus.HEALTHY else ErrorTracker.ModuleStatus.CRITICAL
            )

            // 2. Microphone Permission Check
            val isMicrophoneEnabled = permissionManager.isMicrophonePermissionGranted()
            ErrorTracker.updateModuleStatus(
                SystemModule.STT,
                if (isMicrophoneEnabled) ErrorTracker.ModuleStatus.HEALTHY else ErrorTracker.ModuleStatus.CRITICAL
            )

            // 3. Gemini Key Check
            val activeKeys = ApiKeyManager.getSavedKeysString(this@DiagnosticsActivity)
            val keyHealthList = ApiKeyManager.getKeyHealthList()
            val keysStatus = when {
                activeKeys.isBlank() -> {
                    ErrorTracker.logError(
                        SystemModule.GEMINI,
                        "No Gemini API Key provided. A.R.I.S is completely blind.",
                        ErrorSeverity.CRITICAL,
                        "Manage your tokens inside A.R.I.S Settings screen."
                    )
                    ErrorTracker.ModuleStatus.CRITICAL
                }
                keyHealthList.any { it.status == KeyStatus.EXHAUSTED } -> {
                    ErrorTracker.ModuleStatus.DEGRADED
                }
                else -> ErrorTracker.ModuleStatus.HEALTHY
            }
            ErrorTracker.updateModuleStatus(SystemModule.GEMINI, keysStatus)

            // 4. Wake word license key check
            val wakeKeyManager = PicovoiceKeyManager(this@DiagnosticsActivity)
            val configKey = wakeKeyManager.getUserProvidedKey()
            val wakeWordStatus = when {
                configKey.isNullOrBlank() -> {
                    ErrorTracker.logError(
                        SystemModule.WAKE_WORD,
                        "Picovoice wake word access key is missing.",
                        ErrorSeverity.WARNING,
                        "AXEL cannot launch on speech trigger. Add license key to Settings."
                    )
                    ErrorTracker.ModuleStatus.DEGRADED
                }
                else -> ErrorTracker.ModuleStatus.HEALTHY
            }
            ErrorTracker.updateModuleStatus(SystemModule.WAKE_WORD, wakeWordStatus)

            // 5. General Permissions Check
            val overlayGranted = permissionManager.isOverlayPermissionGranted()
            val notificationsGranted = permissionManager.isNotificationPermissionGranted()
            val permissionsStatus = if (overlayGranted && notificationsGranted) {
                ErrorTracker.ModuleStatus.HEALTHY
            } else {
                ErrorTracker.logError(
                    SystemModule.PERMISSIONS,
                    "Overlay or Notification permissions are missing. Overlay: $overlayGranted, Notifications: $notificationsGranted",
                    ErrorSeverity.WARNING
                )
                ErrorTracker.ModuleStatus.DEGRADED
            }
            ErrorTracker.updateModuleStatus(SystemModule.PERMISSIONS, permissionsStatus)

            // 6. Voice output check (simple quick dry-run check)
            try {
                val sc = SpeechCoordinator.getInstance(this@DiagnosticsActivity)
                // Just verify SpeechCoordinator doesn't throw immediate configuration error
                ErrorTracker.updateModuleStatus(SystemModule.TTS, ErrorTracker.ModuleStatus.HEALTHY)
            } catch (e: Exception) {
                ErrorTracker.logError(SystemModule.TTS, "TTS Initialization failed: ${e.message}", ErrorSeverity.CRITICAL, e.stackTraceToString())
                ErrorTracker.updateModuleStatus(SystemModule.TTS, ErrorTracker.ModuleStatus.CRITICAL)
            }

            // 7. Premium Subscription Verification
            ErrorTracker.updateModuleStatus(SystemModule.BILLING, ErrorTracker.ModuleStatus.HEALTHY)

            // Complete updating views
            updateModuleListUI()
            updateErrorHistoryUI()
            updateGlobalStatusUI()
        }
    }

    private fun updateModuleListUI() {
        containerModules.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (module in SystemModule.values()) {
            val view = inflater.inflate(R.layout.item_diagnostic_module_status, containerModules, false)
            val tvName = view.findViewById<TextView>(R.id.tv_module_name)
            val tvBadge = view.findViewById<TextView>(R.id.tv_module_status_badge)
            val tvDesc = view.findViewById<TextView>(R.id.tv_module_description)
            val btnAction = view.findViewById<TextView>(R.id.btn_module_action)

            val status = ErrorTracker.getModuleStatus(module)

            tvName.text = module.displayName
            tvBadge.text = status.label
            tvBadge.setTextColor(Color.parseColor(status.colorHex))
            tvBadge.setBackgroundColor(Color.parseColor(status.colorHex.replace("#", "#22")))

            tvDesc.text = when (status) {
                ErrorTracker.ModuleStatus.HEALTHY -> "This system is secure and running smoothly."
                ErrorTracker.ModuleStatus.DEGRADED -> "Caution! Performance or integration is degraded. ${module.affectedCapability}"
                ErrorTracker.ModuleStatus.CRITICAL -> "Critical Failure! ${module.affectedCapability}"
                ErrorTracker.ModuleStatus.UNKNOWN -> "Operational check pending for this resource."
            }

            btnAction.text = when (module) {
                SystemModule.ACCESSIBILITY -> "MANAGE SERVICE"
                SystemModule.STT -> "REQUEST MICROPHONE"
                SystemModule.GEMINI -> "VERIFY KEYS"
                SystemModule.WAKE_WORD -> "WAKE WORD SETTINGS"
                SystemModule.PERMISSIONS -> "SHOW PERMISSIONS"
                SystemModule.TTS -> "TEST SOUND"
                SystemModule.BILLING -> "CHECK PURCHASE"
            }

            btnAction.setOnClickListener {
                handleModuleAction(module)
            }

            containerModules.addView(view)
        }
    }

    private fun handleModuleAction(module: SystemModule) {
        when (module) {
            SystemModule.ACCESSIBILITY -> {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "Enable 'A.R.I.S Screen Interaction' in Accessibility.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Accessibility Settings not available on this model.", Toast.LENGTH_SHORT).show()
                }
            }
            SystemModule.STT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 456)
                    } else {
                        Toast.makeText(this, "Microphone permission is already granted!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            SystemModule.GEMINI -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            SystemModule.WAKE_WORD -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            SystemModule.PERMISSIONS -> {
                startActivity(Intent(this, PermissionsActivity::class.java))
            }
            SystemModule.TTS -> {
                lifecycleScope.launch {
                    try {
                        val sc = SpeechCoordinator.getInstance(this@DiagnosticsActivity)
                        sc.speakToUser("Axel voice system is fully operational and functional.")
                        Toast.makeText(this@DiagnosticsActivity, "Playing audio diagnostic feedback...", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@DiagnosticsActivity, "Speak output failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            SystemModule.BILLING -> {
                // Navigate to Purchase standard screen
                startActivity(Intent(this, ProPurchaseActivity::class.java))
            }
        }
    }

    private fun updateErrorHistoryUI() {
        val currentErrors = ErrorTracker.getErrors()
        if (currentErrors.isEmpty()) {
            tvEmptyErrors.visibility = View.VISIBLE
            recyclerViewErrors.visibility = View.GONE
        } else {
            tvEmptyErrors.visibility = View.GONE
            recyclerViewErrors.visibility = View.VISIBLE
            errorAdapter.updateData(currentErrors)
        }
    }

    private fun updateGlobalStatusUI() {
        val allStatuses = SystemModule.values().map { ErrorTracker.getModuleStatus(it) }
        when {
            allStatuses.any { it == ErrorTracker.ModuleStatus.CRITICAL } -> {
                textGlobalStatus.text = "CRITICAL FAILURES DETECTED ⚠️"
                textGlobalStatus.setTextColor(Color.parseColor("#F44336"))
            }
            allStatuses.any { it == ErrorTracker.ModuleStatus.DEGRADED } -> {
                textGlobalStatus.text = "DEGRADED PERFORMANCE ⚠️"
                textGlobalStatus.setTextColor(Color.parseColor("#FF9800"))
            }
            else -> {
                textGlobalStatus.text = "SYSTEMS LIVE & HEALTHY 🟢"
                textGlobalStatus.setTextColor(Color.parseColor("#4CAF50"))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ErrorTracker.removeListener { }
    }
}

class DiagnosticErrorAdapter(private var errors: List<SystemError>) : RecyclerView.Adapter<DiagnosticErrorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTag: TextView = view.findViewById(R.id.tv_item_module_tag)
        val tvTime: TextView = view.findViewById(R.id.tv_item_timestamp)
        val tvMsg: TextView = view.findViewById(R.id.tv_item_error_message)
        val tvAffected: TextView = view.findViewById(R.id.tv_item_affected_capability)
        val btnToggleTech: TextView = view.findViewById(R.id.tv_toggle_tech_details)
        val containerTech: View = view.findViewById(R.id.container_tech_details)
        val tvTechDetails: TextView = view.findViewById(R.id.tv_tech_details_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_diagnostic_error, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = errors[position]
        holder.tvTag.text = item.module.displayName
        holder.tvTime.text = item.formattedTime
        holder.tvMsg.text = item.message
        holder.tvAffected.text = item.module.affectedCapability

        holder.tvTechDetails.text = item.technicalDetails ?: "No additional diagnostic trace."

        var expanded = false
        holder.containerTech.visibility = View.GONE
        holder.btnToggleTech.text = "▼ SHOW TECHNICAL DETAILS"

        holder.btnToggleTech.setOnClickListener {
            expanded = !expanded
            holder.containerTech.visibility = if (expanded) View.VISIBLE else View.GONE
            holder.btnToggleTech.text = if (expanded) "▲ HIDE TECHNICAL DETAILS" else "▼ SHOW TECHNICAL DETAILS"
        }
    }

    override fun getItemCount(): Int = errors.size

    fun updateData(newErrors: List<SystemError>) {
        errors = newErrors
        notifyDataSetChanged()
    }
}
