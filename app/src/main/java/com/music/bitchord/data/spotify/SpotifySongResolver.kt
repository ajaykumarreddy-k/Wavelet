package com.music.bitchord.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves Spotify track IDs ("spotify_<id>") to real YouTube Music videoIds in milliseconds.
 * Caches resolved mappings in memory and persistent storage so matching only happens once.
 */
object SpotifySongResolver {
    private const val TAG = "SpotifySongResolver"
    private const val PREF_NAME = "bitchord_spotify_yt_mappings"

    private val memoryCache = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs?.all?.forEach { (k, v) ->
                if (v is String && v.isNotBlank()) {
                    memoryCache[k] = v
                }
            }
        }
    }

    fun getCached(spotifyVideoId: String): String? {
        val cleanKey = normalizeKey(spotifyVideoId)
        return memoryCache[cleanKey] ?: prefs?.getString(cleanKey, null)?.also { memoryCache[cleanKey] = it }
    }

    fun put(spotifyVideoId: String, youtubeVideoId: String) {
        if (youtubeVideoId.isBlank() || youtubeVideoId.startsWith("spotify_")) return
        val cleanKey = normalizeKey(spotifyVideoId)
        memoryCache[cleanKey] = youtubeVideoId
        prefs?.edit()?.putString(cleanKey, youtubeVideoId)?.apply()
    }

    private fun normalizeKey(id: String): String {
        return if (id.startsWith("spotify_")) id else "spotify_$id"
    }

    /**
     * Resolves a Spotify track to a real YouTube videoId.
     * Takes ~150-250ms on first run, 0ms on subsequent runs.
     */
    suspend fun resolveSpotifyToYouTube(
        spotifyVideoId: String,
        title: String,
        artist: String,
        durationSec: Int? = null,
        album: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val cleanKey = normalizeKey(spotifyVideoId)
        getCached(cleanKey)?.let { return@withContext it }

        if (title.isBlank()) return@withContext null

        // Coalesce duplicate in-flight requests for the same track
        inFlight[cleanKey]?.let { return@withContext it.await() }

        val deferred = scope.async(Dispatchers.IO) {
            try {
                val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
                Log.d(TAG, "Resolving Spotify track to YouTube Music: '$query' ($cleanKey)")

                val candidates = YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
                    ?.mapNotNull {
                        when (it) {
                            is SearchResult.Track -> it.song
                            is SearchResult.TopTrack -> it.song
                            else -> null
                        }
                    }
                    ?.filter { it.videoId.isNotBlank() && !it.videoId.startsWith("spotify_") }
                    .orEmpty()

                val target = TrackMatcher.Target(
                    title = title,
                    artist = artist,
                    durationSec = durationSec,
                    album = album,
                )

                val match = TrackMatcher.best(candidates, target) ?: candidates.firstOrNull()
                val ytVideoId = match?.videoId

                if (!ytVideoId.isNullOrBlank()) {
                    Log.d(TAG, "Successfully matched '$query' -> YouTube videoId: $ytVideoId")
                    put(cleanKey, ytVideoId)

                    // Also update any matching song in LocalPlaylistStore
                    updateLocalPlaylists(cleanKey, ytVideoId)

                    ytVideoId
                } else {
                    Log.w(TAG, "No YouTube match found for '$query'")
                    null
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Exception resolving Spotify track '$title': ${e.message}", e)
                }
                null
            } finally {
                inFlight.remove(cleanKey)
            }
        }

        val running = inFlight.putIfAbsent(cleanKey, deferred)
        if (running != null) {
            deferred.cancel()
            return@withContext running.await()
        }

        deferred.await()
    }

    /**
     * Asynchronously pre-fetches and warms YouTube mappings for a list of songs.
     */
    fun prefetch(songs: List<Song>) {
        scope.launch {
            for (song in songs) {
                if (song.videoId.startsWith("spotify_")) {
                    if (getCached(song.videoId) == null) {
                        resolveSpotifyToYouTube(
                            spotifyVideoId = song.videoId,
                            title = song.title,
                            artist = song.artist,
                            durationSec = TrackMatcher.secondsOf(song.durationText),
                            album = song.albumName,
                        )
                    }
                }
            }
        }
    }

    private fun updateLocalPlaylists(spotifyKey: String, ytVideoId: String) {
        scope.launch {
            runCatching {
                val playlists = LocalPlaylistStore.playlists.value
                for (pl in playlists) {
                    val needsUpdate = pl.songs.any { it.videoId == spotifyKey }
                    if (needsUpdate) {
                        val updatedSongs = pl.songs.map { s ->
                            if (s.videoId == spotifyKey) s.copy(videoId = ytVideoId) else s
                        }
                        LocalPlaylistStore.updateSongs(pl.id, updatedSongs)
                    }
                }
            }
        }
    }
}

