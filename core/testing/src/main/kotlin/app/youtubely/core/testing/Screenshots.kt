// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.testing

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import app.youtubely.core.designsystem.theme.YouTubelyTheme
import java.io.File
import java.io.FileOutputStream

/** Where screenshots go: `build/screenshots` of the module under test, or `-Dscreenshot.dir`. */
private val outputDirectory: File get() = File(System.getProperty("screenshot.dir") ?: "build/screenshots")

/**
 * Shows [content] on the app's surface, in the app theme with a fixed palette (no wallpaper colors),
 * light or dark.
 */
fun ComposeContentTestRule.setScreen(
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    setContent {
        YouTubelyTheme(darkTheme = dark, dynamicColor = false) {
            val surface = MaterialTheme.colorScheme.surface
            Box(Modifier.fillMaxSize().background(surface)) { content() }
        }
    }
}

/** Saves what is on screen as `<name>.png`. */
fun ComposeContentTestRule.saveScreenshot(name: String) {
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    outputDirectory.mkdirs()
    FileOutputStream(
        File(outputDirectory, "$name.png"),
    ).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
}

private const val PNG_QUALITY = 100
