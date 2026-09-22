package com.trionsandroid.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trionsandroid.app.data.alarm.AlarmAcknowledger
import com.trionsandroid.app.data.alarm.SupplementalAlarmKind
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
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            when (intent.action) {
                ACTION_ACKNOWLEDGE -> runCatching { alarmAcknowledger.acknowledge() }
                ACTION_ACKNOWLEDGE_SUPPLEMENTAL -> {
                    val kind = intent.getStringExtra(EXTRA_SUPPLEMENTAL_KIND)
                        ?.let { runCatching { SupplementalAlarmKind.valueOf(it) }.getOrNull() }
                    if (kind != null) runCatching { alarmAcknowledger.acknowledgeSupplemental(kind) }
                }
            }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.trionsandroid.app.ACTION_ACKNOWLEDGE_ALARM"
        const val ACTION_ACKNOWLEDGE_SUPPLEMENTAL = "com.trionsandroid.app.ACTION_ACKNOWLEDGE_SUPPLEMENTAL_ALARM"
        const val EXTRA_SUPPLEMENTAL_KIND = "supplemental_kind"
    }
}
