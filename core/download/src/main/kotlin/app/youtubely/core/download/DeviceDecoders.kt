// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import app.youtubely.core.model.VideoStream

/**
 * Whether this phone can play a video stream smoothly. Downloading a file the device cannot decode
 * (typically 1440p/4K, which YouTube only offers as AV1) leaves a video that stutters or stays
 * black, so such formats are not offered at all.
 */
object DeviceDecoders {
    private val decoders: List<MediaCodecInfo> by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
    }

    /** Reads the codec list ahead of time, off the main thread, so the first check does not wait for it. */
    fun warmUp() {
        decoders.size
    }

    fun canPlay(stream: VideoStream): Boolean {
        val mime = mimeOf(stream.codecs) ?: return false
        return decoders.any { it.supports(mime, stream) }
    }

    private fun mimeOf(codecs: String?): String? =
        when {
            codecs == null -> null
            codecs.startsWith("avc1") -> "video/avc"
            codecs.startsWith("av01") -> "video/av01"
            codecs.startsWith("vp09") || codecs.startsWith("vp9") -> "video/x-vnd.on2.vp9"
            codecs.startsWith("hev1") || codecs.startsWith("hvc1") -> "video/hevc"
            else -> null
        }

    private fun MediaCodecInfo.supports(
        mime: String,
        stream: VideoStream,
    ): Boolean {
        // getCapabilitiesForType throws for a type the codec does not handle, so check first.
        val type = supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) }
        val video = type?.let { runCatching { getCapabilitiesForType(it).videoCapabilities }.getOrNull() }
        val fps = stream.fps.coerceAtLeast(1).toDouble()
        val fits = video?.areSizeAndRateSupported(stream.width, stream.height, fps) == true
        // A software AV1 decoder cannot keep up with sharp pictures on a phone.
        val fast = mime != "video/av01" || isHardware()
        return fits && fast
    }

    private fun MediaCodecInfo.isHardware(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAccelerated
        } else {
            !name.startsWith("OMX.google.") && !name.startsWith("c2.android.")
        }
}
