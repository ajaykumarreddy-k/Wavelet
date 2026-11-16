package com.music.bitchord.spotify.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.spotify.api.SpotifyItemsResult
import com.music.bitchord.spotify.api.SpotifyTrack
import com.music.bitchord.spotify.data.SpotifyRepository
import kotlinx.coroutines.launch

/**
 * Screen 2: Shows tracks of a playlist or Liked Songs with progressive high-speed loading.
 * Handles lazy loading and caching. If NotAvailable, shows Dev Mode notice with "Import via link" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    isOwnedOrCollab: Boolean,
    coverUrl: String?,
    ownerName: String?,
    onNavigateBack: () -> Unit,
    onImportViaLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SpotifyRepository(context) }

    var tracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var isStreamingMore by remember { mutableStateOf(false) }
    var resultState by remember { mutableStateOf<SpotifyItemsResult?>(null) }
    var importingToWavelet by remember { mutableStateOf(false) }

    fun loadTracks(forceRefresh: Boolean = false) {
        val isWebSession = AppSettings.spotifySpdcToken.value.isNotBlank()
        if (!isWebSession && !isOwnedOrCollab && playlistId != "liked") {
            resultState = SpotifyItemsResult.NotAvailable
            return
        }

        loading = tracks.isEmpty()
        isStreamingMore = false
        if (tracks.isEmpty()) {
            resultState = null
        }

        scope.launch {
            val res = if (playlistId == "liked") {
                repo.getLikedSongs(forceRefresh = forceRefresh) { partial ->
                    tracks = partial
                    loading = false
                    isStreamingMore = true
                }
            } else {
                repo.getPlaylistItems(playlistId, forceRefresh = forceRefresh) { partial ->
                    tracks = partial
                    loading = false
                    isStreamingMore = true
                }
            }
            resultState = res
            if (res is SpotifyItemsResult.Success) {
                tracks = res.items
            }
            loading = false
            isStreamingMore = false
        }
    }

    LaunchedEffect(playlistId) {
        loadTracks()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlistName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadTracks(forceRefresh = true) }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when {
                resultState is SpotifyItemsResult.NotAvailable && tracks.isEmpty() -> {
                    // Unavailable View (Spotify Dev Mode limitation)
                    NotAvailableView(
                        playlistName = playlistName,
                        onImportViaLink = onImportViaLink,
                    )
                }

                resultState is SpotifyItemsResult.Error && tracks.isEmpty() -> {
                    val errorMsg = (resultState as SpotifyItemsResult.Error).message
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { loadTracks(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }

                tracks.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Header card
                        item {
                            PlaylistHeader(
                                name = playlistName,
                                coverUrl = coverUrl,
                                ownerName = ownerName,
                                trackCount = tracks.size,
                                onImportToWavelet = {
                                    if (tracks.isEmpty()) {
                                        Toast.makeText(context, "No tracks to import", Toast.LENGTH_SHORT).show()
                                        return@PlaylistHeader
                                    }
                                    importingToWavelet = true
                                    scope.launch {
                                        val waveletSongs = tracks.map { track ->
                                            Song(
                                                videoId = "spotify_${track.id}",
                                                title = track.name,
                                                artist = track.artistsString,
                                                thumbnailUrl = track.album?.coverUrl ?: coverUrl,
                                                durationText = formatDuration(track.duration_ms),
                                                albumName = track.album?.name,
                                            )
                                        }
                                        LocalPlaylistStore.savePlaylist(
                                            title = playlistName,
                                            songs = waveletSongs,
                                            thumbnailUrl = coverUrl,
                                        )
                                        // Prefetch and warm YouTube video ID mappings in the background
                                        com.music.bitchord.data.spotify.SpotifySongResolver.prefetch(waveletSongs)

                                        importingToWavelet = false
                                        Toast.makeText(context, "Saved '$playlistName' to Wavelet playlists!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                importing = importingToWavelet,
                            )
                        }

                        // Tracks
                        itemsIndexed(tracks, key = { index, item -> "${item.id}_$index" }) { index, track ->
                            TrackRow(index = index + 1, track = track)
                        }

                        if (isStreamingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF1DB954),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Loading more tracks...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Empty or still initial loading
                }
            }

            if (loading && tracks.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF1DB954),
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    name: String,
    coverUrl: String?,
    ownerName: String?,
    trackCount: Int,
    onImportToWavelet: () -> Unit,
    importing: Boolean,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1DB954)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("♫", fontSize = 32.sp, color = Color.White)
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (!ownerName.isNullOrBlank()) "$ownerName • $trackCount tracks" else "$trackCount tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onImportToWavelet,
                enabled = !importing && trackCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (importing) "Saving..." else "Import to Wavelet Playlists")
            }
        }
    }
}

@Composable
private fun TrackRow(index: Int, track: SpotifyTrack) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )

            val artUrl = track.album?.coverUrl
            if (!artUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♫", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artistsString,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = formatDuration(track.duration_ms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotAvailableView(
    playlistName: String,
    onImportViaLink: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Songs Unavailable",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Spotify restriction prevents viewing tracks for '$playlistName'. You can import it using the playlist link instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onImportViaLink,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Import via Link")
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
