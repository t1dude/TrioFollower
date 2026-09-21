package com.trionsandroid.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
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
    private val widgetPrefs: WidgetPrefs,
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
        val baseData = loadData()
        val density = context.resources.displayMetrics.density
        withContext(Dispatchers.Default) {
            ids.forEach { id ->
                val data = baseData.copy(transparencyPercent = widgetPrefs.transparency(id))
                // One bitmap per size the launcher may show the widget at, each drawn at exactly that
                // aspect ratio: a single bitmap is stretched to fit and would distort the bubble.
                val bySize = sizesFor(manager.getAppWidgetOptions(id), kind).associateWith { size ->
                    val w = (size.width * density).toInt().coerceIn(60, MAX_BITMAP_SIDE)
                    val h = (size.height * density).toInt().coerceIn(60, MAX_BITMAP_SIDE)
                    when (kind) {
                        WidgetKind.BUBBLE -> bubbleViews(WidgetRenderer.renderBubble(data, w, h, density))
                        WidgetKind.GRAPH -> graphViews(data, w, h, density)
                    }
                }
                manager.updateAppWidget(id, RemoteViews(bySize))
            }
        }
    }

    private fun bubbleViews(bitmap: android.graphics.Bitmap) =
        RemoteViews(context.packageName, R.layout.widget_image).apply {
            setImageViewBitmap(R.id.widget_image, bitmap)
            setOnClickPendingIntent(R.id.widget_image, openAppIntent())
        }

    /** Bubble and graph are separate images in one layout, one third and two thirds of the width. */
    private fun graphViews(data: WidgetData, widthPx: Int, heightPx: Int, density: Float): RemoteViews {
        val innerWidth = widthPx - (10 * density).toInt() // 4dp start + 6dp end padding
        val innerHeight = (heightPx - 12 * density).toInt().coerceAtLeast(40)
        val bubbleWidth = (innerWidth / 3).coerceAtLeast(40)
        val graphWidth = (innerWidth - bubbleWidth).coerceAtLeast(100)
        val alpha = ((100 - data.transparencyPercent.coerceIn(0, 100)) / 100f * 255).toInt()
        return RemoteViews(context.packageName, R.layout.widget_graph).apply {
            setImageViewBitmap(R.id.widget_bubble, WidgetRenderer.renderBubbleOnly(data, bubbleWidth, innerHeight))
            setImageViewBitmap(R.id.widget_graph, WidgetRenderer.renderGraphOnly(data, graphWidth, innerHeight, density))
            setInt(R.id.widget_background, "setImageAlpha", alpha)
            setOnClickPendingIntent(R.id.widget_root, openAppIntent())
        }
    }

    @Suppress("DEPRECATION")
    private fun sizesFor(options: Bundle, kind: WidgetKind): List<SizeF> {
        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        if (!sizes.isNullOrEmpty()) return sizes
        val defaultW = if (kind == WidgetKind.BUBBLE) 110 else 250
        return listOf(
            SizeF(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultW).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).toFloat(),
            ),
        )
    }

    suspend fun loadData(): WidgetData {
        val now = System.currentTimeMillis()
        val since = now - TimeUnit.HOURS.toMillis(7)
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
            transparencyPercent = 0,
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
