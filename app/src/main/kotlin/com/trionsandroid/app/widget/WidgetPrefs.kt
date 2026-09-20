package com.trionsandroid.app.widget

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Per-widget settings (currently just the background transparency), keyed by widget id. */
@Singleton
class WidgetPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    /** 0 = solid black, 100 = fully transparent. */
    fun transparency(widgetId: Int): Int = prefs.getInt("transparency_$widgetId", 0)

    fun setTransparency(widgetId: Int, percent: Int) {
        prefs.edit { putInt("transparency_$widgetId", percent.coerceIn(0, 100)) }
    }

    fun remove(widgetIds: IntArray) {
        prefs.edit { widgetIds.forEach { remove("transparency_$it") } }
    }
}
