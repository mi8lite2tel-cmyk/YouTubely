// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.transfer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.data.QrTransfer
import app.youtubely.core.data.TransferBundle
import app.youtubely.core.data.TransferScope
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.ui.groupedListItemColors
import app.youtubely.core.ui.groupedListItemShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_FILE_BYTES = 8 * 1024 * 1024
private const val FRAME_MILLIS = 700L

/** Move subscriptions and playlists between phones with QR codes, or in and out of files. */
@Composable
fun TransferScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var scanning by rememberSaveable { mutableStateOf(false) }

    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                scope.launch {
                    val text = viewModel.exportText() ?: return@launch
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                }
            }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                // A subscription list is a few hundred kilobytes at most; anything huge is not one.
                viewModel.preview(bytes?.takeIf { it.size <= MAX_FILE_BYTES }?.toString(Charsets.UTF_8).orEmpty())
            }
        }

    val message = state.message
    val imported = stringResource(R.string.transfer_imported)
    val unreadable = stringResource(R.string.transfer_unreadable)
    val nothing = stringResource(R.string.transfer_nothing)
    LaunchedEffect(message) {
        if (message != null) {
            val text =
                when (message) {
                    is TransferMessage.Imported -> {
                        imported.format(message.summary.channels, message.summary.playlists, message.summary.videos)
                    }

                    TransferMessage.Unreadable -> {
                        unreadable
                    }

                    TransferMessage.Nothing -> {
                        nothing
                    }
                }
            snackbar.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.transfer_title)) },
                subtitle = { Text(stringResource(R.string.transfer_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.transfer_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "scope") { ScopeChips(state.scope, viewModel::setScope) }
            item(key = "qr") {
                ActionRow(R.string.transfer_show_qr, R.string.transfer_show_qr_summary, YouTubelyIcons.QrCode, 0, 4) {
                    viewModel.showQr()
                }
            }
            item(key = "scan") {
                ActionRow(R.string.transfer_scan, R.string.transfer_scan_summary, YouTubelyIcons.QrScanner, 1, 4) {
                    scanning = true
                }
            }
            item(key = "export") {
                ActionRow(R.string.transfer_export, R.string.transfer_export_summary, YouTubelyIcons.Upload, 2, 4) {
                    export.launch("youtubely-backup.json")
                }
            }
            item(key = "import") {
                ActionRow(R.string.transfer_import, R.string.transfer_import_summary, YouTubelyIcons.FolderOpen, 3, 4) {
                    import.launch(arrayOf("*/*"))
                }
            }
            item(key = "formats") {
                Text(
                    stringResource(R.string.transfer_formats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    state.qrFrames?.let { frames -> QrSheet(frames, onDismiss = viewModel::hideQr) }
    if (scanning) {
        ScanSheet(
            onDismiss = { scanning = false },
            onText = {
                scanning = false
                viewModel.preview(it)
            },
        )
    }
    state.preview?.let { bundle ->
        PreviewDialog(bundle, onConfirm = viewModel::confirmImport, onDismiss = viewModel::cancelPreview)
    }
}

@Composable
private fun ScopeChips(
    selected: TransferScope,
    onSelect: (TransferScope) -> Unit,
) {
    val labels =
        listOf(
            TransferScope.Subscriptions to R.string.transfer_scope_subscriptions,
            TransferScope.Playlists to R.string.transfer_scope_playlists,
            TransferScope.WatchLater to R.string.transfer_scope_later,
            TransferScope.Everything to R.string.transfer_scope_everything,
        )
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.transfer_what),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(labels, key = { it.first }) { (scope, label) ->
                ExpressiveFilterChip(selected == scope, { onSelect(scope) }, stringResource(label))
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: Int,
    summary: Int,
    icon: Int,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = groupedListItemShapes(index, count),
        colors = groupedListItemColors(),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        supportingContent = { Text(stringResource(summary)) },
    ) { Text(stringResource(title)) }
}

/** The QR codes of a transfer, shown one after another until the other phone has read them all. */
@Composable
private fun QrSheet(
    frames: List<String>,
    onDismiss: () -> Unit,
) {
    var index by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        while (true) {
            delay(FRAME_MILLIS)
            index = (index + 1) % frames.size
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.transfer_qr_title), style = MaterialTheme.typography.titleLarge)
            Box(Modifier.clip(MaterialTheme.shapes.extraLarge)) {
                QrCodeView(frames[index], stringResource(R.string.transfer_qr_description, index + 1, frames.size))
            }
            Text(
                stringResource(R.string.transfer_qr_frame, index + 1, frames.size),
                style = MaterialTheme.typography.labelLarge,
            )
            LinearWavyProgressIndicator(progress = { (index + 1f) / frames.size }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.transfer_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Reads the QR codes of another phone; hands the text over once every frame is there. */
@Composable
private fun ScanSheet(
    onDismiss: () -> Unit,
    onText: (String) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }

    val receiver = remember { QrTransfer.Receiver() }
    var received by remember { mutableIntStateOf(0) }
    var expected by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.transfer_scan), style = MaterialTheme.typography.titleLarge)
            if (granted) {
                Box(Modifier.fillMaxWidth().height(CAMERA_HEIGHT).clip(MaterialTheme.shapes.extraLarge)) {
                    QrScanner(
                        onCode = { code ->
                            if (!finished && receiver.accept(code)) {
                                received = receiver.received
                                expected = receiver.expected
                                if (receiver.isComplete) {
                                    finished = true
                                    receiver.text()?.let(onText) ?: onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(CAMERA_HEIGHT),
                    )
                }
                val motion = MaterialTheme.motionScheme
                AnimatedVisibility(
                    visible = expected > 0,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.transfer_scan_progress, received, expected),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LinearWavyProgressIndicator(
                            progress = { if (expected == 0) 0f else received.toFloat() / expected },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text(
                    stringResource(R.string.transfer_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(stringResource(R.string.transfer_camera_denied), textAlign = TextAlign.Center)
                FilledTonalButton(onClick = { request.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.transfer_camera_allow))
                }
            }
        }
    }
}

private val CAMERA_HEIGHT = 320.dp

@Composable
private fun PreviewDialog(
    bundle: TransferBundle,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val videos = bundle.playlists.sumOf { it.videos.size } + bundle.watchLater.size
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(YouTubelyIcons.Download), contentDescription = null) },
        title = { Text(stringResource(R.string.transfer_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (bundle.subscriptions.isNotEmpty()) {
                    Text(
                        pluralStringResource(
                            R.plurals.transfer_channels,
                            bundle.subscriptions.size,
                            bundle.subscriptions.size,
                        ),
                    )
                }
                if (bundle.playlists.isNotEmpty()) {
                    val playlists = bundle.playlists.size
                    Text(pluralStringResource(R.plurals.transfer_playlists, playlists, playlists))
                }
                if (videos > 0) Text(pluralStringResource(R.plurals.transfer_videos, videos, videos))
                Text(
                    stringResource(R.string.transfer_preview_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.transfer_do_import)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_cancel)) } },
    )
}
