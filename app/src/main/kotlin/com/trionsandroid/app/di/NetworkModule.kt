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
            level = HttpLoggingInterceptor.Level.BODY
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
