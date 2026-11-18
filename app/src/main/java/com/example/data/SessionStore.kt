package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AppLanguage
import kotlinx.coroutines.flow.Flow
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
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LANGUAGE_CHOSEN = booleanPreferencesKey("language_chosen_by_user")
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

    suspend fun saveLanguage(language: AppLanguage, markChosen: Boolean = true) {
        context.tiffinDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.dbValue
            if (markChosen) {
                prefs[Keys.LANGUAGE_CHOSEN] = true
            }
        }
    }

    suspend fun getLanguage(): AppLanguage {
        val raw = context.tiffinDataStore.data.map { it[Keys.APP_LANGUAGE] }.first()
        return AppLanguage.fromDb(raw)
    }

    suspend fun hasChosenLanguage(): Boolean {
        return context.tiffinDataStore.data.map { it[Keys.LANGUAGE_CHOSEN] ?: false }.first()
    }

    fun observeLanguage(): Flow<AppLanguage> {
        return context.tiffinDataStore.data.map { prefs ->
            AppLanguage.fromDb(prefs[Keys.APP_LANGUAGE])
        }
    }

    fun observeHasChosenLanguage(): Flow<Boolean> {
        return context.tiffinDataStore.data.map { prefs ->
            prefs[Keys.LANGUAGE_CHOSEN] ?: false
        }
    }

    suspend fun clearAll() {
        context.tiffinDataStore.edit { prefs ->
            val savedLang = prefs[Keys.APP_LANGUAGE]
            val savedChosen = prefs[Keys.LANGUAGE_CHOSEN]
            prefs.clear()
            if (!savedLang.isNullOrBlank()) prefs[Keys.APP_LANGUAGE] = savedLang
            if (savedChosen != null) prefs[Keys.LANGUAGE_CHOSEN] = savedChosen
        }
    }

    suspend fun clearLanguageForTesting() {
        context.tiffinDataStore.edit { prefs ->
            prefs.remove(Keys.APP_LANGUAGE)
            prefs.remove(Keys.LANGUAGE_CHOSEN)
        }
    }
}
