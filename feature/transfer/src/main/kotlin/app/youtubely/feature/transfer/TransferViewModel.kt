// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.data.ImportSummary
import app.youtubely.core.data.LibraryTransfer
import app.youtubely.core.data.QrTransfer
import app.youtubely.core.data.TransferBundle
import app.youtubely.core.data.TransferFormats
import app.youtubely.core.data.TransferScope
import app.youtubely.core.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-off results the screen shows once. */
sealed interface TransferMessage {
    data class Imported(
        val summary: ImportSummary,
    ) : TransferMessage

    data object Unreadable : TransferMessage

    data object Nothing : TransferMessage
}

data class TransferUiState(
    val scope: TransferScope = TransferScope.Subscriptions,
    /** Frames of the QR codes being shown to another phone; `null` when no QR is shown. */
    val qrFrames: List<String>? = null,
    /** Something read from a file or QR codes that waits for the user to confirm the import. */
    val preview: TransferBundle? = null,
    val message: TransferMessage? = null,
)

@HiltViewModel
class TransferViewModel
    @Inject
    constructor(
        private val transfer: LibraryTransfer,
        private val sync: SyncScheduler,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TransferUiState())
        val state: StateFlow<TransferUiState> = _state.asStateFlow()

        fun setScope(scope: TransferScope) = _state.update { it.copy(scope = scope) }

        fun showQr() {
            viewModelScope.launch {
                val bundle = transfer.export(_state.value.scope)
                _state.update {
                    if (bundle.isEmpty) {
                        it.copy(message = TransferMessage.Nothing)
                    } else {
                        it.copy(qrFrames = QrTransfer.frames(TransferFormats.encode(bundle)))
                    }
                }
            }
        }

        fun hideQr() = _state.update { it.copy(qrFrames = null) }

        /** The text of an export file for the chosen scope, or `null` when there is nothing to export. */
        suspend fun exportText(): String? {
            val bundle = transfer.export(_state.value.scope)
            if (bundle.isEmpty) {
                _state.update { it.copy(message = TransferMessage.Nothing) }
                return null
            }
            return TransferFormats.encode(bundle)
        }

        /** Reads [text] (a file or the text of scanned QR codes) and asks to confirm what it holds. */
        fun preview(text: String) {
            val bundle = TransferFormats.parse(text)
            _state.update {
                if (bundle == null) it.copy(message = TransferMessage.Unreadable) else it.copy(preview = bundle)
            }
        }

        /** A file could not be read or written. */
        fun unreadable() = _state.update { it.copy(message = TransferMessage.Unreadable) }

        fun cancelPreview() = _state.update { it.copy(preview = null) }

        fun confirmImport() {
            val bundle = _state.value.preview ?: return
            _state.update { it.copy(preview = null) }
            viewModelScope.launch {
                val summary = transfer.import(bundle)
                if (bundle.subscriptions.isNotEmpty()) sync.refreshNow()
                _state.update { it.copy(message = TransferMessage.Imported(summary)) }
            }
        }

        fun messageShown() = _state.update { it.copy(message = null) }
    }
