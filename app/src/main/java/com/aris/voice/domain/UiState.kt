package com.aris.voice.domain

data class UiElement(
    val id: String,
    val text: String?,
    val contentDescription: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isSelected: Boolean,
    val isFocused: Boolean,
    val bounds: String // Representing rect bounds
)

data class UiState(
    val visibleHierarchy: List<UiElement> = emptyList(),
    val clickableElements: List<UiElement> = emptyList(),
    val editableFields: List<UiElement> = emptyList(),
    val scrollableContainers: List<UiElement> = emptyList(),
    val selectedElements: List<UiElement> = emptyList(),
    val focusedElement: UiElement? = null
)
