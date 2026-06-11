package ru.sputnik.otk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Глобальный логгер для отладки.
 * Хранит последние 200 строк логов, доступных для просмотра и копирования.
 */
object AppLogger {

    private val maxLines = 200
    private val logs = mutableListOf<String>()

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @JvmStatic
    @Synchronized
    fun d(tag: String, message: String) {
        val line = "[${timeFormat.format(Date())}] D/$tag: $message"
        logs.add(line)
        trim()
        _lines.value = logs.toList()
        android.util.Log.d(tag, message)
    }

    @JvmStatic
    @Synchronized
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val line = buildString {
            append("[${timeFormat.format(Date())}] E/$tag: $message")
            throwable?.let {
                append(" | ")
                append(it.javaClass.simpleName)
                append(": ")
                append(it.message)
            }
        }
        logs.add(line)
        trim()
        _lines.value = logs.toList()
        android.util.Log.e(tag, message, throwable)
    }

    @JvmStatic
    @Synchronized
    fun w(tag: String, message: String) {
        val line = "[${timeFormat.format(Date())}] W/$tag: $message"
        logs.add(line)
        trim()
        _lines.value = logs.toList()
        android.util.Log.w(tag, message)
    }

    @JvmStatic
    @Synchronized
    fun i(tag: String, message: String) {
        val line = "[${timeFormat.format(Date())}] I/$tag: $message"
        logs.add(line)
        trim()
        _lines.value = logs.toList()
        android.util.Log.i(tag, message)
    }

    @Synchronized
    fun getText(): String = logs.joinToString("\n")

    @Synchronized
    fun clear() {
        logs.clear()
        _lines.value = emptyList()
    }

    private fun trim() {
        while (logs.size > maxLines) {
            logs.removeAt(0)
        }
    }
}
