package com.music.bitchord.spotify.auth

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.auth.EncryptedPrefs
import com.music.bitchord.auth.clearSpotifyWebSession
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Stores Spotify OAuth and web-session authentication state securely.
 */
object SpotifyTokenStore {
    private const val TAG = "SpotifyTokenStore"
    private const val PREFS_NAME = "spotify_tokens_enc"
    private const val PLAIN_PREFS_NAME = "spotify_tokens_plain"

    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_CUSTOM_CLIENT_ID = "custom_client_id"
    private const val KEY_CURRENTLY_PLAYING_DISABLED = "currently_playing_disabled"

    private var prefs: SharedPreferences? = null

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = EncryptedPrefs.open(context.applicationContext, PREFS_NAME, PLAIN_PREFS_NAME)
            updateState()
            CoroutineScope(Dispatchers.IO).launch {
                AppSettings.spotifySpdcToken.collect { cookie ->
                    updateState()
                    if (cookie.isNotBlank() && _userName.value.isNullOrBlank()) {
                        fetchProfile(context)
                    }
                }
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: synchronized(this) {
            prefs ?: EncryptedPrefs.open(context.applicationContext, PREFS_NAME, PLAIN_PREFS_NAME).also {
                prefs = it
                updateState()
            }
        }
    }

    private fun updateState() {
        val p = prefs ?: return
        val hasPkceTokens = !p.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank() &&
                !p.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()
        val hasWebCookie = AppSettings.spotifySpdcToken.value.isNotBlank()
        _isLoggedIn.value = hasPkceTokens || hasWebCookie
        _userName.value = p.getString(KEY_USER_NAME, null)
        _userId.value = p.getString(KEY_USER_ID, null)
    }

    fun getAccessToken(context: Context): String? {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    }

    fun getExpiresAtMs(context: Context): Long {
        return getPrefs(context).getLong(KEY_EXPIRES_AT_MS, 0L)
    }

    fun isTokenExpired(context: Context): Boolean {
        return System.currentTimeMillis() >= (getExpiresAtMs(context) - 60_000L)
    }

    fun saveTokens(
        context: Context,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long
    ) {
        val editor = getPrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT_MS, System.currentTimeMillis() + (expiresInSeconds * 1000L))
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        editor.apply()
        updateState()
    }

    fun saveUser(context: Context, userId: String, userName: String?, email: String?) {
        getPrefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_EMAIL, email)
            .apply()
        updateState()
    }

    fun getUserId(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_ID, null)
    }

    fun getUserName(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_NAME, null)
    }

    fun getCustomClientId(context: Context): String? {
        return getPrefs(context).getString(KEY_CUSTOM_CLIENT_ID, null)
    }

    fun setCustomClientId(context: Context, clientId: String?) {
        getPrefs(context).edit()
            .putString(KEY_CUSTOM_CLIENT_ID, clientId?.trim())
            .apply()
    }

    fun isCurrentlyPlayingDisabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CURRENTLY_PLAYING_DISABLED, false)
    }

    fun setCurrentlyPlayingDisabled(context: Context, disabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_CURRENTLY_PLAYING_DISABLED, disabled)
            .apply()
    }

    suspend fun getValidAccessToken(context: Context): String? {
        // 1. Try PKCE access token if valid
        val pkce = getAccessToken(context)
        if (!pkce.isNullOrBlank() && !isTokenExpired(context)) {
            return pkce
        }

        // 2. Try web player session token via SpotifyToken
        val webCookie = AppSettings.spotifySpdcToken.value
        if (webCookie.isNotBlank()) {
            val harvested = SpotifyToken.accessToken()
            if (!harvested.isNullOrBlank()) {
                return harvested
            }
        }

        // 3. Fallback to PKCE token refresh
        if (!pkce.isNullOrBlank()) {
            val refreshed = SpotifyAuthManager.refreshToken(context)
            if (refreshed) {
                return getAccessToken(context)
            }
        }

        return null
    }

    fun onWebLoginConnected(context: Context, spdcToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            SpotifyToken.invalidate()
            updateState()
            fetchProfile(context)
        }
    }

    private suspend fun fetchProfile(context: Context) {
        try {
            val token = SpotifyToken.accessToken() ?: getAccessToken(context) ?: return
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val id = json.optString("id", "")
                val displayName = json.optString("display_name", id)
                val email = json.optString("email", "")
                saveUser(context, id, displayName, email)
                Log.d(TAG, "Authenticated Spotify user: $displayName ($id)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch user profile: ${e.message}")
        }
    }

    fun clear(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT_MS)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_CURRENTLY_PLAYING_DISABLED)
            .apply()
        AppSettings.setSpotifySpdcToken("")
        clearSpotifyWebSession()
        SpotifyToken.invalidate()
        com.music.bitchord.spotify.data.SpotifyRepository.clearCache()
        updateState()
    }
}
