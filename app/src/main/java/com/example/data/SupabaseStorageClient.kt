package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class SignRequestBody(
    val expiresIn: Int = 3600
)

@Serializable
private data class SignResponseBody(
    val signedURL: String? = null,
    @SerialName("signedUrl") val signedUrlCamel: String? = null
)

@Serializable
private data class StorageErrorBody(
    val message: String? = null,
    val error: String? = null,
    val statusCode: String? = null
)

class SupabaseStorageClient(
    private val httpClient: OkHttpClient,
    private val authClient: SupabaseAuthClient,
    private val json: Json
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun uploadImage(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String = "image/jpeg"
    ): Result<String> = executeWithAuth { token ->
        val cleanPath = objectPath.trimStart('/')
        val request = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/storage/v1/object/$bucket/$cleanPath")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("x-upsert", "true")
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        performCall(request)
        cleanPath
    }

    suspend fun createSignedUrl(
        bucket: String,
        objectPath: String,
        expiresInSeconds: Int = 3600
    ): Result<String> = executeWithAuth { token ->
        val cleanPath = objectPath.trimStart('/')
        val body = json.encodeToString(SignRequestBody.serializer(), SignRequestBody(expiresInSeconds))
        val request = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/storage/v1/object/sign/$bucket/$cleanPath")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()
        val raw = performCall(request)
        val parsed = json.decodeFromString(SignResponseBody.serializer(), raw)
        val rawSigned = parsed.signedURL ?: parsed.signedUrlCamel
            ?: error("Missing signedURL in storage response")
        normalizeSignedUrl(rawSigned)
    }

    fun normalizeSignedUrl(rawSignedUrl: String): String {
        val base = SupabaseConfig.supabaseUrl.trimEnd('/')
        return when {
            rawSignedUrl.startsWith("http://") || rawSignedUrl.startsWith("https://") -> rawSignedUrl
            rawSignedUrl.startsWith("/storage/v1/") -> "$base$rawSignedUrl"
            rawSignedUrl.startsWith("/object/") -> "$base/storage/v1$rawSignedUrl"
            else -> "$base/storage/v1/${rawSignedUrl.trimStart('/')}"
        }
    }

    private suspend fun <T> executeWithAuth(block: suspend (String) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext Result.failure(
                    IllegalStateException("Supabase URL / Anon Key not configured in AI Studio Secrets.")
                )
            }
            val token = authClient.getValidAccessToken(forceRefresh = false)
                .getOrElse { return@withContext Result.failure(it) }
            try {
                Result.success(block(token))
            } catch (_: UnauthorizedException) {
                val refreshed = authClient.getValidAccessToken(forceRefresh = true)
                    .getOrElse { return@withContext Result.failure(it) }
                try {
                    Result.success(block(refreshed))
                } catch (retryErr: Exception) {
                    Result.failure(retryErr)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun performCall(request: Request): String {
        return httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.code == 401) throw UnauthorizedException()
            if (!resp.isSuccessful) {
                val msg = try {
                    val err = json.decodeFromString(StorageErrorBody.serializer(), raw)
                    err.message ?: err.error
                } catch (_: Exception) {
                    null
                }
                throw IllegalStateException(msg ?: "Storage request failed (${resp.code})")
            }
            raw
        }
    }

    private class UnauthorizedException : RuntimeException("401 Unauthorized")

    companion object {
        const val PAYMENT_PROOFS_BUCKET = "payment-proofs"
    }
}
