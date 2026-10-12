// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Combines a video-only and an audio-only file into one MP4 with Media3 Transformer. Inputs are
 * chosen by [StreamSelection] so that no re-encoding is needed: samples are copied as they are
 * (no FFmpeg, no quality loss, fast).
 */
@OptIn(UnstableApi::class)
internal class MediaMerger(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun merge(
        video: File,
        audio: File,
        output: File,
    ) = withContext(Dispatchers.Main) {
        output.delete()
        val composition =
            Composition
                .Builder(
                    EditedMediaItemSequence.withVideoFrom(listOf(track(video, keepVideo = true))),
                    EditedMediaItemSequence.withAudioFrom(listOf(track(audio, keepVideo = false))),
                ).build()

        suspendCancellableCoroutine { continuation ->
            val transformer =
                Transformer
                    .Builder(context)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult,
                            ) {
                                continuation.resume(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                continuation.resumeWithException(IOException("Merge failed", exportException))
                            }
                        },
                    ).build()
            // Transformer must be driven from the thread it was created on (main).
            continuation.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
            transformer.start(composition, output.absolutePath)
        }
    }

    private fun track(
        file: File,
        keepVideo: Boolean,
    ): EditedMediaItem =
        EditedMediaItem
            .Builder(MediaItem.fromUri(file.toURI().toString()))
            .setRemoveAudio(keepVideo)
            .setRemoveVideo(!keepVideo)
            .build()
}
