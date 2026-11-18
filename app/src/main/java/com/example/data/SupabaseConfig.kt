package com.example.data

import com.example.BuildConfig

object SupabaseConfig {
    const val OAUTH_REDIRECT_URI = "tiffinmess://auth/callback"

    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val googleWebClientId: String
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()

    val isConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("YOUR_PROJECT_REF") &&
            supabaseAnonKey.isNotBlank() &&
            !supabaseAnonKey.contains("YOUR_SUPABASE_ANON_KEY")

    val hasGoogleWebClientId: Boolean
        get() = googleWebClientId.isNotBlank() &&
            !googleWebClientId.contains("YOUR_GOOGLE_WEB_CLIENT_ID")
}
