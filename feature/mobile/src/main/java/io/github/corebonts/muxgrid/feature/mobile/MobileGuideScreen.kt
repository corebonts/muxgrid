package io.github.corebonts.muxgrid.feature.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.BroadcastUnderlineTab
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridConfirmDialog
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridModalSheet
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.buildUpcomingDays
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.filterEpgEventsForDay
import io.github.corebonts.muxgrid.core.designsystem.findMatchingDvrEntry
import io.github.corebonts.muxgrid.core.designsystem.formatDayLabel
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileGuideScreen(
    channels: List<Channel>,
    epgByChannel: Map<Long, List<EpgEvent>>,
    dvrEntries: Map<Long, DvrEntry>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    initialSelectedChannelId: Long?,
    bottomContentPadding: Dp = 84.dp,
    onWatchChannel: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel?, Long) -> Unit,
    onScheduleRecording: suspend (Long) -> Result<Long>,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val days = remember(nowEpochSec / 3600L) { buildUpcomingDays(nowEpochSec, count = 7) }
    var selectedDayIndex by remember { mutableStateOf(0) }
    var selectedChannelId by remember(initialSelectedChannelId) {
        mutableStateOf(initialSelectedChannelId ?: channels.firstOrNull()?.id)
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedEvent by remember { mutableStateOf<EpgEvent?>(null) }
    var pendingConfirm by remember { mutableStateOf<Pair<DvrConfirmKind, DvrEntry>?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val activeDay = days.getOrElse(selectedDayIndex) { days.first() }
    val channelById = remember(channels) { channels.associateBy { it.id } }

    // Ensure selectedChannelId stays valid if channel list loads after initial render
    val effectiveChannelId = when {
        selectedChannelId != null && channelById.containsKey(selectedChannelId) -> selectedChannelId
        else -> channels.firstOrNull()?.id
    }

    val trimmedQuery = searchQuery.trim()
    val agendaItems: List<Pair<EpgEvent, Channel?>> = remember(
        trimmedQuery,
        effectiveChannelId,
        activeDay,
        epgByChannel,
        channels,
        nowEpochSec / 60L,
    ) {
        if (trimmedQuery.isNotEmpty()) {
            val results = ArrayList<Pair<EpgEvent, Channel?>>()
            for (ch in channels) {
                val list = epgByChannel[ch.id].orEmpty()
                for (ev in list) {
                    if (ev.stopEpochSec >= nowEpochSec - 900L && (
                            ev.title.contains(trimmedQuery, ignoreCase = true) ||
                                (ev.subtitle?.contains(trimmedQuery, ignoreCase = true) == true) ||
                                (ev.description?.contains(trimmedQuery, ignoreCase = true) == true)
                            )
                    ) {
                        results.add(ev to ch)
                    }
                }
            }
            results.sortedBy { it.first.startEpochSec }.take(120)
        } else {
            val ch = effectiveChannelId?.let { channelById[it] }
            val events = effectiveChannelId?.let { epgByChannel[it] }.orEmpty()
            filterEpgEventsForDay(events, activeDay, nowEpochSec).map { it to ch }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact Search box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(MuxGridShapes.Sm)
                .background(MuxGridColors.CardSurface)
                .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Sm)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MuxGridIcons.Search,
                contentDescription = null,
                tint = MuxGridColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search upcoming programs…",
                        color = MuxGridColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MuxGridColors.TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(MuxGridColors.AccentSoft),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (searchQuery.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(MuxGridShapes.Xs)
                        .clickable { searchQuery = "" }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MuxGridIcons.Close,
                        contentDescription = "Clear search",
                        tint = MuxGridColors.AccentSoft,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        if (trimmedQuery.isEmpty()) {
            // Day underline tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                days.forEachIndexed { idx, day ->
                    BroadcastUnderlineTab(
                        label = formatDayLabel(day, nowEpochSec),
                        selected = idx == selectedDayIndex,
                        onClick = { selectedDayIndex = idx },
                    )
                }
            }
            MuxGridDivider()

            // Compact borderless channel strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                channels.forEach { ch ->
                    val selected = ch.id == effectiveChannelId
                    Row(
                        modifier = Modifier
                            .clip(MuxGridShapes.Sm)
                            .background(if (selected) MuxGridColors.CardSurfaceElevated else Color.Transparent)
                            .clickable { selectedChannelId = ch.id }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        ChannelPiconBadge(
                            channelNumber = ch.formattedNumber,
                            channelName = ch.name,
                            piconModel = ch.icon?.let { PiconModel(ch.id, it) },
                            imageLoader = imageLoader,
                            size = 22.dp,
                        )
                        Text(
                            text = ch.name,
                            color = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
            MuxGridDivider()
        }

        if (agendaItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (trimmedQuery.isNotEmpty()) "No programs match \"$trimmedQuery\"" else "No EPG data for this day",
                    style = MuxGridType.CardSubtitle,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = bottomContentPadding),
            ) {
                items(agendaItems, key = { it.first.id }) { (event, channel) ->
                    val linkedDvr = findMatchingDvrEntry(event, dvrEntries)
                    val isLiveNow = nowEpochSec in event.startEpochSec until event.stopEpochSec
                    val isRecording = linkedDvr?.state == DvrState.RECORDING

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(if (isLiveNow) MuxGridColors.CardSurface else Color.Transparent)
                                .clickable {
                                    actionError = null
                                    selectedEvent = event
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLiveNow || isRecording) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(if (isRecording) MuxGridColors.CoralLive else MuxGridColors.Accent),
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(
                                        start = if (isLiveNow || isRecording) 13.dp else 16.dp,
                                        end = 16.dp,
                                        top = 11.dp,
                                        bottom = 11.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.width(56.dp)) {
                                    Text(
                                        text = formatEpochHm(event.startEpochSec),
                                        style = MuxGridType.TabularNumber,
                                        color = MuxGridColors.TextPrimary,
                                    )
                                    Text(
                                        text = formatEpochHm(event.stopEpochSec),
                                        style = MuxGridType.TabularSmall,
                                    )
                                    Text(
                                        text = formatDurationMinutes(event.startEpochSec, event.stopEpochSec),
                                        style = MuxGridType.TabularSmall,
                                        color = MuxGridColors.TextMuted,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = event.title,
                                            style = MuxGridType.CardTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (linkedDvr != null) {
                                            Spacer(Modifier.width(8.dp))
                                            DvrStateBadge(state = linkedDvr.state, isWatched = linkedDvr.isWatched)
                                        } else if (isLiveNow) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "ON NOW",
                                                color = MuxGridColors.CoralLive,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    if (trimmedQuery.isNotEmpty() && channel != null) {
                                        Text(
                                            text = "${channel.name} · ${formatDayLabel(buildUpcomingDays(event.startEpochSec, 1).first(), nowEpochSec)}",
                                            style = MuxGridType.TabularSmall,
                                            color = MuxGridColors.AccentSoft,
                                        )
                                    } else if (!event.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = event.subtitle!!,
                                            style = MuxGridType.CardSubtitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else if (!event.summary.isNullOrBlank()) {
                                        Text(
                                            text = event.summary!!,
                                            style = MuxGridType.CardSubtitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (isLiveNow) {
                                        Spacer(Modifier.height(6.dp))
                                        val dur = (event.stopEpochSec - event.startEpochSec).coerceAtLeast(1L)
                                        val frac = (nowEpochSec - event.startEpochSec).toFloat() / dur.toFloat()
                                        LiveProgressBar(
                                            progress = frac,
                                            fillColor = if (isRecording) MuxGridColors.CoralLive else MuxGridColors.ProgressFill,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                        MuxGridDivider()
                    }
                }
            }
        }
    }

    val detailEvent = selectedEvent
    if (detailEvent != null) {
        val detailChannel = channelById[detailEvent.channelId]
        val linkedDvr = findMatchingDvrEntry(detailEvent, dvrEntries)
        val isLiveNow = nowEpochSec in detailEvent.startEpochSec until detailEvent.stopEpochSec

        MuxGridModalSheet(
            onDismissRequest = { if (!actionBusy) selectedEvent = null },
        ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (detailChannel != null) {
                        ChannelPiconBadge(
                            channelNumber = detailChannel.formattedNumber,
                            channelName = detailChannel.name,
                            piconModel = detailChannel.icon?.let { PiconModel(detailChannel.id, it) },
                            imageLoader = imageLoader,
                            size = 44.dp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detailEvent.title,
                            style = MuxGridType.Title,
                            fontSize = 18.sp,
                        )
                        val meta = buildString {
                            if (detailChannel != null) append("${detailChannel.name} · ")
                            append("${formatEpochHm(detailEvent.startEpochSec)}–${formatEpochHm(detailEvent.stopEpochSec)}")
                            append(" (${formatDurationMinutes(detailEvent.startEpochSec, detailEvent.stopEpochSec)})")
                        }
                        Text(text = meta, style = MuxGridType.TabularSmall)
                    }
                    if (linkedDvr != null) {
                        DvrStateBadge(state = linkedDvr.state, isWatched = linkedDvr.isWatched)
                    }
                }

                if (!detailEvent.subtitle.isNullOrBlank()) {
                    Text(
                        text = detailEvent.subtitle!!,
                        style = MuxGridType.CardTitle,
                        color = MuxGridColors.AccentSoft,
                    )
                }

                val body = detailEvent.description ?: detailEvent.summary
                if (!body.isNullOrBlank()) {
                    Text(
                        text = body,
                        style = MuxGridType.CardSubtitle,
                        color = MuxGridColors.TextPrimary.copy(alpha = 0.88f),
                        lineHeight = 19.sp,
                    )
                }

                actionError?.let { err ->
                    Text(
                        text = err,
                        color = MuxGridColors.CoralLive,
                        fontSize = 13.sp,
                    )
                }

                if (linkedDvr?.state == DvrState.RECORDING) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (detailChannel != null) {
                            Button(
                                onClick = {
                                    selectedEvent = null
                                    onWatchChannel(detailChannel)
                                },
                                shape = MuxGridShapes.Sm,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MuxGridColors.Accent,
                                    contentColor = MuxGridColors.OnAccent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isLiveNow) "Watch Live" else "Watch Channel", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                enabled = !actionBusy,
                                shape = MuxGridShapes.Sm,
                                onClick = {
                                    selectedEvent = null
                                    val startSec = if (linkedDvr.hasResumePosition) linkedDvr.playPositionSec else 0L
                                    onPlayRecording(linkedDvr, detailChannel, startSec)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(MuxGridIcons.Replay, contentDescription = null, tint = MuxGridColors.AccentSoft, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (linkedDvr.hasResumePosition) "Resume Chase Play" else "Chase Play",
                                    color = MuxGridColors.AccentSoft,
                                    maxLines = 1,
                                )
                            }
                            OutlinedButton(
                                enabled = !actionBusy,
                                shape = MuxGridShapes.Sm,
                                onClick = {
                                    pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to linkedDvr
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(MuxGridIcons.Stop, contentDescription = null, tint = MuxGridColors.TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop & Save", color = MuxGridColors.TextPrimary, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (detailChannel != null) {
                            Button(
                                onClick = {
                                    selectedEvent = null
                                    onWatchChannel(detailChannel)
                                },
                                shape = MuxGridShapes.Sm,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MuxGridColors.Accent,
                                    contentColor = MuxGridColors.OnAccent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isLiveNow) "Watch Live" else "Watch Channel", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }

                        when (linkedDvr?.state) {
                            DvrState.SCHEDULED -> {
                                OutlinedButton(
                                    enabled = !actionBusy,
                                    shape = MuxGridShapes.Sm,
                                    onClick = {
                                        pendingConfirm = DvrConfirmKind.CANCEL_TIMER to linkedDvr
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(MuxGridIcons.Cancel, contentDescription = null, tint = MuxGridColors.CoralLive, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cancel Timer", color = MuxGridColors.CoralLive, maxLines = 1)
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    enabled = !actionBusy,
                                    shape = MuxGridShapes.Sm,
                                    onClick = {
                                        scope.launch {
                                            actionBusy = true
                                            actionError = null
                                            onScheduleRecording(detailEvent.id)
                                                .onSuccess { selectedEvent = null }
                                                .onFailure { actionError = it.message ?: "Failed to schedule recording" }
                                            actionBusy = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(MuxGridIcons.Record, contentDescription = null, tint = MuxGridColors.CoralLive, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isLiveNow) "Record Now" else "Record",
                                        color = MuxGridColors.CoralLive,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    pendingConfirm?.let { (kind, entry) ->
        val spec = dvrConfirmSpec(kind, entry.title)
        MuxGridConfirmDialog(
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
                    val result = when (kind) {
                        DvrConfirmKind.STOP_AND_SAVE -> onStopRecording(entry.id)
                        DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(entry.id)
                        else -> Result.success(Unit)
                    }
                    result
                        .onSuccess { selectedEvent = null }
                        .onFailure {
                            actionError = it.message ?: when (kind) {
                                DvrConfirmKind.STOP_AND_SAVE -> "Failed to stop recording"
                                DvrConfirmKind.CANCEL_TIMER -> "Failed to cancel timer"
                                else -> "Action failed"
                            }
                        }
                    actionBusy = false
                }
            },
        )
    }
}
