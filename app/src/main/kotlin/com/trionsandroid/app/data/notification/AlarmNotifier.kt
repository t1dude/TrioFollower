package com.trionsandroid.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trionsandroid.app.R
import com.trionsandroid.app.data.alarm.AlarmZone
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notify(zone: AlarmZone, reading: GlucoseReading, unit: GlucoseUnit, alarms: AlarmSettings) {
        ensureChannel()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(zone.displayTitle)
            .setContentText("${unit.format(reading.mgDl)} ${unit.label}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
        // NotificationCompat can't independently silence sound vs. vibration on a shared
        // channel — only both together via setSilent. Good enough: the common cases are
        // "alert me" (both on) and "just show it" (both off).
        if (!alarms.soundEnabled && !alarms.vibrationEnabled) {
            builder.setSilent(true)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Glucose alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Urgent low, low, high, and urgent high glucose alerts"
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "trio_glucose_alarms"
        const val NOTIFICATION_ID = 1001
    }
}
