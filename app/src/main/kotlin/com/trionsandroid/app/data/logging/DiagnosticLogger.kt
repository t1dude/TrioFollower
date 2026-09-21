package com.trionsandroid.app.data.logging

import android.content.Context
import android.util.Log
import com.trionsandroid.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
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

    private var writesSinceSizeCheck = 0

    private fun appendLine(level: String, tag: String, message: String) {
        // Checking the size on every line costs a file stat each time; every so often is enough.
        if (++writesSinceSizeCheck >= SIZE_CHECK_EVERY) {
            writesSinceSizeCheck = 0
            rotateIfOverCap()
        }
        val timestamp = TIME_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))
        logFile.appendText("$timestamp $level/$tag: $message\n")
    }

    private fun rotateIfOverCap() {
        val length = logFile.length()
        if (length <= MAX_LOG_BYTES) return
        // Keep the newest half, reading only that part of the file.
        val keep = MAX_LOG_BYTES / 2
        val tail = ByteArray(keep)
        RandomAccessFile(logFile, "r").use { file ->
            file.seek(length - keep)
            file.readFully(tail)
        }
        logFile.writeBytes(tail)
    }

    private companion object {
        const val MAX_LOG_BYTES = 2 * 1024 * 1024
        const val SIZE_CHECK_EVERY = 50
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}
