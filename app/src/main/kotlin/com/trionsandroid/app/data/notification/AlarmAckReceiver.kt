package com.trionsandroid.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trionsandroid.app.data.alarm.AlarmAcknowledger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "OK" action button on an alarm notification — acknowledges without opening the app. */
@AndroidEntryPoint
class AlarmAckReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmAcknowledger: AlarmAcknowledger

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { alarmAcknowledger.acknowledge() }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.trionsandroid.app.ACTION_ACKNOWLEDGE_ALARM"
    }
}
