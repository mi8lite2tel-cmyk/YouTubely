// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.youtubely.core.model.PlaybackSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository() =
        PlaybackSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "p.preferences_pb") },
        )

    @Test
    fun defaultsLimitMobileDataOnly() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = repository().settings.first()
            assertEquals(PlaybackSettings(), settings)
            assertNull(settings.maxHeightUnmetered)
            assertEquals(PlaybackSettings.DEFAULT_METERED_LIMIT, settings.maxHeightMetered)
        }

    @Test
    fun noLimitIsStoredDistinctFromDefault() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository()
            repo.setMaxHeightMetered(null)
            repo.setMaxHeightUnmetered(1080)
            val settings = repo.settings.first()
            assertNull(settings.maxHeightMetered)
            assertEquals(1080, settings.maxHeightUnmetered)
        }
}
