// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
            DataStoreFactory.create(DownloadRecordsSerializer) { context.dataStoreFile("downloads.json") },
        )
}
