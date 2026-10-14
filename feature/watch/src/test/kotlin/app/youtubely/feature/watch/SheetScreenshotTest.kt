// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.player.AbLoop
import app.youtubely.core.player.SleepTimer
import app.youtubely.core.testing.saveScreenshot
import app.youtubely.core.testing.setScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h900dp-xxhdpi")
class SheetScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mainPage() {
        compose.setScreen {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                MainPage(
                    player = player,
                    state = PlayerSheetState(null, null, PlaybackSettings(), SleepTimer.Off, AbLoop()),
                    speed = 1.25f,
                    repeat = false,
                    actions = noActions(),
                    onRepeat = {},
                    onOpen = {},
                )
            }
        }
        compose.saveScreenshot("sheet-main")
    }

    @Test
    fun speedPage() {
        compose.setScreen {
            Column(Modifier.padding(16.dp)) { SpeedPage(1.75f) {} }
        }
        compose.saveScreenshot("sheet-speed")
    }

    @Test
    fun sleepPage() {
        compose.setScreen(dark = false) {
            Column(Modifier.padding(16.dp)) { SleepTimerSection(SleepTimer.AtEndOfVideo, noActions()) }
        }
        compose.saveScreenshot("sheet-sleep")
    }
}

internal fun noActions() =
    PlayerSheetActions(
        onSkipSilence = {},
        onNormalizeVolume = {},
        onAutoplay = {},
        onSleepMinutes = {},
        onSleepAtEnd = {},
        onSleepCancel = {},
        onLoopStart = {},
        onLoopEnd = {},
        onLoopClear = {},
        onSubtitles = {},
        onEqualizer = null,
        onSpeed = {},
    )

@Suppress("unused")
private val loop = AbLoop()
