// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.transfer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

private const val QUIET_ZONE_MODULES = 3

/**
 * A QR code for [content], always dark on white (scanners need that contrast whatever the theme
 * is), drawn straight onto the canvas so it stays sharp at any size.
 */
@Composable
fun QrCodeView(
    content: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(content) { encode(content) }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }) {
        drawRect(Color.White)
        val modules = matrix.width + 2 * QUIET_ZONE_MODULES
        val cell = size.minDimension / modules
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        Color.Black,
                        topLeft = Offset((x + QUIET_ZONE_MODULES) * cell, (y + QUIET_ZONE_MODULES) * cell),
                        // A hair of overlap hides seams between modules at fractional sizes.
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}

private fun encode(content: String): BitMatrix =
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
