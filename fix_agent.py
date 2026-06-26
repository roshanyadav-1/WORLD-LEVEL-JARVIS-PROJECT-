import re
import os

filepath = "./app/src/main/java/com/blurr/voice/v2/AgentService.kt"

with open(filepath, "r") as f:
    content = f.read()

# IMP-6: Pause/Resume Task
content = content.replace(
    'private val _currentTaskStartTime = java.util.concurrent.atomic.AtomicLong(0L)',
    '''private val _currentTaskStartTime = java.util.concurrent.atomic.AtomicLong(0L)
        private val _isPaused = java.util.concurrent.atomic.AtomicBoolean(false)

        var isPaused: Boolean
            get() = _isPaused.get()
            set(v) = _isPaused.set(v)'''
)

with open(filepath, "w") as f:
    f.write(content)
print("Updated AgentService.kt")


filepath = "./app/src/main/java/com/blurr/voice/v2/Agent.kt"

with open(filepath, "r") as f:
    content = f.read()

# IMP-6: Pause in Agent loop
content = content.replace(
    'while (!state.stopped && state.nSteps <= maxSteps) {',
    '''while (!state.stopped && state.nSteps <= maxSteps) {
            while (AgentService.isPaused) {
                kotlinx.coroutines.delay(500)
            }'''
)

# IMP-4: Task progress reporting via notification
update_progress = """                // Update task progress notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val progress = (state.nSteps * 100) / maxSteps
                val notification = androidx.core.app.NotificationCompat.Builder(context, "AgentServiceChannelV2")
                    .setContentTitle("Executing: ${AgentService.currentTask?.take(30)}")
                    .setContentText("Step ${state.nSteps}/$maxSteps: ${agentOutput.nextGoal?.take(40)}")
                    .setProgress(100, progress, false)
                    .setSmallIcon(com.blurr.voice.R.drawable.ic_launcher_foreground)
                    .setOngoing(true)
                    .build()
                notificationManager.notify(14, notification)"""

content = content.replace(
    'Log.d(TAG,"💪 Executing actions...")',
    f'Log.d(TAG,"💪 Executing actions...")\n{update_progress}'
)

with open(filepath, "w") as f:
    f.write(content)
print("Updated Agent.kt")
