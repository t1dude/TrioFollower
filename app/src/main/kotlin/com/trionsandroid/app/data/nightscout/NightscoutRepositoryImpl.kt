package com.trionsandroid.app.data.nightscout

import com.trionsandroid.app.data.local.GlucoseEntryDao
import com.trionsandroid.app.data.local.TreatmentDao
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
) : NightscoutRepository {

    override fun observeGlucoseEntries(sinceMillis: Long): Flow<List<GlucoseReading>> =
        glucoseEntryDao.observeSince(sinceMillis).map { entries -> entries.map { it.toDomain() } }

    override fun observeTreatments(sinceMillis: Long): Flow<List<Treatment>> =
        treatmentDao.observeSince(sinceMillis).map { treatments -> treatments.map { it.toDomain() } }

    override suspend fun refresh(lookbackHours: Int): Result<Unit> = runCatching {
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
        glucoseEntryDao.upsertAll(entries.result.mapNotNull { it.toEntity() })

        val treatments = api.getTreatments(bearerToken = bearer, sinceMillis = sinceMillis)
        treatmentDao.upsertAll(treatments.result.mapNotNull { it.toEntity() })

        val cutoffMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(RETENTION_HOURS)
        glucoseEntryDao.deleteOlderThan(cutoffMillis)
        treatmentDao.deleteOlderThan(cutoffMillis)
    }

    private companion object {
        // Keep a week of local cache so the future scrollable graph can pan back without
        // re-fetching, well beyond what a single refresh's lookback window covers.
        const val RETENTION_HOURS = 24L * 7
    }
}
