package ru.sputnik.otk.data.bitrix

/**
 * Конфигурация Bitrix24 для блока «Гарантия».
 *
 * ⚠ ID пользователей (ASSIGNED_BY_ID) — заглушки. Заменить на реальные
 *    после получения от руководителя!
 */
object BitrixConfig {

    /** Базовый URL REST API. */
    const val BASE_URL =
        "https://sputniksystems.bitrix24.ru/rest/13/deuvrpwbc9ng4us5/"

    /** ID пользовательских полей (UF_CRM_...) и стандартных полей. */
    object Fields {
        /** Номер панели (ключ поиска). */
        const val PANEL_ID = "UF_CRM_1629819273209"

        /** Входящий трек-номер. */
        const val INCOMING_TRACK = "UF_CRM_1693478562036"

        /** Исходящий трек-номер. */
        const val OUTGOING_TRACK = "UF_CRM_1693292930596"

        /** Клемная колодка (1/0). */
        const val TERMINAL_BLOCK = "UF_CRM_1693478607504"

        /** Дата приёмки (DD.MM.YYYY). */
        const val RECEIPT_DATE = "UF_CRM_1635513856970"

        /** Дефекты внешнего вида — множественный список (enumeration). */
        const val DEFECTS = "UF_CRM_1747747695061"

        /** Ответственный — стандартное поле сделки (не UF_CRM). */
        const val ASSIGNED_BY_ID = "ASSIGNED_BY_ID"
    }

    /** ID стадий сделки (pipeline C25). */
    object Stages {
        /** Новая. */
        const val NEW = "C25:NEW"

        /** Подготовка. */
        const val PREPARATION = "C25:PREPARATION"

        /** Принята на склад. */
        const val RECEIVED = "C25:EXECUTING"

        /** В ремонте. */
        const val IN_REPAIR = "C25:2"

        /** Выходной контроль (бывш. «В проверку»). */
        const val QC_OUT = "C25:FINAL_INVOICE"

        /** Готово к отправке. */
        const val READY_TO_SHIP = "C25:7"

        /** Закрыта / проиграна. */
        const val WON = "C25:WON"
        const val LOSE = "C25:LOSE"
    }

    /** enum_id для дефектов внешнего вида (поле UF_CRM_1747747695061). */
    object DefectEnum {
        const val NONE = "2103"
        const val KEY_READER_CAP = "2105"
        const val SCRATCHES = "2107"
        const val GLASS_CLOUDY = "2109"
        const val GLASS_MISSING = "2111"
        const val WORN_BUTTONS = "2123"
        const val ANTENNA_CAP_MISSING = "2127"
        const val BLE_CAP_MISSING = "2129"

        /** Мапа: enum_id → человекочитаемое название. */
        val ALL: Map<String, String> = mapOf(
            NONE to "Замечаний нет",
            KEY_READER_CAP to "Потерята заглушка считывателя ключей",
            SCRATCHES to "Потертости и царапины на корпусе",
            GLASS_CLOUDY to "Стекло мутное",
            GLASS_MISSING to "Стекло отсутствует",
            WORN_BUTTONS to "Стёртые кнопки",
            ANTENNA_CAP_MISSING to "Заглушка антенны отсутствует",
            BLE_CAP_MISSING to "Заглушка BLE отсутствует",
        )

        /** Обратная мапа: название → enum_id. */
        val BY_NAME: Map<String, String> = ALL.entries.associate { (id, name) -> name to id }
    }

    /** Ответственные (ASSIGNED_BY_ID). */
    val ASSIGNED_USERS: List<AssignedUser> = listOf(
        AssignedUser("Журавлёв Виктор", id = "4229"),
        AssignedUser("Хусаинов Тимур", id = "5871"),
    )

    data class AssignedUser(val name: String, val id: String)
}
