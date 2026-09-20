package com.trionsandroid.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.trionsandroid.app.ui.theme.TrioNSTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

/** Shown when a widget is added (and from "Reconfigure" on Android 12+): sets that widget's transparency. */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var updater: WidgetUpdater
    @Inject lateinit var prefs: WidgetPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cancelled unless the user taps Done, so backing out doesn't add the widget.
        setResult(Activity.RESULT_CANCELED)

        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val kind = if (AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className ==
            BubbleWidgetProvider::class.java.name
        ) {
            WidgetKind.BUBBLE
        } else {
            WidgetKind.GRAPH
        }

        setContent {
            TrioNSTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigScreen(
                        kind = kind,
                        initialTransparency = prefs.transparency(widgetId),
                        loadData = { updater.loadData() },
                        onDone = { transparency ->
                            prefs.setTransparency(widgetId, transparency)
                            finishWithResult(kind, widgetId)
                        },
                    )
                }
            }
        }
    }

    private fun finishWithResult(kind: WidgetKind, widgetId: Int) {
        lifecycleScope.launch {
            updater.update(kind, intArrayOf(widgetId))
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    kind: WidgetKind,
    initialTransparency: Int,
    loadData: suspend () -> WidgetData,
    onDone: (Int) -> Unit,
) {
    var data by remember { mutableStateOf<WidgetData?>(null) }
    var transparency by remember { mutableFloatStateOf(initialTransparency.toFloat()) }
    LaunchedEffect(Unit) { data = loadData() }

    val density = LocalDensity.current.density
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Widget transparency", style = MaterialTheme.typography.titleLarge)

        // A busy backdrop so the effect of the transparency is visible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFFE8D9A8), Color(0xFF7FB7D8), Color(0xFFB5D99C))))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            data?.let { loaded ->
                val widthDp = if (kind == WidgetKind.BUBBLE) 110 else 250
                val bitmap = remember(loaded, transparency) {
                    WidgetRenderer.renderPreview(
                        loaded.copy(transparencyPercent = transparency.roundToInt()),
                        kind,
                        (widthDp * density).toInt(),
                        (110 * density).toInt(),
                        density,
                    )
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Widget preview",
                    modifier = Modifier.size(widthDp.dp, 110.dp),
                )
            }
        }

        Text("${transparency.roundToInt()}%  (0% is solid black, 100% is fully transparent)")
        Slider(value = transparency, onValueChange = { transparency = it }, valueRange = 0f..100f)
        Button(onClick = { onDone(transparency.roundToInt()) }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
