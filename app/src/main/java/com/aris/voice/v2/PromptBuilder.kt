package com.aris.voice.v2

import android.content.Context
import android.util.Log
import com.aris.voice.v2.actions.Action
import com.aris.voice.v2.fs.FileSystem
import com.aris.voice.v2.llm.GeminiMessage
import com.aris.voice.v2.llm.MessageRole
import com.aris.voice.v2.llm.TextPart
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.aris.voice.intents.IntentRegistry

private const val DEFAULT_PROMPT_TEMPLATE = "prompts/system_prompt.md"

/**
 * Loads and prepares the system prompt from the default template
 * stored in the app's assets.
 *
 * @param context The Android application context, needed to access the AssetManager.
 */
class SystemPromptLoader(private val context: Context) {

    /**
     * Constructs the final system message.
     *
     * @param settings The agent's configuration.
     * @return A GeminiMessage containing the fully formatted system prompt.
     */
    fun getSystemMessage(settings: AgentSettings): GeminiMessage {
        val actionsDescription = generateActionsDescription()
        val intentsCatalog = generateIntentsCatalog()

        val userProfileManager = com.aris.voice.utilities.UserProfileManager(context)
        val userName = userProfileManager.getName() ?: "User"
        val userEmail = userProfileManager.getEmail() ?: "Unknown Email"
        
        val dataMemoryManager = com.aris.voice.data.MemoryManager.getInstance(context)
        val profileSummary = kotlinx.coroutines.runBlocking { dataMemoryManager.getUserProfileSummary() }
        
        val userInfo = "Name: $userName\nEmail: $userEmail\n\n$profileSummary"

        var prompt = settings.overrideSystemMessage ?: loadDefaultTemplate()
            .replace("{max_actions}", settings.maxActionsPerStep.toString())
            .replace("{available_actions}", actionsDescription)
            .replace("{user_info}", userInfo)

        // Append intents catalog and a usage hint for the launch_intent action
        if (intentsCatalog.isNotBlank()) {
            prompt += "\n\n<intents_catalog>\n$intentsCatalog\n</intents_catalog>\n\n" +
                "Usage: To launch any of the above intents, add an action like {\"launch_intent\": {\"intent_name\": \"Dial\", \"parameters\": {\"phone_number\": \"+123456789\"}}}."
        }

        if (!settings.extendSystemMessage.isNullOrBlank()) {
            prompt += "\n${settings.extendSystemMessage}"
        }
        Log.d("SYSTEM_PROMPT_BUILDER", prompt)
        return GeminiMessage(role = MessageRole.MODEL, parts = listOf(TextPart(prompt)))
    }
    /**
     * NEW: This function generates a structured, LLM-friendly description
     * of all available actions using the single source of truth in Action.kt.
     */
    private fun generateActionsDescription(): String {
        return """
            The following actions are available to execute as part of your steps. Format each action in your JSON steps exactly as described:
            
            1. TapElement: Tap an element on screen.
               Format: {"TapElement": {"elementId": <int>}}
               
            2. Speak: Speak a message directly to the user.
               Format: {"Speak": {"message": "<string>"}}
               
            3. Ask: Ask the user a question and wait for voice input/response.
               Format: {"Ask": {"question": "<string>"}}
               
            4. LongPressElement: Long press on a screen element.
               Format: {"LongPressElement": {"elementId": <int>}}
               
            5. OpenApp: Open an app by name.
               Format: {"OpenApp": {"appName": "<string>"}}
               
            6. Back: Perform a back navigation action.
               Format: {"Back": {}}
               
            7. Home: Navigate to the home screen.
               Format: {"Home": {}}
               
            8. SwitchApp: Show the recent apps screen.
               Format: {"SwitchApp": {}}
               
            9. Wait: Pause action execution for a short duration (1-2 seconds) to let animations load.
               Format: {"Wait": {}}
               
            10. ScrollDown: Scroll down the screen by a specified pixel amount (e.g., 500).
                Format: {"ScrollDown": {"amount": <int>}}
                
            11. ScrollUp: Scroll up the screen by a specified pixel amount.
                Format: {"ScrollUp": {"amount": <int>}}
                
            12. SearchGoogle: Perform a Google search on the device.
                Format: {"SearchGoogle": {}}
                
            13. Done: Indicate the task is finished (either successfully or failed) and provide summary text.
                Format: {"Done": {"success": <boolean>, "text": "<string>", "filesToDisplay": null}}
                
            14. InputText: Type text into the currently focused element.
                Format: {"InputText": {"text": "<string>"}}
                
            15. AppendFile: Append content to a local file.
                Format: {"AppendFile": {"fileName": "<string>", "content": "<string>"}}
                
            16. ReadFile: Read content from a local file.
                Format: {"ReadFile": {"fileName": "<string>"}}
                
            17. WriteFile: Write content to a local file (creates or overwrites).
                Format: {"WriteFile": {"fileName": "<string>", "content": "<string>"}}
                
            18. TapElementInputTextPressEnter: Combination action to tap an element, type text, and press enter.
                Format: {"TapElementInputTextPressEnter": {"index": <int>, "text": "<string>"}}
                
            19. LaunchIntent: Launch a deep-link intent (e.g., to dial numbers). Refer to intents catalog.
                Format: {"LaunchIntent": {"intentName": "<string>", "parameters": {"param1": "value1"}}}
                
            20. SetAlarm: Set an alarm.
                Format: {"SetAlarm": {"hour": <int>, "minutes": <int>, "label": "<string>"}}
                
            21. SendWhatsApp: Send a WhatsApp message.
                Format: {"SendWhatsApp": {"phoneNumber": "<string>", "message": "<string>"}}
                
            22. TakeScreenshot: Take a screenshot of the current screen.
                Format: {"TakeScreenshot": {}}
                
            23. ControlMedia: Send a media control command ("play", "pause", "next", "previous").
                Format: {"ControlMedia": {"command": "<string>"}}
                
            24. ReadNotifications: Read recent notifications.
                Format: {"ReadNotifications": {}}
                
            25. SetReminder: Set a reminder.
                Format: {"SetReminder": {"title": "<string>", "hour": <int>, "minutes": <int>}}
                
            26. ControlBrightness: Set screen brightness level (0 to 100).
                Format: {"ControlBrightness": {"level": <int>}}
                
            27. ControlVolume: Set media volume level (0 to 100).
                Format: {"ControlVolume": {"level": <int>}}
        """.trimIndent()
    }

