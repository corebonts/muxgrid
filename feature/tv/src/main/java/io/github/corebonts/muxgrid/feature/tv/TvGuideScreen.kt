package io.github.corebonts.muxgrid.feature.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import io.github.corebonts.muxgrid.core.designsystem.R as DesignR
import io.github.corebonts.muxgrid.core.designsystem.rememberLocalizedDateLabels
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrLookupIndex
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.EpgLiveProgressSection
import io.github.corebonts.muxgrid.core.designsystem.HALF_HOUR_SEC
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDetailModalLayout
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridRadii
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSyncingState
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.resolveModalSubtitleAndDescription
import io.github.corebonts.muxgrid.core.designsystem.syncStatusMessage
import io.github.corebonts.muxgrid.core.designsystem.TimelineRowTrack
import io.github.corebonts.muxgrid.core.designsystem.TimelineRuler
import io.github.corebonts.muxgrid.core.designsystem.buildAvailableGuideDays
import io.github.corebonts.muxgrid.core.designsystem.buildContinuousTimelineCells
import io.github.corebonts.muxgrid.core.designsystem.computeDayJumpWindowStart
import io.github.corebonts.muxgrid.core.designsystem.computeGuideEventBounds
import io.github.corebonts.muxgrid.core.designsystem.computeTimelineBufferWindow
import io.github.corebonts.muxgrid.core.designsystem.findAdjacentEpgEvent
import io.github.corebonts.muxgrid.core.designsystem.floorToHalfHour
import io.github.corebonts.muxgrid.core.designsystem.formatDayLabel
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMinutes
import io.github.corebonts.muxgrid.core.designsystem.formatEpochDayHm
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.formatProgramModalMeta
import io.github.corebonts.muxgrid.core.designsystem.rememberTimelineScrollState
import io.github.corebonts.muxgrid.core.designsystem.scrollItemIntoViewport
import io.github.corebonts.muxgrid.core.designsystem.searchEpgEvents
import io.github.corebonts.muxgrid.core.designsystem.timelineStickyTextOffset
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
    initialWindowStartEpochSec: Long = nowEpochSec,
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
    val timelineOriginSec = nowEpochSec
    val timelineEndSec = remember(eventBounds, todayStartHalfHour) {
        val latest = eventBounds?.second ?: (todayStartHalfHour + 24L * 3600L)
        maxOf(todayStartHalfHour + 6L * 3600L, floorToHalfHour(latest) + HALF_HOUR_SEC)
    }

    val timelineScrollState = rememberTimelineScrollState(initialEpochSec = initialWindowStartEpochSec)
    val bucketedStartSec by remember(timelineScrollState) {
        derivedStateOf { floorToHalfHour(timelineScrollState.visibleStartEpochSec) }
    }
    LaunchedEffect(bucketedStartSec) {
        onWindowStartChange(bucketedStartSec)
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

    LaunchedEffect(selectedDayIndex, days.size, dayStripHasFocus) {
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
    val pendingFocusChannel = remember {
        kotlinx.coroutines.channels.Channel<Pair<Long, Long>>(kotlinx.coroutines.channels.Channel.CONFLATED)
    }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var localFocusRestoreNonce by remember { mutableIntStateOf(0) }
    var wasSearchOpen by remember { mutableStateOf(searchOpen) }

    LaunchedEffect(Unit) {
        for ((chId, evId) in pendingFocusChannel) {
            val key = "ev_${chId}_$evId"
            repeat(4) {
                withFrameNanos { }
                val requester = cellFocusRequesters[key]
                if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                    return@repeat
                }
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

    val fallbackPreviewEvent = remember(searchOpen, trimmedQuery, searchResults, channels, epgByChannel, timelineOriginSec) {
        if (searchOpen && trimmedQuery.isNotEmpty()) {
            searchResults.firstOrNull()?.first
        } else {
            val endSec = timelineOriginSec + timelineScrollState.visibleDurationSec
            channels.firstNotNullOfOrNull { ch ->
                epgByChannel[ch.id]?.firstOrNull { it.stopEpochSec > timelineOriginSec && it.startEpochSec < endSec }
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
                                        text = stringResource(DesignR.string.guide_search_placeholder),
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
                        Text(stringResource(DesignR.string.common_clear), fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Now button (pinned) + Day jump bar (auto-scrolled to keep selected in viewport)
            val dateLabels = rememberLocalizedDateLabels()
            val isAtNow by remember(timelineScrollState) {
                derivedStateOf {
                    timelineScrollState.visibleStartEpochSec - timelineScrollState.originEpochSec < HALF_HOUR_SEC
                }
            }
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
                        scope.launch { timelineScrollState.animateScrollToEpoch(nowEpochSec) }
                    },
                    modifier = Modifier.focusRequester(nowButtonFocusRequester),
                    shape = tvButtonShape(),
                    colors = if (isAtNow) tvSecondaryButtonColors() else tvPrimaryButtonColors(),
                ) {
                    Icon(MuxGridIcons.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(DesignR.string.guide_now), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        TvFilterChip(
                            label = formatDayLabel(dayDate, nowEpochSec, activeZone, dateLabels),
                            selected = selected,
                            selectedContainerColor = MuxGridColors.AccentContainer,
                            selectedContentColor = MuxGridColors.AccentSoft,
                            unselectedContainerColor = MuxGridColors.CardSurface,
                            unselectedContentColor = MuxGridColors.TextSecondary,
                            onClick = {
                                val targetWindow = computeDayJumpWindowStart(
                                    dayDate = dayDate,
                                    todayDate = todayDate,
                                    todayStartHalfHour = nowEpochSec,
                                    epgByChannel = epgByChannel,
                                    windowDurationSec = timelineScrollState.visibleDurationSec,
                                    zoneId = activeZone,
                                )
                                scope.launch { timelineScrollState.animateScrollToEpoch(targetWindow) }
                            },
                        )
                    }
                }
            }
        }

        if (searchOpen && trimmedQuery.isNotEmpty()) {
            if (searchResults.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(DesignR.string.guide_no_programs_match, trimmedQuery), style = MuxGridType.CardSubtitle, fontSize = 16.sp)
                }
            } else {
                val searchDateLabels = rememberLocalizedDateLabels()
                val onNowLabel = stringResource(DesignR.string.live_on_now)
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
                        val rowFocusedState = remember(ev.id) { mutableStateOf(false) }
                        val rowDescription = remember(ev.id, ch.name, ev.title) {
                            "${ev.title}, ${ch.name}"
                        }
                        // Draw-phase focus styling avoids recomposition, modifier churn, and rounded-rect clips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableSurface(
                                    focusedState = rowFocusedState,
                                    cornerRadius = MuxGridRadii.Sm,
                                    containerColor = if (isLiveNow) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurface,
                                    focusedContainerColor = MuxGridColors.Accent.copy(alpha = 0.22f),
                                    focusBorderColor = MuxGridColors.FocusBorder,
                                    focusBorderWidth = MuxGridBorders.Focused,
                                )
                                .onFocusChanged { state ->
                                    val focused = state.isFocused
                                    if (rowFocusedState.value != focused) rowFocusedState.value = focused
                                    if (focused) focusedEventState.value = ev
                                }
                                .clickable(
                                    interactionSource = null,
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
                                    text = "${ch.name} · ${formatEpochDayHm(ev.startEpochSec, nowEpochSec, activeZone, searchDateLabels)}–${formatEpochHm(ev.stopEpochSec)} (${formatDurationMinutes(ev.startEpochSec, ev.stopEpochSec)})",
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
                                    text = onNowLabel,
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
            val (emptyText, showSpinner) = syncStatusMessage(
                connectionState = connectionState,
                emptyLabel = stringResource(DesignR.string.live_no_channels_available),
            )
            MuxGridSyncingState(
                message = emptyText,
                showSpinner = showSpinner,
                isTv = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            // Sticky time ruler + continuous broadcast horizontal timeline rows starting at Now
            val pxPerSec = remember(density) { with(density) { TV_TIMELINE_DP_PER_HOUR.toPx() / 3600f } }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                val bufferWindowCache = remember { LongArray(2) }
                val bufferRange by remember(timelineScrollState, timelineOriginSec, timelineEndSec, visibleDurationSec) {
                    derivedStateOf {
                        val range = computeTimelineBufferWindow(
                            visibleStartSec = timelineScrollState.visibleStartEpochSec,
                            visibleDurationSec = visibleDurationSec,
                            timelineOriginSec = timelineOriginSec,
                            timelineEndSec = timelineEndSec,
                            prevStartSec = bufferWindowCache[0],
                            prevEndSec = bufferWindowCache[1],
                        )
                        bufferWindowCache[0] = range.first
                        bufferWindowCache[1] = range.second
                        range
                    }
                }
                val bufferStartSec = bufferRange.first
                val bufferEndSec = bufferRange.second

                val accentHairlineColor = MuxGridColors.Accent
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val trackStartPx = channelColWidth.toPx()
                            val lineX = trackStartPx +
                                (nowEpochSec - timelineScrollState.originEpochSec) * timelineScrollState.pxPerSec -
                                timelineScrollState.scrollOffsetPx
                            if (lineX >= trackStartPx && lineX <= size.width) {
                                val lineW = 2.dp.toPx()
                                val rulerMidY = 14.dp.toPx()
                                drawCircle(
                                    color = accentHairlineColor,
                                    radius = 3.dp.toPx(),
                                    center = Offset(lineX, rulerMidY),
                                )
                                drawRect(
                                    color = accentHairlineColor,
                                    topLeft = Offset(lineX - lineW / 2f, rulerMidY),
                                    size = Size(lineW, (size.height - rulerMidY).coerceAtLeast(0f)),
                                )
                            }
                        },
                ) {
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
                                text = stringResource(DesignR.string.guide_channels_header),
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

                    // Hoist invariant EPG cell label once above LazyColumn for 32-bit ARM TV scroll performance
                    val noProgramDataLabel = stringResource(DesignR.string.guide_no_program_data)
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
                                timelineOriginSec,
                            ) {
                                buildContinuousTimelineCells(
                                    channelId = channel.id,
                                    events = channelEvents,
                                    rangeStart = bufferStartSec,
                                    rangeEnd = bufferEndSec,
                                    minStartEpochSec = timelineOriginSec,
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
                                // Sticky channel column (draw-phase focus styling avoids recomposition & rounded-rect clip layers)
                                val chFocusedState = remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .width(channelColWidth)
                                        .fillMaxHeight()
                                        .padding(end = 4.dp)
                                        .tvFocusableSurface(
                                            focusedState = chFocusedState,
                                            cornerRadius = MuxGridRadii.EpgCell,
                                            containerColor = baseCellBg,
                                            focusedContainerColor = MuxGridColors.CardSurfaceElevated,
                                            focusBorderColor = MuxGridColors.FocusBorder,
                                            focusBorderWidth = MuxGridBorders.Focused,
                                            unfocusedBorderColor = MuxGridColors.CardBorder,
                                            unfocusedBorderWidth = MuxGridBorders.Hairline,
                                        )
                                        .focusRequester(chFocusRequester)
                                        .onFocusChanged { state ->
                                            val focused = state.isFocused
                                            if (chFocusedState.value != focused) chFocusedState.value = focused
                                            if (focused) {
                                                focusMemory.guideTarget = TvGuideFocusTarget(channelId = channel.id)
                                            }
                                        }
                                        .clickable(
                                            interactionSource = null,
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
                                    anchorEpochSec = bufferStartSec,
                                    modifier = Modifier
                                        .width(trackWidth)
                                        .fillMaxHeight(),
                                ) { cell ->
                                    val ev = cell.event
                                    if (ev == null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clipToBounds()
                                                .background(baseCellBg.copy(alpha = 0.55f), MuxGridShapes.Xs)
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Text(
                                                text = noProgramDataLabel,
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
                                        val isRecording = linkedDvr?.state == DvrState.RECORDING
                                        val evFocusKey = "ev_${channel.id}_${ev.id}"
                                        val evFocusRequester = remember(evFocusKey) { FocusRequester() }
                                        val timeRangeLabel = remember(ev.id, ev.startEpochSec, ev.stopEpochSec, activeZone) {
                                            "${formatEpochHm(ev.startEpochSec, activeZone)}–${formatEpochHm(ev.stopEpochSec, activeZone)}"
                                        }
                                        DisposableEffect(evFocusKey) {
                                            cellFocusRequesters[evFocusKey] = evFocusRequester
                                            onDispose {
                                                if (cellFocusRequesters[evFocusKey] === evFocusRequester) {
                                                    cellFocusRequesters.remove(evFocusKey)
                                                }
                                            }
                                        }
                                        val evFocusedState = remember(evFocusKey) { mutableStateOf(false) }
                                        val liveAccentColor = MuxGridColors.Accent
                                        val recAccentColor = MuxGridColors.CoralLive
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clipToBounds()
                                                .tvFocusableSurface(
                                                    focusedState = evFocusedState,
                                                    cornerRadius = MuxGridRadii.EpgCell,
                                                    containerColor = if (isLiveNow) MuxGridColors.EpgLiveSurface else baseCellBg,
                                                    focusedContainerColor = MuxGridColors.AccentContainer,
                                                    focusBorderColor = MuxGridColors.FocusBorder,
                                                    focusBorderWidth = MuxGridBorders.Focused,
                                                    unfocusedBorderColor = when {
                                                        isRecording -> recAccentColor.copy(alpha = 0.65f)
                                                        isLiveNow -> MuxGridColors.EpgLiveBorder
                                                        else -> MuxGridColors.CardBorder
                                                    },
                                                    unfocusedBorderWidth = MuxGridBorders.Hairline,
                                                )
                                                .drawWithContent {
                                                    drawContent()
                                                    if (isLiveNow || isRecording) {
                                                        val barColor = if (isRecording) recAccentColor else liveAccentColor
                                                        val insetY = 3.dp.toPx()
                                                        drawRect(
                                                            color = barColor,
                                                            topLeft = Offset(0f, insetY),
                                                            size = Size(3.dp.toPx(), (size.height - insetY * 2f).coerceAtLeast(0f)),
                                                        )
                                                    }
                                                }
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
                                                        currentStartEpochSec = ev.startEpochSec,
                                                    ) ?: return@onPreviewKeyEvent false
                                                    val targetKey = "ev_${channel.id}_${target.id}"
                                                    val immediateRequester = cellFocusRequesters[targetKey]
                                                    if (immediateRequester == null || runCatching { immediateRequester.requestFocus() }.isFailure) {
                                                        pendingFocusChannel.trySend(channel.id to target.id)
                                                    }
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
                                                    if (evFocusedState.value != focused) evFocusedState.value = focused
                                                    if (focused) {
                                                        focusedEventState.value = ev
                                                        focusMemory.guideTarget = TvGuideFocusTarget(channel.id, ev.id)
                                                    }
                                                }
                                                .clickable(
                                                    interactionSource = null,
                                                    indication = null,
                                                    onClick = {
                                                        focusMemory.guideTarget = TvGuideFocusTarget(channel.id, ev.id)
                                                        actionError = null
                                                        actionEventKey = ev.channelId to ev.id
                                                    },
                                                )
                                                .clearAndSetSemantics {
                                                    role = Role.Button
                                                    contentDescription = "${ev.title}, $timeRangeLabel"
                                                },
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .timelineStickyTextOffset(cell.startEpochSec, cell.stopEpochSec, timelineScrollState)
                                                    .padding(
                                                        horizontal = 9.dp,
                                                        vertical = 7.dp,
                                                    ),
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
        val (subtitleText, desc) = resolveModalSubtitleAndDescription(
            subtitle = ev.subtitle ?: linkedDvr?.subtitle,
            description = ev.description ?: linkedDvr?.description,
            summary = ev.summary,
        )

        LaunchedEffect(ev.id, pendingConfirm == null) {
            if (pendingConfirm == null) {
                runCatching { primaryFocusRequester.requestFocus() }
            }
        }

        TvModalDialog(
            onDismissRequest = { if (!actionBusy) actionEventKey = null },
        ) {
            MuxGridDetailModalLayout(
                isSplitLayout = true,
                actionsColumnWidth = 224.dp,
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
                            Text(ev.title, style = MuxGridType.Title, fontSize = 18.sp)
                            val meta = formatProgramModalMeta(
                                channelName = ch?.name,
                                startEpochSec = ev.startEpochSec,
                                stopEpochSec = ev.stopEpochSec,
                                nowEpochSec = nowEpochSec,
                                dataSizeBytes = linkedDvr?.dataSize ?: 0L,
                            )
                            Text(text = meta, style = MuxGridType.TabularSmall)
                        }
                        if (linkedDvr != null) {
                            DvrStateBadge(state = linkedDvr.state, isWatched = linkedDvr.isWatched)
                        }
                    }

                    if (isLiveNow) {
                        EpgLiveProgressSection(
                            event = ev,
                            nowEpochSec = nowEpochSec,
                            isRecording = linkedDvr?.state == DvrState.RECORDING,
                        )
                    }

                    if (subtitleText != null) {
                        Text(
                            text = subtitleText,
                            style = MuxGridType.CardTitle,
                            color = MuxGridColors.AccentSoft,
                        )
                    }

                    if (desc != null) {
                        TvScrollableDescriptionBox(
                            text = desc,
                            resetKey = ev.id,
                        )
                    }

                    actionError?.let { err ->
                        Text(err, color = MuxGridColors.CoralLive, fontSize = 13.sp)
                    }
                },
                actionsContent = {
                    val hasSecondaryRecordAction = when (linkedDvr?.state) {
                        DvrState.RECORDING, DvrState.SCHEDULED -> true
                        DvrState.COMPLETED -> !linkedDvr.isFileMissing
                        else -> !isPast
                    }

                    if (ch != null) {
                        Button(
                            enabled = !actionBusy,
                            onClick = {
                                actionEventKey = null
                                onWatchChannel(ch)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(primaryFocusRequester),
                            shape = tvButtonShape(),
                            colors = tvPrimaryButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(if (isLiveNow) DesignR.string.live_watch_live else DesignR.string.live_watch_channel),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    val recordButtonModifier = if (ch == null) {
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(primaryFocusRequester)
                    } else {
                        Modifier.fillMaxWidth()
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
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        if (linkedDvr.hasResumePosition) DesignR.string.recordings_resume_chase_play else DesignR.string.recordings_watch_from_start,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            if (linkedDvr.hasResumePosition) {
                                Button(
                                    enabled = !actionBusy,
                                    onClick = {
                                        actionEventKey = null
                                        onPlayRecording(linkedDvr, ch, 0L)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = tvButtonShape(),
                                    colors = tvDialogSecondaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(DesignR.string.recordings_start_over), maxLines = 1, softWrap = false)
                                }
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = { pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to linkedDvr },
                                modifier = Modifier.fillMaxWidth(),
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(DesignR.string.recordings_stop_and_save), maxLines = 1, softWrap = false)
                            }
                            Button(
                                enabled = !actionBusy,
                                onClick = { pendingConfirm = DvrConfirmKind.ABORT_RECORDING to linkedDvr },
                                modifier = Modifier.fillMaxWidth(),
                                shape = tvButtonShape(),
                                colors = tvDialogDestructiveButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(DesignR.string.recordings_abort_recording), maxLines = 1, softWrap = false)
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
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(DesignR.string.recordings_cancel_timer), maxLines = 1, softWrap = false)
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
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        if (linkedDvr.hasResumePosition) DesignR.string.recordings_resume_recording else DesignR.string.recordings_play_recording,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            if (linkedDvr.hasResumePosition) {
                                Button(
                                    enabled = !actionBusy,
                                    onClick = {
                                        actionEventKey = null
                                        onPlayRecording(linkedDvr, ch, 0L)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = tvButtonShape(),
                                    colors = tvDialogSecondaryButtonColors(),
                                ) {
                                    Icon(MuxGridIcons.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(DesignR.string.recordings_start_over), maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        else -> if (!isPast) {
                            val scheduleFailedMsg = stringResource(DesignR.string.recordings_error_schedule_failed)
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    scope.launch {
                                        actionBusy = true
                                        actionError = null
                                        try {
                                            onScheduleRecording(ev.id)
                                                .onSuccess { actionEventKey = null }
                                                .onFailure { actionError = it.message ?: scheduleFailedMsg }
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
                                Text(
                                    stringResource(if (isLiveNow) DesignR.string.recordings_record_now else DesignR.string.recordings_record),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }

                    Button(
                        enabled = !actionBusy,
                        onClick = { actionEventKey = null },
                        modifier = if (ch == null && !hasSecondaryRecordAction) {
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(primaryFocusRequester)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        shape = tvButtonShape(),
                        colors = tvDialogSecondaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(DesignR.string.common_close), maxLines = 1, softWrap = false)
                    }
                },
            )
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
                            DvrConfirmKind.ABORT_RECORDING, DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(entry.id)
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
    val isLiveNow = previewEvent != null && nowEpochSec in previewEvent.startEpochSec until previewEvent.stopEpochSec
    val dvr = previewEvent?.let { dvrIndex.findMatch(it) }
    val isRecording = dvr?.state == DvrState.RECORDING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MuxGridColors.CardSurface, MuxGridShapes.Card)
            .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Card),
    ) {
        // Fixed 3.dp slot keeps the bottom bar height invariant when moving between live & upcoming shows
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLiveNow) {
                LiveProgressBar(
                    progress = previewEvent.progressFraction(nowEpochSec),
                    fillColor = if (isRecording) MuxGridColors.CoralLive else MuxGridColors.Accent,
                    height = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MuxGridDivider()
            }
        }
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (previewEvent != null) {
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
                        val previewDateLabels = rememberLocalizedDateLabels()
                        val remainingLabel = if (isLiveNow) {
                            stringResource(DesignR.string.time_minutes_left, previewEvent.remainingMinutes(nowEpochSec))
                        } else {
                            null
                        }
                        val timeText = buildString {
                            append("${formatEpochDayHm(previewEvent.startEpochSec, nowEpochSec, labels = previewDateLabels)}–${formatEpochHm(previewEvent.stopEpochSec)}")
                            append(" (${formatDurationMinutes(previewEvent.startEpochSec, previewEvent.stopEpochSec)})")
                            if (remainingLabel != null) {
                                append(" · $remainingLabel")
                            }
                        }
                        Text(
                            text = timeText,
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.AccentSoft,
                        )
                    }
                    val synopsis = previewEvent.description ?: previewEvent.summary ?: previewEvent.subtitle
                    Text(
                        text = synopsis?.takeIf { it.isNotBlank() } ?: stringResource(DesignR.string.guide_press_ok_hint),
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
                        text = stringResource(DesignR.string.guide_title),
                        style = MuxGridType.Title,
                        fontSize = 17.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(DesignR.string.guide_browse_hint),
                        style = MuxGridType.CardSubtitle,
                        minLines = 2,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
