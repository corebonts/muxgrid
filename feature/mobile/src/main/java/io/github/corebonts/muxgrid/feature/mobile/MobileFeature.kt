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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.data.TvheadendRepository
import io.github.corebonts.muxgrid.core.designsystem.BroadcastUnderlineTab
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.ConnectionStatusBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridCompactButton
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSwitch
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
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
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
    isInPictureInPictureMode: Boolean = false,
    openPlayerSignal: Int = 0,
    canEnterPip: Boolean = false,
    onPlayerScreenActiveChanged: (Boolean) -> Unit = {},
    onEnterPip: () -> Unit = {},
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
    val backgroundPlayEnabled by repository.backgroundPlayEnabled.collectAsState()
    val pipEnabled by repository.pipEnabled.collectAsState()
    val playbackState by playbackEngine.state.collectAsState()

    val currentProfile = profile
    if (currentProfile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MuxGridColors.CanvasPhone),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MuxGridColors.AccentSoft)
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

    LaunchedEffect(route) {
        onPlayerScreenActiveChanged(route == MobileRoute.PLAYER)
    }
    DisposableEffect(Unit) {
        onDispose { onPlayerScreenActiveChanged(false) }
    }

    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0 && playbackState.status != PlaybackStatus.IDLE) {
            if (!playbackState.isRadio && playbackState.isAudioOnly) {
                playbackEngine.setAudioOnly(false)
            }
            route = MobileRoute.PLAYER
        }
    }

    val filteredChannels = remember(channels, activeFilter, favorites) {
        filterChannels(channels, activeFilter, favorites)
    }

    val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
    val nowEpochSec = (System.currentTimeMillis() + offsetMs) / 1000L

    val hasMiniPlayer = playbackState.status != PlaybackStatus.IDLE &&
        (playbackState.channelId != null || playbackState.dvrEntryId != null)

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
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp
                val gridColumns = if (useRail) {
                    (maxWidth / 310.dp).toInt().coerceIn(2, 3)
                } else {
                    1
                }
                val bottomPad = when {
                    useRail && hasMiniPlayer -> 92.dp
                    useRail -> 24.dp
                    hasMiniPlayer -> 144.dp
                    else -> 82.dp
                }

                MobileMainScaffold(
                    activeTab = activeTab,
                    useNavigationRail = useRail,
                    connectionState = connectionState,
                    playbackState = playbackState,
                    channels = channels,
                    nowNextMap = nowNextMap,
                    nowEpochSec = nowEpochSec,
                    imageLoader = repository.imageLoader,
                    onSelectTab = { activeTab = it },
                    onOpenSettings = { settingsOpen = true },
                    onOpenServerSetup = { route = MobileRoute.ONBOARDING },
                    onRetryConnect = { repository.reconnect() },
                    onExpandMiniPlayer = {
                        if (!playbackState.isRadio && playbackState.isAudioOnly) {
                            playbackEngine.setAudioOnly(false)
                        }
                        route = MobileRoute.PLAYER
                    },
                    onToggleMiniPlayerPause = { playbackEngine.togglePause() },
                    onStopMiniPlayer = { playbackEngine.stop() },
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
                                gridColumns = gridColumns,
                                bottomContentPadding = bottomPad,
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
                                onRefresh = { repository.refresh() },
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
                                bottomContentPadding = bottomPad,
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
                                bottomContentPadding = bottomPad,
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
                                onRefresh = { repository.refresh() },
                            )
                        }
                    }
                }
            }

            if (settingsOpen) {
                MobileSettingsSheet(
                    profile = currentProfile,
                    connectionState = connectionState,
                    hwDecPreset = hwDecPreset,
                    timeshiftWindow = timeshiftWindow,
                    backgroundPlayEnabled = backgroundPlayEnabled,
                    pipEnabled = pipEnabled,
                    onSelectPreset = { preset ->
                        scope.launch { repository.setHwDecPreset(preset) }
                        playbackEngine.applyPreset(preset)
                    },
                    onSelectTimeshiftWindow = { window ->
                        scope.launch { repository.setTimeshiftWindow(window) }
                    },
                    onToggleBackgroundPlay = { enabled ->
                        scope.launch { repository.setBackgroundPlayEnabled(enabled) }
                    },
                    onTogglePip = { enabled ->
                        scope.launch { repository.setPipEnabled(enabled) }
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
            val minimizeOnBack = playbackState.isRadio || playbackState.isAudioOnly || backgroundPlayEnabled
            BackHandler(enabled = !isInPictureInPictureMode) {
                if (minimizeOnBack) {
                    playbackEngine.setAudioOnly(true)
                    route = MobileRoute.MAIN
                } else {
                    playbackEngine.stop()
                    route = MobileRoute.MAIN
                }
            }
            val zapPool = filteredChannels.ifEmpty { channels }
            MobilePlayerScreen(
                playbackEngine = playbackEngine,
                playbackState = playbackState,
                activeChannel = activeChannel,
                nowNext = activeNowNext,
                channels = zapPool,
                nowNextMap = nowNextMap,
                nowEpochSec = nowEpochSec,
                hwDecPreset = hwDecPreset,
                imageLoader = repository.imageLoader,
                isInPip = isInPictureInPictureMode,
                canEnterPip = canEnterPip,
                canZap = zapPool.size > 1,
                minimizeOnBack = minimizeOnBack,
                onEnterPip = onEnterPip,
                onMinimizeToAudio = {
                    playbackEngine.setAudioOnly(true)
                    route = MobileRoute.MAIN
                },
                onSelectChannel = { channel ->
                    playbackEngine.playChannel(channel, hwDecPreset)
                },
                onZapPrevious = {
                    if (zapPool.isNotEmpty()) {
                        val idx = zapPool.indexOfFirst { it.id == playbackState.channelId }
                        val prev = if (idx <= 0) zapPool.last() else zapPool[idx - 1]
                        playbackEngine.playChannel(prev, hwDecPreset)
                    }
                },
                onZapNext = {
                    if (zapPool.isNotEmpty()) {
                        val idx = zapPool.indexOfFirst { it.id == playbackState.channelId }
                        val next = if (idx < 0 || idx >= zapPool.lastIndex) zapPool.first() else zapPool[idx + 1]
                        playbackEngine.playChannel(next, hwDecPreset)
                    }
                },
                onSelectPreset = { preset ->
                    scope.launch { repository.setHwDecPreset(preset) }
                    playbackEngine.applyPreset(preset)
                },
                onSelectAspectMode = { mode ->
                    playbackEngine.setVideoAspectMode(mode)
                },
                onPlaybackCompleted = {
                    playbackEngine.stop()
                    route = MobileRoute.MAIN
                },
                onStop = {
                    playbackEngine.stop()
                    route = MobileRoute.MAIN
                },
                onExit = {
                    if (minimizeOnBack) {
                        playbackEngine.setAudioOnly(true)
                    } else {
                        playbackEngine.stop()
                    }
                    route = MobileRoute.MAIN
                },
            )
        }
    }
}

