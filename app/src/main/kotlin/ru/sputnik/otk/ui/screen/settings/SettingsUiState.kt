package ru.sputnik.otk.ui.screen.settings

data class SettingsUiState(
    val webhookUrl: String = "",
    val webhookPassword: String = "",
    val masters: List<String> = emptyList(),
    val newMasterInput: String = "",
    val isLoading: Boolean = true,
)
