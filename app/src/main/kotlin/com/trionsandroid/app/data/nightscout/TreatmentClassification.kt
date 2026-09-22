package com.trionsandroid.app.data.nightscout

// Most bolus types contain "Bolus", but Trio uploads SMBs and manual doses as exactly "SMB" and
// "External Insulin", so those are matched explicitly.
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

// Trio uploads overrides as "Exercise" and temp targets as "Temporary Target". Names are only
// in `notes`, and only temp targets have a target value.
const val OVERRIDE_EVENT_TYPE = "Exercise"
const val TEMP_TARGET_EVENT_TYPE = "Temporary Target"
val ADJUSTMENT_EVENT_TYPES = listOf(OVERRIDE_EVENT_TYPE, TEMP_TARGET_EVENT_TYPE)

fun isOverrideEventType(eventType: String): Boolean = eventType == OVERRIDE_EVENT_TYPE

fun isTempTargetEventType(eventType: String): Boolean = eventType == TEMP_TARGET_EVENT_TYPE

fun isAdjustmentEventType(eventType: String): Boolean =
    isOverrideEventType(eventType) || isTempTargetEventType(eventType)

// Trio uploads a pump site (cannula) change as "Site Change" and a CGM sensor change as "Sensor
// Start". Shared between the sync layer (which fetches them) and DeviceLifecycle (which times them).
const val SITE_CHANGE_EVENT_TYPE = "Site Change"
const val SENSOR_START_EVENT_TYPE = "Sensor Start"
val LIFECYCLE_EVENT_TYPES = listOf(SITE_CHANGE_EVENT_TYPE, SENSOR_START_EVENT_TYPE)
