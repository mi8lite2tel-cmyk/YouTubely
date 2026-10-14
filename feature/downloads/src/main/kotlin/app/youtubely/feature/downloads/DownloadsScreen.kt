// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.downloads

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.StorageUsage
import app.youtubely.core.model.VideoId
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.formatDuration
import app.youtubely.core.ui.motionItem
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

private const val THUMBNAIL_ASPECT = 16f / 9f

/** Keeps a sliver of the bar visible when downloads take almost no space. */
private const val MIN_VISIBLE_SHARE = 0.01f

@Composable
fun DownloadsScreen(
    onPlay: (downloadId: String) -> Unit,
    onOpenOnline: (VideoId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val waitingForWifi by viewModel.waitingForWifi.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.downloads_deleted)
    val undoLabel = stringResource(R.string.downloads_undo)

    fun delete(download: Download) {
        viewModel.requestDelete(download.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            var undone = false
            try {
                undone =
                    snackbar.showSnackbar(
                        message = deletedMessage.format(download.title),
                        actionLabel = undoLabel,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    ) == SnackbarResult.ActionPerformed
            } finally {
                // Also runs when the screen goes away while the snackbar is still shown.
                if (undone) viewModel.undoDelete(download.id) else viewModel.commitDelete(download.id)
            }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                subtitle = { Text(stringResource(R.string.downloads_swipe_hint)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val list = state.downloads
        when {
            list == null -> {
                LoadingState(Modifier.padding(padding))
            }

            state.isEmpty -> {
                EmptyDownloads(Modifier.padding(padding))
            }

            else -> {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (waitingForWifi) {
                        item(key = "wifi") {
                            WifiOnlyCard(onDownloadNow = viewModel::downloadNow, modifier = motionItem())
                        }
                    }
                    storage?.let { usage -> item(key = "storage") { StorageCard(usage) } }
                    item(key = "filters") { Filters(state.filter, viewModel::setFilter) }
                    if (list.isEmpty()) {
                        item(key = "no-match") {
                            Text(
                                stringResource(R.string.downloads_no_match),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { download ->
                        DownloadRow(
                            download = download,
                            onPlay = { onPlay(download.id) },
                            onPause = { viewModel.pause(download.id) },
                            onResume = { viewModel.resume(download.id) },
                            onDelete = { delete(download) },
                            onOpenOnline = { onOpenOnline(download.videoId) },
                            modifier = motionItem(),
                        )
                    }
                }
            }
        }
    }
}

/** Explains why downloads do not start on mobile data and lets the user go ahead anyway. */
@Composable
private fun WifiOnlyCard(
    onDownloadNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.downloads_wifi_title), style = MaterialTheme.typography.titleSmallEmphasized)
            Text(stringResource(R.string.downloads_wifi_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDownloadNow, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.downloads_wifi_action))
            }
        }
    }
}

/** Used by downloads against what is left on the device. */
@Composable
private fun StorageCard(usage: StorageUsage) {
    val context = LocalContext.current
    val total = (usage.usedBytes + usage.freeBytes).coerceAtLeast(1)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(painterResource(YouTubelyIcons.Storage), contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.downloads_storage,
                        Formatter.formatShortFileSize(context, usage.usedBytes),
                        Formatter.formatShortFileSize(context, usage.freeBytes),
                    ),
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                )
                LinearProgressIndicator(
                    progress = { (usage.usedBytes.toFloat() / total).coerceAtLeast(MIN_VISIBLE_SHARE) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Filters(
    selected: DownloadFilter,
    onSelect: (DownloadFilter) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DownloadFilter.entries) { filter ->
            ExpressiveFilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = stringResource(filter.label),
            )
        }
    }
}

private val DownloadFilter.label: Int
    get() =
        when (this) {
            DownloadFilter.All -> R.string.downloads_filter_all
            DownloadFilter.Video -> R.string.downloads_filter_video
            DownloadFilter.Audio -> R.string.downloads_filter_audio
            DownloadFilter.InProgress -> R.string.downloads_filter_in_progress
        }

@Composable
private fun DownloadRow(
    download: Download,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpenOnline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(YouTubelyIcons.Delete),
                    contentDescription = stringResource(R.string.downloads_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        modifier = modifier,
    ) {
        Surface(
            onClick = onPlay,
            enabled = download.state == DownloadState.Completed,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumbnail(download, Modifier.width(144.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        download.title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText(download),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (download.state == DownloadState.Failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 2,
                    )
                    val busy = download.state == DownloadState.Downloading || download.state == DownloadState.Merging
                    // The bar grows and shrinks with the row instead of popping in and out.
                    val motion = MaterialTheme.motionScheme
                    AnimatedVisibility(
                        visible = busy,
                        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
                    ) {
                        if (download.state == DownloadState.Merging) {
                            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            // Progress arrives in whole percents; glide between them.
                            val progress by animateFloatAsState(
                                targetValue = download.progress,
                                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                label = "downloadProgress",
                            )
                            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (download.state == DownloadState.Completed) {
                    ItemMenu(onOpenOnline, onDelete, Modifier.align(Alignment.CenterVertically))
                } else {
                    ActionButton(download, onPause, onResume, Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(
    download: Download,
    modifier: Modifier,
) {
    Box(
        modifier
            .aspectRatio(THUMBNAIL_ASPECT)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = download.thumbnailPath?.let(::File),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val badge =
            when (val format = download.format) {
                DownloadFormat.Audio -> stringResource(R.string.downloads_audio)
                is DownloadFormat.Video -> "${format.height}p"
            }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        ) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmallEmphasized,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ItemMenu(
    onOpenOnline: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(YouTubelyIcons.MoreVert), stringResource(R.string.downloads_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_open_online)) },
                leadingIcon = { Icon(painterResource(YouTubelyIcons.Video), contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenOnline()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_delete)) },
                leadingIcon = { Icon(painterResource(YouTubelyIcons.Delete), contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun ActionButton(
    download: Download,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier,
) {
    val (icon, description, action) =
        when (download.state) {
            DownloadState.Queued, DownloadState.Downloading -> {
                Triple(YouTubelyIcons.PauseOutline, R.string.downloads_pause, onPause)
            }

            DownloadState.Paused, DownloadState.Failed -> {
                Triple(YouTubelyIcons.Refresh, R.string.downloads_resume, onResume)
            }

            DownloadState.Merging, DownloadState.Completed -> {
                return
            }
        }
    FilledTonalIconButton(onClick = action, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
        Icon(painterResource(icon), contentDescription = stringResource(description))
    }
}

@Composable
private fun statusText(download: Download): String {
    val context = LocalContext.current
    val size = download.totalBytes?.let { Formatter.formatShortFileSize(context, it) }
    return when (download.state) {
        DownloadState.Queued -> {
            stringResource(R.string.downloads_state_queued)
        }

        DownloadState.Downloading -> {
            stringResource(R.string.downloads_state_downloading, (download.progress * 100).toInt(), size.orEmpty())
        }

        DownloadState.Merging -> {
            stringResource(R.string.downloads_state_merging)
        }

        DownloadState.Paused -> {
            stringResource(R.string.downloads_state_paused)
        }

        DownloadState.Failed -> {
            stringResource(R.string.downloads_state_failed) + download.error?.let { "\n$it" }.orEmpty()
        }

        DownloadState.Completed -> {
            listOfNotNull(
                download.channelName,
                download.duration?.let(::formatDuration),
                size,
            ).joinToString(" · ")
        }
    }
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(104.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(YouTubelyIcons.Download),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.downloads_empty_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.downloads_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
