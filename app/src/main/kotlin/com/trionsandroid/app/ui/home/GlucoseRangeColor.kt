package com.trionsandroid.app.ui.home

import androidx.compose.ui.graphics.Color
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.ui.theme.TrioGlucoseHigh
import com.trionsandroid.app.ui.theme.TrioGlucoseInRange
import com.trionsandroid.app.ui.theme.TrioGlucoseLow
import com.trionsandroid.app.ui.theme.TrioGlucoseUrgent

/** Colors a glucose value by range, reusing the user's own alarm thresholds from Settings. */
fun rangeColor(mgDl: Int, alarms: AlarmSettings): Color = when {
    mgDl <= alarms.urgentLow.thresholdMgDl || mgDl >= alarms.urgentHigh.thresholdMgDl -> TrioGlucoseUrgent
    mgDl <= alarms.low.thresholdMgDl -> TrioGlucoseLow
    mgDl >= alarms.high.thresholdMgDl -> TrioGlucoseHigh
    else -> TrioGlucoseInRange
}
