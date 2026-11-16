package com.music.bitchord.spotify.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface SpotifyApi {

    @GET("v1/me")
    suspend fun getMe(): Response<SpotifyUser>

    @GET("v1/me/playlists")
    suspend fun getPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyPlaylistsResponse>

    @GET
    suspend fun getPlaylistsByUrl(
        @Url url: String
    ): Response<SpotifyPlaylistsResponse>

    @GET("v1/playlists/{playlist_id}/items")
    suspend fun getPlaylistItems(
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyPlaylistItemsResponse>

    @GET
    suspend fun getPlaylistItemsByUrl(
        @Url url: String
    ): Response<SpotifyPlaylistItemsResponse>

    // Liked songs: returns raw ResponseBody so we can log JSON on first run and safely parse
    @GET("v1/me/tracks")
    suspend fun getSavedTracks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ResponseBody>

    @GET
    suspend fun getSavedTracksByUrl(
        @Url url: String
    ): Response<ResponseBody>

    // Fallback library endpoint if tracks endpoint fails
    @GET("v1/me/library")
    suspend fun getLibrary(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ResponseBody>

    @GET("v1/me/player/currently-playing")
    suspend fun getCurrentlyPlaying(): Response<SpotifyCurrentlyPlaying>
}

