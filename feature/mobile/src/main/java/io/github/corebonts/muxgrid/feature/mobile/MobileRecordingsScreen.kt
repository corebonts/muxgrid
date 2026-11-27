package io.github.corebonts.muxgrid.feature.mobile

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.corebonts.muxgrid.core.designsystem.R as DesignR
import io.github.corebonts.muxgrid.core.designsystem.rememberLocalizedDateLabels
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.BroadcastPagerTabRow
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrSeriesGroup
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MobileTopBarHeight
import io.github.corebonts.muxgrid.core.designsystem.MuxGridAlphas
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridCompactButton
import io.github.corebonts.muxgrid.core.designsystem.MuxGridConfirmDialog
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDetailModalLayout
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridModalSheet
import io.github.corebonts.muxgrid.core.designsystem.MuxGridPrimaryActionButton
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSecondaryButton
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSyncingState
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.muxGridCardSurface
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMs
import io.github.corebonts.muxgrid.core.designsystem.formatEpochDayHm
import io.github.corebonts.muxgrid.core.designsystem.formatFileSize
import io.github.corebonts.muxgrid.core.designsystem.formatProgramModalMeta
import io.github.corebonts.muxgrid.core.designsystem.groupCompletedDvrEntries
import io.github.corebonts.muxgrid.core.designsystem.resolveModalSubtitleAndDescription
import io.github.corebonts.muxgrid.core.designsystem.syncStatusMessage
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import kotlinx.coroutines.launch

private enum class RecordingsFilter(@StringRes val labelRes: Int) {
    ALL(DesignR.string.recordings_tab_all),
    COMPLETED(DesignR.string.recordings_tab_completed),
    SCHEDULED(DesignR.string.recordings_tab_scheduled),
}

