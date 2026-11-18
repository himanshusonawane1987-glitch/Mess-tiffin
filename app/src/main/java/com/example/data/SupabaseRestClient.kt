package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class PostgrestErrorBody(
    val message: String? = null,
    val details: String? = null,
    val hint: String? = null,
    val code: String? = null
)

class SupabaseRestClient(
    private val httpClient: OkHttpClient,
    private val authClient: SupabaseAuthClient,
    val json: Json
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun <T> select(
        table: String,
        queryParams: List<Pair<String, String>>,
        deserializer: DeserializationStrategy<List<T>>
    ): Result<List<T>> = executeWithAuth { token ->
        val urlBuilder = "${SupabaseConfig.supabaseUrl}/rest/v1/$table".toHttpUrl().newBuilder()
        queryParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()
        val raw = performCall(request)
        json.decodeFromString(deserializer, raw)
    }

    suspend fun <T> insert(
        table: String,
        bodyJson: String,
        deserializer: DeserializationStrategy<List<T>>,
        upsertOnConflict: String? = null
    ): Result<T> = executeWithAuth { token ->
        val urlBuilder = "${SupabaseConfig.supabaseUrl}/rest/v1/$table".toHttpUrl().newBuilder()
        if (!upsertOnConflict.isNullOrBlank()) {
            urlBuilder.addQueryParameter("on_conflict", upsertOnConflict)
        }
        val prefer = if (!upsertOnConflict.isNullOrBlank()) {
            "resolution=merge-duplicates,return=representation"
        } else {
            "return=representation"
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Prefer", prefer)
            .post(bodyJson.toRequestBody(jsonMedia))
            .build()
        val raw = performCall(request)
        val list = json.decodeFromString(deserializer, raw)
        list.firstOrNull() ?: error("Empty response returned from $table")
    }

    suspend fun <T> update(
        table: String,
        filters: List<Pair<String, String>>,
        bodyJson: String,
        deserializer: DeserializationStrategy<List<T>>
    ): Result<List<T>> = executeWithAuth { token ->
        val urlBuilder = "${SupabaseConfig.supabaseUrl}/rest/v1/$table".toHttpUrl().newBuilder()
        filters.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Prefer", "return=representation")
            .patch(bodyJson.toRequestBody(jsonMedia))
            .build()
        val raw = performCall(request)
        json.decodeFromString(deserializer, raw)
    }

    suspend fun <T> rpc(
        functionName: String,
        paramsJson: String,
        deserializer: DeserializationStrategy<T>
    ): Result<T> = executeWithAuth { token ->
        val request = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/rest/v1/rpc/$functionName")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .post(paramsJson.toRequestBody(jsonMedia))
            .build()
        val raw = performCall(request)
        json.decodeFromString(deserializer, raw)
    }

    private suspend fun <T> executeWithAuth(block: suspend (String) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext Result.failure(
                    IllegalStateException("Supabase URL / Anon Key not configured in AI Studio Secrets.")
                )
            }
            val tokenRes = authClient.getValidAccessToken(forceRefresh = false)
            val token = tokenRes.getOrElse { return@withContext Result.failure(it) }
            try {
                Result.success(block(token))
            } catch (e: UnauthorizedException) {
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
                val parsedMsg = try {
                    val err = json.decodeFromString(PostgrestErrorBody.serializer(), raw)
                    listOfNotNull(err.message, err.hint).joinToString(" — ").ifBlank { null }
                } catch (_: Exception) {
                    null
                }
                throw IllegalStateException(parsedMsg ?: "Request failed (${resp.code})")
            }
            raw
        }
    }

    private class UnauthorizedException : RuntimeException("401 Unauthorized")
}
