package ru.sputnik.otk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

open class WebhookClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    sealed class Result {
        data object Ok : Result()
        data object WrongPassword : Result()
        data class ServerError(val reason: String) : Result()
        data class NetworkError(val reason: String) : Result()
    }

    open suspend fun send(
        url: String,
        password: String,
        panel: Panel,
        master: String,
        date: LocalDate,
    ): Result = withContext(Dispatchers.IO) {
        val body = RequestPayload(
            password = password,
            panel = panel.id,
            master = master,
            date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            fault = panel.fault,
        )
        val requestBody = json.encodeToString(RequestPayload.serializer(), body)
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.ServerError("HTTP ${response.code}")
                }
                val responseBody = response.body?.string().orEmpty()
                parseResponse(responseBody)
            }
        } catch (e: IOException) {
            Result.NetworkError(e.message ?: "network error")
        }
    }

    private fun parseResponse(body: String): Result = try {
        val parsed = json.decodeFromString(ResponsePayload.serializer(), body)
        when {
            parsed.ok -> Result.Ok
            parsed.error == "wrong password" -> Result.WrongPassword
            else -> Result.ServerError(parsed.error ?: "unknown error")
        }
    } catch (e: Exception) {
        Result.ServerError("malformed response: ${e.message}")
    }

    @Serializable
    private data class RequestPayload(
        val password: String,
        val panel: String,
        val master: String,
        val date: String,
        val fault: String,
    )

    @Serializable
    private data class ResponsePayload(
        val ok: Boolean,
        val error: String? = null,
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
