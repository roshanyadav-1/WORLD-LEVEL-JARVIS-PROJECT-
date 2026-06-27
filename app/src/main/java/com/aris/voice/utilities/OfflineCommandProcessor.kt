package com.aris.voice.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class returns both execution status AND the speech feedback for TTS
data class CommandResult(
    val isHandled: Boolean,
    val feedbackSpeech: String? = null
)

class OfflineCommandProcessor(private val context: Context) {

    private val appCache = mutableMapOf<String, String>()

    init {
        loadAppCache()
    }

    private fun loadAppCache() {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in packages) {
                val appName = pm.getApplicationLabel(appInfo).toString().lowercase(Locale.getDefault())
                if (appName.isNotEmpty()) {
                    appCache[appName] = appInfo.packageName
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineCmdProcessor", "Failed reading app cache list", e)
        }
    }

    // 50+ apps aliases with high typo tolerance
    private val appAliases = mapOf(
        "whatsapp" to listOf("whatsapp", "whats app", "wassup", "whatapp", "wtsp", "wassap", "watsp", "watsap", "watsapp", "washap"),
        "instagram" to listOf("instagram", "insta", "instgram", "ig", "instragram", "instagrm", "instram", "ins"),
        "youtube" to listOf("youtube", "yt", "utub", "ytube", "play music"),
        "facebook" to listOf("facebook", "fb", "fbook", "face book"),
        "chrome" to listOf("chrome", "browser", "internet", "safari", "fire fox", "firefox", "opera"),
        "gmail" to listOf("gmail", "mail", "email", "g mail", "inbox"),
        "spotify" to listOf("spotify", "music", "gaana", "spoti", "wynk", "jiosaavn", "saavn", "music app"),
        "phonepe" to listOf("phonepe", "phone pe", "ppe", "pephone", "phonepay"),
        "paytm" to listOf("paytm", "patm", "pay tm", "payt"),
        "gpay" to listOf("gpay", "google pay", "googlepay", "gp"),
        "maps" to listOf("maps", "navigation", "google maps", "map", "rasta", "road"),
        "settings" to listOf("settings", "setting", "phone settings", "system settings", "control panel", "panel"),
        "camera" to listOf("camera", "photo", "video camera", "cam", "snap", "selfie"),
        "calendar" to listOf("calendar", "schedule", "agenda", "reminders", "meet", "matches", "diary"),
        "dialer" to listOf("dialer", "phone", "contacts", "call", "dial", "call list", "phonebook"),
        "calculator" to listOf("calculator", "calc", "hisab", "math", "maths"),
        "files" to listOf("files", "file manager", "manager", "documents", "storage", "downloads"),
        "telegram" to listOf("telegram", "tg", "tele", "telegrame"),
        "twitter" to listOf("twitter", "x", "twt", "twtr"),
        "playstore" to listOf("playstore", "play store", "app store", "market", "google play")
    )

