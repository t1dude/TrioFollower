package com.trionsandroid.app.data.nightscout

import java.time.Instant
import com.trionsandroid.app.data.local.DeviceStatusDao
import com.trionsandroid.app.data.local.TreatmentEntity
import com.trionsandroid.app.data.local.GlucoseEntryDao
import com.trionsandroid.app.data.local.TreatmentDao
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.remote.DeviceStatusDto
import com.trionsandroid.app.data.remote.EntryDto
import com.trionsandroid.app.data.remote.NightscoutServiceFactory
import com.trionsandroid.app.data.remote.ProfileDocumentDto
import com.trionsandroid.app.data.remote.TreatmentDto
import com.trionsandroid.app.data.settings.SecureTokenStore
import com.trionsandroid.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import com.trionsandroid.app.widget.WidgetUpdater
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NightscoutRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureTokenStore: SecureTokenStore,
    private val authManager: NightscoutAuthManager,
    private val serviceFactory: NightscoutServiceFactory,
    private val glucoseEntryDao: GlucoseEntryDao,
    private val treatmentDao: TreatmentDao,
    private val deviceStatusDao: DeviceStatusDao,
    private val widgetUpdater: WidgetUpdater,
    private val diagnosticLogger: DiagnosticLogger,
    private val json: Json,
) : NightscoutRepository {

    private val insulinProfile = MutableStateFlow<InsulinProfile?>(null)

    override fun observeGlucoseEntries(sinceMillis: Long): Flow<List<GlucoseReading>> =
        glucoseEntryDao.observeSince(sinceMillis).map { entries -> entries.map { it.toDomain() } }

    override fun observeTreatments(sinceMillis: Long): Flow<List<Treatment>> =
        treatmentDao.observeSince(sinceMillis).map { treatments ->
            treatments.map { it.toDomain() }.withOverlappingAdjustmentsClipped()
        }

    override fun observeDeviceStatus(sinceMillis: Long): Flow<List<DeviceStatusPoint>> =
        deviceStatusDao.observeSummariesSince(sinceMillis).map { points -> points.map { it.toDomain() } }

    override fun observeLatestForecast(): Flow<Forecast?> =
        deviceStatusDao.observeLatestForecast().map { it?.toForecast() }

    override suspend fun getReasoningForReading(readingTimestamp: Instant): Reasoning? {
        val readingMillis = readingTimestamp.toEpochMilli()
        val entity = deviceStatusDao.firstWithReasonBetween(
            fromMillis = readingMillis - REASONING_SLACK_BEFORE_MILLIS,
            toMillis = readingMillis + REASONING_WINDOW_AFTER_MILLIS,
        ) ?: return null
        return Reasoning(Instant.ofEpochMilli(entity.dateMillis), entity.reason ?: return null)
    }

    override fun observeInsulinProfile(): Flow<InsulinProfile?> = insulinProfile.asStateFlow()

    override suspend fun refresh(lookbackHours: Int): Result<Unit> {
        diagnosticLogger.log(TAG, "refresh() starting, lookbackHours=$lookbackHours")
        return runCatching {
            val settings = settingsRepository.settings.first()
            val baseUrl = settings.nightscoutUrl
            val accessToken = secureTokenStore.getAccessToken()
            require(baseUrl.isNotBlank() && accessToken.isNotBlank()) {
                "Set a Nightscout URL and access token in Settings first"
            }

            val bearer = authManager.getBearerToken(baseUrl, accessToken)
            val api = serviceFactory.dataApi(baseUrl)
            val sinceMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(lookbackHours.toLong())

            val entries = api.getEntries(bearerToken = bearer, sinceMillis = sinceMillis)
            val entryDtos = decodeResilient<EntryDto>("$TAG.Entries", entries.result)
            val storedEntries = entryDtos.mapNotNull { it.toEntity() }
            glucoseEntryDao.upsertAll(storedEntries)
            diagnosticLogger.log(
                TAG,
                "Entries: status=${entries.status} received=${entries.result.size} " +
                    "decoded=${entryDtos.size} stored=${storedEntries.size}",
            )

            // Query by both `date` and `created_at`: older uploads may lack an indexed `date`.
            // Results can overlap, so merge by identifier.
            val treatmentsByDate = api.getTreatments(bearerToken = bearer, sinceMillis = sinceMillis)
            val treatmentsByCreatedAt = api.getTreatmentsByCreatedAt(bearerToken = bearer, sinceMillis = sinceMillis)
            val treatmentDtosByDate = decodeResilient<TreatmentDto>("$TAG.Treatments", treatmentsByDate.result)
            val treatmentDtosByCreatedAt = decodeResilient<TreatmentDto>("$TAG.Treatments", treatmentsByCreatedAt.result)
            val mergedTreatmentDtos = (treatmentDtosByDate + treatmentDtosByCreatedAt).distinctBy { it.stableId }
            val storedTreatments = mergedTreatmentDtos.mapNotNull { it.toEntity() }.notInFuture()
            treatmentDao.upsertAll(storedTreatments)
            diagnosticLogger.log(
                TAG,
                "Treatments: byDate=${treatmentsByDate.result.size} byCreatedAt=${treatmentsByCreatedAt.result.size} " +
                    "merged=${mergedTreatmentDtos.size} stored=${storedTreatments.size}",
            )

            val cutoffMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(RETENTION_HOURS)
            glucoseEntryDao.deleteOlderThan(cutoffMillis)
            treatmentDao.deleteOlderThan(cutoffMillis)
            // Purge future-dated rows cached before they were filtered on the way in.
            treatmentDao.deleteNewerThan(futureCutoffMillis())
            deviceStatusDao.deleteOlderThan(cutoffMillis)

            // Profile, devicestatus, lifecycle and adjustment fetches below are isolated so a failure in
            // one doesn't fail the whole refresh.
            runCatching {
                val profileEnvelope = api.getProfile(bearerToken = bearer)
                decodeResilient<ProfileDocumentDto>("$TAG.Profile", profileEnvelope.result)
                    .firstOrNull()
                    ?.toInsulinProfile()
            }.onSuccess { profile ->
                if (profile != null) {
                    insulinProfile.value = profile
                    diagnosticLogger.log(
                        TAG,
                        "Profile: dia=${profile.diaHours}h basalEntries=${profile.basalSchedule.size}",
                    )
                } else {
                    diagnosticLogger.log(TAG, "Profile: no usable profile document found")
                }
            }.onFailure { e ->
                diagnosticLogger.logError(TAG, "Profile fetch failed (non-fatal)", e)
            }

            // Devicestatus is queried by date and created_at, like treatments.
            runCatching {
                val byDate = api.getDeviceStatus(bearerToken = bearer, sinceMillis = sinceMillis)
                val byCreatedAt = api.getDeviceStatusByCreatedAt(bearerToken = bearer, sinceMillis = sinceMillis)
                val dtosByDate = decodeResilient<DeviceStatusDto>("$TAG.DeviceStatus", byDate.result)
                val dtosByCreatedAt = decodeResilient<DeviceStatusDto>("$TAG.DeviceStatus", byCreatedAt.result)
                val merged = (dtosByDate + dtosByCreatedAt).distinctBy { it.stableId }
                val storedDeviceStatus = merged.mapNotNull { it.toEntity() }
                deviceStatusDao.upsertAll(storedDeviceStatus)
                Triple(byDate.result.size, byCreatedAt.result.size, storedDeviceStatus.size)
            }.onSuccess { (byDate, byCreatedAt, stored) ->
                diagnosticLogger.log(TAG, "DeviceStatus: byDate=$byDate byCreatedAt=$byCreatedAt stored=$stored")
            }.onFailure { e ->
                diagnosticLogger.logError(TAG, "DeviceStatus fetch failed (non-fatal)", e)
            }

            runCatching {
                val lookbackMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(LIFECYCLE_LOOKBACK_DAYS)
                val lifecycleDtos = LIFECYCLE_EVENT_TYPES.flatMap { eventType ->
                    val byDate = api.getLatestLifecycleEvent(
                        bearerToken = bearer,
                        eventType = eventType,
                        sinceMillis = lookbackMillis,
                        untilMillis = futureCutoffMillis(),
                    )
                    val byCreatedAt = api.getLatestLifecycleEventByCreatedAt(
                        bearerToken = bearer,
                        eventType = eventType,
                        sinceMillis = lookbackMillis,
                        untilMillis = futureCutoffMillis(),
                    )
                    decodeResilient<TreatmentDto>("$TAG.Lifecycle", byDate.result) +
                        decodeResilient<TreatmentDto>("$TAG.Lifecycle", byCreatedAt.result)
                }.distinctBy { it.stableId }
                val storedLifecycle = lifecycleDtos.mapNotNull { it.toEntity() }.notInFuture()
                treatmentDao.upsertAll(storedLifecycle)
                storedLifecycle.size
            }.onSuccess { count ->
                diagnosticLogger.log(TAG, "Lifecycle events: stored=$count")
            }.onFailure { e ->
                diagnosticLogger.logError(TAG, "Lifecycle events fetch failed (non-fatal)", e)
            }

            runCatching {
                val lookbackMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ADJUSTMENT_LOOKBACK_DAYS)
                val adjustmentDtos = ADJUSTMENT_EVENT_TYPES.flatMap { eventType ->
                    val byDate = api.getAdjustments(
                        bearerToken = bearer,
                        eventType = eventType,
                        sinceMillis = lookbackMillis,
                    )
                    val byCreatedAt = api.getAdjustmentsByCreatedAt(
                        bearerToken = bearer,
                        eventType = eventType,
                        sinceMillis = lookbackMillis,
                    )
                    decodeResilient<TreatmentDto>("$TAG.Adjustments", byDate.result) +
                        decodeResilient<TreatmentDto>("$TAG.Adjustments", byCreatedAt.result)
                }.distinctBy { it.stableId }
                val storedAdjustments = adjustmentDtos.mapNotNull { it.toEntity() }.notInFuture()
                treatmentDao.upsertAll(storedAdjustments)
                // Trio replaces an ended override's entry under a new id, so drop the old ones.
                treatmentDao.deleteStaleAdjustments(
                    eventTypes = ADJUSTMENT_EVENT_TYPES,
                    sinceMillis = lookbackMillis,
                    keepIds = storedAdjustments.map { it.id },
                )
                storedAdjustments.size
            }.onSuccess { count ->
                diagnosticLogger.log(TAG, "Adjustments: stored=$count")
            }.onFailure { e ->
                diagnosticLogger.logError(TAG, "Adjustments fetch failed (non-fatal)", e)
            }
            // Redraw the home screen widgets with the new data.
            runCatching { widgetUpdater.updateAll() }
            Unit
        }.onFailure { e ->
            diagnosticLogger.logError(TAG, "refresh() failed", e)
        }
    }

    /** Decodes each element on its own so one malformed record doesn't drop the whole batch. */
    private inline fun <reified T> decodeResilient(tag: String, elements: List<JsonElement>): List<T> {
        val decoded = mutableListOf<T>()
        for (element in elements) {
            try {
                decoded.add(json.decodeFromJsonElement<T>(element))
            } catch (e: SerializationException) {
                diagnosticLogger.logError(tag, "Failed to decode record: $element", e)
            }
        }
        return decoded
    }

    /** Treatments dated far in the future (e.g. year 2162) are bogus and would hide the real latest event. */
    private fun futureCutoffMillis() = System.currentTimeMillis() + FUTURE_TOLERANCE_MILLIS

    private fun List<TreatmentEntity>.notInFuture(): List<TreatmentEntity> {
        val cutoff = futureCutoffMillis()
        return filter { it.dateMillis <= cutoff }
    }

    private companion object {
        // Allowance for clock skew.
        const val FUTURE_TOLERANCE_MILLIS = 10 * 60_000L
        const val TAG = "NightscoutRepository"

        // Long enough for the chart history and for lifecycle events fetched below.
        const val RETENTION_HOURS = 24L * 30
        // A determination belongs to the reading it follows: a little slack before, one reading interval after.
        const val REASONING_SLACK_BEFORE_MILLIS = 60_000L
        const val REASONING_WINDOW_AFTER_MILLIS = 270_000L
        const val LIFECYCLE_LOOKBACK_DAYS = 30L
        val LIFECYCLE_EVENT_TYPES = listOf("Site Change", "Sensor Start")
        const val ADJUSTMENT_LOOKBACK_DAYS = 30L
    }
}
