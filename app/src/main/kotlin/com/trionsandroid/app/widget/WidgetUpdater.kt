package com.trionsandroid.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.trionsandroid.app.MainActivity
import com.trionsandroid.app.R
import com.trionsandroid.app.data.local.DeviceStatusDao
import com.trionsandroid.app.data.local.GlucoseEntryDao
import com.trionsandroid.app.data.nightscout.toDomain
import com.trionsandroid.app.data.nightscout.toForecast
import com.trionsandroid.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class WidgetKind(val providerClass: Class<*>) {
    BUBBLE(BubbleWidgetProvider::class.java),
    GRAPH(GraphWidgetProvider::class.java),
}

/** Renders the home screen widgets from the local cache. Reads the DAOs directly (not the
 *  repository) so the repository can call it after a refresh without a dependency cycle. */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val glucoseEntryDao: GlucoseEntryDao,
    private val deviceStatusDao: DeviceStatusDao,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun updateAll() {
        val manager = AppWidgetManager.getInstance(context)
        WidgetKind.entries.forEach { kind ->
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.providerClass))
            if (ids.isNotEmpty()) update(kind, ids)
        }
    }

    suspend fun update(kind: WidgetKind, ids: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        val data = loadData()
        val density = context.resources.displayMetrics.density
        withContext(Dispatchers.Default) {
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val defaultW = if (kind == WidgetKind.BUBBLE) 110 else 250
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultW).coerceAtLeast(60)
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).coerceAtLeast(60)
                val w = (widthDp * density).toInt().coerceAtMost(MAX_BITMAP_SIDE)
                val h = (heightDp * density).toInt().coerceAtMost(MAX_BITMAP_SIDE)
                val bitmap = when (kind) {
                    WidgetKind.BUBBLE -> WidgetRenderer.renderBubble(data, w, h, density)
                    WidgetKind.GRAPH -> WidgetRenderer.renderGraph(data, w, h, density)
                }
                val views = RemoteViews(context.packageName, R.layout.widget_image).apply {
                    setImageViewBitmap(R.id.widget_image, bitmap)
                    setOnClickPendingIntent(R.id.widget_image, openAppIntent())
                }
                manager.updateAppWidget(id, views)
            }
        }
    }

    private suspend fun loadData(): WidgetData {
        val now = System.currentTimeMillis()
        val since = now - TimeUnit.HOURS.toMillis(4)
        val settings = settingsRepository.settings.first()
        val readings = glucoseEntryDao.observeSince(since).first().map { it.toDomain() }.sortedBy { it.timestamp }
        val forecast = deviceStatusDao.observeLatestForecast().first()?.toForecast()
        return WidgetData(
            readings = readings,
            forecast = forecast,
            unit = settings.glucoseUnit,
            timeFormat = settings.timeFormat,
            alarms = settings.alarms,
            colorScheme = settings.glucoseColorScheme,
            forecastDisplay = settings.forecastDisplay,
            showNowLine = settings.showNowLine,
            transparencyPercent = settings.widgetTransparencyPercent,
            nowMillis = now,
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        // Keeps the bitmap comfortably under the RemoteViews size limit.
        const val MAX_BITMAP_SIDE = 900
    }
}
