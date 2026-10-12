// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import java.time.Duration
import java.util.Locale

/** Locale-aware compact numbers: 1.2M, 1,2 млн, 1,2 млн. */
fun compactNumber(
    value: Long,
    locale: Locale,
): String = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(value)

/** 4:05, 12:34, 1:02:03. */
fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val seconds = duration.toSecondsPart()
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun viewCountText(count: Long): String =
    pluralStringResource(
        R.plurals.views,
        count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        compactNumber(count, currentLocale()),
    )

@Composable
fun subscriberCountText(count: Long): String =
    pluralStringResource(
        R.plurals.subscribers,
        count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        compactNumber(count, currentLocale()),
    )
