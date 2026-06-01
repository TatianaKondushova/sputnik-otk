package ru.sputnik.otk.ui.screen.errorlog

import ru.sputnik.otk.data.ErrorEntry

data class ErrorLogUiState(
    val entries: List<ErrorEntry> = emptyList(),
    val isLoading: Boolean = true,
)
