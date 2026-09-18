package com.trionsandroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.ui.navigation.TrioNavHost
import com.trionsandroid.app.ui.theme.TrioNSTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var nightscoutRepository: NightscoutRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrioNSTheme {
                TrioNavHost()
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // Fired when the user taps the real-time-mode foreground-service notification (see
    // RefreshForegroundService.openAppPendingIntent) — the notification only shows whatever the
    // last background cycle fetched, so tapping it should kick off a fresh fetch rather than
    // leaving the user looking at stale data until the next scheduled cycle.
    private fun handleIntent(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_REFRESH_ON_OPEN, false)) return
        intent.removeExtra(EXTRA_REFRESH_ON_OPEN)
        lifecycleScope.launch { nightscoutRepository.refresh() }
    }

    companion object {
        const val EXTRA_REFRESH_ON_OPEN = "refresh_on_open"
    }
}
