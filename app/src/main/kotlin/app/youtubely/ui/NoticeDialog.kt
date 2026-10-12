// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.youtubely.R
import app.youtubely.core.designsystem.icon.YouTubelyIcons

/** First-launch notice required by CLAUDE.md (A3): unofficial app, ToS risk, download rights. */
@Composable
fun NoticeDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = {
            Icon(
                painterResource(YouTubelyIcons.Info),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(stringResource(R.string.notice_title)) },
        text = { Text(stringResource(R.string.notice_body)) },
        confirmButton = {
            Button(
                onClick = onAccept,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.notice_accept)) }
        },
    )
}
