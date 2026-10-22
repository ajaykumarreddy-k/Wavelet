package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.youtube.YouTubePlaylistImporter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dialog to add any YouTube or YouTube Music playlist directly into local storage.
 * Fetches initial batch immediately so the playlist is available instantly, then
 * lazily loads subsequent tracks in the background.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun YouTubePlaylistImportAlert(
    hazeState: HazeState,
    applicationScope: CoroutineScope,
    onImported: (playlist: LocalPlaylist, initialCount: Int, isComplete: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun start() {
        val id = YouTubePlaylistImporter.extractPlaylistId(link)
        if (id == null) {
            status = context.getString(R.string.youtube_import_invalid)
            failed = true
            return
        }

        working = true
        failed = false
        status = context.getString(R.string.youtube_import_fetching)

        scope.launch {
            try {
                val initialResult = YouTubePlaylistImporter.fetchInitialBatch(id)
                val data = initialResult.getOrThrow()

                if (data.initialSongs.isEmpty()) {
                    status = context.getString(R.string.spotify_import_empty_playlist)
                    failed = true
                    working = false
                    return@launch
                }

                status = context.getString(R.string.youtube_import_saving, data.initialSongs.size)

                // Save locally right away
                val localPlaylist = LocalPlaylistStore.savePlaylist(
                    title = data.title,
                    songs = data.initialSongs,
                    thumbnailUrl = data.thumbnailUrl,
                    sourcePlaylistId = data.playlistId,
                    continuation = data.continuation,
                    isComplete = data.isComplete,
                )

                val continuation = data.continuation
                if (!data.isComplete && !continuation.isNullOrBlank()) {
                    YouTubePlaylistImporter.startLazyContinuation(
                        applicationScope,
                        continuation,
                    ) { nextSongs, nextToken, complete ->
                        LocalPlaylistStore.appendSongs(
                            id = localPlaylist.id,
                            newSongs = nextSongs,
                            nextContinuation = nextToken,
                            isComplete = complete,
                        )
                    }
                }

                onImported(localPlaylist, data.initialSongs.size, data.isComplete)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = context.getString(R.string.youtube_import_failed, e.message.orEmpty())
                failed = true
            } finally {
                working = false
            }
        }
    }

    AlertScaffold(hazeState = hazeState, onDismiss = { if (!working) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.youtube_import_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = status ?: stringResource(R.string.youtube_import_description),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = when {
                    status == null -> MaterialTheme.colorScheme.onSurface
                    failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.Center,
            )

            PillTextField(
                value = link,
                onValueChange = {
                    link = it
                    if (failed) {
                        status = null
                        failed = false
                    }
                },
                placeholder = stringResource(R.string.youtube_import_hint),
                enabled = !working,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (link.isNotBlank() && !working) start() }),
            )
        }

        AlertRule()
        AlertAction(
            label = stringResource(R.string.youtube_import_button),
            emphasised = true,
            onClick = ::start,
            enabled = link.isNotBlank() && !working,
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !working,
        )
    }
}
