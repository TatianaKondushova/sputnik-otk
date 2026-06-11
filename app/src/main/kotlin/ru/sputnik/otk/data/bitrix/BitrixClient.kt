package ru.sputnik.otk.data.bitrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.sputnik.otk.data.ErrorLogRepository

/**
 * Клиент Bitrix24 REST API для блока «Гарантия».
 *
 * Поиск сделки → получение данных → обновление полей + стадии.
 */
class BitrixClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val errorLog: ErrorLogRepository,
) {

    sealed class Result {
        data class Found(val deal: Deal) : Result()
        data class NotFound(val panelId: String) : Result()
        data class ApiError(val reason: String) : Result()
        data class NetworkError(val reason: String) : Result()
    }

    sealed class UpdateResult {
        data class Success(val stageName: String) : UpdateResult()
        data class Error(val reason: String) : UpdateResult()
        data class NetworkError(val reason: String) : UpdateResult()
    }

    /**
     * Ищет сделку по номеру панели [panelId] через поле UF_CRM_1629819273209.
     * Возвращает последнюю найденную сделку (как в старом приложении).
     */
    suspend fun findDeal(panelId: String): Result = withContext(Dispatchers.IO) {
        val url = buildListUrl(panelId) ?: return@withContext Result.ApiError("bad base URL")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.ApiError("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseDealList(panelId, body)
            }
        } catch (e: Exception) {
            errorLog.log(
                panelId = panelId,
                reason = "bitrix.findDeal: ${e.message}",
            )
            Result.NetworkError(e.message ?: "network error")
        }
    }

    /**
     * Обновляет сделку: стадия + дополнительные поля.
     *
     * @param dealId ID сделки в Bitrix24
     * @param stageId ID новой стадии (например, C25:EXECUTING)
     * @param fields Карта полей для обновления (ключ = UF_CRM_..., значение = строка)
     * @param defectEnums Список enum_id дефектов для множественного выбора (отдельно, т.к. массив)
     */
    suspend fun updateDeal(
        dealId: Int,
        stageId: String,
        fields: Map<String, String> = emptyMap(),
        defectEnums: List<String> = emptyList(),
    ): UpdateResult = withContext(Dispatchers.IO) {
        val url = buildUpdateUrl(dealId, stageId, fields, defectEnums)
            ?: return@withContext UpdateResult.Error("bad base URL")

        val request = Request.Builder()
            .url(url)
            .get() // Bitrix REST принимает параметры в GET
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use UpdateResult.Error("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseUpdateResponse(stageId, body)
            }
        } catch (e: Exception) {
            errorLog.log(
                panelId = "deal_$dealId",
                reason = "bitrix.updateDeal $dealId → $stageId: ${e.message}",
            )
            UpdateResult.NetworkError(e.message ?: "network error")
        }
    }

    // --- URL builders ---

    private fun buildListUrl(panelId: String): String? {
        val base = BitrixConfig.BASE_URL.toHttpUrlOrNull() ?: return null
        return base.newBuilder()
            .addPathSegment("crm.deal.list.json")
            .addQueryParameter("FILTER[${BitrixConfig.Fields.PANEL_ID}]", panelId)
            .addQueryParameter("SELECT[]", "ID")
            .addQueryParameter("SELECT[]", "TITLE")
            .addQueryParameter("SELECT[]", "STAGE_ID")
            .addQueryParameter("SELECT[]", BitrixConfig.Fields.INCOMING_TRACK)
            .addQueryParameter("SELECT[]", BitrixConfig.Fields.OUTGOING_TRACK)
            .addQueryParameter("SELECT[]", BitrixConfig.Fields.TERMINAL_BLOCK)
            .addQueryParameter("SELECT[]", BitrixConfig.Fields.RECEIPT_DATE)
            .addQueryParameter("SELECT[]", "ASSIGNED_BY_ID")
            .addQueryParameter("SELECT[]", BitrixConfig.Fields.DEFECTS)
            .addQueryParameter("ORDER[ID]", "DESC")
            .build()
            .toString()
    }

    private fun buildUpdateUrl(
        dealId: Int,
        stageId: String,
        fields: Map<String, String>,
        defectEnums: List<String>,
    ): String? {
        val base = BitrixConfig.BASE_URL.toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder()
            .addPathSegment("crm.deal.update.json")
            .addQueryParameter("id", dealId.toString())
            .addQueryParameter("fields[STAGE_ID]", stageId)

        fields.forEach { (key, value) ->
            builder.addQueryParameter("fields[$key]", value)
        }

        // Дефекты — множественный выбор: массивный синтаксис
        defectEnums.forEach { enumId ->
            builder.addQueryParameter("fields[${BitrixConfig.Fields.DEFECTS}][]", enumId)
        }

        return builder.build().toString()
    }

    // --- Response parsers ---

    private fun parseDealList(panelId: String, body: String): Result = try {
        val parsed = json.decodeFromString(DealListResponse.serializer(), body)
        if (parsed.error != null) {
            Result.ApiError(parsed.errorDescription ?: parsed.error)
        } else {
            val deals = parsed.result
            if (deals.isNullOrEmpty()) {
                Result.NotFound(panelId)
            } else {
                Result.Found(deals.first()) // Порядок DESC → последняя первая
            }
        }
    } catch (e: Exception) {
        Result.ApiError("parse error: ${e.message}")
    }

    private fun parseUpdateResponse(stageId: String, body: String): UpdateResult = try {
        val parsed = json.decodeFromString(UpdateResponse.serializer(), body)
        if (parsed.error != null) {
            UpdateResult.Error(parsed.errorDescription ?: parsed.error)
        } else if (parsed.result == true) {
            val stageName = stageName(stageId)
            UpdateResult.Success(stageName)
        } else {
            UpdateResult.Error("update failed")
        }
    } catch (e: Exception) {
        UpdateResult.Error("parse error: ${e.message}")
    }

    private fun stageName(stageId: String): String = when (stageId) {
        BitrixConfig.Stages.NEW -> "Новая"
        BitrixConfig.Stages.PREPARATION -> "Подготовка"
        BitrixConfig.Stages.RECEIVED -> "Принята на склад"
        BitrixConfig.Stages.IN_REPAIR -> "В ремонте"
        BitrixConfig.Stages.QC_OUT -> "Выходной контроль"
        BitrixConfig.Stages.READY_TO_SHIP -> "Готово к отправке"
        BitrixConfig.Stages.WON -> "Закрыта"
        BitrixConfig.Stages.LOSE -> "Проиграна"
        else -> stageId
    }

    // --- DTO ---

    @Serializable
    data class Deal(
        @SerialName("ID") private val idRaw: JsonElement? = null,
        @SerialName("TITLE") private val titleRaw: JsonElement? = null,
        @SerialName("STAGE_ID") private val stageIdRaw: JsonElement? = null,
        @SerialName(BitrixConfig.Fields.INCOMING_TRACK) private val incomingTrackRaw: JsonElement? = null,
        @SerialName(BitrixConfig.Fields.OUTGOING_TRACK) private val outgoingTrackRaw: JsonElement? = null,
        @SerialName(BitrixConfig.Fields.TERMINAL_BLOCK) private val terminalBlockRaw: JsonElement? = null,
        @SerialName(BitrixConfig.Fields.RECEIPT_DATE) private val receiptDateRaw: JsonElement? = null,
        @SerialName("ASSIGNED_BY_ID") private val assignedByIdRaw: JsonElement? = null,
        @SerialName(BitrixConfig.Fields.DEFECTS) private val defectsRaw: JsonElement? = null,
    ) {
        /** Bitrix24 может вернуть ID как число или строку. */
        val id: Int
            get() = parseBitrixString(idRaw).toIntOrNull() ?: 0

        val title: String
            get() = parseBitrixString(titleRaw)

        val stageId: String
            get() = parseBitrixString(stageIdRaw)

        val incomingTrack: String
            get() = parseBitrixString(incomingTrackRaw)

        val outgoingTrack: String
            get() = parseBitrixString(outgoingTrackRaw)

        val terminalBlock: String
            get() = parseBitrixString(terminalBlockRaw)

        val receiptDate: String
            get() = parseBitrixString(receiptDateRaw)

        val assignedById: String
            get() = parseBitrixString(assignedByIdRaw)

        /** Распарсенное поле дефектов: строка/массив → список enum_id, false/null → пусто. */
        val defects: List<String>
            get() = parseBitrixList(defectsRaw)
    }

    @Serializable
    private data class DealListResponse(
        val result: List<Deal>? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    @Serializable
    private data class UpdateResponse(
        val result: Boolean? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )
}

/** Распарсить значение из Bitrix24: строка/число → значение, массив → первый элемент, false/null → пусто. */
private fun parseBitrixString(raw: JsonElement?): String {
    return when (raw) {
        is JsonPrimitive -> raw.content.takeIf { it != "true" && it != "false" } ?: ""
        is JsonArray -> raw.firstOrNull()?.let { first ->
            (first as? JsonPrimitive)?.content?.takeIf { it != "true" && it != "false" }
        } ?: ""
        else -> ""
    }
}

/** Распарсить список значений: строка/число → [значение], массив → все элементы, false/null → пусто. */
private fun parseBitrixList(raw: JsonElement?): List<String> {
    return when (raw) {
        is JsonPrimitive -> {
            val v = raw.content.takeIf { it != "true" && it != "false" && it.isNotBlank() }
            if (v != null) listOf(v) else emptyList()
        }
        is JsonArray -> raw.mapNotNull { element ->
            (element as? JsonPrimitive)?.content?.takeIf { it != "true" && it != "false" && it.isNotBlank() }
        }
        else -> emptyList()
    }
}
