// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.DownloadState

private const val PERCENT = 100
private const val HALF_TURN = 180f

/**
 * Expressive split button: the main part downloads the default quality (or shows progress /
 * "Downloaded"), the trailing part opens every quality the video offers plus audio only.
 */
@Composable
internal fun DownloadButton(
    formats: List<DownloadFormat>,
    downloads: List<Download>,
    onDownload: (DownloadFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (formats.isEmpty()) return
    var menuOpen by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (menuOpen) HALF_TURN else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chevron",
    )
    // Downloads need the progress notification on Android 13+; they also work without it.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun download(format: DownloadFormat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onDownload(format)
    }

    val active = downloads.firstOrNull { it.state != DownloadState.Completed && it.state != DownloadState.Failed }
    val done = downloads.firstOrNull { it.state == DownloadState.Completed }
    val label =
        when {
            active != null -> stringResource(R.string.watch_download_progress, (active.progress * PERCENT).toInt())
            done != null -> stringResource(R.string.watch_downloaded)
            else -> stringResource(R.string.watch_download)
        }

    Box(modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = { download(formats.first()) },
                    enabled = active == null,
                ) {
                    Icon(
                        painterResource(if (done != null) YouTubelyIcons.DownloadDone else YouTubelyIcons.Download),
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    )
                    Text(label, Modifier.padding(start = 8.dp))
                }
            },
            trailingButton = {
                val description = stringResource(R.string.watch_download_options)
                SplitButtonDefaults.TonalTrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Icon(
                        painterResource(YouTubelyIcons.ExpandMore),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(SplitButtonDefaults.TrailingIconSize)
                                .graphicsLayer { rotationZ = rotation },
                    )
                }
            },
        )
        FormatMenu(
            expanded = menuOpen,
            formats = formats,
            downloads = downloads,
            onDismiss = { menuOpen = false },
            onPick = {
                menuOpen = false
                download(it)
            },
        )
    }
}

@Composable
private fun FormatMenu(
    expanded: Boolean,
    formats: List<DownloadFormat>,
    downloads: List<Download>,
    onDismiss: () -> Unit,
    onPick: (DownloadFormat) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        formats.forEach { format ->
            val saved = downloads.any { it.format == format && it.state == DownloadState.Completed }
            DropdownMenuItem(
                text = {
                    Text(
                        when (format) {
                            is DownloadFormat.Video -> "${format.height}p"
                            DownloadFormat.Audio -> stringResource(R.string.watch_download_audio)
                        },
                    )
                },
                leadingIcon = {
                    val icon = if (format == DownloadFormat.Audio) YouTubelyIcons.Headphones else YouTubelyIcons.Video
                    Icon(painterResource(icon), contentDescription = null)
                },
                trailingIcon =
                    if (saved) {
                        { Icon(painterResource(YouTubelyIcons.Check), contentDescription = null) }
                    } else {
                        null
                    },
                onClick = { onPick(format) },
            )
        }
    }
}
