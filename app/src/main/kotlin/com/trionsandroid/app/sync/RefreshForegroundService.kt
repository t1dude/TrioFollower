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
 * The "real-time" background mode: a persistent foreground service polling more often than
 * WorkManager's 15-minute floor allows, at the cost of an ongoing notification and more battery.
 * Only one of this service / WorkManager's periodic work is ever active — see
 * BackgroundSyncScheduler.
 */
@AndroidEntryPoint
class RefreshForegroundService : Service() {

    @Inject lateinit var nightscoutRepository: NightscoutRepository
    @Inject lateinit var alarmCheckRunner: AlarmCheckRunner
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    // A foreground service alone doesn't guarantee the OS lets its coroutine timers fire on
    // schedule — confirmed via a diagnostic log where this service ran continuously for 2.5 hours
    // with no restarts at all, yet delay(5 minutes) actually resumed after anywhere from 13 to 39
    // minutes (only the very last of 7 cycles landed near the configured interval). That's Samsung
    // One UI (and Doze-like power management generally) deprioritizing the process's CPU/timer
    // scheduling despite the active foreground service and "Unrestricted" battery setting. Holding
    // a partial wake lock for as long as the loop is running is the standard fix. setReferenceCounted(false)
    // because acquire() is called idempotently (re-acquiring just refreshes the safety timeout).
    private val wakeLock: PowerManager.WakeLock by lazy {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:RefreshForegroundService").apply {
            setReferenceCounted(false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // A fresh onCreate() means a brand-new Service instance — the OS destroyed the previous
        // one entirely, as opposed to onStartCommand being re-invoked on one that's still alive
        // (e.g. a START_STICKY restart passes a null Intent to onStartCommand without a new
        // onCreate if the process itself was never killed). Distinguishing those two is the
        // whole point of this log line.
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
        // Refreshes the safety-timeout window on every (re)configuration; see the wakeLock's own
        // doc comment for why this is held at all.
        wakeLock.acquire(WAKE_LOCK_SAFETY_TIMEOUT_MILLIS)

        loopJob?.cancel()
        loopJob = scope.launch {
            diagnosticLogger.log(TAG, "Foreground sync loop starting, interval=${intervalMinutes}m")
            var cycle = 0
            while (isActive) {
                cycle++
                // Re-acquiring (not just once up front) keeps the safety-timeout window comfortably
                // ahead of the loop for as long as it keeps running, however many cycles that is.
                wakeLock.acquire(WAKE_LOCK_SAFETY_TIMEOUT_MILLIS)
                diagnosticLogger.log(TAG, "Cycle $cycle starting at ${TIME_FORMATTER.format(LocalTime.now())}")
                runCatching {
                    nightscoutRepository.refresh()
                    alarmCheckRunner.checkAndNotify()
                }.onFailure { diagnosticLogger.logError(TAG, "Foreground sync cycle failed", it) }
                // Updates the ongoing notification with the current glucose and last sync time on
                // every cycle, successful or not — this doubles as a live, always-visible way to
                // tell the loop is actually still ticking, without needing a fresh diagnostic log.
                updateNotification(
                    glucoseText = runCatching { latestGlucoseText() }.getOrNull(),
                    lastSyncText = "Last synced ${TIME_FORMATTER.format(LocalTime.now())}",
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

    // Android 14+ (API 34) enforces an execution-time budget for the dataSync foreground service
    // type and calls this once it's exhausted; the service is required to stop itself promptly
    // or the system will. BackgroundSyncScheduler restarts it on the next settings re-apply (or
    // simply reopening the app), so this isn't a permanent loss of background sync.
    override fun onTimeout(startId: Int, fgsType: Int) {
        diagnosticLogger.log(TAG, "Foreground service execution time limit reached, stopping")
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(glucoseText: String?, lastSyncText: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(glucoseText ?: "TrioNS syncing")
            .setContentText(lastSyncText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .build()

    private fun updateNotification(glucoseText: String?, lastSyncText: String) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(glucoseText, lastSyncText))
    }

    /** e.g. "128 mg/dL ↗", matching the format used elsewhere (AlarmNotifier, GlucoseBubble). */
    private suspend fun latestGlucoseText(): String? {
        val sinceMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(LATEST_READING_LOOKBACK_HOURS)
        val latest = nightscoutRepository.observeGlucoseEntries(sinceMillis).first().maxByOrNull { it.timestamp }
            ?: return null
        val unit = settingsRepository.settings.first().glucoseUnit
        return "${unit.format(latest.mgDl)} ${unit.label} ${latest.trend.arrow}"
    }

    // Tapping the notification opens/foregrounds MainActivity and asks it to trigger a fresh
    // refresh (see MainActivity.EXTRA_REFRESH_ON_OPEN) rather than just showing whatever the
    // last background cycle happened to fetch.
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
        // Comfortably longer than the slowest allowed real-time interval (30m, see
        // BackgroundMode.allowedRefreshIntervals) so a legitimately slow-but-healthy loop never
        // has its wake lock expire between two cycles — this is a leak safety net, not a
        // scheduling mechanism; re-acquiring every cycle is what actually keeps it fresh.
        private const val WAKE_LOCK_SAFETY_TIMEOUT_MILLIS = 45 * 60_000L
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }
}
