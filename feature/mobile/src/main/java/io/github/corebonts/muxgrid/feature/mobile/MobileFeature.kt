package io.github.corebonts.muxgrid.feature.mobile

import android.widget.Toast
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.corebonts.muxgrid.core.designsystem.ConnectionStatusDot
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MobileTopBarHeight
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridModalSheet
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSwitch
import io.github.corebonts.muxgrid.core.designsystem.MuxGridTheme
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.OnboardingServerScreen
import io.github.corebonts.muxgrid.core.designsystem.buildLiveFilters
import io.github.corebonts.muxgrid.core.designsystem.collectDebugEnvironmentSnapshot
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusDotColor
import io.github.corebonts.muxgrid.core.designsystem.copyDebugInfoToClipboard
import io.github.corebonts.muxgrid.core.designsystem.filterChannels
import io.github.corebonts.muxgrid.core.designsystem.formatDebugInfoReport
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.label
import io.github.corebonts.muxgrid.core.designsystem.rememberNowEpochSec
import io.github.corebonts.muxgrid.core.model.AppBuildInfo
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.MainTab
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.StartScreen
import io.github.corebonts.muxgrid.core.model.ThemeMode
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MobileRoute {
    ONBOARDING,
    MAIN,
    PLAYER,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileRoot(
    repository: TvheadendRepository,
    playbackEngine: PlaybackEngine,
    appInfo: AppBuildInfo = AppBuildInfo(versionName = "", versionCode = 0, buildSha = ""),
    isInPictureInPictureMode: Boolean = false,
    openPlayerSignal: Int = 0,
    canEnterPip: Boolean = false,
    onPlayerScreenActiveChanged: (Boolean) -> Unit = {},
    onEnterPip: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val profile by repository.serverProfile.collectAsState()
    val startScreen by repository.startScreen.collectAsState()
    val connectionState by repository.connectionState.collectAsState()
    val channels by repository.channels.collectAsState()
    val favorites by repository.favoriteChannelIds.collectAsState()

    val currentProfile = profile
    val currentStartScreen = startScreen
    if (currentProfile == null || currentStartScreen == null) {
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
    var activeTab by remember { mutableStateOf(currentStartScreen.tab) }
    var activeFilter by remember { mutableStateOf(currentStartScreen.liveFilter) }
    var guideChannelId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(route) {
        onPlayerScreenActiveChanged(route == MobileRoute.PLAYER)
    }
    DisposableEffect(Unit) {
        onDispose { onPlayerScreenActiveChanged(false) }
    }

    LaunchedEffect(openPlayerSignal) {
        val currentPlayback = playbackEngine.state.value
        if (openPlayerSignal > 0 && currentPlayback.status != PlaybackStatus.IDLE) {
            if (!currentPlayback.isRadio && currentPlayback.isAudioOnly) {
                playbackEngine.setAudioOnly(false)
            }
            route = MobileRoute.PLAYER
        }
    }

    val filteredChannels = remember(channels, activeFilter, favorites) {
        filterChannels(channels, activeFilter, favorites)
    }

    val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L

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
            BackHandler(enabled = activeTab != currentStartScreen.tab) {
                activeTab = currentStartScreen.tab
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp
                val gridColumns = if (useRail) {
                    (maxWidth / 310.dp).toInt().coerceIn(2, 3)
                } else {
                    1
                }

                MobileMainScaffold(
                    activeTab = activeTab,
                    useNavigationRail = useRail,
                    connectionState = connectionState,
                    playbackEngine = playbackEngine,
                    repository = repository,
                    channels = channels,
                    offsetMs = offsetMs,
                    onSelectTab = { activeTab = it },
                    onExpandMiniPlayer = {
                        val currentPlayback = playbackEngine.state.value
                        if (!currentPlayback.isRadio && currentPlayback.isAudioOnly) {
                            playbackEngine.setAudioOnly(false)
                        }
                        route = MobileRoute.PLAYER
                    },
                    onToggleMiniPlayerPause = { playbackEngine.togglePause() },
                    onStopMiniPlayer = { playbackEngine.stop() },
                ) { bottomPad ->
                    when (activeTab) {
                        MainTab.LIVE -> {
                            val tags by repository.tags.collectAsState()
                            val nowNextMap by repository.nowNextByChannel.collectAsState()
                            val nowEpochSec = rememberNowEpochSec(offsetMs)
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
                                    playbackEngine.playChannel(channel, repository.hwDecPreset.value)
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
                            val epgByChannel by repository.epgByChannel.collectAsState()
                            val dvrEntries by repository.dvrEntries.collectAsState()
                            val nowEpochSec = rememberNowEpochSec(offsetMs)
                            MobileGuideScreen(
                                channels = channels,
                                epgByChannel = epgByChannel,
                                dvrEntries = dvrEntries,
                                nowEpochSec = nowEpochSec,
                                imageLoader = repository.imageLoader,
                                initialSelectedChannelId = guideChannelId,
                                bottomContentPadding = bottomPad,
                                onWatchChannel = { channel ->
                                    playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                    route = MobileRoute.PLAYER
                                },
                                onPlayRecording = { entry, channel, startPosSec ->
                                    playbackEngine.playDvrEntry(entry, channel, repository.hwDecPreset.value, startPosSec)
                                    route = MobileRoute.PLAYER
                                },
                                onScheduleRecording = { eventId -> repository.scheduleRecording(eventId) },
                                onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                            )
                        }
                        MainTab.RECORDINGS -> {
                            val dvrEntries by repository.dvrEntries.collectAsState()
                            val nowEpochSec = rememberNowEpochSec(offsetMs)
                            MobileRecordingsScreen(
                                dvrEntries = dvrEntries,
                                channels = channels,
                                nowEpochSec = nowEpochSec,
                                imageLoader = repository.imageLoader,
                                bottomContentPadding = bottomPad,
                                onWatchLiveChannel = { channel ->
                                    playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                    route = MobileRoute.PLAYER
                                },
                                onPlayRecording = { entry, channel, startPosSec ->
                                    playbackEngine.playDvrEntry(entry, channel, repository.hwDecPreset.value, startPosSec)
                                    route = MobileRoute.PLAYER
                                },
                                onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                                onDeleteRecording = { dvrId -> repository.deleteRecording(dvrId) },
                                onToggleWatched = { dvrId, watched -> repository.markRecordingWatched(dvrId, watched) },
                                onRefresh = { repository.refresh() },
                            )
                        }
                        MainTab.SETTINGS -> {
                            val themeMode by repository.themeMode.collectAsState()
                            val hwDecPreset by repository.hwDecPreset.collectAsState()
                            val timeshiftWindow by repository.timeshiftWindow.collectAsState()
                            val backgroundPlayEnabled by repository.backgroundPlayEnabled.collectAsState()
                            val pipEnabled by repository.pipEnabled.collectAsState()
                            MobileSettingsTab(
                                profile = currentProfile,
                                connectionState = connectionState,
                                startScreen = currentStartScreen,
                                themeMode = themeMode,
                                hwDecPreset = hwDecPreset,
                                timeshiftWindow = timeshiftWindow,
                                backgroundPlayEnabled = backgroundPlayEnabled,
                                pipEnabled = pipEnabled,
                                appInfo = appInfo,
                                bottomContentPadding = bottomPad,
                                onSelectStartScreen = { screen ->
                                    scope.launch { repository.setStartScreen(screen) }
                                },
                                onSelectThemeMode = { mode ->
                                    scope.launch { repository.setThemeMode(mode) }
                                },
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
                                onRetryConnect = {
                                    repository.reconnect()
                                },
                                onOpenServerSetup = {
                                    route = MobileRoute.ONBOARDING
                                },
                            )
                        }
                    }
                }
            }
        }
        MobileRoute.PLAYER -> {
            val playbackState by playbackEngine.state.collectAsState()
            val nowNextMap by repository.nowNextByChannel.collectAsState()
            val hwDecPreset by repository.hwDecPreset.collectAsState()
            val backgroundPlayEnabled by repository.backgroundPlayEnabled.collectAsState()
            val nowEpochSec = rememberNowEpochSec(offsetMs)
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
            MuxGridTheme(isTv = false, darkTheme = true) {
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
}

@Composable
private fun rememberFrostedGlassStyle(): HazeBlurStyle {
    val canvasPhone = MuxGridColors.CanvasPhone
    val glassSurface = MuxGridColors.GlassSurface
    val scrimSurface = MuxGridColors.ScrimSurface
    return remember(canvasPhone, glassSurface, scrimSurface) {
        HazeBlurStyle {
            blurRadius(22.dp)
            backgroundColor(canvasPhone)
            colorEffects(listOf(HazeColorEffect.tint(glassSurface)))
            fallbackColorEffect(HazeColorEffect.tint(scrimSurface))
        }
    }
}

@Composable
private fun MobileMainScaffold(
    activeTab: MainTab,
    useNavigationRail: Boolean,
    connectionState: ConnectionState,
    playbackEngine: PlaybackEngine,
    repository: TvheadendRepository,
    channels: List<Channel>,
    offsetMs: Long,
    onSelectTab: (MainTab) -> Unit,
    onExpandMiniPlayer: () -> Unit,
    onToggleMiniPlayerPause: () -> Unit,
    onStopMiniPlayer: () -> Unit,
    content: @Composable (bottomContentPadding: Dp) -> Unit,
) {
    val hazeState = rememberHazeState()
    val glassStyle = rememberFrostedGlassStyle()
    val settingsStatusDotColor = connectionStatusDotColor(connectionState)
    val density = LocalDensity.current
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var bottomChromeHeightPx by remember { mutableIntStateOf(0) }

    // Observe only the boolean visibility here so high-frequency playbackState ticks never
    // recompose the scaffold or active tab (including Settings); MobileMiniPlayerSlot collects the rest.
    val hasMiniPlayer by remember(playbackEngine) {
        playbackEngine.state
            .map { it.status != PlaybackStatus.IDLE && (it.channelId != null || it.dvrEntryId != null) }
            .distinctUntilChanged()
    }.collectAsState(
        initial = playbackEngine.state.value.let {
            it.status != PlaybackStatus.IDLE && (it.channelId != null || it.dvrEntryId != null)
        },
    )
    val hasBottomChrome = hasMiniPlayer || !useNavigationRail
    val bottomContentPadding = if (hasBottomChrome) {
        val chromeHeight = if (bottomChromeHeightPx > 0) {
            with(density) { bottomChromeHeightPx.toDp() }
        } else {
            navBarBottomPadding + when {
                !useNavigationRail && hasMiniPlayer -> 120.dp
                !useNavigationRail -> 60.dp
                else -> 76.dp
            }
        }
        chromeHeight + 20.dp
    } else {
        navBarBottomPadding + 24.dp
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MuxGridColors.CanvasPhone)
            .statusBarsPadding(),
    ) {
        if (useNavigationRail) {
            MobileNavigationRail(
                activeTab = activeTab,
                settingsStatusDotColor = settingsStatusDotColor,
                hazeState = hazeState,
                glassStyle = glassStyle,
                onSelectTab = onSelectTab,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Haze source wraps non-video browsing content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                content(bottomContentPadding)
            }

            // Docked frosted-glass bottom chrome (Mini-player + Portrait Bottom Nav Bar)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomChromeHeightPx = it.height },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hasMiniPlayer) {
                    MobileMiniPlayerSlot(
                        useNavigationRail = useNavigationRail,
                        playbackEngine = playbackEngine,
                        repository = repository,
                        channels = channels,
                        offsetMs = offsetMs,
                        hazeState = hazeState,
                        glassStyle = glassStyle,
                        onExpand = onExpandMiniPlayer,
                        onTogglePause = onToggleMiniPlayerPause,
                        onStop = onStopMiniPlayer,
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
                                Triple(MainTab.SETTINGS, "Settings", MuxGridIcons.Settings to MuxGridIcons.SettingsFilled),
                            ).forEach { (tab, label, icons) ->
                                val selected = tab == activeTab
                                val contentColor = if (selected) MuxGridColors.Accent else MuxGridColors.TextMuted
                                val dotColor = if (tab == MainTab.SETTINGS) settingsStatusDotColor else null
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(MuxGridShapes.Sm)
                                        .clickable { onSelectTab(tab) }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (selected) icons.second else icons.first,
                                            contentDescription = label,
                                            tint = contentColor,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        if (dotColor != null) {
                                            ConnectionStatusDot(
                                                color = dotColor,
                                                borderColor = MuxGridColors.CanvasPhone,
                                                modifier = Modifier.align(Alignment.TopEnd),
                                            )
                                        }
                                    }
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
    settingsStatusDotColor: Color?,
    hazeState: HazeState,
    glassStyle: HazeBlurStyle,
    onSelectTab: (MainTab) -> Unit,
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
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(MainTab.LIVE, "Live", MuxGridIcons.Live to MuxGridIcons.LiveFilled),
                    Triple(MainTab.GUIDE, "Guide", MuxGridIcons.Guide to MuxGridIcons.GuideFilled),
                    Triple(MainTab.RECORDINGS, "Recordings", MuxGridIcons.Recordings to MuxGridIcons.RecordingsFilled),
                ).forEach { (tab, label, icons) ->
                    MobileRailItem(
                        icon = if (tab == activeTab) icons.second else icons.first,
                        label = label,
                        selected = tab == activeTab,
                        onClick = { onSelectTab(tab) },
                    )
                }
            }

            val settingsSelected = activeTab == MainTab.SETTINGS
            MobileRailItem(
                icon = if (settingsSelected) MuxGridIcons.SettingsFilled else MuxGridIcons.Settings,
                label = "Settings",
                selected = settingsSelected,
                statusDotColor = settingsStatusDotColor,
                onClick = { onSelectTab(MainTab.SETTINGS) },
            )
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
private fun MobileRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    statusDotColor: Color? = null,
) {
    val contentColor = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextMuted
    val itemBg = if (selected) MuxGridColors.CardSurfaceElevated else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Sm)
            .background(itemBg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .background(if (selected) MuxGridColors.Accent else Color.Transparent),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                if (statusDotColor != null) {
                    ConnectionStatusDot(
                        color = statusDotColor,
                        borderColor = if (selected) MuxGridColors.CardSurfaceElevated else MuxGridColors.CanvasPhone,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MobileMiniPlayerSlot(
    useNavigationRail: Boolean,
    playbackEngine: PlaybackEngine,
    repository: TvheadendRepository,
    channels: List<Channel>,
    offsetMs: Long,
    hazeState: HazeState,
    glassStyle: HazeBlurStyle,
    onExpand: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
) {
    val playbackState by playbackEngine.state.collectAsState()
    val nowNextMap by repository.nowNextByChannel.collectAsState()
    val nowEpochSec = rememberNowEpochSec(offsetMs)
    val miniChannel = remember(playbackState.channelId, channels) {
        channels.firstOrNull { it.id == playbackState.channelId }
    }
    val miniNowNext = miniChannel?.let { nowNextMap[it.id] }
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
        imageLoader = repository.imageLoader,
        hazeState = hazeState,
        glassStyle = glassStyle,
        onExpand = onExpand,
        onTogglePause = onTogglePause,
        onStop = onStop,
        modifier = miniModifier,
    )
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
    var actionChannelId by remember { mutableStateOf<Long?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }

    val triggerRefresh = {
        if (!isRefreshing) {
            scope.launch {
                isRefreshing = true
                refreshError = null
                try {
                    onRefresh().onFailure {
                        refreshError = it.message ?: "Failed to refresh channels"
                    }
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Broadcast Monitor underline filter tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MobileTopBarHeight)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                    modifier = Modifier.fillMaxHeight(),
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
                                actionChannelId = channel.id
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
                                    actionChannelId = channel.id
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

    val actionChannel = actionChannelId?.let { id -> channels.firstOrNull { it.id == id } }
    actionChannel?.let { ch ->
        val nowEvent = nowNextMap[ch.id]?.now
        val isFav = ch.id in favorites
        MuxGridModalSheet(
            onDismissRequest = { if (!actionBusy) actionChannelId = null },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChannelPiconBadge(
                    channelNumber = ch.formattedNumber,
                    channelName = ch.name,
                    piconModel = ch.icon?.let { PiconModel(ch.id, it) },
                    imageLoader = imageLoader,
                    size = 44.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(ch.name, style = MuxGridType.Title, fontSize = 18.sp)
                    nowEvent?.let { Text("On Now: ${it.title}", style = MuxGridType.CardSubtitle) }
                }
            }
            actionMessage?.let { Text(it, color = MuxGridColors.CoralLive, fontSize = 13.sp) }

            Button(
                enabled = !actionBusy,
                onClick = {
                    actionChannelId = null
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
                enabled = !actionBusy,
                onClick = {
                    onToggleFavorite(ch)
                    actionChannelId = null
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
                enabled = !actionBusy,
                onClick = {
                    actionChannelId = null
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
                    enabled = !actionBusy,
                    onClick = {
                        scope.launch {
                            actionBusy = true
                            actionMessage = null
                            try {
                                onRecordEvent(nowEvent.id)
                                    .onSuccess { actionChannelId = null }
                                    .onFailure { actionMessage = it.message ?: "Failed to record" }
                            } finally {
                                actionBusy = false
                            }
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

@Composable
private fun MobileSettingsTab(
    profile: ServerProfile,
    connectionState: ConnectionState,
    startScreen: StartScreen,
    themeMode: ThemeMode,
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    backgroundPlayEnabled: Boolean,
    pipEnabled: Boolean,
    appInfo: AppBuildInfo,
    bottomContentPadding: Dp,
    onSelectStartScreen: (StartScreen) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
    onToggleBackgroundPlay: (Boolean) -> Unit,
    onTogglePip: (Boolean) -> Unit,
    onRetryConnect: () -> Unit,
    onOpenServerSetup: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugReport by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val isServerError = connectionState is ConnectionState.Error
        val canRetryConnect = isServerError || connectionState is ConnectionState.Disconnected
        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("TVHEADEND SERVER", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.CardSurface)
                    .then(
                        if (isServerError) {
                            Modifier.border(
                                width = MuxGridBorders.Hairline,
                                color = MuxGridColors.CoralLive.copy(alpha = 0.45f),
                                shape = MuxGridShapes.Card,
                            )
                        } else {
                            Modifier
                        },
                    )
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
                        maxLines = if (isServerError) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canRetryConnect) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MuxGridShapes.Sm)
                                .background(MuxGridColors.CardSurfaceElevated)
                                .clickable(onClick = onRetryConnect),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MuxGridIcons.Refresh,
                                contentDescription = "Reconnect",
                                tint = MuxGridColors.AccentSoft,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MuxGridShapes.Sm)
                            .background(MuxGridColors.CardSurfaceElevated)
                            .clickable(onClick = onOpenServerSetup),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MuxGridIcons.Edit,
                            contentDescription = "Edit Server",
                            tint = MuxGridColors.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("GENERAL", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.Divider),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MuxGridColors.CardSurface)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Start Screen",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MuxGridColors.TextPrimary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        StartScreen.entries.forEach { option ->
                            BroadcastUnderlineTab(
                                label = option.label,
                                selected = option == startScreen,
                                onClick = { onSelectStartScreen(option) },
                            )
                        }
                    }
                    Text(
                        text = startScreen.description,
                        style = MuxGridType.TabularSmall,
                        color = MuxGridColors.TextSecondary,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MuxGridColors.CardSurface)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Theme",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MuxGridColors.TextPrimary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        ThemeMode.entries.forEach { option ->
                            BroadcastUnderlineTab(
                                label = option.label,
                                selected = option == themeMode,
                                onClick = { onSelectThemeMode(option) },
                            )
                        }
                    }
                    Text(
                        text = themeMode.description,
                        style = MuxGridType.TabularSmall,
                        color = MuxGridColors.TextSecondary,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("PLAYBACK & BACKGROUND", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.Divider),
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

        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("DECODER PRESET", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.Divider),
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
                            .background(MuxGridColors.CardSurface)
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

        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("LIVE TIMESHIFT CACHE LIMIT", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.CardSurface)
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

        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("APP INFO", style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.Divider),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                SettingInfoRow(
                    label = "Version",
                    value = appInfo.versionDisplay,
                )
                SettingInfoRow(
                    label = "Build SHA",
                    value = appInfo.shortSha,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MuxGridColors.CardSurface)
                        .clickable {
                            runCatching {
                                uriHandler.openUri(appInfo.githubUrl)
                            }.onFailure {
                                Toast.makeText(context, appInfo.githubUrl, Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "GitHub",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MuxGridColors.TextPrimary,
                        )
                        Text(
                            text = appInfo.githubDisplayUrl,
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.AccentSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = MuxGridIcons.ChevronRight,
                        contentDescription = "Open GitHub",
                        tint = MuxGridColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MuxGridColors.CardSurface)
                        .clickable {
                            scope.launch {
                                debugReport = withContext(Dispatchers.IO) {
                                    formatDebugInfoReport(
                                        collectDebugEnvironmentSnapshot(
                                            context = context,
                                            appInfo = appInfo,
                                            isTv = false,
                                            connectionState = connectionState,
                                            hwDecPreset = hwDecPreset,
                                            timeshiftWindow = timeshiftWindow,
                                            themeMode = themeMode,
                                            backgroundAudioEnabled = backgroundPlayEnabled,
                                            pipEnabled = pipEnabled,
                                        ),
                                    )
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "Debug Info",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MuxGridColors.TextPrimary,
                        )
                        Text(
                            text = "Device, OS, and playback environment",
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = MuxGridIcons.ChevronRight,
                        contentDescription = "Show Debug Info",
                        tint = MuxGridColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    debugReport?.let { report ->
        MuxGridModalSheet(
            onDismissRequest = { debugReport = null },
        ) {
            Text(
                text = "Debug Info",
                style = MuxGridType.Title,
                fontSize = 18.sp,
            )
            Text(
                text = "Non-personal hardware and software environment for issue reports.",
                style = MuxGridType.CardSubtitle,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(MuxGridShapes.Sm)
                    .background(MuxGridColors.CardSurface)
                    .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Sm)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = report,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MuxGridColors.TextPrimary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { debugReport = null },
                    shape = MuxGridShapes.Sm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Close", color = MuxGridColors.TextPrimary)
                }
                Button(
                    onClick = { copyDebugInfoToClipboard(context, report) },
                    shape = MuxGridShapes.Sm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MuxGridColors.Accent,
                        contentColor = MuxGridColors.OnAccent,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copy", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MuxGridColors.CardSurface)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MuxGridColors.TextPrimary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MuxGridType.TabularNumber,
            color = MuxGridColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            .background(MuxGridColors.CardSurface)
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
