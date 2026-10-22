package com.music.bitchord.data.youtube

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI

private const val TAG = "YouTubePlaylistImporter"

/**
 * Initial metadata and first batch of tracks fetched when importing a YouTube / YouTube Music playlist.
 */
data class YouTubePlaylistInitialData(
    val playlistId: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val initialSongs: List<Song>,
    val continuation: String?,
    val isComplete: Boolean,
)

object YouTubePlaylistImporter {

    /**
     * Extracts a YouTube or YouTube Music playlist ID from any link or raw input.
     *
     * Supported formats:
     * - https://music.youtube.com/playlist?list=PL...
     * - https://www.youtube.com/playlist?list=PL...
     * - https://youtu.be/... (if list parameter exists)
     * - https://youtube.com/watch?v=...&list=PL...
     * - Bare playlist IDs: PL..., VLPL..., OLAK5uy_..., RDCLAK5uy_...
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Match "list=..." parameter from URLs
        if (trimmed.contains("list=")) {
            val listParamRegex = Regex("""[?&]list=([a-zA-Z0-9_-]+)""")
            val match = listParamRegex.find(trimmed)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // Try standard URI query parameter parsing
        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri != null && uri.query != null) {
            val params = uri.query.split("&").associate {
                val parts = it.split("=", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
            params["list"]?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // Clean raw ID
        val bareId = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("music.youtube.com/playlist?list=")
            .removePrefix("youtube.com/playlist?list=")
            .removePrefix("www.youtube.com/playlist?list=")
            .removePrefix("VL")
            .substringBefore("&")
            .substringBefore("?")
            .substringBefore("/")
            .trim()

        if (bareId.isNotBlank() && bareId.all { it.isLetterOrDigit() || it == '_' || it == '-' } && bareId.length >= 4) {
            return bareId
        }

        return null
    }

    /**
     * Fetches the first page of songs and playlist metadata from YouTube / YouTube Music.
     * This returns immediately with the first batch of songs (typically 50-100) and continuation token.
     */
    suspend fun fetchInitialBatch(listId: String): Result<YouTubePlaylistInitialData> {
        val cleanId = listId.removePrefix("VL")
        val browseId = "VL$cleanId"

        return YtMusicRepository.browseSongs(browseId).map { page ->
            val title = page.header?.title?.trim().takeIf { !it.isNullOrBlank() } ?: "YouTube Playlist"
            val author = page.header?.subtitle?.trim().takeIf { !it.isNullOrBlank() }
            val thumb = page.header?.thumbnailUrl ?: page.songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
            val continuation = page.continuation?.takeIf { it.isNotBlank() }

            YouTubePlaylistInitialData(
                playlistId = cleanId,
                title = title,
                author = author,
                thumbnailUrl = thumb,
                initialSongs = page.songs,
                continuation = continuation,
                isComplete = continuation == null,
            )
        }
    }

    /**
     * Lazily fetches remaining songs page by page in the background, invoking [onBatchLoaded] after each batch.
     * Ensures non-blocking streaming of tracks into local storage.
     */
    fun startLazyContinuation(
        scope: CoroutineScope,
        initialContinuation: String,
        onBatchLoaded: suspend (newSongs: List<Song>, nextContinuation: String?, isComplete: Boolean) -> Unit,
    ): Job {
        return scope.launch(Dispatchers.IO) {
            var token: String? = initialContinuation
            while (token != null) {
                // Cooperative delay to keep network calls gentle and lazy
                delay(200)
                try {
                    val result = YtMusicRepository.moreSongs(token)
                    val page = result.getOrNull()
                    if (page == null || page.songs.isEmpty()) {
                        Log.d(TAG, "Lazy fetch completed with no further rows")
                        onBatchLoaded(emptyList(), null, true)
                        break
                    }
                    val nextToken = page.continuation?.takeIf { it.isNotBlank() }
                    val isComplete = nextToken == null
                    token = nextToken
                    Log.d(TAG, "Lazy batch loaded: ${page.songs.size} songs, nextToken? ${nextToken != null}")
                    onBatchLoaded(page.songs, nextToken, isComplete)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Lazy continuation failed: ${e.message}")
                    break
                }
            }
        }
    }
}
