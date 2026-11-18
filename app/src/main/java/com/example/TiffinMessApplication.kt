package com.example

import android.app.Application
import com.example.data.SessionStore
import com.example.data.SupabaseAuthClient
import com.example.data.SupabaseRealtimeClient
import com.example.data.SupabaseRestClient
import com.example.data.TiffinRepository
import com.example.domain.AppLanguage
import com.example.ui.theme.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    val sessionStore = SessionStore(application)
    val authClient = SupabaseAuthClient(httpClient, sessionStore, json)
    val restClient = SupabaseRestClient(httpClient, authClient, json)
    val realtimeClient = SupabaseRealtimeClient(httpClient, authClient)
    val repository = TiffinRepository(restClient, sessionStore, realtimeClient)

    val activeLanguage = MutableStateFlow(AppLanguage.DEFAULT)
    val appStrings = AppStrings(application) { activeLanguage.value }

    init {
        appScope.launch {
            sessionStore.observeLanguage().collect { lang ->
                activeLanguage.value = lang
            }
        }
    }
}

class TiffinMessApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
