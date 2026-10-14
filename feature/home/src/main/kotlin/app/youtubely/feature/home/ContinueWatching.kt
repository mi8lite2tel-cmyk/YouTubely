// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youtubely.core.model.HistoryEntry
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import app.youtubely.core.ui.formatDuration
import app.youtubely.core.ui.motionItem
import app.youtubely.core.ui.sharedVideoFrame
import app.youtubely.core.ui.videoFrameKey
import coil3.compose.AsyncImage
import java.time.Duration

private const val CONTINUE_ORIGIN = "continue"
private const val THUMBNAIL_WIDTH = 480

/** Started videos as a row of cards with how much is left; a tap continues where it stopped. */
@Composable
internal fun ContinueWatchingRow(
    entries: List<HistoryEntry>,
    onOpenVideo: (VideoId, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.home_continue),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.item.id.value }) { entry ->
                ContinueCard(entry, onOpenVideo, motionItem())
            }
        }
    }
}

@Composable
private fun ContinueCard(
    entry: HistoryEntry,
    onOpenVideo: (VideoId, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = entry.item
    val key = videoFrameKey(CONTINUE_ORIGIN, item.id)
    val left = item.duration?.minus(Duration.ofMillis(entry.positionMs))
    Column(
        modifier
            .width(232.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable { onOpenVideo(item.id, key) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large)) {
            AsyncImage(
                model = item.thumbnails.bestFor(THUMBNAIL_WIDTH)?.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).sharedVideoFrame(key),
            )
            LinearProgressIndicator(
                progress = { entry.progress ?: 0f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.4f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (left != null && !left.isNegative) {
            Text(
                stringResource(R.string.home_continue_left, formatDuration(left)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
