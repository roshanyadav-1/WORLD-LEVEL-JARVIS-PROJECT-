package com.aris.voice.v2.logging

import android.content.Context

data class TaskLog(val uid: String, val timestamp: Long, val input: String, val output: String? = null)

object TaskLogger {
    fun log(context: Context, input: String, output: String?) {}
    fun getLogs(context: Context): List<TaskLog> = emptyList()
    fun getLog(context: Context, uid: String): TaskLog? = null
}
