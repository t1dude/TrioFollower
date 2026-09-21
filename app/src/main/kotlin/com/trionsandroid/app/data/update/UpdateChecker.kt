package com.trionsandroid.app.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.trionsandroid.app.BuildConfig
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/t1dude/TrioFollower/releases/latest"
private val CHECK_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)

/** A newer release found on GitHub. [notes] is the release description. */
data class UpdateInfo(val version: String, val url: String, val notes: String)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val url: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
private data class AssetDto(val name: String)

/**
 * Looks for a newer release on GitHub, at most once a day. Only releases with an APK attached
 * count, so users aren't told about an update they can't download yet.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val client: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
    private val diagnosticLogger: DiagnosticLogger,
) {
    /** The newest release if it's newer than this app and hasn't been dismissed. */
    val availableUpdate: Flow<UpdateInfo?> = dataStore.data.map { prefs ->
        val version = prefs[LATEST_VERSION] ?: return@map null
        val dismissed = prefs[DISMISSED_VERSION]
        if (version == dismissed || !isNewer(version, BuildConfig.VERSION_NAME)) return@map null
        UpdateInfo(version, prefs[LATEST_URL].orEmpty(), prefs[LATEST_NOTES].orEmpty())
    }

    /** Checks if the setting is on and the last check was more than a day ago. */
    suspend fun checkIfDue() {
        if (!settingsRepository.settings.first().checkForUpdates) return
        val last = dataStore.data.first()[LAST_CHECK] ?: 0L
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MILLIS) return
        fetch()
    }

    /** Manual check: ignores the setting and the daily limit, and un-dismisses a found update. */
    suspend fun checkNow(): UpdateCheckResult {
        val result = fetch()
        if (result is UpdateCheckResult.Available) {
            dataStore.edit { it.remove(DISMISSED_VERSION) }
        }
        return result
    }

    suspend fun dismiss(version: String) {
        dataStore.edit { it[DISMISSED_VERSION] = version }
    }

    private suspend fun fetch(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    diagnosticLogger.log(TAG, "Update check failed: HTTP ${response.code}")
                    return@withContext UpdateCheckResult.Failed
                }
                val release = json.decodeFromString<ReleaseDto>(response.body?.string().orEmpty())
                dataStore.edit { it[LAST_CHECK] = System.currentTimeMillis() }
                val version = release.tag.removePrefix("v")
                val usable = !release.draft && !release.prerelease && release.assets.any { it.name.endsWith(".apk") }
                if (!usable || !isNewer(version, BuildConfig.VERSION_NAME)) {
                    dataStore.edit { it.remove(LATEST_VERSION) }
                    return@withContext UpdateCheckResult.UpToDate
                }
                dataStore.edit {
                    it[LATEST_VERSION] = version
                    it[LATEST_URL] = release.url
                    it[LATEST_NOTES] = release.body.orEmpty()
                }
                UpdateCheckResult.Available(UpdateInfo(version, release.url, release.body.orEmpty()))
            }
        } catch (e: Exception) {
            diagnosticLogger.logError(TAG, "Update check failed", e)
            UpdateCheckResult.Failed
        }
    }

    private companion object {
        const val TAG = "UpdateChecker"
        val LATEST_VERSION = stringPreferencesKey("update_latest_version")
        val LATEST_URL = stringPreferencesKey("update_latest_url")
        val LATEST_NOTES = stringPreferencesKey("update_latest_notes")
        val DISMISSED_VERSION = stringPreferencesKey("update_dismissed_version")
        val LAST_CHECK = longPreferencesKey("update_last_check")
    }
}

/** True if [candidate] is a higher version than [current], comparing dot-separated numbers. */
internal fun isNewer(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