    fun processCommandOffline(command: String): CommandResult {
        val lowerCommand = command.lowercase(Locale.getDefault())

        try {
            // New: Mathematics parsing check
            val mathResult = handleMath(lowerCommand)
            if (mathResult != null) return mathResult

            return when {
                // Payments apps deep links
                lowerCommand.contains("paytm") || lowerCommand.contains("patm") -> handleOpenAppByAlias("paytm", "Paytm")
                lowerCommand.contains("phonepe") || lowerCommand.contains("phone pe") -> handleOpenAppByAlias("phonepe", "PhonePe")
                lowerCommand.contains("gpay") || lowerCommand.contains("google pay") -> handleOpenAppByAlias("gpay", "Google Pay")

                // WhatsApp deep link formatting integrations
                lowerCommand.contains("whatsapp") || lowerCommand.contains("whats app") || lowerCommand.contains("whats-app") || lowerCommand.contains("wassup") -> handleWhatsAppDeepLink(lowerCommand)

                // Play Store integrations
                lowerCommand.contains("play store") || lowerCommand.contains("playstore") -> handlePlayStoreDeepLink(lowerCommand)

                // 📸 Screenshot (Hinglish/English commands)
                lowerCommand.contains("screenshot") || lowerCommand.contains("screen shot") || lowerCommand.contains("screen capture") || lowerCommand.contains("photo khicho") || lowerCommand.contains("screenshot lo") -> handleScreenshot()

                // 🔔 Open notification panel / settings
                lowerCommand.contains("notification") || lowerCommand.contains("shone") || lowerCommand.contains("pull down") || lowerCommand.contains("notification panel") || lowerCommand.contains("notification shade") -> handleNotifications()

                // 🗺️ Maps / Navigation deep links
                lowerCommand.contains("navigate") || lowerCommand.contains("location") || lowerCommand.contains("maps") || lowerCommand.contains("rasta") || lowerCommand.contains("map kholo") || lowerCommand.contains("connaught place") -> handleMaps(lowerCommand)

                // Contacts / Dialer shortcuts
                lowerCommand.contains("contact") || lowerCommand.contains("phonebook") || lowerCommand.contains("call list") || lowerCommand.contains("contact list") || lowerCommand.contains("number list") -> handleContacts()

                // 📅 Reminders & Calendar
                lowerCommand.contains("remind") || lowerCommand.contains("reminder") || lowerCommand.contains("calendar") || lowerCommand.contains("calender") || lowerCommand.contains("tarikh panel") -> handleCalendar()

                // General Launch Commands (includes alias resolutions)
                lowerCommand.contains("open ") || lowerCommand.contains("launch ") || lowerCommand.contains("kholo") || lowerCommand.contains("chalu karo") -> handleOpenApp(lowerCommand)
                
                // Calling & Messaging
                lowerCommand.contains("call ") || lowerCommand.contains("dial ") || lowerCommand.contains("phone ") -> handleCall(lowerCommand)
                lowerCommand.contains("message ") || lowerCommand.contains("sms ") -> handleMessage(lowerCommand)
                
                // Hardware control Hinglish / English settings
                lowerCommand.contains("torch") || lowerCommand.contains("flashlight") || lowerCommand.contains("wi-fi") || lowerCommand.contains("wifi") ||
                lowerCommand.contains("bluetooth") || lowerCommand.contains("flight mode") || lowerCommand.contains("airplane mode") || lowerCommand.contains("location") ||
                lowerCommand.contains("do not disturb") || lowerCommand.contains("dnd") || lowerCommand.contains("torch jalo") || lowerCommand.contains("bulb") || lowerCommand.contains("flash") || lowerCommand.contains("light jalo") || lowerCommand.contains("light band") -> handleSettings(lowerCommand)
                
                // Time, Alarms & Date check
                lowerCommand.contains("alarm") || lowerCommand.contains("wake me up") || lowerCommand.contains("baje") || lowerCommand.contains("alarm lagao") -> handleAlarm(lowerCommand)
                lowerCommand.contains("timer") -> handleTimer(lowerCommand)
                lowerCommand.contains("time") || lowerCommand.contains("date") || lowerCommand.contains("samay") || lowerCommand.contains("tarikh") || lowerCommand.contains("samay batao") || lowerCommand.contains("time batao") -> handleDateTime(lowerCommand)
                
                // Volume Adjustments
                lowerCommand.contains("volume") || lowerCommand.contains("sound") || lowerCommand.contains("awaaz") || lowerCommand.contains("unmute") || lowerCommand.contains("shant") || lowerCommand.contains("tej") || lowerCommand.contains("badhao") || lowerCommand.contains("kam") -> handleVolume(lowerCommand)

                // Media controls
                lowerCommand.contains("play") || lowerCommand.contains("pause") || lowerCommand.contains("next song") || lowerCommand.contains("previous song") || lowerCommand.contains("gaana play") || lowerCommand.contains("gaana") -> handleMedia(lowerCommand)
                
                // Hardware diagnostics Checks
                lowerCommand.contains("battery") || lowerCommand.contains("charging") -> handleBattery()
                lowerCommand.contains("camera") || lowerCommand.contains("photo") -> handleCamera()
                
                else -> CommandResult(false)
            }
        } catch (e: Exception) {
            Log.e("OfflineCmdProcessor", "Failed to process command: ${e.message}", e)
            return CommandResult(true, "I encountered an issue launching that command.")
        }
    }

    private fun handleMath(command: String): CommandResult? {
        val clean = command
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("into", "*")
            .replace("multiply", "*")
            .replace("divided by", "/")
            .replace("divided", "/")
            .replace("jama", "+")
            .replace("multiplied", "*")
            .trim()
        
        val regex = Regex("(\\d+)\\s*([+\\-*/])\\s*(\\d+)")
        val match = regex.find(clean) ?: return null
        
        val (num1, op, num2) = match.destructured
        val n1 = num1.toLong()
        val n2 = num2.toLong()
        
        val res = when (op) {
            "+" -> n1 + n2
            "-" -> n1 - n2
            "*" -> n1 * n2
            "/" -> if (n2 != 0L) (n1 / n2) else null
            else -> null
        }
        
        return if (res != null) {
            CommandResult(true, "The answer is $res")
        } else if (op == "/" && n2 == 0L) {
            CommandResult(true, "Division by zero is undefined.")
        } else {
            null
        }
    }

