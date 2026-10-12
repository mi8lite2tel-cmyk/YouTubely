// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.di

import app.youtubely.core.network.HttpClientFactory
import app.youtubely.core.network.NewPipeDownloader
import app.youtubely.core.provider.StreamProvider
import app.youtubely.core.provider.newpipe.NewPipeStreamProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = HttpClientFactory.create()

    /** The default source; swapping it (e.g. for Piped) touches only this binding. */
    @Provides
    @Singleton
    fun provideStreamProvider(client: OkHttpClient): StreamProvider =
        NewPipeStreamProvider(NewPipeDownloader(client), Dispatchers.IO)
}
