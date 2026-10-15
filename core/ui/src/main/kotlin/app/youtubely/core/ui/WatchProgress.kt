// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import androidx.compose.runtime.compositionLocalOf
import app.youtubely.core.model.VideoId

/**
 * Watched share 0..1 per video from the local history. Provided once at the app root so every
 * thumbnail can show its progress bar without each screen passing it down.
 */
val LocalWatchProgress = compositionLocalOf<Map<VideoId, Float>> { emptyMap() }