    private fun handleOpenAppByAlias(alias: String, cleanName: String): CommandResult {
        val resolver = com.aris.voice.utilities.AppLauncherResolver(context)
        return when (val res = resolver.resolveApp(alias)) {
            is com.aris.voice.utilities.AppResolutionResult.Success -> {
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(res.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val launchContext = com.aris.voice.ScreenInteractionService.instance ?: context
                    launchContext.startActivity(intent)
                    CommandResult(true, "Opening $cleanName")
                } else {
                    CommandResult(false)
                }
            }
            else -> CommandResult(false)
        }
    }

    private fun handleOpenApp(command: String): CommandResult {
        val cleanName = command
            .replace("open", "")
            .replace("launch", "")
            .replace("kholo", "")
            .replace("chalu karo", "")
            .trim()

        val resolver = com.aris.voice.utilities.AppLauncherResolver(context)
        return when (val res = resolver.resolveApp(cleanName)) {
            is com.aris.voice.utilities.AppResolutionResult.Success -> {
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(res.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val launchContext = com.aris.voice.ScreenInteractionService.instance ?: context
                    launchContext.startActivity(intent)
                    CommandResult(true, "Sure, opening ${res.appLabel}.")
                } else {
                    CommandResult(false)
                }
            }
            is com.aris.voice.utilities.AppResolutionResult.Ambiguous -> {
                val apps = res.candidates.joinToString(" or ") { it.label }
                CommandResult(true, "I found multiple apps matching '$cleanName': $apps. Which one would you like to open?")
            }
            is com.aris.voice.utilities.AppResolutionResult.NotFound -> {
                CommandResult(false) // Let it fallback to the brain/LLM path for complex commands
            }
        }
    }

    private fun handleCall(command: String): CommandResult {
        val numberRegex = Regex("\\d{8,15}")
        val match = numberRegex.find(command)
        
        return if (match != null) {
            val phoneNumber = match.value
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult(true, "Dialing $phoneNumber")
        } else {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult(true, "Opening your phone dialer")
        }
    }

    private fun handleMessage(command: String): CommandResult {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("smsto:")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return CommandResult(true, "Opening message client")
    }

    private fun handleScreenshot(): CommandResult {
        return try {
            val service = com.aris.voice.ScreenInteractionService.instance
            if (service != null) {
                service.takeScreenshot()
                CommandResult(true, "Screenshot le liya!")
            } else {
                // Fallback: power + volume down simulate karo
                val instructions = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                CommandResult(true, "Screenshot ke liye Power + Volume Down dabao.")
            }
        } catch (e: Exception) {
            CommandResult(true, "Screenshot lene ki koshish ki.")
        }
    }

    private fun handleNotifications(): CommandResult {
        return try {
            // Method 1: Status bar expand via reflection
            @SuppressLint("WrongConstant")
            val service = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expand = statusBarManager.getMethod("expandNotificationsPanel")
            expand.invoke(service)
            CommandResult(true, "Notification panel khola.")
        } catch (e: Exception) {
            // Method 2: ScreenInteractionService se swipe down
            val screenService = com.aris.voice.ScreenInteractionService.instance
            if (screenService != null) {
                screenService.swipe(500f, 0f, 500f, 800f, 300L)
                CommandResult(true, "Notification panel neeche khicha.")
            } else {
                CommandResult(false)
            }
        }
    }

