package com.trionsandroid.app.data.logging

import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject

/**
 * Sends OkHttp logging to [DiagnosticLogger]. The access token is in the auth request path, not a
 * header, so it is masked here in addition to the header redaction.
 */
class DiagnosticHttpLogger @Inject constructor(
    private val diagnosticLogger: DiagnosticLogger,
) : HttpLoggingInterceptor.Logger {

    override fun log(message: String) {
        diagnosticLogger.log("OkHttp", redactAccessToken(message))
    }

    private fun redactAccessToken(message: String): String =
        message.replace(ACCESS_TOKEN_IN_PATH_REGEX, "$1<redacted>")

    private companion object {
        val ACCESS_TOKEN_IN_PATH_REGEX = Regex("(authorization/request/)[^\\s/?]+", RegexOption.IGNORE_CASE)
    }
}
