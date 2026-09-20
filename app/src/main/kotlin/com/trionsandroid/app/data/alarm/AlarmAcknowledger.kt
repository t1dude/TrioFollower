package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.notification.AlarmNotifier
import javax.inject.Inject
import javax.inject.Singleton

/** What acknowledging an alarm means; shared by MainActivity and AlarmAckReceiver. */
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
