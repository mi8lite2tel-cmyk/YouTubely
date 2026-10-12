// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.downloads

import android.text.format.Formatter
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.DownloadState
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.formatDuration
import coil3.compose.AsyncImage
import java.io.File

private const val THUMBNAIL_ASPECT = 16f / 9f

@Composable
fun DownloadsScreen(
    onPlay: (downloadId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                subtitle = { Text(stringResource(R.string.downloads_swipe_hint)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val list = downloads
        when {
            list == null -> {
                LoadingState(Modifier.padding(padding))
            }

            list.isEmpty() -> {
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
                    items(list, key = { it.id }) { download ->
                        DownloadRow(
                            download = download,
                            onPlay = { onPlay(download.id) },
                            onPause = { viewModel.pause(download.id) },
                            onResume = { viewModel.resume(download.id) },
                            onDelete = { viewModel.delete(download.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    download: Download,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
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
                    if (download.state == DownloadState.Downloading || download.state == DownloadState.Merging) {
                        if (download.state == DownloadState.Merging) {
                            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearWavyProgressIndicator(
                                progress = { download.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                ActionButton(download, onPause, onResume, Modifier.align(Alignment.CenterVertically))
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
            stringResource(R.string.downloads_state_failed)
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
