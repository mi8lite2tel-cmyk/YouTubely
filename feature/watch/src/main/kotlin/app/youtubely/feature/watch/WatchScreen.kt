// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import app.youtubely.core.ui.ChannelAvatar
import app.youtubely.core.ui.ErrorState
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.LocalVideoActions
import app.youtubely.core.ui.StateTransition
import app.youtubely.core.ui.SubscribeButton
import app.youtubely.core.ui.VerifiedBadge
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.compactNumber
import app.youtubely.core.ui.currentLocale
import app.youtubely.core.ui.formatDuration
import app.youtubely.core.ui.motionItem
import app.youtubely.core.ui.sharedVideoFrame
import app.youtubely.core.ui.videoFrameKey
import app.youtubely.core.ui.viewCountText

private const val VIDEO_ASPECT = 16f / 9f
private const val COLLAPSED_DESCRIPTION_LINES = 3
private const val ARTWORK_WIDTH = 720
private val TwoPaneMinWidth = 840.dp

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun WatchScreen(
    videoId: VideoId,
    sharedKey: String,
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
    playlistId: PlaylistId? = null,
) {
    val viewModel =
        hiltViewModel<WatchViewModel, WatchViewModel.Factory>(key = "${videoId.value}/${playlistId?.value}") {
            it.create(videoId.value, playlistId?.value)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val subscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    val sponsorSegments by viewModel.sponsorSegments.collectAsStateWithLifecycle()
    val skipNotice by viewModel.skipNotice.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val abLoop by viewModel.abLoop.collectAsStateWithLifecycle()
    val player = viewModel.player
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalActivity.current
    val isPlaying = rememberIsPlaying(player)
    val inPip = rememberIsInPictureInPicture()
    val pipEnabled = isPlaying && settings.pictureInPicture && !settings.audioOnly

    // The screen stays on only while a picture actually plays.
    KeepScreenOn(isPlaying && !inPip && !settings.audioOnly)
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose {
            // Without background playback, leaving the app pauses (PiP keeps the activity started).
            if (!settings.backgroundPlayback && activity?.isChangingConfigurations != true) player.pause()
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.onHidden() } }
    ImmersiveWhen(landscape && !inPip)

    fun toggleFullscreen() {
        activity?.requestedOrientation =
            if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
    }
    // Leaving fullscreen with back returns to the portrait layout instead of closing the video.
    BackHandler(enabled = landscape) { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    if (inPip) {
        // The PiP window shows only the picture; the system adds play/pause from the media session.
        ContentFrame(player = player, modifier = modifier.fillMaxSize())
        return
    }

    val content = state as? WatchUiState.Content
    val artworkUrl =
        content
            ?.info
            ?.item
            ?.thumbnails
            ?.bestFor(ARTWORK_WIDTH)
            ?.url
    val playerView = @Composable { playerModifier: Modifier ->
        VideoPlayer(
            player = player,
            fullscreen = landscape,
            onToggleFullscreen = ::toggleFullscreen,
            onOpenSettings = { showSettings = true },
            modifier = playerModifier.pictureInPicture(pipEnabled),
            seekStepSeconds = settings.seekStepSeconds,
            audioOnly = settings.audioOnly,
            onToggleAudioOnly = { viewModel.setAudioOnly(!settings.audioOnly) },
            artworkUrl = artworkUrl,
            extras =
                PlayerExtras(
                    chapters = content?.info?.chapters.orEmpty(),
                    segments = sponsorSegments,
                    skipNotice = skipNotice,
                    onUndoSkip = viewModel::undoSkip,
                    settings = settings,
                    onViewport = viewModel.playbackManager::setViewport,
                ),
        )
    }
    val body = @Composable { includeRelated: Boolean ->
        WatchBody(
            state = state,
            onRetry = viewModel::retry,
            player = player,
            includeRelated = includeRelated,
            actions =
                WatchActions(
                    onOpenVideo = onOpenVideo,
                    onOpenChannel = onOpenChannel,
                    onToggleSubscription = viewModel::toggleSubscription,
                    onPlayQueueItem = viewModel::playQueueItem,
                    formatsFor = viewModel::downloadFormats,
                    onDownload = viewModel::download,
                ),
            downloads = downloads,
            queue = queue,
            subscribed = subscribed,
        )
    }

    WatchLayout(
        landscape = landscape,
        sharedKey = sharedKey,
        playerView = playerView,
        body = body,
        sidePane = {
            content?.let { RelatedList(it.info, queue, onOpenVideo, viewModel::playQueueItem) }
        },
        modifier = modifier,
    )

    if (showSettings) {
        val qualityCap by viewModel.playbackManager.qualityCap.collectAsStateWithLifecycle()
        PlayerSettingsSheet(
            player = player,
            state = PlayerSheetState(viewModel.pinnedQuality, qualityCap, settings, sleepTimer, abLoop),
            actions = remember(viewModel) { viewModel.playbackManager.sheetActions(context) },
            onQuality = viewModel::setQuality,
            onDismiss = { showSettings = false },
        )
    }
}

/**
 * Fullscreen player in landscape; otherwise the player above the details, with related videos in
 * a second pane on wide windows.
 */
@Composable
private fun WatchLayout(
    landscape: Boolean,
    sharedKey: String,
    playerView: @Composable (Modifier) -> Unit,
    body: @Composable (includeRelated: Boolean) -> Unit,
    sidePane: @Composable () -> Unit,
    modifier: Modifier,
) {
    if (landscape) {
        playerView(modifier.fillMaxSize())
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {
                Column(Modifier.weight(1.6f)) {
                    playerView(Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT).sharedVideoFrame(sharedKey))
                    body(false)
                }
                Column(Modifier.weight(1f)) { sidePane() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.background(Color.Black).statusBarsPadding()) {
                    playerView(Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT).sharedVideoFrame(sharedKey))
                }
                body(true)
            }
        }
    }
}

