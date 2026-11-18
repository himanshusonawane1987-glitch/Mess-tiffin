package com.example.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

@Serializable
data class SupabaseAuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    val user: SupabaseAuthUser
)

@Serializable
data class SupabaseAuthUser(
    val id: String,
    val email: String? = null
)

@Serializable
private data class IdTokenBody(
    val provider: String = "google",
    @SerialName("id_token") val idToken: String
)

@Serializable
private data class PkceExchangeBody(
    @SerialName("auth_code") val authCode: String,
    @SerialName("code_verifier") val codeVerifier: String
)

@Serializable
private data class RefreshBody(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
private data class SupabaseErrorBody(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val message: String? = null,
    val msg: String? = null
)

class SupabaseAuthClient(
    private val httpClient: OkHttpClient,
    private val sessionStore: SessionStore,
    private val json: Json
) {
    private val refreshMutex = Mutex()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun buildGoogleOAuthAuthorizeUrl(): Uri {
        val verifier = generateCodeVerifier()
        sessionStore.savePkceVerifier(verifier)
        val challenge = generateCodeChallenge(verifier)
        return Uri.parse("${SupabaseConfig.supabaseUrl}/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", SupabaseConfig.OAUTH_REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .build()
    }

    suspend fun handleOAuthCallback(uri: Uri): Result<StoredSession> {
        val errorDesc = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        if (!errorDesc.isNullOrBlank()) {
            return Result.failure(IllegalStateException(errorDesc))
        }

        val code = uri.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            val verifier = sessionStore.consumePkceVerifier()
                ?: return Result.failure(IllegalStateException("Missing PKCE verifier; please retry Google Sign-In."))
            return exchangePkceCode(code, verifier)
        }

        // Implicit flow fallback if Supabase project is configured without PKCE
        val fragment = uri.fragment ?: ""
        if (fragment.contains("access_token=")) {
            val params = parseUrlParams(fragment)
            val access = params["access_token"]
            val refresh = params["refresh_token"]
            val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
            if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
                return fetchUserAndSaveSession(access, refresh, expiresIn)
            }
        }

        return Result.failure(IllegalStateException("Invalid OAuth callback parameters."))
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<StoredSession> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = json.encodeToString(IdTokenBody.serializer(), IdTokenBody(idToken = idToken))
            val request = Request.Builder()
                .url("${SupabaseConfig.supabaseUrl}/auth/v1/token?grant_type=id_token")
                .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()
            executeAuthRequest(request)
        }
    }

    suspend fun getValidAccessToken(forceRefresh: Boolean = false): Result<String> = refreshMutex.withLock {
        val current = sessionStore.getSession()
            ?: return@withLock Result.failure(IllegalStateException("Signed out"))
        val nowSec = Instant.now().epochSecond
        if (!forceRefresh && current.expiresAtEpochSec > nowSec + 60) {
            return@withLock Result.success(current.accessToken)
        }
        return@withLock refreshSession(current.refreshToken).map { it.accessToken }
    }

    suspend fun getCurrentSession(): StoredSession? = sessionStore.getSession()

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        val current = sessionStore.getSession()
        runCatching {
            if (current != null && SupabaseConfig.isConfigured) {
                val request = Request.Builder()
                    .url("${SupabaseConfig.supabaseUrl}/auth/v1/logout")
                    .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                    .addHeader("Authorization", "Bearer ${current.accessToken}")
                    .post("".toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(request).execute().use { }
            }
        }
        sessionStore.clearAll()
        Result.success(Unit)
    }

    private suspend fun exchangePkceCode(code: String, verifier: String): Result<StoredSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bodyJson = json.encodeToString(
                    PkceExchangeBody.serializer(),
                    PkceExchangeBody(authCode = code, codeVerifier = verifier)
                )
                val request = Request.Builder()
                    .url("${SupabaseConfig.supabaseUrl}/auth/v1/token?grant_type=pkce")
                    .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
                executeAuthRequest(request)
            }
        }

    private suspend fun refreshSession(refreshToken: String): Result<StoredSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bodyJson = json.encodeToString(
                    RefreshBody.serializer(),
                    RefreshBody(refreshToken = refreshToken)
                )
                val request = Request.Builder()
                    .url("${SupabaseConfig.supabaseUrl}/auth/v1/token?grant_type=refresh_token")
                    .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
                executeAuthRequest(request)
            }.onFailure {
                // If refresh fails due to revoked session, clear stale tokens
                if (it.message?.contains("Invalid Refresh Token", ignoreCase = true) == true ||
                    it.message?.contains("refresh_token_not_found", ignoreCase = true) == true
                ) {
                    sessionStore.clearAll()
                }
            }
        }

    private suspend fun fetchUserAndSaveSession(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long
    ): Result<StoredSession> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${SupabaseConfig.supabaseUrl}/auth/v1/user")
                .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val user = httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error(extractErrorMessage(raw, resp.code))
                json.decodeFromString(SupabaseAuthUser.serializer(), raw)
            }
            val stored = StoredSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochSec = Instant.now().epochSecond + expiresIn,
                userId = user.id,
                userEmail = user.email.orEmpty()
            )
            sessionStore.saveSession(stored)
            stored
        }
    }

    private suspend fun executeAuthRequest(request: Request): StoredSession {
        val rawResponse = httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(extractErrorMessage(raw, resp.code))
            }
            raw
        }
        val parsed = json.decodeFromString(SupabaseAuthResponse.serializer(), rawResponse)
        val stored = StoredSession(
            accessToken = parsed.accessToken,
            refreshToken = parsed.refreshToken,
            expiresAtEpochSec = Instant.now().epochSecond + parsed.expiresIn,
            userId = parsed.user.id,
            userEmail = parsed.user.email.orEmpty()
        )
        sessionStore.saveSession(stored)
        return stored
    }

    private fun extractErrorMessage(raw: String, statusCode: Int): String {
        return try {
            val err = json.decodeFromString(SupabaseErrorBody.serializer(), raw)
            err.errorDescription ?: err.message ?: err.msg ?: err.error ?: "Auth failed ($statusCode)"
        } catch (_: Exception) {
            "Auth failed ($statusCode)"
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun parseUrlParams(query: String): Map<String, String> {
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) pair.substring(0, idx) to Uri.decode(pair.substring(idx + 1)) else null
        }.toMap()
    }
}
