package ru.sputnik.otk.ui.screen.otk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.WebhookClient

class OtkViewModelFactory(
    private val webhookClient: WebhookClient,
    private val panelRepository: PanelRepository,
    private val errorLogRepository: ErrorLogRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == OtkViewModel::class.java) {
            "OtkViewModelFactory can create only OtkViewModel"
        }
        return OtkViewModel(webhookClient, panelRepository, errorLogRepository) as T
    }
}
