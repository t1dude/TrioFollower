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
import com.trionsandroid.app.data.alarm.SupplementalAlarmKind
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.AlarmBehavior
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
    /** [reading] is the newest reading; null only for No data with nothing recent. [behavior] is
     *  that zone's own (see AlarmSettings.behaviorFor), since sound/vibration/acknowledgement are
     *  now set per alarm rather than globally. */
    fun notify(zone: AlarmZone, reading: GlucoseReading?, unit: GlucoseUnit, behavior: AlarmBehavior) {
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
        if (behavior.requireAcknowledgement) {
            // Ongoing notifications can't be swiped away. OK or a tap acknowledges and cancels it.
            builder.setOngoing(true)
            builder.setAutoCancel(false)
            builder.addAction(0, "OK", acknowledgePendingIntent())
        } else {
            builder.setOngoing(false)
            builder.setAutoCancel(true)
        }
        // Sound and vibration can't be silenced separately on one channel, so only both off is silent.
        if (!behavior.soundEnabled && !behavior.vibrationEnabled) {
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

    /**
     * A supplemental alarm (IOB, COB, reservoir, sensor/pump change, not looping, low phone
     * battery): independent of the glucose zone and of the other supplemental kinds, so each gets
     * its own notification id and can be showing at the same time as any other.
     */
    fun notifySupplemental(kind: SupplementalAlarmKind, text: String, behavior: AlarmBehavior) {
        ensureChannel()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(kind.displayTitle)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppAndAcknowledgeSupplementalPendingIntent(kind))
        if (behavior.requireAcknowledgement) {
            builder.setOngoing(true)
            builder.setAutoCancel(false)
            builder.addAction(0, "OK", acknowledgeSupplementalPendingIntent(kind))
        } else {
            builder.setOngoing(false)
            builder.setAutoCancel(true)
        }
        if (!behavior.soundEnabled && !behavior.vibrationEnabled) {
            builder.setSilent(true)
        }

        NotificationManagerCompat.from(context).notify(kind.notificationId, builder.build())
    }

    fun cancelSupplemental(kind: SupplementalAlarmKind) {
        NotificationManagerCompat.from(context).cancel(kind.notificationId)
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

    private fun openAppAndAcknowledgeSupplementalPendingIntent(kind: SupplementalAlarmKind): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ACKNOWLEDGE_SUPPLEMENTAL_ALARM, kind.name)
        }
        return PendingIntent.getActivity(
            context,
            SUPPLEMENTAL_OPEN_REQUEST_CODE_BASE + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun acknowledgeSupplementalPendingIntent(kind: SupplementalAlarmKind): PendingIntent {
        val intent = Intent(context, AlarmAckReceiver::class.java).apply {
            action = AlarmAckReceiver.ACTION_ACKNOWLEDGE_SUPPLEMENTAL
            putExtra(AlarmAckReceiver.EXTRA_SUPPLEMENTAL_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(
            context,
            SUPPLEMENTAL_ACK_REQUEST_CODE_BASE + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Glucose, IOB/COB, reservoir, sensor/pump change, not-looping and phone battery alerts"
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "trio_glucose_alarms"
        const val NOTIFICATION_ID = 1001
        const val OPEN_APP_REQUEST_CODE = 1002
        const val ACKNOWLEDGE_REQUEST_CODE = 1003
        // Offset by SupplementalAlarmKind.ordinal, so each kind gets its own stable request code.
        const val SUPPLEMENTAL_OPEN_REQUEST_CODE_BASE = 1100
        const val SUPPLEMENTAL_ACK_REQUEST_CODE_BASE = 1200
    }
}
