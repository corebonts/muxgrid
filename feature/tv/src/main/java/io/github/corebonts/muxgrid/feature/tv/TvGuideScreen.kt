package io.github.corebonts.muxgrid.feature.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.buildUpcomingDays
import io.github.corebonts.muxgrid.core.designsystem.findMatchingDvrEntry
import io.github.corebonts.muxgrid.core.designsystem.formatDayLabel
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private const val TIMELINE_WINDOW_SEC = 3L * 3600L // 3-hour horizontal viewport
private const val TIMELINE_STEP_SEC = 2L * 3600L

private data class TimelineCell(
    val key: String,
    val weight: Float,
    val event: EpgEvent?,
    val startEpochSec: Long,
    val stopEpochSec: Long,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvGuideScreen(
    channels: List<Channel>,
    epgByChannel: Map<Long, List<EpgEvent>>,
    dvrEntries: Map<Long, DvrEntry>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onWatchChannel: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel?, Long) -> Unit,
    onScheduleRecording: suspend (Long) -> Result<Long>,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val days = remember(nowEpochSec / 3600L) { buildUpcomingDays(nowEpochSec, count = 7) }
    val initialWindowStart = remember { floorToHalfHour(nowEpochSec) }
    var windowStartEpochSec by remember { mutableLongStateOf(initialWindowStart) }
    val windowEndEpochSec = windowStartEpochSec + TIMELINE_WINDOW_SEC

    var focusedEvent by remember { mutableStateOf<EpgEvent?>(null) }
    var actionEvent by remember { mutableStateOf<EpgEvent?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val channelById = remember(channels) { channels.associateBy { it.id } }
    val previewEvent = focusedEvent ?: channels.firstNotNullOfOrNull { ch ->
        epgByChannel[ch.id]?.firstOrNull { it.stopEpochSec > windowStartEpochSec && it.startEpochSec < windowEndEpochSec }
    }
    val previewChannel = previewEvent?.let { channelById[it.channelId] }

    val slotStarts = remember(windowStartEpochSec) {
        List(6) { idx -> windowStartEpochSec + idx * 1800L } // 6 x 30-min markers
    }
    val nowFraction = remember(nowEpochSec, windowStartEpochSec) {
        if (nowEpochSec in windowStartEpochSec..windowEndEpochSec) {
            (nowEpochSec - windowStartEpochSec).toFloat() / TIMELINE_WINDOW_SEC.toFloat()
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Dynamic top preview header for focused program
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MuxGridColors.CardSurfaceElevated)
                .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            if (previewEvent != null) {
                val dvr = findMatchingDvrEntry(previewEvent, dvrEntries)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = previewEvent.title,
                            style = MuxGridType.Title,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        if (dvr != null) {
                            DvrStateBadge(state = dvr.state, isWatched = dvr.isWatched)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = "${previewChannel?.name ?: ""} · ${formatEpochHm(previewEvent.startEpochSec)}–${formatEpochHm(previewEvent.stopEpochSec)} (${formatDurationMinutes(previewEvent.startEpochSec, previewEvent.stopEpochSec)})",
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.AccentBlueLight,
                        )
                    }
                    val synopsis = previewEvent.description ?: previewEvent.summary ?: previewEvent.subtitle
                    Text(
                        text = synopsis?.takeIf { it.isNotBlank() } ?: "Press OK to watch or schedule recording",
                        style = MuxGridType.CardSubtitle,
                        color = MuxGridColors.TextPrimary.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = "Browse upcoming programs across all channels",
                    style = MuxGridType.CardSubtitle,
                )
            }
        }

        // Time navigation + Day jump bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { windowStartEpochSec = (windowStartEpochSec - TIMELINE_STEP_SEC).coerceAtLeast(floorToHalfHour(nowEpochSec) - 3600L) },
                colors = tvSecondaryButtonColors(),
            ) {
                Text("< -2h", fontSize = 12.sp)
            }
            Button(
                onClick = { windowStartEpochSec = floorToHalfHour(nowEpochSec) },
                colors = tvPrimaryButtonColors(),
            ) {
                Text("Now", fontSize = 12.sp)
            }
            Button(
                onClick = { windowStartEpochSec += TIMELINE_STEP_SEC },
                colors = tvSecondaryButtonColors(),
            ) {
                Text("+2h >", fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            val activeZone = ZoneId.systemDefault()
            val windowDate = Instant.ofEpochSecond(windowStartEpochSec).atZone(activeZone).toLocalDate()
            val todayDate = Instant.ofEpochSecond(nowEpochSec).atZone(activeZone).toLocalDate()
            days.forEach { dayDate ->
                val selected = dayDate == windowDate
                FilterChip(
                    selected = selected,
                    onClick = {
                        val todayStart = floorToHalfHour(nowEpochSec)
                        val targetMidnight = dayDate.atStartOfDay(activeZone).toEpochSecond()
                        windowStartEpochSec = if (dayDate == todayDate) {
                            todayStart
                        } else {
                            targetMidnight + 18L * 3600L // Prime time 18:00 on future days
                        }
                    },
                    colors = FilterChipDefaults.colors(
                        containerColor = MuxGridColors.CardSurface,
                        contentColor = MuxGridColors.TextPrimary,
                        selectedContainerColor = MuxGridColors.AccentBlue,
                        selectedContentColor = Color.White,
                        focusedContainerColor = MuxGridColors.FocusBorder,
                        focusedContentColor = MuxGridColors.CanvasTv,
                        focusedSelectedContainerColor = MuxGridColors.FocusBorder,
                        focusedSelectedContentColor = MuxGridColors.CanvasTv,
                    ),
                ) {
                    Text(formatDayLabel(dayDate, nowEpochSec), fontSize = 12.sp)
                }
            }
        }

        if (channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels synced yet", style = MuxGridType.CardSubtitle, fontSize = 16.sp)
            }
        } else {
            // Sticky time ruler + horizontal timeline rows with red "Now" hairline
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val channelColWidth = 176.dp
                val trackWidth = (maxWidth - channelColWidth).coerceAtLeast(320.dp)

                Column(modifier = Modifier.fillMaxSize()) {
                    // Time ruler header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.width(channelColWidth).padding(start = 6.dp),
                        ) {
                            Text(
                                text = "Channels",
                                style = MuxGridType.TabularSmall,
                                color = MuxGridColors.TextMuted,
                            )
                        }
                        Box(modifier = Modifier.width(trackWidth)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                slotStarts.forEach { slotSec ->
                                    Text(
                                        text = formatEpochHm(slotSec),
                                        style = MuxGridType.TabularSmall,
                                        color = MuxGridColors.TextSecondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            nowFraction?.let { frac ->
                                Box(
                                    modifier = Modifier
                                        .offset(x = trackWidth * frac)
                                        .width(2.dp)
                                        .height(18.dp)
                                        .background(MuxGridColors.CoralLive),
                                )
                            }
                        }
                    }

                    // Channel rows
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(channels, key = { it.id }) { channel ->
                            val rowCells = remember(
                                channel.id,
                                epgByChannel[channel.id],
                                windowStartEpochSec,
                            ) {
                                buildRowTimelineCells(
                                    channelId = channel.id,
                                    events = epgByChannel[channel.id].orEmpty(),
                                    windowStart = windowStartEpochSec,
                                    windowEnd = windowEndEpochSec,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Sticky channel column
                                Surface(
                                    onClick = { onWatchChannel(channel) },
                                    modifier = Modifier
                                        .width(channelColWidth)
                                        .fillMaxHeight()
                                        .padding(end = 8.dp),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = MuxGridColors.CardSurface,
                                        contentColor = MuxGridColors.TextPrimary,
                                        focusedContainerColor = MuxGridColors.CardSurfaceElevated,
                                        focusedContentColor = MuxGridColors.TextPrimary,
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        border = Border(
                                            border = BorderStroke(1.dp, MuxGridColors.HairlineBorder),
                                            shape = RoundedCornerShape(12.dp),
                                        ),
                                        focusedBorder = Border(
                                            border = BorderStroke(2.dp, MuxGridColors.FocusBorder),
                                            shape = RoundedCornerShape(12.dp),
                                        ),
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ChannelPiconBadge(
                                            channelNumber = channel.formattedNumber,
                                            channelName = channel.name,
                                            piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                                            imageLoader = imageLoader,
                                            size = 34.dp,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (channel.number > 0) {
                                                Text(
                                                    text = channel.formattedNumber,
                                                    style = MuxGridType.TabularSmall,
                                                    color = MuxGridColors.AccentBlueLight,
                                                )
                                            }
                                            Text(
                                                text = channel.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }

                                // Horizontal timeline track for this channel
                                Box(
                                    modifier = Modifier
                                        .width(trackWidth)
                                        .fillMaxHeight(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        rowCells.forEach { cell ->
                                            val ev = cell.event
                                            if (ev == null) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(cell.weight)
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(MuxGridColors.CardSurface.copy(alpha = 0.45f))
                                                        .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(10.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart,
                                                ) {
                                                    Text(
                                                        text = "No program data",
                                                        fontSize = 11.sp,
                                                        color = MuxGridColors.TextMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            } else {
                                                val isLiveNow = nowEpochSec in ev.startEpochSec until ev.stopEpochSec
                                                val linkedDvr = findMatchingDvrEntry(ev, dvrEntries)
                                                Surface(
                                                    onClick = {
                                                        if (isLiveNow && linkedDvr == null) {
                                                            onWatchChannel(channel)
                                                        } else {
                                                            actionError = null
                                                            actionEvent = ev
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .weight(cell.weight)
                                                        .fillMaxHeight()
                                                        .onFocusChanged { if (it.isFocused) focusedEvent = ev },
                                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                                    colors = ClickableSurfaceDefaults.colors(
                                                        containerColor = if (isLiveNow) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurface,
                                                        contentColor = MuxGridColors.TextPrimary,
                                                        focusedContainerColor = MuxGridColors.AccentBlue.copy(alpha = 0.28f),
                                                        focusedContentColor = Color.White,
                                                    ),
                                                    border = ClickableSurfaceDefaults.border(
                                                        border = Border(
                                                            border = BorderStroke(
                                                                1.dp,
                                                                when {
                                                                    linkedDvr?.state == DvrState.RECORDING -> MuxGridColors.CoralLive.copy(alpha = 0.65f)
                                                                    isLiveNow -> MuxGridColors.AccentBlue.copy(alpha = 0.55f)
                                                                    else -> MuxGridColors.HairlineBorder
                                                                },
                                                            ),
                                                            shape = RoundedCornerShape(10.dp),
                                                        ),
                                                        focusedBorder = Border(
                                                            border = BorderStroke(2.dp, MuxGridColors.FocusBorder),
                                                            shape = RoundedCornerShape(10.dp),
                                                        ),
                                                    ),
                                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                text = ev.title,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            if (linkedDvr != null) {
                                                                Spacer(Modifier.width(4.dp))
                                                                DvrStateBadge(state = linkedDvr.state, isWatched = linkedDvr.isWatched)
                                                            }
                                                        }
                                                        Text(
                                                            text = "${formatEpochHm(ev.startEpochSec)}–${formatEpochHm(ev.stopEpochSec)}",
                                                            style = MuxGridType.TabularSmall,
                                                            color = if (isLiveNow) MuxGridColors.AccentBlueLight else MuxGridColors.TextMuted,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    nowFraction?.let { frac ->
                                        Box(
                                            modifier = Modifier
                                                .offset(x = trackWidth * frac)
                                                .width(2.dp)
                                                .fillMaxHeight()
                                                .background(MuxGridColors.CoralLive),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionEvent?.let { ev ->
        val ch = channelById[ev.channelId]
        val linkedDvr = findMatchingDvrEntry(ev, dvrEntries)
        val isLiveNow = nowEpochSec in ev.startEpochSec until ev.stopEpochSec
        val primaryFocusRequester = remember { FocusRequester() }

        LaunchedEffect(ev.id) {
            runCatching { primaryFocusRequester.requestFocus() }
        }

        Dialog(onDismissRequest = { if (!actionBusy) actionEvent = null }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 560.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MuxGridColors.CardSurfaceElevated)
                    .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(20.dp))
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
                        Text(ev.title, style = MuxGridType.Title, fontSize = 18.sp)
                        Text(
                            text = "${ch?.name ?: ""} · ${formatEpochHm(ev.startEpochSec)}–${formatEpochHm(ev.stopEpochSec)}",
                            style = MuxGridType.TabularSmall,
                        )
                    }
                }

                val desc = ev.description ?: ev.summary
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = MuxGridType.CardSubtitle,
                        color = MuxGridColors.TextPrimary.copy(alpha = 0.88f),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                actionError?.let { err ->
                    Text(err, color = MuxGridColors.CoralLive, fontSize = 13.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ch != null) {
                        Button(
                            onClick = {
                                actionEvent = null
                                onWatchChannel(ch)
                            },
                            modifier = Modifier.focusRequester(primaryFocusRequester),
                            colors = tvPrimaryButtonColors(),
                        ) {
                            Text(if (isLiveNow) "Watch Live" else "Watch Channel")
                        }
                    }

                    val recordButtonModifier = if (ch == null) {
                        Modifier.focusRequester(primaryFocusRequester)
                    } else {
                        Modifier
                    }

                    when (linkedDvr?.state) {
                        DvrState.RECORDING -> {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    actionEvent = null
                                    val startSec = if (linkedDvr.hasResumePosition) linkedDvr.playPositionSec else 0L
                                    onPlayRecording(linkedDvr, ch, startSec)
                                },
                                modifier = recordButtonModifier,
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text("Watch from Start")
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        actionError = null
                                        onStopRecording(linkedDvr.id)
                                            .onSuccess { actionEvent = null }
                                            .onFailure { actionError = it.message ?: "Failed to stop" }
                                        actionBusy = false
                                    }
                                },
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text("Stop & Save")
                            }
                        }
                        DvrState.SCHEDULED -> {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        actionError = null
                                        onCancelRecording(linkedDvr.id)
                                            .onSuccess { actionEvent = null }
                                            .onFailure { actionError = it.message ?: "Failed to cancel" }
                                        actionBusy = false
                                    }
                                },
                                modifier = recordButtonModifier,
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text("Cancel Timer")
                            }
                        }
                        else -> {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        actionError = null
                                        onScheduleRecording(ev.id)
                                            .onSuccess { actionEvent = null }
                                            .onFailure { actionError = it.message ?: "Failed to schedule" }
                                        actionBusy = false
                                    }
                                },
                                modifier = recordButtonModifier,
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text(if (isLiveNow) "Record Now" else "Record")
                            }
                        }
                    }

                    Button(
                        enabled = !actionBusy,
                        onClick = { actionEvent = null },
                        colors = tvSecondaryButtonColors(),
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

private fun floorToHalfHour(epochSec: Long): Long = (epochSec / 1800L) * 1800L

private fun buildRowTimelineCells(
    channelId: Long,
    events: List<EpgEvent>,
    windowStart: Long,
    windowEnd: Long,
): List<TimelineCell> {
    val visible = events
        .filter { it.stopEpochSec > windowStart && it.startEpochSec < windowEnd }
        .sortedBy { it.startEpochSec }
    if (visible.isEmpty()) {
        return listOf(
            TimelineCell(
                key = "empty_${channelId}_$windowStart",
                weight = 1f,
                event = null,
                startEpochSec = windowStart,
                stopEpochSec = windowEnd,
            ),
        )
    }

    val cells = ArrayList<TimelineCell>()
    var cursor = windowStart
    for (ev in visible) {
        val clampedStart = maxOf(ev.startEpochSec, cursor)
        val clampedEnd = minOf(ev.stopEpochSec, windowEnd)
        if (clampedEnd <= clampedStart) continue
        if (clampedStart > cursor) {
            val gapSec = clampedStart - cursor
            if (gapSec >= 300L) {
                cells.add(
                    TimelineCell(
                        key = "gap_${channelId}_$cursor",
                        weight = gapSec.toFloat().coerceAtLeast(300f),
                        event = null,
                        startEpochSec = cursor,
                        stopEpochSec = clampedStart,
                    ),
                )
            }
        }
        val spanSec = (clampedEnd - clampedStart).coerceAtLeast(300L)
        cells.add(
            TimelineCell(
                key = "ev_${ev.id}",
                weight = spanSec.toFloat(),
                event = ev,
                startEpochSec = clampedStart,
                stopEpochSec = clampedEnd,
            ),
        )
        cursor = clampedEnd
    }
    if (cursor < windowEnd && (windowEnd - cursor) >= 300L) {
        cells.add(
            TimelineCell(
                key = "tail_${channelId}_$cursor",
                weight = (windowEnd - cursor).toFloat(),
                event = null,
                startEpochSec = cursor,
                stopEpochSec = windowEnd,
            ),
        )
    }
    return cells.ifEmpty {
        listOf(
            TimelineCell(
                key = "empty_${channelId}_$windowStart",
                weight = 1f,
                event = null,
                startEpochSec = windowStart,
                stopEpochSec = windowEnd,
            ),
        )
    }
}
