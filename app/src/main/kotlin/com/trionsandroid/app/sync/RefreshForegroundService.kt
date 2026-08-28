package com.trionsandroid.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.trionsandroid.app.R
import com.trionsandroid.app.data.alarm.AlarmCheckRunner
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intervalMinutes = intent?.getIntExtra(EXTRA_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
            ?: DEFAULT_INTERVAL_MINUTES
        startForeground(NOTIFICATION_ID, buildNotification())

        loopJob?.cancel()
        loopJob = scope.launch {
            diagnosticLogger.log(TAG, "Foreground sync loop starting, interval=${intervalMinutes}m")
            while (isActive) {
                runCatching {
                    nightscoutRepository.refresh()
                    alarmCheckRunner.checkAndNotify()
                }.onFailure { diagnosticLogger.logError(TAG, "Foreground sync cycle failed", it) }
                delay(intervalMinutes * 60_000L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        diagnosticLogger.log(TAG, "Foreground sync service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("TrioNS syncing")
            .setContentText("Watching your glucose in the background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

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
    }
}
