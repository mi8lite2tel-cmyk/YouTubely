// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val LOAD_MORE_THRESHOLD = 6

/** Calls [onLoadMore] when the list scrolls close to its last item. */
@Composable
fun LoadMoreEffect(
    state: LazyListState,
    onLoadMore: () -> Unit,
) {
    val load by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        // Keyed by the item count too: a page that still leaves the end in view asks for the next one.
        snapshotFlow {
            val info = state.layoutInfo
            val nearEnd =
                info.totalItemsCount > 0 &&
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_THRESHOLD
            nearEnd to info.totalItemsCount
        }.distinctUntilChanged()
            .filter { it.first }
            .collect { load() }
    }
}

/** Grid variant of [LoadMoreEffect]. */
@Composable
fun LoadMoreEffect(
    state: LazyGridState,
    onLoadMore: () -> Unit,
) {
    val load by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        // Keyed by the item count too: a page that still leaves the end in view asks for the next one.
        snapshotFlow {
            val info = state.layoutInfo
            val nearEnd =
                info.totalItemsCount > 0 &&
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_THRESHOLD
            nearEnd to info.totalItemsCount
        }.distinctUntilChanged()
            .filter { it.first }
            .collect { load() }
    }
}
