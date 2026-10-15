// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.content.Context
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import java.io.File
import java.io.IOException

/** Free room on the volume of [dir], counting cache the system would clear to make space. */
internal fun Context.freeSpaceFor(dir: File): Long {
    val storage = getSystemService<StorageManager>() ?: return dir.usableSpace
    return try {
        storage.getAllocatableBytes(storage.getUuidForPath(dir))
    } catch (_: IOException) {
        dir.usableSpace
    }
}
