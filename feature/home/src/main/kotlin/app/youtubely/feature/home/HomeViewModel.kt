// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.common.Outcome
import app.youtubely.core.common.outcomeOf
import app.youtubely.core.model.ExploreKind
import app.youtubely.core.model.ExploreSection
import app.youtubely.core.model.Region
import app.youtubely.core.provider.StreamProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Error(
        val error: Throwable,
    ) : HomeUiState

    data class Content(
        val sections: ImmutableList<ExploreSection>,
        val selected: ExploreKind,
        val refreshing: Boolean = false,
        /** The last pull-to-refresh failed; the old content stays and a message is shown once. */
        val refreshError: Throwable? = null,
    ) : HomeUiState {
        val current: ExploreSection get() = sections.first { it.kind == selected }
    }
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val provider: StreamProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val state: StateFlow<HomeUiState> = _state.asStateFlow()

        private val region =
            Locale
                .getDefault()
                .country
                .takeIf { it.length == 2 && it.all(Char::isUpperCase) }
                ?.let(::Region) ?: Region("US")

        init {
            load()
        }

        /** Charts are fetched live each time: they change through the day. */
        fun load(refresh: Boolean = false) {
            val previous = _state.value as? HomeUiState.Content
            _state.value =
                when {
                    refresh && previous != null -> previous.copy(refreshing = true, refreshError = null)
                    else -> HomeUiState.Loading
                }
            viewModelScope.launch {
                _state.value =
                    when (val result = outcomeOf { provider.explore(region) }) {
                        is Outcome.Success -> {
                            val sections = result.value.toImmutableList()
                            val keep = previous?.selected?.takeIf { kind -> sections.any { it.kind == kind } }
                            HomeUiState.Content(sections, keep ?: sections.first().kind)
                        }

                        // A failed pull-to-refresh keeps the old content on screen.
                        is Outcome.Failure -> {
                            previous?.copy(refreshing = false, refreshError = result.error)
                                ?: HomeUiState.Error(result.error)
                        }
                    }
            }
        }

        fun refreshErrorShown() {
            _state.update { (it as? HomeUiState.Content)?.copy(refreshError = null) ?: it }
        }

        fun select(kind: ExploreKind) {
            _state.update { (it as? HomeUiState.Content)?.copy(selected = kind) ?: it }
        }
    }
