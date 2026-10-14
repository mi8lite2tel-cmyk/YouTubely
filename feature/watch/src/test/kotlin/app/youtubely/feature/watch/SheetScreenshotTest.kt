// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(sdk = [35], qualifiers = "w392dp-h800dp-xxhdpi")
class SheetScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun smoke() {
        compose.setScreen {
            SleepTimerSection(SleepTimer.Off, noActions())
        }
        compose.saveScreenshot("smoke")
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
    )

@Suppress("unused")
private val loop = AbLoop()
