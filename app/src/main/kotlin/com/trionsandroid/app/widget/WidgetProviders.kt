package com.trionsandroid.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
}

abstract class BaseWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetIds)
    }

    // Redraw when the widget is resized.
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refresh(context, intArrayOf(appWidgetId))
    }

    private fun refresh(context: Context, ids: IntArray) {
        val pending = goAsync()
        val updater = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).widgetUpdater()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updater.update(kind, ids)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Widget with just the glucose bubble. */
class BubbleWidgetProvider : BaseWidgetProvider(WidgetKind.BUBBLE)

/** Widget with the bubble and a 3h past + 2h forecast graph. */
class GraphWidgetProvider : BaseWidgetProvider(WidgetKind.GRAPH)
