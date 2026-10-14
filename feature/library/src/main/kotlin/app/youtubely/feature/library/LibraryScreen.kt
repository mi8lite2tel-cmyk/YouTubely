// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.HistoryEntry
import app.youtubely.core.model.VideoId
import app.youtubely.core.ui.ChannelListItem
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.currentLocale
import app.youtubely.core.ui.videoFrameKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val ORIGIN = "history"

private enum class LibraryTab(
    val label: Int,
) {
    History(R.string.library_history),
    Subscriptions(R.string.library_subscriptions),
}

/** Local history (with resume positions) and local subscriptions, side by side in swipeable tabs. */
@Composable
fun LibraryScreen(
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val history by viewModel.historyEntries.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val historyEnabled by viewModel.historyEnabled.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pager = rememberPagerState { LibraryTab.entries.size }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val removedMessage = stringResource(R.string.library_removed)
    val unsubscribedMessage = stringResource(R.string.library_unsubscribed)
    val undoLabel = stringResource(R.string.library_undo)

    fun offerUndo(
        message: String,
        undo: () -> Unit,
    ) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, undoLabel, withDismissAction = true, SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                subtitle = { Text(stringResource(R.string.library_subtitle)) },
                actions = {
                    if (pager.currentPage == LibraryTab.History.ordinal) {
                        HistoryMenu(
                            historyEnabled = historyEnabled,
                            canClear = !history.isNullOrEmpty(),
                            onToggleHistory = { viewModel.setHistoryEnabled(!historyEnabled) },
                            onClear = { confirmClear = true },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = pager.currentPage == tab.ordinal,
                        onClick = { scope.launch { pager.animateScrollToPage(tab.ordinal) } },
                        text = { Text(stringResource(tab.label)) },
                    )
                }
            }
            // The pager centres its pages vertically by default, which floated short lists to mid-screen.
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (LibraryTab.entries[page]) {
                    LibraryTab.History -> {
                        HistoryPage(
                            entries = history,
                            historyEnabled = historyEnabled,
                            onEnableHistory = { viewModel.setHistoryEnabled(true) },
                            onOpenVideo = onOpenVideo,
                            onRemove = { entry ->
                                viewModel.remove(entry.item.id)
                                offerUndo(removedMessage.format(entry.item.title)) { viewModel.restore(entry) }
                            },
                        )
                    }

                    LibraryTab.Subscriptions -> {
                        SubscriptionsPage(
                            channels = channels,
                            onOpenChannel = onOpenChannel,
                            onUnsubscribe = { channel ->
                                viewModel.unsubscribe(channel)
                                offerUndo(unsubscribedMessage.format(channel.name)) { viewModel.resubscribe(channel) }
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(painterResource(YouTubelyIcons.DeleteSweep), contentDescription = null) },
            title = { Text(stringResource(R.string.library_clear_title)) },
            text = { Text(stringResource(R.string.library_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text(stringResource(R.string.library_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.library_cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryMenu(
    historyEnabled: Boolean,
    canClear: Boolean,
    onToggleHistory: () -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(YouTubelyIcons.MoreVert), stringResource(R.string.library_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (historyEnabled) R.string.library_pause_history else R.string.library_resume_history,
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        painterResource(if (historyEnabled) YouTubelyIcons.HistoryOff else YouTubelyIcons.History),
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onToggleHistory()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_clear_title)) },
                leadingIcon = { Icon(painterResource(YouTubelyIcons.DeleteSweep), contentDescription = null) },
                enabled = canClear,
                onClick = {
                    expanded = false
                    onClear()
                },
            )
        }
    }
}

@Composable
private fun HistoryPage(
    entries: ImmutableList<HistoryEntry>?,
    historyEnabled: Boolean,
    onEnableHistory: () -> Unit,
    onOpenVideo: (VideoId, String) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
) {
    if (entries == null) {
        LoadingState()
        return
    }
    if (entries.isEmpty() && historyEnabled) {
        EmptyPage(YouTubelyIcons.History, R.string.library_history_empty)
        return
    }
    val locale = currentLocale()
    val today = LocalDate.now()
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val groups = remember(entries) { entries.groupBy { it.watchedAt.atZone(ZoneId.systemDefault()).toLocalDate() } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
    ) {
        if (!historyEnabled) {
            item(key = "paused") { PausedCard(onEnableHistory) }
        }
        groups.forEach { (day, dayEntries) ->
            item(key = "day-$day") {
                Text(
                    when (day) {
                        today -> stringResource(R.string.library_today)
                        today.minusDays(1) -> stringResource(R.string.library_yesterday)
                        else -> dateFormat.format(day)
                    },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).animateItem(),
                )
            }
            items(dayEntries, key = { it.item.id.value }) { entry ->
                SwipeToRemove(onRemove = { onRemove(entry) }, modifier = Modifier.animateItem()) {
                    val key = videoFrameKey(ORIGIN, entry.item.id)
                    VideoRow(
                        item = entry.item,
                        sharedKey = key,
                        onClick = { onOpenVideo(entry.item.id, key) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PausedCard(onEnable: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(painterResource(YouTubelyIcons.HistoryOff), contentDescription = null)
            Text(
                stringResource(R.string.library_history_paused),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable) { Text(stringResource(R.string.library_resume_history)) }
        }
    }
}

@Composable
private fun SubscriptionsPage(
    channels: ImmutableList<ChannelRef>?,
    onOpenChannel: (ChannelId) -> Unit,
    onUnsubscribe: (ChannelRef) -> Unit,
) {
    when {
        channels == null -> {
            LoadingState()
        }

        channels.isEmpty() -> {
            EmptyPage(YouTubelyIcons.Subscriptions.filled, R.string.library_subscriptions_empty)
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(channels, key = { it.id.value }) { channel ->
                    ChannelListItem(
                        channel = channel,
                        onClick = { onOpenChannel(channel.id) },
                        trailing = {
                            IconButton(onClick = { onUnsubscribe(channel) }) {
                                Icon(
                                    painterResource(YouTubelyIcons.Subscribed),
                                    stringResource(R.string.library_unsubscribe, channel.name),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onRemove() },
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
                    contentDescription = stringResource(R.string.library_remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        modifier = modifier,
    ) { content() }
}

@Composable
private fun EmptyPage(
    icon: Int,
    text: Int,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = MaterialShapes.Clover4Leaf.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(104.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(44.dp))
                }
            }
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
