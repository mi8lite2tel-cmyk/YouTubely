// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.data.TrafficMeter
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.TrafficSummary
import app.youtubely.core.model.WatchStats
import app.youtubely.core.ui.ChannelAvatar
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.groupedListItemColors
import app.youtubely.core.ui.groupedListItemShapes
import app.youtubely.core.ui.motionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

/** Watching habits from the local history: counts, time, days, favourite channels and traffic. */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.stats_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val current = stats
        if (current == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        StatsContent(
            current,
            traffic,
            PaddingValues(
                top = padding.calculateTopPadding(),
                bottom =
                    padding.calculateBottomPadding() + 24.dp,
            ),
        )
    }
}

@Composable
internal fun StatsContent(
    stats: WatchStats,
    traffic: TrafficSummary,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        item(key = "periods") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = motionItem()) {
                PeriodCard(
                    stringResource(R.string.stats_week),
                    stats.videosWeek,
                    watchTime(stats.timeWeek),
                    Modifier.weight(1f),
                )
                PeriodCard(
                    stringResource(R.string.stats_month),
                    stats.videosMonth,
                    watchTime(stats.timeMonth),
                    Modifier.weight(1f),
                )
            }
        }
        if (stats.videosTotal == 0) {
            item(key = "empty") { Note(stringResource(R.string.stats_empty)) }
            return@LazyColumn
        }
        item(key = "days") {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = motionItem(),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.stats_days), style = MaterialTheme.typography.titleMediumEmphasized)
                    DayBars(stats, Modifier.fillMaxWidth().height(96.dp))
                    stats.busiestHour?.let { Note(stringResource(R.string.stats_hour, it)) }
                    Note(pluralStringResource(R.plurals.stats_total, stats.videosTotal, stats.videosTotal))
                }
            }
        }
        if (stats.topChannels.isNotEmpty()) {
            item(key = "top-title") {
                Text(
                    stringResource(R.string.stats_top),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp).then(motionItem()),
                )
            }
            item(key = "top") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    modifier = motionItem(),
                ) {
                    stats.topChannels.forEachIndexed { index, stat ->
                        SegmentedListItem(
                            onClick = {},
                            shapes = groupedListItemShapes(index, stats.topChannels.size),
                            colors = groupedListItemColors(),
                            leadingContent = {
                                ChannelAvatar(
                                    stat.channel.avatars,
                                    contentDescription = null,
                                    size = 40.dp,
                                )
                            },
                            supportingContent = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.stats_channel_value,
                                        stat.videos,
                                        watchTime(stat.time),
                                        stat.videos,
                                    ),
                                )
                            },
                        ) { Text(stat.channel.name) }
                    }
                }
            }
        }
        item(key = "traffic") {
            Note(
                stringResource(
                    R.string.stats_traffic,
                    Formatter.formatShortFileSize(context, traffic.monthTotal),
                    Formatter.formatShortFileSize(context, traffic.monthMobile),
                ),
            )
        }
        item(key = "note") { Note(stringResource(R.string.stats_note)) }
    }
}

@Composable
private fun PeriodCard(
    label: String,
    videos: Int,
    time: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(time, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1)
            Text(
                pluralStringResource(R.plurals.stats_videos, videos, videos),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun DayBars(
    stats: WatchStats,
    modifier: Modifier,
) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val description = stats.days.joinToString { (day, count) -> "$day: $count" }
    Canvas(modifier.semantics { contentDescription = description }) {
        val max =
            stats.days
                .maxOf { it.second }
                .coerceAtLeast(1)
                .toFloat()
        val slot = size.width / stats.days.size
        val width = slot * BAR_SHARE
        val radius = CornerRadius(width / 2, width / 2)
        stats.days.forEachIndexed { index, (_, count) ->
            val left = index * slot + (slot - width) / 2
            drawRoundRect(track, Offset(left, 0f), Size(width, size.height), radius)
            val height = size.height * count / max
            if (height > 0f) drawRoundRect(bar, Offset(left, size.height - height), Size(width, height), radius)
        }
    }
}

private const val BAR_SHARE = 0.6f
private const val MINUTES_PER_HOUR = 60

/** "2 h 45 min" rather than a clock-like "2:45:00". */
@Composable
private fun watchTime(time: java.time.Duration): String {
    val minutes = time.toMinutes()
    return if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.stats_minutes, minutes.toInt())
    } else {
        stringResource(
            R.string.stats_hours_minutes,
            (minutes / MINUTES_PER_HOUR).toInt(),
            (minutes % MINUTES_PER_HOUR).toInt(),
        )
    }
}
