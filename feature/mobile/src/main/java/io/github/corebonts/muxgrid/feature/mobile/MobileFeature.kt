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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.ConnectionStatusBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
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
                    hasMiniPlayer -> 172.dp
                    else -> 100.dp
                }

                MobileMainScaffold(
                    activeTab = activeTab,
                    useNavigationRail = useRail,
                    connectionState = connectionState,
                    playbackState = playbackState,
                    channels = channels,
                    nowNextMap = nowNextMap,
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
                        if (!useNavigationRail) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MuxGridColors.CardSurface)
                                    .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, CircleShape)
                                    .clickable(onClick = onOpenSettings),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MuxGridIcons.Settings,
                                    contentDescription = "Settings",
                                    tint = MuxGridColors.TextPrimary,
                                    modifier = Modifier.size(18.dp),
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
                                tint = MuxGridColors.AccentBlueLight,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = if (errState.isAuthError) "Edit" else "Retry",
                                color = MuxGridColors.AccentBlueLight,
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

            // Bottom floating chrome (Mini-player + Portrait Bottom Nav Pill)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasMiniPlayer) {
                    MobileMiniPlayerBar(
                        playbackState = playbackState,
                        channel = miniChannel,
                        nowNext = miniNowNext,
                        imageLoader = imageLoader,
                        hazeState = hazeState,
                        glassStyle = glassStyle,
                        onExpand = onExpandMiniPlayer,
                        onTogglePause = onToggleMiniPlayerPause,
                        onStop = onStopMiniPlayer,
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    )
                }

                if (!useNavigationRail) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MuxGridShapes.Dock)
                            .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle)
                            .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Dock)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            Triple(MainTab.LIVE, "Live", MuxGridIcons.Live to MuxGridIcons.LiveFilled),
                            Triple(MainTab.GUIDE, "Guide", MuxGridIcons.Guide to MuxGridIcons.GuideFilled),
                            Triple(MainTab.RECORDINGS, "Recordings", MuxGridIcons.Recordings to MuxGridIcons.RecordingsFilled),
                        ).forEach { (tab, label, icons) ->
                            val selected = tab == activeTab
                            val contentColor = if (selected) MuxGridColors.OnAccent else MuxGridColors.TextSecondary
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MuxGridShapes.CardLg)
                                    .background(if (selected) MuxGridColors.AccentBlue else Color.Transparent)
                                    .clickable { onSelectTab(tab) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (selected) icons.second else icons.first,
                                    contentDescription = label,
                                    tint = contentColor,
                                    modifier = Modifier.size(26.dp),
                                )
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
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(76.dp)
            .navigationBarsPadding()
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
            .clip(MuxGridShapes.Sheet)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle)
            .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Sheet)
            .padding(8.dp),
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
                val contentColor = if (selected) MuxGridColors.OnAccent else MuxGridColors.TextSecondary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Row)
                        .background(if (selected) MuxGridColors.AccentBlue else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (selected) icons.second else icons.first,
                        contentDescription = label,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MuxGridShapes.Row)
                .background(MuxGridColors.CardSurface)
                .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Row)
                .clickable(onClick = onOpenSettings)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MuxGridIcons.Settings,
                contentDescription = "Settings",
                tint = MuxGridColors.AccentBlueLight,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MobileMiniPlayerBar(
    playbackState: PlaybackState,
    channel: Channel?,
    nowNext: NowNextProgram?,
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
    val statusDotColor = when {
        playbackState.isPaused -> MuxGridColors.AmberWarn
        playbackState.isRadio -> MuxGridColors.AccentBlueLight
        else -> MuxGridColors.CoralLive
    }

    Row(
        modifier = modifier
            .clip(MuxGridShapes.CardLg)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle)
            .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.CardLg)
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (channel != null) {
            ChannelPiconBadge(
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                imageLoader = imageLoader,
                size = 38.dp,
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
                .size(36.dp)
                .clip(CircleShape)
                .background(MuxGridColors.AccentBlue)
                .clickable(onClick = onTogglePause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playbackState.isPaused) MuxGridIcons.Play else MuxGridIcons.Pause,
                contentDescription = if (playbackState.isPaused) "Play" else "Pause",
                tint = MuxGridColors.OnAccent,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MuxGridColors.CardSurface)
                .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, CircleShape)
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
                val filterIcon = when (filter) {
                    LiveFilter.Tv -> MuxGridIcons.Tv
                    LiveFilter.Radio -> MuxGridIcons.Radio
                    LiveFilter.Favorites -> MuxGridIcons.StarFilled
                    else -> null
                }
                Row(
                    modifier = Modifier
                        .clip(MuxGridShapes.Pill)
                        .background(if (selected) MuxGridColors.AccentBlue else MuxGridColors.CardSurface)
                        .border(
                            MuxGridBorders.Hairline,
                            if (selected) MuxGridColors.AccentBlueLight else MuxGridColors.HairlineBorder,
                            MuxGridShapes.Pill,
                        )
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (filterIcon != null) {
                        Icon(
                            imageVector = filterIcon,
                            contentDescription = null,
                            tint = if (selected) Color.White else MuxGridColors.TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
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
        } else if (gridColumns > 1) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomContentPadding),
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomContentPadding),
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
                    Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watch Live")
                }
                OutlinedButton(
                    onClick = {
                        onToggleFavorite(ch)
                        actionChannel = null
                    },
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val shape = MuxGridShapes.Card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, shape)
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
                modifier = Modifier.size(22.dp),
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
        containerColor = MuxGridColors.CardSurfaceElevated,
        contentColor = MuxGridColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = MuxGridIcons.Settings,
                    contentDescription = null,
                    tint = MuxGridColors.AccentBlueLight,
                    modifier = Modifier.size(22.dp),
                )
                Text("Settings", style = MuxGridType.Title, fontSize = 19.sp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Row)
                    .background(MuxGridColors.CardSurface)
                    .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Row)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MuxGridColors.AccentBlue, contentColor = MuxGridColors.OnAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(MuxGridIcons.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit Server Connection")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Row)
                    .background(MuxGridColors.CardSurface)
                    .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Row)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Background & Picture-in-Picture", style = MuxGridType.CardTitle)
                SettingToggleRow(
                    title = "Auto Picture-in-Picture",
                    subtitle = "Shrink live TV & recordings into PiP when leaving the app",
                    checked = pipEnabled,
                    onCheckedChange = onTogglePip,
                )
                SettingToggleRow(
                    title = "Background Play (Live TV Audio)",
                    subtitle = "Continue audio playback when backgrounded (Radio is always enabled)",
                    checked = backgroundPlayEnabled,
                    onCheckedChange = onToggleBackgroundPlay,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Row)
                    .background(MuxGridColors.CardSurface)
                    .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Row)
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
                            .clip(MuxGridShapes.Control)
                            .background(if (selected) MuxGridColors.AccentBlue.copy(alpha = 0.22f) else MuxGridColors.CardSurfaceElevated)
                            .border(
                                MuxGridBorders.Hairline,
                                if (selected) MuxGridColors.AccentBlueLight else MuxGridColors.HairlineBorder,
                                MuxGridShapes.Control,
                            )
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 13.sp, color = MuxGridColors.TextPrimary, modifier = Modifier.weight(1f))
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = MuxGridIcons.Check,
                                contentDescription = "Active",
                                tint = MuxGridColors.AccentBlueLight,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Row)
                    .background(MuxGridColors.CardSurface)
                    .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Row)
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
                                .clip(MuxGridShapes.Pill)
                                .background(if (selected) MuxGridColors.AccentBlue else MuxGridColors.CardSurfaceElevated)
                                .border(
                                    MuxGridBorders.Hairline,
                                    if (selected) MuxGridColors.AccentBlueLight else MuxGridColors.HairlineBorder,
                                    MuxGridShapes.Pill,
                                )
                                .clickable { onSelectTimeshiftWindow(option) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = option.shortLabel,
                                color = if (selected) MuxGridColors.OnAccent else MuxGridColors.TextSecondary,
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
            .clip(MuxGridShapes.Control)
            .background(MuxGridColors.CardSurfaceElevated)
            .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Control)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MuxGridColors.TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = MuxGridColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MuxGridColors.AccentBlue,
            ),
        )
    }
}
