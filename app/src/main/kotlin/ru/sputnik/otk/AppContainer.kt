package ru.sputnik.otk

import androidx.lifecycle.ViewModelProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.InMemoryErrorLogRepository
import ru.sputnik.otk.data.InMemoryPanelRepository
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.WebhookClient
import ru.sputnik.otk.ui.screen.otk.OtkViewModelFactory
import java.time.Duration

class AppContainer {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    val webhookClient: WebhookClient = WebhookClient(httpClient, json)
    val panelRepository: PanelRepository = InMemoryPanelRepository()
    val errorLogRepository: ErrorLogRepository = InMemoryErrorLogRepository()

    fun otkViewModelFactory(): ViewModelProvider.Factory =
        OtkViewModelFactory(webhookClient, panelRepository, errorLogRepository)
}
