package io.github.corebonts.muxgrid.feature.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrLookupIndex
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.HALF_HOUR_SEC
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.TimelineRowTrack
import io.github.corebonts.muxgrid.core.designsystem.TimelineRuler
import io.github.corebonts.muxgrid.core.designsystem.buildAvailableGuideDays
import io.github.corebonts.muxgrid.core.designsystem.buildContinuousTimelineCells
import io.github.corebonts.muxgrid.core.designsystem.computeDayJumpWindowStart
import io.github.corebonts.muxgrid.core.designsystem.computeGuideEventBounds
import io.github.corebonts.muxgrid.core.designsystem.findAdjacentEpgEvent
import io.github.corebonts.muxgrid.core.designsystem.floorToHalfHour
import io.github.corebonts.muxgrid.core.designsystem.formatDayLabel
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatEpochDayHm
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.rememberTimelineScrollState
import io.github.corebonts.muxgrid.core.designsystem.scrollItemIntoViewport
import io.github.corebonts.muxgrid.core.designsystem.searchEpgEvents
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

private val TV_TIMELINE_DP_PER_HOUR = 260.dp

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvGuideScreen(
    channels: List<Channel>,
    connectionState: ConnectionState = ConnectionState.Disconnected,
    epgByChannel: Map<Long, List<EpgEvent>>,
    dvrEntries: Map<Long, DvrEntry>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    listState: LazyListState = rememberLazyListState(),
    windowStartEpochSec: Long = floorToHalfHour(nowEpochSec),
    onWindowStartChange: (Long) -> Unit = {},
    focusMemory: TvFocusMemory = remember { TvFocusMemory() },
    focusRequestNonce: Int = 0,
    searchOpen: Boolean = false,
    onCloseSearch: () -> Unit = {},
    onWatchChannel: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel?, Long) -> Unit,
    onScheduleRecording: suspend (Long) -> Result<Long>,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val activeZone = remember { ZoneId.systemDefault() }
    val todayDate = remember(nowEpochSec / 3600L) {
        Instant.ofEpochSecond(nowEpochSec).atZone(activeZone).toLocalDate()
    }
    val days = remember(epgByChannel, nowEpochSec / 3600L) {
        buildAvailableGuideDays(epgByChannel, nowEpochSec, activeZone)
    }
    val eventBounds = remember(epgByChannel, nowEpochSec / 3600L) {
        computeGuideEventBounds(epgByChannel, nowEpochSec, activeZone)
    }
    val todayStartHalfHour = floorToHalfHour(nowEpochSec)
    val timelineOriginSec = remember(eventBounds, todayStartHalfHour) {
        val earliest = eventBounds?.first ?: (todayStartHalfHour - 3600L)
        minOf(floorToHalfHour(earliest), todayStartHalfHour - 3600L)
    }
    val timelineEndSec = remember(eventBounds, todayStartHalfHour) {
        val latest = eventBounds?.second ?: (todayStartHalfHour + 24L * 3600L)
        maxOf(todayStartHalfHour + 6L * 3600L, floorToHalfHour(latest) + HALF_HOUR_SEC)
    }

    val timelineScrollState = rememberTimelineScrollState(initialEpochSec = windowStartEpochSec)
    val bucketedStartSec by remember(timelineScrollState) {
        derivedStateOf { floorToHalfHour(timelineScrollState.visibleStartEpochSec) }
    }
    LaunchedEffect(bucketedStartSec) {
        if (bucketedStartSec != windowStartEpochSec) {
            onWindowStartChange(bucketedStartSec)
        }
    }

    val windowDate = remember(bucketedStartSec) {
        Instant.ofEpochSecond(bucketedStartSec).atZone(activeZone).toLocalDate()
    }
    val selectedDayIndex = remember(days, windowDate) {
        val exact = days.indexOf(windowDate)
        if (exact >= 0) {
            exact
        } else {
            days.indexOfLast { !it.isAfter(windowDate) }.coerceAtLeast(0)
        }
    }
    val dayListState = rememberLazyListState()
    var dayStripHasFocus by remember { mutableStateOf(false) }
    var hasInitializedDayScroll by remember { mutableStateOf(false) }
    var lastScrolledDayIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(selectedDayIndex, days.size, dayStripHasFocus, dayListState.layoutInfo.viewportSize.width) {
        val dayChanged = selectedDayIndex != lastScrolledDayIndex
        if (!hasInitializedDayScroll || !dayStripHasFocus || dayChanged) {
            scrollItemIntoViewport(
                listState = dayListState,
                index = selectedDayIndex,
                animate = hasInitializedDayScroll,
            )
            hasInitializedDayScroll = true
            lastScrolledDayIndex = selectedDayIndex
        }
    }

    val dvrIndex = remember(dvrEntries) { DvrLookupIndex(dvrEntries) }

    val searchState = rememberTextFieldState()
    val searchFocusRequester = remember { FocusRequester() }
    val nowButtonFocusRequester = remember { FocusRequester() }
    val cellFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var pendingFocusTarget by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var localFocusRestoreNonce by remember { mutableIntStateOf(0) }
    var wasSearchOpen by remember { mutableStateOf(searchOpen) }

    LaunchedEffect(pendingFocusTarget, bucketedStartSec) {
        val (chId, evId) = pendingFocusTarget ?: return@LaunchedEffect
        val key = "ev_${chId}_$evId"
        repeat(3) {
            withFrameNanos { }
            val requester = cellFocusRequesters[key]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                pendingFocusTarget = null
                return@LaunchedEffect
            }
        }
    }

    BackHandler(enabled = searchOpen) {
        searchState.clearText()
        onCloseSearch()
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            runCatching { searchFocusRequester.requestFocus() }
        } else {
            searchState.clearText()
            if (wasSearchOpen) {
                localFocusRestoreNonce++
            }
        }
        wasSearchOpen = searchOpen
    }

    // Isolated state: written on D-pad cell focus, read ONLY inside TvGuidePreviewBar so timeline rows never recompose
    val focusedEventState = remember { mutableStateOf<EpgEvent?>(null) }
    var actionEventKey by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var wasActionDialogOpen by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<Pair<DvrConfirmKind, DvrEntry>?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val channelById = remember(channels) { channels.associateBy { it.id } }

    LaunchedEffect(actionEventKey) {
        val isOpen = actionEventKey != null
        if (!isOpen && wasActionDialogOpen && !searchOpen) {
            localFocusRestoreNonce++
        }
        wasActionDialogOpen = isOpen
    }

    LaunchedEffect(focusRequestNonce, localFocusRestoreNonce) {
        if (focusRequestNonce == 0 && localFocusRestoreNonce == 0) return@LaunchedEffect
        if (searchOpen) {
            runCatching { searchFocusRequester.requestFocus() }
            return@LaunchedEffect
        }
        if (channels.isEmpty()) {
            runCatching { nowButtonFocusRequester.requestFocus() }
            return@LaunchedEffect
        }
        val lastFocusTarget = focusMemory.guideTarget
        val targetRowIndex = lastFocusTarget?.channelId
            ?.let { id -> channels.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val targetChannel = channels.getOrNull(targetRowIndex) ?: return@LaunchedEffect
        val isRowVisible = listState.layoutInfo.visibleItemsInfo.any { it.key == targetChannel.id }
        if (!isRowVisible) {
            runCatching { listState.scrollToItem(targetRowIndex) }
        }
        val targetEventId = lastFocusTarget?.takeIf { it.channelId == targetChannel.id }?.eventId
        val visibleStart = timelineScrollState.visibleStartEpochSec
        val visibleEnd = visibleStart + timelineScrollState.visibleDurationSec
        val rowFirstEventId = epgByChannel[targetChannel.id]
            ?.firstOrNull { it.stopEpochSec > visibleStart && it.startEpochSec < visibleEnd }
            ?.id
        val candidateKeys = buildList {
            if (targetEventId != null) add("ev_${targetChannel.id}_$targetEventId")
            if (rowFirstEventId != null) add("ev_${targetChannel.id}_$rowFirstEventId")
            add("ch_${targetChannel.id}")
        }
        repeat(2) {
            withFrameNanos { }
            for (key in candidateKeys) {
                val requester = cellFocusRequesters[key]
                if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                    return@LaunchedEffect
                }
            }
        }
        val fallbackChannelId = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Long
        val fallbackRequester = fallbackChannelId?.let { cellFocusRequesters["ch_$it"] }
        if (fallbackRequester != null) {
            runCatching { fallbackRequester.requestFocus() }
        } else {
            runCatching { nowButtonFocusRequester.requestFocus() }
        }
    }
    val searchQuery = searchState.text.toString()
    val trimmedQuery = searchQuery.trim()
    val searchResults by produceState<List<Pair<EpgEvent, Channel>>>(
        initialValue = emptyList(),
        searchOpen,
        trimmedQuery,
        epgByChannel,
        channels,
        nowEpochSec / 60L,
    ) {
        value = if (!searchOpen || trimmedQuery.isEmpty()) {
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

    val fallbackPreviewEvent = remember(searchOpen, trimmedQuery, searchResults, channels, epgByChannel, bucketedStartSec) {
        if (searchOpen && trimmedQuery.isNotEmpty()) {
            searchResults.firstOrNull()?.first
        } else {
            val endSec = bucketedStartSec + timelineScrollState.visibleDurationSec
            channels.firstNotNullOfOrNull { ch ->
                epgByChannel[ch.id]?.firstOrNull { it.stopEpochSec > bucketedStartSec && it.startEpochSec < endSec }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (searchOpen) {
            val searchTextStyle = TextStyle(
                color = MuxGridColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(MuxGridShapes.Sm)
                        .background(MuxGridColors.CardSurface)
                        .border(
                            width = if (searchFieldFocused) MuxGridBorders.Focused else MuxGridBorders.Divider,
                            color = if (searchFieldFocused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
                            shape = MuxGridShapes.Sm,
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = MuxGridIcons.Search,
                        contentDescription = null,
                        tint = if (searchFieldFocused) MuxGridColors.AccentSoft else MuxGridColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    BasicTextField(
                        state = searchState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = searchTextStyle,
                        cursorBrush = SolidColor(MuxGridColors.AccentSoft),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { searchFieldFocused = it.isFocused },
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
                }
                if (searchQuery.isNotEmpty()) {
                    Button(
                        onClick = { searchState.clearText() },
                        shape = tvButtonShape(),
                        colors = tvSecondaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Now button (pinned) + Day jump bar (auto-scrolled to keep selected in viewport)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        scope.launch { timelineScrollState.animateScrollToEpoch(todayStartHalfHour) }
                    },
                    modifier = Modifier.focusRequester(nowButtonFocusRequester),
                    shape = tvButtonShape(),
                    colors = tvPrimaryButtonColors(),
                ) {
                    Icon(MuxGridIcons.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(6.dp))
                LazyRow(
                    state = dayListState,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { dayStripHasFocus = it.hasFocus }
                        .focusGroup()
                        .focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(
                        items = days,
                        key = { _, dayDate -> dayDate.toEpochDay() },
                    ) { idx, dayDate ->
                        val selected = idx == selectedDayIndex
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val targetWindow = computeDayJumpWindowStart(
                                    dayDate = dayDate,
                                    todayDate = todayDate,
                                    todayStartHalfHour = todayStartHalfHour,
                                    epgByChannel = epgByChannel,
                                    windowDurationSec = timelineScrollState.visibleDurationSec,
                                    zoneId = activeZone,
                                )
                                scope.launch { timelineScrollState.animateScrollToEpoch(targetWindow) }
                            },
                            shape = FilterChipDefaults.shape(shape = MuxGridShapes.Sm),
                            colors = FilterChipDefaults.colors(
                                containerColor = MuxGridColors.CardSurface,
                                contentColor = MuxGridColors.TextSecondary,
                                selectedContainerColor = MuxGridColors.Accent,
                                selectedContentColor = MuxGridColors.OnAccent,
                                focusedContainerColor = MuxGridColors.FocusBorder,
                                focusedContentColor = MuxGridColors.OnFocusBorder,
                                focusedSelectedContainerColor = MuxGridColors.FocusBorder,
                                focusedSelectedContentColor = MuxGridColors.OnFocusBorder,
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
            }
        }

        if (searchOpen && trimmedQuery.isNotEmpty()) {
            if (searchResults.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No programs match \"$trimmedQuery\"", style = MuxGridType.CardSubtitle, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = searchResults,
                        key = { it.first.id },
                        contentType = { "guide_search_row" },
                    ) { (ev, ch) ->
                        val isLiveNow = nowEpochSec in ev.startEpochSec until ev.stopEpochSec
                        val linkedDvr = dvrIndex.findMatch(ev)
                        var rowFocused by remember(ev.id) { mutableStateOf(false) }
                        val rowBg = when {
                            rowFocused -> MuxGridColors.Accent.copy(alpha = 0.22f)
                            isLiveNow -> MuxGridColors.CardSurfaceElevated
                            else -> MuxGridColors.CardSurface
                        }
                        val rowDescription = remember(ev.id, ch.name, ev.title) {
                            "${ev.title}, ${ch.name}"
                        }
                        // Lightweight focusable row avoids tv.material3.Surface's SurfaceGlowNode and scale animations
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MuxGridShapes.Sm)
                                .background(rowBg)
                                .then(
                                    if (rowFocused) {
                                        Modifier.border(
                                            MuxGridBorders.Focused,
                                            MuxGridColors.FocusBorder,
                                            MuxGridShapes.Sm,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .onFocusChanged { state ->
                                    val focused = state.isFocused
                                    if (rowFocused != focused) rowFocused = focused
                                    if (focused) focusedEventState.value = ev
                                }
                                .clickable(
                                    interactionSource = remember(ev.id) { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        actionError = null
                                        actionEventKey = ev.channelId to ev.id
                                    },
                                )
                                .clearAndSetSemantics {
                                    role = Role.Button
                                    contentDescription = rowDescription
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ChannelPiconBadge(
                                channelNumber = ch.formattedNumber,
                                channelName = ch.name,
                                piconModel = ch.icon?.let { PiconModel(ch.id, it) },
                                imageLoader = imageLoader,
                                size = 34.dp,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = ev.title,
                                    style = MuxGridType.CardTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${ch.name} · ${formatEpochDayHm(ev.startEpochSec, nowEpochSec)}–${formatEpochHm(ev.stopEpochSec)} (${formatDurationMinutes(ev.startEpochSec, ev.stopEpochSec)})",
                                    style = MuxGridType.TabularSmall,
                                    color = MuxGridColors.AccentSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (linkedDvr != null) {
                                DvrStateBadge(state = linkedDvr.state, isWatched = linkedDvr.isWatched)
                            } else if (isLiveNow) {
                                Text(
                                    text = "ON NOW",
                                    color = MuxGridColors.CoralLive,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        } else if (channels.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val emptyText = when (connectionState) {
                    is ConnectionState.Connecting, is ConnectionState.Syncing -> "Syncing channels from Tvheadend…"
                    is ConnectionState.Disconnected -> "Connecting to Tvheadend…"
                    is ConnectionState.Error -> connectionState.message
                    is ConnectionState.Connected -> "No channels synced yet"
                }
                Text(emptyText, style = MuxGridType.CardSubtitle, fontSize = 16.sp)
            }
        } else {
            // Sticky time ruler + continuous broadcast horizontal timeline rows with red "Now" hairline
            val pxPerSec = remember(density) { with(density) { TV_TIMELINE_DP_PER_HOUR.toPx() / 3600f } }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .scrollable(
                        state = timelineScrollState.scrollableState,
                        orientation = Orientation.Horizontal,
                        reverseDirection = true,
                    ),
            ) {
                val channelColWidth = 172.dp
                val trackWidth = (maxWidth - channelColWidth).coerceAtLeast(320.dp)
                val trackWidthPx = with(density) { trackWidth.toPx() }
                SideEffect {
                    timelineScrollState.updateBounds(
                        newOriginSec = timelineOriginSec,
                        newEndSec = timelineEndSec,
                        newViewportWidthPx = trackWidthPx,
                        newPxPerSec = pxPerSec,
                    )
                }

                val visibleDurationSec = timelineScrollState.visibleDurationSec
                val bufferStartSec = (bucketedStartSec - 3600L).coerceAtLeast(timelineOriginSec)
                val bufferEndSec = (bucketedStartSec + visibleDurationSec + 3600L).coerceAtMost(timelineEndSec)

                Column(modifier = Modifier.fillMaxSize()) {
                    // Synchronized time ruler header
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
                        TimelineRuler(
                            scrollState = timelineScrollState,
                            bucketStartEpochSec = bufferStartSec,
                            bucketEndEpochSec = bufferEndSec,
                            nowEpochSec = nowEpochSec,
                            zoneId = activeZone,
                            modifier = Modifier.width(trackWidth),
                        )
                    }

                    // Channel rows
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        itemsIndexed(
                            items = channels,
                            key = { _, ch -> ch.id },
                            contentType = { _, _ -> "guide_row" },
                        ) { rowIndex, channel ->
                            val channelEvents = epgByChannel[channel.id].orEmpty()
                            val rowCells = remember(
                                channel.id,
                                channelEvents,
                                bufferStartSec,
                                bufferEndSec,
                            ) {
                                buildContinuousTimelineCells(
                                    channelId = channel.id,
                                    events = channelEvents,
                                    rangeStart = bufferStartSec,
                                    rangeEnd = bufferEndSec,
                                )
                            }
                            val isEvenRow = rowIndex % 2 == 0
                            val baseCellBg = if (isEvenRow) MuxGridColors.CardSurface else MuxGridColors.CardSurfaceAlt
                            val chFocusKey = "ch_${channel.id}"
                            val chFocusRequester = remember(chFocusKey) { FocusRequester() }
                            val piconModel = remember(channel.id, channel.icon) {
                                channel.icon?.let { PiconModel(channel.id, it) }
                            }
                            DisposableEffect(chFocusKey) {
                                cellFocusRequesters[chFocusKey] = chFocusRequester
                                onDispose {
                                    if (cellFocusRequesters[chFocusKey] === chFocusRequester) {
                                        cellFocusRequesters.remove(chFocusKey)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Sticky channel column (lightweight focusable container avoids SurfaceGlowNode overhead)
                                var chFocused by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .width(channelColWidth)
                                        .fillMaxHeight()
                                        .padding(end = 4.dp)
                                        .clip(MuxGridShapes.Sm)
                                        .background(if (chFocused) MuxGridColors.CardSurfaceElevated else baseCellBg)
                                        .then(
                                            if (chFocused) {
                                                Modifier.border(MuxGridBorders.Focused, MuxGridColors.FocusBorder, MuxGridShapes.Sm)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .focusRequester(chFocusRequester)
                                        .onFocusChanged { state ->
                                            val focused = state.isFocused
                                            if (chFocused != focused) chFocused = focused
                                            if (focused) {
                                                focusMemory.guideTarget = TvGuideFocusTarget(channelId = channel.id)
                                            }
                                        }
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                focusMemory.guideTarget = TvGuideFocusTarget(channelId = channel.id)
                                                onWatchChannel(channel)
                                            },
                                        )
                                        .clearAndSetSemantics {
                                            role = Role.Button
                                            contentDescription = "${channel.formattedNumber} ${channel.name}"
                                        }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ChannelPiconBadge(
                                        channelNumber = channel.formattedNumber,
                                        channelName = channel.name,
                                        piconModel = piconModel,
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
                                            color = MuxGridColors.TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                // Continuous horizontal timeline track for this channel
                                TimelineRowTrack(
                                    cells = rowCells,
                                    scrollState = timelineScrollState,
                                    nowEpochSec = nowEpochSec,
                                    modifier = Modifier
                                        .width(trackWidth)
                                        .fillMaxHeight(),
                                ) { cell ->
                                    val ev = cell.event
                                    if (ev == null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
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
                                        val isPast = ev.stopEpochSec <= nowEpochSec
                                        val linkedDvr = dvrIndex.findMatch(ev)
                                        val evFocusKey = "ev_${channel.id}_${ev.id}"
                                        val evFocusRequester = remember(evFocusKey) { FocusRequester() }
                                        val timeRangeLabel = remember(ev.id, ev.startEpochSec, ev.stopEpochSec) {
                                            "${formatEpochHm(ev.startEpochSec)}–${formatEpochHm(ev.stopEpochSec)}"
                                        }
                                        DisposableEffect(evFocusKey) {
                                            cellFocusRequesters[evFocusKey] = evFocusRequester
                                            onDispose {
                                                if (cellFocusRequesters[evFocusKey] === evFocusRequester) {
                                                    cellFocusRequesters.remove(evFocusKey)
                                                }
                                            }
                                        }
                                        var evFocused by remember(evFocusKey) { mutableStateOf(false) }
                                        val cellBg = when {
                                            evFocused -> MuxGridColors.Accent.copy(alpha = 0.22f)
                                            isLiveNow -> MuxGridColors.CardSurfaceElevated
                                            else -> baseCellBg
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(MuxGridShapes.Xs)
                                                .background(cellBg)
                                                .then(
                                                    when {
                                                        evFocused -> Modifier.border(
                                                            MuxGridBorders.Focused,
                                                            MuxGridColors.FocusBorder,
                                                            MuxGridShapes.Xs,
                                                        )
                                                        linkedDvr?.state == DvrState.RECORDING -> Modifier.border(
                                                            MuxGridBorders.Hairline,
                                                            MuxGridColors.CoralLive.copy(alpha = 0.65f),
                                                            MuxGridShapes.Xs,
                                                        )
                                                        else -> Modifier
                                                    },
                                                )
                                                .focusRequester(evFocusRequester)
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                    val dir = when (keyEvent.key) {
                                                        Key.DirectionRight -> 1
                                                        Key.DirectionLeft -> -1
                                                        else -> 0
                                                    }
                                                    if (dir == 0) return@onPreviewKeyEvent false
                                                    val target = findAdjacentEpgEvent(
                                                        events = channelEvents,
                                                        currentEventId = ev.id,
                                                        direction = dir,
                                                        minEpochSec = timelineOriginSec,
                                                        maxEpochSec = timelineEndSec,
                                                    ) ?: return@onPreviewKeyEvent false
                                                    pendingFocusTarget = channel.id to target.id
                                                    scope.launch {
                                                        timelineScrollState.ensureIntervalVisible(
                                                            startSec = target.startEpochSec,
                                                            stopSec = target.stopEpochSec,
                                                        )
                                                    }
                                                    true
                                                }
                                                .onFocusChanged { state ->
                                                    val focused = state.isFocused
                                                    if (evFocused != focused) evFocused = focused
                                                    if (focused) {
                                                        focusedEventState.value = ev
                                                        focusMemory.guideTarget = TvGuideFocusTarget(channel.id, ev.id)
                                                        scope.launch {
                                                            timelineScrollState.ensureIntervalVisible(
                                                                startSec = ev.startEpochSec,
                                                                stopSec = ev.stopEpochSec,
                                                            )
                                                        }
                                                    }
                                                }
                                                .clickable(
                                                    interactionSource = remember(evFocusKey) { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {
                                                        focusMemory.guideTarget = TvGuideFocusTarget(channel.id, ev.id)
                                                        if (isLiveNow && linkedDvr == null) {
                                                            onWatchChannel(channel)
                                                        } else {
                                                            actionError = null
                                                            actionEventKey = ev.channelId to ev.id
                                                        }
                                                    },
                                                )
                                                .clearAndSetSemantics {
                                                    role = Role.Button
                                                    contentDescription = "${ev.title}, $timeRangeLabel"
                                                }
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
                                                    color = if (isPast && !evFocused) MuxGridColors.TextSecondary else MuxGridColors.TextPrimary,
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
                        }
                    }
                }
            }
        }

        // Fixed-height bottom details bar isolated in its own composable so D-pad focus changes never recompose the grid
        TvGuidePreviewBar(
            focusedEventState = focusedEventState,
            fallbackEvent = fallbackPreviewEvent,
            dvrIndex = dvrIndex,
            nowEpochSec = nowEpochSec,
        )
    }

    val actionEvent = actionEventKey?.let { (chId, evId) ->
        epgByChannel[chId]?.firstOrNull { it.id == evId }
    }
    actionEvent?.let { ev ->
        val ch = channelById[ev.channelId]
        val linkedDvr = dvrIndex.findMatch(ev)
        val isLiveNow = nowEpochSec in ev.startEpochSec until ev.stopEpochSec
        val isPast = ev.stopEpochSec <= nowEpochSec
        val primaryFocusRequester = remember { FocusRequester() }

        LaunchedEffect(ev.id, pendingConfirm == null) {
            if (pendingConfirm == null) {
                runCatching { primaryFocusRequester.requestFocus() }
            }
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
                        append("${formatEpochDayHm(ev.startEpochSec, nowEpochSec)}–${formatEpochHm(ev.stopEpochSec)}")
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
                                onPlayRecording(linkedDvr, ch, linkedDvr.effectiveStartSec)
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
                    DvrState.COMPLETED -> if (!linkedDvr.isFileMissing) {
                        Button(
                            enabled = !actionBusy,
                            onClick = {
                                actionEventKey = null
                                onPlayRecording(linkedDvr, ch, linkedDvr.effectiveStartSec)
                            },
                            modifier = recordButtonModifier,
                            shape = tvButtonShape(),
                            colors = tvDialogSecondaryButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (linkedDvr.hasResumePosition) "Resume Recording" else "Play Recording",
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    else -> if (!isPast) {
                        Button(
                            enabled = !actionBusy,
                            onClick = {
                                scope.launch {
                                    actionBusy = true
                                    actionError = null
                                    try {
                                        onScheduleRecording(ev.id)
                                            .onSuccess { actionEventKey = null }
                                            .onFailure { actionError = it.message ?: "Failed to schedule" }
                                    } finally {
                                        actionBusy = false
                                    }
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
                    try {
                        val result = when (kind) {
                            DvrConfirmKind.STOP_AND_SAVE -> onStopRecording(entry.id)
                            DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(entry.id)
                            else -> Result.success(Unit)
                        }
                        result
                            .onSuccess { actionEventKey = null }
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

@Composable
private fun TvGuidePreviewBar(
    focusedEventState: State<EpgEvent?>,
    fallbackEvent: EpgEvent?,
    dvrIndex: DvrLookupIndex,
    nowEpochSec: Long,
) {
    val previewEvent = focusedEventState.value ?: fallbackEvent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Sm)
            .background(MuxGridColors.CardSurface),
    ) {
        MuxGridDivider()
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (previewEvent != null) {
                val dvr = dvrIndex.findMatch(previewEvent)
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
                            text = "${formatEpochDayHm(previewEvent.startEpochSec, nowEpochSec)}–${formatEpochHm(previewEvent.stopEpochSec)} (${formatDurationMinutes(previewEvent.startEpochSec, previewEvent.stopEpochSec)})",
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
