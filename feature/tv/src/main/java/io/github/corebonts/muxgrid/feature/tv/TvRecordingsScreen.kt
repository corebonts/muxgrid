package io.github.corebonts.muxgrid.feature.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMs
import io.github.corebonts.muxgrid.core.designsystem.formatEpochDayHm
import io.github.corebonts.muxgrid.core.designsystem.formatFileSize
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvRecordingsScreen(
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

    var selectedEntry by remember { mutableStateOf<DvrEntry?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    if (recordingNow.isEmpty() && completed.isEmpty() && scheduled.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recordings or scheduled timers", style = MuxGridType.CardSubtitle, fontSize = 16.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (recordingNow.isNotEmpty()) {
            item(key = "tv_rec_now") {
                TvRecordingShelf(
                    title = "RECORDING NOW",
                    entries = recordingNow,
                    channelById = channelById,
                    nowEpochSec = nowEpochSec,
                    imageLoader = imageLoader,
                    onClickEntry = { entry ->
                        actionError = null
                        selectedEntry = entry
                    },
                )
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "tv_continue") {
                TvRecordingShelf(
                    title = "CONTINUE WATCHING",
                    entries = continueWatching,
                    channelById = channelById,
                    nowEpochSec = nowEpochSec,
                    imageLoader = imageLoader,
                    onClickEntry = { entry ->
                        actionError = null
                        selectedEntry = entry
                    },
                )
            }
        }

        if (completed.isNotEmpty()) {
            item(key = "tv_completed") {
                TvRecordingShelf(
                    title = "ALL RECORDINGS",
                    entries = completed,
                    channelById = channelById,
                    nowEpochSec = nowEpochSec,
                    imageLoader = imageLoader,
                    onClickEntry = { entry ->
                        actionError = null
                        selectedEntry = entry
                    },
                )
            }
        }

        if (scheduled.isNotEmpty()) {
            item(key = "tv_scheduled") {
                TvRecordingShelf(
                    title = "SCHEDULED TIMERS",
                    entries = scheduled,
                    channelById = channelById,
                    nowEpochSec = nowEpochSec,
                    imageLoader = imageLoader,
                    onClickEntry = { entry ->
                        actionError = null
                        selectedEntry = entry
                    },
                )
            }
        }
    }

    selectedEntry?.let { entry ->
        val ch = channelById[entry.channelId]
        val primaryFocusRequester = remember { FocusRequester() }
        LaunchedEffect(entry.id) {
            runCatching { primaryFocusRequester.requestFocus() }
        }

        Dialog(
            onDismissRequest = { if (!actionBusy) selectedEntry = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 440.dp, max = 680.dp)
                    .clip(MuxGridShapes.Dialog)
                    .background(MuxGridColors.CardSurfaceElevated)
                    .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Dialog)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                onClick = {
                                    selectedEntry = null
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
                                    text = if (entry.hasResumePosition) "Resume" else "Watch from Start",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            if (ch != null) {
                                Button(
                                    onClick = {
                                        selectedEntry = null
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
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        actionError = null
                                        onStopRecording(entry.id)
                                            .onSuccess { selectedEntry = null }
                                            .onFailure { actionError = it.message ?: "Failed to stop" }
                                        actionBusy = false
                                    }
                                },
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop & Save", maxLines = 1, softWrap = false)
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        actionError = null
                                        onCancelRecording(entry.id)
                                            .onSuccess { selectedEntry = null }
                                            .onFailure { actionError = it.message ?: "Failed to abort" }
                                        actionBusy = false
                                    }
                                },
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Abort", maxLines = 1, softWrap = false)
                            }
                        }
                        DvrState.COMPLETED -> {
                            if (!entry.isFileMissing) {
                                Button(
                                    onClick = {
                                        selectedEntry = null
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
                                        onClick = {
                                            selectedEntry = null
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
                                            onToggleWatched(entry.id, !entry.isWatched)
                                                .onSuccess { selectedEntry = null }
                                                .onFailure { actionError = it.message ?: "Failed to update" }
                                            actionBusy = false
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
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        onDeleteRecording(entry.id)
                                            .onSuccess { selectedEntry = null }
                                            .onFailure { actionError = it.message ?: "Failed to delete" }
                                        actionBusy = false
                                    }
                                },
                                modifier = if (entry.isFileMissing) Modifier.focusRequester(primaryFocusRequester) else Modifier,
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", maxLines = 1, softWrap = false)
                            }
                        }
                        DvrState.SCHEDULED -> {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        onCancelRecording(entry.id)
                                            .onSuccess { selectedEntry = null }
                                            .onFailure { actionError = it.message ?: "Failed to cancel" }
                                        actionBusy = false
                                    }
                                },
                                modifier = Modifier.focusRequester(primaryFocusRequester),
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel Timer", maxLines = 1, softWrap = false)
                            }
                        }
                        else -> {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        onDeleteRecording(entry.id)
                                            .onSuccess { selectedEntry = null }
                                            .onFailure { actionError = it.message ?: "Failed to remove" }
                                        actionBusy = false
                                    }
                                },
                                modifier = Modifier.focusRequester(primaryFocusRequester),
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    Button(
                        enabled = !actionBusy,
                        onClick = { selectedEntry = null },
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
    }
}

@Composable
private fun TvRecordingShelf(
    title: String,
    entries: List<DvrEntry>,
    channelById: Map<Long, Channel>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onClickEntry: (DvrEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MuxGridType.Eyebrow,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                val ch = channelById[entry.channelId]
                Surface(
                    onClick = { onClickEntry(entry) },
                    modifier = Modifier.width(292.dp),
                    shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Card),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MuxGridColors.CardSurface,
                        contentColor = MuxGridColors.TextPrimary,
                        focusedContainerColor = MuxGridColors.CardSurfaceElevated,
                        focusedContentColor = MuxGridColors.TextPrimary,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border.None,
                        focusedBorder = Border(
                            border = BorderStroke(MuxGridBorders.FocusedCard, MuxGridColors.FocusBorder),
                            shape = MuxGridShapes.Card,
                        ),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
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
                                    piconModel = ch.icon?.let { PiconModel(ch.id, it) },
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
}