@Composable
private fun rememberFrostedGlassStyle(): HazeBlurStyle = remember {
    HazeBlurStyle {
        blurRadius(22.dp)
        backgroundColor(MuxGridColors.CanvasPhone)
        colorEffects(listOf(HazeColorEffect.tint(MuxGridColors.GlassSurface)))
        fallbackColorEffect(HazeColorEffect.tint(MuxGridColors.ScrimSurface))
    }
}

@Composable
private fun MobileMainScaffold(
    activeTab: MainTab,
    useNavigationRail: Boolean,
    connectionState: ConnectionState,
    playbackState: PlaybackState,
    channels: List<Channel>,
    nowNextMap: Map<Long, NowNextProgram>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    onSelectTab: (MainTab) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenServerSetup: () -> Unit,
    onRetryConnect: () -> Unit,
    onExpandMiniPlayer: () -> Unit,
    onToggleMiniPlayerPause: () -> Unit,
    onStopMiniPlayer: () -> Unit,
    content: @Composable () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var visibleError by remember { mutableStateOf<ConnectionState.Error?>(null) }
    val hazeState = rememberHazeState()
    val glassStyle = rememberFrostedGlassStyle()

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

    val hasMiniPlayer = playbackState.status != PlaybackStatus.IDLE &&
        (playbackState.channelId != null || playbackState.dvrEntryId != null)
    val miniChannel = remember(playbackState.channelId, channels) {
        channels.firstOrNull { it.id == playbackState.channelId }
    }
    val miniNowNext = miniChannel?.let { nowNextMap[it.id] }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MuxGridColors.CanvasPhone)
            .statusBarsPadding(),
    ) {
        if (useNavigationRail) {
            MobileNavigationRail(
                activeTab = activeTab,
                hazeState = hazeState,
                glassStyle = glassStyle,
                onSelectTab = onSelectTab,
                onOpenSettings = onOpenSettings,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Haze source wraps non-video browsing content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (activeTab) {
                            MainTab.LIVE -> "Live"
                            MainTab.GUIDE -> "Guide"
                            MainTab.RECORDINGS -> "Recordings"
                        },
                        style = MuxGridType.ScreenTitle,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (connectionState !is ConnectionState.Connected) {
                            ConnectionStatusBadge(state = connectionState)
                        }
                        if (!useNavigationRail) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(MuxGridShapes.Sm)
                                    .clickable(onClick = onOpenSettings),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MuxGridIcons.Settings,
                                    contentDescription = "Settings",
                                    tint = MuxGridColors.TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                visibleError?.let { errState ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MuxGridShapes.Row)
                            .background(MuxGridColors.CoralLive.copy(alpha = 0.14f))
                            .border(MuxGridBorders.Hairline, MuxGridColors.CoralLive.copy(alpha = 0.4f), MuxGridShapes.Row)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(MuxGridShapes.Sm)
                                .clickable {
                                    if (errState.isAuthError) onOpenServerSetup() else onRetryConnect()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (errState.isAuthError) MuxGridIcons.Edit else MuxGridIcons.Refresh,
                                contentDescription = null,
                                tint = MuxGridColors.AccentSoft,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = if (errState.isAuthError) "Edit" else "Retry",
                                color = MuxGridColors.AccentSoft,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }

            // Docked frosted-glass bottom chrome (Mini-player + Portrait Bottom Nav Bar)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hasMiniPlayer) {
                    val miniModifier = if (useNavigationRail) {
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .clip(MuxGridShapes.Card)
                            .border(MuxGridBorders.Divider, MuxGridColors.Divider, MuxGridShapes.Card)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    MobileMiniPlayerBar(
                        playbackState = playbackState,
                        channel = miniChannel,
                        nowNext = miniNowNext,
                        nowEpochSec = nowEpochSec,
                        imageLoader = imageLoader,
                        hazeState = hazeState,
                        glassStyle = glassStyle,
                        onExpand = onExpandMiniPlayer,
                        onTogglePause = onToggleMiniPlayerPause,
                        onStop = onStopMiniPlayer,
                        modifier = miniModifier,
                    )
                }

                if (!useNavigationRail) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle),
                    ) {
                        MuxGridDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            listOf(
                                Triple(MainTab.LIVE, "Live", MuxGridIcons.Live to MuxGridIcons.LiveFilled),
                                Triple(MainTab.GUIDE, "Guide", MuxGridIcons.Guide to MuxGridIcons.GuideFilled),
                                Triple(MainTab.RECORDINGS, "Recordings", MuxGridIcons.Recordings to MuxGridIcons.RecordingsFilled),
                            ).forEach { (tab, label, icons) ->
                                val selected = tab == activeTab
                                val contentColor = if (selected) MuxGridColors.Accent else MuxGridColors.TextMuted
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(MuxGridShapes.Sm)
                                        .clickable { onSelectTab(tab) }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        imageVector = if (selected) icons.second else icons.first,
                                        contentDescription = label,
                                        tint = contentColor,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        text = label,
                                        color = contentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
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

@Composable
private fun MobileNavigationRail(
    activeTab: MainTab,
    hazeState: HazeState,
    glassStyle: HazeBlurStyle,
    onSelectTab: (MainTab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle)
                .navigationBarsPadding()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(MainTab.LIVE, "Live", MuxGridIcons.Live to MuxGridIcons.LiveFilled),
                    Triple(MainTab.GUIDE, "Guide", MuxGridIcons.Guide to MuxGridIcons.GuideFilled),
                    Triple(MainTab.RECORDINGS, "Recordings", MuxGridIcons.Recordings to MuxGridIcons.RecordingsFilled),
                ).forEach { (tab, label, icons) ->
                    val selected = tab == activeTab
                    val contentColor = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextMuted
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MuxGridShapes.Sm)
                            .background(if (selected) MuxGridColors.CardSurfaceElevated else Color.Transparent)
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .background(if (selected) MuxGridColors.Accent else Color.Transparent),
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (selected) icons.second else icons.first,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Sm)
                    .clickable(onClick = onOpenSettings)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MuxGridIcons.Settings,
                    contentDescription = "Settings",
                    tint = MuxGridColors.TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(MuxGridBorders.Divider)
                .fillMaxHeight()
                .background(MuxGridColors.Divider),
        )
    }
}

