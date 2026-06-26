package com.aris.voice.brain.intent

class AppAliasProvider {
    val appAliases = mapOf(
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

    fun resolveAppName(appName: String): String {
        val cleanName = appName.lowercase().trim()
        for ((key, aliases) in appAliases) {
            if (aliases.any { cleanName.contains(it) }) {
                return key
            }
        }
        return cleanName
    }
}
