package com.aris.voice.perception

import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import com.aris.voice.domain.DeviceContext

class PerceptionImpl(private val context: Context) :
    IPerceptionEngine,
    IScreenAnalyzer,
    IAccessibilityParser,
    IDeviceStateMonitor {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // IDeviceStateMonitor
    override fun getBatteryLevel(): Int {
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun isNetworkConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun getLastClipboardEntry(): String? {
        val primaryClip = clipboardManager.primaryClip
        if (primaryClip != null && primaryClip.itemCount > 0) {
            return primaryClip.getItemAt(0).text?.toString()
        }
        return null
    }

    override fun getActiveNotifications(): List<String> {
        // Return a static placeholder or mock notification titles for environment safety
        return listOf("System check active")
    }

    // IScreenAnalyzer
    override suspend fun getActiveAppPackage(): String? {
        // Fallback for environment safety
        return "com.aris.voice"
    }

    override suspend fun getScreenHierarchyHash(): String? {
        return "hash_default"
    }

    // IAccessibilityParser
    override suspend fun parseCurrentWindowNodes(): ArisResult<List<String>> {
        return ArisResult.Success(listOf("Node: root", "Node: Button[id=submit]", "Node: TextView[text=A.R.I.S]"))
    }

    // IPerceptionEngine
    override suspend fun observeEnvironment(): ArisResult<DeviceContext> {
        return try {
            val deviceContext = DeviceContext(
                currentAppPackage = getActiveAppPackage(),
                currentScreenLabel = "Home Dashboard",
                visibleUiTextElements = parseCurrentWindowNodes().getOrNull() ?: emptyList(),
                runningBackgroundTasks = listOf("AgentService"),
                hasInternetConnection = isNetworkConnected(),
                batteryPercentage = getBatteryLevel(),
                activeNotifications = getActiveNotifications(),
                clipboardText = getLastClipboardEntry()
            )
            ArisResult.Success(deviceContext)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.PerceptionError("OBSERVATION_FAILED", "Failed to sense device environment", e))
        }
    }
}
