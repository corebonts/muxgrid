package io.github.corebonts.muxgrid.feature.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
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
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.buildUpcomingDays
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
    var selectedDayIndex by remember { mutableStateOf(0) }
    var selectedChannelId by remember { mutableStateOf(channels.firstOrNull()?.id) }
    var focusedEvent by remember { mutableStateOf<EpgEvent?>(null) }
    var actionEvent by remember { mutableStateOf<EpgEvent?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val channelById = remember(channels) { channels.associateBy { it.id } }
    val effectiveChannelId = when {
        selectedChannelId != null && channelById.containsKey(selectedChannelId) -> selectedChannelId
        else -> channels.firstOrNull()?.id
    }
    val selectedChannel = effectiveChannelId?.let { channelById[it] }
    val activeDay = days.getOrElse(selectedDayIndex) { days.first() }
    val dayEvents = remember(effectiveChannelId, activeDay, epgByChannel, nowEpochSec / 60L) {
        val raw = effectiveChannelId?.let { epgByChannel[it] }.orEmpty()
        filterEpgEventsForDay(raw, activeDay, nowEpochSec)
    }

    val previewEvent = focusedEvent?.takeIf { ev -> dayEvents.any { it.id == ev.id } } ?: dayEvents.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top preview header for focused program
        previewEvent?.let { ev ->
            val dvr = findMatchingDvrEntry(ev, dvrEntries)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MuxGridColors.CardSurfaceElevated)
                    .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ev.title,
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
                        text = "${selectedChannel?.name ?: ""} · ${formatEpochHm(ev.startEpochSec)}–${formatEpochHm(ev.stopEpochSec)} (${formatDurationMinutes(ev.startEpochSec, ev.stopEpochSec)})",
                        style = MuxGridType.TabularSmall,
                        color = MuxGridColors.AccentBlueLight,
                    )
                }
                val synopsis = ev.description ?: ev.summary ?: ev.subtitle
                if (!synopsis.isNullOrBlank()) {
                    Text(
                        text = synopsis,
                        style = MuxGridType.CardSubtitle,
                        color = MuxGridColors.TextPrimary.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Day filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEachIndexed { idx, day ->
                FilterChip(
                    selected = idx == selectedDayIndex,
                    onClick = { selectedDayIndex = idx },
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
                    Text(formatDayLabel(day, nowEpochSec), fontSize = 13.sp)
                }
            }
        }

        // Channel selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            channels.forEach { ch ->
                FilterChip(
                    selected = ch.id == effectiveChannelId,
                    onClick = { selectedChannelId = ch.id },
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
                    Text(
                        text = if (ch.number > 0) "${ch.formattedNumber} · ${ch.name}" else ch.name,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        if (dayEvents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No EPG schedule available for this day", style = MuxGridType.CardSubtitle, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(dayEvents, key = { it.id }) { ev ->
                    val linkedDvr = findMatchingDvrEntry(ev, dvrEntries)
                    val isLiveNow = nowEpochSec in ev.startEpochSec until ev.stopEpochSec
                    Surface(
                        onClick = {
                            actionError = null
                            actionEvent = ev
                        },
                        modifier = Modifier.onFocusChanged { if (it.isFocused) focusedEvent = ev },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isLiveNow) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurface,
                            contentColor = MuxGridColors.TextPrimary,
                            focusedContainerColor = MuxGridColors.CardSurfaceElevated,
                            focusedContentColor = MuxGridColors.TextPrimary,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(
                                    1.dp,
                                    if (isLiveNow) MuxGridColors.AccentBlue.copy(alpha = 0.5f) else MuxGridColors.HairlineBorder,
                                ),
                                shape = RoundedCornerShape(14.dp),
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.5.dp, MuxGridColors.FocusBorder),
                                shape = RoundedCornerShape(14.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.width(120.dp)) {
                                Text(
                                    text = "${formatEpochHm(ev.startEpochSec)}–${formatEpochHm(ev.stopEpochSec)}",
                                    style = MuxGridType.TabularNumber,
                                    color = if (isLiveNow) MuxGridColors.AccentBlueLight else MuxGridColors.TextPrimary,
                                )
                                Text(
                                    text = formatDurationMinutes(ev.startEpochSec, ev.stopEpochSec),
                                    style = MuxGridType.TabularSmall,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ev.title,
                                    style = MuxGridType.CardTitle,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val sub = ev.subtitle ?: ev.summary
                                if (!sub.isNullOrBlank()) {
                                    Text(
                                        text = sub,
                                        style = MuxGridType.CardSubtitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isLiveNow) {
                                    Spacer(Modifier.height(6.dp))
                                    val dur = (ev.stopEpochSec - ev.startEpochSec).coerceAtLeast(1L)
                                    val frac = (nowEpochSec - ev.startEpochSec).toFloat() / dur.toFloat()
                                    LiveProgressBar(progress = frac, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            if (linkedDvr != null) {
                                Spacer(Modifier.width(12.dp))
                                DvrStateBadge(state = linkedDvr.state, isWatched = linkedDvr.isWatched)
                            } else if (isLiveNow) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "ON NOW",
                                    color = MuxGridColors.CoralLive,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
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
