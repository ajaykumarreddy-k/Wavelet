package com.music.bitchord.spotify.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.spotify.api.SpotifyPlaylist
import com.music.bitchord.spotify.auth.SpotifyTokenStore
import com.music.bitchord.spotify.data.SpotifyRepository
import kotlinx.coroutines.launch

data class SpotifyDetailNav(
    val id: String,
    val name: String,
    val isOwnedOrCollab: Boolean,
    val coverUrl: String?,
    val ownerName: String?
)

/**
 * Screen 1: Displays Spotify connection status, Liked Songs pinned first, and user playlists.
 * Tapping "Sign in with Spotify" directly opens the in-app browser window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistsScreen(
    onNavigateBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenPlaylistDetail: (playlistId: String, playlistName: String, isOwnedOrCollab: Boolean, coverUrl: String?, ownerName: String?) -> Unit,
    onOpenYouTubeImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SpotifyRepository(context) }

    val isLoggedIn by SpotifyTokenStore.isLoggedIn.collectAsStateWithLifecycle()
    val userName by SpotifyTokenStore.userName.collectAsStateWithLifecycle()
    val myUserId by SpotifyTokenStore.userId.collectAsStateWithLifecycle()

    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadPlaylists(forceRefresh: Boolean = false) {
        if (!isLoggedIn) return
        loading = playlists.isEmpty()
        error = null
        scope.launch {
            // Fetch profile asynchronously without delaying playlist loading
            launch { repo.getMe() }
            val result = repo.getAllPlaylists(forceRefresh = forceRefresh)
            result.onSuccess {
                playlists = it
                loading = false
            }.onFailure {
                if (playlists.isEmpty()) {
                    error = it.message ?: "Failed to load playlists"
                }
                loading = false
            }
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            loadPlaylists()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Spotify Library",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
                    if (isLoggedIn) {
                        IconButton(onClick = { loadPlaylists(forceRefresh = true) }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (!isLoggedIn) {
                // Not logged in view: direct in-app browser sign in
                SpotifyLoginPrompt(
                    onConnect = onOpenLogin,
                )
            } else {
                // Logged in: show playlists
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Profile banner with logout
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF1DB954), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (userName?.take(1) ?: "S").uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userName ?: "Spotify User",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Connected (Read-Only)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        SpotifyTokenStore.clear(context)
                                        Toast.makeText(context, "Logged out from Spotify", Toast.LENGTH_SHORT).show()
                                    },
                                ) {
                                    Text("Logout", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // Pinned Liked Songs Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenPlaylistDetail("liked", "Liked Songs", true, null, userName)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                listOf(Color(0xFF450AF5), Color(0xFFC4EFD9))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Liked Songs",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = "Your saved library tracks",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }

                    // Section title
                    item {
                        Text(
                            text = "Playlists (${playlists.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }

                    // Playlists list
                    items(playlists, key = { it.id }) { playlist ->
                        val isWebSession = com.music.bitchord.data.settings.AppSettings.spotifySpdcToken.value.isNotBlank()
                        val isOwned = !myUserId.isNullOrBlank() && playlist.owner.id == myUserId
                        val isOwnedOrCollab = isWebSession || isOwned || playlist.collaborative

                        PlaylistCard(
                            playlist = playlist,
                            isOwnedOrCollab = isOwnedOrCollab,
                            onClick = {
                                onOpenPlaylistDetail(
                                    playlist.id,
                                    playlist.name,
                                    isOwnedOrCollab,
                                    playlist.coverUrl,
                                    playlist.owner.display_name ?: playlist.owner.id,
                                )
                            },
                        )
                    }
                }
            }

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF1DB954),
                )
            }

            if (error != null && playlists.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error ?: "Error loading playlists",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { loadPlaylists() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyLoginPrompt(
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Color(0xFF1DB954), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "♫",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Sign in to Spotify",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Log in with your Spotify account to view playlists, import your saved tracks, and sync playback to Discord.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onConnect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(50.dp),
        ) {
            Text(
                text = "Sign in with Spotify",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: SpotifyPlaylist,
    isOwnedOrCollab: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (isOwnedOrCollab) 0.4f else 0.2f
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (isOwnedOrCollab) 1f else 0.55f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cover = playlist.coverUrl
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♪", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                val owner = playlist.owner.display_name ?: playlist.owner.id
                val trackCount = playlist.tracks?.total
                val infoText = buildString {
                    if (owner.isNotBlank()) append(owner)
                    if (trackCount != null && trackCount > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("$trackCount songs")
                    }
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!isOwnedOrCollab) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Songs unavailable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}
