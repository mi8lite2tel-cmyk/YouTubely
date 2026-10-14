// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons

private val Heights = listOf(1080, 720, 480, 360)
private const val DEFAULT_HEIGHT = 720
private const val AUDIO = 0

/** Asks for the quality before saving [count] videos at once; `null` in [onConfirm] means sound only. */
@Composable
fun DownloadAllDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (maxHeight: Int?) -> Unit,
) {
    var height by rememberSaveable { mutableStateOf(DEFAULT_HEIGHT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_all_title, count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.download_all_body))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Heights.forEach { option ->
                        ExpressiveFilterChip(height == option, { height = option }, "${option}p")
                    }
                    ExpressiveFilterChip(
                        height == AUDIO,
                        { height = AUDIO },
                        stringResource(R.string.download_all_audio),
                        icon = YouTubelyIcons.Headphones,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(height.takeIf { it != AUDIO }) }) {
                Text(stringResource(R.string.download_all_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_all_cancel)) } },
    )
}

/** Toolbar action that asks for a quality and then saves [count] videos. */
@Composable
fun DownloadAllButton(
    count: Int,
    onConfirm: (maxHeight: Int?) -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { asking = true }) {
        Icon(painterResource(YouTubelyIcons.Download), stringResource(R.string.download_all))
    }
    if (asking) {
        DownloadAllDialog(
            count = count,
            onDismiss = { asking = false },
            onConfirm = {
                asking = false
                onConfirm(it)
            },
        )
    }
}
