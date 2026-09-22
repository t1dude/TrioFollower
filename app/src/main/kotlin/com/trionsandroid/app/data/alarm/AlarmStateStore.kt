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
 * Remembers the last alarm zone notified, so a check doesn't re-notify every cycle. Also tracks
 * acknowledgement and the time of the last notification (for repeats).
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

    /** Defaults to true so an install without this key never shows a phantom unacknowledged alarm. */
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

    // --- Supplemental alarms (IOB, COB, reservoir, sensor/pump change, not looping, phone battery):
    // each kind is independent of the others and of the glucose zone above, so each gets its own
    // active/acknowledged/last-notified state, keyed by name rather than a fixed field per kind. ---

    suspend fun isSupplementalActive(kind: SupplementalAlarmKind): Boolean =
        dataStore.data.map { it[supplementalActiveKey(kind)] ?: false }.first()

    suspend fun setSupplementalActive(kind: SupplementalAlarmKind, active: Boolean) {
        dataStore.edit { it[supplementalActiveKey(kind)] = active }
    }

    /** Defaults to true, like [isAcknowledged], so an install without this key never phantom-alarms. */
    suspend fun isSupplementalAcknowledged(kind: SupplementalAlarmKind): Boolean =
        dataStore.data.map { it[supplementalAcknowledgedKey(kind)] ?: true }.first()

    suspend fun setSupplementalAcknowledged(kind: SupplementalAlarmKind, acknowledged: Boolean) {
        dataStore.edit { it[supplementalAcknowledgedKey(kind)] = acknowledged }
    }

    suspend fun getSupplementalLastNotifiedAtMillis(kind: SupplementalAlarmKind): Long =
        dataStore.data.map { it[supplementalLastNotifiedAtKey(kind)] ?: 0L }.first()

    suspend fun setSupplementalLastNotifiedAtMillis(kind: SupplementalAlarmKind, millis: Long) {
        dataStore.edit { it[supplementalLastNotifiedAtKey(kind)] = millis }
    }

    private fun supplementalActiveKey(kind: SupplementalAlarmKind) =
        booleanPreferencesKey("supplemental_${kind.name}_active")

    private fun supplementalAcknowledgedKey(kind: SupplementalAlarmKind) =
        booleanPreferencesKey("supplemental_${kind.name}_acknowledged")

    private fun supplementalLastNotifiedAtKey(kind: SupplementalAlarmKind) =
        longPreferencesKey("supplemental_${kind.name}_last_notified_at_millis")

    private object Keys {
        val LAST_PREDICTED_HIGH_NOTIFIED_AT_MILLIS = longPreferencesKey("alarm_last_predicted_high_notified_at_millis")
        val LAST_ZONE = stringPreferencesKey("last_alarm_zone")
        val ACKNOWLEDGED = booleanPreferencesKey("alarm_acknowledged")
        val LAST_NOTIFIED_AT_MILLIS = longPreferencesKey("alarm_last_notified_at_millis")
    }
}
