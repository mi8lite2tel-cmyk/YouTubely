// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The end of a list whose next page failed to load. Scrolling does not retry on its own, which
 * would hammer a server that is already refusing; the user asks again.
 */
@Composable
fun LoadMoreRetry(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
