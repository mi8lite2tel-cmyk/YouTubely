// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Puts a video-only and an audio-only file into one MP4 by copying their samples: no decoding,
 * no quality loss and no waiting. Samples of both tracks are interleaved by timestamp so the
 * result seeks and streams well.
 */
internal object Mp4Remuxer {
    private const val BUFFER_SIZE = 2 * 1024 * 1024

    /** @throws java.io.IOException or [IllegalStateException] when the platform cannot mux these codecs. */
    fun remux(
        video: File,
        audio: File,
        output: File,
    ) {
        output.delete()
        val videoExtractor = open(video, "video/")
        val audioExtractor = open(audio, "audio/")
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val videoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoExtractor.sampleTrackIndex))
            val audioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioExtractor.sampleTrackIndex))
            muxer.start()
            copyInterleaved(muxer, videoExtractor to videoTrack, audioExtractor to audioTrack)
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    /** Opens [file] with its first track whose MIME type starts with [prefix] selected. */
    private fun open(
        file: File,
        prefix: String,
    ): MediaExtractor {
        val extractor = MediaExtractor()
        var ready = false
        try {
            extractor.setDataSource(file.absolutePath)
            val index =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
                } ?: error("No $prefix track in ${file.name}")
            extractor.selectTrack(index)
            ready = true
            return extractor
        } finally {
            if (!ready) extractor.release()
        }
    }

    private fun copyInterleaved(
        muxer: MediaMuxer,
        first: Pair<MediaExtractor, Int>,
        second: Pair<MediaExtractor, Int>,
    ) {
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val info = MediaCodec.BufferInfo()
        val sources = listOf(first, second)
        while (true) {
            // The source whose next sample is the earliest goes first.
            val source =
                sources
                    .filter { it.first.sampleTime >= 0 }
                    .minByOrNull { it.first.sampleTime } ?: return
            // A source that cannot deliver its sample is dropped, which ends its track.
            if (!copySample(muxer, source, buffer, info)) source.first.unselectTrack(source.first.sampleTrackIndex)
        }
    }

    private fun copySample(
        muxer: MediaMuxer,
        source: Pair<MediaExtractor, Int>,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ): Boolean {
        val (extractor, track) = source
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size >= 0) {
            val keyFrame = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
            info.set(0, size, extractor.sampleTime, if (keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
        }
        return size >= 0
    }
}
