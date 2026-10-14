// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QualityPolicyTest {
    private fun inputs(
        settings: PlaybackSettings = PlaybackSettings(),
        metered: Boolean = false,
        systemDataSaver: Boolean = false,
        powerSave: Boolean = false,
        used: Float? = null,
        pinned: Int? = null,
    ) = QualityInputs(settings, metered, systemDataSaver, powerSave, used, pinned)

    @Test
    fun `wifi without limits is unrestricted`() {
        val cap = QualityPolicy.decide(inputs())
        assertNull(cap.maxHeight)
        assertNull(cap.maxFps)
        assertEquals(QualityReason.Unlimited, cap.reason)
    }

    @Test
    fun `network limits apply per network type`() {
        val settings = PlaybackSettings(maxHeightUnmetered = 1080, maxHeightMetered = 480)
        assertEquals(1080, QualityPolicy.decide(inputs(settings, metered = false)).maxHeight)
        val mobile = QualityPolicy.decide(inputs(settings, metered = true))
        assertEquals(480, mobile.maxHeight)
        assertEquals(QualityReason.MeteredLimit, mobile.reason)
    }

    @Test
    fun `auto saver stays off on mobile data until it matters`() {
        val settings = PlaybackSettings(maxHeightMetered = 720)
        assertFalse(QualityPolicy.isSaving(inputs(settings, metered = true)))
        assertTrue(QualityPolicy.isSaving(inputs(settings, metered = true, systemDataSaver = true)))
        assertTrue(QualityPolicy.isSaving(inputs(settings, metered = true, used = 0.8f)))
        assertFalse(QualityPolicy.isSaving(inputs(settings, metered = false, systemDataSaver = true)))
    }

    @Test
    fun `saver lowers height and frame rate and prefers efficient codecs`() {
        val settings = PlaybackSettings(maxHeightMetered = 720, dataSaverMaxHeight = 360)
        val cap = QualityPolicy.decide(inputs(settings, metered = true, systemDataSaver = true))
        assertEquals(360, cap.maxHeight)
        assertEquals(QualityPolicy.SAVER_FPS, cap.maxFps)
        assertTrue(cap.preferEfficientCodec)
        assertEquals(QualityReason.DataSaver, cap.reason)
    }

    @Test
    fun `always mode saves on wifi too`() {
        val settings = PlaybackSettings(dataSaver = DataSaverMode.Always)
        val cap = QualityPolicy.decide(inputs(settings, metered = false))
        assertEquals(PlaybackSettings.DEFAULT_SAVER_HEIGHT, cap.maxHeight)
    }

    @Test
    fun `off mode never saves even with an exhausted allowance`() {
        val settings = PlaybackSettings(dataSaver = DataSaverMode.Off, maxHeightMetered = null)
        val cap = QualityPolicy.decide(inputs(settings, metered = true, systemDataSaver = true, used = 2f))
        assertNull(cap.maxHeight)
    }

    @Test
    fun `exhausted allowance caps hard on mobile data`() {
        val settings = PlaybackSettings(maxHeightMetered = 720)
        val cap = QualityPolicy.decide(inputs(settings, metered = true, used = 1.2f))
        assertEquals(QualityPolicy.EXHAUSTED_HEIGHT, cap.maxHeight)
        assertEquals(QualityReason.MonthlyLimit, cap.reason)
    }

    @Test
    fun `battery saver caps at 480 unless disabled`() {
        assertEquals(QualityPolicy.POWER_SAVE_HEIGHT, QualityPolicy.decide(inputs(powerSave = true)).maxHeight)
        val off = PlaybackSettings(limitOnPowerSave = false)
        assertNull(QualityPolicy.decide(inputs(off, powerSave = true)).maxHeight)
    }

    @Test
    fun `lowest applicable ceiling wins`() {
        val settings = PlaybackSettings(maxHeightUnmetered = 360)
        val cap = QualityPolicy.decide(inputs(settings, powerSave = true))
        assertEquals(360, cap.maxHeight)
        assertEquals(QualityReason.UnmeteredLimit, cap.reason)
    }

    @Test
    fun `user pick overrides every ceiling`() {
        val settings = PlaybackSettings(maxHeightMetered = 360, dataSaver = DataSaverMode.Always)
        val cap = QualityPolicy.decide(inputs(settings, metered = true, powerSave = true, pinned = 1080))
        assertEquals(1080, cap.maxHeight)
        assertEquals(QualityReason.Pinned, cap.reason)
    }

    @Test
    fun `audio only uses the lowest picture and keeps the fps cap`() {
        val settings = PlaybackSettings(audioOnly = true, maxFps = 30)
        val cap = QualityPolicy.decide(inputs(settings))
        assertEquals(QualityPolicy.LOWEST_HEIGHT, cap.maxHeight)
        assertEquals(30, cap.maxFps)
        assertEquals(QualityReason.AudioOnly, cap.reason)
    }

    @Test
    fun `explicit codec choice beats the saver default`() {
        val compatible = PlaybackSettings(codec = CodecPreference.Compatible, dataSaver = DataSaverMode.Always)
        assertFalse(QualityPolicy.decide(inputs(compatible)).preferEfficientCodec)
        val efficient = PlaybackSettings(codec = CodecPreference.Efficient)
        assertTrue(QualityPolicy.decide(inputs(efficient)).preferEfficientCodec)
    }
}
