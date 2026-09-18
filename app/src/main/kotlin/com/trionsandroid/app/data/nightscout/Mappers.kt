package com.trionsandroid.app.data.nightscout

import com.trionsandroid.app.data.local.DeviceStatusEntity
import com.trionsandroid.app.data.local.GlucoseEntryEntity
import com.trionsandroid.app.data.local.TreatmentEntity
import com.trionsandroid.app.data.remote.BasalScheduleEntryDto
import com.trionsandroid.app.data.remote.DeviceStatusDto
import com.trionsandroid.app.data.remote.EntryDto
import com.trionsandroid.app.data.remote.OpenApsStatusDto
import com.trionsandroid.app.data.remote.ProfileDocumentDto
import com.trionsandroid.app.data.remote.ProfileStoreEntryDto
import com.trionsandroid.app.data.remote.TreatmentDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

fun EntryDto.toEntity(): GlucoseEntryEntity? {
    val sgvValue = sgv ?: return null
    return GlucoseEntryEntity(
        id = stableId,
        dateMillis = date.toLong(),
        sgvMgDl = sgvValue,
        direction = direction,
        device = device,
    )
}

fun GlucoseEntryEntity.toDomain(): GlucoseReading = GlucoseReading(
    id = id,
    timestamp = Instant.ofEpochMilli(dateMillis),
    mgDl = sgvMgDl,
    trend = GlucoseTrend.fromDirection(direction),
)

fun TreatmentDto.toEntity(): TreatmentEntity? {
    val type = eventType ?: return null
    val dateMillis = date?.toLong()
        ?: createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: return null
    return TreatmentEntity(
        id = stableId,
        dateMillis = dateMillis,
        eventType = type,
        insulinUnits = insulin,
        carbsGrams = carbs,
        durationMinutes = duration,
        basalRateUnitsPerHour = absolute,
        notes = notes,
        targetMgDl = targetTop,
    )
}

fun TreatmentEntity.toDomain(): Treatment = Treatment(
    id = id,
    timestamp = Instant.ofEpochMilli(dateMillis),
    eventType = eventType,
    insulinUnits = insulinUnits,
    carbsGrams = carbsGrams,
    durationMinutes = durationMinutes,
    basalRateUnitsPerHour = basalRateUnitsPerHour,
    notes = notes,
    targetMgDl = targetMgDl,
)

private const val DEFAULT_DIA_HOURS = 6.0

fun ProfileDocumentDto.toInsulinProfile(): InsulinProfile? {
    val active: ProfileStoreEntryDto = store?.let { it[defaultProfile] ?: it.values.firstOrNull() }
        ?: basal?.let { ProfileStoreEntryDto(dia = dia, basal = it) }
        ?: return null

    val schedule = active.basal.orEmpty()
        .mapNotNull { it.toBasalScheduleEntry() }
        .sortedBy { it.secondsFromMidnight }
    if (schedule.isEmpty()) return null

    return InsulinProfile(diaHours = active.dia.toDiaHours(), basalSchedule = schedule)
}

private fun BasalScheduleEntryDto.toBasalScheduleEntry(): BasalScheduleEntry? {
    val rate = value ?: return null
    val seconds = timeAsSeconds ?: time?.let { parseTimeStringToSecondsOfDay(it) } ?: return null
    return BasalScheduleEntry(secondsFromMidnight = seconds, rateUnitsPerHour = rate)
}

private fun parseTimeStringToSecondsOfDay(time: String): Int? {
    val parts = time.split(":")
    if (parts.size < 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    return hours * 3600 + minutes * 60
}

/** Handles the common case (a plain number) and, rarely, a time-array like the basal schedule. */
private fun JsonElement?.toDiaHours(): Double = when (this) {
    null -> DEFAULT_DIA_HOURS
    is JsonPrimitive -> doubleOrNull ?: DEFAULT_DIA_HOURS
    is JsonArray -> lastOrNull()?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull ?: DEFAULT_DIA_HOURS
    else -> DEFAULT_DIA_HOURS
}

private const val RESERVOIR_UNKNOWN_FULL_SENTINEL = 3_735_928_559.0 // 0xDEADBEEF, see PumpStatusDto

fun DeviceStatusDto.toEntity(): DeviceStatusEntity? {
    val dateMillis = date?.toLong()
        ?: createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: return null
    val iob = openaps.extractIobUnits()
    val cob = openaps?.suggested?.cob ?: openaps?.enacted?.cob
    // A `pump` block with no `reservoir` key is Omnipod's actual "50+, exact level unknown"
    // signal on the wire — confirmed against Trio's NightscoutManager.swift upload code
    // (`reservoir: reservoir != 0xDEAD_BEEF ? reservoir : nil`): the local sentinel becomes
    // Swift `nil`, which is omitted from the uploaded JSON entirely, not sent as the literal
    // sentinel number. Treating "pump present, reservoir absent" as "no data" made the HUD fall
    // back to whatever old record last had a real number — sometimes days-stale, from a
    // previous pod. The literal-sentinel check stays as a defensive fallback for any uploader
    // that does send the raw number.
    val reservoir = when {
        pump == null -> null
        pump.reservoir == null -> Double.POSITIVE_INFINITY
        pump.reservoir == RESERVOIR_UNKNOWN_FULL_SENTINEL -> Double.POSITIVE_INFINITY
        else -> pump.reservoir
    }
    if (iob == null && cob == null && reservoir == null) return null
    return DeviceStatusEntity(
        id = stableId,
        dateMillis = dateMillis,
        iobUnits = iob,
        cobGrams = cob,
        reservoirUnits = reservoir,
    )
}

fun DeviceStatusEntity.toDomain(): DeviceStatusPoint = DeviceStatusPoint(
    timestamp = Instant.ofEpochMilli(dateMillis),
    iobUnits = iobUnits,
    cobGrams = cobGrams,
    reservoirUnits = reservoirUnits,
)

/**
 * openaps.iob is a single object in the shape Trio actually uploads, but Trio's own local
 * (pre-upload) storage keeps it as an array, and other uploaders may differ again — handle both
 * shapes, then fall back to the IOB carried on the suggested/enacted determination if the
 * dedicated iob entry is absent entirely.
 */
private fun OpenApsStatusDto?.extractIobUnits(): Double? {
    val fromIobEntry = when (val element = this?.iob) {
        is JsonObject -> element["iob"]?.jsonPrimitive?.doubleOrNull
        is JsonArray -> element.firstOrNull()?.jsonObject?.get("iob")?.jsonPrimitive?.doubleOrNull
        else -> null
    }
    return fromIobEntry ?: this?.suggested?.iob ?: this?.enacted?.iob
}
