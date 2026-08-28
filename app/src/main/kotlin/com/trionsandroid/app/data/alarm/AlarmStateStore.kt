package com.trionsandroid.app.data.alarm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last zone we already notified about, so a background check doesn't re-notify
 * every cycle for an ongoing high/low — only when the zone actually changes (including
 * escalating from low to urgent-low, or a fresh return to in-range clearing the state so the
 * next excursion notifies again).
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

    private object Keys {
        val LAST_ZONE = stringPreferencesKey("last_alarm_zone")
    }
}
