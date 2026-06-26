package com.aris.voice.v2.actions

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
sealed class Action {

    @Serializable
    @SerialName("TapElement")
    data class TapElement(val elementId: Int) : Action()

    @Serializable
    @SerialName("Speak")
    data class Speak(val message: String) : Action()

    @Serializable
    @SerialName("Ask")
    data class Ask(val question: String) : Action()

    @Serializable
    @SerialName("LongPressElement")
    data class LongPressElement(val elementId: Int) : Action()

    @Serializable
    @SerialName("OpenApp")
    data class OpenApp(val appName: String) : Action()

    @Serializable
    @SerialName("Back")
    object Back : Action()

    @Serializable
    @SerialName("Home")
    object Home : Action()

    @Serializable
    @SerialName("SwitchApp")
    object SwitchApp : Action()

    @Serializable
    @SerialName("Wait")
    object Wait : Action()

    @Serializable
    @SerialName("ScrollDown")
    data class ScrollDown(val amount: Int) : Action()

    @Serializable
    @SerialName("ScrollUp")
    data class ScrollUp(val amount: Int) : Action()

    @Serializable
    @SerialName("SearchGoogle")
    object SearchGoogle : Action()

    @Serializable
    @SerialName("Done")
    data class Done(val success: Boolean? = null, val text: String, val filesToDisplay: List<String>? = null) : Action()

    @Serializable
    @SerialName("InputText")
    data class InputText(val text: String) : Action()

    @Serializable
    @SerialName("AppendFile")
    data class AppendFile(val fileName: String, val content: String) : Action()

    @Serializable
    @SerialName("ReadFile")
    data class ReadFile(val fileName: String) : Action()

    @Serializable
    @SerialName("WriteFile")
    data class WriteFile(val fileName: String, val content: String) : Action()

    @Serializable
    @SerialName("TapElementInputTextPressEnter")
    data class TapElementInputTextPressEnter(val index: Int, val text: String) : Action()

    @Serializable
    @SerialName("LaunchIntent")
    data class LaunchIntent(val intentName: String, val parameters: Map<String, String>) : Action()

    @Serializable
    @SerialName("SetAlarm")
    data class SetAlarm(val hour: Int, val minutes: Int, val label: String? = null) : Action()

    @Serializable
    @SerialName("SendWhatsApp")
    data class SendWhatsApp(val phoneNumber: String, val message: String) : Action()

    @Serializable
    @SerialName("TakeScreenshot")
    object TakeScreenshot : Action()

    @Serializable
    @SerialName("ControlMedia")
    data class ControlMedia(val command: String) : Action()

    @Serializable
    @SerialName("ReadNotifications")
    object ReadNotifications : Action()

    @Serializable
    @SerialName("SetReminder")
    data class SetReminder(val title: String, val hour: Int, val minutes: Int) : Action()

    @Serializable
    @SerialName("ControlBrightness")
    data class ControlBrightness(val level: Int) : Action()

    @Serializable
    @SerialName("ControlVolume")
    data class ControlVolume(val level: Int) : Action()
}
