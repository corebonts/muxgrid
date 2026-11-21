package io.github.corebonts.muxgrid.feature.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import io.github.corebonts.muxgrid.core.designsystem.DvrLookupIndex
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
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
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.filterEpgEventsForDay
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
    var selectedDay by remember { mutableStateOf(todayDate) }
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

    val days = remember(effectiveChannelId, epgByChannel, nowEpochSec / 3600L) {
        val events = effectiveChannelId?.let { epgByChannel[it] }.orEmpty()
        buildAvailableGuideDays(events, nowEpochSec, activeZone)
    }
    val activeDay = if (selectedDay in days) selectedDay else todayDate
    val selectedDayIndex = days.indexOf(activeDay).coerceAtLeast(0)
    val selectedChannelIndex = remember(channels, effectiveChannelId) {
        channels.indexOfFirst { it.id == effectiveChannelId }
    }

    val dayListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val agendaListState = rememberLazyListState()
    var hasScrolledDayOnce by remember { mutableStateOf(false) }
    var hasScrolledChannelOnce by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDayIndex, days.size, dayListState.layoutInfo.viewportSize.width) {
        scrollItemIntoViewport(dayListState, selectedDayIndex, animate = hasScrolledDayOnce)
        hasScrolledDayOnce = true
    }
    LaunchedEffect(selectedChannelIndex, channels.size, channelListState.layoutInfo.viewportSize.width) {
        if (selectedChannelIndex >= 0) {
            scrollItemIntoViewport(channelListState, selectedChannelIndex, animate = hasScrolledChannelOnce)
            hasScrolledChannelOnce = true
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
        trimmedQuery.isEmpty(),
        effectiveChannelId,
        activeDay,
        epgByChannel,
        channelById,
        activeZone,
    ) {
        if (trimmedQuery.isNotEmpty()) {
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
    LaunchedEffect(activeDay, effectiveChannelId, trimmedQuery.isEmpty(), agendaItems.size) {
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
            // Day underline tabs + icon-only search trigger
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
                            onClick = { selectedDay = day },
                            modifier = Modifier.fillMaxHeight(),
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

        if (trimmedQuery.isEmpty()) {
            // Compact borderless channel strip
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
                                        val startSec = if (linkedDvr.hasResumePosition) linkedDvr.playPositionSec else 0L
                                        onPlayRecording(linkedDvr, detailChannel, startSec)
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
                                actionError = it.message ?: when (kind) {
                                    DvrConfirmKind.STOP_AND_SAVE -> "Failed to stop recording"
                                    DvrConfirmKind.CANCEL_TIMER -> "Failed to cancel timer"
                                    else -> "Action failed"
                                }
                            }
                    } finally {
                        actionBusy = false
                    }
                }
            },
        )
    }
}
