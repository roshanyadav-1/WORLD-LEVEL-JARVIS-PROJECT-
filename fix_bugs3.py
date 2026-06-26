import re
import os

filepath = "./app/src/main/java/com/blurr/voice/ConversationalAgentService.kt"

with open(filepath, "r") as f:
    content = f.read()

# BUG-12: Delay in graceful shutdown
old_graceful = """                speechCoordinator.speakText(exitMessage)
                delay(2000) // Give TTS time to finish"""
new_graceful = """                speechCoordinator.speakText(exitMessage)
                while(speechCoordinator.isSpeaking) {
                    delay(100)
                }
                delay(500) // Small buffer time"""
content = content.replace(old_graceful, new_graceful)

with open(filepath, "w") as f:
    f.write(content)
print("File updated!")
