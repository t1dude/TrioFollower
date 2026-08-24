package com.trionsandroid.app.data.nightscout

import com.trionsandroid.app.data.local.GlucoseEntryEntity
import com.trionsandroid.app.data.local.TreatmentEntity
import com.trionsandroid.app.data.remote.EntryDto
import com.trionsandroid.app.data.remote.TreatmentDto
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
