package io.github.corebonts.muxgrid.feature.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.data.TvheadendRepository
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.ConnectionStatusBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.OnboardingServerScreen
import io.github.corebonts.muxgrid.core.designsystem.buildLiveFilters
import io.github.corebonts.muxgrid.core.designsystem.filterChannels
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.label
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.MainTab
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MobileRoute {
    ONBOARDING,
    MAIN,
    PLAYER,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMvpRoot(
    repository: TvheadendRepository,
    playbackEngine: PlaybackEngine,
) {
    val scope = rememberCoroutineScope()
    val profile by repository.serverProfile.collectAsState()
    val connectionState by repository.connectionState.collectAsState()
    val channels by repository.channels.collectAsState()
    val tags by repository.tags.collectAsState()
    val nowNextMap by repository.nowNextByChannel.collectAsState()
    val epgByChannel by repository.epgByChannel.collectAsState()
    val dvrEntries by repository.dvrEntries.collectAsState()
    val favorites by repository.favoriteChannelIds.collectAsState()
    val hwDecPreset by repository.hwDecPreset.collectAsState()
    val timeshiftWindow by repository.timeshiftWindow.collectAsState()
    val playbackState by playbackEngine.state.collectAsState()

    val currentProfile = profile
    if (currentProfile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MuxGridColors.CanvasPhone),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MuxGridColors.AccentBlueLight)
        }
        return
    }

    var route by remember(currentProfile.isConfigured) {
        mutableStateOf(if (currentProfile.isConfigured) MobileRoute.MAIN else MobileRoute.ONBOARDING)
    }
    var activeTab by remember { mutableStateOf(MainTab.LIVE) }
    var activeFilter by remember { mutableStateOf<LiveFilter>(LiveFilter.All) }
    var guideChannelId by remember { mutableStateOf<Long?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    val filteredChannels = remember(channels, activeFilter, favorites) {
        filterChannels(channels, activeFilter, favorites)
    }

    val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
    val nowEpochSec = (System.currentTimeMillis() + offsetMs) / 1000L

    when (route) {
        MobileRoute.ONBOARDING -> {
            BackHandler(enabled = currentProfile.isConfigured) {
                route = MobileRoute.MAIN
            }
            OnboardingServerScreen(
                initialProfile = currentProfile,
                isTv = false,
                canCancel = currentProfile.isConfigured,
                onTestConnection = { repository.testConnection(it) },
                onSaveAndConnect = { updated ->
                    repository.saveAndConnect(updated)
                    route = MobileRoute.MAIN
                },
                onCancel = { route = MobileRoute.MAIN },
            )
        }
        MobileRoute.MAIN -> {
            BackHandler(enabled = activeTab != MainTab.LIVE) {
                activeTab = MainTab.LIVE
            }
            MobileMainScaffold(
                activeTab = activeTab,
                connectionState = connectionState,
                onSelectTab = { activeTab = it },
                onOpenSettings = { settingsOpen = true },
                onOpenServerSetup = { route = MobileRoute.ONBOARDING },
                onRetryConnect = { repository.reconnect() },
            ) {
                when (activeTab) {
                    MainTab.LIVE -> {
                        MobileLiveChannelsTab(
                            connectionState = connectionState,
                            channels = filteredChannels,
                            filters = remember(tags) { buildLiveFilters(tags) },
                            activeFilter = activeFilter,
                            favorites = favorites,
                            nowNextMap = nowNextMap,
                            nowEpochSec = nowEpochSec,
                            imageLoader = repository.imageLoader,
                            onSelectFilter = { activeFilter = it },
                            onChannelClick = { channel ->
                                playbackEngine.playChannel(channel, hwDecPreset)
                                route = MobileRoute.PLAYER
                            },
                            onToggleFavorite = { channel ->
                                scope.launch { repository.toggleFavorite(channel.id) }
                            },
                            onOpenGuideForChannel = { channel ->
                                guideChannelId = channel.id
                                activeTab = MainTab.GUIDE
                            },
                            onRecordEvent = { eventId -> repository.scheduleRecording(eventId) },
                        )
                    }
                    MainTab.GUIDE -> {
                        MobileGuideScreen(
                            channels = channels,
                            epgByChannel = epgByChannel,
                            dvrEntries = dvrEntries,
                            nowEpochSec = nowEpochSec,
                            imageLoader = repository.imageLoader,
                            initialSelectedChannelId = guideChannelId,
                            onWatchChannel = { channel ->
                                playbackEngine.playChannel(channel, hwDecPreset)
                                route = MobileRoute.PLAYER
                            },
                            onPlayRecording = { entry, channel, startPosSec ->
                                playbackEngine.playDvrEntry(entry, channel, hwDecPreset, startPosSec)
                                route = MobileRoute.PLAYER
                            },
                            onScheduleRecording = { eventId -> repository.scheduleRecording(eventId) },
                            onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                            onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                        )
                    }
                    MainTab.RECORDINGS -> {
                        MobileRecordingsScreen(
                            dvrEntries = dvrEntries,
                            channels = channels,
                            nowEpochSec = nowEpochSec,
                            imageLoader = repository.imageLoader,
                            onWatchLiveChannel = { channel ->
                                playbackEngine.playChannel(channel, hwDecPreset)
                                route = MobileRoute.PLAYER
                            },
                            onPlayRecording = { entry, channel, startPosSec ->
                                playbackEngine.playDvrEntry(entry, channel, hwDecPreset, startPosSec)
                                route = MobileRoute.PLAYER
                            },
                            onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                            onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                            onDeleteRecording = { dvrId -> repository.deleteRecording(dvrId) },
                            onToggleWatched = { dvrId, watched -> repository.markRecordingWatched(dvrId, watched) },
                        )
                    }
                }
            }

            if (settingsOpen) {
                MobileSettingsSheet(
                    profile = currentProfile,
                    connectionState = connectionState,
                    hwDecPreset = hwDecPreset,
                    timeshiftWindow = timeshiftWindow,
                    onSelectPreset = { preset ->
                        scope.launch { repository.setHwDecPreset(preset) }
                        playbackEngine.applyPreset(preset)
                    },
                    onSelectTimeshiftWindow = { window ->
                        scope.launch { repository.setTimeshiftWindow(window) }
                    },
                    onOpenServerSetup = {
                        settingsOpen = false
                        route = MobileRoute.ONBOARDING
                    },
                    onDismiss = { settingsOpen = false },
                )
            }
        }
        MobileRoute.PLAYER -> {
            val activeChannel = channels.firstOrNull { it.id == playbackState.channelId }
            val activeNowNext = activeChannel?.let { nowNextMap[it.id] }
            BackHandler {
                playbackEngine.stop()
                route = MobileRoute.MAIN
            }
            MobilePlayerScreen(
                playbackEngine = playbackEngine,
                playbackState = playbackState,
                activeChannel = activeChannel,
                nowNext = activeNowNext,
                hwDecPreset = hwDecPreset,
                imageLoader = repository.imageLoader,
                canZap = filteredChannels.size > 1,
                onZapPrevious = {
                    val list = filteredChannels.ifEmpty { channels }
                    if (list.isNotEmpty()) {
                        val idx = list.indexOfFirst { it.id == playbackState.channelId }
                        val prev = if (idx <= 0) list.last() else list[idx - 1]
                        playbackEngine.playChannel(prev, hwDecPreset)
                    }
                },
                onZapNext = {
                    val list = filteredChannels.ifEmpty { channels }
                    if (list.isNotEmpty()) {
                        val idx = list.indexOfFirst { it.id == playbackState.channelId }
                        val next = if (idx < 0 || idx >= list.lastIndex) list.first() else list[idx + 1]
                        playbackEngine.playChannel(next, hwDecPreset)
                    }
                },
                onSelectPreset = { preset ->
                    scope.launch { repository.setHwDecPreset(preset) }
                    playbackEngine.applyPreset(preset)
                },
                onExit = {
                    playbackEngine.stop()
                    route = MobileRoute.MAIN
                },
            )
        }
    }
}

