// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.VideoId
import app.youtubely.core.testing.saveScreenshot
import app.youtubely.core.testing.setScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h900dp-xxhdpi")
class DownloadsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun download(
        id: String,
        state: DownloadState,
        progress: Float = 0f,
        error: String? = null,
    ) = Download(
        id = id,
        videoId = VideoId(id),
        title = "Как работает видеокодек: подробный разбор $id",
        channelName = "Science Channel",
        duration = Duration.ofMinutes(18),
        format = DownloadFormat.Video(720),
        state = state,
        progress = progress,
        totalBytes = 180_000_000,
        filePath = null,
        thumbnailPath = null,
        createdAtMillis = 0,
        error = error,
    )

    @Test
    fun rows() {
        compose.setScreen {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    download("a", DownloadState.Completed) to false,
                    download("b", DownloadState.Downloading, 0.42f) to false,
                    download("c", DownloadState.Queued) to false,
                    download("d", DownloadState.Failed, error = "no_space") to false,
                    download("e", DownloadState.Completed) to true,
                ).forEach { (item, selected) ->
                    DownloadRow(
                        download = item,
                        onPlay = {},
                        onPause = {},
                        onResume = {},
                        onDelete = {},
                        onOpenOnline = {},
                        selecting = selected,
                        selected = selected,
                        onToggleSelected = {},
                        waitingForWifi = item.state == DownloadState.Queued,
                    )
                }
            }
        }
        compose.saveScreenshot("downloads-rows")
    }
}
