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
            // BODY logging is off: one devicestatus response can exceed a megabyte and would flush the
            // diagnostic log. decodeResilient() logs the raw JSON of records that fail to parse.
            level = HttpLoggingInterceptor.Level.BASIC
            // Never log the bearer token.
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
