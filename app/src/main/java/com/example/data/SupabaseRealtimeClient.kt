package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

data class RealtimeChangeEvent(
    val table: String,
    val eventType: String
)

class SupabaseRealtimeClient(
    private val httpClient: OkHttpClient,
    private val authClient: SupabaseAuthClient
) {
    /**
     * Opens a Phoenix WebSocket channel listening to postgres_changes for the specified tables
     * scoped by mess_id. Automatically sends heartbeats and closes cleanly when collected Flow ends.
     */
    fun observeMessChanges(
        messId: String,
        tables: List<String>
    ): Flow<RealtimeChangeEvent> = callbackFlow {
        if (!SupabaseConfig.isConfigured) {
            close()
            return@callbackFlow
        }
        val token = authClient.getValidAccessToken().getOrNull()
        if (token == null) {
            close()
            return@callbackFlow
        }

        val wsUrl = SupabaseConfig.supabaseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${SupabaseConfig.supabaseAnonKey}&vsn=1.0.0"

        val refCounter = AtomicInteger(1)
        var heartbeatJob: Job? = null
        val topic = "realtime:mess_$messId"

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val changesArray = JSONArray()
                tables.forEach { table ->
                    changesArray.put(
                        JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", table)
                            put("filter", "mess_id=eq.$messId")
                        }
                    )
                }
                val joinMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("postgres_changes", changesArray)
                        })
                        put("access_token", token)
                    })
                    put("ref", refCounter.getAndIncrement().toString())
                }
                webSocket.send(joinMsg.toString())

                heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
                    while (isActive) {
                        delay(25_000L)
                        val hb = JSONObject().apply {
                            put("topic", "phoenix")
                            put("event", "heartbeat")
                            put("payload", JSONObject())
                            put("ref", refCounter.getAndIncrement().toString())
                        }
                        webSocket.send(hb.toString())
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val obj = JSONObject(text)
                    val event = obj.optString("event")
                    if (event == "postgres_changes") {
                        val data = obj.optJSONObject("payload")?.optJSONObject("data")
                        val table = data?.optString("table").orEmpty()
                        val type = data?.optString("type").orEmpty()
                        if (table.isNotBlank()) {
                            trySend(RealtimeChangeEvent(table = table, eventType = type))
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Non-fatal: app continues working with manual refresh / foreground sync
            }
        }

        val request = Request.Builder().url(wsUrl).build()
        val socket = httpClient.newWebSocket(request, listener)

        awaitClose {
            heartbeatJob?.cancel()
            socket.close(1000, "Screen unmounted")
        }
    }
}
