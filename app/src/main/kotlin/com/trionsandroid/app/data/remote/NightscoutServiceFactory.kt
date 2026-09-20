package com.trionsandroid.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Builds Retrofit clients for the Nightscout URL chosen in Settings. */
@Singleton
class NightscoutServiceFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    fun authApi(baseUrl: String): NightscoutAuthApi = retrofit(baseUrl).create(NightscoutAuthApi::class.java)

    fun dataApi(baseUrl: String): NightscoutDataApi = retrofit(baseUrl).create(NightscoutDataApi::class.java)

    private fun retrofit(baseUrl: String): Retrofit {
        val withScheme = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            baseUrl
        } else {
            "https://$baseUrl"
        }
        val normalizedUrl = if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
