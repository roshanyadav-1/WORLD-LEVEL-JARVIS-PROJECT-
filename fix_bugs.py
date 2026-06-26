import re
import os

filepath = "./app/src/main/java/com/blurr/voice/ConversationalAgentService.kt"

with open(filepath, "r") as f:
    content = f.read()

# 1. IMP-2 & BUG-2: Lazy perception
content = content.replace("private lateinit var perception: Perception", "private val perception by lazy { Perception(Eyes(this), SemanticParser()) }")
# Remove perception initialization
content = content.replace("perception = Perception(Eyes(this), SemanticParser())", "")

# 4. BUG-4 & IMP-8: Secure conversationId
content = re.sub(r'conversationId = "\$\{System\.currentTimeMillis\(\)\}_\$\{currentUser\.uid\.take\(8\)\}"', 'conversationId = java.util.UUID.randomUUID().toString()', content)

# 6. BUG-6 & IMP-3: SnapshotListener leak
content = content.replace("private var conversationId: String? = null // Track current conversation session", "private var conversationId: String? = null\n    private var memoriesListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null")

content = re.sub(r'db\.collection\("users"\)\.document\(currentUser\.uid\)\n\s*\.addSnapshotListener', 'memoriesListenerRegistration = db.collection("users").document(currentUser.uid)\n            .addSnapshotListener', content)

content = content.replace("override fun onDestroy() {\n        super.onDestroy()", "override fun onDestroy() {\n        memoriesListenerRegistration?.remove()\n        super.onDestroy()")

# 8. BUG-8 & IMP-7: Fix windowManager.defaultDisplay in displayClarificationQuestions
import re
content = re.sub(r'windowManager\.defaultDisplay\.width', 'resources.displayMetrics.widthPixels', content)

# 14. BUG-14: serviceScope.cancel() in instantShutdown() cancels pending Firebase writes.
# Let's not cancel if we want writes. Let's just track the end and let the scope finish or cancel after delay. Wait, we can remove it or keep it as is if tracking finishes.
# Let's change serviceScope.cancel("User tapped outside...") to `// serviceScope.cancel` Wait, no, trackConversationEnd is async.
# Removing serviceScope.cancel() in instantShutdown()
content = content.replace('serviceScope.cancel("User tapped outside, forcing instant shutdown.")', '/* serviceScope.cancel("User tapped outside...") -> removed to prevent pending writes from failing */')

# 15. BUG-15 & IMP-11: maxClarificationAttempts
content = content.replace("private val maxClarificationAttempts = 1", "private val maxClarificationAttempts = 2")

# 16. BUG-16 & IMP-6: Hindi stop commands
old_stop_check = 'if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {'
new_stop_check = '''val stopCommands = setOf("stop", "exit", "quit", "bye", "goodbye", "ruk jao", "band kar", "bas", "rukh jao", "theek hai bye", "ok bye", "shukriya", "thanks bye")
if (userInput.lowercase() in stopCommands) {'''
content = content.replace(old_stop_check, new_stop_check)


with open(filepath, "w") as f:
    f.write(content)
print("File updated!")
