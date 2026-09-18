package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.notification.AlarmNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that knows what "acknowledging an alarm" means — shared by MainActivity
 * (tapping the notification body) and AlarmAckReceiver (the notification's OK action), so both
 * paths stay in sync.
 */
@Singleton
class AlarmAcknowledger @Inject constructor(
    private val alarmStateStore: AlarmStateStore,
    private val alarmNotifier: AlarmNotifier,
) {
    suspend fun acknowledge() {
        alarmStateStore.setAcknowledged(true)
        alarmNotifier.cancel()
    }
}
