package com.aris.voice.runtime

class ResponseFormatter {
    fun format(input: String): String {
        var text = input

        // Remove common markdown formatting
        text = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // Bold
        text = text.replace(Regex("\\*(.*?)\\*"), "$1") // Italic
        text = text.replace(Regex("_(.*?)_"), "$1") // Italic
        text = text.replace(Regex("`(.*?)`"), "$1") // Code

        // Expand common abbreviations
        text = text.replace(Regex("(?i)\\bDr\\.\\s"), "Doctor ")
        text = text.replace(Regex("(?i)\\bMr\\.\\s"), "Mister ")
        text = text.replace(Regex("(?i)\\bMrs\\.\\s"), "Missus ")
        text = text.replace(Regex("(?i)\\bMs\\.\\s"), "Miss ")
        text = text.replace(Regex("(?i)\\bProf\\.\\s"), "Professor ")
        
        // Currency
        text = text.replace("₹", "Rupees ")
        text = text.replace("$", "Dollars ")
        text = text.replace("€", "Euros ")
        text = text.replace("£", "Pounds ")

        // Normalize whitespace
        text = text.replace(Regex("\\s+"), " ")
        
        return text.trim()
    }
}
