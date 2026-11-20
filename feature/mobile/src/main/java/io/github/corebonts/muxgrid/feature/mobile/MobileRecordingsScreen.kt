package io.github.corebonts.muxgrid.feature.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrSeriesGroup
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
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

@Composable
internal fun MobileRecordingsScreen(
    dvrEntries: Map<Long, DvrEntry>,
    channels: List<Channel>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onWatchLiveChannel: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel?, Long) -> Unit,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
    onDeleteRecording: suspend (Long) -> Result<Unit>,
    onToggleWatched: suspend (Long, Boolean) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(RecordingsFilter.ALL) }
    var expandedGroupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<DvrEntry?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

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
        // Segmented filter pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecordingsFilter.entries.forEach { tab ->
                val selected = filter == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MuxGridColors.AccentBlue else MuxGridColors.CardSurface)
                        .border(
                            1.dp,
                            if (selected) MuxGridColors.AccentBlueLight else MuxGridColors.HairlineBorder,
                            RoundedCornerShape(50),
                        )
                        .clickable { filter = tab }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = tab.label,
                        color = if (selected) Color.White else MuxGridColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }

        actionFeedback?.let { msg ->
            Text(
                text = msg,
                color = MuxGridColors.CoralLive,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val showRecordingNow = recordingNow.isNotEmpty() && filter != RecordingsFilter.COMPLETED
        val showCompleted = completedGroups.isNotEmpty() && filter != RecordingsFilter.SCHEDULED
        val showScheduled = scheduled.isNotEmpty() && filter != RecordingsFilter.COMPLETED
        val showFailed = failed.isNotEmpty() && filter == RecordingsFilter.ALL

        if (!showRecordingNow && !showCompleted && !showScheduled && !showFailed) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No recordings or scheduled timers",
                    style = MuxGridType.CardSubtitle,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showRecordingNow) {
                    item(key = "hdr_rec_now") {
                        SectionHeader("Recording Now")
                    }
                    items(recordingNow, key = { "rec_${it.id}" }) { entry ->
                        val ch = channelById[entry.channelId]
                        RecordingNowCard(
                            entry = entry,
                            channel = ch,
                            nowEpochSec = nowEpochSec,
                            imageLoader = imageLoader,
                            onWatchLive = { if (ch != null) onWatchLiveChannel(ch) },
                            onStop = {
                                scope.launch {
                                    actionFeedback = null
                                    onStopRecording(entry.id).onFailure {
                                        actionFeedback = it.message ?: "Failed to stop recording"
                                    }
                                }
                            },
                            onAbort = {
                                scope.launch {
                                    actionFeedback = null
                                    onCancelRecording(entry.id).onFailure {
                                        actionFeedback = it.message ?: "Failed to abort recording"
                                    }
                                }
                            },
                        )
                    }
                }

                if (showCompleted) {
                    item(key = "hdr_completed") {
                        SectionHeader("Completed")
                    }
                    items(completedGroups, key = { "grp_${it.key}" }) { group ->
                        if (group.entries.size == 1) {
                            val entry = group.entries.first()
                            CompletedRecordingCard(
                                entry = entry,
                                channel = channelById[entry.channelId],
                                nowEpochSec = nowEpochSec,
                                imageLoader = imageLoader,
                                onResumeOrPlay = {
                                    val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                                    onPlayRecording(entry, channelById[entry.channelId], startSec)
                                },
                                onPlayFromStart = {
                                    onPlayRecording(entry, channelById[entry.channelId], 0L)
                                },
                                onToggleWatched = {
                                    scope.launch {
                                        actionFeedback = null
                                        onToggleWatched(entry.id, !entry.isWatched).onFailure {
                                            actionFeedback = it.message ?: "Failed to update watched state"
                                        }
                                    }
                                },
                                onRequestDelete = { pendingDelete = entry },
                            )
                        } else {
                            val expanded = group.key in expandedGroupKeys
                            SeriesGroupCard(
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
                                onToggleWatched = { entry ->
                                    scope.launch {
                                        onToggleWatched(entry.id, !entry.isWatched)
                                    }
                                },
                                onRequestDelete = { pendingDelete = it },
                            )
                        }
                    }
                }

                if (showScheduled) {
                    item(key = "hdr_scheduled") {
                        SectionHeader("Scheduled")
                    }
                    items(scheduled, key = { "sched_${it.id}" }) { entry ->
                        ScheduledTimerCard(
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
                    }
                }

                if (showFailed) {
                    item(key = "hdr_failed") {
                        SectionHeader("Missed / Failed")
                    }
                    items(failed, key = { "fail_${it.id}" }) { entry ->
                        FailedRecordingCard(
                            entry = entry,
                            channel = channelById[entry.channelId],
                            nowEpochSec = nowEpochSec,
                            imageLoader = imageLoader,
                            onDelete = { pendingDelete = entry },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
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
                    Text("Cancel", color = MuxGridColors.AccentBlueLight)
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MuxGridType.Title,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun RecordingNowCard(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onWatchLive: () -> Unit,
    onStop: () -> Unit,
    onAbort: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val dur = entry.durationSec.coerceAtLeast(1L)
    val elapsed = (nowEpochSec - entry.startEpochSec).coerceIn(0L, dur)
    val progress = elapsed.toFloat() / dur.toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MuxGridColors.CardSurfaceElevated)
            .border(1.dp, MuxGridColors.CoralLive.copy(alpha = 0.45f), shape)
            .padding(14.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (channel != null) {
                Button(
                    onClick = onWatchLive,
                    colors = ButtonDefaults.buttonColors(containerColor = MuxGridColors.AccentBlue, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Watch Live", fontSize = 12.sp)
                }
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                Text("Stop & Save", color = MuxGridColors.TextPrimary, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onAbort) {
                Text("Abort", color = MuxGridColors.CoralLive, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CompletedRecordingCard(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onResumeOrPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onToggleWatched: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val sizeLabel = formatFileSize(entry.dataSize)
    val metaParts = listOfNotNull(
        channel?.name,
        formatEpochDayHm(entry.startEpochSec, nowEpochSec),
        formatDurationMinutes(entry.startEpochSec, entry.stopEpochSec),
        sizeLabel.takeIf { it.isNotEmpty() },
        entry.error?.takeIf { it.isNotBlank() },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MuxGridColors.CardSurface)
            .border(
                1.dp,
                if (entry.isFileMissing) MuxGridColors.CoralLive.copy(alpha = 0.4f) else MuxGridColors.HairlineBorder,
                shape,
            )
            .then(if (!entry.isFileMissing) Modifier.clickable(onClick = onResumeOrPlay) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                Text(
                    text = entry.title,
                    style = MuxGridType.CardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.isFileMissing) {
                Spacer(Modifier.width(8.dp))
                DvrStateBadge(state = DvrState.INVALID)
            } else if (entry.isWatched) {
                Spacer(Modifier.width(8.dp))
                DvrStateBadge(state = DvrState.COMPLETED, isWatched = true)
            }
        }

        if (entry.hasResumePosition && !entry.isFileMissing) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LiveProgressBar(
                    progress = entry.resumeProgressFraction(),
                    modifier = Modifier.fillMaxWidth(),
                )
                val remaining = (entry.durationSec - entry.playPositionSec).coerceAtLeast(0L)
                Text(
                    text = "Resume at ${formatDurationMs(entry.playPositionSec * 1000L)} (${remaining / 60L}m left)",
                    style = MuxGridType.TabularSmall,
                    color = MuxGridColors.AccentBlueLight,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!entry.isFileMissing) {
                Button(
                    onClick = onResumeOrPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = MuxGridColors.AccentBlue, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(if (entry.hasResumePosition) "Resume" else "Play", fontSize = 12.sp)
                }
                if (entry.hasResumePosition) {
                    OutlinedButton(
                        onClick = onPlayFromStart,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("Start Over", color = MuxGridColors.TextPrimary, fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onToggleWatched,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (entry.isWatched) "Unwatched" else "Watched",
                        color = MuxGridColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Text(
                    text = entry.error ?: "File missing on server",
                    color = MuxGridColors.CoralLive,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onRequestDelete,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Delete", color = MuxGridColors.CoralLive, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SeriesGroupCard(
    group: DvrSeriesGroup,
    expanded: Boolean,
    channelById: Map<Long, Channel>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onToggleExpand: () -> Unit,
    onPlayEntry: (DvrEntry, Long) -> Unit,
    onToggleWatched: (DvrEntry) -> Unit,
    onRequestDelete: (DvrEntry) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val latest = group.entries.first()
    val channel = channelById[latest.channelId]
    val unwatchedCount = group.entries.count { !it.isWatched }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MuxGridColors.CardSurface)
            .border(1.dp, MuxGridColors.HairlineBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
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
                )
            }
            if (unwatchedCount > 0) {
                Text(
                    text = "$unwatchedCount new",
                    color = MuxGridColors.AccentBlueLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MuxGridColors.AccentBlue.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (expanded) "Hide" else "Show",
                color = MuxGridColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (expanded) {
            group.entries.forEach { entry ->
                CompletedRecordingCard(
                    entry = entry,
                    channel = channelById[entry.channelId],
                    nowEpochSec = nowEpochSec,
                    imageLoader = imageLoader,
                    onResumeOrPlay = {
                        val startSec = if (entry.hasResumePosition) entry.playPositionSec else 0L
                        onPlayEntry(entry, startSec)
                    },
                    onPlayFromStart = { onPlayEntry(entry, 0L) },
                    onToggleWatched = { onToggleWatched(entry) },
                    onRequestDelete = { onRequestDelete(entry) },
                )
            }
        }
    }
}

@Composable
private fun ScheduledTimerCard(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onCancel: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MuxGridColors.CardSurface)
            .border(1.dp, MuxGridColors.HairlineBorder, shape)
            .padding(12.dp),
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
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("Cancel", color = MuxGridColors.CoralLive, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FailedRecordingCard(
    entry: DvrEntry,
    channel: Channel?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MuxGridColors.CardSurface)
            .border(1.dp, MuxGridColors.HairlineBorder, shape)
            .padding(12.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onDelete,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("Remove", color = MuxGridColors.CoralLive, fontSize = 12.sp)
        }
    }
}
