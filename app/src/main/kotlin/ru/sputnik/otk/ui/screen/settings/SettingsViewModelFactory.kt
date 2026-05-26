package ru.sputnik.otk.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.SettingsStore

class SettingsViewModelFactory(
    private val settingsStore: SettingsStore,
    private val panelRepository: PanelRepository,
    private val errorLogRepository: ErrorLogRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsStore, panelRepository, errorLogRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
