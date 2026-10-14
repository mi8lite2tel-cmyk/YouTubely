// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.Subscription
import app.youtubely.core.ui.ChannelListItem
import app.youtubely.core.ui.LoadingState
import kotlinx.collections.immutable.ImmutableList

/** Followed channels with their notification, automatic download and group settings. */
@Composable
internal fun SubscriptionsPage(
    subscriptions: ImmutableList<Subscription>?,
    groups: ImmutableList<String>,
    onOpenChannel: (ChannelId) -> Unit,
    onToggleNotify: (Subscription) -> Unit,
    onToggleAutoDownload: (Subscription) -> Unit,
    onSetGroup: (Subscription, String?) -> Unit,
    onUnsubscribe: (Subscription) -> Unit,
) {
    when {
        subscriptions == null -> {
            LoadingState()
        }

        subscriptions.isEmpty() -> {
            EmptyPage(YouTubelyIcons.Subscriptions.filled, R.string.library_subscriptions_empty)
        }

        else -> {
            var group by rememberSaveable { mutableStateOf<String?>(null) }
            // A group that no channel belongs to any more must not leave an empty list behind.
            val selected = group?.takeIf { it in groups }
            val visible =
                remember(subscriptions, selected) {
                    subscriptions.filter {
                        selected == null ||
                            it.group == selected
                    }
                }
            var editing by remember { mutableStateOf<Subscription?>(null) }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (groups.isNotEmpty()) {
                    item(key = "groups") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            item {
                                ExpressiveFilterChip(
                                    selected == null,
                                    { group = null },
                                    stringResource(R.string.library_all_groups),
                                )
                            }
                            items(groups, key = { it }) { name ->
                                ExpressiveFilterChip(
                                    selected == name,
                                    { group = name },
                                    name,
                                    icon = YouTubelyIcons.Label,
                                )
                            }
                        }
                    }
                }
                items(visible, key = { it.channel.id.value }) { subscription ->
                    SubscriptionItem(
                        subscription = subscription,
                        onOpen = { onOpenChannel(subscription.channel.id) },
                        onToggleNotify = { onToggleNotify(subscription) },
                        onToggleAutoDownload = { onToggleAutoDownload(subscription) },
                        onEditGroup = { editing = subscription },
                        onUnsubscribe = { onUnsubscribe(subscription) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            editing?.let { target ->
                GroupDialog(
                    subscription = target,
                    groups = groups,
                    onDismiss = { editing = null },
                    onSave = {
                        onSetGroup(target, it)
                        editing = null
                    },
                )
            }
        }
    }
}

@Composable
private fun SubscriptionItem(
    subscription: Subscription,
    onOpen: () -> Unit,
    onToggleNotify: () -> Unit,
    onToggleAutoDownload: () -> Unit,
    onEditGroup: () -> Unit,
    onUnsubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channel = subscription.channel
    val motion = MaterialTheme.motionScheme
    ChannelListItem(
        channel = channel,
        onClick = onOpen,
        supporting = subscription.group,
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onToggleNotify) {
                    AnimatedContent(
                        targetState = subscription.notify,
                        transitionSpec = {
                            (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()))
                                .togetherWith(fadeOut(motion.fastEffectsSpec()))
                        },
                        label = "notify",
                    ) { on ->
                        Icon(
                            painterResource(if (on) YouTubelyIcons.Notifications else YouTubelyIcons.NotificationsOff),
                            contentDescription =
                                stringResource(
                                    if (on) R.string.library_notify_on else R.string.library_notify_off,
                                    channel.name,
                                ),
                            tint =
                                if (on) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
                SubscriptionMenu(
                    channelName = channel.name,
                    autoDownload = subscription.autoDownload,
                    onToggleAutoDownload = onToggleAutoDownload,
                    onEditGroup = onEditGroup,
                    onUnsubscribe = onUnsubscribe,
                )
            }
        },
    )
}

@Composable
private fun SubscriptionMenu(
    channelName: String,
    autoDownload: Boolean,
    onToggleAutoDownload: () -> Unit,
    onEditGroup: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(YouTubelyIcons.MoreVert), stringResource(R.string.library_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_auto_download)) },
                leadingIcon = { Icon(painterResource(YouTubelyIcons.Download), contentDescription = null) },
                trailingIcon =
                    if (autoDownload) {
                        { Icon(painterResource(YouTubelyIcons.Check), contentDescription = null) }
                    } else {
                        null
                    },
                onClick = {
                    expanded = false
                    onToggleAutoDownload()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_group)) },
                leadingIcon = { Icon(painterResource(YouTubelyIcons.Label), contentDescription = null) },
                onClick = {
                    expanded = false
                    onEditGroup()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_unsubscribe, channelName)) },
                leadingIcon = { Icon(painterResource(YouTubelyIcons.Subscribed), contentDescription = null) },
                onClick = {
                    expanded = false
                    onUnsubscribe()
                },
            )
        }
    }
}

/** Name a group for a channel, or pick one that exists. */
@Composable
private fun GroupDialog(
    subscription: Subscription,
    groups: ImmutableList<String>,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var name by rememberSaveable(subscription.channel.id.value) { mutableStateOf(subscription.group.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_group_title, subscription.channel.name)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.library_group_hint)) },
                )
                if (groups.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups, key = { it }) { existing ->
                            ExpressiveFilterChip(name == existing, { name = existing }, existing)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.library_save)) } },
        dismissButton = {
            TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.library_group_none)) }
        },
    )
}
