package com.trionsandroid.app.data.nightscout

import com.trionsandroid.app.data.local.GlucoseEntryEntity
import com.trionsandroid.app.data.local.TreatmentEntity
import com.trionsandroid.app.data.remote.BasalScheduleEntryDto
import com.trionsandroid.app.data.remote.EntryDto
import com.trionsandroid.app.data.remote.ProfileDocumentDto
import com.trionsandroid.app.data.remote.ProfileStoreEntryDto
import com.trionsandroid.app.data.remote.TreatmentDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
    val dateValue = date ?: return null
    val type = eventType ?: return null
    return TreatmentEntity(
        id = stableId,
        dateMillis = dateValue.toLong(),
        eventType = type,
        insulinUnits = insulin,
        carbsGrams = carbs,
        durationMinutes = duration,
        basalRateUnitsPerHour = absolute,
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
