package com.music.bitchord.spotify.api

sealed interface SpotifyItemsResult {
    data class Success(val items: List<SpotifyTrack>) : SpotifyItemsResult
    data object NotAvailable : SpotifyItemsResult
    data class Error(val message: String) : SpotifyItemsResult
}