/** Callbacks of the watch page below the player. */
private class WatchActions(
    val onOpenVideo: (VideoId, String) -> Unit,
    val onOpenChannel: (ChannelId) -> Unit,
    val onToggleSubscription: (ChannelRef) -> Unit,
    val onPlayQueueItem: (Int) -> Unit,
    val formatsFor: (StreamInfo) -> List<DownloadFormat>,
    val onDownload: (DownloadFormat) -> Unit,
)

@Composable
private fun WatchBody(
    state: WatchUiState,
    onRetry: () -> Unit,
    player: Player,
    includeRelated: Boolean,
    actions: WatchActions,
    downloads: List<Download>,
    queue: PlaylistQueue?,
    subscribed: Boolean,
) {
    StateTransition(state, label = "watchBody") { target ->
        when (target) {
            WatchUiState.Loading -> {
                LoadingState()
            }

            is WatchUiState.Error -> {
                ErrorState(target.error, onRetry)
            }

            is WatchUiState.Content -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    item(key = "details") {
                        Details(
                            info = target.info,
                            onSeek = { player.seekTo(it) },
                            downloadFormats = remember(target.info) { actions.formatsFor(target.info) },
                            downloads = downloads,
                            actions = actions,
                            subscribed = subscribed,
                        )
                    }
                    if (includeRelated) {
                        queue(queue, actions.onPlayQueueItem)
                        related(target.info, actions.onOpenVideo)
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedList(
    info: StreamInfo,
    queue: PlaylistQueue?,
    onOpenVideo: (VideoId, String) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        queue(queue, onPlayQueueItem)
        related(info, onOpenVideo)
    }
}

/** The playlist the video was opened from, with the playing item highlighted. */
private fun LazyListScope.queue(
    queue: PlaylistQueue?,
    onPlay: (Int) -> Unit,
) {
    if (queue == null) return
    item(key = "queue-header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(painterResource(YouTubelyIcons.PlaylistPlay), contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    queue.playlist.name.ifBlank { stringResource(R.string.watch_queue_later) },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.watch_queue_position, queue.index + 1, queue.items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            queue.next?.let {
                FilledTonalIconButton(onClick = { onPlay(queue.index + 1) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(YouTubelyIcons.SkipNext), stringResource(R.string.watch_next))
                }
            }
        }
    }
    itemsIndexed(queue.items, key = { index, item -> "queue-$index-${item.id.value}" }) { index, item ->
        val playing = index == queue.index
        VideoRow(
            item = item,
            sharedKey = "queue-$index",
            onClick = { if (!playing) onPlay(index) },
            modifier = motionItem().padding(horizontal = 4.dp),
            containerColor =
                if (playing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        )
    }
}

private fun LazyListScope.related(
    info: StreamInfo,
    onOpenVideo: (VideoId, String) -> Unit,
) {
    if (info.related.isEmpty()) return
    item(key = "related-header") {
        Text(
            stringResource(R.string.watch_related),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
    items(info.related, key = { "related-${it.id.value}" }) { item ->
        val key = videoFrameKey("related-${info.item.id.value}", item.id)
        VideoRow(
            item = item,
            sharedKey = key,
            onClick = { onOpenVideo(item.id, key) },
            modifier = motionItem(),
        )
    }
}

@Composable
private fun Details(
    info: StreamInfo,
    onSeek: (Long) -> Unit,
    downloadFormats: List<DownloadFormat>,
    downloads: List<Download>,
    actions: WatchActions,
    subscribed: Boolean,
) {
    val context = LocalContext.current
    val item = info.item
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(item.title, style = MaterialTheme.typography.titleLargeEmphasized)
        Text(
            listOfNotNull(item.viewCount?.let { viewCountText(it) }, item.uploadedAtText).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.channel?.let { channel ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.large)
                            .clickable(role = Role.Button) { actions.onOpenChannel(channel.id) }
                            .padding(vertical = 4.dp),
                ) {
                    ChannelAvatar(channel.avatars, contentDescription = null, size = 44.dp)
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channel.isVerified) VerifiedBadge()
                }
                SubscribeButton(subscribed = subscribed, onClick = { actions.onToggleSubscription(channel) })
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            info.likeCount?.let { likes ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(compactNumber(likes, currentLocale())) },
                    leadingIcon = { Icon(painterResource(YouTubelyIcons.ThumbUp.outlined), null) },
                )
            }
            FilledTonalButton(
                onClick = {
                    val link = "https://www.youtube.com/watch?v=${item.id.value}"
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                    context.startActivity(Intent.createChooser(send, null))
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(painterResource(YouTubelyIcons.Share), contentDescription = null)
                Text(stringResource(R.string.watch_share), Modifier.padding(start = 8.dp))
            }
            LocalVideoActions.current?.let { open ->
                FilledTonalButton(onClick = { open(item) }, shapes = ButtonDefaults.shapes()) {
                    Icon(painterResource(YouTubelyIcons.BookmarkAdd), contentDescription = null)
                    Text(stringResource(R.string.watch_save), Modifier.padding(start = 8.dp))
                }
            }
        }
        DownloadButton(formats = downloadFormats, downloads = downloads, onDownload = actions.onDownload)
        if (info.chapters.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(info.chapters) { chapter ->
                    AssistChip(
                        onClick = { onSeek(chapter.start.toMillis()) },
                        label = {
                            Text(
                                "${formatDuration(chapter.start)}  ${chapter.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
        if (info.description.isNotBlank()) Description(info.description, onSeek)
    }
}

@Composable
private fun Description(
    text: String,
    onSeek: (Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(Modifier.padding(12.dp)) {
            val linkColor = MaterialTheme.colorScheme.primary
            Text(
                remember(text, linkColor) { linkify(text, linkColor, onSeek) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (expanded) R.string.watch_show_less else R.string.watch_show_more),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun rememberIsPlaying(player: Player): Boolean {
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return playing
}

/** Hides the system bars while the player fills the screen; swiping from an edge shows them briefly. */
@Composable
private fun ImmersiveWhen(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(enabled) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
