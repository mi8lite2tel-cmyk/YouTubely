// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.di

import app.youtubely.core.data.TrafficMeter
import app.youtubely.core.network.HttpClientFactory
import app.youtubely.core.network.NewPipeDownloader
import app.youtubely.core.network.TrafficInterceptor
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
    fun provideOkHttpClient(meter: TrafficMeter): OkHttpClient =
        // A network interceptor sees the bytes as they travel (compressed), so the Traffic screen is accurate.
        HttpClientFactory.create { addNetworkInterceptor(TrafficInterceptor(meter)) }

    /** The default source; swapping it (e.g. for Piped) touches only this binding. */
    @Provides
    @Singleton
    fun provideStreamProvider(client: OkHttpClient): StreamProvider =
        NewPipeStreamProvider(NewPipeDownloader(client), Dispatchers.IO)
}
