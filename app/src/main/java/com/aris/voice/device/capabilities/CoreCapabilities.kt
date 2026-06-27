package com.aris.voice.device.capabilities

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.aris.voice.device.DeviceCapability
import com.aris.voice.utilities.CommandResult
import com.aris.voice.utilities.AppLauncherResolver
import com.aris.voice.utilities.AppResolutionResult
import com.aris.voice.ScreenInteractionService

class MathCapability : DeviceCapability {
    override val name = "MathCalculation"
    override val patterns = listOf(
        Regex("(?i)(\\d+)\\s*(plus|minus|multiplied|divided|into|jama|\\+|\\-|\\*|/)\\s*(by)?\\s*(\\d+)")
    )
    
    override fun execute(context: Context, command: String, match: MatchResult): CommandResult {
        val groups = match.groupValues
        val num1 = groups[1].toLongOrNull() ?: return CommandResult(false)
        val num2 = groups[4].toLongOrNull() ?: return CommandResult(false)
        val op = groups[2].lowercase()
        
        val operator = when (op) {
            "plus", "jama", "+" -> "+"
            "minus", "-" -> "-"
            "multiplied", "into", "*" -> "*"
            "divided", "/" -> "/"
            else -> return CommandResult(false)
        }
        
        val res = when (operator) {
            "+" -> num1 + num2
            "-" -> num1 - num2
            "*" -> num1 * num2
            "/" -> if (num2 != 0L) (num1 / num2) else null
            else -> null
        }
        
        return if (res != null) {
            CommandResult(true, "The answer is $res")
        } else if (operator == "/" && num2 == 0L) {
            CommandResult(true, "Division by zero is undefined.")
        } else {
            CommandResult(false)
        }
    }
}

class AppLaunchCapability : DeviceCapability {
    override val name = "AppLaunch"
    override val patterns = listOf(
        Regex("(?i)^(open|launch|start|kholo|chalu karo)\\s+(.+)$")
    )
    
    override fun execute(context: Context, command: String, match: MatchResult): CommandResult {
        val appName = match.groupValues[2].trim()
        val resolver = AppLauncherResolver(context)
        return when (val res = resolver.resolveApp(appName)) {
            is AppResolutionResult.Success -> {
                val intent = context.packageManager.getLaunchIntentForPackage(res.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val launchContext = ScreenInteractionService.instance ?: context
                    launchContext.startActivity(intent)
                    CommandResult(true, "Opening ${res.appLabel}")
                } else {
                    CommandResult(false)
                }
            }
            is AppResolutionResult.Ambiguous -> {
                val apps = res.candidates.joinToString(" or ") { it.label }
                CommandResult(true, "I found multiple apps matching '$appName': $apps. Which one would you like to open?")
            }
            is AppResolutionResult.NotFound -> {
                CommandResult(false)
            }
        }
    }
}

class CommunicationCapability : DeviceCapability {
    override val name = "Communication"
    override val patterns = listOf(
        Regex("(?i)^(call|dial|phone)\\s*(.*)"),
        Regex("(?i)^(message|sms|text)\\s*(.*)")
    )
    
    override fun execute(context: Context, command: String, match: MatchResult): CommandResult {
        val action = match.groupValues[1].lowercase()
        val target = match.groupValues[2].trim()
        
        if (action in listOf("call", "dial", "phone")) {
            val numberRegex = Regex("\\d{8,15}")
            val numMatch = numberRegex.find(target)
            return if (numMatch != null) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${numMatch.value}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                CommandResult(true, "Dialing ${numMatch.value}")
            } else {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                CommandResult(true, "Opening your phone dialer")
            }
        } else {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return CommandResult(true, "Opening message client")
        }
    }
}
