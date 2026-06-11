package ru.sputnik.otk.ui.screen.warranty

import ru.sputnik.otk.data.bitrix.BitrixClient
import ru.sputnik.otk.data.bitrix.BitrixConfig

/**
 * Состояние экрана «Гарантия».
 *
 * Режим: одна панель за раз — найти → посмотреть → обновить стадию.
 */
data class WarrantyUiState(
    /** Вводимый номер панели. */
    val panelInput: String = "",

    /** Идёт поиск в Bitrix24. */
    val isSearching: Boolean = false,

    /** Найденная сделка (null — ещё не искали или не найдена). */
    val deal: DealInfo? = null,

    /** Ошибка поиска. */
    val searchError: String? = null,

    /** Редактируемые поля (могут отличаться от данных сделки). */
    val incomingTrack: String = "",
    val terminalBlock: Boolean = false,
    val receiptDate: String = "",
    val outgoingTrack: String = "",

    /** Выбранный ответственный (для «В ремонте», ASSIGNED_BY_ID). */
    val selectedResponsible: String = "",

    /** Выбранный дефект — enum_id для множественного списка (для «Принята на склад»). */
    val selectedDefect: String = BitrixConfig.DefectEnum.NONE,

    /** Идёт обновление сделки. */
    val isUpdating: Boolean = false,

    /** Результат последнего обновления (для снекбара). */
    val updateResult: String? = null,

    /** Список ответственных для выпадающего списка. */
    val responsibles: List<String> = BitrixConfig.ASSIGNED_USERS.map { it.name },

    /** Список дефектов для выпадающего списка (названия). */
    val defectNames: List<String> = BitrixConfig.DefectEnum.ALL.values.toList(),
)

/**
 * Информация о сделке, полученная из Bitrix24.
 */
data class DealInfo(
    val id: Int,
    val title: String,
    val stageId: String,
    val incomingTrack: String,
    val outgoingTrack: String,
    val terminalBlock: Boolean,
    val receiptDate: String,
    /** ID ответственного (ASSIGNED_BY_ID) */
    val responsibleId: String,
    /** enum_id дефекта из поля UF_CRM_1747747695061 */
    val defects: String,
)

/** Преобразует DTO BitrixClient в UI-модель. */
fun BitrixClient.Deal.toDealInfo(): DealInfo = DealInfo(
    id = id,
    title = title,
    stageId = stageId,
    incomingTrack = incomingTrack.orEmpty(),
    outgoingTrack = outgoingTrack.orEmpty(),
    terminalBlock = terminalBlock == "1" || terminalBlock == "true" || terminalBlock == "Да",
    receiptDate = receiptDate.orEmpty(),
    responsibleId = assignedById.orEmpty(),
    defects = defects.orEmpty(),
)
