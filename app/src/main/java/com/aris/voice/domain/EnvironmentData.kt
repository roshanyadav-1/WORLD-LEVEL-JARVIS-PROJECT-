package com.aris.voice.domain

data class EnvironmentData(
    val time: String = "",
    val date: String = "",
    val isNetworkAvailable: Boolean = false,
    val networkType: String = "UNKNOWN",
    val clipboardText: String? = null,
    val activeNotifications: List<String> = emptyList(),
    val grantedPermissions: List<String> = emptyList(),
    val runningServices: List<String> = emptyList()
)
