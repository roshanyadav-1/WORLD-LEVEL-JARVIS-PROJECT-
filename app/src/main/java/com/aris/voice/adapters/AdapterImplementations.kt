package com.aris.voice.adapters

import android.content.Context
import com.aris.voice.core.ArisResult
import com.aris.voice.core.ArisError
import com.aris.voice.domain.LlmProvider
import com.aris.voice.domain.LlmRequest
import com.aris.voice.domain.LlmResponse
import com.aris.voice.domain.LlmFinishReason
import com.aris.voice.llm.ILlmProvider
import com.aris.voice.utilities.TTSManager
import com.aris.voice.utilities.STTManager
import com.aris.voice.utilities.OfflineCommandProcessor
import com.aris.voice.triggers.TriggerManager

class VoiceAdapterImpl(private val context: Context) : IVoiceAdapter {
    override suspend fun speak(text: String): ArisResult<Unit> {
        TTSManager.getInstance(context).speakToUser(text)
        return ArisResult.Success(Unit)
    }

    override suspend fun listen(): ArisResult<String> {
        // Mock implementation, as STT is callback based in legacy
        return ArisResult.Success("User said something")
    }

    override fun stopSpeaking() {
        TTSManager.getInstance(context).stop()
    }
}

class ActionAdapterImpl(
    private val context: Context,
    private val finger: com.aris.voice.api.Finger
) : IActionAdapter {
    private val processor = OfflineCommandProcessor(context)
    
    override suspend fun executeAction(action: String, params: Map<String, Any>): ArisResult<Any> {
        return try {
            when (action.uppercase()) {
                "CLICK", "TAP" -> {
                    val x = params["x"]?.toString()?.toIntOrNull() ?: 0
                    val y = params["y"]?.toString()?.toIntOrNull() ?: 0
                    finger.tap(x, y)
                    ArisResult.Success("Clicked coordinates ($x, $y) successfully")
                }
                "LONG_PRESS" -> {
                    val x = params["x"]?.toString()?.toIntOrNull() ?: 0
                    val y = params["y"]?.toString()?.toIntOrNull() ?: 0
                    finger.longPress(x, y)
                    ArisResult.Success("Long pressed coordinates ($x, $y) successfully")
                }
                "SWIPE" -> {
                    val startX = params["startX"]?.toString()?.toIntOrNull() ?: 0
                    val startY = params["startY"]?.toString()?.toIntOrNull() ?: 0
                    val endX = params["endX"]?.toString()?.toIntOrNull() ?: 0
                    val endY = params["endY"]?.toString()?.toIntOrNull() ?: 0
                    finger.swipe(startX, startY, endX, endY, 500)
                    ArisResult.Success("Swiped from ($startX, $startY) to ($endX, $endY) successfully")
                }
                "SCROLL" -> {
                    val direction = params["direction"]?.toString()?.uppercase() ?: "DOWN"
                    val pixels = params["pixels"]?.toString()?.toIntOrNull() ?: 800
                    if (direction == "UP") finger.scrollUp(pixels) else finger.scrollDown(pixels)
                    ArisResult.Success("Scrolled $direction successfully")
                }
                "TYPE", "TEXT_INPUT" -> {
                    val text = params["text"]?.toString() ?: ""
                    finger.type(text)
                    ArisResult.Success("Typed text: '$text' successfully")
                }
                "ENTER" -> {
                    finger.enter()
                    ArisResult.Success("Pressed Enter key successfully")
                }
                "BACK" -> {
                    finger.back()
                    ArisResult.Success("Performed system Back action")
                }
                "HOME" -> {
                    finger.home()
                    ArisResult.Success("Performed system Home action")
                }
                "RECENTS", "RECENT_APPS" -> {
                    finger.switchApp()
                    ArisResult.Success("Performed system Recents action")
                }
                "NOTIFICATIONS" -> {
                    processor.processCommandOffline("notification panel")
                    ArisResult.Success("Opened notification panel")
                }
                "TOGGLE_SETTING" -> {
                    val settingKey = params["settingKey"]?.toString() ?: ""
                    val enable = params["enable"] as? Boolean ?: true
                    val command = if (enable) "turn on $settingKey" else "turn off $settingKey"
                    val result = processor.processCommandOffline(command)
                    if (result.isHandled) {
                        ArisResult.Success(result.feedbackSpeech ?: "Toggled setting $settingKey")
                    } else {
                        ArisResult.Failure(ArisError.ExecutionError("UNSUPPORTED_SETTING", "Setting '$settingKey' is not supported"))
                    }
                }
                "CONTROL_MEDIA" -> {
                    val command = params["command"]?.toString() ?: ""
                    val result = processor.processCommandOffline(command)
                    if (result.isHandled) {
                        ArisResult.Success(result.feedbackSpeech ?: "Controlled media with $command")
                    } else {
                        ArisResult.Failure(ArisError.ExecutionError("UNSUPPORTED_MEDIA_COMMAND", "Media command '$command' is not supported"))
                    }
                }
                "LAUNCH_APP" -> {
                    val packageName = params["packageName"]?.toString() ?: ""
                    var success = finger.openApp(packageName)
                    var finalLabel = packageName
                    if (!success && !packageName.contains(".")) {
                        val resolver = com.aris.voice.utilities.AppLauncherResolver(context)
                        when (val res = resolver.resolveApp(packageName)) {
                            is com.aris.voice.utilities.AppResolutionResult.Success -> {
                                success = finger.openApp(res.packageName)
                                finalLabel = res.appLabel
                            }
                            else -> {}
                        }
                    }
                    if (success) {
                        ArisResult.Success("Sure, opening $finalLabel.")
                    } else {
                        ArisResult.Failure(ArisError.ExecutionError("APP_NOT_FOUND", "Failed to open app $packageName"))
                    }
                }
                "PACKAGE_MANAGER" -> {
                    val target = params["target"]?.toString() ?: ""
                    val resolver = com.aris.voice.utilities.AppLauncherResolver(context)
                    when (val res = resolver.resolveApp(target)) {
                        is com.aris.voice.utilities.AppResolutionResult.Success -> {
                            val success = finger.openApp(res.packageName)
                            if (success) {
                                ArisResult.Success("Sure, opening ${res.appLabel}.")
                            } else {
                                ArisResult.Failure(ArisError.ExecutionError("APP_NOT_FOUND", "Failed to open app ${res.appLabel} (${res.packageName})"))
                            }
                        }
                        is com.aris.voice.utilities.AppResolutionResult.Ambiguous -> {
                            val apps = res.candidates.joinToString(" or ") { it.label }
                            ArisResult.Failure(ArisError.ExecutionError("AMBIGUOUS_APP", "I found multiple apps matching '$target': $apps. Which one would you like to open?"))
                        }
                        is com.aris.voice.utilities.AppResolutionResult.NotFound -> {
                            ArisResult.Failure(ArisError.ExecutionError("APP_NOT_FOUND", "I couldn't find an app named '$target' installed on this device."))
                        }
                    }
                }
                else -> {
                    // Fallback to offline command processor
                    val result = processor.processCommandOffline(action)
                    if (result.isHandled) {
                        ArisResult.Success(result.feedbackSpeech ?: "Action handled successfully")
                    } else {
                        ArisResult.Failure(ArisError.ExecutionError("UNSUPPORTED_ACTION", "Action '$action' is not currently supported by the device runtime"))
                    }
                }
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("ACTION_FAILED", "Failed executing action $action", e))
        }
    }
}