@Composable
private fun MobileMainScaffold(
    activeTab: MainTab,
    connectionState: ConnectionState,
    onSelectTab: (MainTab) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenServerSetup: () -> Unit,
    onRetryConnect: () -> Unit,
    content: @Composable () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var visibleError by remember { mutableStateOf<ConnectionState.Error?>(null) }

    LaunchedEffect(connectionState, lifecycleOwner) {
        visibleError = null
        if (connectionState is ConnectionState.Error) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!connectionState.isAuthError) {
                    delay(1_200L)
                }
                visibleError = connectionState
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MuxGridColors.CanvasPhone)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("MuxGrid", style = MuxGridType.Title)
                    Text(
                        text = when (activeTab) {
                            MainTab.LIVE -> "Live TV & Radio"
                            MainTab.GUIDE -> "Program Guide"
                            MainTab.RECORDINGS -> "DVR Recordings"
                        },
                        style = MuxGridType.TabularSmall,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConnectionStatusBadge(state = connectionState)
                    Text(
                        text = "Settings",
                        color = MuxGridColors.AccentBlueLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MuxGridColors.CardSurface)
                            .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(50))
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            visibleError?.let { errState ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MuxGridColors.CoralLive.copy(alpha = 0.14f))
                        .border(1.dp, MuxGridColors.CoralLive.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = errState.message,
                        color = MuxGridColors.TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (errState.isAuthError) "Edit" else "Retry",
                        color = MuxGridColors.AccentBlueLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (errState.isAuthError) onOpenServerSetup() else onRetryConnect()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }

        // Floating translucent bottom navigation pill
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MuxGridColors.ScrimSurface)
                .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(24.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                MainTab.LIVE to "Live",
                MainTab.GUIDE to "Guide",
                MainTab.RECORDINGS to "Recordings",
            ).forEach { (tab, label) ->
                val selected = tab == activeTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) MuxGridColors.AccentBlue else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else MuxGridColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileLiveChannelsTab(
    connectionState: ConnectionState,
    channels: List<Channel>,
    filters: List<LiveFilter>,
    activeFilter: LiveFilter,
    favorites: Set<Long>,
    nowNextMap: Map<Long, NowNextProgram>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onSelectFilter: (LiveFilter) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenGuideForChannel: (Channel) -> Unit,
    onRecordEvent: suspend (Long) -> Result<Long>,
) {
    val scope = rememberCoroutineScope()
    var actionChannel by remember { mutableStateOf<Channel?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { filter ->
                val selected = filter == activeFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MuxGridColors.AccentBlue else MuxGridColors.CardSurface)
                        .border(
                            1.dp,
                            if (selected) MuxGridColors.AccentBlueLight else MuxGridColors.HairlineBorder,
                            RoundedCornerShape(50),
                        )
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = filter.label(),
                        color = if (selected) Color.White else MuxGridColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val emptyText = when (connectionState) {
                    is ConnectionState.Connecting, is ConnectionState.Syncing -> "Syncing channels from Tvheadend…"
                    else -> "No channels match this filter"
                }
                Text(emptyText, style = MuxGridType.CardSubtitle)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(channels, key = { it.id }) { channel ->
                    MobileChannelCard(
                        channel = channel,
                        nowNext = nowNextMap[channel.id],
                        nowEpochSec = nowEpochSec,
                        isFavorite = channel.id in favorites,
                        imageLoader = imageLoader,
                        onClick = { onChannelClick(channel) },
                        onLongClick = {
                            actionMessage = null
                            actionChannel = channel
                        },
                        onToggleFavorite = { onToggleFavorite(channel) },
                    )
                }
            }
        }
    }

    actionChannel?.let { ch ->
        val nowEvent = nowNextMap[ch.id]?.now
        val isFav = ch.id in favorites
        ModalBottomSheet(
            onDismissRequest = { actionChannel = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MuxGridColors.CardSurfaceElevated,
            contentColor = MuxGridColors.TextPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(ch.name, style = MuxGridType.Title, fontSize = 18.sp)
                nowEvent?.let { Text("On Now: ${it.title}", style = MuxGridType.CardSubtitle) }
                actionMessage?.let { Text(it, color = MuxGridColors.CoralLive, fontSize = 12.sp) }

                Button(
                    onClick = {
                        actionChannel = null
                        onChannelClick(ch)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MuxGridColors.AccentBlue, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Watch Live")
                }
                OutlinedButton(
                    onClick = {
                        onToggleFavorite(ch)
                        actionChannel = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isFav) "Remove from Favorites" else "Add to Favorites", color = MuxGridColors.TextPrimary)
                }
                OutlinedButton(
                    onClick = {
                        actionChannel = null
                        onOpenGuideForChannel(ch)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View EPG Guide", color = MuxGridColors.TextPrimary)
                }
                if (nowEvent != null && nowEvent.dvrId == null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                onRecordEvent(nowEvent.id)
                                    .onSuccess { actionChannel = null }
                                    .onFailure { actionMessage = it.message ?: "Failed to record" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Record Current Show", color = MuxGridColors.CoralLive)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileChannelCard(
    channel: Channel,
    nowNext: NowNextProgram?,
    nowEpochSec: Long,
    isFavorite: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MuxGridColors.CardSurface)
            .border(1.dp, MuxGridColors.HairlineBorder, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelPiconBadge(
            channelNumber = channel.formattedNumber,
            channelName = channel.name,
            piconModel = channel.icon?.let { PiconModel(channel.id, it) },
            imageLoader = imageLoader,
            size = 50.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (channel.number > 0) {
                    Text(
                        text = channel.formattedNumber,
                        style = MuxGridType.TabularNumber,
                        color = MuxGridColors.AccentBlueLight,
                    )
                }
                Text(
                    text = channel.name,
                    style = MuxGridType.CardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(3.dp))
            val now = nowNext?.now
            if (now != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = now.title,
                        style = MuxGridType.CardSubtitle,
                        color = MuxGridColors.TextPrimary.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    nowNext.remainingMinutes(nowEpochSec)?.let { mins ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${mins}m left",
                            style = MuxGridType.TabularSmall,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                LiveProgressBar(
                    progress = nowNext.progressFraction(nowEpochSec),
                    modifier = Modifier.fillMaxWidth(),
                )
                nowNext.next?.let { next ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Next ${formatEpochHm(next.startEpochSec)} · ${next.title}",
                        style = MuxGridType.TabularSmall,
                        color = MuxGridColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = if (channel.isRadio) "Live Radio" else "No program info",
                    style = MuxGridType.CardSubtitle,
                    color = MuxGridColors.TextMuted,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isFavorite) "★" else "☆",
            color = if (isFavorite) MuxGridColors.AmberWarn else MuxGridColors.TextMuted,
            fontSize = 18.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleFavorite)
                .padding(6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSettingsSheet(
    profile: ServerProfile,
    connectionState: ConnectionState,
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
    onOpenServerSetup: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MuxGridColors.CardSurfaceElevated,
        contentColor = MuxGridColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MuxGridType.Title, fontSize = 19.sp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MuxGridColors.CardSurface)
                    .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Tvheadend Server", style = MuxGridType.CardTitle)
                Text(
                    text = "${profile.host}:${profile.htspPort} (HTTP ${profile.httpPort})",
                    style = MuxGridType.TabularSmall,
                )
                if (connectionState is ConnectionState.Connected) {
                    Text(
                        text = "${connectionState.serverName} ${connectionState.serverVersion} · HTSP v${connectionState.htspVersion}",
                        style = MuxGridType.TabularSmall,
                        color = MuxGridColors.EmeraldOk,
                    )
                }
                Button(
                    onClick = onOpenServerSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = MuxGridColors.AccentBlue, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit Server Connection")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MuxGridColors.CardSurface)
                    .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Video Decoder & Deinterlacer", style = MuxGridType.CardTitle)
                listOf(
                    HwDecPreset.MEDIACODEC_COPY_BWDIF to "Hardware Copy + bwdif Deinterlace (Phone Default)",
                    HwDecPreset.MEDIACODEC_ZERO_COPY to "Hardware Zero-Copy (mediacodec)",
                ).forEach { (preset, label) ->
                    val selected = preset == hwDecPreset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MuxGridColors.AccentBlue.copy(alpha = 0.22f) else MuxGridColors.CardSurfaceElevated)
                            .border(
                                1.dp,
                                if (selected) MuxGridColors.AccentBlueLight else MuxGridColors.HairlineBorder,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 13.sp, color = MuxGridColors.TextPrimary, modifier = Modifier.weight(1f))
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Text("Active", color = MuxGridColors.AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MuxGridColors.CardSurface)
                    .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Live Timeshift Cache Limit", style = MuxGridType.CardTitle)
                Text(
                    text = "Split evenly between pause & rewind · applies on next tune",
                    style = MuxGridType.TabularSmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeshiftWindow.entries.forEach { option ->
                        val selected = option == timeshiftWindow
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) MuxGridColors.AccentBlue else MuxGridColors.CardSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (selected) MuxGridColors.AccentBlueLight else MuxGridColors.HairlineBorder,
                                    RoundedCornerShape(50),
                                )
                                .clickable { onSelectTimeshiftWindow(option) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = option.shortLabel,
                                color = if (selected) Color.White else MuxGridColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
                Text(
                    text = timeshiftWindow.description,
                    style = MuxGridType.TabularSmall,
                    color = MuxGridColors.AccentBlueLight,
                )
            }
        }
    }
}
