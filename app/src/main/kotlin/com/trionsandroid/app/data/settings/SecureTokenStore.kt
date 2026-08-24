package com.trionsandroid.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the Nightscout access token in an encrypted (Keystore-backed) preferences file. */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "trio_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
    }

    suspend fun setAccessToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit { putString(KEY_ACCESS_TOKEN, token) }
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
    }
}
