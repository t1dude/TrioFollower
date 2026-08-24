package com.trionsandroid.app.data.settings

import java.util.Locale

enum class GlucoseUnit(val label: String) {
    MG_DL("mg/dL"),
    MMOL_L("mmol/L"),
}

/** Formats a canonical mg/dL value for display in this unit. */
fun GlucoseUnit.format(mgDl: Int): String = when (this) {
    GlucoseUnit.MG_DL -> mgDl.toString()
    GlucoseUnit.MMOL_L -> String.format(Locale.getDefault(), "%.1f", mgDl / 18.0182)
}
