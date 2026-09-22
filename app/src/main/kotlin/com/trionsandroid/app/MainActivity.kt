package com.trionsandroid.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.trionsandroid.app.data.alarm.AlarmAcknowledger
import com.trionsandroid.app.data.alarm.SupplementalAlarmKind
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
        // The theme is always dark (see TrioNSTheme), so the status/nav bar icons must always be
        // light, regardless of the system's own light/dark setting. The default enableEdgeToEdge()
        // picks icon color from the system's mode instead of the app's, so on a device in system
        // light mode it chose dark icons over our dark background: unreadable status bar text.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            TrioNSTheme {
                TrioNavHost()
            }
        }
        // The flag only applies while the window is visible, so backgrounding releases it.
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
        // Tapping the real-time sync notification refreshes right away.
        if (intent.getBooleanExtra(EXTRA_REFRESH_ON_OPEN, false)) {
            intent.removeExtra(EXTRA_REFRESH_ON_OPEN)
            lifecycleScope.launch { nightscoutRepository.refresh() }
        }
        // Tapping an alarm notification counts as acknowledging it.
        if (intent.getBooleanExtra(EXTRA_ACKNOWLEDGE_ALARM, false)) {
            intent.removeExtra(EXTRA_ACKNOWLEDGE_ALARM)
            lifecycleScope.launch { alarmAcknowledger.acknowledge() }
        }
        // Same, for a supplemental alarm (IOB, COB, reservoir, sensor/pump change, not looping, battery).
        intent.getStringExtra(EXTRA_ACKNOWLEDGE_SUPPLEMENTAL_ALARM)?.let { kindName ->
            intent.removeExtra(EXTRA_ACKNOWLEDGE_SUPPLEMENTAL_ALARM)
            val kind = runCatching { SupplementalAlarmKind.valueOf(kindName) }.getOrNull()
            if (kind != null) lifecycleScope.launch { alarmAcknowledger.acknowledgeSupplemental(kind) }
        }
    }

    companion object {
        const val EXTRA_REFRESH_ON_OPEN = "refresh_on_open"
        const val EXTRA_ACKNOWLEDGE_ALARM = "acknowledge_alarm"
        const val EXTRA_ACKNOWLEDGE_SUPPLEMENTAL_ALARM = "acknowledge_supplemental_alarm"
    }
}
