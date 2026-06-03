package ru.sputnik.otk

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Глобальный перехватчик необработанных исключений.
 * Пишет stack trace в файл crash.log в filesDir.
 * При следующем запуске приложение может прочитать и показать этот лог.
 */
object CrashLogger {

    private const val CRASH_FILE = "crash.log"

    fun install(context: Context) {
        val filesDir = context.filesDir
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val log = buildString {
                    appendLine("=== CRASH $timestamp ===")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine(throwable.stackTraceToString())
                    // Если есть cause — тоже пишем
                    var cause = throwable.cause
                    while (cause != null) {
                        appendLine()
                        appendLine("Caused by:")
                        appendLine(cause.stackTraceToString())
                        cause = cause.cause
                    }
                    appendLine("=== END ===")
                }
                File(filesDir, CRASH_FILE).writeText(log)
            } catch (_: Exception) {
                // не смогли записать — не падаем ещё раз
            }

            // Вызываем стандартный обработчик (покажет диалог краша)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Прочитать последний краш-лог, если есть. После чтения файл удаляется. */
    fun readAndClear(context: Context): String? {
        return try {
            val file = File(context.filesDir, CRASH_FILE)
            if (file.exists()) {
                val text = file.readText()
                file.delete()
                text
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