class TriggerAdapterImpl(private val context: Context) : ITriggerAdapter {
    private val triggerManager = TriggerManager(context)
    
    override suspend fun registerTrigger(triggerId: String, condition: String): ArisResult<Unit> {
        // Wrap legacy functionality
        return ArisResult.Success(Unit)
    }

    override suspend fun unregisterTrigger(triggerId: String): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }
}

class OverlayAdapterImpl(private val context: Context) : IOverlayAdapter {
    override suspend fun showOverlay(content: String): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }

    override suspend fun hideOverlay(): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }
}

class MemoryStorageAdapterImpl(private val context: Context) : IMemoryStorageAdapter {
    private val prefs = context.getSharedPreferences("aris_memory", Context.MODE_PRIVATE)

    override suspend fun save(key: String, value: String): ArisResult<Unit> {
        prefs.edit().putString(key, value).apply()
        return ArisResult.Success(Unit)
    }

    override suspend fun load(key: String): ArisResult<String?> {
        return ArisResult.Success(prefs.getString(key, null))
    }

    override suspend fun delete(key: String): ArisResult<Unit> {
        prefs.edit().remove(key).apply()
        return ArisResult.Success(Unit)
    }
}

class LegacyGeminiProviderAdapter(private val context: Context) : ILlmProvider {
    override val providerType: LlmProvider = LlmProvider.GEMINI
    override val isAvailableOffline: Boolean = false
    override val supportsVision: Boolean = true

    override suspend fun generateResponse(request: LlmRequest): ArisResult<LlmResponse> {
        return try {
            val chatList = listOf("user" to listOf<Any>(request.prompt))
            val responseText = com.aris.voice.api.GeminiApi.generateContent(chat = chatList, context = context)
            if (responseText != null) {
                ArisResult.Success(
                    LlmResponse(
                        provider = providerType,
                        model = "gemini-2.5-flash",
                        content = responseText,
                        finishReason = LlmFinishReason.STOP,
                        latencyMs = 1000L,
                        promptTokens = 0,
                        completionTokens = 0,
                        totalTokens = 0,
                        estimatedCost = 0.0,
                        confidence = 0.9f
                    )
                )
            } else {
                ArisResult.Failure(ArisError.BrainError("API_ERROR", "Null response from Gemini"))
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.BrainError("API_EXCEPTION", e.message ?: "Unknown error"))
        }
    }
}

class AccessibilityAdapterImpl(private val context: Context) : IAccessibilityAdapter {
    override suspend fun getScreenElements(): ArisResult<List<String>> {
        return ArisResult.Success(emptyList())
    }

    override suspend fun clickElement(elementId: String): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }

    override suspend fun scroll(direction: String): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }

    override suspend fun goBack(): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }

    override suspend fun goHome(): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }
}
