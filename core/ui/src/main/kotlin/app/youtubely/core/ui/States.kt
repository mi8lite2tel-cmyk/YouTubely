// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.provider.ProviderError

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(
            modifier =
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = description
                },
        )
    }
}

private class ErrorUi(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val retryable: Boolean,
)

private fun Throwable.toErrorUi(): ErrorUi =
    when (this) {
        is ProviderError.Network -> {
            ErrorUi(YouTubelyIcons.WifiOff, R.string.error_network_title, R.string.error_network_body, true)
        }

        is ProviderError.RateLimited -> {
            ErrorUi(YouTubelyIcons.Schedule, R.string.error_rate_title, R.string.error_rate_body, true)
        }

        is ProviderError.GeoBlocked -> {
            ErrorUi(YouTubelyIcons.Language, R.string.error_geo_title, R.string.error_geo_body, false)
        }

        is ProviderError.AgeRestricted -> {
            ErrorUi(YouTubelyIcons.Info, R.string.error_age_title, R.string.error_age_body, false)
        }

        is ProviderError.Paid -> {
            ErrorUi(YouTubelyIcons.Info, R.string.error_paid_title, R.string.error_paid_body, false)
        }

        is ProviderError.Removed -> {
            ErrorUi(YouTubelyIcons.Info, R.string.error_removed_title, R.string.error_removed_body, false)
        }

        is ProviderError.ExtractorOutdated -> {
            ErrorUi(YouTubelyIcons.Error, R.string.error_outdated_title, R.string.error_outdated_body, true)
        }

        else -> {
            ErrorUi(YouTubelyIcons.Error, R.string.error_unknown_title, R.string.error_unknown_body, true)
        }
    }

/** Full-screen error with an expressive soft-burst icon container and a retry button. */
@Composable
fun ErrorState(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = error.toErrorUi()
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Surface(
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(ui.icon), contentDescription = null, modifier = Modifier.size(40.dp))
                }
            }
            Text(
                text = stringResource(ui.title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(ui.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (ui.retryable) {
                Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                    Icon(painterResource(YouTubelyIcons.Refresh), contentDescription = null)
                    Text(stringResource(R.string.retry), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
