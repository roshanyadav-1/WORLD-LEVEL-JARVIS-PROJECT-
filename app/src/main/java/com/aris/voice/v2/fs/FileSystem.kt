package com.aris.voice.v2.fs

import android.content.Context
import java.io.File

class FileSystem(context: Context) {
    val workspaceDir: File = context.filesDir

    fun getTodoContents(): String = ""
    fun describe(): String = ""

    fun appendFile(fileName: String, content: String): Boolean {
        return try {
            File(workspaceDir, fileName).appendText(content)
            true
        } catch (e: Exception) { false }
    }

    fun readFile(fileName: String): String {
        val file = File(workspaceDir, fileName)
        return if (file.exists()) file.readText() else "Error: File not found"
    }

    fun writeFile(fileName: String, content: String): Boolean {
        return try {
            File(workspaceDir, fileName).writeText(content)
            true
        } catch (e: Exception) { false }
    }
}
