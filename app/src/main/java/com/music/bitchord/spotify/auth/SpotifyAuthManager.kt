package com.music.bitchord.spotify.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.DebugLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object SpotifyAuthManager {
    private const val TAG = "SpotifyAuth"
    const val REDIRECT_URI = "wavelet://spotify-callback"
    private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
    private const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"

    const val SCOPES = "playlist-read-private playlist-read-collaborative user-library-read user-read-currently-playing user-read-playback-state"

    private const val PREFS_AUTH = "spotify_auth_temp"
    private const val KEY_CODE_VERIFIER = "code_verifier"

    private val refreshMutex = Mutex()
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun getClientId(context: Context): String {
        val buildId = BuildConfig.SPOTIFY_CLIENT_ID.trim()
        if (buildId.isNotEmpty()) return buildId
        return SpotifyTokenStore.getCustomClientId(context)?.trim().orEmpty()
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun startAuth(context: Context, customClientId: String? = null): Boolean {
        if (!customClientId.isNullOrBlank()) {
            SpotifyTokenStore.setCustomClientId(context, customClientId)
        }
        val clientId = getClientId(context)
        if (clientId.isBlank()) {
            Log.e(TAG, "No Spotify Client ID configured")
            return false
        }

        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)

        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE_VERIFIER, verifier)
            .apply()

        val authUri = Uri.parse(AUTHORIZE_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .build()

        return try {
            val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, authUri)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Custom Tabs launch failed, falling back to browser: ${e.message}")
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                true
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Failed to launch browser: ${fallbackError.message}")
                false
            }
        }
    }

    suspend fun exchangeCode(context: Context, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clientId = getClientId(context)
        if (clientId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Spotify Client ID not set"))
        }

        val prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        val verifier = prefs.getString(KEY_CODE_VERIFIER, null)
            ?: return@withContext Result.failure(IllegalStateException("No code verifier found for PKCE exchange"))

        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "Token exchange failed (${response.code}): $body")
                return@withContext Result.failure(RuntimeException("Token exchange failed: ${response.code} $body"))
            }

            val json = JSONObject(body)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token")
            val expiresIn = json.optLong("expires_in", 3600L)

            SpotifyTokenStore.saveTokens(context, accessToken, refreshToken, expiresIn)

            // Clear temporary verifier
            prefs.edit().remove(KEY_CODE_VERIFIER).apply()

            // Fetch user profile
            fetchMe(context, accessToken)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging auth code", e)
            Result.failure(e)
        }
    }

    suspend fun refreshToken(context: Context): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            // Check if another coroutine already refreshed it while waiting for lock
            if (!SpotifyTokenStore.isTokenExpired(context)) {
                return@withLock true
            }

            val refreshToken = SpotifyTokenStore.getRefreshToken(context)
            if (refreshToken.isNullOrBlank()) {
                Log.w(TAG, "No refresh token available")
                return@withLock false
            }

            val clientId = getClientId(context)
            if (clientId.isBlank()) {
                Log.w(TAG, "No Client ID for refresh")
                return@withLock false
            }

            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val newAccessToken = json.getString("access_token")
                    val newRefreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                    val expiresIn = json.optLong("expires_in", 3600L)
                    SpotifyTokenStore.saveTokens(context, newAccessToken, newRefreshToken ?: refreshToken, expiresIn)
                    Log.d(TAG, "Spotify access token refreshed successfully")
                    true
                } else {
                    Log.e(TAG, "Failed to refresh token (${response.code}): $body")
                    if (response.code in 400..401) {
                        // Refresh token expired or revoked; re-login needed
                        SpotifyTokenStore.clear(context)
                    }
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network exception refreshing token", e)
                false
            }
        }
    }

    private fun fetchMe(context: Context, accessToken: String) {
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val id = json.optString("id", "")
                val displayName = json.optString("display_name", id)
                val email = json.optString("email", "")
                SpotifyTokenStore.saveUser(context, id, displayName, email)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch user profile: ${e.message}")
        }
    }

    fun logout(context: Context) {
        SpotifyTokenStore.clear(context)
    }
}

