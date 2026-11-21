package io.github.corebonts.muxgrid.feature.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import io.github.corebonts.muxgrid.core.designsystem.DvrLookupIndex
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.HALF_HOUR_SEC
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MobileTopBarHeight
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridConfirmDialog
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridModalSheet
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.buildAvailableGuideDays
import io.github.corebonts.muxgrid.core.designsystem.buildRowTimelineCells
import io.github.corebonts.muxgrid.core.designsystem.computeDayJumpWindowStart
import io.github.corebonts.muxgrid.core.designsystem.computeGuideEventBounds
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.filterEpgEventsForDay
import io.github.corebonts.muxgrid.core.designsystem.floorToHalfHour
import io.github.corebonts.muxgrid.core.designsystem.formatDayLabel
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatEpochDayHm
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.scrollItemIntoViewport
import io.github.corebonts.muxgrid.core.designsystem.searchEpgEvents
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileGuideScreen(
    channels: List<Channel>,
    epgByChannel: Map<Long, List<EpgEvent>>,
    dvrEntries: Map<Long, DvrEntry>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    initialSelectedChannelId: Long?,
    useTimelineGrid: Boolean = false,
    bottomContentPadding: Dp = 84.dp,
    onWatchChannel: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel?, Long) -> Unit,
    onScheduleRecording: suspend (Long) -> Result<Long>,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val activeZone = remember { ZoneId.systemDefault() }
    val todayDate = remember(nowEpochSec / 3600L) {
        Instant.ofEpochSecond(nowEpochSec).atZone(activeZone).toLocalDate()
    }
    val todayStartHalfHour = floorToHalfHour(nowEpochSec)
    var selectedDay by remember { mutableStateOf(todayDate) }
    var windowStartEpochSec by remember { mutableLongStateOf(todayStartHalfHour) }
    var selectedChannelId by remember(initialSelectedChannelId) {
        mutableStateOf(initialSelectedChannelId ?: channels.firstOrNull()?.id)
    }
    val searchState = rememberTextFieldState()
    var isSearchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var selectedEventKey by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var pendingConfirm by remember { mutableStateOf<Pair<DvrConfirmKind, DvrEntry>?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val searchQuery = searchState.text.toString()

    BackHandler(enabled = isSearchExpanded) {
        searchState.clearText()
        isSearchExpanded = false
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    val channelById = remember(channels) { channels.associateBy { it.id } }
    val dvrIndex = remember(dvrEntries) { DvrLookupIndex(dvrEntries) }

    // Ensure selectedChannelId stays valid if channel list loads after initial render
    val effectiveChannelId = when {
        selectedChannelId != null && channelById.containsKey(selectedChannelId) -> selectedChannelId
        else -> channels.firstOrNull()?.id
    }

    val days = remember(useTimelineGrid, effectiveChannelId, epgByChannel, nowEpochSec / 3600L) {
        if (useTimelineGrid) {
            buildAvailableGuideDays(epgByChannel, nowEpochSec, activeZone)
        } else {
            val events = effectiveChannelId?.let { epgByChannel[it] }.orEmpty()
            buildAvailableGuideDays(events, nowEpochSec, activeZone)
        }
    }
    val windowDate = remember(windowStartEpochSec) {
        Instant.ofEpochSecond(windowStartEpochSec).atZone(activeZone).toLocalDate()
    }
    val activeDay = if (useTimelineGrid) {
        windowDate
    } else if (selectedDay in days) {
        selectedDay
    } else {
        todayDate
    }
    val selectedDayIndex = remember(days, activeDay) {
        val exact = days.indexOf(activeDay)
        if (exact >= 0) {
            exact
        } else {
            days.indexOfLast { !it.isAfter(activeDay) }.coerceAtLeast(0)
        }
    }
    val selectedChannelIndex = remember(channels, effectiveChannelId) {
        channels.indexOfFirst { it.id == effectiveChannelId }
    }

    val dayListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val agendaListState = rememberLazyListState()
    val timelineListState = rememberLazyListState()
    var hasScrolledDayOnce by remember { mutableStateOf(false) }
    var hasScrolledChannelOnce by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDayIndex, days.size, dayListState.layoutInfo.viewportSize.width) {
        scrollItemIntoViewport(dayListState, selectedDayIndex, animate = hasScrolledDayOnce)
        hasScrolledDayOnce = true
    }
    LaunchedEffect(useTimelineGrid, selectedChannelIndex, channels.size, channelListState.layoutInfo.viewportSize.width) {
        if (!useTimelineGrid && selectedChannelIndex >= 0) {
            scrollItemIntoViewport(channelListState, selectedChannelIndex, animate = hasScrolledChannelOnce)
            hasScrolledChannelOnce = true
        }
    }
    LaunchedEffect(useTimelineGrid, initialSelectedChannelId, channels.size) {
        if (useTimelineGrid && initialSelectedChannelId != null) {
            val idx = channels.indexOfFirst { it.id == initialSelectedChannelId }
            if (idx >= 0) {
                timelineListState.scrollToItem(idx)
            }
        }
    }

    val trimmedQuery = searchQuery.trim()
    val searchResults by produceState<List<Pair<EpgEvent, Channel>>>(
        initialValue = emptyList(),
        trimmedQuery,
        epgByChannel,
        channels,
        nowEpochSec / 60L,
    ) {
        value = if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                searchEpgEvents(
                    query = trimmedQuery,
                    channels = channels,
                    epgByChannel = epgByChannel,
                    nowEpochSec = nowEpochSec,
                )
            }
        }
    }
    val dayAgendaItems = remember(
        useTimelineGrid,
        trimmedQuery.isEmpty(),
        effectiveChannelId,
        activeDay,
        epgByChannel,
        channelById,
        activeZone,
    ) {
        if (useTimelineGrid || trimmedQuery.isNotEmpty()) {
            emptyList()
        } else {
            val ch = effectiveChannelId?.let { channelById[it] }
            val events = effectiveChannelId?.let { epgByChannel[it] }.orEmpty()
            filterEpgEventsForDay(events, activeDay, activeZone).map { it to ch }
        }
    }
    val agendaItems: List<Pair<EpgEvent, Channel?>> = if (trimmedQuery.isNotEmpty()) {
        searchResults
    } else {
        dayAgendaItems
    }

    var lastAgendaAnchorKey by remember { mutableStateOf<Triple<LocalDate, Long?, Boolean>?>(null) }
    var hasAnchoredAgenda by remember { mutableStateOf(false) }
    LaunchedEffect(useTimelineGrid, activeDay, effectiveChannelId, trimmedQuery.isEmpty(), agendaItems.size) {
        if (useTimelineGrid && trimmedQuery.isEmpty()) return@LaunchedEffect
        val anchorKey = Triple(activeDay, effectiveChannelId, trimmedQuery.isEmpty())
        if (anchorKey != lastAgendaAnchorKey) {
            lastAgendaAnchorKey = anchorKey
            hasAnchoredAgenda = false
        }
        if (!hasAnchoredAgenda && agendaItems.isNotEmpty()) {
            val targetIdx = if (trimmedQuery.isEmpty() && activeDay == todayDate) {
                agendaItems.indexOfFirst { it.first.stopEpochSec > nowEpochSec }.coerceAtLeast(0)
            } else {
                0
            }
            agendaListState.scrollToItem(targetIdx)
            hasAnchoredAgenda = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideTimeline = maxWidth >= 760.dp
        val timelineWindowSec = if (isWideTimeline) 3L * 3600L else 2L * 3600L
        val timelineStepSec = if (isWideTimeline) 2L * 3600L else 1L * 3600L
        val timelineStepLabel = if (isWideTimeline) "2h" else "1h"

        val eventBounds = remember(useTimelineGrid, epgByChannel, nowEpochSec / 3600L) {
            if (useTimelineGrid) computeGuideEventBounds(epgByChannel, nowEpochSec, activeZone) else null
        }
        val minWindowStartSec = remember(eventBounds, todayStartHalfHour) {
            val earliest = eventBounds?.first ?: (todayStartHalfHour - 3600L)
            minOf(floorToHalfHour(earliest), todayStartHalfHour - 3600L)
        }
        val maxWindowStartSec = remember(eventBounds, todayStartHalfHour, timelineWindowSec) {
            val latest = eventBounds?.second ?: (todayStartHalfHour + 24L * 3600L)
            maxOf(todayStartHalfHour, floorToHalfHour(latest - timelineWindowSec))
        }

        val updateWindowStart: (Long) -> Unit = { rawTarget ->
            val clamped = rawTarget.coerceIn(minWindowStartSec, maxWindowStartSec)
            windowStartEpochSec = clamped
            selectedDay = Instant.ofEpochSecond(clamped).atZone(activeZone).toLocalDate()
        }

        LaunchedEffect(useTimelineGrid) {
            if (useTimelineGrid && windowDate != selectedDay) {
                val target = computeDayJumpWindowStart(
                    dayDate = selectedDay,
                    todayDate = todayDate,
                    todayStartHalfHour = todayStartHalfHour,
                    epgByChannel = epgByChannel,
                    windowDurationSec = timelineWindowSec,
                    zoneId = activeZone,
                )
                windowStartEpochSec = target.coerceIn(minWindowStartSec, maxWindowStartSec)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isSearchExpanded) {
                val searchTextStyle = TextStyle(
                    color = MuxGridColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                // Full-width expanded search bar matching MobileTopBarHeight to prevent layout jumps
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MobileTopBarHeight)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(MuxGridShapes.Sm)
                            .background(MuxGridColors.CardSurface)
                            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Sm)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(MuxGridShapes.Xs)
                                .clickable {
                                    searchState.clearText()
                                    isSearchExpanded = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MuxGridIcons.ArrowBack,
                                contentDescription = "Close search",
                                tint = MuxGridColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            state = searchState,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            textStyle = searchTextStyle,
                            cursorBrush = SolidColor(MuxGridColors.AccentSoft),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            decorator = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search programs…",
                                            style = searchTextStyle.copy(color = MuxGridColors.TextMuted),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        if (searchQuery.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(MuxGridShapes.Xs)
                                    .clickable { searchState.clearText() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MuxGridIcons.Close,
                                    contentDescription = "Clear search",
                                    tint = MuxGridColors.AccentSoft,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                MuxGridDivider()
            } else {
                // Day underline tabs + optional timeline step controls + icon-only search trigger
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MobileTopBarHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LazyRow(
                        state = dayListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        itemsIndexed(
                            items = days,
                            key = { _, day -> day.toEpochDay() },
                        ) { idx, day ->
                            BroadcastUnderlineTab(
                                label = formatDayLabel(day, nowEpochSec),
                                selected = idx == selectedDayIndex,
                                onClick = {
                                    if (useTimelineGrid) {
                                        val target = computeDayJumpWindowStart(
                                            dayDate = day,
                                            todayDate = todayDate,
                                            todayStartHalfHour = todayStartHalfHour,
                                            epgByChannel = epgByChannel,
                                            windowDurationSec = timelineWindowSec,
                                            zoneId = activeZone,
                                        )
                                        updateWindowStart(target)
                                    } else {
                                        selectedDay = day
                                    }
                                },
                                modifier = Modifier.fillMaxHeight(),
                            )
                        }
                    }
                    if (useTimelineGrid) {
                        val canStepBack = windowStartEpochSec > minWindowStartSec
                        val canStepForward = windowStartEpochSec < maxWindowStartSec
                        val isAtNow = windowStartEpochSec == todayStartHalfHour
                        Row(
                            modifier = Modifier.padding(end = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TimelineStepChip(
                                label = "-$timelineStepLabel",
                                enabled = canStepBack,
                                onClick = { updateWindowStart(windowStartEpochSec - timelineStepSec) },
                            )
                            TimelineStepChip(
                                label = "Now",
                                highlighted = !isAtNow,
                                onClick = { updateWindowStart(todayStartHalfHour) },
                            )
                            TimelineStepChip(
                                label = "+$timelineStepLabel",
                                enabled = canStepForward,
                                onClick = { updateWindowStart(windowStartEpochSec + timelineStepSec) },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(36.dp)
                            .clip(MuxGridShapes.Sm)
                            .clickable { isSearchExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MuxGridIcons.Search,
                            contentDescription = "Search programs",
                            tint = MuxGridColors.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                MuxGridDivider()
            }

            if (useTimelineGrid && trimmedQuery.isEmpty()) {
                MobileTimelineGrid(
                    channels = channels,
                    epgByChannel = epgByChannel,
                    dvrIndex = dvrIndex,
                    nowEpochSec = nowEpochSec,
                    windowStartEpochSec = windowStartEpochSec,
                    windowDurationSec = timelineWindowSec,
                    stepSec = timelineStepSec,
                    minWindowStartSec = minWindowStartSec,
                    maxWindowStartSec = maxWindowStartSec,
                    channelColWidth = if (isWideTimeline) 156.dp else 124.dp,
                    bottomContentPadding = bottomContentPadding,
                    listState = timelineListState,
                    imageLoader = imageLoader,
                    onWindowStartChange = updateWindowStart,
                    onWatchChannel = onWatchChannel,
                    onSelectEvent = { ev ->
                        actionError = null
                        selectedEventKey = ev.channelId to ev.id
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                if (trimmedQuery.isEmpty()) {
                    // Compact borderless channel strip (portrait agenda mode)
                    LazyRow(
                        state = channelListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = channels,
                            key = { it.id },
                        ) { ch ->
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
                        state = agendaListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 2.dp, bottom = bottomContentPadding),
                    ) {
                        items(agendaItems, key = { it.first.id }) { (event, channel) ->
                            val linkedDvr = dvrIndex.findMatch(event)
                            val isLiveNow = nowEpochSec in event.startEpochSec until event.stopEpochSec
                            val isPast = event.stopEpochSec <= nowEpochSec
                            val isRecording = linkedDvr?.state == DvrState.RECORDING

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .background(if (isLiveNow) MuxGridColors.CardSurface else Color.Transparent)
                                        .clickable {
                                            actionError = null
                                            selectedEventKey = event.channelId to event.id
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
                                                    color = if (isPast) MuxGridColors.TextSecondary else MuxGridColors.TextPrimary,
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
                                                    text = "${channel.name} · ${formatEpochDayHm(event.startEpochSec, nowEpochSec)}",
                                                    style = MuxGridType.TabularSmall,
                                                    color = if (isPast) MuxGridColors.TextMuted else MuxGridColors.AccentSoft,
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
        }
    }

    val detailEvent = selectedEventKey?.let { (chId, evId) ->
        epgByChannel[chId]?.firstOrNull { it.id == evId }
    }
    if (detailEvent != null) {
        val detailChannel = channelById[detailEvent.channelId]
        val linkedDvr = dvrIndex.findMatch(detailEvent)
        val isLiveNow = nowEpochSec in detailEvent.startEpochSec until detailEvent.stopEpochSec
        val isPast = detailEvent.stopEpochSec <= nowEpochSec

        MuxGridModalSheet(
            onDismissRequest = { if (!actionBusy) selectedEventKey = null },
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
                            append("${formatEpochDayHm(detailEvent.startEpochSec, nowEpochSec)}–${formatEpochHm(detailEvent.stopEpochSec)}")
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
                                enabled = !actionBusy,
                                onClick = {
                                    selectedEventKey = null
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
                                    selectedEventKey = null
                                    onPlayRecording(linkedDvr, detailChannel, linkedDvr.effectiveStartSec)
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
                                enabled = !actionBusy,
                                onClick = {
                                    selectedEventKey = null
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
                            DvrState.COMPLETED -> if (!linkedDvr.isFileMissing) {
                                OutlinedButton(
                                    enabled = !actionBusy,
                                    shape = MuxGridShapes.Sm,
                                    onClick = {
                                        selectedEventKey = null
                                        onPlayRecording(linkedDvr, detailChannel, linkedDvr.effectiveStartSec)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(MuxGridIcons.Play, contentDescription = null, tint = MuxGridColors.AccentSoft, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (linkedDvr.hasResumePosition) "Resume Recording" else "Play Recording",
                                        color = MuxGridColors.AccentSoft,
                                        maxLines = 1,
                                    )
                                }
                            }
                            else -> if (!isPast) {
                                OutlinedButton(
                                    enabled = !actionBusy,
                                    shape = MuxGridShapes.Sm,
                                    onClick = {
                                        scope.launch {
                                            actionBusy = true
                                            actionError = null
                                            try {
                                                onScheduleRecording(detailEvent.id)
                                                    .onSuccess { selectedEventKey = null }
                                                    .onFailure { actionError = it.message ?: "Failed to schedule recording" }
                                            } finally {
                                                actionBusy = false
                                            }
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
                    try {
                        val result = when (kind) {
                            DvrConfirmKind.STOP_AND_SAVE -> onStopRecording(entry.id)
                            DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(entry.id)
                            else -> Result.success(Unit)
                        }
                        result
                            .onSuccess { selectedEventKey = null }
                            .onFailure {
                                actionError = it.message ?: spec.failureFallbackMessage
                            }
                    } finally {
                        actionBusy = false
                    }
                }
            },
        )
    }
}

@Composable
private fun TimelineStepChip(
    label: String,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        highlighted -> MuxGridColors.Accent
        else -> MuxGridColors.CardSurface
    }
    val fg = when {
        highlighted -> MuxGridColors.OnAccent
        else -> MuxGridColors.TextSecondary
    }
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(MuxGridShapes.Sm)
            .background(bg)
            .border(
                width = MuxGridBorders.Divider,
                color = if (highlighted) MuxGridColors.Accent else MuxGridColors.HairlineBorder,
                shape = MuxGridShapes.Sm,
            )
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun MobileTimelineGrid(
    channels: List<Channel>,
    epgByChannel: Map<Long, List<EpgEvent>>,
    dvrIndex: DvrLookupIndex,
    nowEpochSec: Long,
    windowStartEpochSec: Long,
    windowDurationSec: Long,
    stepSec: Long,
    minWindowStartSec: Long,
    maxWindowStartSec: Long,
    channelColWidth: Dp,
    bottomContentPadding: Dp,
    listState: LazyListState,
    imageLoader: ImageLoader,
    onWindowStartChange: (Long) -> Unit,
    onWatchChannel: (Channel) -> Unit,
    onSelectEvent: (EpgEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = "No channels synced yet", style = MuxGridType.CardSubtitle)
        }
        return
    }

    val windowEndEpochSec = windowStartEpochSec + windowDurationSec
    val slotCount = (windowDurationSec / HALF_HOUR_SEC).toInt().coerceAtLeast(2)
    val slotLabels = remember(windowStartEpochSec, slotCount) {
        List(slotCount) { idx -> formatEpochHm(windowStartEpochSec + idx * HALF_HOUR_SEC) }
    }
    val nowFraction = remember(nowEpochSec, windowStartEpochSec, windowDurationSec) {
        if (nowEpochSec in windowStartEpochSec..windowEndEpochSec) {
            (nowEpochSec - windowStartEpochSec).toFloat() / windowDurationSec.toFloat()
        } else {
            null
        }
    }

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val currentWindowStart by rememberUpdatedState(windowStartEpochSec)
    val currentStepSec by rememberUpdatedState(stepSec)
    val currentMinStart by rememberUpdatedState(minWindowStartSec)
    val currentMaxStart by rememberUpdatedState(maxWindowStartSec)
    val currentOnWindowStartChange by rememberUpdatedState(onWindowStartChange)

    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .pointerInput(Unit) {
                var totalDragPx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDragPx = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDragPx += dragAmount },
                    onDragEnd = {
                        when {
                            totalDragPx < -swipeThresholdPx && currentWindowStart < currentMaxStart ->
                                currentOnWindowStartChange(currentWindowStart + currentStepSec)
                            totalDragPx > swipeThresholdPx && currentWindowStart > currentMinStart ->
                                currentOnWindowStartChange(currentWindowStart - currentStepSec)
                        }
                    },
                )
            },
    ) {
        val trackWidth = (maxWidth - channelColWidth).coerceAtLeast(220.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky time ruler header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
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
                        slotLabels.forEach { slotLabel ->
                            Text(
                                text = slotLabel,
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(
                    items = channels,
                    key = { _, ch -> ch.id },
                    contentType = { _, _ -> "mobile_guide_timeline_row" },
                ) { rowIndex, channel ->
                    val rowCells = remember(
                        channel.id,
                        epgByChannel[channel.id],
                        windowStartEpochSec,
                        windowEndEpochSec,
                    ) {
                        buildRowTimelineCells(
                            channelId = channel.id,
                            events = epgByChannel[channel.id].orEmpty(),
                            windowStart = windowStartEpochSec,
                            windowEnd = windowEndEpochSec,
                        )
                    }
                    val isEvenRow = rowIndex % 2 == 0
                    val baseCellBg = if (isEvenRow) MuxGridColors.CardSurface else MuxGridColors.CardSurfaceAlt
                    val piconModel = remember(channel.id, channel.icon) {
                        channel.icon?.let { PiconModel(channel.id, it) }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Sticky channel column — tap tunes directly
                        Row(
                            modifier = Modifier
                                .width(channelColWidth)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                                .clip(MuxGridShapes.Sm)
                                .background(baseCellBg)
                                .clickable { onWatchChannel(channel) }
                                .padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            ChannelPiconBadge(
                                channelNumber = channel.formattedNumber,
                                channelName = channel.name,
                                piconModel = piconModel,
                                imageLoader = imageLoader,
                                size = 28.dp,
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
                                    color = MuxGridColors.TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                                                .padding(horizontal = 8.dp),
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
                                        val isPast = ev.stopEpochSec <= nowEpochSec
                                        val linkedDvr = dvrIndex.findMatch(ev)
                                        val timeRangeLabel = remember(ev.id, ev.startEpochSec, ev.stopEpochSec) {
                                            "${formatEpochHm(ev.startEpochSec)}–${formatEpochHm(ev.stopEpochSec)}"
                                        }
                                        val cellBg = if (isLiveNow) MuxGridColors.CardSurfaceElevated else baseCellBg
                                        Column(
                                            modifier = Modifier
                                                .weight(cell.weight)
                                                .fillMaxHeight()
                                                .clip(MuxGridShapes.Xs)
                                                .background(cellBg)
                                                .then(
                                                    if (linkedDvr?.state == DvrState.RECORDING) {
                                                        Modifier.border(
                                                            MuxGridBorders.Hairline,
                                                            MuxGridColors.CoralLive.copy(alpha = 0.65f),
                                                            MuxGridShapes.Xs,
                                                        )
                                                    } else {
                                                        Modifier
                                                    },
                                                )
                                                .clickable { onSelectEvent(ev) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = ev.title,
                                                    color = if (isPast) MuxGridColors.TextSecondary else MuxGridColors.TextPrimary,
                                                    fontSize = 12.sp,
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
                                                text = timeRangeLabel,
                                                style = MuxGridType.TabularSmall,
                                                color = MuxGridColors.TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
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