    private fun handleContacts(): CommandResult {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "vnd.android.cursor.dir/contact"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult(true, "Opening your phonebook contacts.")
        } catch (e: Exception) {
            CommandResult(false)
        }
    }

    private fun handleCalendar(): CommandResult {
        val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult(true, "Opening your reminders schedule in calendar.")
        } catch (e: Exception) {
            CommandResult(false)
        }
    }

    private fun handleMaps(command: String): CommandResult {
        var query = command
            .replace("navigate to", "")
            .replace("location of", "")
            .replace("maps", "")
            .replace("rasta dikhao", "")
            .replace("le chalo", "")
            .trim()

        val uri = if (query.isNotEmpty()) {
            Uri.parse("google.navigation:q=" + Uri.encode(query))
        } else {
            Uri.parse("geo:0,0")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult(true, if (query.isNotEmpty()) "Navigating to $query in Google Maps." else "Opening Google Maps.")
        } catch (e: Exception) {
            val fallbackWeb = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackWeb)
            CommandResult(true, "Opening Google Maps in your web browser.")
        }
    }

    private fun handleSettings(command: String): CommandResult {
        val turnOn = command.contains("turn on") || command.contains("enable") || command.contains("jalo") || command.contains("on karo") || command.contains("chalu karo")
        
        if (command.contains("torch") || command.contains("flashlight") || command.contains("bujhao") || command.contains("band") || command.contains("light")) {
            return try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, turnOn)
                CommandResult(true, if (turnOn) "Flashlight turned on" else "Flashlight turned off")
            } catch (e: Exception) {
                CommandResult(true, "Flashlight control is unavailable on this device model.")
            }
        }
        
        when {
            command.contains("wi-fi") || command.contains("wifi") -> {
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                return CommandResult(true, "Opening Wi-Fi configuration.")
            }
            command.contains("bluetooth") -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                return CommandResult(true, "Opening Bluetooth settings.")
            }
            command.contains("location") || command.contains("gps") -> {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                return CommandResult(true, "Opening satellite location settings.")
            }
            command.contains("do not disturb") || command.contains("dnd") || command.contains("shant") -> {
                val intent = Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                return CommandResult(true, "Opening Do Not Disturb screen panel.")
            }
            command.contains("flight mode") || command.contains("airplane mode") -> {
                val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                return CommandResult(true, "Opening flight airplane mode panel.")
            }
        }
        return CommandResult(false)
    }
    
    private fun parseAlarmTime(command: String): Pair<Int, Int>? {
        val clean = command.lowercase()
        val timeRegex = Regex("(\\d{1,2}):?(\\d{2})?\\s*(am|pm|baje)?")
        val match = timeRegex.find(clean) ?: return null
        
        val hourStr = match.groups[1]?.value ?: return null
        val minuteStr = match.groups[2]?.value
        val ampBaje = match.groups[3]?.value ?: ""
        
        var hour = hourStr.toInt()
        val minute = minuteStr?.toInt() ?: 0
        
        val isShaam = clean.contains("shaam") || clean.contains("night") || clean.contains("raat") || clean.contains("evening") || clean.contains("pm")
        val isSubah = clean.contains("subah") || clean.contains("morning") || clean.contains("am")
        
        if (isShaam && hour < 12) {
            hour += 12
        } else if (isSubah && hour == 12) {
            hour = 0
        } else if (ampBaje.contains("pm") && hour < 12) {
            hour += 12
        } else if (ampBaje.contains("am") && hour == 12) {
            hour = 0
        }
        
        return Pair(hour, minute)
    }

    private fun handleAlarm(command: String): CommandResult {
        val parsedTime = parseAlarmTime(command)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, "Alarm from A.R.I.S")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return if (parsedTime != null) {
            val (hour, minute) = parsedTime
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour)
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute)
            context.startActivity(intent)
            val timeText = if (minute == 0) "${hour}:00" else "$hour baj ke $minute minute"
            CommandResult(true, "Alarm set kar diya $timeText ke liye.")
        } else {
            context.startActivity(intent)
            CommandResult(true, "Opening your device alarm database.")
        }
    }

    private fun handleTimer(command: String): CommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val numberRegex = Regex("\\d+")
        val match = numberRegex.find(command)
        var speech = "Opening clock timers."

        if (match != null) {
            val duration = match.value.toInt()
            val seconds = if (command.contains("hour") || command.contains("ghanta")) {
                duration * 3600
            } else if (command.contains("second")) {
                duration
            } else {
                duration * 60
            }
            intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            speech = when {
                command.contains("hour") || command.contains("ghanta") -> "Setting timer for $duration hours."
                command.contains("second") -> "Setting timer for $duration seconds."
                else -> "Setting timer for $duration minutes."
            }
        }

        context.startActivity(intent)
        return CommandResult(true, speech)
    }

    private fun handleVolume(command: String): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (command.contains("poori awaaz") || command.contains("full volume") || command.contains("max volume") || command.contains("max sound") || command.contains("tez awaaz")) {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
            return CommandResult(true, "Setting system music volume to maximum")
        }

        return when {
            command.contains("up") || command.contains("increase") || command.contains("badhao") || command.contains("tej") -> {
                am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                CommandResult(true, "Increasing sound volume")
            }
            command.contains("down") || command.contains("decrease") || command.contains("kam") -> {
                am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                CommandResult(true, "Decreasing sound volume")
            }
            command.contains("mute") || command.contains("silent") || command.contains("shant") || command.contains("band") -> {
                am.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                CommandResult(true, "Muting sound profiles")
            }
            command.contains("unmute") || command.contains("on") || command.contains("kholo") -> {
                am.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                CommandResult(true, "Unmuting volume output")
            }
            else -> CommandResult(false)
        }
    }

    private fun handleMedia(command: String): CommandResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var actionText = ""
        val keyCode = when {
            command.contains("play") || command.contains("pause") || command.contains("gaana") || command.contains("bajao") || command.contains("rok") -> {
                actionText = "Toggling music media playback"
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            command.contains("next") || command.contains("agla") -> {
                actionText = "Playing next track"
                KeyEvent.KEYCODE_MEDIA_NEXT
            }
            command.contains("previous") || command.contains("pichla") -> {
                actionText = "Playing previous media track"
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            }
            else -> return CommandResult(false)
        }
        
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return CommandResult(true, actionText)
    }

    private fun handleBattery(): CommandResult {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
        val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
        
        val chargingText = if (isCharging) "and is currently charging." else "and is not charging."
        return CommandResult(true, "Your device battery level is currently at $level percent $chargingText")
    }

    private fun handleDateTime(command: String): CommandResult {
        val isTime = command.contains("time") || command.contains("samay")
        val format = if (isTime) SimpleDateFormat("hh:mm a", Locale.getDefault()) else SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val currentInfo = format.format(Date())
        
        return CommandResult(true, "The answer is $currentInfo")
    }

    private fun handleCamera(): CommandResult {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return CommandResult(true, "Opening camera viewfinder.")
        }
        return CommandResult(true, "Default camera application could not be launched.")
    }

    private fun handleWhatsAppDeepLink(command: String): CommandResult {
        val numberRegex = Regex("\\+?\\d{10,15}")
        val matchResult = numberRegex.find(command)
        
        if (matchResult != null) {
            var phoneNumber = matchResult.value
            if (phoneNumber.length == 10 && !phoneNumber.startsWith("+") && !phoneNumber.startsWith("91")) {
                phoneNumber = "91$phoneNumber"
            } else if (phoneNumber.startsWith("+")) {
                phoneNumber = phoneNumber.replace("+", "")
            }
            
            var message = ""
            val sayingIndex = command.indexOf("saying ")
            if (sayingIndex != -1) {
                message = command.substring(sayingIndex + 7).trim()
            }
            
            return try {
                val uriString = if (message.isNotEmpty()) {
                    "whatsapp://send?phone=$phoneNumber&text=${Uri.encode(message)}"
                } else {
                    "whatsapp://send?phone=$phoneNumber"
                }
                
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult(true, "Opening WhatsApp thread with $phoneNumber.")
            } catch (e: Exception) {
                val webUriString = "https://api.whatsapp.com/send?phone=$phoneNumber"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult(true, "Redirecting WhatsApp chat link in browser.")
            }
        }
        
        return handleOpenAppByAlias("whatsapp", "WhatsApp")
    }

    private fun handlePlayStoreDeepLink(command: String): CommandResult {
        var query = command
            .replace("play store", "")
            .replace("playstore", "")
            .replace("search", "")
            .replace("details", "")
            .trim()
            
        if (query.isEmpty()) {
            return handleOpenAppByAlias("chrome", "Google Play Store")
        }
        
        val appPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android"
        )
        
        val matchedPkg = appPackages.entries.find { query.contains(it.key) }?.value
        
        return if (matchedPkg != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$matchedPkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult(true, "Opening Google Play Store application details panels.")
            } catch (e: Exception) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$matchedPkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult(true, "Opening Google Play Store portal in web browser.")
            }
        } else {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult(true, "Searching store for $query")
            } catch (e: Exception) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult(true, "Searching Play Store web locator.")
            }
        }
    }
}

private class ContactsContract {
    class Contacts {
        companion object {
            val CONTENT_URI: Uri = Uri.parse("content://com.android.contacts/contacts")
        }
    }
}
