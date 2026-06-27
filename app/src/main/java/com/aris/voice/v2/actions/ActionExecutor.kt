package com.aris.voice.v2.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.view.KeyEvent
import com.aris.voice.triggers.ArisNotificationListenerService
import java.net.URLEncoder
import java.util.Calendar
import com.aris.voice.ScreenInteractionService
import com.aris.voice.api.Finger
import com.aris.voice.utilities.SpeechCoordinator
import com.aris.voice.utilities.UserInputManager
import com.aris.voice.overlay.OverlayManager
import com.aris.voice.v2.ActionResult
import com.aris.voice.v2.fs.FileSystem
import com.aris.voice.v2.perception.ScreenAnalysis
import com.aris.voice.intents.IntentRegistry
import com.aris.voice.overlay.OverlayDispatcher
import com.aris.voice.overlay.OverlayPriority
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.system.measureTimeMillis
import kotlin.text.removePrefix

/**
 * Executes a pre-validated, type-safe Action command.
 * The 'when' block is exhaustive, ensuring every action is handled.
 */
class ActionExecutor(private val finger: Finger) {

    // Add this function inside ActionExecutor.kt, outside the class, or as a private fun.
    private fun getExtraInfo(node: AccessibilityNodeInfo): String {
        val infoParts = mutableListOf<String>()
        if (node.isCheckable) infoParts.add("checkable")
        if (node.isChecked) infoParts.add("checked")
        if (node.isClickable) infoParts.add("clickable")
        if (node.isEnabled) infoParts.add("enabled")
        if (node.isFocusable) infoParts.add("focusable")
        if (node.isFocused) infoParts.add("focused")
        if (node.isScrollable) infoParts.add("scrollable")
        if (node.isLongClickable) infoParts.add("long clickable")
        if (node.isSelected) infoParts.add("selected")

        return if (infoParts.isNotEmpty()) {
            "This element is ${infoParts.joinToString(", ")}."
        } else {
            ""
        }
    }

