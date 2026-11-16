package com.music.bitchord.spotify.discord

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.discord.DiscordRPC
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.spotify.api.SpotifyApiClient
import com.music.bitchord.spotify.auth.SpotifyTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Monitors Spotify's Currently Playing endpoint while the app is in the foreground,
 * publishing track metadata to Discord Rich Presence if Wavelet is not currently playing.
 */
object SpotifyDiscordWatcher {
    private const val TAG = "SpotifyDiscordWatcher"
    private const val POLL_INTERVAL_MS = 7_000L
    private const val ERROR_BACKOFF_MS = 30_000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null
    private var application: Application? = null

    private var discordRpc: DiscordRPC? = null
    private var spotifyPresenceActive = false
    private var consecutiveErrors = 0

    fun install(app: Application) {
        application = app
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                startPolling()
            }

            override fun onStop(owner: LifecycleOwner) {
                stopPolling()
            }
        })
    }

    @Synchronized
    fun startPolling() {
        val app = application ?: return
        if (pollJob?.isActive == true) return

        if (!SpotifyTokenStore.isLoggedIn.value) return
        if (SpotifyTokenStore.isCurrentlyPlayingDisabled(app)) return

        pollJob = scope.launch {
            Log.d(TAG, "Starting Spotify Discord polling loop")
            while (isActive) {
                try {
                    val appCtx = application ?: break

                    // If user logged out or feature disabled, terminate loop
                    if (!SpotifyTokenStore.isLoggedIn.value ||
                        SpotifyTokenStore.isCurrentlyPlayingDisabled(appCtx)
                    ) {
                        clearPresenceIfNeeded()
                        break
                    }

                    // Check if Wavelet itself is playing: Wavelet playback always wins!
                    if (PlaybackService.isWaveletPlaying) {
                        // Wavelet is playing; do not override presence
                        if (spotifyPresenceActive) {
                            spotifyPresenceActive = false
                        }
                        delay(POLL_INTERVAL_MS)
                        continue
                    }

                    val discordToken = AppSettings.discordToken.value
                    val rpcEnabled = AppSettings.discordRpcEnabled.value
                    if (discordToken.isBlank() || !rpcEnabled) {
                        // Discord not connected or disabled
                        clearPresenceIfNeeded()
                        delay(POLL_INTERVAL_MS)
                        continue
                    }

                    // /v1/me/player/currently-playing requires OAuth PKCE scope (user-read-currently-playing).
                    // Web Player session tokens do not have this REST scope and will return 429/401.
                    val pkceToken = SpotifyTokenStore.getAccessToken(appCtx)
                    if (pkceToken.isNullOrBlank()) {
                        delay(ERROR_BACKOFF_MS)
                        continue
                    }

                    val api = SpotifyApiClient.getApi(appCtx)
                    val response = api.getCurrentlyPlaying()

                    when (response.code()) {
                        200 -> {
                            consecutiveErrors = 0
                            val body = response.body()
                            val track = body?.item
                            if (body != null && body.is_playing && track != null) {
                                // Double check Wavelet hasn't started playing while awaiting response
                                if (!PlaybackService.isWaveletPlaying) {
                                    updatePresence(track, body.progress_ms)
                                }
                            } else {
                                clearPresenceIfNeeded()
                            }
                            delay(POLL_INTERVAL_MS)
                        }

                        204 -> {
                            // 204 No Content: nothing currently playing
                            consecutiveErrors = 0
                            clearPresenceIfNeeded()
                            delay(POLL_INTERVAL_MS)
                        }

                        403 -> {
                            Log.w(TAG, "403 Forbidden on currently-playing: Dev Mode restriction")
                            SpotifyTokenStore.setCurrentlyPlayingDisabled(appCtx, true)
                            clearPresenceIfNeeded()
                            showNotice("Spotify Dev Mode: Currently Playing presence is restricted (403).")
                            break
                        }

                        429 -> {
                            val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 5L
                            Log.w(TAG, "429 Rate limited; retrying in ${retryAfter}s")
                            delay((retryAfter * 1000L).coerceAtLeast(POLL_INTERVAL_MS))
                        }

                        else -> {
                            consecutiveErrors++
                            Log.w(TAG, "Unexpected HTTP ${response.code()} from currently-playing")
                            if (consecutiveErrors >= 3) {
                                delay(ERROR_BACKOFF_MS)
                            } else {
                                delay(POLL_INTERVAL_MS)
                            }
                        }
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Exception during Spotify Discord poll: ${e.message}")
                    if (consecutiveErrors >= 3) {
                        delay(ERROR_BACKOFF_MS)
                    } else {
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
        }
    }

    @Synchronized
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        scope.launch {
            clearPresenceIfNeeded()
        }
    }

    private suspend fun updatePresence(
        track: com.music.bitchord.spotify.api.SpotifyTrack,
        progressMs: Long
    ) {
        val app = application ?: return
        val discordToken = AppSettings.discordToken.value
        if (discordToken.isBlank()) return

        try {
            val rpc = discordRpc ?: DiscordRPC(app, discordToken).also { discordRpc = it }

            val song = Song(
                videoId = "spotify_${track.id}",
                title = track.name,
                artist = track.artistsString,
                thumbnailUrl = track.album?.coverUrl,
                albumName = track.album?.name,
            )

            rpc.updateSong(
                song = song,
                currentPlaybackTimeMillis = progressMs,
                durationMillis = track.duration_ms,
                activityName = "Spotify",
                button1Text = "Listen on Spotify",
                button1Visible = true,
                button2Text = "Visit Wavelet",
                button2Visible = true,
            )
            spotifyPresenceActive = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Discord presence for Spotify track: ${e.message}")
        }
    }

    private suspend fun clearPresenceIfNeeded() {
        if (spotifyPresenceActive) {
            spotifyPresenceActive = false
            try {
                discordRpc?.close()
                discordRpc = null
            } catch (e: Exception) {
                Log.w(TAG, "Error closing Discord presence: ${e.message}")
            }
        }
    }

    private fun showNotice(message: String) {
        val app = application ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }
}

