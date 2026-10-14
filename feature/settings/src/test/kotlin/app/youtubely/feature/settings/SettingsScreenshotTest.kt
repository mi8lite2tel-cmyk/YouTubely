// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.youtubely.core.model.DayTraffic
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.SyncSettings
import app.youtubely.core.model.TrafficKind
import app.youtubely.core.model.TrafficSummary
import app.youtubely.core.testing.saveScreenshot
import app.youtubely.core.testing.setScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Renders the settings pages to PNG so their design can be checked without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h1400dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val mb = 1024L * 1024

    @Test
    fun mainPage() {
        compose.setScreen {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) { mainPage {} }
        }
        compose.saveScreenshot("settings-main")
    }

    @Test
    fun qualityPage() {
        val summary =
            TrafficSummary(
                monthMobile = 1_400 * mb,
                monthWifi = 6_200 * mb,
                monthByKind =
                    mapOf(
                        TrafficKind.Video to 6_000 * mb,
                        TrafficKind.Download to 1_200 * mb,
                        TrafficKind.Images to 300 * mb,
                    ),
                days =
                    (13 downTo 0).map {
                        DayTraffic(
                            LocalDate.of(2026, 10, 14).minusDays(it.toLong()),
                            (it % 3) * 90 * mb,
                            (it % 5) * 200 * mb,
                        )
                    },
            )
        val settings = PlaybackSettings(monthlyMobileLimitMb = 3072)
        compose.setScreen {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                QualitySettings(settings) {}
                SectionHeader(R.string.settings_traffic)
                TrafficSettings(summary, settings, {}, {})
            }
        }
        compose.saveScreenshot("settings-quality")
    }

    @Test
    fun subscriptionsPage() {
        compose.setScreen(dark = false) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                RefreshSettings(SyncSettings(), {}, {})
                SectionHeader(R.string.settings_auto_download)
                AutoDownloadSettings(SyncSettings(), {})
            }
        }
        compose.saveScreenshot("settings-subscriptions")
    }
}
