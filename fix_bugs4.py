import re
import os

filepath = "./app/src/main/java/com/blurr/voice/ConversationalAgentService.kt"

with open(filepath, "r") as f:
    content = f.read()

# IMP-10: Service notification update
create_notif_def = """    private fun createNotification(): Notification {"""
update_notif_def = """    private fun updateNotificationState(state: String) {
        val text = when(state) {
            "listening" -> "🎙️ Listening..."
            "thinking" -> "🤔 Thinking..."
            "speaking" -> "💬 Speaking..."
            "task" -> "⚙️ Executing task..."
            else -> "🔄 Active"
        }
        val stopIntent = android.content.Intent(this, ConversationalAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notif = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blurr Voice")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
        val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun createNotification(): Notification {"""

content = content.replace(create_notif_def, update_notif_def)

# Replace "Listening for your commands..."
content = content.replace('.setContentText("Listening for your commands...")', '.setContentText("🎙️ Listening...")')
content = content.replace('.setSmallIcon(R.drawable.ic_launcher_foreground)', '.setSmallIcon(R.mipmap.ic_launcher)')


# Update notifications during usage
content = content.replace("pandaStateManager.setState(PandaState.LISTENING)", "pandaStateManager.setState(PandaState.LISTENING)\n        updateNotificationState(\"listening\")")
content = content.replace("pandaStateManager.setState(PandaState.PROCESSING)", "pandaStateManager.setState(PandaState.PROCESSING)\n            updateNotificationState(\"thinking\")")
content = content.replace("pandaStateManager.setState(PandaState.SPEAKING)", "pandaStateManager.setState(PandaState.SPEAKING)\n                updateNotificationState(\"speaking\")")


with open(filepath, "w") as f:
    f.write(content)
print("File updated!")
