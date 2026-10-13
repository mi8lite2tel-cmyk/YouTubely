// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.download.DownloadManager
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.StorageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DownloadFilter { All, Video, Audio, InProgress }

data class DownloadsUiState(
    /** `null` until the stored list is read, so the empty state doesn't flash. */
    val downloads: ImmutableList<Download>?,
    val filter: DownloadFilter,
    /** Nothing at all is downloaded (as opposed to nothing matching the filter). */
    val isEmpty: Boolean,
)

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val manager: DownloadManager,
    ) : ViewModel() {
        private val filter = MutableStateFlow(DownloadFilter.All)

        /** Swiped away but not deleted yet, so the snackbar can undo it. */
        private val pendingDeletion = MutableStateFlow<Set<String>>(emptySet())

        val state: StateFlow<DownloadsUiState> =
            combine(manager.downloads, filter, pendingDeletion) { all, filter, pending ->
                val visible = all.filterNot { it.id in pending }.sortedByDescending { it.createdAtMillis }
                DownloadsUiState(
                    downloads = visible.filter(filter::matches).toImmutableList(),
                    filter = filter,
                    isEmpty = visible.isEmpty(),
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                DownloadsUiState(null, DownloadFilter.All, isEmpty = false),
            )

        private val _storage = MutableStateFlow<StorageUsage?>(null)
        val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

        init {
            // Recount when a download is added, removed or finishes, not on every progress tick.
            viewModelScope.launch {
                manager.downloads
                    .map { list -> list.map { it.id to it.state } }
                    .distinctUntilChanged()
                    .collect { _storage.value = manager.storage() }
            }
        }

        fun setFilter(value: DownloadFilter) {
            filter.value = value
        }

        fun pause(id: String) = launch { manager.pause(id) }

        fun resume(id: String) = launch { manager.resume(id) }

        /** Hides the download; [commitDelete] or [undoDelete] follows once the snackbar closes. */
        fun requestDelete(id: String) = pendingDeletion.update { it + id }

        fun undoDelete(id: String) = pendingDeletion.update { it - id }

        fun commitDelete(id: String) =
            launch {
                manager.delete(id)
                pendingDeletion.update { it - id }
            }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun DownloadFilter.matches(download: Download): Boolean =
    when (this) {
        DownloadFilter.All -> true
        DownloadFilter.Video -> download.format is DownloadFormat.Video && download.state == DownloadState.Completed
        DownloadFilter.Audio -> download.format == DownloadFormat.Audio && download.state == DownloadState.Completed
        DownloadFilter.InProgress -> download.state != DownloadState.Completed
    }
