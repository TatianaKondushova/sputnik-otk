package ru.sputnik.otk.ui.screen.warranty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.sputnik.otk.AppLogger
import ru.sputnik.otk.data.bitrix.BitrixClient
import ru.sputnik.otk.data.bitrix.BitrixConfig
import ru.sputnik.otk.ui.screen.otk.SnackbarEvent

/**
 * ViewModel экрана «Гарантия».
 *
 * Логика: ввели номер → нашли сделку → редактируем поля → жмём кнопку стадии → обновляем в Bitrix.
 */
class WarrantyViewModel(
    private val bitrixClient: BitrixClient,
) : ViewModel() {

    private val tag = "WarrantyVM"

    private val _uiState = MutableStateFlow(WarrantyUiState())
    val uiState: StateFlow<WarrantyUiState> = _uiState

    private val _events = Channel<SnackbarEvent>(Channel.BUFFERED)
    val snackbarEvents: Flow<SnackbarEvent> = _events.receiveAsFlow()

    /** Callback для вибрации (устанавливается в Screen). */
    var onVibrate: ((OtkVibrateType) -> Unit)? = null

    enum class OtkVibrateType { SUCCESS, ERROR }

    fun onPanelInputChanged(text: String) {
        _uiState.update { it.copy(panelInput = text, searchError = null) }
    }

    /** Поиск сделки по номеру панели. */
    fun onSearchClicked() {
        val panelId = _uiState.value.panelInput.trim()
        AppLogger.d(tag, "onSearchClicked: panelId='$panelId'")
        if (panelId.isEmpty()) {
            AppLogger.w(tag, "panelId пустой, пропускаем")
            return
        }

        _uiState.update { it.copy(isSearching = true, deal = null, searchError = null) }

        viewModelScope.launch {
            try {
                when (val result = bitrixClient.findDeal(panelId)) {
                    is BitrixClient.Result.Found -> {
                        val deal = result.deal.toDealInfo()
                        AppLogger.i(tag, "Сделка найдена: id=${deal.id}, title='${deal.title}', stage=${deal.stageId}")
                        _uiState.update { current ->
                            current.copy(
                                isSearching = false,
                                deal = deal,
                                incomingTrack = deal.incomingTrack,
                                terminalBlock = deal.terminalBlock,
                                receiptDate = deal.receiptDate,
                                outgoingTrack = deal.outgoingTrack,
                                selectedResponsible = current.responsibles.firstOrNull { name ->
                                    userIdByName(name) == deal.responsibleId
                                } ?: "",
                                selectedDefect = deal.defects.ifBlank { BitrixConfig.DefectEnum.NONE },
                            )
                        }
                    }
                    is BitrixClient.Result.NotFound -> {
                        AppLogger.w(tag, "Сделка не найдена для panelId='$panelId'")
                        _uiState.update { it.copy(isSearching = false, searchError = "Сделка не найдена") }
                        onVibrate?.invoke(OtkVibrateType.ERROR)
                        _events.send(SnackbarEvent.Error("Сделка не найдена для панели $panelId"))
                    }
                    is BitrixClient.Result.ApiError -> {
                        AppLogger.e(tag, "API ошибка: ${result.reason}")
                        _uiState.update { it.copy(isSearching = false, searchError = result.reason) }
                        onVibrate?.invoke(OtkVibrateType.ERROR)
                        _events.send(SnackbarEvent.Error("Ошибка API: ${result.reason}"))
                    }
                    is BitrixClient.Result.NetworkError -> {
                        AppLogger.e(tag, "Сетевая ошибка: ${result.reason}")
                        _uiState.update { it.copy(isSearching = false, searchError = result.reason) }
                        onVibrate?.invoke(OtkVibrateType.ERROR)
                        _events.send(SnackbarEvent.Error("Ошибка сети: ${result.reason}"))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(tag, "Ошибка поиска: ${e.message}", e)
                _uiState.update { it.copy(isSearching = false, searchError = e.message) }
                onVibrate?.invoke(OtkVibrateType.ERROR)
                _events.send(SnackbarEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

    // --- Редактирование полей ---

    fun onIncomingTrackChanged(value: String) {
        _uiState.update { it.copy(incomingTrack = value) }
    }

    fun onTerminalBlockChanged(checked: Boolean) {
        _uiState.update { it.copy(terminalBlock = checked) }
    }

    fun onReceiptDateChanged(value: String) {
        _uiState.update { it.copy(receiptDate = value) }
    }

    fun onOutgoingTrackChanged(value: String) {
        _uiState.update { it.copy(outgoingTrack = value) }
    }

    fun onResponsibleSelected(name: String) {
        _uiState.update { it.copy(selectedResponsible = name) }
    }

    fun onDefectSelected(defectName: String) {
        val enumId = BitrixConfig.DefectEnum.BY_NAME[defectName] ?: BitrixConfig.DefectEnum.NONE
        _uiState.update { it.copy(selectedDefect = enumId) }
    }

    // --- Кнопки перехода стадий ---

    /** «Принята на склад» — стадия C25:EXECUTING + трек, клемник, дата, дефекты (enum_id). */
    fun onReceivedClicked() {
        updateStage(BitrixConfig.Stages.RECEIVED) {
            buildMap {
                if (it.incomingTrack.isNotBlank()) put(BitrixConfig.Fields.INCOMING_TRACK, it.incomingTrack)
                put(BitrixConfig.Fields.TERMINAL_BLOCK, if (it.terminalBlock) "1" else "0")
                if (it.receiptDate.isNotBlank()) put(BitrixConfig.Fields.RECEIPT_DATE, it.receiptDate)
                put(BitrixConfig.Fields.DEFECTS, it.selectedDefect)
            }
        }
    }

    /** «В ремонте» — стадия C25:2 + ответственный (ASSIGNED_BY_ID). */
    fun onRepairClicked() {
        val responsibleName = _uiState.value.selectedResponsible
        if (responsibleName.isBlank()) {
            viewModelScope.launch {
                _events.send(SnackbarEvent.Error("Сначала выбери ответственного"))
            }
            return
        }
        val userId = userIdByName(responsibleName)
        if (userId == null) {
            viewModelScope.launch {
                _events.send(SnackbarEvent.Error("Неизвестный ответственный"))
            }
            return
        }
        updateStage(BitrixConfig.Stages.IN_REPAIR) {
            mapOf(BitrixConfig.Fields.ASSIGNED_BY_ID to userId)
        }
    }

    /** «Выходной контроль» — стадия C25:FINAL_INVOICE. */
    fun onQcOutClicked() {
        updateStage(BitrixConfig.Stages.QC_OUT)
    }

    /** «Готово к отправке» — стадия C25:7 + исходящий трек. */
    fun onReadyToShipClicked() {
        updateStage(BitrixConfig.Stages.READY_TO_SHIP) { state ->
            if (state.outgoingTrack.isNotBlank()) {
                mapOf(BitrixConfig.Fields.OUTGOING_TRACK to state.outgoingTrack)
            } else {
                emptyMap()
            }
        }
    }

    // --- Внутренний helper ---

    private fun userIdByName(name: String): String? =
        BitrixConfig.ASSIGNED_USERS.find { it.name == name }?.id

    private fun updateStage(
        stageId: String,
        fieldsBuilder: (WarrantyUiState) -> Map<String, String> = { emptyMap() },
    ) {
        val deal = _uiState.value.deal ?: return
        val fields = fieldsBuilder(_uiState.value)

        _uiState.update { it.copy(isUpdating = true, updateResult = null) }

        viewModelScope.launch {
            try {
                when (val result = bitrixClient.updateDeal(deal.id, stageId, fields)) {
                    is BitrixClient.UpdateResult.Success -> {
                        _uiState.update { current ->
                            current.copy(
                                isUpdating = false,
                                updateResult = result.stageName,
                                deal = current.deal?.copy(stageId = stageId),
                            )
                        }
                        onVibrate?.invoke(OtkVibrateType.SUCCESS)
                        _events.send(SnackbarEvent.Success("Переведено: ${result.stageName} ✓"))
                    }
                    is BitrixClient.UpdateResult.Error -> {
                        _uiState.update { it.copy(isUpdating = false, updateResult = result.reason) }
                        onVibrate?.invoke(OtkVibrateType.ERROR)
                        _events.send(SnackbarEvent.Error("Ошибка: ${result.reason}"))
                    }
                    is BitrixClient.UpdateResult.NetworkError -> {
                        _uiState.update { it.copy(isUpdating = false, updateResult = result.reason) }
                        onVibrate?.invoke(OtkVibrateType.ERROR)
                        _events.send(SnackbarEvent.Error("Сеть: ${result.reason}"))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(tag, "Ошибка обновления стадии: ${e.message}", e)
                _uiState.update { it.copy(isUpdating = false, updateResult = e.message) }
                onVibrate?.invoke(OtkVibrateType.ERROR)
                _events.send(SnackbarEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

    /** Очистить результат обновления. */
    fun consumeUpdateResult() {
        _uiState.update { it.copy(updateResult = null) }
    }

    /** Очистить найденную сделку. */
    fun onClearDeal() {
        _uiState.update {
            it.copy(
                deal = null,
                searchError = null,
                panelInput = "",
                incomingTrack = "",
                terminalBlock = false,
                receiptDate = "",
                outgoingTrack = "",
                selectedResponsible = "",
                selectedDefect = BitrixConfig.DefectEnum.NONE,
            )
        }
    }

    /** NFC: скан → автопоиск. */
    fun onNfcScanned(panelId: String) {
        AppLogger.d(tag, "onNfcScanned: panelId='$panelId'")
        _uiState.update { it.copy(panelInput = panelId) }
        onSearchClicked()
    }
}
