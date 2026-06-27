package com.aris.voice.device.capabilities

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.AlarmClock
import android.view.KeyEvent
import com.aris.voice.device.DeviceCapability
import com.aris.voice.utilities.CommandResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeAndAlarmCapability : DeviceCapability {
    override val name = "TimeAndAlarm"
    override val patterns = listOf(
        Regex("(?i).*(alarm|wake me up|baje).*"),
        Regex("(?i).*(timer).*"),
        Regex("(?i).*(time|date|samay|tarikh).*")
    )
    
    override fun execute(context: Context, command: String, match: MatchResult): CommandResult {
        val lower = command.lowercase()
        
        if (lower.contains("alarm") || lower.contains("wake me up") || lower.contains("baje")) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return CommandResult(true, "Opening your device alarm database.")
        }
        
        if (lower.contains("timer")) {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return CommandResult(true, "Opening clock timers.")
        }
        
        if (lower.contains("time") || lower.contains("samay")) {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return CommandResult(true, "The time is $time")
        }
        
        if (lower.contains("date") || lower.contains("tarikh")) {
            val date = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            return CommandResult(true, "Today's date is $date")
        }
        
        return CommandResult(false)
    }
}

class MediaControlCapability : DeviceCapability {
    override val name = "MediaControl"
    override val patterns = listOf(
        Regex("(?i).*(volume|sound|awaaz|unmute|shant|tej|badhao|kam).*"),
        Regex("(?i).*(play|pause|next song|previous song|gaana).*")
    )
    
    override fun execute(context: Context, command: String, match: MatchResult): CommandResult {
        val lower = command.lowercase()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Volume
        if (lower.contains("volume") || lower.contains("sound") || lower.contains("awaaz")) {
            if (lower.contains("up") || lower.contains("increase") || lower.contains("badhao") || lower.contains("tej")) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return CommandResult(true, "Volume increased")
            }
            if (lower.contains("down") || lower.contains("decrease") || lower.contains("kam")) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return CommandResult(true, "Volume decreased")
            }
            if (lower.contains("mute") || lower.contains("shant")) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                return CommandResult(true, "Audio muted")
            }
            if (lower.contains("unmute")) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                return CommandResult(true, "Audio unmuted")
            }
        }
        
        // Media Keys
        if (lower.contains("play") || lower.contains("pause") || lower.contains("gaana")) {
            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            audioManager.dispatchMediaKeyEvent(event)
            return CommandResult(true, "Toggled media playback")
        }
        if (lower.contains("next")) {
            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
            audioManager.dispatchMediaKeyEvent(event)
            return CommandResult(true, "Skipped to next track")
        }
        if (lower.contains("previous")) {
            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            audioManager.dispatchMediaKeyEvent(event)
            return CommandResult(true, "Went to previous track")
        }
        
        return CommandResult(false)
    }
}
