// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.youtubely.core.model.DarkMode
import app.youtubely.core.model.ThemeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository(): ThemeSettingsRepository {
        val store =
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                File(folder.root, "test.preferences_pb")
            }
        return ThemeSettingsRepository(store)
    }

    @Test
    fun defaultsWhenEmpty() =
        runTest(UnconfinedTestDispatcher()) {
            assertEquals(ThemeSettings(), repository().settings.first())
        }

    @Test
    fun changesArePersisted() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository()
            repo.setDarkMode(DarkMode.Dark)
            repo.setDynamicColor(false)
            repo.setPureBlack(true)
            repo.setHighContrast(true)
            assertEquals(
                ThemeSettings(DarkMode.Dark, dynamicColor = false, pureBlack = true, highContrast = true),
                repo.settings.first(),
            )
        }
}
