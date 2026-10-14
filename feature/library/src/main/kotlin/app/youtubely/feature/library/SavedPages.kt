// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.LocalPlaylist
import app.youtubely.core.model.SavedVideo
import app.youtubely.core.model.VideoId
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.motionItem
import app.youtubely.core.ui.videoFrameKey
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList

private const val ORIGIN_LATER = "later"

/** Videos put aside for later. [onPlayAll] starts the first one and plays the rest in order. */
@Composable
internal fun WatchLaterPage(
    videos: ImmutableList<SavedVideo>?,
    onOpenVideo: (VideoId, String) -> Unit,
    onPlayAll: (VideoId, String) -> Unit,
    onRemove: (SavedVideo) -> Unit,
) {
    if (videos == null) {
        LoadingState()
        return
    }
    if (videos.isEmpty()) {
        EmptyPage(YouTubelyIcons.Schedule, R.string.library_watch_later_empty)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item(key = "play-all") {
            FilledTonalButton(
                onClick = {
                    val first = videos.first().item.id
                    onPlayAll(first, videoFrameKey(ORIGIN_LATER, first))
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).then(motionItem()),
            ) {
                Icon(painterResource(YouTubelyIcons.PlaylistPlay), contentDescription = null)
                Text(stringResource(R.string.library_play_all), Modifier.padding(start = 8.dp))
            }
        }
        items(videos, key = { it.item.id.value }) { video ->
            SwipeToRemove(onRemove = { onRemove(video) }, modifier = motionItem()) {
                val key = videoFrameKey(ORIGIN_LATER, video.item.id)
                VideoRow(
                    item = video.item,
                    sharedKey = key,
                    onClick = { onOpenVideo(video.item.id, key) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Playlists made on this device, with a button to start a new one. */
@Composable
internal fun PlaylistsPage(
    playlists: ImmutableList<LocalPlaylist>?,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    if (playlists == null) {
        LoadingState()
        return
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "new") {
            ListItem(
                onClick = { creating = true },
                leadingContent = {
                    Icon(
                        painterResource(YouTubelyIcons.Add),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(PLAYLIST_THUMBNAIL),
                    )
                },
                modifier = motionItem(),
            ) { Text(stringResource(R.string.library_new_playlist), color = MaterialTheme.colorScheme.primary) }
        }
        if (playlists.isEmpty()) {
            item(key = "empty") { EmptyPage(YouTubelyIcons.PlaylistPlay, R.string.library_playlists_empty) }
        }
        items(playlists, key = { it.id }) { playlist ->
            ListItem(
                onClick = { onOpen(playlist.id) },
                supportingContent = { Text(stringResource(R.string.library_videos_count, playlist.itemCount)) },
                leadingContent = { PlaylistCover(playlist.thumbnailUrl) },
                modifier = motionItem(),
            ) { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (creating) {
        NameDialog(
            title = R.string.library_new_playlist,
            confirm = R.string.library_create,
            initial = "",
            onDismiss = { creating = false },
            onConfirm = {
                creating = false
                onCreate(it)
            },
        )
    }
}

private val PLAYLIST_THUMBNAIL = 56.dp

@Composable
private fun PlaylistCover(url: String?) {
    val shape = MaterialTheme.shapes.medium
    if (url == null) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(PLAYLIST_THUMBNAIL).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(YouTubelyIcons.PlaylistPlay), contentDescription = null)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(PLAYLIST_THUMBNAIL).clip(shape),
        )
    }
}

/** A dialog with one text field, used to name or rename a playlist. */
@Composable
internal fun NameDialog(
    title: Int,
    confirm: Int,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.library_playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}
