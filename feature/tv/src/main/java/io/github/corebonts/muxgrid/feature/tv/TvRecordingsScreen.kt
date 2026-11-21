package io.github.corebonts.muxgrid.feature.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMs
import io.github.corebonts.muxgrid.core.designsystem.formatEpochDayHm
import io.github.corebonts.muxgrid.core.designsystem.formatFileSize
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvRecordingsScreen(
    dvrEntries: Map<Long, DvrEntry>,
    channels: List<Channel>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    listState: LazyListState = rememberLazyListState(),
    shelfStateFor: (String) -> LazyListState = { LazyListState() },
    focusMemory: TvFocusMemory = remember { TvFocusMemory() },
    focusRequestNonce: Int = 0,
    onWatchLiveChannel: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel?, Long) -> Unit,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
    onDeleteRecording: suspend (Long) -> Result<Unit>,
    onToggleWatched: suspend (Long, Boolean) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val channelById = remember(channels) { channels.associateBy { it.id } }
    val allEntries = remember(dvrEntries) { dvrEntries.values.toList() }

    val recordingNow = remember(allEntries) {
        allEntries.filter { it.state == DvrState.RECORDING }.sortedBy { it.startEpochSec }
    }
    val continueWatching = remember(allEntries) {
        allEntries.filter { it.state == DvrState.COMPLETED && it.hasResumePosition }
            .sortedByDescending { it.startEpochSec }
    }
    val completed = remember(allEntries) {
        allEntries.filter { it.state == DvrState.COMPLETED }.sortedByDescending { it.startEpochSec }
    }
    val scheduled = remember(allEntries) {
        allEntries.filter { it.state == DvrState.SCHEDULED }.sortedBy { it.startEpochSec }
    }

    val shelves = remember(recordingNow, continueWatching, completed, scheduled) {
        buildList {
            if (recordingNow.isNotEmpty()) add(Triple("tv_rec_now", "RECORDING NOW", recordingNow))
            if (continueWatching.isNotEmpty()) add(Triple("tv_continue", "CONTINUE WATCHING", continueWatching))
            if (completed.isNotEmpty()) add(Triple("tv_completed", "ALL RECORDINGS", completed))
            if (scheduled.isNotEmpty()) add(Triple("tv_scheduled", "SCHEDULED TIMERS", scheduled))
        }
    }

    var selectedEntryId by remember { mutableStateOf<Long?>(null) }
    var wasDialogOpen by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<Pair<DvrConfirmKind, DvrEntry>?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var localFocusRestoreNonce by remember { mutableIntStateOf(0) }
    val cardFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(selectedEntryId) {
        val isOpen = selectedEntryId != null
        if (!isOpen && wasDialogOpen) {
            localFocusRestoreNonce++
        }
        wasDialogOpen = isOpen
    }

    fun resolveTarget(): Triple<Int, String, Int>? {
        if (shelves.isEmpty()) return null
        val lastFocusTarget = focusMemory.recordingTarget
        if (lastFocusTarget != null) {
            val exactShelfIdx = shelves.indexOfFirst { it.first == lastFocusTarget.shelfKey }
            if (exactShelfIdx >= 0) {
                val entryIdx = shelves[exactShelfIdx].third.indexOfFirst { it.id == lastFocusTarget.entryId }
                if (entryIdx >= 0) {
                    return Triple(exactShelfIdx, shelves[exactShelfIdx].first, entryIdx)
                }
            }
            shelves.forEachIndexed { shelfIdx, (shelfKey, _, entries) ->
                val entryIdx = entries.indexOfFirst { it.id == lastFocusTarget.entryId }
                if (entryIdx >= 0) {
                    return Triple(shelfIdx, shelfKey, entryIdx)
                }
            }
            if (exactShelfIdx >= 0 && shelves[exactShelfIdx].third.isNotEmpty()) {
                return Triple(exactShelfIdx, shelves[exactShelfIdx].first, 0)
            }
        }
        return Triple(0, shelves[0].first, 0)
    }

    LaunchedEffect(focusRequestNonce, localFocusRestoreNonce) {
        if (focusRequestNonce == 0 && localFocusRestoreNonce == 0) return@LaunchedEffect
        val target = resolveTarget() ?: return@LaunchedEffect
        val (shelfIdx, shelfKey, entryIdx) = target
        val targetEntry = shelves.getOrNull(shelfIdx)?.third?.getOrNull(entryIdx) ?: return@LaunchedEffect
        val focusKey = "${shelfKey}_${targetEntry.id}"

        val isShelfVisible = listState.layoutInfo.visibleItemsInfo.any { it.key == shelfKey }
        if (!isShelfVisible) {
            runCatching { listState.scrollToItem(shelfIdx) }
            withFrameNanos { }
        }
        val rowState = shelfStateFor(shelfKey)
        val isCardVisible = rowState.layoutInfo.visibleItemsInfo.any { it.key == targetEntry.id }
        if (!isCardVisible) {
            runCatching { rowState.scrollToItem(entryIdx) }
        }
        repeat(2) {
            withFrameNanos { }
            val requester = cardFocusRequesters[focusKey]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
        val firstVisibleShelfKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
        if (firstVisibleShelfKey != null) {
            val firstCardId = shelfStateFor(firstVisibleShelfKey).layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Long
            if (firstCardId != null) {
                cardFocusRequesters["${firstVisibleShelfKey}_$firstCardId"]?.let { runCatching { it.requestFocus() } }
            }
        }
    }

    if (shelves.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recordings or scheduled timers", style = MuxGridType.CardSubtitle, fontSize = 16.sp)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        shelves.forEach { (shelfKey, shelfTitle, shelfEntries) ->
            item(key = shelfKey, contentType = "recording_shelf") {
                TvRecordingShelf(
                    shelfKey = shelfKey,
                    title = shelfTitle,
                    entries = shelfEntries,
                    rowState = shelfStateFor(shelfKey),
                    channelById = channelById,
                    nowEpochSec = nowEpochSec,
                    imageLoader = imageLoader,
                    cardFocusRequesters = cardFocusRequesters,
                    onFocusEntry = { entry ->
                        focusMemory.recordingTarget = TvRecordingFocusTarget(shelfKey, entry.id)
                    },
                    onClickEntry = { entry ->
                        focusMemory.recordingTarget = TvRecordingFocusTarget(shelfKey, entry.id)
                        actionError = null
                        selectedEntryId = entry.id
                    },
                )
            }
        }
    }

    val selectedEntry = selectedEntryId?.let { dvrEntries[it] }
    selectedEntry?.let { entry ->
        val ch = channelById[entry.channelId]
        val primaryFocusRequester = remember { FocusRequester() }
        LaunchedEffect(entry.id, pendingConfirm == null) {
            if (pendingConfirm == null) {
                runCatching { primaryFocusRequester.requestFocus() }
            }
        }

        TvModalDialog(
            onDismissRequest = { if (!actionBusy) selectedEntryId = null },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ch != null) {
                    ChannelPiconBadge(
                        channelNumber = ch.formattedNumber,
                        channelName = ch.name,
                        piconModel = ch.icon?.let { PiconModel(ch.id, it) },
                        imageLoader = imageLoader,
                        size = 44.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = MuxGridType.Title, fontSize = 18.sp)
                    val meta = listOfNotNull(
                        ch?.name,
                        formatEpochDayHm(entry.startEpochSec, nowEpochSec),
                        formatDurationMinutes(entry.startEpochSec, entry.stopEpochSec),
                        formatFileSize(entry.dataSize).takeIf { it.isNotEmpty() },
                    ).joinToString(" · ")
                    Text(meta, style = MuxGridType.TabularSmall)
                }
                DvrStateBadge(state = entry.state, isWatched = entry.isWatched)
            }

            if (!entry.description.isNullOrBlank()) {
                Text(
                    text = entry.description!!,
                    style = MuxGridType.CardSubtitle,
                    color = MuxGridColors.TextPrimary.copy(alpha = 0.85f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            actionError?.let { err ->
                Text(err, color = MuxGridColors.CoralLive, fontSize = 13.sp)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (entry.state) {
                    DvrState.RECORDING -> {
                        Button(
                            enabled = !actionBusy,
                            onClick = {
                                selectedEntryId = null
                                val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                                onPlayRecording(entry, ch, startSec)
                            },
                            modifier = Modifier.focusRequester(primaryFocusRequester),
                            shape = tvButtonShape(),
                            colors = tvPrimaryButtonColors(),
                        ) {
                            Icon(
                                imageVector = if (entry.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (entry.hasResumePosition) "Resume Chase Play" else "Watch from Start",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        if (ch != null) {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    selectedEntryId = null
                                    onWatchLiveChannel(ch)
                                },
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Live, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Watch Live", maxLines = 1, softWrap = false)
                            }
                        }
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to entry },
                            shape = tvButtonShape(),
                            colors = tvDialogSecondaryButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop & Save", maxLines = 1, softWrap = false)
                        }
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.ABORT_RECORDING to entry },
                            shape = tvButtonShape(),
                            colors = tvDialogDestructiveButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Abort", maxLines = 1, softWrap = false)
                        }
                    }
                    DvrState.COMPLETED -> {
                        if (!entry.isFileMissing) {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    selectedEntryId = null
                                    val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                                    onPlayRecording(entry, ch, startSec)
                                },
                                modifier = Modifier.focusRequester(primaryFocusRequester),
                                shape = tvButtonShape(),
                                colors = tvPrimaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (entry.hasResumePosition) "Resume" else "Play",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            if (entry.hasResumePosition) {
                                Button(
                                    enabled = !actionBusy,
                                    onClick = {
                                        selectedEntryId = null
                                        onPlayRecording(entry, ch, 0L)
                                    },
                                    shape = tvButtonShape(),
                                    colors = tvDialogSecondaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Start Over", maxLines = 1, softWrap = false)
                                }
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        actionError = null
                                        try {
                                            onToggleWatched(entry.id, !entry.isWatched)
                                                .onSuccess { selectedEntryId = null }
                                                .onFailure { actionError = it.message ?: "Failed to update" }
                                        } finally {
                                            actionBusy = false
                                        }
                                    }
                                },
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (entry.isWatched) "Unwatched" else "Watched", maxLines = 1, softWrap = false)
                            }
                        }
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.DELETE_RECORDING to entry },
                            modifier = if (entry.isFileMissing) Modifier.focusRequester(primaryFocusRequester) else Modifier,
                            shape = tvButtonShape(),
                            colors = tvDialogDestructiveButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", maxLines = 1, softWrap = false)
                        }
                    }
                    DvrState.SCHEDULED -> {
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.CANCEL_TIMER to entry },
                            modifier = Modifier.focusRequester(primaryFocusRequester),
                            shape = tvButtonShape(),
                            colors = tvDialogDestructiveButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel Timer", maxLines = 1, softWrap = false)
                        }
                    }
                    else -> {
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.DELETE_RECORDING to entry },
                            modifier = Modifier.focusRequester(primaryFocusRequester),
                            shape = tvButtonShape(),
                            colors = tvDialogDestructiveButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove", maxLines = 1, softWrap = false)
                        }
                    }
                }
                Button(
                    enabled = !actionBusy,
                    onClick = { selectedEntryId = null },
                    shape = tvButtonShape(),
                    colors = tvDialogSecondaryButtonColors(),
                ) {
                    Icon(MuxGridIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Close", maxLines = 1, softWrap = false)
                }
            }
        }
    }

    pendingConfirm?.let { (kind, entry) ->
        val spec = dvrConfirmSpec(kind, entry.title)
        TvConfirmDialog(
            title = spec.title,
            message = spec.message,
            confirmLabel = spec.confirmLabel,
            isDestructive = spec.isDestructive,
            enabled = !actionBusy,
            onDismiss = { pendingConfirm = null },
            onConfirm = {
                pendingConfirm = null
                scope.launch {
                    actionBusy = true
                    actionError = null
                    try {
                        val result = when (kind) {
                            DvrConfirmKind.STOP_AND_SAVE -> onStopRecording(entry.id)
                            DvrConfirmKind.ABORT_RECORDING, DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(entry.id)
                            DvrConfirmKind.DELETE_RECORDING -> onDeleteRecording(entry.id)
                        }
                        result
                            .onSuccess { selectedEntryId = null }
                            .onFailure { err ->
                                actionError = err.message ?: when (kind) {
                                    DvrConfirmKind.STOP_AND_SAVE -> "Failed to stop"
                                    DvrConfirmKind.ABORT_RECORDING -> "Failed to abort"
                                    DvrConfirmKind.CANCEL_TIMER -> "Failed to cancel"
                                    DvrConfirmKind.DELETE_RECORDING -> "Failed to delete"
                                }
                            }
                    } finally {
                        actionBusy = false
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvRecordingShelf(
    shelfKey: String,
    title: String,
    entries: List<DvrEntry>,
    rowState: LazyListState,
    channelById: Map<Long, Channel>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    cardFocusRequesters: MutableMap<String, FocusRequester>,
    onFocusEntry: (DvrEntry) -> Unit,
    onClickEntry: (DvrEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MuxGridType.Eyebrow,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        LazyRow(
            state = rowState,
            modifier = Modifier
                .focusGroup()
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            items(
                items = entries,
                key = { it.id },
                contentType = { "recording_card" },
            ) { entry ->
                val ch = channelById[entry.channelId]
                val focusKey = "${shelfKey}_${entry.id}"
                val cardFocusRequester = remember(focusKey) { FocusRequester() }
                val piconModel = remember(ch?.id, ch?.icon) {
                    ch?.icon?.let { PiconModel(ch.id, it) }
                }
                var isFocused by remember(focusKey) { mutableStateOf(false) }
                DisposableEffect(focusKey) {
                    cardFocusRequesters[focusKey] = cardFocusRequester
                    onDispose {
                        if (cardFocusRequesters[focusKey] === cardFocusRequester) {
                            cardFocusRequesters.remove(focusKey)
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .width(292.dp)
                        .graphicsLayer {
                            val scale = if (isFocused) 1.015f else 1f
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(MuxGridShapes.Card)
                        .background(if (isFocused) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurface)
                        .then(
                            if (isFocused) {
                                Modifier.border(MuxGridBorders.FocusedCard, MuxGridColors.FocusBorder, MuxGridShapes.Card)
                            } else {
                                Modifier
                            },
                        )
                        .focusRequester(cardFocusRequester)
                        .onFocusChanged { state ->
                            val focused = state.isFocused
                            if (isFocused != focused) isFocused = focused
                            if (focused) onFocusEntry(entry)
                        }
                        .clickable(
                            interactionSource = remember(focusKey) { MutableInteractionSource() },
                            indication = null,
                            onClick = { onClickEntry(entry) },
                        )
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = entry.title
                        }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (ch != null) {
                            ChannelPiconBadge(
                                channelNumber = ch.formattedNumber,
                                channelName = ch.name,
                                piconModel = piconModel,
                                imageLoader = imageLoader,
                                size = 40.dp,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MuxGridType.CardTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(
                                    ch?.name,
                                    formatEpochDayHm(entry.startEpochSec, nowEpochSec),
                                ).joinToString(" · "),
                                style = MuxGridType.TabularSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DvrStateBadge(
                            state = if (entry.isFileMissing) DvrState.INVALID else entry.state,
                            isWatched = entry.isWatched,
                        )
                    }

                    if (entry.state == DvrState.RECORDING) {
                        val dur = entry.durationSec.coerceAtLeast(1L)
                        val elapsed = (nowEpochSec - entry.startEpochSec).coerceIn(0L, dur)
                        LiveProgressBar(
                            progress = elapsed.toFloat() / dur.toFloat(),
                            fillColor = MuxGridColors.CoralLive,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (entry.hasResumePosition) {
                        LiveProgressBar(
                            progress = entry.resumeProgressFraction(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Resume at ${formatDurationMs(entry.playPositionSec * 1000L)}",
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.AccentSoft,
                        )
                    } else {
                        Text(
                            text = listOfNotNull(
                                formatDurationMinutes(entry.startEpochSec, entry.stopEpochSec),
                                formatFileSize(entry.dataSize).takeIf { it.isNotEmpty() },
                                entry.error?.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MuxGridType.TabularSmall,
                            color = if (entry.isFileMissing) MuxGridColors.CoralLive else MuxGridColors.TextMuted,
                        )
                    }
                }
            }
        }
    }
}