    // New: Describe all registered AppIntents for the model
    private fun generateIntentsCatalog(): String {
        val intents = IntentRegistry.listIntents(context)
        if (intents.isEmpty()) return ""
        return buildString {
            intents.forEach { intent ->
                append("<intent>\n")
                append("  <name>${intent.name}</name>\n")
                append("  <description>${intent.description()}</description>\n")
                val params = intent.parametersSpec()
                if (params.isNotEmpty()) {
                    append("  <parameters>\n")
                    params.forEach { p ->
                        append("    <param>\n")
                        append("      <name>${p.name}</name>\n")
                        append("      <type>${p.type}</type>\n")
                        append("      <required>${p.required}</required>\n")
                        append("      <description>${p.description}</description>\n")
                        append("    </param>\n")
                    }
                    append("  </parameters>\n")
                }
                append("</intent>\n\n")
            }
        }.trim()
    }

    private fun loadDefaultTemplate(): String {
        return try {
            context.assets.open(DEFAULT_PROMPT_TEMPLATE).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw RuntimeException("Failed to load default system prompt template: $DEFAULT_PROMPT_TEMPLATE", e)
        }
    }
}

/**
 * A builder responsible for constructing the detailed user message for each step of the agent's loop.
 * It aggregates all state information into a single, structured prompt.
 */
object UserMessageBuilder {

    /**
     * A data class to hold the numerous arguments required to build the user message.
     */
    data class Args(
        val task: String,
        val screenState: ScreenState,
        val fileSystem: FileSystem,
        val agentHistoryDescription: String?,
        val readStateDescription: String?,
        val stepInfo: AgentStepInfo?,
        val sensitiveDataDescription: String?,
        val availableFilePaths: List<String>?,
        val maxUiRepresentationLength: Int = 40000
    )

    /**
     * The main entry point to build the user message.
     *
     * @param args All the necessary data for constructing the prompt.
     * @return A GeminiMessage ready to be sent to the LLM.
     */
    fun build(args: Args): GeminiMessage {
        val messageContent = buildString {
            append("<agent_history>\n")
            append(args.agentHistoryDescription?.trim() ?: "No history yet.")
            append("\n</agent_history>\n\n")

            append("<agent_state>\n")
            append(buildAgentStateBlock(args))
            append("\n</agent_state>\n\n")

            append("<android_state>\n")
            append(buildAndroidStateBlock(args.screenState, args.maxUiRepresentationLength))
            append("\n</android_state>\n\n")

            if (!args.readStateDescription.isNullOrBlank()) {
                append("<read_state>\n")
                append(args.readStateDescription.trim())
                append("\n</read_state>\n\n")
            }
        }

        return GeminiMessage(text = messageContent.trim())
    }

    private fun buildAndroidStateBlock(screenState: ScreenState, maxUiRepresentationLength: Int): String {
        val originalUiString = screenState.uiRepresentation
        val truncationMessage: String
        val finalUiString: String

        if (originalUiString.length > maxUiRepresentationLength) {
            finalUiString = originalUiString.substring(0, maxUiRepresentationLength)
            truncationMessage = " (truncated to $maxUiRepresentationLength characters)"
        } else {
            finalUiString = originalUiString
            truncationMessage = ""
        }

        return buildString {
            appendLine("Current Activity: ${screenState.activityName}")
            appendLine("Visible elements on the current screen:$truncationMessage")
            append(finalUiString)
        }.trim()
    }

    private fun buildAgentStateBlock(args: Args): String {
        val todoContents = args.fileSystem.getTodoContents().let {
            it.ifBlank { "[Current todo.md is empty, fill it with your plan when applicable]" }
        }

        val stepInfoDescription = args.stepInfo?.let {
            val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            "Step ${it.stepNumber + 1} of ${it.maxSteps} max possible steps\nCurrent date and time: $timeStr"
        } ?: "Step information not available."

        return buildString {
            appendLine("<user_request>")
            appendLine(args.task)
            appendLine("</user_request>")

            appendLine("<file_system>")
            appendLine(args.fileSystem.describe())
            appendLine("</file_system>")

            appendLine("<todo_contents>")
            appendLine(todoContents)
            appendLine("</todo_contents>")

            if (!args.sensitiveDataDescription.isNullOrBlank()) {
                appendLine("<sensitive_data>")
                appendLine(args.sensitiveDataDescription)
                appendLine("</sensitive_data>")
            }

            appendLine("<step_info>")
            appendLine(stepInfoDescription)
            appendLine("</step_info>")

            if (args.stepInfo != null && args.stepInfo.stepNumber > 1) {
                appendLine("<important_reminder>")
                appendLine("After EVERY action, verify the screen changed. If not, change strategy.")
                appendLine("</important_reminder>")
            }

            if (!args.availableFilePaths.isNullOrEmpty()) {
                appendLine("<available_file_paths>")
                appendLine(args.availableFilePaths.joinToString("\n"))
                appendLine("</available_file_paths>")
            }
        }.trim()
    }
}