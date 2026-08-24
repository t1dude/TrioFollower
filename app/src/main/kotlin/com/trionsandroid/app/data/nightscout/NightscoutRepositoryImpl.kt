package com.trionsandroid.app.data.nightscout

import com.trionsandroid.app.data.local.GlucoseEntryDao
import com.trionsandroid.app.data.local.TreatmentDao
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.remote.NightscoutServiceFactory
import com.trionsandroid.app.data.settings.SecureTokenStore
import com.trionsandroid.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
) : NightscoutRepository {

    override fun observeGlucoseEntries(sinceMillis: Long): Flow<List<GlucoseReading>> =
        glucoseEntryDao.observeSince(sinceMillis).map { entries -> entries.map { it.toDomain() } }

    override fun observeTreatments(sinceMillis: Long): Flow<List<Treatment>> =
        treatmentDao.observeSince(sinceMillis).map { treatments -> treatments.map { it.toDomain() } }

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
            val storedEntries = entries.result.mapNotNull { it.toEntity() }
            glucoseEntryDao.upsertAll(storedEntries)
            diagnosticLogger.log(
                TAG,
                "Entries: status=${entries.status} received=${entries.result.size} stored=${storedEntries.size}",
            )

            val treatments = api.getTreatments(bearerToken = bearer, sinceMillis = sinceMillis)
            val storedTreatments = treatments.result.mapNotNull { it.toEntity() }
            treatmentDao.upsertAll(storedTreatments)
            diagnosticLogger.log(
                TAG,
                "Treatments: status=${treatments.status} received=${treatments.result.size} stored=${storedTreatments.size}",
            )

            val cutoffMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(RETENTION_HOURS)
            glucoseEntryDao.deleteOlderThan(cutoffMillis)
            treatmentDao.deleteOlderThan(cutoffMillis)
        }.onFailure { e ->
            diagnosticLogger.logError(TAG, "refresh() failed", e)
        }
    }

    private companion object {
        const val TAG = "NightscoutRepository"

        // Keep a week of local cache so the future scrollable graph can pan back without
        // re-fetching, well beyond what a single refresh's lookback window covers.
        const val RETENTION_HOURS = 24L * 7
    }
}