@Composable
internal fun MobileRecordingsScreen(
    connectionState: ConnectionState,
    dvrEntries: Map<Long, DvrEntry>,
    channels: List<Channel>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    bottomContentPadding: Dp = 84.dp,
    onWatchLiveChannel: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel?, Long) -> Unit,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
    onDeleteRecording: suspend (Long) -> Result<Unit>,
    onToggleWatched: suspend (Long, Boolean) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { RecordingsFilter.entries.size },
    )
    var expandedGroupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionEntryId by remember { mutableStateOf<Long?>(null) }
    var pendingConfirm by remember { mutableStateOf<Pair<DvrConfirmKind, DvrEntry>?>(null) }
    var sheetBusy by remember { mutableStateOf(false) }
    var sheetError by remember { mutableStateOf<String?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    val channelById = remember(channels) { channels.associateBy { it.id } }
    val allList = remember(dvrEntries) { dvrEntries.values.toList() }

    val recordingNow = remember(allList) {
        allList.filter { it.state == DvrState.RECORDING }.sortedBy { it.startEpochSec }
    }
    val scheduled = remember(allList) {
        allList.filter { it.state == DvrState.SCHEDULED }.sortedBy { it.startEpochSec }
    }
    val untitledFallback = stringResource(DesignR.string.common_untitled_recording)
    val completedGroups = remember(allList, untitledFallback) {
        groupCompletedDvrEntries(allList, untitledFallback = untitledFallback)
    }
    val failed = remember(allList) {
        allList.filter { it.state == DvrState.MISSED || it.state == DvrState.INVALID }
            .sortedByDescending { it.startEpochSec }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Underline filter tabs matching Live & Guide
        BroadcastPagerTabRow(
            tabs = RecordingsFilter.entries,
            pagerState = pagerState,
            labelFor = { stringResource(it.labelRes) },
        )
        MuxGridDivider()

        actionFeedback?.let { msg ->
            Text(
                text = msg,
                color = MuxGridColors.CoralLive,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        HorizontalPager(
            state = pagerState,
            key = { page -> RecordingsFilter.entries[page].name },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val filter = RecordingsFilter.entries[page]
            val showRecordingNow = recordingNow.isNotEmpty() && filter != RecordingsFilter.COMPLETED
            val showCompleted = completedGroups.isNotEmpty() && filter != RecordingsFilter.SCHEDULED
            val showScheduled = scheduled.isNotEmpty() && filter != RecordingsFilter.COMPLETED
            val showFailed = failed.isNotEmpty() && filter == RecordingsFilter.ALL

            if (!showRecordingNow && !showCompleted && !showScheduled && !showFailed) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        val (emptyText, showSpinner) = syncStatusMessage(
                            connectionState = connectionState,
                            emptyLabel = stringResource(DesignR.string.recordings_empty),
                        )
                        MuxGridSyncingState(
                            message = emptyText,
                            showSpinner = showSpinner,
                            isTv = false,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = bottomContentPadding),
                ) {
                    if (showRecordingNow) {
                        item(key = "hdr_rec_now") {
                            SectionHeader(stringResource(DesignR.string.recordings_section_recording_now))
                        }
                        items(recordingNow, key = { "rec_${it.id}" }) { entry ->
                            val ch = channelById[entry.channelId]
                            RecordingNowCard(
                                entry = entry,
                                channel = ch,
                                nowEpochSec = nowEpochSec,
                                imageLoader = imageLoader,
                                onResumeOrWatchFromStart = {
                                    onPlayRecording(entry, ch, entry.effectiveStartSec)
                                },
                                onStartOver = {
                                    onPlayRecording(entry, ch, 0L)
                                },
                                onStop = {
                                    pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to entry
                                },
                                onOpenMenu = {
                                    sheetError = null
                                    actionEntryId = entry.id
                                },
                            )
                        }
                    }

                    if (showCompleted) {
                        item(key = "hdr_completed") {
                            SectionHeader(stringResource(DesignR.string.recordings_section_completed))
                        }
                        items(completedGroups, key = { "grp_${it.key}" }) { group ->
                            if (group.entries.size == 1) {
                                val entry = group.entries.first()
                                CompletedRecordingRow(
                                    entry = entry,
                                    channel = channelById[entry.channelId],
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = imageLoader,
                                    onResumeOrPlay = {
                                        onPlayRecording(entry, channelById[entry.channelId], entry.effectiveStartSec)
                                    },
                                    onOpenMenu = {
                                        sheetError = null
                                        actionEntryId = entry.id
                                    },
                                )
                                MuxGridDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                            } else {
                                val expanded = group.key in expandedGroupKeys
                                SeriesGroupRow(
                                    group = group,
                                    expanded = expanded,
                                    channelById = channelById,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = imageLoader,
                                    onToggleExpand = {
                                        expandedGroupKeys = if (expanded) {
                                            expandedGroupKeys - group.key
                                        } else {
                                            expandedGroupKeys + group.key
                                        }
                                    },
                                    onPlayEntry = { entry, startSec ->
                                        onPlayRecording(entry, channelById[entry.channelId], startSec)
                                    },
                                    onOpenMenu = {
                                        sheetError = null
                                        actionEntryId = it.id
                                    },
                                )
                                MuxGridDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                            }
                        }
                    }

                    if (showScheduled) {
                        item(key = "hdr_scheduled") {
                            SectionHeader(stringResource(DesignR.string.recordings_section_scheduled))
                        }
                        items(scheduled, key = { "sched_${it.id}" }) { entry ->
                            ScheduledTimerRow(
                                entry = entry,
                                channel = channelById[entry.channelId],
                                nowEpochSec = nowEpochSec,
                                imageLoader = imageLoader,
                                onOpenMenu = {
                                    sheetError = null
                                    actionEntryId = entry.id
                                },
                                onCancel = {
                                    pendingConfirm = DvrConfirmKind.CANCEL_TIMER to entry
                                },
                            )
                            MuxGridDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                        }
                    }

                    if (showFailed) {
                        item(key = "hdr_failed") {
                            SectionHeader(stringResource(DesignR.string.recordings_section_missed_failed))
                        }
                        items(failed, key = { "fail_${it.id}" }) { entry ->
                            FailedRecordingRow(
                                entry = entry,
                                channel = channelById[entry.channelId],
                                nowEpochSec = nowEpochSec,
                                imageLoader = imageLoader,
                                onOpenMenu = {
                                    sheetError = null
                                    actionEntryId = entry.id
                                },
                                onDelete = { pendingConfirm = DvrConfirmKind.DELETE_RECORDING to entry },
                            )
                            MuxGridDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                        }
                    }
                }
            }
        }
    }

    val actionEntry = actionEntryId?.let { dvrEntries[it] }
    actionEntry?.let { entry ->
        val ch = channelById[entry.channelId]
        val isLiveWindow = nowEpochSec in entry.startEpochSec until entry.stopEpochSec
        val (subtitleText, bodyText) = resolveModalSubtitleAndDescription(
            subtitle = entry.subtitle,
            description = entry.description,
        )
        MuxGridModalSheet(
            onDismissRequest = { if (!sheetBusy) actionEntryId = null },
        ) {
            MuxGridDetailModalLayout(
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
                                labels = rememberLocalizedDateLabels(),
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

                    if (bodyText != null) {
                        Text(
                            text = bodyText,
                            style = MuxGridType.CardSubtitle,
                            color = MuxGridColors.TextPrimary.copy(alpha = MuxGridAlphas.SynopsisText),
                            lineHeight = 19.sp,
                        )
                    }

                    if (!entry.error.isNullOrBlank()) {
                        Text(
                            text = entry.error!!,
                            color = MuxGridColors.CoralLive,
                            fontSize = 13.sp,
                        )
                    }

                    sheetError?.let { err ->
                        Text(text = err, color = MuxGridColors.CoralLive, fontSize = 13.sp)
                    }
                },
                actionsContent = {
                    when (entry.state) {
                        DvrState.RECORDING -> {
                            MuxGridPrimaryActionButton(
                                text = stringResource(
                                    if (entry.hasResumePosition) DesignR.string.recordings_resume_chase_play else DesignR.string.recordings_watch_from_start,
                                ),
                                leadingIcon = if (entry.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                                enabled = !sheetBusy,
                                onClick = {
                                    actionEntryId = null
                                    onPlayRecording(entry, ch, entry.effectiveStartSec)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (entry.hasResumePosition) {
                                MuxGridSecondaryButton(
                                    text = stringResource(DesignR.string.recordings_start_over),
                                    leadingIcon = MuxGridIcons.Replay,
                                    enabled = !sheetBusy,
                                    onClick = {
                                        actionEntryId = null
                                        onPlayRecording(entry, ch, 0L)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (ch != null) {
                                MuxGridSecondaryButton(
                                    text = stringResource(DesignR.string.live_watch_live),
                                    leadingIcon = MuxGridIcons.Live,
                                    enabled = !sheetBusy,
                                    onClick = {
                                        actionEntryId = null
                                        onWatchLiveChannel(ch)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            MuxGridSecondaryButton(
                                text = stringResource(DesignR.string.recordings_stop_and_save),
                                leadingIcon = MuxGridIcons.Stop,
                                enabled = !sheetBusy,
                                onClick = {
                                    pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to entry
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MuxGridSecondaryButton(
                                text = stringResource(DesignR.string.recordings_abort_recording),
                                leadingIcon = MuxGridIcons.Cancel,
                                contentColor = MuxGridColors.CoralLive,
                                enabled = !sheetBusy,
                                onClick = {
                                    pendingConfirm = DvrConfirmKind.ABORT_RECORDING to entry
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        DvrState.COMPLETED -> {
                            if (!entry.isFileMissing) {
                                MuxGridPrimaryActionButton(
                                    text = stringResource(
                                        if (entry.hasResumePosition) DesignR.string.recordings_resume_recording else DesignR.string.recordings_play_recording,
                                    ),
                                    leadingIcon = MuxGridIcons.Play,
                                    enabled = !sheetBusy,
                                    onClick = {
                                        actionEntryId = null
                                        onPlayRecording(entry, ch, entry.effectiveStartSec)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (entry.hasResumePosition) {
                                    MuxGridSecondaryButton(
                                        text = stringResource(DesignR.string.recordings_start_over),
                                        leadingIcon = MuxGridIcons.Replay,
                                        enabled = !sheetBusy,
                                        onClick = {
                                            actionEntryId = null
                                            onPlayRecording(entry, ch, 0L)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                val watchedErrorMsg = stringResource(DesignR.string.recordings_error_watched_failed)
                                MuxGridSecondaryButton(
                                    text = stringResource(
                                        if (entry.isWatched) DesignR.string.recordings_mark_unwatched else DesignR.string.recordings_mark_watched,
                                    ),
                                    leadingIcon = MuxGridIcons.CheckCircle,
                                    enabled = !sheetBusy,
                                    onClick = {
                                        scope.launch {
                                            sheetBusy = true
                                            sheetError = null
                                            try {
                                                onToggleWatched(entry.id, !entry.isWatched)
                                                    .onSuccess { actionEntryId = null }
                                                    .onFailure { sheetError = it.message ?: watchedErrorMsg }
                                            } finally {
                                                sheetBusy = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            MuxGridSecondaryButton(
                                text = stringResource(DesignR.string.recordings_delete_recording),
                                leadingIcon = MuxGridIcons.Delete,
                                contentColor = MuxGridColors.CoralLive,
                                enabled = !sheetBusy,
                                onClick = {
                                    pendingConfirm = DvrConfirmKind.DELETE_RECORDING to entry
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        DvrState.SCHEDULED -> {
                            if (ch != null) {
                                MuxGridPrimaryActionButton(
                                    text = stringResource(if (isLiveWindow) DesignR.string.live_watch_live else DesignR.string.live_watch_channel),
                                    leadingIcon = MuxGridIcons.Play,
                                    enabled = !sheetBusy,
                                    onClick = {
                                        actionEntryId = null
                                        onWatchLiveChannel(ch)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            MuxGridSecondaryButton(
                                text = stringResource(DesignR.string.recordings_cancel_timer),
                                leadingIcon = MuxGridIcons.Cancel,
                                contentColor = MuxGridColors.CoralLive,
                                enabled = !sheetBusy,
                                onClick = {
                                    pendingConfirm = DvrConfirmKind.CANCEL_TIMER to entry
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        DvrState.MISSED, DvrState.INVALID -> {
                            if (ch != null) {
                                MuxGridPrimaryActionButton(
                                    text = stringResource(DesignR.string.live_watch_channel),
                                    leadingIcon = MuxGridIcons.Play,
                                    enabled = !sheetBusy,
                                    onClick = {
                                        actionEntryId = null
                                        onWatchLiveChannel(ch)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            MuxGridSecondaryButton(
                                text = stringResource(DesignR.string.recordings_delete_recording),
                                leadingIcon = MuxGridIcons.Delete,
                                contentColor = MuxGridColors.CoralLive,
                                enabled = !sheetBusy,
                                onClick = {
                                    pendingConfirm = DvrConfirmKind.DELETE_RECORDING to entry
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
            )
        }
    }

    pendingConfirm?.let { (kind, entry) ->
        val spec = dvrConfirmSpec(kind, entry.title)
        MuxGridConfirmDialog(
            title = spec.title,
            message = spec.message,
            confirmLabel = spec.confirmLabel,
            isDestructive = spec.isDestructive,
            enabled = !sheetBusy,
            onDismiss = { pendingConfirm = null },
            onConfirm = {
                val targetId = entry.id
                pendingConfirm = null
                scope.launch {
                    sheetBusy = true
                    sheetError = null
                    actionFeedback = null
                    try {
                        val result = when (kind) {
                            DvrConfirmKind.STOP_AND_SAVE -> onStopRecording(targetId)
                            DvrConfirmKind.ABORT_RECORDING, DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(targetId)
                            DvrConfirmKind.DELETE_RECORDING -> onDeleteRecording(targetId)
                        }
                        result
                            .onSuccess { actionEntryId = null }
                            .onFailure { err ->
                                val msg = err.message ?: spec.failureFallbackMessage
                                if (actionEntryId != null) {
                                    sheetError = msg
                                } else {
                                    actionFeedback = msg
                                }
                            }
                    } finally {
                        sheetBusy = false
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MuxGridType.Eyebrow,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingNowCard(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onResumeOrWatchFromStart: () -> Unit,
    onStartOver: () -> Unit,
    onStop: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val shape = MuxGridShapes.Card
    val dur = entry.durationSec.coerceAtLeast(1L)
    val elapsed = (nowEpochSec - entry.startEpochSec).coerceIn(0L, dur)
    val progress = entry.recordingProgressFraction(nowEpochSec)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .muxGridCardSurface(containerColor = MuxGridColors.CardSurfaceElevated)
            .combinedClickable(
                onClick = onOpenMenu,
                onLongClick = onOpenMenu,
            ),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MuxGridColors.CoralLive),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel != null) {
                    ChannelPiconBadge(
                        channelNumber = channel.formattedNumber,
                        channelName = channel.name,
                        piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                        imageLoader = imageLoader,
                        size = 44.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = MuxGridType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${channel?.name ?: stringResource(DesignR.string.common_channel)} · ${formatDurationMs(elapsed * 1000L)} / ${formatDurationMs(dur * 1000L)}",
                        style = MuxGridType.TabularSmall,
                    )
                }
                DvrStateBadge(state = entry.state)
            }
            LiveProgressBar(
                progress = progress,
                fillColor = MuxGridColors.CoralLive,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuxGridCompactButton(
                    text = stringResource(
                        if (entry.hasResumePosition) DesignR.string.recordings_resume_chase_play else DesignR.string.recordings_watch_from_start,
                    ),
                    leadingIcon = if (entry.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                    containerColor = MuxGridColors.Accent,
                    contentColor = MuxGridColors.OnAccent,
                    onClick = onResumeOrWatchFromStart,
                )
                if (entry.hasResumePosition) {
                    MuxGridCompactButton(
                        text = stringResource(DesignR.string.recordings_start_over),
                        leadingIcon = MuxGridIcons.Replay,
                        containerColor = MuxGridColors.CardSurface,
                        contentColor = MuxGridColors.TextPrimary,
                        onClick = onStartOver,
                    )
                }
                MuxGridCompactButton(
                    text = stringResource(DesignR.string.recordings_stop_and_save),
                    leadingIcon = MuxGridIcons.Stop,
                    containerColor = Color.Transparent,
                    contentColor = MuxGridColors.TextPrimary,
                    onClick = onStop,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(MuxGridShapes.Control)
                        .clickable(onClick = onOpenMenu)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MuxGridIcons.MoreHoriz,
                        contentDescription = stringResource(DesignR.string.player_options_short),
                        tint = MuxGridColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletedRecordingRow(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onResumeOrPlay: () -> Unit,
    onOpenMenu: () -> Unit,
    startPadding: Dp = 16.dp,
) {
    val dateLabels = rememberLocalizedDateLabels()
    val sizeLabel = formatFileSize(entry.dataSize)
    val metaParts = listOfNotNull(
        channel?.name,
        formatEpochDayHm(entry.startEpochSec, nowEpochSec, labels = dateLabels),
        if (entry.hasResumePosition && !entry.isFileMissing) {
            val remaining = (entry.durationSec - entry.playPositionSec).coerceAtLeast(0L)
            stringResource(DesignR.string.time_minutes_left, (remaining / 60L).toInt())
        } else {
            formatDurationMinutes(entry.startEpochSec, entry.stopEpochSec)
        },
        sizeLabel.takeIf { it.isNotEmpty() },
        entry.error?.takeIf { it.isNotBlank() },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (!entry.isFileMissing) onResumeOrPlay() else onOpenMenu() },
                onLongClick = onOpenMenu,
            )
            .padding(start = startPadding, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel != null) {
            ChannelPiconBadge(
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                imageLoader = imageLoader,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MuxGridType.CardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isFileMissing) {
                    DvrStateBadge(state = DvrState.INVALID)
                } else if (entry.isWatched) {
                    DvrStateBadge(state = DvrState.COMPLETED, isWatched = true)
                }
            }
            if (!entry.subtitle.isNullOrBlank()) {
                Text(
                    text = entry.subtitle!!,
                    style = MuxGridType.CardSubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = metaParts.joinToString(" · "),
                style = MuxGridType.TabularSmall,
                color = if (entry.isFileMissing) MuxGridColors.CoralLive else MuxGridColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.hasResumePosition && !entry.isFileMissing) {
                Spacer(Modifier.height(2.dp))
                LiveProgressBar(
                    progress = entry.resumeProgressFraction(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(MuxGridShapes.Control)
                .clickable(onClick = onOpenMenu)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MuxGridIcons.MoreHoriz,
                contentDescription = stringResource(DesignR.string.player_options_short),
                tint = MuxGridColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SeriesGroupRow(
    group: DvrSeriesGroup,
    expanded: Boolean,
    channelById: Map<Long, Channel>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onToggleExpand: () -> Unit,
    onPlayEntry: (DvrEntry, Long) -> Unit,
    onOpenMenu: (DvrEntry) -> Unit,
) {
    val latest = group.entries.first()
    val channel = channelById[latest.channelId]
    val unwatchedCount = group.entries.count { !it.isWatched }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel != null) {
                ChannelPiconBadge(
                    channelNumber = channel.formattedNumber,
                    channelName = channel.name,
                    piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                    imageLoader = imageLoader,
                    size = 44.dp,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(group.title, style = MuxGridType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val countLabel = pluralStringResource(
                    DesignR.plurals.recordings_group_count,
                    group.entries.size,
                    group.entries.size,
                )
                val latestLabel = formatEpochDayHm(latest.startEpochSec, nowEpochSec, labels = rememberLocalizedDateLabels())
                Text(
                    text = stringResource(DesignR.string.recordings_group_summary, countLabel, latestLabel),
                    style = MuxGridType.TabularSmall,
                    color = MuxGridColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unwatchedCount > 0) {
                Text(
                    text = stringResource(DesignR.string.recordings_group_new_count, unwatchedCount),
                    color = MuxGridColors.AccentSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(MuxGridShapes.Badge)
                        .background(MuxGridColors.Accent.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (expanded) MuxGridIcons.ExpandLess else MuxGridIcons.ExpandMore,
                contentDescription = stringResource(if (expanded) DesignR.string.a11y_collapse else DesignR.string.a11y_expand),
                tint = MuxGridColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            Column(modifier = Modifier.background(MuxGridColors.CardSurfaceAlt)) {
                group.entries.forEach { entry ->
                    MuxGridDivider(modifier = Modifier.padding(start = 28.dp, end = 16.dp))
                    CompletedRecordingRow(
                        entry = entry,
                        channel = channelById[entry.channelId],
                        nowEpochSec = nowEpochSec,
                        imageLoader = imageLoader,
                        startPadding = 28.dp,
                        onResumeOrPlay = {
                            onPlayEntry(entry, entry.effectiveStartSec)
                        },
                        onOpenMenu = { onOpenMenu(entry) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduledTimerRow(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onOpenMenu: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpenMenu,
                onLongClick = onOpenMenu,
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel != null) {
            ChannelPiconBadge(
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                imageLoader = imageLoader,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(entry.title, style = MuxGridType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!entry.subtitle.isNullOrBlank()) {
                Text(
                    text = entry.subtitle!!,
                    style = MuxGridType.CardSubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatProgramModalMeta(
                    channelName = channel?.name,
                    startEpochSec = entry.startEpochSec,
                    stopEpochSec = entry.stopEpochSec,
                    nowEpochSec = nowEpochSec,
                    labels = rememberLocalizedDateLabels(),
                ),
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(MuxGridShapes.Sm)
                .background(MuxGridColors.CardSurface)
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(MuxGridIcons.Cancel, contentDescription = null, tint = MuxGridColors.CoralLive, modifier = Modifier.size(14.dp))
                Text(stringResource(DesignR.string.recordings_cancel_timer), color = MuxGridColors.CoralLive, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FailedRecordingRow(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onOpenMenu: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpenMenu,
                onLongClick = onOpenMenu,
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel != null) {
            ChannelPiconBadge(
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                imageLoader = imageLoader,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MuxGridType.CardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                DvrStateBadge(state = entry.state)
            }
            Text(
                text = listOfNotNull(
                    channel?.name,
                    formatEpochDayHm(entry.startEpochSec, nowEpochSec, labels = rememberLocalizedDateLabels()),
                    entry.error?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(MuxGridShapes.Sm)
                .clickable(onClick = onDelete)
                .padding(8.dp),
        ) {
            Icon(MuxGridIcons.Delete, contentDescription = stringResource(DesignR.string.recordings_delete_recording), tint = MuxGridColors.CoralLive, modifier = Modifier.size(18.dp))
        }
    }
}
