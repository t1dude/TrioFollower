package com.trionsandroid.app.data.nightscout

import com.trionsandroid.app.data.remote.NightscoutServiceFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches the short-lived JWT obtained from Nightscout's v3 auth endpoint so callers
 * don't re-authenticate on every request. Nightscout's default JWT lifetime is one hour;
 * we refresh a bit early rather than parsing the token's `exp` claim, which keeps this simple.
 */
@Singleton
class NightscoutAuthManager @Inject constructor(
    private val serviceFactory: NightscoutServiceFactory,
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var cachedForBaseUrl: String? = null
    private var cachedForAccessToken: String? = null
    private var expiresAtMillis: Long = 0

    suspend fun getBearerToken(baseUrl: String, accessToken: String): String = mutex.withLock {
        val now = System.currentTimeMillis()
        val token = cachedToken
        if (token != null && cachedForBaseUrl == baseUrl && cachedForAccessToken == accessToken && now < expiresAtMillis) {
            return@withLock "Bearer $token"
        }
        val response = serviceFactory.authApi(baseUrl).requestAuthorization(accessToken)
        cachedToken = response.token
        cachedForBaseUrl = baseUrl
        cachedForAccessToken = accessToken
        expiresAtMillis = now + TOKEN_TTL_MILLIS
        "Bearer ${response.token}"
    }

    private companion object {
        const val TOKEN_TTL_MILLIS = 50L * 60 * 1000
    }
}
