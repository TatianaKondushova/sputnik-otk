package ru.sputnik.otk

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import ru.sputnik.otk.data.ErrorLogRepository
import ru.sputnik.otk.data.JsonFileErrorLogRepository
import ru.sputnik.otk.data.JsonFilePanelRepository
import ru.sputnik.otk.data.PanelRepository
import ru.sputnik.otk.data.SettingsStore
import ru.sputnik.otk.data.WebhookClient
import ru.sputnik.otk.ui.screen.errorlog.ErrorLogViewModelFactory
import ru.sputnik.otk.ui.screen.otk.OtkViewModelFactory
import ru.sputnik.otk.ui.screen.settings.SettingsViewModelFactory
import java.time.Duration

class AppContainer(context: Context) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    private val filesDir = context.filesDir

    val nfcScans = MutableSharedFlow<String>(extraBufferCapacity = 1)
    var pendingNfcPanelId: String? = null
        @Synchronized get
        @Synchronized set

    val webhookClient: WebhookClient = WebhookClient(httpClient, json)
    val panelRepository: PanelRepository = JsonFilePanelRepository(
        file = filesDir.resolve("pending_panels.json"),
        json = json,
    )
    val errorLogRepository: ErrorLogRepository = JsonFileErrorLogRepository(
        file = filesDir.resolve("error_log.json"),
        json = json,
    )
    val settingsStore: SettingsStore = SettingsStore.singleton(context, json)

    fun otkViewModelFactory(): ViewModelProvider.Factory =
        OtkViewModelFactory(webhookClient, panelRepository, errorLogRepository, settingsStore)

    fun settingsViewModelFactory(): ViewModelProvider.Factory =
        SettingsViewModelFactory(settingsStore, panelRepository, errorLogRepository)

    fun errorLogViewModelFactory(): ViewModelProvider.Factory =
        ErrorLogViewModelFactory(errorLogRepository)
}
