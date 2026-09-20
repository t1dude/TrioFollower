package com.trionsandroid.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trionsandroid.app.MainActivity
import com.trionsandroid.app.R
import com.trionsandroid.app.data.alarm.AlarmZone
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** [reading] is the newest one we have — null only for No data with nothing recent at all. */
    fun notify(zone: AlarmZone, reading: GlucoseReading?, unit: GlucoseUnit, alarms: AlarmSettings) {
        ensureChannel()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(zone.displayTitle)
            .setContentText(contentText(zone, reading, unit))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppAndAcknowledgePendingIntent())
        if (alarms.requireAcknowledgement) {
            // setOngoing(true) is what makes this un-swipe-dismissible — the only ways off screen
            // are the OK action below or tapping the notification to open the app, both of which
            // route through AlarmAcknowledger and explicitly cancel it.
            builder.setOngoing(true)
            builder.setAutoCancel(false)
            builder.addAction(0, "OK", acknowledgePendingIntent())
        } else {
            builder.setOngoing(false)
            builder.setAutoCancel(true)
        }
        // NotificationCompat can't independently silence sound vs. vibration on a shared
        // channel — only both together via setSilent. Good enough: the common cases are
        // "alert me" (both on) and "just show it" (both off).
        if (!alarms.soundEnabled && !alarms.vibrationEnabled) {
            builder.setSilent(true)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun contentText(zone: AlarmZone, reading: GlucoseReading?, unit: GlucoseUnit): String {
        if (zone == AlarmZone.NO_DATA) {
            if (reading == null) return "No glucose data received"
            val minutes = Duration.between(reading.timestamp, Instant.now()).toMinutes()
            return "No new glucose data for $minutes min (last ${unit.format(reading.mgDl)} ${unit.label})"
        }
        val value = "${unit.format(reading!!.mgDl)} ${unit.label}"
        return if (zone == AlarmZone.PREDICTED_HIGH) "$value · climbing toward high" else value
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun openAppAndAcknowledgePendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ACKNOWLEDGE_ALARM, true)
        }
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun acknowledgePendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmAckReceiver::class.java).apply {
            action = AlarmAckReceiver.ACTION_ACKNOWLEDGE
        }
        return PendingIntent.getBroadcast(
            context,
            ACKNOWLEDGE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Glucose alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Urgent low, low, predicted high, high, urgent high, and no-data glucose alerts"
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "trio_glucose_alarms"
        const val NOTIFICATION_ID = 1001
        const val OPEN_APP_REQUEST_CODE = 1002
        const val ACKNOWLEDGE_REQUEST_CODE = 1003
    }
}
