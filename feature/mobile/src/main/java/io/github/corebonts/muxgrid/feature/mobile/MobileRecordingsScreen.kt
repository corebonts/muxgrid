package io.github.corebonts.muxgrid.feature.mobile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.BroadcastUnderlineTab
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrSeriesGroup
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridCompactButton
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMs
import io.github.corebonts.muxgrid.core.designsystem.formatEpochDayHm
import io.github.corebonts.muxgrid.core.designsystem.formatFileSize
import io.github.corebonts.muxgrid.core.designsystem.groupCompletedDvrEntries
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import kotlinx.coroutines.launch

private enum class RecordingsFilter(val label: String) {
    ALL("All"),
    COMPLETED("Completed"),
    SCHEDULED("Scheduled"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileRecordingsScreen(
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
    onRefresh: suspend () -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(RecordingsFilter.ALL) }
    var expandedGroupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionEntry by remember { mutableStateOf<DvrEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<DvrEntry?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val triggerRefresh = {
        if (!isRefreshing) {
            scope.launch {
                isRefreshing = true
                actionFeedback = null
                onRefresh().onFailure {
                    actionFeedback = it.message ?: "Failed to refresh recordings"
                }
                isRefreshing = false
            }
        }
    }

    val channelById = remember(channels) { channels.associateBy { it.id } }
    val allList = remember(dvrEntries) { dvrEntries.values.toList() }

    val recordingNow = remember(allList) {
        allList.filter { it.state == DvrState.RECORDING }.sortedBy { it.startEpochSec }
    }
    val scheduled = remember(allList) {
        allList.filter { it.state == DvrState.SCHEDULED }.sortedBy { it.startEpochSec }
    }
    val completedGroups = remember(allList) {
        groupCompletedDvrEntries(allList)
    }
    val failed = remember(allList) {
        allList.filter { it.state == DvrState.MISSED || it.state == DvrState.INVALID }
            .sortedByDescending { it.startEpochSec }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Underline filter tabs matching Live & Guide
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RecordingsFilter.entries.forEach { tab ->
                BroadcastUnderlineTab(
                    label = tab.label,
                    selected = filter == tab,
                    onClick = { filter = tab },
                )
            }
        }
        MuxGridDivider()

        actionFeedback?.let { msg ->
            Text(
                text = msg,
                color = MuxGridColors.CoralLive,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        val showRecordingNow = recordingNow.isNotEmpty() && filter != RecordingsFilter.COMPLETED
        val showCompleted = completedGroups.isNotEmpty() && filter != RecordingsFilter.SCHEDULED
        val showScheduled = scheduled.isNotEmpty() && filter != RecordingsFilter.COMPLETED
        val showFailed = failed.isNotEmpty() && filter == RecordingsFilter.ALL

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = triggerRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!showRecordingNow && !showCompleted && !showScheduled && !showFailed) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No recordings or scheduled timers",
                                style = MuxGridType.CardSubtitle,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = bottomContentPadding),
                ) {
                    if (showRecordingNow) {
                        item(key = "hdr_rec_now") {
                            SectionHeader("RECORDING NOW")
                        }
                        items(recordingNow, key = { "rec_${it.id}" }) { entry ->
                            val ch = channelById[entry.channelId]
                            RecordingNowCard(
                                entry = entry,
                                channel = ch,
                                nowEpochSec = nowEpochSec,
                                imageLoader = imageLoader,
                                onWatchFromStart = {
                                    val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                                    onPlayRecording(entry, ch, startSec)
                                },
                                onStop = {
                                    scope.launch {
                                        actionFeedback = null
                                        onStopRecording(entry.id).onFailure {
                                            actionFeedback = it.message ?: "Failed to stop recording"
                                        }
                                    }
                                },
                                onOpenMenu = { actionEntry = entry },
                            )
                        }
                    }

                    if (showCompleted) {
                        item(key = "hdr_completed") {
                            SectionHeader("COMPLETED")
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
                                        val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                                        onPlayRecording(entry, channelById[entry.channelId], startSec)
                                    },
                                    onOpenMenu = { actionEntry = entry },
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
                                    onOpenMenu = { actionEntry = it },
                                )
                                MuxGridDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                            }
                        }
                    }

                    if (showScheduled) {
                        item(key = "hdr_scheduled") {
                            SectionHeader("SCHEDULED")
                        }
                        items(scheduled, key = { "sched_${it.id}" }) { entry ->
                            ScheduledTimerRow(
                                entry = entry,
                                channel = channelById[entry.channelId],
                                nowEpochSec = nowEpochSec,
                                imageLoader = imageLoader,
                                onCancel = {
                                    scope.launch {
                                        actionFeedback = null
                                        onCancelRecording(entry.id).onFailure {
                                            actionFeedback = it.message ?: "Failed to cancel timer"
                                        }
                                    }
                                },
                            )
                            MuxGridDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp))
                        }
                    }

                    if (showFailed) {
                        item(key = "hdr_failed") {
                            SectionHeader("MISSED / FAILED")
                        }
                        items(failed, key = { "fail_${it.id}" }) { entry ->
                            FailedRecordingRow(
                                entry = entry,
                                channel = channelById[entry.channelId],
                                nowEpochSec = nowEpochSec,
                                imageLoader = imageLoader,
                                onDelete = { pendingDelete = entry },
                            )
                            MuxGridDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp))
                        }
                    }
                }
            }
        }
    }

    actionEntry?.let { entry ->
        val ch = channelById[entry.channelId]
        ModalBottomSheet(
            onDismissRequest = { actionEntry = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = MuxGridShapes.SheetTop,
            containerColor = MuxGridColors.CardSurfaceElevated,
            contentColor = MuxGridColors.TextPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            size = 40.dp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = MuxGridType.Title, fontSize = 17.sp)
                        val meta = listOfNotNull(
                            ch?.name,
                            formatEpochDayHm(entry.startEpochSec, nowEpochSec),
                            formatDurationMinutes(entry.startEpochSec, entry.stopEpochSec),
                        ).joinToString(" · ")
                        Text(meta, style = MuxGridType.TabularSmall)
                    }
                }

                if (entry.state == DvrState.RECORDING) {
                    Button(
                        onClick = {
                            actionEntry = null
                            val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                            onPlayRecording(entry, ch, startSec)
                        },
                        shape = MuxGridShapes.Sm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MuxGridColors.Accent,
                            contentColor = MuxGridColors.OnAccent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (entry.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (entry.hasResumePosition) "Resume Chase Play" else "Watch from Start", fontWeight = FontWeight.SemiBold)
                    }
                    if (ch != null) {
                        OutlinedButton(
                            onClick = {
                                actionEntry = null
                                onWatchLiveChannel(ch)
                            },
                            shape = MuxGridShapes.Sm,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(MuxGridIcons.Live, contentDescription = null, tint = MuxGridColors.TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Watch Live Channel", color = MuxGridColors.TextPrimary)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            actionEntry = null
                            scope.launch {
                                actionFeedback = null
                                onStopRecording(entry.id).onFailure {
                                    actionFeedback = it.message ?: "Failed to stop recording"
                                }
                            }
                        },
                        shape = MuxGridShapes.Sm,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(MuxGridIcons.Stop, contentDescription = null, tint = MuxGridColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop & Save", color = MuxGridColors.TextPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            actionEntry = null
                            scope.launch {
                                actionFeedback = null
                                onCancelRecording(entry.id).onFailure {
                                    actionFeedback = it.message ?: "Failed to abort recording"
                                }
                            }
                        },
                        shape = MuxGridShapes.Sm,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(MuxGridIcons.Cancel, contentDescription = null, tint = MuxGridColors.CoralLive, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Abort Recording", color = MuxGridColors.CoralLive)
                    }
                } else {
                    if (!entry.isFileMissing) {
                        Button(
                            onClick = {
                                actionEntry = null
                                val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                                onPlayRecording(entry, ch, startSec)
                            },
                            shape = MuxGridShapes.Sm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MuxGridColors.Accent,
                                contentColor = MuxGridColors.OnAccent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (entry.hasResumePosition) "Resume" else "Play", fontWeight = FontWeight.SemiBold)
                        }
                        if (entry.hasResumePosition) {
                            OutlinedButton(
                                onClick = {
                                    actionEntry = null
                                    onPlayRecording(entry, ch, 0L)
                                },
                                shape = MuxGridShapes.Sm,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(MuxGridIcons.Replay, contentDescription = null, tint = MuxGridColors.TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Start Over", color = MuxGridColors.TextPrimary)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                actionEntry = null
                                scope.launch {
                                    actionFeedback = null
                                    onToggleWatched(entry.id, !entry.isWatched).onFailure {
                                        actionFeedback = it.message ?: "Failed to update watched state"
                                    }
                                }
                            },
                            shape = MuxGridShapes.Sm,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(MuxGridIcons.CheckCircle, contentDescription = null, tint = MuxGridColors.TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (entry.isWatched) "Mark as Unwatched" else "Mark as Watched",
                                color = MuxGridColors.TextPrimary,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            actionEntry = null
                            pendingDelete = entry
                        },
                        shape = MuxGridShapes.Sm,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(MuxGridIcons.Delete, contentDescription = null, tint = MuxGridColors.CoralLive, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Recording", color = MuxGridColors.CoralLive)
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = MuxGridShapes.Dialog,
            containerColor = MuxGridColors.CardSurfaceElevated,
            titleContentColor = MuxGridColors.TextPrimary,
            textContentColor = MuxGridColors.TextSecondary,
            title = { Text("Delete Recording?") },
            text = { Text("Delete \"${entry.title}\" from Tvheadend storage? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = entry.id
                        pendingDelete = null
                        scope.launch {
                            actionFeedback = null
                            onDeleteRecording(targetId).onFailure {
                                actionFeedback = it.message ?: "Failed to delete recording"
                            }
                        }
                    },
                ) {
                    Text("Delete", color = MuxGridColors.CoralLive, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = MuxGridColors.TextSecondary)
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

@Composable
private fun RecordingNowCard(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onWatchFromStart: () -> Unit,
    onStop: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val shape = MuxGridShapes.Card
    val dur = entry.durationSec.coerceAtLeast(1L)
    val elapsed = (nowEpochSec - entry.startEpochSec).coerceIn(0L, dur)
    val progress = elapsed.toFloat() / dur.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(MuxGridColors.CardSurfaceElevated),
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
                        size = 42.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = MuxGridType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${channel?.name ?: "Channel"} · ${formatDurationMs(elapsed * 1000L)} / ${formatDurationMs(dur * 1000L)}",
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
                    text = if (entry.hasResumePosition) "Resume" else "Chase Play",
                    leadingIcon = if (entry.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                    containerColor = MuxGridColors.Accent,
                    contentColor = MuxGridColors.OnAccent,
                    onClick = onWatchFromStart,
                )
                MuxGridCompactButton(
                    text = "Stop",
                    leadingIcon = MuxGridIcons.Stop,
                    containerColor = Color.Transparent,
                    contentColor = MuxGridColors.TextPrimary,
                    onClick = onStop,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(MuxGridShapes.Sm)
                        .clickable(onClick = onOpenMenu)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "•••",
                        color = MuxGridColors.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
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
    val sizeLabel = formatFileSize(entry.dataSize)
    val metaParts = listOfNotNull(
        channel?.name,
        formatEpochDayHm(entry.startEpochSec, nowEpochSec),
        if (entry.hasResumePosition && !entry.isFileMissing) {
            val remaining = (entry.durationSec - entry.playPositionSec).coerceAtLeast(0L)
            "${remaining / 60L}m left"
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
                .clip(MuxGridShapes.Sm)
                .clickable(onClick = onOpenMenu)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "•••",
                color = MuxGridColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(group.title, style = MuxGridType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${group.entries.size} recordings · Latest ${formatEpochDayHm(latest.startEpochSec, nowEpochSec)}",
                    style = MuxGridType.TabularSmall,
                    color = MuxGridColors.TextMuted,
                )
            }
            if (unwatchedCount > 0) {
                Text(
                    text = "$unwatchedCount new",
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
                contentDescription = if (expanded) "Collapse" else "Expand",
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
                            val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                            onPlayEntry(entry, startSec)
                        },
                        onOpenMenu = { onOpenMenu(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledTimerRow(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel != null) {
            ChannelPiconBadge(
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                imageLoader = imageLoader,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MuxGridType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${channel?.name ?: "Channel"} · ${formatEpochDayHm(entry.startEpochSec, nowEpochSec)} (${formatDurationMinutes(entry.startEpochSec, entry.stopEpochSec)})",
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
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
                Text("Cancel", color = MuxGridColors.CoralLive, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FailedRecordingRow(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel != null) {
            ChannelPiconBadge(
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                imageLoader = imageLoader,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
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
                    formatEpochDayHm(entry.startEpochSec, nowEpochSec),
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
            Icon(MuxGridIcons.Delete, contentDescription = "Remove", tint = MuxGridColors.CoralLive, modifier = Modifier.size(18.dp))
        }
    }
}
