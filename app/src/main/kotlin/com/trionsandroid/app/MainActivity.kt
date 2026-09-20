package com.trionsandroid.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.trionsandroid.app.data.alarm.AlarmAcknowledger
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.settings.SettingsRepository
import com.trionsandroid.app.ui.navigation.TrioNavHost
import com.trionsandroid.app.ui.theme.TrioNSTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var nightscoutRepository: NightscoutRepository
    @Inject lateinit var alarmAcknowledger: AlarmAcknowledger
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrioNSTheme {
                TrioNavHost()
            }
        }
        // FLAG_KEEP_SCREEN_ON only holds while this window is visible, so backgrounding the app
        // releases it automatically — no manual cleanup needed.
        lifecycleScope.launch {
            settingsRepository.settings.map { it.keepScreenOn }.distinctUntilChanged().collect { keepOn ->
                if (keepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Fired when the user taps the real-time-mode foreground-service notification (see
        // RefreshForegroundService.openAppPendingIntent) — the notification only shows whatever
        // the last background cycle fetched, so tapping it should kick off a fresh fetch rather
        // than leaving the user looking at stale data until the next scheduled cycle.
        if (intent.getBooleanExtra(EXTRA_REFRESH_ON_OPEN, false)) {
            intent.removeExtra(EXTRA_REFRESH_ON_OPEN)
            lifecycleScope.launch { nightscoutRepository.refresh() }
        }
        // Fired when the user taps an alarm notification's body (see
        // AlarmNotifier.openAppAndAcknowledgePendingIntent) — tapping counts as acknowledging it,
        // same as pressing its OK action.
        if (intent.getBooleanExtra(EXTRA_ACKNOWLEDGE_ALARM, false)) {
            intent.removeExtra(EXTRA_ACKNOWLEDGE_ALARM)
            lifecycleScope.launch { alarmAcknowledger.acknowledge() }
        }
    }

    companion object {
        const val EXTRA_REFRESH_ON_OPEN = "refresh_on_open"
        const val EXTRA_ACKNOWLEDGE_ALARM = "acknowledge_alarm"
    }
}
