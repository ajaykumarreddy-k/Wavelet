package com.music.bitchord.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LocalPlaylist(
    val id: String,
    val title: String,
    val songs: List<Song>,
    val thumbnailUrl: String? = null,
    val sourcePlaylistId: String? = null,
    val continuation: String? = null,
    val isComplete: Boolean = true,
) {
    val browseId: String get() = "local:playlist:$id"
}

object LocalPlaylistStore {
    const val LOCAL_PREFIX = "local:playlist:"
    private const val PREF_NAME = "bitchord_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        if (!this::prefs.isInitialized) return
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return
        runCatching {
            val list = mutableListOf<LocalPlaylist>()
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val thumbnailUrl = obj.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" }
                val sourcePlaylistId = obj.optString("sourcePlaylistId", null).takeIf { !it.isNullOrBlank() && it != "null" }
                val continuation = obj.optString("continuation", null).takeIf { !it.isNullOrBlank() && it != "null" }
                val isComplete = obj.optBoolean("isComplete", true)
                val songsArray = obj.getJSONArray("songs")
                val songs = mutableListOf<Song>()
                for (j in 0 until songsArray.length()) {
                    val sObj = songsArray.getJSONObject(j)
                    val song = Song(
                        videoId = sObj.optString("videoId", ""),
                        title = sObj.optString("title", ""),
                        artist = sObj.optString("artist", ""),
                        thumbnailUrl = sObj.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        durationText = sObj.optString("durationText", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        artistId = sObj.optString("artistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumId = sObj.optString("albumId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumName = sObj.optString("albumName", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        isVideo = sObj.optBoolean("isVideo", false),
                    )
                    songs.add(song)
                }
                list.add(LocalPlaylist(id, title, songs, thumbnailUrl, sourcePlaylistId, continuation, isComplete))
            }
            _playlists.value = list
        }
    }

    fun savePlaylist(
        title: String,
        songs: List<Song>,
        thumbnailUrl: String? = null,
        sourcePlaylistId: String? = null,
        continuation: String? = null,
        isComplete: Boolean = true,
    ): LocalPlaylist {
        val id = "local_pl_" + System.currentTimeMillis()
        val thumb = thumbnailUrl ?: songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
        val playlist = LocalPlaylist(
            id = id,
            title = title,
            songs = songs,
            thumbnailUrl = thumb,
            sourcePlaylistId = sourcePlaylistId,
            continuation = continuation,
            isComplete = isComplete,
        )
        val updated = listOf(playlist) + _playlists.value.filterNot { it.id == id }
        _playlists.value = updated
        persist(updated)
        return playlist
    }

    /**
     * Appends freshly fetched songs from lazy continuation into the local playlist.
     * Prevents duplicate videoIds and updates completion status and thumbnail.
     */
    fun appendSongs(
        id: String,
        newSongs: List<Song>,
        nextContinuation: String?,
        isComplete: Boolean,
    ): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        var modified: LocalPlaylist? = null
        val updated = _playlists.value.map { playlist ->
            if (playlist.id == cleanId || playlist.id == id || playlist.browseId == id) {
                val existingIds = playlist.songs.map { it.videoId }.toSet()
                val fresh = newSongs.filterNot { it.videoId in existingIds }
                val mergedSongs = playlist.songs + fresh
                val updatedThumb = playlist.thumbnailUrl ?: mergedSongs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                val res = playlist.copy(
                    songs = mergedSongs,
                    thumbnailUrl = updatedThumb,
                    continuation = nextContinuation,
                    isComplete = isComplete,
                )
                modified = res
                res
            } else {
                playlist
            }
        }
        _playlists.value = updated
        persist(updated)
        return modified
    }

    fun appendSongs(id: String, newSongs: List<Song>): LocalPlaylist? {
        val existing = getPlaylist(id) ?: return null
        return appendSongs(id, newSongs, existing.continuation, existing.isComplete)
    }

    fun updateSongs(id: String, updatedSongs: List<Song>): LocalPlaylist? {
        val cleanId = id.removePrefix(LOCAL_PREFIX).removePrefix("VL")
        var modified: LocalPlaylist? = null
        val updated = _playlists.value.map { playlist ->
            if (playlist.id == cleanId || playlist.id == id || playlist.browseId == id) {
                val updatedThumb = playlist.thumbnailUrl ?: updatedSongs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                val res = playlist.copy(songs = updatedSongs, thumbnailUrl = updatedThumb)
                modified = res
                res
            } else {
                playlist
            }
        }
        _playlists.value = updated
        persist(updated)
        return modified
    }

    fun deletePlaylist(id: String) {
        val updated = _playlists.value.filterNot { it.id == id || it.browseId == id || it.id == id.removePrefix("local:playlist:") }
        _playlists.value = updated
        persist(updated)
    }

    fun renamePlaylist(id: String, newTitle: String) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val updated = _playlists.value.map {
            if (it.id == cleanId || it.id == id || it.browseId == id) {
                it.copy(title = newTitle)
            } else it
        }
        _playlists.value = updated
        persist(updated)
    }

    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        return _playlists.value.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
    }

    private fun persist(list: List<LocalPlaylist>) {
        if (!this::prefs.isInitialized) return
        runCatching {
            val jsonArray = JSONArray()
            for (playlist in list) {
                val pObj = JSONObject()
                pObj.put("id", playlist.id)
                pObj.put("title", playlist.title)
                pObj.put("thumbnailUrl", playlist.thumbnailUrl)
                pObj.put("sourcePlaylistId", playlist.sourcePlaylistId)
                pObj.put("continuation", playlist.continuation)
                pObj.put("isComplete", playlist.isComplete)
                val songsArray = JSONArray()
                for (song in playlist.songs) {
                    val sObj = JSONObject()
                    sObj.put("videoId", song.videoId)
                    sObj.put("title", song.title)
                    sObj.put("artist", song.artist)
                    sObj.put("thumbnailUrl", song.thumbnailUrl)
                    sObj.put("durationText", song.durationText)
                    sObj.put("artistId", song.artistId)
                    sObj.put("albumId", song.albumId)
                    sObj.put("albumName", song.albumName)
                    sObj.put("isVideo", song.isVideo)
                    songsArray.put(sObj)
                }
                pObj.put("songs", songsArray)
                jsonArray.put(pObj)
            }
            prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
        }
    }
}
