// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.actions

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.StreamItem

/**
 * What can be done with a video from any list: put it aside for later, add it to a playlist of
 * this device, open its channel or share it.
 */
@Composable
fun VideoActionsSheet(
    item: StreamItem,
    onDismiss: () -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoActionsViewModel = hiltViewModel(),
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = picking,
            transitionSpec = {
                val forward = targetState
                (
                    slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 4 else -it / 4 } +
                        fadeIn(motion.defaultEffectsSpec())
                ).togetherWith(
                    slideOutHorizontally(motion.defaultSpatialSpec()) { if (forward) -it / 4 else it / 4 } +
                        fadeOut(motion.fastEffectsSpec()),
                )
            },
            label = "videoActions",
        ) { choosing ->
            if (choosing) {
                PlaylistPicker(item = item, viewModel = viewModel, onBack = { picking = false })
            } else {
                ActionList(
                    item = item,
                    viewModel = viewModel,
                    onChoosePlaylist = { picking = true },
                    onOpenChannel = {
                        onDismiss()
                        onOpenChannel(it)
                    },
                    onDone = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ActionList(
    item: StreamItem,
    viewModel: VideoActionsViewModel,
    onChoosePlaylist: () -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onDone: () -> Unit,
) {
    val saved by viewModel.savedForLater.collectAsStateWithLifecycle()
    val isSaved = item.id in saved
    val context = LocalContext.current
    Column(Modifier.padding(bottom = 16.dp)) {
        ListItem(
            headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = item.channel?.let { { Text(it.name) } },
        )
        ActionItem(
            text = if (isSaved) R.string.actions_remove_watch_later else R.string.actions_watch_later,
            icon = if (isSaved) YouTubelyIcons.Check else YouTubelyIcons.Schedule,
        ) { viewModel.toggleWatchLater(item) }
        ActionItem(
            text = R.string.actions_save_to_playlist,
            icon = YouTubelyIcons.PlaylistAdd,
            onClick = onChoosePlaylist,
        )
        item.channel?.let { channel ->
            ActionItem(text = R.string.actions_open_channel, icon = YouTubelyIcons.Subscribed) {
                onOpenChannel(channel.id)
            }
        }
        ActionItem(text = R.string.actions_share, icon = YouTubelyIcons.Share) {
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${item.id.value}")
            context.startActivity(Intent.createChooser(send, null))
            onDone()
        }
    }
}

@Composable
private fun ActionItem(
    text: Int,
    icon: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(text)) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun PlaylistPicker(
    item: StreamItem,
    viewModel: VideoActionsViewModel,
    onBack: () -> Unit,
) {
    val playlists by viewModel.playlistList.collectAsStateWithLifecycle()
    val containing by remember(item.id) { viewModel.containing(item.id) }.collectAsState(initial = emptySet())
    var creating by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 16.dp)) {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.actions_save_to_playlist), style = MaterialTheme.typography.titleMedium)
            },
            leadingContent = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.actions_back))
                }
            },
        )
        ActionItem(text = R.string.actions_new_playlist, icon = YouTubelyIcons.Add) { creating = true }
        LazyColumn {
            items(playlists, key = { it.id }) { playlist ->
                val present = playlist.id in containing
                ListItem(
                    headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(stringResource(R.string.actions_videos_count, playlist.itemCount)) },
                    trailingContent = { Checkbox(checked = present, onCheckedChange = null) },
                    modifier = Modifier.clickable { viewModel.setInPlaylist(playlist.id, item, !present) },
                )
            }
        }
    }
    if (creating) {
        NewPlaylistDialog(
            onDismiss = { creating = false },
            onCreate = {
                creating = false
                viewModel.createPlaylist(it, item)
            },
        )
    }
}

@Composable
private fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.actions_new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.actions_playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.actions_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) } },
    )
}
