package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tiffinDataStore: DataStore<Preferences> by preferencesDataStore(name = "tiffin_mess_prefs")

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val userId: String,
    val userEmail: String
)

class SessionStore(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at_epoch_sec")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val PKCE_VERIFIER = stringPreferencesKey("pkce_code_verifier")
        val CACHED_AUTH_CONTEXT = stringPreferencesKey("cached_auth_context_json")
        val CACHED_MENUS = stringPreferencesKey("cached_menus_json")
    }

    suspend fun saveSession(session: StoredSession) {
        context.tiffinDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.REFRESH_TOKEN] = session.refreshToken
            prefs[Keys.EXPIRES_AT] = session.expiresAtEpochSec
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_EMAIL] = session.userEmail
        }
    }

    suspend fun getSession(): StoredSession? {
        return context.tiffinDataStore.data.map { prefs ->
            val access = prefs[Keys.ACCESS_TOKEN]
            val refresh = prefs[Keys.REFRESH_TOKEN]
            val userId = prefs[Keys.USER_ID]
            if (access.isNullOrBlank() || refresh.isNullOrBlank() || userId.isNullOrBlank()) {
                null
            } else {
                StoredSession(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAtEpochSec = prefs[Keys.EXPIRES_AT] ?: 0L,
                    userId = userId,
                    userEmail = prefs[Keys.USER_EMAIL] ?: ""
                )
            }
        }.first()
    }

    suspend fun savePkceVerifier(verifier: String) {
        context.tiffinDataStore.edit { prefs ->
            prefs[Keys.PKCE_VERIFIER] = verifier
        }
    }

    suspend fun consumePkceVerifier(): String? {
        var verifier: String? = null
        context.tiffinDataStore.edit { prefs ->
            verifier = prefs[Keys.PKCE_VERIFIER]
            prefs.remove(Keys.PKCE_VERIFIER)
        }
        return verifier
    }

    suspend fun saveCachedAuthContext(json: String) {
        context.tiffinDataStore.edit { prefs ->
            prefs[Keys.CACHED_AUTH_CONTEXT] = json
        }
    }

    suspend fun getCachedAuthContext(): String? {
        return context.tiffinDataStore.data.map { it[Keys.CACHED_AUTH_CONTEXT] }.first()
    }

    suspend fun saveCachedMenus(json: String) {
        context.tiffinDataStore.edit { prefs ->
            prefs[Keys.CACHED_MENUS] = json
        }
    }

    suspend fun getCachedMenus(): String? {
        return context.tiffinDataStore.data.map { it[Keys.CACHED_MENUS] }.first()
    }

    suspend fun clearAll() {
        context.tiffinDataStore.edit { it.clear() }
    }
}
