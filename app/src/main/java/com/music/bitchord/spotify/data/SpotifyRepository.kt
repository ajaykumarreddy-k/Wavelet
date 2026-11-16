package com.music.bitchord.spotify.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.spotify.api.SpotifyApiClient
import com.music.bitchord.spotify.api.SpotifyImage
import com.music.bitchord.spotify.api.SpotifyItemsResult
import com.music.bitchord.spotify.api.SpotifyOwner
import com.music.bitchord.spotify.api.SpotifyPlaylist
import com.music.bitchord.spotify.api.SpotifyPlaylistsResponse
import com.music.bitchord.spotify.api.SpotifySavedTracksResponse
import com.music.bitchord.spotify.api.SpotifyTrack
import com.music.bitchord.spotify.api.SpotifyUser
import com.music.bitchord.spotify.auth.SpotifyTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class SpotifyRepository(private val context: Context) {
    private val api = SpotifyApiClient.getApi(context)
    private val gson = SpotifyApiClient.gson

    companion object {
        private const val TAG = "SpotifyRepository"
        private var hasLoggedLikedSongsJson = false

        // In-memory cache for ultra-fast instant UI rendering
        @Volatile private var cachedPlaylists: List<SpotifyPlaylist>? = null
        @Volatile private var playlistsCacheTime = 0L
        private val trackCache = ConcurrentHashMap<String, List<SpotifyTrack>>()
        private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes cache

        fun clearCache() {
            cachedPlaylists = null
            playlistsCacheTime = 0L
            trackCache.clear()
        }
    }

    suspend fun getMe(): Result<SpotifyUser> = withContext(Dispatchers.IO) {
        try {
            val pkceToken = SpotifyTokenStore.getAccessToken(context)
            if (pkceToken.isNullOrBlank() && AppSettings.spotifySpdcToken.value.isNotBlank()) {
                // For web session, don't spam api.spotify.com to avoid 429 rate limiting
                val existingName = SpotifyTokenStore.getUserName(context) ?: "Spotify User"
                val existingId = SpotifyTokenStore.getUserId(context) ?: "me"
                return@withContext Result.success(SpotifyUser(id = existingId, display_name = existingName))
            }

            val response = api.getMe()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                SpotifyTokenStore.saveUser(context, user.id, user.display_name, user.email)
                Result.success(user)
            } else {
                Result.failure(RuntimeException("Failed to fetch user: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in getMe", e)
            Result.failure(e)
        }
    }

    suspend fun getAllPlaylists(forceRefresh: Boolean = false): Result<List<SpotifyPlaylist>> = withContext(Dispatchers.IO) {
        try {
            // 1. Check in-memory cache first (0ms instantaneous return)
            if (!forceRefresh) {
                val cached = cachedPlaylists
                if (!cached.isNullOrEmpty() && (System.currentTimeMillis() - playlistsCacheTime < CACHE_TTL_MS)) {
                    Log.d(TAG, "Returning ${cached.size} playlists from memory cache (0ms)")
                    return@withContext Result.success(cached)
                }
            }

            // 2. If logged in via Web Login (sp_dc), use high-speed GraphQL SpotifyLibrary directly!
            if (AppSettings.spotifySpdcToken.value.isNotBlank()) {
                Log.d(TAG, "Fetching playlists directly via high-speed SpotifyLibrary GraphQL...")
                val libPlaylists = runCatching {
                    SpotifyLibrary.playlists()
                }.getOrNull()

                if (!libPlaylists.isNullOrEmpty()) {
                    val mapped = libPlaylists
                        .filter { it.id != SpotifyLibrary.LIKED_ID }
                        .map { p ->
                            SpotifyPlaylist(
                                id = p.id,
                                name = p.name,
                                owner = SpotifyOwner(display_name = p.owner ?: "Spotify"),
                                images = p.imageUrl?.let { listOf(SpotifyImage(url = it)) },
                                collaborative = true,
                            )
                        }
                    cachedPlaylists = mapped
                    playlistsCacheTime = System.currentTimeMillis()
                    Log.d(TAG, "Loaded ${mapped.size} playlists instantaneously via SpotifyLibrary")
                    return@withContext Result.success(mapped)
                }
            }

            // 3. Fallback or PKCE REST API
            val allPlaylists = mutableListOf<SpotifyPlaylist>()
            var response = api.getPlaylists(limit = 50, offset = 0)

            while (response.isSuccessful && response.body() != null) {
                val body: SpotifyPlaylistsResponse = response.body()!!
                allPlaylists.addAll(body.items)

                val nextUrl = body.next
                if (!nextUrl.isNullOrBlank()) {
                    response = api.getPlaylistsByUrl(nextUrl)
                } else {
                    break
                }
            }

            if (!response.isSuccessful && allPlaylists.isEmpty()) {
                Result.failure(RuntimeException("Failed to load playlists: ${response.code()} ${response.message()}"))
            } else {
                cachedPlaylists = allPlaylists
                playlistsCacheTime = System.currentTimeMillis()
                Result.success(allPlaylists)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading playlists", e)
            Result.failure(e)
        }
    }

    suspend fun getPlaylistItems(
        playlistId: String,
        forceRefresh: Boolean = false,
        onProgress: ((List<SpotifyTrack>) -> Unit)? = null,
    ): SpotifyItemsResult = withContext(Dispatchers.IO) {
        try {
            // 1. Check in-memory cache first
            if (!forceRefresh) {
                val cached = trackCache[playlistId]
                if (!cached.isNullOrEmpty()) {
                    Log.d(TAG, "Returning ${cached.size} tracks from cache for $playlistId (0ms)")
                    onProgress?.invoke(cached)
                    return@withContext SpotifyItemsResult.Success(cached)
                }
            }

            // 2. If logged in via Web Login (sp_dc), use high-speed GraphQL SpotifyLibrary directly!
            if (AppSettings.spotifySpdcToken.value.isNotBlank()) {
                Log.d(TAG, "Fetching tracks directly via high-speed SpotifyLibrary GraphQL for $playlistId...")
                val libTracks = runCatching {
                    SpotifyLibrary.tracks(playlistId) { partial ->
                        val mapped = partial.map { t ->
                            SpotifyTrack(
                                id = t.id,
                                name = t.title,
                                duration_ms = t.durationMs.toLong(),
                                artists = listOf(com.music.bitchord.spotify.api.SpotifyArtist(name = t.artist)),
                                album = com.music.bitchord.spotify.api.SpotifyAlbum(
                                    name = t.album.orEmpty(),
                                    images = t.imageUrl?.let { listOf(SpotifyImage(url = it)) }
                                )
                            )
                        }
                        trackCache[playlistId] = mapped
                        onProgress?.invoke(mapped)
                    }
                }.getOrNull()

                if (libTracks != null) {
                    val fullMapped = libTracks.map { t ->
                        SpotifyTrack(
                            id = t.id,
                            name = t.title,
                            duration_ms = t.durationMs.toLong(),
                            artists = listOf(com.music.bitchord.spotify.api.SpotifyArtist(name = t.artist)),
                            album = com.music.bitchord.spotify.api.SpotifyAlbum(
                                name = t.album.orEmpty(),
                                images = t.imageUrl?.let { listOf(SpotifyImage(url = it)) }
                            )
                        )
                    }
                    trackCache[playlistId] = fullMapped
                    return@withContext SpotifyItemsResult.Success(fullMapped)
                }
            }

            // 3. Fallback or PKCE REST API
            val tracks = mutableListOf<SpotifyTrack>()
            var response = api.getPlaylistItems(playlistId, limit = 100, offset = 0)

            if (response.code() == 403) {
                Log.w(TAG, "403 Forbidden for playlist items on $playlistId (Dev Mode access restriction)")
                return@withContext SpotifyItemsResult.NotAvailable
            }
            if (!response.isSuccessful) {
                return@withContext SpotifyItemsResult.Error("HTTP ${response.code()}: ${response.message()}")
            }

            while (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                for (wrapper in body.items) {
                    val track = wrapper.actualTrack
                    if (track != null && !wrapper.is_local && !track.is_local) {
                        tracks.add(track)
                    }
                }
                onProgress?.invoke(tracks.toList())

                val nextUrl = body.next
                if (!nextUrl.isNullOrBlank()) {
                    response = api.getPlaylistItemsByUrl(nextUrl)
                    if (response.code() == 403) {
                        return@withContext SpotifyItemsResult.NotAvailable
                    }
                } else {
                    break
                }
            }

            trackCache[playlistId] = tracks
            SpotifyItemsResult.Success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading playlist items", e)
            SpotifyItemsResult.Error(e.message ?: "Failed to load tracks")
        }
    }

    suspend fun getLikedSongs(
        forceRefresh: Boolean = false,
        onProgress: ((List<SpotifyTrack>) -> Unit)? = null,
    ): SpotifyItemsResult = withContext(Dispatchers.IO) {
        try {
            // 1. Check in-memory cache first
            if (!forceRefresh) {
                val cached = trackCache[SpotifyLibrary.LIKED_ID]
                if (!cached.isNullOrEmpty()) {
                    Log.d(TAG, "Returning ${cached.size} liked tracks from cache (0ms)")
                    onProgress?.invoke(cached)
                    return@withContext SpotifyItemsResult.Success(cached)
                }
            }

            // 2. If logged in via Web Login (sp_dc), use high-speed GraphQL SpotifyLibrary directly!
            if (AppSettings.spotifySpdcToken.value.isNotBlank()) {
                Log.d(TAG, "Fetching liked songs directly via high-speed SpotifyLibrary GraphQL...")
                val libTracks = runCatching {
                    SpotifyLibrary.tracks(SpotifyLibrary.LIKED_ID) { partial ->
                        val mapped = partial.map { t ->
                            SpotifyTrack(
                                id = t.id,
                                name = t.title,
                                duration_ms = t.durationMs.toLong(),
                                artists = listOf(com.music.bitchord.spotify.api.SpotifyArtist(name = t.artist)),
                                album = com.music.bitchord.spotify.api.SpotifyAlbum(
                                    name = t.album.orEmpty(),
                                    images = t.imageUrl?.let { listOf(SpotifyImage(url = it)) }
                                )
                            )
                        }
                        trackCache[SpotifyLibrary.LIKED_ID] = mapped
                        onProgress?.invoke(mapped)
                    }
                }.getOrNull()

                if (libTracks != null) {
                    val fullMapped = libTracks.map { t ->
                        SpotifyTrack(
                            id = t.id,
                            name = t.title,
                            duration_ms = t.durationMs.toLong(),
                            artists = listOf(com.music.bitchord.spotify.api.SpotifyArtist(name = t.artist)),
                            album = com.music.bitchord.spotify.api.SpotifyAlbum(
                                name = t.album.orEmpty(),
                                images = t.imageUrl?.let { listOf(SpotifyImage(url = it)) }
                            )
                        )
                    }
                    trackCache[SpotifyLibrary.LIKED_ID] = fullMapped
                    return@withContext SpotifyItemsResult.Success(fullMapped)
                }
            }

            // 3. Fallback or PKCE REST API
            val tracks = mutableListOf<SpotifyTrack>()
            var response = api.getSavedTracks(limit = 50, offset = 0)

            if (response.code() == 403) {
                Log.w(TAG, "403 on liked tracks")
                return@withContext SpotifyItemsResult.NotAvailable
            }

            var rawBody: String? = null
            if (response.isSuccessful) {
                rawBody = response.body()?.string()
            } else {
                val libResp = api.getLibrary(limit = 50, offset = 0)
                if (libResp.code() == 403) {
                    return@withContext SpotifyItemsResult.NotAvailable
                }
                if (libResp.isSuccessful) {
                    rawBody = libResp.body()?.string()
                }
            }

            if (rawBody.isNullOrBlank()) {
                return@withContext SpotifyItemsResult.Error("Empty response from liked songs endpoint")
            }

            // Parse response
            var nextUrl: String? = null
            try {
                val parsed = gson.fromJson(rawBody, SpotifySavedTracksResponse::class.java)
                for (saved in parsed.items) {
                    val track = saved.actualTrack
                    if (track != null && !track.is_local) {
                        tracks.add(track)
                    }
                }
                nextUrl = parsed.next
                onProgress?.invoke(tracks.toList())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse liked songs with standard wrapper, attempting fallback parser", e)
                parseDynamicLikedTracks(rawBody, tracks)
                onProgress?.invoke(tracks.toList())
            }

            // Follow pagination
            while (!nextUrl.isNullOrBlank()) {
                val nextResp = api.getSavedTracksByUrl(nextUrl)
                if (nextResp.code() == 403) break
                if (!nextResp.isSuccessful) break
                val nextBody = nextResp.body()?.string().orEmpty()
                val parsed = gson.fromJson(nextBody, SpotifySavedTracksResponse::class.java)
                for (saved in parsed.items) {
                    val track = saved.actualTrack
                    if (track != null && !track.is_local) {
                        tracks.add(track)
                    }
                }
                onProgress?.invoke(tracks.toList())
                nextUrl = parsed.next
            }

            trackCache[SpotifyLibrary.LIKED_ID] = tracks
            SpotifyItemsResult.Success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in getLikedSongs", e)
            SpotifyItemsResult.Error(e.message ?: "Failed to load liked songs")
        }
    }

    private fun parseDynamicLikedTracks(jsonString: String, outList: MutableList<SpotifyTrack>) {
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val items = root.getAsJsonArray("items") ?: return
            for (element in items) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val trackObj = when {
                    obj.has("track") && obj.get("track").isJsonObject -> obj.getAsJsonObject("track")
                    obj.has("item") && obj.get("item").isJsonObject -> obj.getAsJsonObject("item")
                    obj.has("name") -> obj
                    else -> null
                } ?: continue

                val track = gson.fromJson(trackObj, SpotifyTrack::class.java)
                if (track != null && !track.is_local && track.name.isNotBlank()) {
                    outList.add(track)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback parser error", e)
        }
    }
}
