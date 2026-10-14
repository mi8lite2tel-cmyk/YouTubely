// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.youtubely.core.model.AutoDownloadRule
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
@Config(sdk = [35], qualifiers = "w392dp-h1200dp-xxhdpi")
class RuleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ruleEditor() {
        compose.setScreen {
            Column(Modifier.padding(16.dp)) {
                RuleDetails(
                    rule = AutoDownloadRule(maxHeight = 720, keepLast = 5, maxMinutes = 60, deleteWatched = true),
                    include = "podcast, episode",
                    exclude = "",
                    onRule = {},
                    onInclude = {},
                    onExclude = {},
                )
            }
        }
        compose.saveScreenshot("rule-editor")
    }
}
