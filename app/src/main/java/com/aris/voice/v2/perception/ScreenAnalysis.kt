package com.aris.voice.v2.perception

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class SemanticParser {
    fun parseNodeTree(
        rootNode: AccessibilityNodeInfo?, 
        previousState: Set<String>?, 
        screenWidth: Int, 
        screenHeight: Int
    ): Pair<String, MutableMap<Int, AccessibilityNodeInfo>> {
        val elementMap = mutableMapOf<Int, AccessibilityNodeInfo>()
        val builder = StringBuilder()
        var indexCounter = 0

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!node.isVisibleToUser) return

            val isClickable = node.isClickable
            val isFocusable = node.isFocusable
            val isEditable = node.isEditable
            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val resourceId = node.viewIdResourceName?.substringAfterLast('/') ?: ""
            val className = node.className?.toString()?.substringAfterLast('.') ?: "Element"
            val isChecked = node.isChecked

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val isVisible = bounds.width() > 0 && bounds.height() > 0 &&
                    bounds.right > 0 && bounds.bottom > 0 &&
                    bounds.left < screenWidth && bounds.top < screenHeight

            if (isVisible && (isClickable || isEditable || text.isNotEmpty() || contentDesc.isNotEmpty())) {
                val description = when {
                    text.isNotEmpty() -> text
                    contentDesc.isNotEmpty() && contentDesc != "null" -> contentDesc
                    resourceId.isNotEmpty() -> resourceId
                    else -> ""
                }

                if (description.isNotEmpty() || isEditable || isClickable) {
                    val currentIndex = indexCounter++
                    elementMap[currentIndex] = node

                    builder.append("[$currentIndex] ")
                    if (isEditable) builder.append("INPUT ")
                    else if (isClickable) builder.append("BUTTON ")
                    else builder.append("TEXT ")

                    builder.append("'$description' ")
                    
                    if (isChecked) builder.append("(Checked) ")
                    if (className.isNotEmpty()) builder.append("Class: $className ")
                    if (resourceId.isNotEmpty()) builder.append("ID: $resourceId ")
                    
                    builder.append("\n")
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)
        
        if (builder.isEmpty()) {
            builder.append("No interactable elements found.")
        }

        return Pair(builder.toString(), elementMap)
    }
}

class XmlNode {
    // Stub
}

data class ScreenAnalysis(
    val uiRepresentation: String,
    val isKeyboardOpen: Boolean,
    val activityName: String?,
    val elementMap: MutableMap<Int, AccessibilityNodeInfo>,
    val scrollUp: Int,
    val scrollDown: Int
)
