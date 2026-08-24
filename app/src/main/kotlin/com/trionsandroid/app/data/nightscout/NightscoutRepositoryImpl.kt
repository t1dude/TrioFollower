package com.trionsandroid.app.data.nightscout

import com.trionsandroid.app.data.local.GlucoseEntryDao
import com.trionsandroid.app.data.local.TreatmentDao
import com.trionsandroid.app.data.logging.DiagnosticLogger
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NightscoutRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureTokenStore: SecureTokenStore,
    private val authManager: NightscoutAuthManager,
    private val serviceFactory: NightscoutServiceFactory,
    private val glucoseEntryDao: GlucoseEntryDao,
    private val treatmentDao: TreatmentDao,
    private val diagnosticLogger: DiagnosticLogger,
    private val json: Json,
) : NightscoutRepository {

    private val insulinProfile = MutableStateFlow<InsulinProfile?>(null)

    override fun observeGlucoseEntries(sinceMillis: Long): Flow<List<GlucoseReading>> =
        glucoseEntryDao.observeSince(sinceMillis).map { entries -> entries.map { it.toDomain() } }

    override fun observeTreatments(sinceMillis: Long): Flow<List<Treatment>> =
        treatmentDao.observeSince(sinceMillis).map { treatments -> treatments.map { it.toDomain() } }

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

            val treatments = api.getTreatments(bearerToken = bearer, sinceMillis = sinceMillis)
            val treatmentDtos = decodeResilient<TreatmentDto>("$TAG.Treatments", treatments.result)
            val storedTreatments = treatmentDtos.mapNotNull { it.toEntity() }
            treatmentDao.upsertAll(storedTreatments)
            diagnosticLogger.log(
                TAG,
                "Treatments: status=${treatments.status} received=${treatments.result.size} " +
                    "decoded=${treatmentDtos.size} stored=${storedTreatments.size}",
            )

            val cutoffMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(RETENTION_HOURS)
            glucoseEntryDao.deleteOlderThan(cutoffMillis)
            treatmentDao.deleteOlderThan(cutoffMillis)

            // Isolated from the rest of refresh(): the profile is only needed for the insulin
            // overlay's basal schedule, and a hiccup fetching it shouldn't surface as a failed
            // refresh when the entries/treatments that matter more just updated fine.
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
            Unit
        }.onFailure { e ->
            diagnosticLogger.logError(TAG, "refresh() failed", e)
        }
    }

    /**
     * Decodes each result element independently instead of deserializing the whole array in
     * one shot, so one malformed record (a different Loop/AndroidAPS/Trio uploader writing an
     * unexpected shape into the same collection) doesn't throw away every other record in the
     * batch. Failures are logged with the offending raw JSON so they can all be fixed from a
     * single refresh instead of one crash at a time.
     */
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

    private companion object {
        const val TAG = "NightscoutRepository"

        // Keep a week of local cache so the future scrollable graph can pan back without
        // re-fetching, well beyond what a single refresh's lookback window covers.
        const val RETENTION_HOURS = 24L * 7
    }
}
