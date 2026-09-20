package com.trionsandroid.app.data.logging

import android.content.Context
import android.util.Log
import com.trionsandroid.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** Writes app and network activity to a file that can be exported from Settings. */
@Singleton
class DiagnosticLogger @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val logFile: File = File(context.filesDir, "logs").apply { mkdirs() }
        .let { File(it, "trio-debug.log") }
    private val writeExecutor = Executors.newSingleThreadExecutor()

    fun log(tag: String, message: String) {
        // Logcat mirroring is only for debug builds.
        if (BuildConfig.DEBUG) Log.d(tag, message)
        writeExecutor.execute { appendLine("D", tag, message) }
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
        writeExecutor.execute {
            val full = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
            appendLine("E", tag, full)
        }
    }

    fun clear() {
        writeExecutor.execute { logFile.writeText("") }
    }

    fun file(): File = logFile

    private fun appendLine(level: String, tag: String, message: String) {
        rotateIfOverCap()
        val timestamp = TIME_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))
        logFile.appendText("$timestamp $level/$tag: $message\n")
    }

    private fun rotateIfOverCap() {
        if (logFile.exists() && logFile.length() > MAX_LOG_BYTES) {
            logFile.writeText(logFile.readText().takeLast(MAX_LOG_BYTES / 2))
        }
    }

    private companion object {
        const val MAX_LOG_BYTES = 2 * 1024 * 1024
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}
