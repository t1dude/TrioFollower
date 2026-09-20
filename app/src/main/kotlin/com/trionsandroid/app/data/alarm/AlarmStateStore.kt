package com.trionsandroid.app.data.alarm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last zone we already notified about, so a background check doesn't re-notify
 * every cycle for an ongoing high/low — only when the zone actually changes (including
 * escalating from low to urgent-low, or a fresh return to in-range clearing the state so the
 * next excursion notifies again). Also tracks whether the current alarm has been acknowledged
 * (for AlarmSettings.requireAcknowledgement) and when it was last (re-)notified about (for
 * AlarmSettings.repeatIfNotAcknowledged).
 */
@Singleton
class AlarmStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun getLastZone(): AlarmZone? =
        dataStore.data
            .map { prefs -> prefs[Keys.LAST_ZONE]?.let { runCatching { AlarmZone.valueOf(it) }.getOrNull() } }
            .first()

    suspend fun setLastZone(zone: AlarmZone) {
        dataStore.edit { it[Keys.LAST_ZONE] = zone.name }
    }

    /** Defaults to true (nothing pending) so a pre-existing install without this key never
     *  behaves as if there's an unacknowledged alarm sitting around. */
    suspend fun isAcknowledged(): Boolean =
        dataStore.data.map { it[Keys.ACKNOWLEDGED] ?: true }.first()

    suspend fun setAcknowledged(acknowledged: Boolean) {
        dataStore.edit { it[Keys.ACKNOWLEDGED] = acknowledged }
    }

    suspend fun getLastNotifiedAtMillis(): Long =
        dataStore.data.map { it[Keys.LAST_NOTIFIED_AT_MILLIS] ?: 0L }.first()

    suspend fun setLastNotifiedAtMillis(millis: Long) {
        dataStore.edit { it[Keys.LAST_NOTIFIED_AT_MILLIS] = millis }
    }

    suspend fun getLastPredictedHighNotifiedAtMillis(): Long =
        dataStore.data.map { it[Keys.LAST_PREDICTED_HIGH_NOTIFIED_AT_MILLIS] ?: 0L }.first()

    suspend fun setLastPredictedHighNotifiedAtMillis(millis: Long) {
        dataStore.edit { it[Keys.LAST_PREDICTED_HIGH_NOTIFIED_AT_MILLIS] = millis }
    }

    private object Keys {
        val LAST_PREDICTED_HIGH_NOTIFIED_AT_MILLIS = longPreferencesKey("alarm_last_predicted_high_notified_at_millis")
        val LAST_ZONE = stringPreferencesKey("last_alarm_zone")
        val ACKNOWLEDGED = booleanPreferencesKey("alarm_acknowledged")
        val LAST_NOTIFIED_AT_MILLIS = longPreferencesKey("alarm_last_notified_at_millis")
    }
}
