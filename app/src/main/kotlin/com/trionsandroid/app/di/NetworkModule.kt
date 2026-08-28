package com.trionsandroid.app.di

import com.trionsandroid.app.data.logging.DiagnosticHttpLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(diagnosticHttpLogger: DiagnosticHttpLogger): OkHttpClient {
        val logging = HttpLoggingInterceptor(diagnosticHttpLogger).apply {
            // BODY was useful while chasing Nightscout's various JSON quirks earlier on, but a
            // single devicestatus response body alone can run past a megabyte (hundreds of
            // determination records, each with its own predBGs arrays) — logged on every
            // background refresh, that blew through the diagnostic log's rotation cap within
            // one or two cycles and wiped out everything else, including whether background
            // sync had been running at all. decodeResilient() already logs the raw JSON for any
            // record that specifically fails to parse, which is the actual case BODY was for.
            level = HttpLoggingInterceptor.Level.BASIC
            // Never write the bearer JWT itself into the log, even though it's short-lived.
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
