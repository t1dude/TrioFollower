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
import kotlin.math.roundToInt
import java.time.OffsetDateTime

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
    // A pump block without a `reservoir` key is Omnipod's "50+, level unknown": Trio sends nil
    // instead of the 0xDEADBEEF sentinel. The sentinel check is a fallback for other uploaders.
    val reservoir = when {
        pump == null -> null
        pump.reservoir == null -> Double.POSITIVE_INFINITY
        pump.reservoir == RESERVOIR_UNKNOWN_FULL_SENTINEL -> Double.POSITIVE_INFINITY
        else -> pump.reservoir
    }
    // suggested is always present; enacted only when a dose was sent.
    val reason = openaps?.suggested?.reason ?: openaps?.enacted?.reason
    // Forecast from the same determination, anchored at deliverAt (as Trio does).
    val determination = openaps?.suggested?.takeIf { it.predBGs != null } ?: openaps?.enacted
    val predictions = determination?.predBGs
    val hasForecast = predictions != null &&
        listOf(predictions.iob, predictions.zt, predictions.cob, predictions.uam).any { !it.isNullOrEmpty() }
    val forecastStart = if (hasForecast) {
        parseIsoMillis(determination?.deliverAt) ?: parseIsoMillis(determination?.timestamp) ?: dateMillis
    } else {
        null
    }
    val eventualBg = (openaps?.suggested?.eventualBG ?: openaps?.enacted?.eventualBG)?.roundToInt()
    if (iob == null && cob == null && reservoir == null && reason == null && !hasForecast && eventualBg == null) return null
    return DeviceStatusEntity(
        id = stableId,
        dateMillis = dateMillis,
        iobUnits = iob,
        cobGrams = cob,
        reservoirUnits = reservoir,
        reason = reason,
        eventualBgMgDl = eventualBg,
        forecastStartMillis = forecastStart,
        predIob = predictions?.iob.toCsv(),
        predZt = predictions?.zt.toCsv(),
        predCob = predictions?.cob.toCsv(),
        predUam = predictions?.uam.toCsv(),
    )
}

private fun parseIsoMillis(value: String?): Long? {
    if (value == null) return null
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()
}

private fun List<Int>?.toCsv(): String? = this?.takeIf { it.isNotEmpty() }?.joinToString(",")

private fun String?.toIntList(): List<Int>? =
    this?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }

fun DeviceStatusEntity.toForecast(): Forecast? {
    val start = forecastStartMillis ?: return null
    val series = buildMap {
        predIob.toIntList()?.let { put(ForecastType.IOB, it) }
        predZt.toIntList()?.let { put(ForecastType.ZT, it) }
        predCob.toIntList()?.let { put(ForecastType.COB, it) }
        predUam.toIntList()?.let { put(ForecastType.UAM, it) }
    }
    return if (series.isEmpty()) null else Forecast(start, series)
}

fun DeviceStatusEntity.toDomain(): DeviceStatusPoint = DeviceStatusPoint(
    timestamp = Instant.ofEpochMilli(dateMillis),
    iobUnits = iobUnits,
    cobGrams = cobGrams,
    reservoirUnits = reservoirUnits,
    eventualBgMgDl = eventualBgMgDl,
)

/** openaps.iob is an object in Trio's upload but may be an array elsewhere; falls back to the determination's IOB. */
private fun OpenApsStatusDto?.extractIobUnits(): Double? {
    val fromIobEntry = when (val element = this?.iob) {
        is JsonObject -> element["iob"]?.jsonPrimitive?.doubleOrNull
        is JsonArray -> element.firstOrNull()?.jsonObject?.get("iob")?.jsonPrimitive?.doubleOrNull
        else -> null
    }
    return fromIobEntry ?: this?.suggested?.iob ?: this?.enacted?.iob
}
