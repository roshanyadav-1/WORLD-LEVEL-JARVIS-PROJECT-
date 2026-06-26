import re
import os

filepath = "./app/src/main/java/com/blurr/voice/ConversationalAgentService.kt"

with open(filepath, "r") as f:
    content = f.read()

# BUG-9: Thread-safe clarification list
content = content.replace("private val clarificationQuestionViews = mutableListOf<View>()", "private val clarificationQuestionViews = java.util.concurrent.CopyOnWriteArrayList<View>()")

# BUG-12: GRACEFUL SHUTDOWN DELAY - if TTS is 10 seconds long, service stops too early.
# Find gracefulShutdown
old_graceful = """                delay(2000) // Give TTS a moment if finishing
            }
        }
        removeClarificationQuestions()"""
new_graceful = """                // Wait for speech to finish before shutting down
                while(speechCoordinator.isSpeaking) {
                    kotlinx.coroutines.delay(500)
                }
            }
        }
        removeClarificationQuestions()"""
content = content.replace(old_graceful, new_graceful)

# Wait we have to ensure we find gracefulShutdown properly
graceful_old2 = """                delay(2000) // Give TTS a moment if finishing
            }
        }"""
graceful_new2 = """                // Wait for speech to finish before shutting down
                while(speechCoordinator.isSpeaking) {
                    delay(500)
                }
                delay(1000)
            }
        }"""
if graceful_old2 in content:
    content = content.replace(graceful_old2, graceful_new2)


# BUG-13: hasHeardFirstUtterance flag
# It's called in speakAndThenListen and processUserInput. I will use the flag to ensure it's not called twice in the same turn.
# Or I can just check shouldUpdateScreenContext in IMP-13
# Let's add lastScreenContextUpdate
content = content.replace('private var maxSttErrorAttempts = 3', 'private var maxSttErrorAttempts = 3\n    private var lastScreenContextUpdate = 0L')

update_screen_old = """    private suspend fun updateSystemPromptWithScreenContext() {
        try {"""
update_screen_new = """    private suspend fun updateSystemPromptWithScreenContext() {
        // IMP-13: Context-aware system prompt updates
        val timeSinceLastUpdate = System.currentTimeMillis() - lastScreenContextUpdate
        if (timeSinceLastUpdate < 30_000) return
        lastScreenContextUpdate = System.currentTimeMillis()
        try {"""
content = content.replace(update_screen_old, update_screen_new)

# BUG-10 & IMP-14: ServicePermissionManager check moved to startup
# We need to find onStartCommand and add it at the top
onstart_old = """    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action"""
onstart_new = """    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!servicePermissionManager.isAccessibilityServiceEnabled()) {
            ttsManager.speakText("Please enable accessibility service first")
            stopSelf()
            return START_NOT_STICKY
        }
        val action = intent?.action"""
content = content.replace(onstart_old, onstart_new)


# BUG-11: transcriptionView managed in two places
# Actually we can just use VisualFeedbackManager for ALL transcription view things instead of local transcriptionView.
# Let's replace local transciprtionView references if possible, or just leave it since VisualFeedbackManager also manages one.
content = re.sub(r'transcriptionView\?\.text = (.*?)\n', r'visualFeedbackManager.updateTranscription(\1)\n', content)


with open(filepath, "w") as f:
    f.write(content)
print("File updated!")
