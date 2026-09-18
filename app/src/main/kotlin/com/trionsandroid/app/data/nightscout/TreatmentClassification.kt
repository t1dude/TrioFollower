package com.trionsandroid.app.data.nightscout

// "Bolus" substring catches Correction/Meal/Snack/Combo Bolus etc. across uploaders, but Trio
// itself (see PumpHistoryStorage.swift's determineBolusEventType) uploads SMB and manually
// administered doses under eventType "SMB" / "External Insulin" specifically — neither contains
// "Bolus", so they need an explicit match alongside the substring heuristic. Shared by the chart,
// the basal strip, and the History tab so this hard-won distinction lives in exactly one place.
private const val BOLUS_EVENT_TYPE_SUBSTRING = "Bolus"
const val SMB_EVENT_TYPE = "SMB"
const val EXTERNAL_INSULIN_EVENT_TYPE = "External Insulin"
const val TEMP_BASAL_EVENT_TYPE = "Temp Basal"
private val EXACT_BOLUS_EVENT_TYPES = setOf(SMB_EVENT_TYPE, EXTERNAL_INSULIN_EVENT_TYPE)

fun isBolusEventType(eventType: String): Boolean =
    eventType.contains(BOLUS_EVENT_TYPE_SUBSTRING, ignoreCase = true) ||
        EXACT_BOLUS_EVENT_TYPES.any { it.equals(eventType, ignoreCase = true) }

fun isSmbEventType(eventType: String): Boolean = eventType.equals(SMB_EVENT_TYPE, ignoreCase = true)

fun isExternalInsulinEventType(eventType: String): Boolean =
    eventType.equals(EXTERNAL_INSULIN_EVENT_TYPE, ignoreCase = true)

fun isTempBasalEventType(eventType: String): Boolean = eventType == TEMP_BASAL_EVENT_TYPE

// Trio uploads profile overrides under eventType "Exercise" (NightscoutExercise/
// OverrideStored.EventType.nsExercise — a plain "Exercise" label is Nightscout's own long-
// standing convention for this, inherited from AndroidAPS/Loop, not a Trio-specific choice) and
// temp targets under "Temporary Target" (NightscoutTreatment/PumpEventStored.EventType.nsTempTarget).
// Neither NS-uploaded record carries the user-visible name anywhere but `notes` — see
// TreatmentDto's doc comment — and only the temp target one carries a target value at all.
const val OVERRIDE_EVENT_TYPE = "Exercise"
const val TEMP_TARGET_EVENT_TYPE = "Temporary Target"
val ADJUSTMENT_EVENT_TYPES = listOf(OVERRIDE_EVENT_TYPE, TEMP_TARGET_EVENT_TYPE)

fun isOverrideEventType(eventType: String): Boolean = eventType == OVERRIDE_EVENT_TYPE

fun isTempTargetEventType(eventType: String): Boolean = eventType == TEMP_TARGET_EVENT_TYPE

fun isAdjustmentEventType(eventType: String): Boolean =
    isOverrideEventType(eventType) || isTempTargetEventType(eventType)
