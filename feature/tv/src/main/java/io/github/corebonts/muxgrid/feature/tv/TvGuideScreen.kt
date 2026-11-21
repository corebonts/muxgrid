package io.github.corebonts.muxgrid.feature.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var actionEventKey by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var pendingConfirm by remember { mutableStateOf<Pair<DvrConfirmKind, DvrEntry>?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val channelById = remember(channels) { channels.associateBy { it.id } }
    val previewEvent = focusedEvent ?: channels.firstNotNullOfOrNull { ch ->
        epgByChannel[ch.id]?.firstOrNull { it.stopEpochSec > windowStartEpochSec && it.startEpochSec < windowEndEpochSec }
    }

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
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Time navigation + Day jump bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { windowStartEpochSec = (windowStartEpochSec - TIMELINE_STEP_SEC).coerceAtLeast(floorToHalfHour(nowEpochSec) - 3600L) },
                shape = tvButtonShape(),
                colors = tvSecondaryButtonColors(),
            ) {
                Icon(MuxGridIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("-2h", fontSize = 12.sp)
            }
            Button(
                onClick = { windowStartEpochSec = floorToHalfHour(nowEpochSec) },
                shape = tvButtonShape(),
                colors = tvPrimaryButtonColors(),
            ) {
                Icon(MuxGridIcons.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { windowStartEpochSec += TIMELINE_STEP_SEC },
                shape = tvButtonShape(),
                colors = tvSecondaryButtonColors(),
            ) {
                Text("+2h", fontSize = 12.sp)
                Spacer(Modifier.width(2.dp))
                Icon(MuxGridIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
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
                    shape = FilterChipDefaults.shape(shape = MuxGridShapes.Sm),
                    colors = FilterChipDefaults.colors(
                        containerColor = MuxGridColors.CardSurface,
                        contentColor = MuxGridColors.TextSecondary,
                        selectedContainerColor = MuxGridColors.Accent,
                        selectedContentColor = MuxGridColors.OnAccent,
                        focusedContainerColor = MuxGridColors.FocusBorder,
                        focusedContentColor = MuxGridColors.CanvasTv,
                        focusedSelectedContainerColor = MuxGridColors.FocusBorder,
                        focusedSelectedContentColor = MuxGridColors.CanvasTv,
                    ),
                    border = FilterChipDefaults.border(
                        border = Border.None,
                        selectedBorder = Border.None,
                    ),
                ) {
                    Text(
                        text = formatDayLabel(dayDate, nowEpochSec),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }

        if (channels.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No channels synced yet", style = MuxGridType.CardSubtitle, fontSize = 16.sp)
            }
        } else {
            // Sticky time ruler + tight broadcast horizontal timeline rows with red "Now" hairline
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val channelColWidth = 172.dp
                val trackWidth = (maxWidth - channelColWidth).coerceAtLeast(320.dp)

                Column(modifier = Modifier.fillMaxSize()) {
                    // Time ruler header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.width(channelColWidth).padding(start = 6.dp),
                        ) {
                            Text(
                                text = "CHANNELS",
                                style = MuxGridType.Eyebrow,
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
                                        .height(16.dp)
                                        .background(MuxGridColors.CoralLive),
                                )
                            }
                        }
                    }

                    // Channel rows
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        itemsIndexed(channels, key = { _, ch -> ch.id }) { rowIndex, channel ->
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
                            val baseCellBg = if (rowIndex % 2 == 0) MuxGridColors.CardSurface else MuxGridColors.CardSurfaceAlt

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Sticky channel column
                                Surface(
                                    onClick = { onWatchChannel(channel) },
                                    modifier = Modifier
                                        .width(channelColWidth)
                                        .fillMaxHeight()
                                        .padding(end = 4.dp),
                                    shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Sm),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = baseCellBg,
                                        contentColor = MuxGridColors.TextPrimary,
                                        focusedContainerColor = MuxGridColors.CardSurfaceElevated,
                                        focusedContentColor = MuxGridColors.TextPrimary,
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border(
                                            border = BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder),
                                            shape = MuxGridShapes.Sm,
                                        ),
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ChannelPiconBadge(
                                            channelNumber = channel.formattedNumber,
                                            channelName = channel.name,
                                            piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                                            imageLoader = imageLoader,
                                            size = 32.dp,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (channel.number > 0) {
                                                Text(
                                                    text = channel.formattedNumber,
                                                    style = MuxGridType.TabularSmall,
                                                    color = MuxGridColors.TextMuted,
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
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        rowCells.forEach { cell ->
                                            val ev = cell.event
                                            if (ev == null) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(cell.weight)
                                                        .fillMaxHeight()
                                                        .clip(MuxGridShapes.Xs)
                                                        .background(baseCellBg.copy(alpha = 0.55f))
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
                                                            actionEventKey = ev.channelId to ev.id
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .weight(cell.weight)
                                                        .fillMaxHeight()
                                                        .onFocusChanged { if (it.isFocused) focusedEvent = ev },
                                                    shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Xs),
                                                    colors = ClickableSurfaceDefaults.colors(
                                                        containerColor = if (isLiveNow) MuxGridColors.CardSurfaceElevated else baseCellBg,
                                                        contentColor = MuxGridColors.TextPrimary,
                                                        focusedContainerColor = MuxGridColors.Accent.copy(alpha = 0.22f),
                                                        focusedContentColor = MuxGridColors.TextPrimary,
                                                    ),
                                                    border = ClickableSurfaceDefaults.border(
                                                        border = if (linkedDvr?.state == DvrState.RECORDING) {
                                                            Border(
                                                                border = BorderStroke(
                                                                    MuxGridBorders.Hairline,
                                                                    MuxGridColors.CoralLive.copy(alpha = 0.65f),
                                                                ),
                                                                shape = MuxGridShapes.Xs,
                                                            )
                                                        } else {
                                                            Border.None
                                                        },
                                                        focusedBorder = Border(
                                                            border = BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder),
                                                            shape = MuxGridShapes.Xs,
                                                        ),
                                                    ),
                                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 9.dp, vertical = 7.dp),
                                                        verticalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                text = ev.title,
                                                                fontSize = 12.5.sp,
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
                                                            color = MuxGridColors.TextMuted,
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

        // Fixed-height bottom details bar for focused program (avoids top-layout height jumping)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MuxGridShapes.Sm)
                .background(MuxGridColors.CardSurface),
        ) {
            MuxGridDivider()
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (previewEvent != null) {
                    val dvr = findMatchingDvrEntry(previewEvent, dvrEntries)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = previewEvent.title,
                                style = MuxGridType.Title,
                                fontSize = 17.sp,
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
                                text = "${formatEpochHm(previewEvent.startEpochSec)}–${formatEpochHm(previewEvent.stopEpochSec)} (${formatDurationMinutes(previewEvent.startEpochSec, previewEvent.stopEpochSec)})",
                                style = MuxGridType.TabularSmall,
                                color = MuxGridColors.AccentSoft,
                            )
                        }
                        val synopsis = previewEvent.description ?: previewEvent.summary ?: previewEvent.subtitle
                        Text(
                            text = synopsis?.takeIf { it.isNotBlank() } ?: "Press OK to watch or schedule recording",
                            style = MuxGridType.CardSubtitle,
                            color = MuxGridColors.TextSecondary,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Program Guide",
                            style = MuxGridType.Title,
                            fontSize = 17.sp,
                            maxLines = 1,
                        )
                        Text(
                            text = "Browse upcoming programs across all channels",
                            style = MuxGridType.CardSubtitle,
                            minLines = 2,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }

    val actionEvent = actionEventKey?.let { (chId, evId) ->
        epgByChannel[chId]?.firstOrNull { it.id == evId }
    }
    actionEvent?.let { ev ->
        val ch = channelById[ev.channelId]
        val linkedDvr = findMatchingDvrEntry(ev, dvrEntries)
        val isLiveNow = nowEpochSec in ev.startEpochSec until ev.stopEpochSec
        val primaryFocusRequester = remember { FocusRequester() }

        LaunchedEffect(ev.id) {
            runCatching { primaryFocusRequester.requestFocus() }
        }

        TvModalDialog(
            onDismissRequest = { if (!actionBusy) actionEventKey = null },
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
                    val meta = buildString {
                        if (ch != null) append("${ch.name} · ")
                        append("${formatEpochHm(ev.startEpochSec)}–${formatEpochHm(ev.stopEpochSec)}")
                        append(" (${formatDurationMinutes(ev.startEpochSec, ev.stopEpochSec)})")
                    }
                    Text(text = meta, style = MuxGridType.TabularSmall)
                }
                if (linkedDvr != null) {
                    DvrStateBadge(state = linkedDvr.state, isWatched = linkedDvr.isWatched)
                }
            }

            if (!ev.subtitle.isNullOrBlank()) {
                Text(
                    text = ev.subtitle!!,
                    style = MuxGridType.CardTitle,
                    color = MuxGridColors.AccentSoft,
                )
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ch != null) {
                    Button(
                        enabled = !actionBusy,
                        onClick = {
                            actionEventKey = null
                            onWatchChannel(ch)
                        },
                        modifier = Modifier.focusRequester(primaryFocusRequester),
                        shape = tvButtonShape(),
                        colors = tvPrimaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isLiveNow) "Watch Live" else "Watch Channel",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
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
                                actionEventKey = null
                                val startSec = if (linkedDvr.hasResumePosition) linkedDvr.playPositionSec else 0L
                                onPlayRecording(linkedDvr, ch, startSec)
                            },
                            modifier = recordButtonModifier,
                            shape = tvButtonShape(),
                            colors = tvDialogSecondaryButtonColors(),
                        ) {
                            Icon(
                                imageVector = if (linkedDvr.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (linkedDvr.hasResumePosition) "Resume Chase Play" else "Watch from Start",
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to linkedDvr },
                            shape = tvButtonShape(),
                            colors = tvDialogSecondaryButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop & Save", maxLines = 1, softWrap = false)
                        }
                    }
                    DvrState.SCHEDULED -> {
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.CANCEL_TIMER to linkedDvr },
                            modifier = recordButtonModifier,
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
                            onClick = {
                                scope.launch {
                                    actionBusy = true
                                    actionError = null
                                    onScheduleRecording(ev.id)
                                        .onSuccess { actionEventKey = null }
                                        .onFailure { actionError = it.message ?: "Failed to schedule" }
                                    actionBusy = false
                                }
                            },
                            modifier = recordButtonModifier,
                            shape = tvButtonShape(),
                            colors = tvDialogSecondaryButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Record, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isLiveNow) "Record Now" else "Record", maxLines = 1, softWrap = false)
                        }
                    }
                }

                Button(
                    enabled = !actionBusy,
                    onClick = { actionEventKey = null },
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
                    val result = when (kind) {
                        DvrConfirmKind.STOP_AND_SAVE -> onStopRecording(entry.id)
                        DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(entry.id)
                        else -> Result.success(Unit)
                    }
                    result
                        .onSuccess { actionEventKey = null }
                        .onFailure { err ->
                            actionError = err.message ?: when (kind) {
                                DvrConfirmKind.STOP_AND_SAVE -> "Failed to stop"
                                DvrConfirmKind.CANCEL_TIMER -> "Failed to cancel"
                                else -> "Action failed"
                            }
                        }
                    actionBusy = false
                }
            },
        )
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
