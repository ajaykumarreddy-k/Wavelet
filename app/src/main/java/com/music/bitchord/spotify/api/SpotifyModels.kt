package com.music.bitchord.spotify.api

import com.google.gson.annotations.SerializedName

data class SpotifyUser(
    val id: String = "",
    val display_name: String? = null,
    val email: String? = null,
    val images: List<SpotifyImage>? = null
)

data class SpotifyImage(
    val url: String = "",
    val height: Int? = null,
    val width: Int? = null
)

data class SpotifyOwner(
    val id: String = "",
    val display_name: String? = null
)

data class SpotifyPlaylist(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val images: List<SpotifyImage>? = null,
    val owner: SpotifyOwner = SpotifyOwner(),
    val collaborative: Boolean = false,
    val public: Boolean? = null,
    val tracks: SpotifyPlaylistTracksRef? = null
) {
    val coverUrl: String? get() = images?.firstOrNull()?.url
}

data class SpotifyPlaylistTracksRef(
    val href: String? = null,
    val total: Int = 0
)

data class SpotifyPlaylistsResponse(
    val href: String? = null,
    val items: List<SpotifyPlaylist> = emptyList(),
    val limit: Int = 50,
    val next: String? = null,
    val offset: Int = 0,
    val total: Int = 0
)

data class SpotifyArtist(
    val id: String = "",
    val name: String = ""
)

data class SpotifyAlbum(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage>? = null
) {
    val coverUrl: String? get() = images?.firstOrNull()?.url
}

data class SpotifyTrack(
    val id: String = "",
    val name: String = "",
    val uri: String? = null,
    val duration_ms: Long = 0L,
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null,
    val is_local: Boolean = false
) {
    val artistsString: String get() = artists.joinToString(", ") { it.name }
}

data class SpotifyPlaylistItem(
    val added_at: String? = null,
    val is_local: Boolean = false,
    // Spotify Dev Mode (Feb 2026): items[].item, not track
    val item: SpotifyTrack? = null,
    // Backwards compatibility with previous versions / endpoints
    val track: SpotifyTrack? = null
) {
    val actualTrack: SpotifyTrack? get() = item ?: track
}

data class SpotifyPlaylistItemsResponse(
    val href: String? = null,
    val items: List<SpotifyPlaylistItem> = emptyList(),
    val limit: Int = 100,
    val next: String? = null,
    val offset: Int = 0,
    val total: Int = 0
)

data class SpotifySavedTrack(
    val added_at: String? = null,
    val track: SpotifyTrack? = null,
    val item: SpotifyTrack? = null
) {
    val actualTrack: SpotifyTrack? get() = track ?: item
}

data class SpotifySavedTracksResponse(
    val href: String? = null,
    val items: List<SpotifySavedTrack> = emptyList(),
    val limit: Int = 50,
    val next: String? = null,
    val offset: Int = 0,
    val total: Int = 0
)

data class SpotifyCurrentlyPlaying(
    val is_playing: Boolean = false,
    val progress_ms: Long = 0L,
    val item: SpotifyTrack? = null,
    val currently_playing_type: String? = null
)

