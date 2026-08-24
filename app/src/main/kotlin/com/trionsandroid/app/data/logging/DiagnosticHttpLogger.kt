package com.trionsandroid.app.data.logging

import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject

/**
 * Routes OkHttp's request/response logging into [DiagnosticLogger] so it ends up in the
 * exportable log file. Nightscout's v3 auth exchange puts the access token directly in the
 * request path (api/v2/authorization/request/{accessToken}), not a header, so it isn't
 * covered by HttpLoggingInterceptor's header redaction — mask it here as a second layer,
 * on top of the bearer JWT header redaction already configured on the interceptor itself.
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