    private fun findPackageNameFromAppName(appName: String, context: Context): String? {
        val resolver = com.aris.voice.utilities.AppLauncherResolver(context)
        when (val res = resolver.resolveApp(appName)) {
            is com.aris.voice.utilities.AppResolutionResult.Success -> {
                return res.packageName
            }
            is com.aris.voice.utilities.AppResolutionResult.Ambiguous -> {
                if (res.candidates.isNotEmpty()) {
                    return res.candidates.first().packageName
                }
            }
            else -> {}
        }

        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        // First, try for an exact match (case-insensitive)
        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.equals(appName, ignoreCase = true)) {
                return appInfo.packageName
            }
        }

        // If no exact match, try for a partial match (contains)
        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.contains(appName, ignoreCase = true)) {
                return appInfo.packageName
            }
        }

        return null // Not found
    }

    private fun getVisibleText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        // Prefer text, fall back to content description
        return (if (text.isNotBlank()) text else contentDesc).replace("\n", " ")
    }
    private fun getCenterFromNode(node: AccessibilityNodeInfo): Pair<Int, Int>? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            return null // Node is not on screen or has no bounds
        }
        return Pair(bounds.centerX(), bounds.centerY())
    }

    private suspend fun waitForScreenChangeAndSettle(
        service: ScreenInteractionService?,
        signatureBefore: String,
        maxWaitMs: Long = 1200L,
        pollIntervalMs: Long = 50L,
        requiredStableIntervals: Int = 2
    ): Boolean {
        if (service == null) return false
        val startTime = System.currentTimeMillis()
        var hasChanged = false
        var stableCount = 0
        var lastSignature = signatureBefore

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            val rawSig = service.getWindowHierarchySignature()
            // Ignore temporary "null_root" and "error_generating_signature" signatures because they represent transitioning UI state
            if (rawSig == "null_root" || rawSig == "error_generating_signature") {
                delay(pollIntervalMs)
                continue
            }

            if (!hasChanged) {
                if (rawSig != signatureBefore) {
                    hasChanged = true
                    lastSignature = rawSig
                    stableCount = 0
                }
            } else {
                if (rawSig == lastSignature) {
                    stableCount++
                    if (stableCount >= requiredStableIntervals) {
                        Log.d("ActionExecutor", "Dynamic Delay: Screen changed and settled after ${System.currentTimeMillis() - startTime}ms")
                        return true
                    }
                } else {
                    lastSignature = rawSig
                    stableCount = 0
                }
            }
            delay(pollIntervalMs)
        }
        return hasChanged
    }

    private fun findMatchingNodeInNewState(
        originalNode: AccessibilityNodeInfo,
        newState: ScreenAnalysis
    ): AccessibilityNodeInfo? {
        val originalText = getVisibleText(originalNode)
        val originalResId = originalNode.viewIdResourceName ?: ""

        // Try perfect match first (resource ID and text)
        if (originalResId.isNotEmpty() || originalText.isNotEmpty()) {
            for (node in newState.elementMap.values) {
                val nodeText = getVisibleText(node)
                val nodeResId = node.viewIdResourceName ?: ""

                if (originalResId.isNotEmpty() && originalResId == nodeResId) {
                    if (originalText.isNotEmpty() && originalText == nodeText) {
                        return node
                    }
                }
            }

            // High confidence match: text only if not empty
            if (originalText.isNotEmpty()) {
                for (node in newState.elementMap.values) {
                    val nodeText = getVisibleText(node)
                    if (originalText.equals(nodeText, ignoreCase = true)) {
                        return node
                    }
                }
            }

            // High confidence match: resource ID only if not empty
            if (originalResId.isNotEmpty()) {
                for (node in newState.elementMap.values) {
                    val nodeResId = node.viewIdResourceName ?: ""
                    if (originalResId == nodeResId) {
                        return node
                    }
                }
            }
        }
        return null
    }

    /**
     * Executes a single action and returns the result.
     * @return An ActionResult detailing the outcome of the action.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun execute(
        action: Action,
        screenAnalysis: ScreenAnalysis,
        context: Context,
        fileSystem: FileSystem,
        stepInitialScreenState: ScreenAnalysis? = null
    ): ActionResult {
        // This 'when' block now returns an ActionResult for every case.
        return when (action) {
            is Action.TapElement -> {
                var elementNode = screenAnalysis.elementMap[action.elementId]

                // SELF-HEALING ACTION RESOLVER FOR BATCH ACTIONS Across Screen Transitions
                if (elementNode == null && stepInitialScreenState != null && stepInitialScreenState != screenAnalysis) {
                    val originalNode = stepInitialScreenState.elementMap[action.elementId]
                    if (originalNode != null) {
                        val healedNode = findMatchingNodeInNewState(originalNode, screenAnalysis)
                        if (healedNode != null) {
                            Log.d("ActionExecutor", "Self-Healing: Resolved missing element ID ${action.elementId} from initial state to new element.")
                            elementNode = healedNode
                        }
                    }
                } else if (elementNode != null && stepInitialScreenState != null && stepInitialScreenState != screenAnalysis) {
                    // Even if index exists, the screen changed, so index might point to a DIFFERENT/WRONG element now.
                    // We check if the current element matches the original intended element.
                    val originalNode = stepInitialScreenState.elementMap[action.elementId]
                    if (originalNode != null) {
                        val originalText = getVisibleText(originalNode)
                        val currentText = getVisibleText(elementNode)
                        if (originalText != currentText) {
                            // The text changed, so the index points to a different button! We must heal it.
                            val healedNode = findMatchingNodeInNewState(originalNode, screenAnalysis)
                            if (healedNode != null) {
                                Log.d("ActionExecutor", "Self-Healing: Index ${action.elementId} points to a different element on new screen. Successfully re-routed to correct matching node.")
                                elementNode = healedNode
                            }
                        }
                    }
                }

                if (elementNode != null) {
                    val text = getVisibleText(elementNode)
                    val service = ScreenInteractionService.instance

                    val center = getCenterFromNode(elementNode)
                    if (center != null && com.aris.voice.services.FloatingArisButtonService.instance != null) {
                        try {
                            suspendCancellableCoroutine<Unit> { cont ->
                                com.aris.voice.services.FloatingArisButtonService.instance?.animateToPointAndTap(
                                    center.first.toFloat(),
                                    center.second.toFloat()
                                ) {
                                    if (cont.isActive) cont.resume(Unit)
                                } ?: run {
                                    if (cont.isActive) cont.resume(Unit)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ActionExecutor", "Failed to animate creature for tap", e)
                        }
                    }

                    var signatureBefore = ""
                    var screenChanged = false

                    // --- START: Time Measurement ---
                    val diffTime = measureTimeMillis {
                        // 1. GET SIGNATURE (The entire XML tree)
                        signatureBefore = service?.getWindowHierarchySignature() ?: ""

                        // 2. ATTEMPT 1: Polite Accessibility Action
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                        // 3. WAIT & VERIFY: Dynamically poll for screen change and settle
                        screenChanged = waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 800L, pollIntervalMs = 40L)
                    }

                    // --- LOG THE RESULT ---
                    Log.d("ActionExecutor", "Polite click took ${diffTime}ms. Screen changed: $screenChanged")

                    if (screenChanged) {
                        ActionResult(longTermMemory = "Clicked element '$text'. Screen updated successfully.")
                    } else {
                        // 4. ESCALATE: BRUTE FORCE TAP
                        // The XML is identical, so the app ignored the click.
                        val center = getCenterFromNode(elementNode)
                        if (center != null) {
                            finger.tap(center.first, center.second)
                            // Dynamic verification after physical tap!
                            val physicalChanged = waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 1200L, pollIntervalMs = 50L)
                            Log.d("ActionExecutor", "Physical tap executed. Screen changed dynamically: $physicalChanged")
                            ActionResult(longTermMemory = "Accessibility click failed (screen didn't change). Escalated to physical tap at ${center.first},${center.second} on '$text'.")
                        } else {
                            ActionResult(error = "Click sent to '$text' but screen did not change, and cannot find coordinates for physical retry.")
                        }
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.elementId} not found.")
                }
            }
//            is Action.TapElement -> {
//                val elementNode = screenAnalysis.elementMap[action.elementId]
//                if (elementNode != null) {
//                    val text = getVisibleText(elementNode)
//                    val service = ScreenInteractionService.instance
//
//                    // 1. GET SIGNATURE (The entire XML tree)
//                    val signatureBefore = service?.getWindowHierarchySignature() ?: ""
//
//                    // 2. ATTEMPT 1: Polite Accessibility Action
//                    elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//
//                    // 3. WAIT & VERIFY
//                    // We wait for the app to process the click and update the UI
//                    delay(600)
//
//                    val signatureAfter = service?.getWindowHierarchySignature() ?: ""
//
//                    // If the XML strings are different, the screen changed.
//                    val screenChanged = signatureBefore != signatureAfter
//
//                    if (screenChanged) {
//                        ActionResult(longTermMemory = "Clicked element '$text'. Screen updated successfully.")
//                    } else {
//                        // 4. ESCALATE: BRUTE FORCE TAP
//                        // The XML is identical, so the app ignored the click.
//                        val center = getCenterFromNode(elementNode)
//                        if (center != null) {
//                            finger.tap(center.first, center.second)
//                            delay(500) // Wait for the physical tap to register
//                            ActionResult(longTermMemory = "Accessibility click failed (screen didn't change). Escalated to physical tap at ${center.first},${center.second} on '$text'.")
//                        } else {
//                            ActionResult(error = "Click sent to '$text' but screen did not change, and cannot find coordinates for physical retry.")
//                        }
//                    }
//                } else {
//                    ActionResult(error = "Element with ID ${action.elementId} not found.")
//                }
//            }
//            is Action.TapElement -> {
//                // MODIFIED: 'elementNode' is now AccessibilityNodeInfo
//                val elementNode = screenAnalysis.elementMap[action.elementId]
//                if (elementNode != null) {
//                    // MODIFIED: Use new helpers
//                    val text = getVisibleText(elementNode)
//                    val resourceId = elementNode.viewIdResourceName ?: ""
//                    val extraInfo = getExtraInfo(elementNode)
//                    val className = (elementNode.className ?: "").removePrefix("android.")
//
//                    val center = getCenterFromNode(elementNode)
//                    if (center != null) {
//                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
////                        finger.tap(center.first, center.second)
//                        val si = ScreenInteractionService.instance
//                        si?.showDebugTap(center.first.toFloat(), center.second.toFloat())
//                        ActionResult(longTermMemory = "Tapped element text:$text <$resourceId> <$extraInfo> <$className>")
//                    } else {
//                        ActionResult(error = "Element with ID ${action.elementId} has no visible bounds.")
//                    }
//                } else {
//                    ActionResult(error = "Element with ID ${action.elementId} not found in the current screen state.")
//                }
//            }
            is Action.Speak -> {
                // The message is taken directly from the type-safe action class.
                val message = action.message
                runBlocking {
                    SpeechCoordinator.getInstance(context).speakToUser(message)
                }
                ActionResult(longTermMemory = "Spoke the message: \"${message.take(50)}...\"")
            }
            is Action.Ask -> {
                val question = action.question
                val userResponse = withContext(Dispatchers.IO) { // User input is blocking
                    val userInputManager = UserInputManager(context)
                    userInputManager.askQuestion(question) // This internally speaks and listens
                }

                val memory = "Asked user: '$question'. User responded: '$userResponse'."
                ActionResult(
                    longTermMemory = memory,
                    extractedContent = userResponse, // The user's answer is the result
                    includeExtractedContentOnlyOnce = true
                )
            }
             is Action.LongPressElement -> {
                // MODIFIED: 'elementNode' is now AccessibilityNodeInfo
                val elementNode = screenAnalysis.elementMap[action.elementId]
                if (elementNode != null) {
                    // MODIFIED: Use new helpers
                    val text = getVisibleText(elementNode)
                    val resourceId = elementNode.viewIdResourceName ?: ""
                    val extraInfo = getExtraInfo(elementNode)
                    val className = (elementNode.className ?: "").removePrefix("android.")

                    val center = getCenterFromNode(elementNode)
                    if (center != null) {
                        if (com.aris.voice.services.FloatingArisButtonService.instance != null) {
                            try {
                                suspendCancellableCoroutine<Unit> { cont ->
                                    com.aris.voice.services.FloatingArisButtonService.instance?.animateToPointAndTap(
                                        center.first.toFloat(),
                                        center.second.toFloat()
                                    ) {
                                        if (cont.isActive) cont.resume(Unit)
                                    } ?: run {
                                        if (cont.isActive) cont.resume(Unit)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ActionExecutor", "Failed to animate for long press", e)
                            }
                        }
//                        finger.longPress(center.first, center.second)
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        ActionResult(longTermMemory = "Long-pressed element text:$text <$resourceId> <$extraInfo> <$className>")
                    } else {
                        ActionResult(error = "Element with ID ${action.elementId} has no visible bounds.")
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.elementId} not found in the current screen state.")
                }
            }
            is Action.OpenApp -> {
                val packageName = findPackageNameFromAppName(action.appName, context)
                if (packageName != null) {
                    if (packageName in setOf("com.android.settings", "com.google.android.gms.wallet")) {
                        return ActionResult(error = "For security, I can't perform actions in this restricted app.")
                    }
                    val service = ScreenInteractionService.instance
                    val signatureBefore = service?.getWindowHierarchySignature() ?: ""
                    val success = finger.openApp(packageName)
                    if (success) {
                        waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 1500L, pollIntervalMs = 50L)
                        ActionResult(longTermMemory = "Opened app '${action.appName}'.")
                    } else {
                        ActionResult(error = "Failed to open app '${action.appName}' (package: $packageName). Maybe try using different name or use app drawer by scrolling up.")
                    }
                } else {
                    ActionResult(error = "App '${action.appName}' not found. Maybe try using different name or use app drawer by scrolling up.")
                }
            }
            Action.Back -> {
                val service = ScreenInteractionService.instance
                val signatureBefore = service?.getWindowHierarchySignature() ?: ""
                finger.back()
                waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 1200L, pollIntervalMs = 40L)
                ActionResult(longTermMemory = "Pressed the back button.")
            }
            Action.Home -> {
                val service = ScreenInteractionService.instance
                val signatureBefore = service?.getWindowHierarchySignature() ?: ""
                finger.home()
                waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 1200L, pollIntervalMs = 40L)
                ActionResult(longTermMemory = "Pressed the home button.")
            }
            Action.SwitchApp -> {
                finger.switchApp()
                ActionResult(longTermMemory = "Opened the app switcher.")
            }
            Action.Wait -> {
                // Use delay in a coroutine instead of Thread.sleep
                delay(2_000)
                ActionResult(longTermMemory = "Waited for 2 seconds.")
            }
            is Action.ScrollDown -> {
                val service = ScreenInteractionService.instance
                val signatureBefore = service?.getWindowHierarchySignature() ?: ""
                finger.scrollDown(action.amount)
                waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 1200L, pollIntervalMs = 50L)
                ActionResult(longTermMemory = "Scrolled down by ${action.amount} pixels.")
            }
            is Action.ScrollUp -> {
                val service = ScreenInteractionService.instance
                val signatureBefore = service?.getWindowHierarchySignature() ?: ""
                finger.scrollUp(action.amount)
                waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 1200L, pollIntervalMs = 50L)
                ActionResult(longTermMemory = "Scrolled up by ${action.amount} pixels.")
            }
            is Action.SearchGoogle -> {
                // This is a multi-step conceptual action. The executor should handle the concrete steps.
                finger.openApp("com.android.chrome") // More reliable to use package name
                // The next steps (typing, pressing enter) should be decided by the agent in the next turn.
                ActionResult(longTermMemory = "Opened Chrome to search Google.")
            }
            is Action.Done -> {
                // This action doesn't *do* anything. It's a signal to the main loop.
                // We just construct the final ActionResult.
                ActionResult(
                    isDone = true,
                    success = action.success,
                    longTermMemory = "Task finished: ${action.text}",
                    attachments = action.filesToDisplay
                )
            }
//            is Action.ExtractStructuredData -> {
//                // This is a placeholder for a complex action.
//                // A full implementation would require another LLM call with the screen content.
//                // For now, we return an error indicating it's not yet implemented.
//                ActionResult(error = "Action 'ExtractStructuredData' is not yet implemented.")
//            }
            is Action.InputText -> {
                finger.type(action.text)
                ActionResult(longTermMemory = "Input text ${action.text}.")
            }
//            is Action.ScrollToText -> {
//                // As requested, skipping implementation.
//                ActionResult(error = "Action 'ScrollToText' is not implemented.")
//            }
            is Action.AppendFile -> {
                val success = fileSystem.appendFile(action.fileName, action.content)
                if (success) {
                    ActionResult(longTermMemory = "Appended content to '${action.fileName}'.")
                } else {
                    ActionResult(error = "Failed to append to file '${action.fileName}'.")
                }
            }
            is Action.ReadFile -> {
                val content = fileSystem.readFile(action.fileName)
                if (content.startsWith("Error:")) {
                    ActionResult(error = content)
                } else {
                    ActionResult(
                        longTermMemory = "Read content from '${action.fileName}'.",
                        extractedContent = content,
                        includeExtractedContentOnlyOnce = true
                    )
                }
            }
            is Action.WriteFile -> {
                val success = fileSystem.writeFile(action.fileName, action.content)
                if (success) {
                    Log.d("ActionExecutor", "Wrote content to '${action.fileName} ${action.content}'.")
                        OverlayDispatcher.show(
                            action.content,
                            OverlayPriority.CAPTION
                        )
                    ActionResult(longTermMemory = "Wrote content to '${action.fileName}'.")
                } else {
                    ActionResult(error = "Failed to write to file '${action.fileName}'.")
                }
            }

//            is Action.ScrollToText -> TODO()
            is Action.TapElementInputTextPressEnter -> {
                var elementNode = screenAnalysis.elementMap[action.index]

                // SELF-HEALING FOR BATCH INPUT ACTIONS Across Screen Transitions
                if (elementNode == null && stepInitialScreenState != null && stepInitialScreenState != screenAnalysis) {
                    val originalNode = stepInitialScreenState.elementMap[action.index]
                    if (originalNode != null) {
                        val healedNode = findMatchingNodeInNewState(originalNode, screenAnalysis)
                        if (healedNode != null) {
                            Log.d("ActionExecutor", "Self-Healing Input: Resolved missing element ID ${action.index} from initial state to new element.")
                            elementNode = healedNode
                        }
                    }
                } else if (elementNode != null && stepInitialScreenState != null && stepInitialScreenState != screenAnalysis) {
                    val originalNode = stepInitialScreenState.elementMap[action.index]
                    if (originalNode != null) {
                        val originalText = getVisibleText(originalNode)
                        val currentText = getVisibleText(elementNode)
                        if (originalText != currentText) {
                            val healedNode = findMatchingNodeInNewState(originalNode, screenAnalysis)
                            if (healedNode != null) {
                                Log.d("ActionExecutor", "Self-Healing Input: Corrected element ID ${action.index} to point to correct matching node on new screen.")
                                elementNode = healedNode
                            }
                        }
                    }
                }

                if (elementNode != null) {
                    val text = getVisibleText(elementNode)
                    val resourceId = elementNode.viewIdResourceName ?: ""
                    val extraInfo = getExtraInfo(elementNode)
                    val className = (elementNode.className ?: "").removePrefix("android.")

                    val center = getCenterFromNode(elementNode)
                    if (center != null) {
                        if (com.aris.voice.services.FloatingArisButtonService.instance != null) {
                            try {
                                suspendCancellableCoroutine<Unit> { cont ->
                                    com.aris.voice.services.FloatingArisButtonService.instance?.animateToPointAndTap(
                                        center.first.toFloat(),
                                        center.second.toFloat()
                                    ) {
                                        if (cont.isActive) cont.resume(Unit)
                                    } ?: run {
                                        if (cont.isActive) cont.resume(Unit)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ActionExecutor", "Failed to animate for input click", e)
                            }
                        }
                        val service = ScreenInteractionService.instance
                        val signatureBefore = service?.getWindowHierarchySignature() ?: ""
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        // Wait dynamically for focus/keyboard to appear and settle
                        waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 500L, pollIntervalMs = 30L)
                        finger.type(action.text)
                        finger.enter()
                        // Wait dynamically for input to be processed and keyboard/screen response to settle
                        waitForScreenChangeAndSettle(service, signatureBefore, maxWaitMs = 1100L, pollIntervalMs = 50L)
                        ActionResult(longTermMemory = "Tapped and typed '${action.text}' on element: text:$text <$resourceId> <$extraInfo> <$className>.")
                    } else {
                        ActionResult(error = "Element with ID ${action.index} has no visible bounds.")
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.index} for input not found.")
                }
            }
            is Action.LaunchIntent -> {
                val name = action.intentName
                val params = action.parameters
                val appIntent = IntentRegistry.findByName(context, name)
                if (appIntent == null) {
                    ActionResult(error = "Intent '$name' not found. Check intents catalog for valid names.")
                } else {
                    val intent = appIntent.buildIntent(context, params)
                    if (intent == null) {
                        ActionResult(error = "Intent '$name' missing or invalid parameters: ${params}")
                    } else {
                        try {
                            val launchSuccess = finger.launchIntent(intent)
                            if (launchSuccess) {
                                ActionResult(longTermMemory = "Launched intent '$name' with params ${params}")
                            } else {
                                ActionResult(error = "Failed to launch intent '$name' with params ${params}")
                            }
                        } catch (t: Throwable) {
                            ActionResult(error = "Failed to launch intent '$name': ${t.message}")
                        }
                    }
                }
            }
            is Action.SetAlarm -> {
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, action.minutes)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label ?: "A.R.I.S Agent Alarm")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    }
                    val launched = finger.launchIntent(intent)
                    if (launched) {
                        ActionResult(longTermMemory = "Successfully set an alarm for ${String.format("%02d:%02d", action.hour, action.minutes)}.")
                    } else {
                        ActionResult(error = "Failed to set alarm.")
                    }
                } catch (t: Throwable) {
                    ActionResult(error = "Error setting alarm: ${t.message}")
                }
            }
            is Action.SendWhatsApp -> {
                try {
                    val messageEncoded = URLEncoder.encode(action.message, "UTF-8")
                    val cleanPhone = action.phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$messageEncoded")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    
                    val pm = context.packageManager
                    val whatsappAvailable = try {
                        pm.getPackageInfo("com.whatsapp", 0)
                        true
                    } catch (e: Exception) {
                        false
                    }
                    val whatsappBizAvailable = try {
                        pm.getPackageInfo("com.whatsapp.w4b", 0)
                        true
                    } catch (e: Exception) {
                        false
                    }
                    
                    if (whatsappAvailable) {
                        intent.setPackage("com.whatsapp")
                    } else if (whatsappBizAvailable) {
                        intent.setPackage("com.whatsapp.w4b")
                    }
                    
                    val launched = finger.launchIntent(intent)
                    if (launched) {
                        ActionResult(longTermMemory = "Opened WhatsApp with pre-filled message to ${action.phoneNumber}. " +
                            "The message is NOT sent yet. You must now tap the Send button on screen to complete sending.")
                    } else {
                        ActionResult(error = "Failed to open WhatsApp.")
                    }
                } catch (t: Throwable) {
                    ActionResult(error = "Error sending WhatsApp: ${t.message}")
                }
            }
            is Action.TakeScreenshot -> {
                try {
                    val service = ScreenInteractionService.instance
                    if (service == null) {
                        ActionResult(error = "Accessibility Service is not running to take screenshots.")
                    } else {
                        val bitmap = service.captureScreenshot()
                        if (bitmap != null) {
                            val screenshotFile = java.io.File(fileSystem.workspaceDir, "screenshot.png")
                            java.io.FileOutputStream(screenshotFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            ActionResult(longTermMemory = "Successfully captured screen context and saved screenshot to workspace as 'screenshot.png'.")
                        } else {
                            ActionResult(error = "Failed to capture screenshot bitmap.")
                        }
                    }
                } catch (t: Throwable) {
                    ActionResult(error = "Error during screenshot capture: ${t.message}")
                }
            }
            is Action.ControlMedia -> {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val keyCode = when (action.command.lowercase()) {
                        "play", "pause", "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
                        "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                        else -> null
                    }
                    if (keyCode != null) {
                        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                        audioManager.dispatchMediaKeyEvent(eventDown)
                        audioManager.dispatchMediaKeyEvent(eventUp)
                        ActionResult(longTermMemory = "Sent media command '${action.command}' successfully.")
                    } else {
                        ActionResult(error = "Invalid media command '${action.command}'. Supported: play, pause, next, previous, stop.")
                    }
                } catch (t: Throwable) {
                    ActionResult(error = "Error controlling media: ${t.message}")
                }
            }
            is Action.ReadNotifications -> {
                try {
                    val sbnList = ArisNotificationListenerService.instance?.activeNotifications
                    if (sbnList != null && sbnList.isNotEmpty()) {
                        val parsed = sbnList.joinToString("\n") { sbn ->
                            val extras = sbn.notification.extras
                            val title = extras.getString("android.title") ?: ""
                            val text = extras.getCharSequence("android.text")?.toString() ?: ""
                            "- [${sbn.packageName}] $title: $text"
                        }
                        ActionResult(
                            longTermMemory = "Read ${sbnList.size} active notifications.",
                            extractedContent = parsed,
                            includeExtractedContentOnlyOnce = true
                        )
                    } else {
                        val service = ScreenInteractionService.instance
                        val expanded = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false
                        if (expanded) {
                            ActionResult(longTermMemory = "No programmatically found notifications. Expanded the notifications status bar shade so you/the agent can view active notification views on screen.")
                        } else {
                            ActionResult(error = "Could not programmatically read notifications and failed to expand notifications shade.")
                        }
                    }
                } catch (t: Throwable) {
                    ActionResult(error = "Error reading notifications: ${t.message}")
                }
            }
            is Action.SetReminder -> {
                try {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, action.hour)
                        set(Calendar.MINUTE, action.minutes)
                    }
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, action.title)
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 3600000) // 1 hour duration
                    }
                    val launched = finger.launchIntent(intent)
                    if (launched) {
                        ActionResult(longTermMemory = "Started intent to set a reminder/event: '${action.title}' at ${String.format("%02d:%02d", action.hour, action.minutes)}.")
                    } else {
                        ActionResult(error = "Failed to launch calendar reminder intent.")
                    }
                } catch (t: Throwable) {
                    ActionResult(error = "Error creating reminder: ${t.message}")
                }
            }
            is Action.ControlBrightness -> {
                try {
                    if (Settings.System.canWrite(context)) {
                        val valueToWrite = (action.level.coerceIn(0, 100) * 255) / 100
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, valueToWrite)
                        ActionResult(longTermMemory = "Screen brightness level successfully adjusted to ${action.level}%.")
                    } else {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        finger.launchIntent(intent)
                        ActionResult(error = "Missing 'Write Settings' permission to change screen brightness level directly. Opened the system settings page so you can grant it, and then try again.")
                    }
                } catch (t: Throwable) {
                    ActionResult(error = "Error controlling screen brightness: ${t.message}")
                }
            }
            is Action.ControlVolume -> {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVol = (action.level.coerceIn(0, 100) * maxVol) / 100
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                    ActionResult(longTermMemory = "Music media volume level successfully set to ${action.level}% ($targetVol/$maxVol).")
                } catch (t: Throwable) {
                    ActionResult(error = "Error adjusting music/media volume: ${t.message}")
                }
            }
        }
    }
}
