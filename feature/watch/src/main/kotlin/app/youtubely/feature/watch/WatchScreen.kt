// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.VideoId
import app.youtubely.core.ui.ChannelAvatar
import app.youtubely.core.ui.ErrorState
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.VerifiedBadge
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.compactNumber
import app.youtubely.core.ui.currentLocale
import app.youtubely.core.ui.formatDuration
import app.youtubely.core.ui.sharedVideoFrame
import app.youtubely.core.ui.videoFrameKey
import app.youtubely.core.ui.viewCountText

private const val VIDEO_ASPECT = 16f / 9f
private const val COLLAPSED_DESCRIPTION_LINES = 3
private val TwoPaneMinWidth = 840.dp

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun WatchScreen(
    videoId: VideoId,
    sharedKey: String,
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<WatchViewModel, WatchViewModel.Factory>(key = videoId.value) {
            it.create(videoId.value)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val player = viewModel.player
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalActivity.current
    val isPlaying = rememberIsPlaying(player)
    val inPip = rememberIsInPictureInPicture()
    val pipEnabled = isPlaying && settings.pictureInPicture

    // The screen stays on only while the video actually plays.
    KeepScreenOn(isPlaying && !inPip)
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

    val playerView = @Composable { playerModifier: Modifier ->
        VideoPlayer(
            player = player,
            fullscreen = landscape,
            onToggleFullscreen = ::toggleFullscreen,
            onOpenSettings = { showSettings = true },
            modifier = playerModifier.pictureInPicture(pipEnabled),
        )
    }

    if (landscape) {
        playerView(modifier.fillMaxSize())
    } else {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            if (twoPane) {
                Row(Modifier.fillMaxSize().statusBarsPadding()) {
                    Column(Modifier.weight(1.6f)) {
                        playerView(Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT).sharedVideoFrame(sharedKey))
                        WatchBody(state, viewModel::retry, player, onOpenVideo, includeRelated = false)
                    }
                    Column(Modifier.weight(1f)) {
                        (state as? WatchUiState.Content)?.let { RelatedList(it.info, onOpenVideo) }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.background(Color.Black).statusBarsPadding()) {
                        playerView(Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT).sharedVideoFrame(sharedKey))
                    }
                    WatchBody(state, viewModel::retry, player, onOpenVideo, includeRelated = true)
                }
            }
        }
    }

    if (showSettings) {
        PlayerSettingsSheet(
            player = player,
            pinnedHeight = viewModel.pinnedQuality,
            networkLimit = rememberNetworkLimit(settings),
            onQuality = viewModel::setQuality,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun WatchBody(
    state: WatchUiState,
    onRetry: () -> Unit,
    player: Player,
    onOpenVideo: (VideoId, String) -> Unit,
    includeRelated: Boolean,
) {
    when (state) {
        WatchUiState.Loading -> {
            LoadingState()
        }

        is WatchUiState.Error -> {
            ErrorState(state.error, onRetry)
        }

        is WatchUiState.Content -> {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                item(key = "details") { Details(state.info, onSeek = { player.seekTo(it) }) }
                if (includeRelated) related(state.info, onOpenVideo)
            }
        }
    }
}

@Composable
private fun RelatedList(
    info: StreamInfo,
    onOpenVideo: (VideoId, String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) { related(info, onOpenVideo) }
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
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun Details(
    info: StreamInfo,
    onSeek: (Long) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelAvatar(channel.avatars, contentDescription = null, size = 44.dp)
                Text(channel.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
                if (channel.isVerified) VerifiedBadge()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
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
        if (info.description.isNotBlank()) Description(info.description)
    }
}

@Composable
private fun Description(text: String) {
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
            Text(
                text,
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

/** The resolution cap that applies on the current network, shown next to "Auto". */
@Composable
private fun rememberNetworkLimit(settings: PlaybackSettings): Int? {
    val context = LocalContext.current
    val metered =
        remember {
            context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
        }
    return if (metered) settings.maxHeightMetered else settings.maxHeightUnmetered
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
