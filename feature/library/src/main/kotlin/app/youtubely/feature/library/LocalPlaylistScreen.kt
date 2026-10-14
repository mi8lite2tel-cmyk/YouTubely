// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.VideoId
import app.youtubely.core.ui.DownloadAllButton
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.motionItem
import app.youtubely.core.ui.videoFrameKey
import kotlinx.coroutines.launch

private const val ORIGIN = "local-playlist"

/** A playlist made on this device: play it, rename it, delete it, remove videos by swiping. */
@Composable
fun LocalPlaylistScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    onPlayAll: (VideoId, sharedKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<LocalPlaylistViewModel, LocalPlaylistViewModel.Factory>(key = "local-$playlistId") {
            it.create(playlistId)
        }
    val name by viewModel.name.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var seen by remember { mutableStateOf(false) }

    // The playlist was deleted from this screen: leave it.
    LaunchedEffect(name) {
        if (name != null) {
            seen = true
        } else if (seen) {
            onBack()
        }
    }

    val removedMessage = stringResource(R.string.library_removed)
    val undoLabel = stringResource(R.string.library_undo)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(name.orEmpty()) },
                subtitle = { Text(stringResource(R.string.library_videos_count, items?.size ?: 0)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.library_back))
                    }
                },
                actions = {
                    items?.takeIf { it.isNotEmpty() }?.let { DownloadAllButton(it.size, viewModel::downloadAll) }
                    PlaylistMenu(onRename = { renaming = true }, onDelete = { deleting = true })
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val list = items
        when {
            list == null -> {
                LoadingState(Modifier.padding(padding))
            }

            list.isEmpty() -> {
                Box(Modifier.padding(top = padding.calculateTopPadding())) {
                    EmptyPage(YouTubelyIcons.PlaylistPlay, R.string.library_playlist_empty)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp,
                        ),
                ) {
                    item(key = "play-all") {
                        FilledTonalButton(
                            onClick = {
                                val first = list.first().id
                                onPlayAll(first, videoFrameKey(ORIGIN, first))
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).then(motionItem()),
                        ) {
                            Icon(painterResource(YouTubelyIcons.PlaylistPlay), contentDescription = null)
                            Text(stringResource(R.string.library_play_all), Modifier.padding(start = 8.dp))
                        }
                    }
                    items(list, key = { it.id.value }) { item ->
                        SwipeToRemove(
                            onRemove = {
                                viewModel.remove(item)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result =
                                        snackbar.showSnackbar(
                                            removedMessage.format(item.title),
                                            undoLabel,
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short,
                                        )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(item)
                                }
                            },
                            modifier = motionItem(),
                        ) {
                            val key = videoFrameKey(ORIGIN, item.id)
                            VideoRow(
                                item = item,
                                sharedKey = key,
                                onClick = { onOpenVideo(item.id, key) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = R.string.library_rename,
            confirm = R.string.library_save,
            initial = name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                viewModel.rename(it)
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.library_delete_playlist)) },
            text = { Text(stringResource(R.string.library_delete_playlist_body, name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.library_cancel)) }
            },
        )
    }
}

@Composable
private fun PlaylistMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(YouTubelyIcons.MoreVert), stringResource(R.string.library_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_rename)) },
                leadingIcon = { Icon(painterResource(YouTubelyIcons.Edit), contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_delete_playlist)) },
                leadingIcon = { Icon(painterResource(YouTubelyIcons.Delete), contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
