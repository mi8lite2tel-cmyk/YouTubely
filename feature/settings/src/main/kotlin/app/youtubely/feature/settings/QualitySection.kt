// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.CodecPreference
import app.youtubely.core.model.DataSaverMode
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.TrafficKind
import app.youtubely.core.model.TrafficSummary
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val BYTES_PER_MB = 1024L * 1024
private const val MB_PER_GB = 1024
private const val AUTO_ROWS = 8

/** Everything that decides how sharp the picture is and how much data it costs. */
@Composable
internal fun QualitySettings(
    settings: PlaybackSettings,
    onChange: (suspend PlaybackSettingsRepository.() -> Unit) -> Unit,
) {
    val noLimit = stringResource(R.string.settings_quality_no_limit)
    val heights =
        (listOf<Int?>(null) + PlaybackSettings.HEIGHT_CHOICES).map {
            it to
                (it?.let { h -> "${h}p" } ?: noLimit)
        }
    val saverModes =
        listOf(
            DataSaverMode.Off to stringResource(R.string.settings_data_saver_off),
            DataSaverMode.Auto to stringResource(R.string.settings_data_saver_auto),
            DataSaverMode.Always to stringResource(R.string.settings_data_saver_always),
        )
    val fps =
        listOf<Int?>(null).plus(PlaybackSettings.FPS_CHOICES).map {
            it to
                (it?.let { f -> stringResource(R.string.settings_fps_value, f) } ?: noLimit)
        }
    val codecs =
        listOf(
            CodecPreference.Auto to stringResource(R.string.settings_codec_auto),
            CodecPreference.Efficient to stringResource(R.string.settings_codec_efficient),
            CodecPreference.Compatible to stringResource(R.string.settings_codec_compatible),
        )
    Text(
        stringResource(R.string.settings_quality_auto_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    SettingsGroup {
        ChoiceRow(
            R.string.settings_max_quality_wifi,
            YouTubelyIcons.Wifi,
            heights,
            settings.maxHeightUnmetered,
            RowPosition(0, AUTO_ROWS),
        ) {
            onChange { setMaxHeightUnmetered(it) }
        }
        ChoiceRow(
            R.string.settings_max_quality_mobile,
            YouTubelyIcons.DataUsage,
            heights,
            settings.maxHeightMetered,
            RowPosition(1, AUTO_ROWS),
        ) {
            onChange { setMaxHeightMetered(it) }
        }
        ChoiceRow(
            R.string.settings_data_saver,
            YouTubelyIcons.Savings,
            saverModes,
            settings.dataSaver,
            RowPosition(2, AUTO_ROWS),
            stringResource(saverSummary(settings.dataSaver)),
        ) {
            onChange { setDataSaver(it) }
        }
        ChoiceRow(
            R.string.settings_saver_quality,
            YouTubelyIcons.Tune,
            PlaybackSettings.SAVER_HEIGHT_CHOICES.map { it to "${it}p" },
            settings.dataSaverMaxHeight,
            RowPosition(3, AUTO_ROWS),
        ) { onChange { setDataSaverMaxHeight(it) } }
        ChoiceRow(R.string.settings_max_fps, YouTubelyIcons.Speed, fps, settings.maxFps, RowPosition(4, AUTO_ROWS)) {
            onChange { setMaxFps(it) }
        }
        ChoiceRow(R.string.settings_codec, YouTubelyIcons.Video, codecs, settings.codec, RowPosition(5, AUTO_ROWS)) {
            onChange { setCodec(it) }
        }
        SwitchItem(
            title = R.string.settings_fit_player,
            summary = R.string.settings_fit_player_summary,
            icon = YouTubelyIcons.AspectRatio,
            checked = settings.fitToPlayerSize,
            position = RowPosition(6, AUTO_ROWS),
        ) { onChange { setFitToPlayerSize(it) } }
        SwitchItem(
            title = R.string.settings_power_save_limit,
            summary = R.string.settings_power_save_limit_summary,
            icon = YouTubelyIcons.Battery,
            checked = settings.limitOnPowerSave,
            position = RowPosition(7, AUTO_ROWS),
        ) { onChange { setLimitOnPowerSave(it) } }
    }
    Spacer(Modifier.height(16.dp))
    SettingsGroup {
        SwitchItem(
            title = R.string.settings_remember_channel,
            summary = R.string.settings_remember_channel_summary,
            icon = YouTubelyIcons.Subscriptions.outlined,
            checked = settings.rememberPerChannel,
            position = RowPosition(0, 1),
        ) { onChange { setRememberPerChannel(it) } }
    }
}

private fun saverSummary(mode: DataSaverMode): Int =
    when (mode) {
        DataSaverMode.Off -> R.string.settings_data_saver_off_summary
        DataSaverMode.Auto -> R.string.settings_data_saver_auto_summary
        DataSaverMode.Always -> R.string.settings_data_saver_always_summary
    }

/** This month's traffic, the last two weeks as bars, and the monthly mobile allowance. */
@Composable
internal fun TrafficSettings(
    summary: TrafficSummary,
    settings: PlaybackSettings,
    onChange: (suspend PlaybackSettingsRepository.() -> Unit) -> Unit,
    onReset: () -> Unit,
) {
    val noLimit = stringResource(R.string.settings_quality_no_limit)
    val limits =
        PlaybackSettings.MONTHLY_LIMIT_CHOICES_MB.map {
            it to if (it == 0) noLimit else stringResource(R.string.settings_gb, it / MB_PER_GB)
        }
    TrafficCard(summary, settings.monthlyMobileLimitMb, onReset)
    SettingsGroup {
        ChoiceRow(
            R.string.settings_monthly_limit,
            YouTubelyIcons.DataUsage,
            limits,
            settings.monthlyMobileLimitMb,
            RowPosition(0, 1),
        ) {
            onChange { setMonthlyMobileLimitMb(it) }
        }
    }
}

@Composable
private fun TrafficCard(
    summary: TrafficSummary,
    limitMb: Int,
    onReset: () -> Unit,
) {
    val context = LocalContext.current

    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.settings_traffic_month),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            Text(size(summary.monthTotal), style = MaterialTheme.typography.displaySmallEmphasized)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(colors.primary, stringResource(R.string.settings_traffic_mobile), size(summary.monthMobile))
                Legend(colors.tertiary, stringResource(R.string.settings_traffic_wifi), size(summary.monthWifi))
            }
            if (limitMb > 0) {
                val limitBytes = limitMb * BYTES_PER_MB
                LinearWavyProgressIndicator(
                    progress = { (summary.monthMobile.toFloat() / limitBytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (summary.monthMobile >= limitBytes) colors.error else colors.primary,
                )
                Text(
                    stringResource(R.string.settings_traffic_of_limit, size(summary.monthMobile), size(limitBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.settings_traffic_days),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            DayBars(summary, Modifier.fillMaxWidth().height(96.dp))
            TrafficKind.entries.forEach { kind ->
                val bytes = summary.monthByKind[kind] ?: 0L
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(kindLabel(kind)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(size(bytes), style = MaterialTheme.typography.bodyMediumEmphasized)
                }
            }
            Text(
                stringResource(R.string.settings_traffic_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            OutlinedButton(onClick = onReset, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_traffic_reset))
            }
        }
    }
}

@Composable
private fun Legend(
    color: Color,
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text("$label · $value", style = MaterialTheme.typography.bodyMedium)
    }
}

/** One stacked bar per day: mobile data at the bottom, Wi-Fi on top. */
@Composable
private fun DayBars(
    summary: TrafficSummary,
    modifier: Modifier,
) {
    val mobile = MaterialTheme.colorScheme.primary
    val wifi = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val context = LocalContext.current
    val dates = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val description =
        summary.days.joinToString { "${it.date.format(dates)}: ${Formatter.formatShortFileSize(context, it.total)}" }
    Canvas(modifier.semantics { contentDescription = description }) {
        val days = summary.days
        if (days.isEmpty()) return@Canvas
        val max = days.maxOf { it.total }.coerceAtLeast(1L).toFloat()
        val slot = size.width / days.size
        val barWidth = slot * BAR_SHARE
        val radius = CornerRadius(barWidth / 2, barWidth / 2)
        days.forEachIndexed { index, day ->
            val left = index * slot + (slot - barWidth) / 2
            drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
            val total = size.height * day.total / max
            val mobileHeight = size.height * day.mobile / max
            if (total > 0f) drawRoundRect(wifi, Offset(left, size.height - total), Size(barWidth, total), radius)
            if (mobileHeight > 0f) {
                drawRoundRect(mobile, Offset(left, size.height - mobileHeight), Size(barWidth, mobileHeight), radius)
            }
        }
    }
}

private const val BAR_SHARE = 0.6f

private fun kindLabel(kind: TrafficKind): Int =
    when (kind) {
        TrafficKind.Video -> R.string.settings_traffic_kind_video
        TrafficKind.Download -> R.string.settings_traffic_kind_download
        TrafficKind.Images -> R.string.settings_traffic_kind_images
        TrafficKind.Other -> R.string.settings_traffic_kind_other
    }
