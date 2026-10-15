// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DownloadModule {
    @Provides
    @Singleton
    fun provideDownloadStore(
        @ApplicationContext context: Context,
    ): DownloadStore =
        DownloadStore(
            DataStoreFactory.create(
                serializer = DownloadRecordsSerializer,
                // An unreadable file starts a new list, but the old one is kept for a look.
                corruptionHandler =
                    ReplaceFileCorruptionHandler { _ ->
                        val file = context.dataStoreFile("downloads.json")
                        runCatching { file.copyTo(File(file.path + ".corrupt"), overwrite = true) }
                        emptyList<DownloadRecord>()
                    },
            ) { context.dataStoreFile("downloads.json") },
        )
}
