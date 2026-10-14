// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import app.youtubely.core.model.Chapter
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.SponsorSegment
import kotlinx.coroutines.delay

private const val TICK_MS = 500L
private const val SUBTITLE_BASE_SP = 16f

/** What the player shows besides the picture: chapters, SponsorBlock marks and subtitle style. */
internal class PlayerExtras(
    val chapters: List<Chapter> = emptyList(),
    val segments: List<SponsorSegment> = emptyList(),
    val skipNotice: SkipNotice? = null,
    val onUndoSkip: () -> Unit = {},
    val settings: PlaybackSettings = PlaybackSettings(),
)

/** The standard SponsorBlock colours of the categories. */
internal fun categoryColor(category: String): Color =
    when (category) {
        "sponsor" -> Color(0xFF00D400)
        "selfpromo" -> Color(0xFFFFFF00)
        "interaction" -> Color(0xFFCC00FF)
        "intro" -> Color(0xFF00FFFF)
        "outro" -> Color(0xFF0202ED)
        "preview" -> Color(0xFF008FD6)
        "filler" -> Color(0xFF7300FF)
        else -> Color(0xFFFF9900)
    }

@Composable
internal fun categoryLabel(category: String): String =
    when (category) {
        "sponsor" -> stringResource(R.string.sb_sponsor)
        "selfpromo" -> stringResource(R.string.sb_selfpromo)
        "interaction" -> stringResource(R.string.sb_interaction)
        "intro" -> stringResource(R.string.sb_intro)
        "outro" -> stringResource(R.string.sb_outro)
        "preview" -> stringResource(R.string.sb_preview)
        "filler" -> stringResource(R.string.sb_filler)
        else -> stringResource(R.string.sb_music_offtopic)
    }

/** Position and duration of [player], refreshed twice a second while this is composed. */
@Composable
internal fun rememberPlaybackClock(player: Player): Pair<Long, Long> {
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration
            delay(TICK_MS)
        }
    }
    return position to duration
}

/** The title of the chapter that [positionMs] is in, or `null` without chapters. */
internal fun List<Chapter>.titleAt(positionMs: Long): String? = lastOrNull { it.start.toMillis() <= positionMs }?.title

/**
 * A thin strip above the seek bar: chapter boundaries and the stretches SponsorBlock marks, drawn
 * to the scale of the video.
 */
@Composable
internal fun SeekMarkers(
    durationMs: Long,
    chapters: List<Chapter>,
    segments: List<SponsorSegment>,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0 || (chapters.isEmpty() && segments.isEmpty())) return
    Canvas(modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics {}) {
        val scale = size.width / durationMs
        segments.forEach { segment ->
            drawRect(
                categoryColor(segment.category),
                topLeft = Offset(segment.startMs * scale, 0f),
                size = Size(((segment.endMs - segment.startMs) * scale).coerceAtLeast(2f), size.height),
            )
        }
        chapters.drop(1).forEach { chapter ->
            drawRect(
                Color.Black.copy(alpha = 0.8f),
                topLeft = Offset(chapter.start.toMillis() * scale - 1f, 0f),
                size = Size(2f, size.height),
            )
        }
    }
}

/** Subtitles of the current video, in the size and with the background the user chose. */
@Composable
internal fun SubtitleOverlay(
    player: Player,
    settings: PlaybackSettings,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    text = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (text.isBlank() || !settings.subtitlesEnabled) return
    val fontSize = (SUBTITLE_BASE_SP * settings.subtitleScale).sp
    Surface(
        color = if (settings.subtitleBackground) Color.Black.copy(alpha = 0.7f) else Color.Transparent,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style =
                TextStyle(
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.25f,
                    textAlign = TextAlign.Center,
                    shadow =
                        if (settings.subtitleBackground) {
                            null
                        } else {
                            androidx.compose.ui.graphics
                                .Shadow(Color.Black, Offset(0f, 0f), 6f)
                        },
                ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** "Skipped: sponsor" with a button to go back, shown for a few seconds after an automatic skip. */
@Composable
internal fun SkipNoticeChip(
    notice: SkipNotice,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        ) {
            Text(
                stringResource(R.string.sb_skipped, categoryLabel(notice.segment.category)),
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onUndo) {
                Text(stringResource(R.string.sb_undo), color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}
