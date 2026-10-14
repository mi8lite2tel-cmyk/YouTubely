// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.AutoDownloadRule
import app.youtubely.core.model.Subscription
import app.youtubely.core.ui.groupedListItemColors
import app.youtubely.core.ui.groupedListItemShapes

private val QualityChoices = listOf(1080, 720, 480, 360)
private val KeepChoices = listOf(1, 3, 5, 10, 20)
private val MaxMinuteChoices = listOf(0, 10, 20, 30, 60, 120)
private val MinMinuteChoices = listOf(0, 1, 5, 10, 20)

/** What a channel's automatic download saves; every choice can fall back to the global settings. */
@Composable
internal fun AutoDownloadRuleSheet(
    subscription: Subscription,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, rule: AutoDownloadRule) -> Unit,
) {
    val key = subscription.channel.id.value
    var enabled by rememberSaveable(key) { mutableStateOf(subscription.autoDownload) }
    var rule by rememberSaveable(key, stateSaver = RuleSaver) { mutableStateOf(subscription.autoRule) }
    var include by rememberSaveable(key) { mutableStateOf(rule.includeWords.joinToString(", ")) }
    var exclude by rememberSaveable(key) { mutableStateOf(rule.excludeWords.joinToString(", ")) }
    val motion = MaterialTheme.motionScheme

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(
                    rememberScrollState(),
                ).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.library_rule_title, subscription.channel.name),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            RuleSwitch(
                R.string.library_rule_enabled,
                R.string.library_rule_enabled_summary,
                YouTubelyIcons.Download,
                enabled,
                0,
                1,
            ) {
                enabled = it
            }
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                RuleDetails(
                    rule = rule,
                    include = include,
                    exclude = exclude,
                    onRule = { rule = it },
                    onInclude = { include = it },
                    onExclude = { exclude = it },
                )
            }
            Button(
                onClick = {
                    val words =
                        rule.copy(
                            includeWords = AutoDownloadRule.parseWords(include),
                            excludeWords = AutoDownloadRule.parseWords(exclude),
                        )
                    onSave(enabled, words)
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.library_save)) }
        }
    }
}

@Suppress("LongParameterList") // The rule and the two word fields, each with its setter.
@Composable
internal fun RuleDetails(
    rule: AutoDownloadRule,
    include: String,
    exclude: String,
    onRule: (AutoDownloadRule) -> Unit,
    onInclude: (String) -> Unit,
    onExclude: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val default = stringResource(R.string.library_rule_default)
        ChipGroup(R.string.library_rule_quality) {
            ExpressiveFilterChip(!rule.audioOnly && rule.maxHeight == null, {
                onRule(rule.copy(maxHeight = null, audioOnly = false))
            }, default)
            QualityChoices.forEach { height ->
                ExpressiveFilterChip(!rule.audioOnly && rule.maxHeight == height, {
                    onRule(rule.copy(maxHeight = height, audioOnly = false))
                }, "${height}p")
            }
            ExpressiveFilterChip(
                rule.audioOnly,
                { onRule(rule.copy(audioOnly = true)) },
                stringResource(R.string.library_rule_audio),
                icon = YouTubelyIcons.Headphones,
            )
        }
        ChipGroup(R.string.library_rule_keep) {
            ExpressiveFilterChip(rule.keepLast == null, { onRule(rule.copy(keepLast = null)) }, default)
            KeepChoices.forEach { count ->
                ExpressiveFilterChip(rule.keepLast == count, { onRule(rule.copy(keepLast = count)) }, count.toString())
            }
        }
        val any = stringResource(R.string.library_rule_any)
        ChipGroup(R.string.library_rule_min) {
            MinMinuteChoices.forEach { minutes ->
                val label = if (minutes == 0) any else stringResource(R.string.library_rule_minutes, minutes)
                ExpressiveFilterChip(rule.minMinutes == minutes, { onRule(rule.copy(minMinutes = minutes)) }, label)
            }
        }
        ChipGroup(R.string.library_rule_max) {
            MaxMinuteChoices.forEach { minutes ->
                val label = if (minutes == 0) any else stringResource(R.string.library_rule_minutes, minutes)
                ExpressiveFilterChip(rule.maxMinutes == minutes, { onRule(rule.copy(maxMinutes = minutes)) }, label)
            }
        }
        OutlinedTextField(
            value = include,
            onValueChange = onInclude,
            label = { Text(stringResource(R.string.library_rule_include)) },
            placeholder = { Text(stringResource(R.string.library_rule_words_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = exclude,
            onValueChange = onExclude,
            label = { Text(stringResource(R.string.library_rule_exclude)) },
            placeholder = { Text(stringResource(R.string.library_rule_words_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            RuleSwitch(
                R.string.library_rule_skip_shorts,
                null,
                YouTubelyIcons.VideoFilled,
                rule.skipShorts,
                0,
                SWITCH_ROWS,
            ) {
                onRule(rule.copy(skipShorts = it))
            }
            RuleSwitch(R.string.library_rule_skip_live, null, YouTubelyIcons.Live, rule.skipLive, 1, SWITCH_ROWS) {
                onRule(rule.copy(skipLive = it))
            }
            RuleSwitch(
                R.string.library_rule_delete_watched,
                R.string.library_rule_delete_watched_summary,
                YouTubelyIcons.Delete,
                rule.deleteWatched,
                2,
                SWITCH_ROWS,
            ) { onRule(rule.copy(deleteWatched = it)) }
        }
    }
}

private const val SWITCH_ROWS = 3

@Composable
private fun ChipGroup(
    title: Int,
    chips: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Suppress("LongParameterList") // A list row: texts, icon, state, position and the change.
@Composable
private fun RuleSwitch(
    title: Int,
    summary: Int?,
    icon: Int,
    checked: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = groupedListItemShapes(index, count),
        colors = groupedListItemColors(),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
    ) { Text(stringResource(title)) }
}

/** Keeps the rule across configuration changes; the words travel in their text fields. */
private val RuleSaver =
    listSaver<AutoDownloadRule, Any?>(
        save = {
            listOf(
                it.maxHeight,
                it.audioOnly,
                it.keepLast,
                it.skipShorts,
                it.skipLive,
                it.minMinutes,
                it.maxMinutes,
                it.deleteWatched,
            )
        },
        restore = {
            AutoDownloadRule(
                maxHeight = it[0] as Int?,
                audioOnly = it[1] as Boolean,
                keepLast = it[2] as Int?,
                skipShorts = it[3] as Boolean,
                skipLive = it[4] as Boolean,
                minMinutes = it[5] as Int,
                maxMinutes = it[6] as Int,
                deleteWatched = it[7] as Boolean,
            )
        },
    )