@Composable
private fun MobileMiniPlayerBar(
    playbackState: PlaybackState,
    channel: Channel?,
    nowNext: NowNextProgram?,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    hazeState: HazeState,
    glassStyle: HazeBlurStyle,
    onExpand: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = playbackState.channelName ?: channel?.name ?: playbackState.programTitle ?: "Playing"
    val subtitle = playbackState.programTitle ?: nowNext?.now?.title ?: if (playbackState.isRadio) "Live Radio" else "Live TV"
    val statusDotColor = if (playbackState.isPaused) MuxGridColors.AmberWarn else MuxGridColors.CoralLive
    val progressFraction: Float? = when {
        playbackState.isRecordingPlayback && playbackState.durationMs > 0L -> {
            (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
        }
        nowNext?.now != null -> nowNext.progressFraction(nowEpochSec)
        else -> null
    }

    Column(
        modifier = modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle),
    ) {
        if (progressFraction != null) {
            LiveProgressBar(
                progress = progressFraction,
                fillColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.Accent,
                height = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            MuxGridDivider()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (channel != null) {
                ChannelPiconBadge(
                    channelNumber = channel.formattedNumber,
                    channelName = channel.name,
                    piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                    imageLoader = imageLoader,
                    size = 36.dp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusDotColor),
                    )
                    Text(
                        text = title,
                        color = MuxGridColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitle,
                    color = MuxGridColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MuxGridShapes.Sm)
                    .background(MuxGridColors.Accent)
                    .clickable(onClick = onTogglePause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playbackState.isPaused) MuxGridIcons.Play else MuxGridIcons.Pause,
                    contentDescription = if (playbackState.isPaused) "Play" else "Pause",
                    tint = MuxGridColors.OnAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MuxGridShapes.Sm)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MuxGridIcons.Stop,
                    contentDescription = "Stop",
                    tint = MuxGridColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
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
    gridColumns: Int,
    bottomContentPadding: Dp,
    onSelectFilter: (LiveFilter) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenGuideForChannel: (Channel) -> Unit,
    onRecordEvent: suspend (Long) -> Result<Long>,
    onRefresh: suspend () -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    var actionChannel by remember { mutableStateOf<Channel?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }

    val triggerRefresh = {
        if (!isRefreshing) {
            scope.launch {
                isRefreshing = true
                refreshError = null
                onRefresh().onFailure {
                    refreshError = it.message ?: "Failed to refresh channels"
                }
                isRefreshing = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Broadcast Monitor underline filter tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            filters.forEach { filter ->
                val selected = filter == activeFilter
                val filterIcon = when (filter) {
                    LiveFilter.Tv -> MuxGridIcons.Tv
                    LiveFilter.Radio -> MuxGridIcons.Radio
                    LiveFilter.Favorites -> MuxGridIcons.StarFilled
                    else -> null
                }
                BroadcastUnderlineTab(
                    label = filter.label(),
                    selected = selected,
                    leadingIcon = filterIcon,
                    onClick = { onSelectFilter(filter) },
                )
            }
        }
        MuxGridDivider()

        refreshError?.let { message ->
            Text(
                text = message,
                color = MuxGridColors.CoralLive,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = triggerRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (channels.isEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val emptyText = when (connectionState) {
                                is ConnectionState.Connecting, is ConnectionState.Syncing -> "Syncing channels from Tvheadend…"
                                else -> "No channels match this filter"
                            }
                            Text(emptyText, style = MuxGridType.CardSubtitle)
                        }
                    }
                }
            } else if (gridColumns > 1) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(channels, key = { it.id }) { channel ->
                        MobileChannelCard(
                            channel = channel,
                            nowNext = nowNextMap[channel.id],
                            nowEpochSec = nowEpochSec,
                            isFavorite = channel.id in favorites,
                            imageLoader = imageLoader,
                            asCard = true,
                            onClick = { onChannelClick(channel) },
                            onLongClick = {
                                actionMessage = null
                                actionChannel = channel
                            },
                            onToggleFavorite = { onToggleFavorite(channel) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = bottomContentPadding),
                ) {
                    items(channels, key = { it.id }) { channel ->
                        Column {
                            MobileChannelCard(
                                channel = channel,
                                nowNext = nowNextMap[channel.id],
                                nowEpochSec = nowEpochSec,
                                isFavorite = channel.id in favorites,
                                imageLoader = imageLoader,
                                asCard = false,
                                onClick = { onChannelClick(channel) },
                                onLongClick = {
                                    actionMessage = null
                                    actionChannel = channel
                                },
                                onToggleFavorite = { onToggleFavorite(channel) },
                            )
                            MuxGridDivider(modifier = Modifier.padding(start = 74.dp, end = 16.dp))
                        }
                    }
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
            shape = MuxGridShapes.SheetTop,
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
                    shape = MuxGridShapes.Sm,
                    colors = ButtonDefaults.buttonColors(containerColor = MuxGridColors.Accent, contentColor = MuxGridColors.OnAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watch Live", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = {
                        onToggleFavorite(ch)
                        actionChannel = null
                    },
                    shape = MuxGridShapes.Sm,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (isFav) MuxGridIcons.StarFilled else MuxGridIcons.Star,
                        contentDescription = null,
                        tint = if (isFav) MuxGridColors.AmberWarn else MuxGridColors.TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFav) "Remove from Favorites" else "Add to Favorites", color = MuxGridColors.TextPrimary)
                }
                OutlinedButton(
                    onClick = {
                        actionChannel = null
                        onOpenGuideForChannel(ch)
                    },
                    shape = MuxGridShapes.Sm,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(MuxGridIcons.Guide, contentDescription = null, tint = MuxGridColors.TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
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
                        shape = MuxGridShapes.Sm,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(MuxGridIcons.Record, contentDescription = null, tint = MuxGridColors.CoralLive, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
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
    asCard: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val baseModifier = if (asCard) {
        Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    }
    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelPiconBadge(
            channelNumber = channel.formattedNumber,
            channelName = channel.name,
            piconModel = channel.icon?.let { PiconModel(channel.id, it) },
            imageLoader = imageLoader,
            size = 46.dp,
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
                        color = MuxGridColors.TextMuted,
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
            Spacer(Modifier.height(2.dp))
            val now = nowNext?.now
            if (now != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = now.title,
                        style = MuxGridType.CardSubtitle,
                        color = MuxGridColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    nowNext.remainingMinutes(nowEpochSec)?.let { mins ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${mins}m",
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.TextMuted,
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
        Box(
            modifier = Modifier
                .clip(MuxGridShapes.Sm)
                .clickable(onClick = onToggleFavorite)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFavorite) MuxGridIcons.StarFilled else MuxGridIcons.Star,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) MuxGridColors.AmberWarn else MuxGridColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSettingsSheet(
    profile: ServerProfile,
    connectionState: ConnectionState,
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    backgroundPlayEnabled: Boolean,
    pipEnabled: Boolean,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
    onToggleBackgroundPlay: (Boolean) -> Unit,
    onTogglePip: (Boolean) -> Unit,
    onOpenServerSetup: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MuxGridShapes.SheetTop,
        containerColor = MuxGridColors.CardSurface,
        contentColor = MuxGridColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = MuxGridType.Title, modifier = Modifier.padding(horizontal = 2.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TVHEADEND SERVER", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Card)
                        .background(MuxGridColors.CardSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val serverHeadline = if (connectionState is ConnectionState.Connected) {
                            "${connectionState.serverName} · ${profile.host}:${profile.htspPort}"
                        } else {
                            "${profile.host}:${profile.htspPort}"
                        }
                        Text(
                            text = serverHeadline,
                            style = MuxGridType.TabularNumber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val (statusText, statusColor) = when (connectionState) {
                            is ConnectionState.Connected -> {
                                "Connected · ${connectionState.serverVersion} · HTSP v${connectionState.htspVersion}" to MuxGridColors.EmeraldOk
                            }
                            ConnectionState.Connecting -> "Connecting…" to MuxGridColors.AmberWarn
                            is ConnectionState.Syncing -> "Syncing…" to MuxGridColors.AmberWarn
                            is ConnectionState.Error -> connectionState.message to MuxGridColors.CoralLive
                            ConnectionState.Disconnected -> "Disconnected · HTTP ${profile.httpPort}" to MuxGridColors.TextMuted
                        }
                        Text(
                            text = statusText,
                            style = MuxGridType.TabularSmall,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MuxGridCompactButton(
                        text = "Edit",
                        leadingIcon = MuxGridIcons.Edit,
                        containerColor = MuxGridColors.CardSurface,
                        contentColor = MuxGridColors.TextPrimary,
                        onClick = onOpenServerSetup,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PLAYBACK & BACKGROUND", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Card)
                        .background(MuxGridColors.CanvasPhone),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    SettingToggleRow(
                        title = "Auto Picture-in-Picture",
                        subtitle = "Shrink live TV & recordings into PiP when leaving",
                        checked = pipEnabled,
                        onCheckedChange = onTogglePip,
                    )
                    SettingToggleRow(
                        title = "Background Audio",
                        subtitle = "Keep live TV audio playing when minimized (Radio always on)",
                        checked = backgroundPlayEnabled,
                        onCheckedChange = onToggleBackgroundPlay,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DECODER PRESET", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Card)
                        .background(MuxGridColors.CanvasPhone),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    listOf(
                        HwDecPreset.MEDIACODEC_COPY_BWDIF to "HW Copy + bwdif Deinterlace",
                        HwDecPreset.MEDIACODEC_ZERO_COPY to "Hardware Zero-Copy (mediacodec)",
                    ).forEach { (preset, label) ->
                        val selected = preset == hwDecPreset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MuxGridColors.CardSurfaceElevated)
                                .clickable { onSelectPreset(preset) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = MuxGridIcons.Check,
                                    contentDescription = "Active",
                                    tint = MuxGridColors.Accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LIVE TIMESHIFT CACHE LIMIT", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Card)
                        .background(MuxGridColors.CardSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        TimeshiftWindow.entries.forEach { option ->
                            BroadcastUnderlineTab(
                                label = option.shortLabel,
                                selected = option == timeshiftWindow,
                                onClick = { onSelectTimeshiftWindow(option) },
                            )
                        }
                    }
                    Text(
                        text = "${timeshiftWindow.description} · split pause/rewind · applies on next tune",
                        style = MuxGridType.TabularSmall,
                        color = MuxGridColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MuxGridColors.CardSurfaceElevated)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MuxGridColors.TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = MuxGridColors.TextSecondary)
        }
        MuxGridSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
