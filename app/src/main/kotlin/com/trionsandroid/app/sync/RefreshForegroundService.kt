package com.trionsandroid.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trionsandroid.app.MainActivity
import com.trionsandroid.app.R
import com.trionsandroid.app.data.alarm.AlarmCheckRunner
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.settings.SettingsRepository
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.data.settings.timeFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * "Real-time" background mode: a foreground service that polls more often than WorkManager's
 * 15-minute minimum. Only one of this and WorkManager's periodic work runs (see BackgroundSyncScheduler).
 */
@AndroidEntryPoint
class RefreshForegroundService : Service() {

    @Inject lateinit var nightscoutRepository: NightscoutRepository
    @Inject lateinit var alarmCheckRunner: AlarmCheckRunner
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    // A foreground service alone doesn't keep coroutine timers on schedule (a log showed 5-minute
    // delays resuming after 13 to 39 minutes on Samsung One UI). A partial wake lock fixes that.
    // Not reference counted, so repeated acquire() only refreshes the timeout.
    private val wakeLock: PowerManager.WakeLock by lazy {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:RefreshForegroundService").apply {
            setReferenceCounted(false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // A fresh onCreate means the OS created a new service instance, unlike a repeated
        // onStartCommand on one that is still alive.
        diagnosticLogger.log(TAG, "Service onCreate (new instance)")
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intervalMinutes = intent?.getIntExtra(EXTRA_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
            ?: DEFAULT_INTERVAL_MINUTES
        diagnosticLogger.log(
            TAG,
            "onStartCommand startId=$startId intent=${if (intent == null) "null (likely a START_STICKY restart)" else "explicit"} " +
                "hadRunningLoop=${loopJob?.isActive == true}",
        )
        startForeground(NOTIFICATION_ID, buildNotification(glucoseText = null, lastSyncText = "Not synced yet"))
        wakeLock.acquire(WAKE_LOCK_SAFETY_TIMEOUT_MILLIS)

        loopJob?.cancel()
        loopJob = scope.launch {
            diagnosticLogger.log(TAG, "Foreground sync loop starting, interval=${intervalMinutes}m")
            var cycle = 0
            var lastFullRefreshAtMillis = 0L
            while (isActive) {
                cycle++
                // Re-acquired every cycle so the timeout never expires while the loop runs.
                wakeLock.acquire(WAKE_LOCK_SAFETY_TIMEOUT_MILLIS)
                diagnosticLogger.log(TAG, "Cycle $cycle starting at ${DIAGNOSTIC_TIME_FORMATTER.format(LocalTime.now())}")
                // Alarms only need glucose entries and treatments, so most cycles fetch just those
                // (an "essential" refresh) instead of the full set of Nightscout endpoints (profile,
                // devicestatus, lifecycle, adjustments), which cuts network/CPU work for the fast
                // real-time intervals. A full refresh still runs periodically so that data doesn't go stale.
                val now = System.currentTimeMillis()
                val essential = now - lastFullRefreshAtMillis < FULL_REFRESH_INTERVAL_MILLIS
                if (!essential) lastFullRefreshAtMillis = now
                runCatching {
                    nightscoutRepository.refresh(essential = essential)
                    alarmCheckRunner.checkAndNotify()
                }.onFailure { diagnosticLogger.logError(TAG, "Foreground sync cycle failed", it) }
                // Update the ongoing notification every cycle, so it also shows the loop is still ticking.
                val notificationTimeFormatter = settingsRepository.settings.first().timeFormat.timeFormatter()
                updateNotification(
                    glucoseText = runCatching { latestGlucoseText() }.getOrNull(),
                    lastSyncText = "Last synced ${notificationTimeFormatter.format(LocalTime.now())}",
                )
                delay(intervalMinutes * 60_000L)
            }
            diagnosticLogger.log(TAG, "Loop exited after cycle $cycle (isActive became false)")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        diagnosticLogger.log(TAG, "Foreground sync service stopped")
        super.onDestroy()
    }

    // Android 14+ limits how long a dataSync service may run and calls this when the budget is
    // used up. The service must stop; BackgroundSyncScheduler restarts it later.
    override fun onTimeout(startId: Int, fgsType: Int) {
        diagnosticLogger.log(TAG, "Foreground service execution time limit reached, stopping")
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(glucoseText: String?, lastSyncText: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(glucoseText ?: "TrioFollower syncing")
            .setContentText(lastSyncText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .build()

    private fun updateNotification(glucoseText: String?, lastSyncText: String) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(glucoseText, lastSyncText))
    }

    private suspend fun latestGlucoseText(): String? {
        val sinceMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(LATEST_READING_LOOKBACK_HOURS)
        val latest = nightscoutRepository.observeGlucoseEntries(sinceMillis).first().maxByOrNull { it.timestamp }
            ?: return null
        val unit = settingsRepository.settings.first().glucoseUnit
        return "${unit.format(latest.mgDl)} ${unit.label} ${latest.trend.arrow}"
    }

    // Tapping opens the app and triggers a fresh refresh.
    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_REFRESH_ON_OPEN, true)
        }
        return PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Background sync", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Ongoing notification while real-time background sync is active"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_INTERVAL_MINUTES = "interval_minutes"
        private const val DEFAULT_INTERVAL_MINUTES = 5
        private const val TAG = "RefreshForegroundService"
        private const val CHANNEL_ID = "trio_sync_service"
        private const val NOTIFICATION_ID = 42
        private const val OPEN_APP_REQUEST_CODE = 43
        private const val LATEST_READING_LOOKBACK_HOURS = 24L
        // Matches WorkManager's battery-friendly cadence: how often a real-time cycle does a full
        // refresh (profile, devicestatus, lifecycle, adjustments) instead of an essential one.
        private const val FULL_REFRESH_INTERVAL_MILLIS = 15 * 60_000L
        // Longer than the slowest real-time interval (30 min). Only a leak safety net.
        private const val WAKE_LOCK_SAFETY_TIMEOUT_MILLIS = 45 * 60_000L
        private val DIAGNOSTIC_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }
}
