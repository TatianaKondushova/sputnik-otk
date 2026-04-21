package ru.sputnik.otk.ui.screen.otk

import ru.sputnik.otk.data.Panel

data class OtkUiState(
    val master: String? = null,
    val masters: List<String> = DEFAULT_MASTERS,
    val panelInput: String = "",
    val pendingPanels: List<Panel> = emptyList(),
    val isSending: Boolean = false,
    val sendProgress: Pair<Int, Int>? = null,
) {
    companion object {
        val DEFAULT_MASTERS: List<String> = listOf(
            "Руслан", "Камиль", "Виктор", "Тимур", "Мастер",
        )
    }
}

sealed class SnackbarEvent {
    abstract val text: String
    data class Info(override val text: String) : SnackbarEvent()
    data class Error(override val text: String) : SnackbarEvent()
    data class Success(override val text: String) : SnackbarEvent()
}
