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
import androidx.compose.ui.res.stringResource
import io.github.corebonts.muxgrid.core.designsystem.R as DesignR
import io.github.corebonts.muxgrid.core.designsystem.rememberLocalizedDateLabels
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridAlphas
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDetailModalLayout
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridRadii
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSyncingState
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMs
import io.github.corebonts.muxgrid.core.designsystem.formatEpochDayHm
import io.github.corebonts.muxgrid.core.designsystem.formatFileSize
import io.github.corebonts.muxgrid.core.designsystem.formatProgramModalMeta
import io.github.corebonts.muxgrid.core.designsystem.resolveModalSubtitleAndDescription
import io.github.corebonts.muxgrid.core.designsystem.syncStatusMessage
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvRecordingsScreen(
    dvrEntries: Map<Long, DvrEntry>,
    channels: List<Channel>,
    connectionState: ConnectionState = ConnectionState.Disconnected,
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

    val recNowHeader = stringResource(DesignR.string.recordings_section_recording_now)
    val continueHeader = stringResource(DesignR.string.recordings_shelf_continue_watching)
    val allRecsHeader = stringResource(DesignR.string.recordings_shelf_all_recordings)
    val scheduledHeader = stringResource(DesignR.string.recordings_shelf_scheduled_timers)
    val shelves = remember(recordingNow, continueWatching, completed, scheduled, recNowHeader, continueHeader, allRecsHeader, scheduledHeader) {
        buildList {
            if (recordingNow.isNotEmpty()) add(Triple("tv_rec_now", recNowHeader, recordingNow))
            if (continueWatching.isNotEmpty()) add(Triple("tv_continue", continueHeader, continueWatching))
            if (completed.isNotEmpty()) add(Triple("tv_completed", allRecsHeader, completed))
            if (scheduled.isNotEmpty()) add(Triple("tv_scheduled", scheduledHeader, scheduled))
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
        val (emptyText, showSpinner) = syncStatusMessage(
            connectionState = connectionState,
            emptyLabel = stringResource(DesignR.string.recordings_empty),
        )
        MuxGridSyncingState(
            message = emptyText,
            showSpinner = showSpinner,
            isTv = true,
            modifier = Modifier.fillMaxSize(),
        )
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
        val isLiveWindow = nowEpochSec in entry.startEpochSec until entry.stopEpochSec
        val primaryFocusRequester = remember { FocusRequester() }
        val (subtitleText, descText) = resolveModalSubtitleAndDescription(
            subtitle = entry.subtitle,
            description = entry.description,
        )
        LaunchedEffect(entry.id, pendingConfirm == null) {
            if (pendingConfirm == null) {
                runCatching { primaryFocusRequester.requestFocus() }
            }
        }

        TvModalDialog(
            onDismissRequest = { if (!actionBusy) selectedEntryId = null },
        ) {
            MuxGridDetailModalLayout(
                isSplitLayout = true,
                actionsColumnWidth = 224.dp,
                detailsContent = {
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
                            val meta = formatProgramModalMeta(
                                channelName = ch?.name,
                                startEpochSec = entry.startEpochSec,
                                stopEpochSec = entry.stopEpochSec,
                                nowEpochSec = nowEpochSec,
                                dataSizeBytes = entry.dataSize,
                            )
                            Text(meta, style = MuxGridType.TabularSmall)
                        }
                        DvrStateBadge(
                            state = if (entry.isFileMissing) DvrState.INVALID else entry.state,
                            isWatched = entry.isWatched,
                        )
                    }

                    if (entry.state == DvrState.RECORDING) {
                        val dur = entry.durationSec.coerceAtLeast(1L)
                        val elapsed = (nowEpochSec - entry.startEpochSec).coerceIn(0L, dur)
                        val remMins = ((entry.stopEpochSec - nowEpochSec).coerceAtLeast(0L) + 59L) / 60L
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(DesignR.string.recordings_recording_now_elapsed, formatDurationMs(elapsed * 1000L)),
                                    color = MuxGridColors.CoralLive,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(DesignR.string.time_minutes_left, remMins.toInt()),
                                    style = MuxGridType.TabularSmall,
                                    color = MuxGridColors.TextSecondary,
                                )
                            }
                            LiveProgressBar(
                                progress = entry.recordingProgressFraction(nowEpochSec),
                                fillColor = MuxGridColors.CoralLive,
                                height = 3.dp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (entry.state == DvrState.COMPLETED && entry.hasResumePosition && !entry.isFileMissing) {
                        val remMins = ((entry.durationSec - entry.playPositionSec).coerceAtLeast(0L) + 59L) / 60L
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(DesignR.string.recordings_resume_at, formatDurationMs(entry.playPositionSec * 1000L)),
                                    style = MuxGridType.TabularSmall,
                                    color = MuxGridColors.AccentSoft,
                                )
                                Text(
                                    text = stringResource(DesignR.string.time_minutes_left, remMins.toInt()),
                                    style = MuxGridType.TabularSmall,
                                    color = MuxGridColors.TextSecondary,
                                )
                            }
                            LiveProgressBar(
                                progress = entry.resumeProgressFraction(),
                                height = 3.dp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (subtitleText != null) {
                        Text(
                            text = subtitleText,
                            style = MuxGridType.CardTitle,
                            color = MuxGridColors.AccentSoft,
                        )
                    }

                    if (descText != null) {
                        TvScrollableDescriptionBox(
                            text = descText,
                            resetKey = entry.id,
                        )
                    }

                    if (!entry.error.isNullOrBlank()) {
                        Text(
                            text = entry.error!!,
                            color = MuxGridColors.CoralLive,
                            fontSize = 13.sp,
                        )
                    }

                    actionError?.let { err ->
                        Text(err, color = MuxGridColors.CoralLive, fontSize = 13.sp)
                    }
                },
                actionsContent = {
                    when (entry.state) {
                        DvrState.RECORDING -> {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    selectedEntryId = null
                                    onPlayRecording(entry, ch, entry.effectiveStartSec)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(primaryFocusRequester),
                                shape = tvButtonShape(),
                                colors = tvPrimaryButtonColors(),
                            ) {
                                Icon(
                                    imageVector = if (entry.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        if (entry.hasResumePosition) DesignR.string.recordings_resume_chase_play else DesignR.string.recordings_watch_from_start,
                                    ),
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
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = tvButtonShape(),
                                    colors = tvDialogSecondaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(DesignR.string.recordings_start_over), maxLines = 1, softWrap = false)
                                }
                            }
                            if (ch != null) {
                                Button(
                                    enabled = !actionBusy,
                                    onClick = {
                                        selectedEntryId = null
                                        onWatchLiveChannel(ch)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = tvButtonShape(),
                                    colors = tvDialogSecondaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.Live, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(DesignR.string.live_watch_live), maxLines = 1, softWrap = false)
                                }
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = { pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to entry },
                                modifier = Modifier.fillMaxWidth(),
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(DesignR.string.recordings_stop_and_save), maxLines = 1, softWrap = false)
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = { pendingConfirm = DvrConfirmKind.ABORT_RECORDING to entry },
                                modifier = Modifier.fillMaxWidth(),
                                shape = tvButtonShape(),
                                colors = tvDialogDestructiveButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(DesignR.string.recordings_abort_recording), maxLines = 1, softWrap = false)
                            }
                        }
                        DvrState.COMPLETED -> {
                            if (!entry.isFileMissing) {
                                Button(
                                    enabled = !actionBusy,
                                    onClick = {
                                        selectedEntryId = null
                                        onPlayRecording(entry, ch, entry.effectiveStartSec)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester),
                                    shape = tvButtonShape(),
                                    colors = tvPrimaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (entry.hasResumePosition) DesignR.string.recordings_resume_recording else DesignR.string.recordings_play_recording,
                                        ),
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
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = tvButtonShape(),
                                        colors = tvDialogSecondaryButtonColors(),
                                    ) {
                                        Icon(MuxGridIcons.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(DesignR.string.recordings_start_over), maxLines = 1, softWrap = false)
                                    }
                                }
                                val watchedErrorMsg = stringResource(DesignR.string.recordings_error_watched_failed)
                                Button(
                                    enabled = !actionBusy,
                                    onClick = {
                                        scope.launch {
                                            actionBusy = true
                                            actionError = null
                                            try {
                                                onToggleWatched(entry.id, !entry.isWatched)
                                                    .onSuccess { selectedEntryId = null }
                                                    .onFailure { actionError = it.message ?: watchedErrorMsg }
                                            } finally {
                                                actionBusy = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = tvButtonShape(),
                                    colors = tvDialogSecondaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (entry.isWatched) DesignR.string.recordings_mark_unwatched else DesignR.string.recordings_mark_watched,
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = { pendingConfirm = DvrConfirmKind.DELETE_RECORDING to entry },
                                modifier = if (entry.isFileMissing) {
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                                shape = tvButtonShape(),
                                colors = tvDialogDestructiveButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(DesignR.string.recordings_delete_recording), maxLines = 1, softWrap = false)
                            }
                        }
                        DvrState.SCHEDULED -> {
                            if (ch != null) {
                                Button(
                                    enabled = !actionBusy,
                                    onClick = {
                                        selectedEntryId = null
                                        onWatchLiveChannel(ch)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester),
                                    shape = tvButtonShape(),
                                    colors = tvPrimaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(if (isLiveWindow) DesignR.string.live_watch_live else DesignR.string.live_watch_channel),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = { pendingConfirm = DvrConfirmKind.CANCEL_TIMER to entry },
                                modifier = if (ch == null) {
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                                shape = tvButtonShape(),
                                colors = tvDialogDestructiveButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(DesignR.string.recordings_cancel_timer), maxLines = 1, softWrap = false)
                            }
                        }
                        DvrState.MISSED, DvrState.INVALID -> {
                            if (ch != null) {
                                Button(
                                    enabled = !actionBusy,
                                    onClick = {
                                        selectedEntryId = null
                                        onWatchLiveChannel(ch)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester),
                                    shape = tvButtonShape(),
                                    colors = tvPrimaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(DesignR.string.live_watch_channel), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                }
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = { pendingConfirm = DvrConfirmKind.DELETE_RECORDING to entry },
                                modifier = if (ch == null) {
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                                shape = tvButtonShape(),
                                colors = tvDialogDestructiveButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(DesignR.string.recordings_delete_recording), maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    Button(
                        enabled = !actionBusy,
                        onClick = { selectedEntryId = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = tvButtonShape(),
                        colors = tvDialogSecondaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(DesignR.string.common_close), maxLines = 1, softWrap = false)
                    }
                },
            )
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
                                actionError = err.message ?: spec.failureFallbackMessage
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
                val isFocusedState = remember(focusKey) { mutableStateOf(false) }
                val subtitleLabel = remember(entry.id, entry.startEpochSec, ch?.name, nowEpochSec / 3600L) {
                    listOfNotNull(
                        ch?.name,
                        formatEpochDayHm(entry.startEpochSec, nowEpochSec),
                    ).joinToString(" · ")
                }
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
                        .tvFocusableSurface(
                            focusedState = isFocusedState,
                            cornerRadius = MuxGridRadii.Card,
                            containerColor = MuxGridColors.CardSurface,
                            focusedContainerColor = MuxGridColors.CardSurfaceElevated,
                            focusBorderColor = MuxGridColors.FocusBorder,
                            focusBorderWidth = MuxGridBorders.FocusedCard,
                            focusedScale = 1.015f,
                        )
                        .focusRequester(cardFocusRequester)
                        .onFocusChanged { state ->
                            val focused = state.isFocused
                            if (isFocusedState.value != focused) isFocusedState.value = focused
                            if (focused) onFocusEntry(entry)
                        }
                        .clickable(
                            interactionSource = null,
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
                                text = subtitleLabel,
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
                        LiveProgressBar(
                            progress = entry.recordingProgressFraction(nowEpochSec),
                            fillColor = MuxGridColors.CoralLive,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (entry.hasResumePosition) {
                        LiveProgressBar(
                            progress = entry.resumeProgressFraction(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(DesignR.string.recordings_resume_at, formatDurationMs(entry.playPositionSec * 1000L)),
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
