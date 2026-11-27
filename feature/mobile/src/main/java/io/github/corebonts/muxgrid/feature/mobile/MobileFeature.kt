package io.github.corebonts.muxgrid.feature.mobile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.corebonts.muxgrid.core.designsystem.AppLocaleManager
import io.github.corebonts.muxgrid.core.designsystem.R as DesignR
import io.github.corebonts.muxgrid.core.designsystem.descriptionRes
import io.github.corebonts.muxgrid.core.designsystem.labelRes
import io.github.corebonts.muxgrid.core.designsystem.localizedCapabilitySummary
import io.github.corebonts.muxgrid.core.designsystem.localizedConnectionStatusText
import io.github.corebonts.muxgrid.core.designsystem.localizedLabel
import io.github.corebonts.muxgrid.core.designsystem.localizedShortLabel
import io.github.corebonts.muxgrid.core.designsystem.localizedSummary
import io.github.corebonts.muxgrid.core.designsystem.rememberLocalizedDateLabels
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import io.github.corebonts.muxgrid.core.designsystem.BroadcastPagerTabRow
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.ConnectionStatusDot
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrLookupIndex
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.EpgLiveProgressSection
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MobileTopBarHeight
import io.github.corebonts.muxgrid.core.designsystem.MuxGridAlphas
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridCanvasBackdrop
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridConfirmDialog
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDetailModalLayout
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridModalSheet
import io.github.corebonts.muxgrid.core.designsystem.MuxGridPrimaryActionButton
import io.github.corebonts.muxgrid.core.designsystem.MuxGridRadii
import io.github.corebonts.muxgrid.core.designsystem.MuxGridRadioOptionRow
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSecondaryButton
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSegmentedControl
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSplashScreen
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSwitch
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSyncingState
import io.github.corebonts.muxgrid.core.designsystem.MuxGridThemeSwatchPicker
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.muxGridAccentBrush
import io.github.corebonts.muxgrid.core.designsystem.muxGridCardSurface
import io.github.corebonts.muxgrid.core.designsystem.muxGridTopSpecularHighlight
import io.github.corebonts.muxgrid.core.designsystem.OnboardingServerScreen
import io.github.corebonts.muxgrid.core.designsystem.ServerDiagnosticsDetailContent
import io.github.corebonts.muxgrid.core.designsystem.STARTUP_SPLASH_EXIT_DURATION_MS
import io.github.corebonts.muxgrid.core.designsystem.STARTUP_SPLASH_EXIT_SCALE
import io.github.corebonts.muxgrid.core.designsystem.STARTUP_SPLASH_STATUS_DELAY_MS
import io.github.corebonts.muxgrid.core.designsystem.STARTUP_SPLASH_TIMEOUT_MS
import io.github.corebonts.muxgrid.core.designsystem.buildActiveRecordingsByChannel
import io.github.corebonts.muxgrid.core.designsystem.buildLiveFilters
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.findActiveRecordingForChannel
import io.github.corebonts.muxgrid.core.designsystem.formatProgramModalMeta
import io.github.corebonts.muxgrid.core.designsystem.isStartupSyncReady
import io.github.corebonts.muxgrid.core.designsystem.resolveModalSubtitleAndDescription
import io.github.corebonts.muxgrid.core.designsystem.syncStatusMessage
import kotlinx.coroutines.delay
import io.github.corebonts.muxgrid.core.designsystem.collectDebugEnvironmentSnapshot
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusColor
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusDotColor
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusText
import io.github.corebonts.muxgrid.core.designsystem.copyTextToClipboard
import io.github.corebonts.muxgrid.core.designsystem.filterChannels
import io.github.corebonts.muxgrid.core.designsystem.formatDebugInfoReport
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.icon
import io.github.corebonts.muxgrid.core.designsystem.label
import io.github.corebonts.muxgrid.core.designsystem.rememberNowEpochSec
import io.github.corebonts.muxgrid.core.designsystem.serverHeadline
import io.github.corebonts.muxgrid.core.model.AppAttribution
import io.github.corebonts.muxgrid.core.model.AppBuildInfo
import io.github.corebonts.muxgrid.core.model.AppNotices
import io.github.corebonts.muxgrid.core.model.BackgroundPlaybackMode
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DiagnosticSeverity
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.MainTab
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.ServerDiagnosticsReport
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
        MuxGridSplashScreen(backgroundColor = MuxGridColors.CanvasPhone)
        return
    }

    var hasExitedStartupSplash by rememberSaveable { mutableStateOf(false) }
    var showSplashStatus by remember { mutableStateOf(false) }
    val epgByChannel by repository.epgByChannel.collectAsState()
    val dvrEntries by repository.dvrEntries.collectAsState()
    val startupReady = !currentProfile.isConfigured || isStartupSyncReady(
        startTab = currentStartScreen.tab,
        connectionState = connectionState,
        hasChannels = channels.isNotEmpty(),
        hasEpg = epgByChannel.isNotEmpty(),
        hasRecordings = dvrEntries.isNotEmpty(),
    )
    LaunchedEffect(hasExitedStartupSplash) {
        if (!hasExitedStartupSplash) {
            delay(STARTUP_SPLASH_STATUS_DELAY_MS)
            showSplashStatus = true
        }
    }
    LaunchedEffect(hasExitedStartupSplash) {
        if (!hasExitedStartupSplash) {
            delay(STARTUP_SPLASH_TIMEOUT_MS)
            hasExitedStartupSplash = true
        }
    }
    LaunchedEffect(startupReady) {
        if (startupReady && !hasExitedStartupSplash) {
            // Wait one frame so newly populated lists/grids settle before the exit animation starts.
            withFrameNanos { }
            hasExitedStartupSplash = true
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
        MobileRoute.ONBOARDING -> {
            OnboardingServerScreen(
                initialProfile = currentProfile,
                isTv = false,
                canCancel = currentProfile.isConfigured,
                onProbePorts = { host, htsp, http -> repository.probeServerPorts(host, htsp, http) },
                onRunDiagnostics = { repository.runPreflightDiagnostics(it) },
                onSaveAndConnect = { updated, report ->
                    repository.saveAndConnect(updated, report)
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
                            val dvrEntries by repository.dvrEntries.collectAsState()
                            val activeRecordingsByChannel = remember(dvrEntries, nowNextMap) {
                                buildActiveRecordingsByChannel(dvrEntries, nowNextMap)
                            }
                            val nowEpochSec = rememberNowEpochSec(offsetMs)
                            MobileLiveChannelsTab(
                                connectionState = connectionState,
                                channels = channels,
                                filters = remember(tags) { buildLiveFilters(tags) },
                                activeFilter = activeFilter,
                                favorites = favorites,
                                nowNextMap = nowNextMap,
                                dvrEntries = dvrEntries,
                                activeRecordingsByChannel = activeRecordingsByChannel,
                                nowEpochSec = nowEpochSec,
                                imageLoader = repository.imageLoader,
                                gridColumns = gridColumns,
                                bottomContentPadding = bottomPad,
                                onSelectFilter = { activeFilter = it },
                                onChannelClick = { channel ->
                                    playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                    route = MobileRoute.PLAYER
                                },
                                onPlayRecording = { entry, channel, startPosSec ->
                                    playbackEngine.playDvrEntry(entry, channel, repository.hwDecPreset.value, startPosSec)
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
                                onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                            )
                        }
                        MainTab.GUIDE -> {
                            val nowEpochSec = rememberNowEpochSec(offsetMs)
                            MobileGuideScreen(
                                connectionState = connectionState,
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
                            val nowEpochSec = rememberNowEpochSec(offsetMs)
                            MobileRecordingsScreen(
                                connectionState = connectionState,
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
                            )
                        }
                        MainTab.SETTINGS -> {
                            val themeMode by repository.themeMode.collectAsState()
                            val radioAmbientGlowEnabled by repository.radioAmbientGlowEnabled.collectAsState()
                            val hwDecPreset by repository.hwDecPreset.collectAsState()
                            val timeshiftWindow by repository.timeshiftWindow.collectAsState()
                            val backgroundPlaybackMode by repository.backgroundPlaybackMode.collectAsState()
                            val serverDiagnostics by repository.serverDiagnostics.collectAsState()
                            val isRunningDiagnostics by repository.isRunningDiagnostics.collectAsState()
                            MobileSettingsTab(
                                profile = currentProfile,
                                connectionState = connectionState,
                                serverDiagnostics = serverDiagnostics,
                                isRunningDiagnostics = isRunningDiagnostics,
                                startScreen = currentStartScreen,
                                themeMode = themeMode,
                                radioAmbientGlowEnabled = radioAmbientGlowEnabled,
                                hwDecPreset = hwDecPreset,
                                timeshiftWindow = timeshiftWindow,
                                backgroundPlaybackMode = backgroundPlaybackMode,
                                pipSupported = canEnterPip,
                                appInfo = appInfo,
                                bottomContentPadding = bottomPad,
                                onSelectStartScreen = { screen ->
                                    scope.launch { repository.setStartScreen(screen) }
                                },
                                onSelectThemeMode = { mode ->
                                    scope.launch { repository.setThemeMode(mode) }
                                },
                                onSetRadioAmbientGlowEnabled = { enabled ->
                                    scope.launch { repository.setRadioAmbientGlowEnabled(enabled) }
                                },
                                onSelectPreset = { preset ->
                                    scope.launch { repository.setHwDecPreset(preset) }
                                    playbackEngine.applyPreset(preset)
                                },
                                onSelectTimeshiftWindow = { window ->
                                    scope.launch { repository.setTimeshiftWindow(window) }
                                },
                                onSelectBackgroundPlaybackMode = { mode ->
                                    scope.launch { repository.setBackgroundPlaybackMode(mode) }
                                },
                                onRetryConnect = {
                                    repository.reconnect()
                                },
                                onRefreshDiagnostics = {
                                    scope.launch { repository.refreshServerDiagnostics() }
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
            val radioAmbientGlowEnabled by repository.radioAmbientGlowEnabled.collectAsState()
            val nowEpochSec = rememberNowEpochSec(offsetMs)
            val activeChannel = channels.firstOrNull { it.id == playbackState.channelId }
            val activeNowNext = activeChannel?.let { nowNextMap[it.id] }
            val activeRecording = remember(activeChannel?.id, dvrEntries, activeNowNext?.now?.id, activeNowNext?.now?.dvrId) {
                activeChannel?.let { ch ->
                    findActiveRecordingForChannel(ch.id, dvrEntries, activeNowNext?.now)
                }
            }
            BackHandler(enabled = !isInPictureInPictureMode) {
                playbackEngine.setAudioOnly(true)
                route = MobileRoute.MAIN
            }
            val zapPool = filteredChannels.ifEmpty { channels }
            MobilePlayerScreen(
                    playbackEngine = playbackEngine,
                    playbackState = playbackState,
                    activeChannel = activeChannel,
                    nowNext = activeNowNext,
                    activeRecording = activeRecording,
                    channels = zapPool,
                    nowNextMap = nowNextMap,
                    nowEpochSec = nowEpochSec,
                    hwDecPreset = hwDecPreset,
                    imageLoader = repository.imageLoader,
                    isInPip = isInPictureInPictureMode,
                    canEnterPip = canEnterPip,
                    canZap = zapPool.size > 1,
                    radioAmbientGlowEnabled = radioAmbientGlowEnabled,
                    onEnterPip = onEnterPip,
                    onSelectChannel = { channel ->
                        playbackEngine.playChannel(channel, hwDecPreset)
                    },
                    onPlayRecording = { entry, startPosSec ->
                        playbackEngine.playDvrEntry(entry, activeChannel, hwDecPreset, startPosSec)
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
                        playbackEngine.setAudioOnly(true)
                        route = MobileRoute.MAIN
                    },
            )
        }
        }

        AnimatedVisibility(
            visible = !hasExitedStartupSplash,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = STARTUP_SPLASH_EXIT_DURATION_MS)) +
                scaleOut(
                    targetScale = STARTUP_SPLASH_EXIT_SCALE,
                    animationSpec = tween(durationMillis = STARTUP_SPLASH_EXIT_DURATION_MS),
                ),
        ) {
            val splashStatus = when (connectionState) {
                ConnectionState.Connecting, is ConnectionState.Syncing -> stringResource(DesignR.string.common_synchronizing)
                else -> stringResource(DesignR.string.common_connecting)
            }
            MuxGridSplashScreen(
                backgroundColor = MuxGridColors.CanvasPhone,
                statusText = splashStatus,
                showStatus = showSplashStatus && !hasExitedStartupSplash,
            )
        }
    }
}

@Composable
private fun rememberFrostedGlassStyle(): HazeBlurStyle {
    val canvasPhone = MuxGridColors.CanvasPhone
    val glassSurface = MuxGridColors.GlassSurface
    val scrimSurface = MuxGridColors.ScrimSurface
    val blurDp = if (MuxGridColors.CardBlurEnabled) 26.dp else 22.dp
    return remember(canvasPhone, glassSurface, scrimSurface, blurDp) {
        HazeBlurStyle {
            blurRadius(blurDp)
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Haze source wraps backdrop + browsing content so bottom nav, rail, and mini-player blur both.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            MuxGridCanvasBackdrop(
                isTv = false,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(if (useNavigationRail) Modifier.padding(start = 72.dp) else Modifier),
            ) {
                content(bottomContentPadding)
            }
        }

        if (useNavigationRail) {
            MobileNavigationRail(
                activeTab = activeTab,
                settingsStatusDotColor = settingsStatusDotColor,
                hazeState = hazeState,
                glassStyle = glassStyle,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
            )
        }

        // Docked frosted-glass bottom chrome (Mini-player + Portrait Bottom Nav Bar)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (useNavigationRail) Modifier.padding(start = 72.dp) else Modifier)
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            val label = tab.label()
                            val contentColor = if (selected) MuxGridColors.NavActiveContent else MuxGridColors.TextMuted
                            val dotColor = if (tab == MainTab.SETTINGS) settingsStatusDotColor else null
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MuxGridShapes.Control)
                                    .background(if (selected) MuxGridColors.NavActiveContainer else Color.Transparent)
                                    .clickable { onSelectTab(tab) }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height(2.5.dp)
                                        .clip(MuxGridShapes.Pill)
                                        .background(if (selected) MuxGridColors.NavActiveIndicator else Color.Transparent),
                                )
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = tab.icon(selected),
                                        contentDescription = label,
                                        tint = contentColor,
                                        modifier = Modifier.size(20.dp),
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

@Composable
private fun MobileNavigationRail(
    activeTab: MainTab,
    settingsStatusDotColor: Color?,
    hazeState: HazeState,
    glassStyle: HazeBlurStyle,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MainTab.LIVE, MainTab.GUIDE, MainTab.RECORDINGS).forEach { tab ->
                    val selected = tab == activeTab
                    MobileRailItem(
                        icon = tab.icon(selected),
                        label = tab.label(),
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                    )
                }
            }

            val settingsSelected = activeTab == MainTab.SETTINGS
            MobileRailItem(
                icon = MainTab.SETTINGS.icon(settingsSelected),
                label = MainTab.SETTINGS.label(),
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
    val contentColor = if (selected) MuxGridColors.NavActiveContent else MuxGridColors.TextMuted
    val itemBg = if (selected) MuxGridColors.NavActiveContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Control)
            .background(itemBg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .background(if (selected) MuxGridColors.NavActiveIndicator else Color.Transparent),
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
            .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Card)
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
    val title = playbackState.channelName ?: channel?.name ?: playbackState.programTitle ?: stringResource(DesignR.string.common_playing)
    val subtitle = playbackState.programTitle ?: nowNext?.now?.title ?: stringResource(
        if (playbackState.isRadio) DesignR.string.live_radio else DesignR.string.live_tv,
    )
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
                    .clip(MuxGridShapes.Control)
                    .background(muxGridAccentBrush())
                    .clickable(onClick = onTogglePause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playbackState.isPaused) MuxGridIcons.Play else MuxGridIcons.Pause,
                    contentDescription = stringResource(if (playbackState.isPaused) DesignR.string.common_play else DesignR.string.common_pause),
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
                    contentDescription = stringResource(DesignR.string.common_stop),
                    tint = MuxGridColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun LiveFilter.stableKey(): String = when (this) {
    LiveFilter.All -> "all"
    LiveFilter.Tv -> "tv"
    LiveFilter.Radio -> "radio"
    LiveFilter.Favorites -> "favorites"
    is LiveFilter.Tag -> "tag:$tagId"
}

@Composable
private fun MobileLiveChannelsTab(
    connectionState: ConnectionState,
    channels: List<Channel>,
    filters: List<LiveFilter>,
    activeFilter: LiveFilter,
    favorites: Set<Long>,
    nowNextMap: Map<Long, NowNextProgram>,
    dvrEntries: Map<Long, DvrEntry>,
    activeRecordingsByChannel: Map<Long, DvrEntry>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    gridColumns: Int,
    bottomContentPadding: Dp,
    onSelectFilter: (LiveFilter) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel, Long) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenGuideForChannel: (Channel) -> Unit,
    onRecordEvent: suspend (Long) -> Result<Long>,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val dvrIndex = remember(dvrEntries) { DvrLookupIndex(dvrEntries) }
    var actionChannelId by remember { mutableStateOf<Long?>(null) }
    var pendingConfirm by remember { mutableStateOf<Pair<DvrConfirmKind, DvrEntry>?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val activeIndex = filters.indexOf(activeFilter).takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(
        initialPage = activeIndex,
        pageCount = { filters.size },
    )

    LaunchedEffect(pagerState, filters) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                if (!scrolling) {
                    filters.getOrNull(page)?.let { settledFilter ->
                        if (settledFilter != activeFilter) {
                            onSelectFilter(settledFilter)
                        }
                    }
                }
            }
    }
    LaunchedEffect(activeIndex) {
        if (pagerState.currentPage != activeIndex && pagerState.targetPage != activeIndex) {
            pagerState.animateScrollToPage(activeIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BroadcastPagerTabRow(
            tabs = filters,
            pagerState = pagerState,
            labelFor = { it.label() },
            leadingIconFor = { it.icon() },
            onTabClick = { _, filter -> onSelectFilter(filter) },
        )
        MuxGridDivider()

        HorizontalPager(
            state = pagerState,
            key = { page -> filters.getOrNull(page)?.stableKey() ?: page },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageFilter = filters.getOrNull(page) ?: return@HorizontalPager
            val pageChannels = remember(channels, pageFilter, favorites) {
                filterChannels(channels, pageFilter, favorites)
            }
            MobileLiveChannelList(
                connectionState = connectionState,
                channels = pageChannels,
                hasAnyChannels = channels.isNotEmpty(),
                favorites = favorites,
                nowNextMap = nowNextMap,
                activeRecordingsByChannel = activeRecordingsByChannel,
                nowEpochSec = nowEpochSec,
                imageLoader = imageLoader,
                gridColumns = gridColumns,
                bottomContentPadding = bottomContentPadding,
                onChannelClick = onChannelClick,
                onChannelLongClick = { channel ->
                    actionMessage = null
                    actionChannelId = channel.id
                },
                onToggleFavorite = onToggleFavorite,
            )
        }
    }

    val actionChannel = actionChannelId?.let { id -> channels.firstOrNull { it.id == id } }
    actionChannel?.let { ch ->
        val chNowNext = nowNextMap[ch.id]
        val nowEvent = chNowNext?.now
        val activeRecording = activeRecordingsByChannel[ch.id]
        val linkedNowDvr = activeRecording ?: nowEvent?.let { dvrIndex.findMatch(it) }
        val isFav = ch.id in favorites
        val isLiveNow = nowEvent != null && nowEpochSec in nowEvent.startEpochSec until nowEvent.stopEpochSec
        val headlineText = if (ch.number > 0) "${ch.formattedNumber} · ${ch.name}" else ch.name
        val dateLabels = rememberLocalizedDateLabels()
        val metaText = when {
            nowEvent != null -> formatProgramModalMeta(
                channelName = nowEvent.title,
                startEpochSec = nowEvent.startEpochSec,
                stopEpochSec = nowEvent.stopEpochSec,
                nowEpochSec = nowEpochSec,
                labels = dateLabels,
            )
            activeRecording != null -> formatProgramModalMeta(
                channelName = activeRecording.title,
                startEpochSec = activeRecording.startEpochSec,
                stopEpochSec = activeRecording.stopEpochSec,
                nowEpochSec = nowEpochSec,
                dataSizeBytes = activeRecording.dataSize,
                labels = dateLabels,
            )
            ch.isRadio -> stringResource(DesignR.string.live_radio)
            else -> stringResource(DesignR.string.live_tv)
        }
        val (subtitleText, bodyText) = resolveModalSubtitleAndDescription(
            subtitle = nowEvent?.subtitle ?: activeRecording?.subtitle,
            description = nowEvent?.description ?: activeRecording?.description,
            summary = nowEvent?.summary,
        )
        MuxGridModalSheet(
            onDismissRequest = { if (!actionBusy) actionChannelId = null },
        ) {
            MuxGridDetailModalLayout(
                detailsContent = {
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
                            Text(headlineText, style = MuxGridType.Title, fontSize = 18.sp)
                            Text(metaText, style = MuxGridType.TabularSmall)
                        }
                        if (linkedNowDvr != null) {
                            DvrStateBadge(state = linkedNowDvr.state, isWatched = linkedNowDvr.isWatched)
                        }
                    }

                    if (nowEvent != null && isLiveNow) {
                        EpgLiveProgressSection(
                            event = nowEvent,
                            nowEpochSec = nowEpochSec,
                            isRecording = activeRecording != null,
                        )
                    }

                    if (subtitleText != null) {
                        Text(
                            text = subtitleText,
                            style = MuxGridType.CardTitle,
                            color = MuxGridColors.AccentSoft,
                        )
                    }

                    if (bodyText != null) {
                        Text(
                            text = bodyText,
                            style = MuxGridType.CardSubtitle,
                            color = MuxGridColors.TextPrimary.copy(alpha = MuxGridAlphas.SynopsisText),
                            lineHeight = 19.sp,
                        )
                    }

                    chNowNext?.next?.let { nextEv ->
                        Text(
                            text = stringResource(DesignR.string.live_next_program, formatEpochHm(nextEv.startEpochSec), nextEv.title),
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    actionMessage?.let { Text(it, color = MuxGridColors.CoralLive, fontSize = 13.sp) }
                },
                actionsContent = {
                    MuxGridPrimaryActionButton(
                        text = stringResource(DesignR.string.live_watch_live),
                        leadingIcon = MuxGridIcons.Play,
                        enabled = !actionBusy,
                        onClick = {
                            actionChannelId = null
                            onChannelClick(ch)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (activeRecording != null) {
                        MuxGridSecondaryButton(
                            text = stringResource(
                                if (activeRecording.hasResumePosition) DesignR.string.recordings_resume_chase_play else DesignR.string.recordings_watch_from_start,
                            ),
                            leadingIcon = if (activeRecording.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                            contentColor = MuxGridColors.AccentSoft,
                            enabled = !actionBusy,
                            onClick = {
                                actionChannelId = null
                                onPlayRecording(activeRecording, ch, activeRecording.effectiveStartSec)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (activeRecording.hasResumePosition) {
                            MuxGridSecondaryButton(
                                text = stringResource(DesignR.string.recordings_start_over),
                                leadingIcon = MuxGridIcons.Replay,
                                enabled = !actionBusy,
                                onClick = {
                                    actionChannelId = null
                                    onPlayRecording(activeRecording, ch, 0L)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        MuxGridSecondaryButton(
                            text = stringResource(DesignR.string.recordings_stop_and_save),
                            leadingIcon = MuxGridIcons.Stop,
                            enabled = !actionBusy,
                            onClick = {
                                pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to activeRecording
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MuxGridSecondaryButton(
                            text = stringResource(DesignR.string.recordings_abort_recording),
                            leadingIcon = MuxGridIcons.Cancel,
                            contentColor = MuxGridColors.CoralLive,
                            enabled = !actionBusy,
                            onClick = {
                                pendingConfirm = DvrConfirmKind.ABORT_RECORDING to activeRecording
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (linkedNowDvr?.state == DvrState.SCHEDULED) {
                        MuxGridSecondaryButton(
                            text = stringResource(DesignR.string.recordings_cancel_timer),
                            leadingIcon = MuxGridIcons.Cancel,
                            contentColor = MuxGridColors.CoralLive,
                            enabled = !actionBusy,
                            onClick = {
                                pendingConfirm = DvrConfirmKind.CANCEL_TIMER to linkedNowDvr
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (nowEvent != null) {
                        val scheduleFailedMsg = stringResource(DesignR.string.recordings_error_schedule_failed)
                        MuxGridSecondaryButton(
                            text = stringResource(DesignR.string.recordings_record_now),
                            leadingIcon = MuxGridIcons.Record,
                            contentColor = MuxGridColors.CoralLive,
                            enabled = !actionBusy,
                            onClick = {
                                scope.launch {
                                    actionBusy = true
                                    actionMessage = null
                                    try {
                                        onRecordEvent(nowEvent.id)
                                            .onSuccess { actionChannelId = null }
                                            .onFailure { actionMessage = it.message ?: scheduleFailedMsg }
                                    } finally {
                                        actionBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    MuxGridSecondaryButton(
                        text = stringResource(if (isFav) DesignR.string.live_remove_from_favorites else DesignR.string.live_add_to_favorites),
                        leadingIcon = if (isFav) MuxGridIcons.StarFilled else MuxGridIcons.Star,
                        enabled = !actionBusy,
                        onClick = {
                            onToggleFavorite(ch)
                            actionChannelId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MuxGridSecondaryButton(
                        text = stringResource(DesignR.string.live_view_in_guide),
                        leadingIcon = MuxGridIcons.Guide,
                        enabled = !actionBusy,
                        onClick = {
                            actionChannelId = null
                            onOpenGuideForChannel(ch)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
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
                val targetId = entry.id
                pendingConfirm = null
                scope.launch {
                    actionBusy = true
                    actionMessage = null
                    try {
                        val result = when (kind) {
                            DvrConfirmKind.STOP_AND_SAVE -> onStopRecording(targetId)
                            DvrConfirmKind.ABORT_RECORDING, DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(targetId)
                            else -> Result.success(Unit)
                        }
                        result
                            .onSuccess { actionChannelId = null }
                            .onFailure { err ->
                                actionMessage = err.message ?: spec.failureFallbackMessage
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
private fun MobileLiveChannelList(
    connectionState: ConnectionState,
    channels: List<Channel>,
    hasAnyChannels: Boolean,
    favorites: Set<Long>,
    nowNextMap: Map<Long, NowNextProgram>,
    activeRecordingsByChannel: Map<Long, DvrEntry>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    gridColumns: Int,
    bottomContentPadding: Dp,
    onChannelClick: (Channel) -> Unit,
    onChannelLongClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    if (channels.isEmpty()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                val (emptyText, showSpinner) = if (hasAnyChannels) {
                    stringResource(DesignR.string.live_no_channels_match_filter) to false
                } else {
                    syncStatusMessage(
                        connectionState = connectionState,
                        emptyLabel = stringResource(DesignR.string.live_no_channels_available),
                    )
                }
                MuxGridSyncingState(
                    message = emptyText,
                    showSpinner = showSpinner,
                    isTv = false,
                    modifier = Modifier.fillParentMaxSize(),
                )
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
                    isRecording = channel.id in activeRecordingsByChannel,
                    nowEpochSec = nowEpochSec,
                    isFavorite = channel.id in favorites,
                    imageLoader = imageLoader,
                    asCard = true,
                    onClick = { onChannelClick(channel) },
                    onLongClick = { onChannelLongClick(channel) },
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
                        isRecording = channel.id in activeRecordingsByChannel,
                        nowEpochSec = nowEpochSec,
                        isFavorite = channel.id in favorites,
                        imageLoader = imageLoader,
                        asCard = false,
                        onClick = { onChannelClick(channel) },
                        onLongClick = { onChannelLongClick(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) },
                    )
                    MuxGridDivider(modifier = Modifier.padding(start = 74.dp, end = 16.dp))
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
    isRecording: Boolean,
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
            .muxGridCardSurface()
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
                if (isRecording) {
                    DvrStateBadge(state = DvrState.RECORDING)
                }
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
                            text = stringResource(DesignR.string.time_minutes_short, mins),
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
                        text = stringResource(DesignR.string.live_next_program, formatEpochHm(next.startEpochSec), next.title),
                        style = MuxGridType.TabularSmall,
                        color = MuxGridColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = stringResource(if (channel.isRadio) DesignR.string.live_radio else DesignR.string.live_no_program_info),
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
                contentDescription = stringResource(
                    if (isFavorite) DesignR.string.live_remove_from_favorites else DesignR.string.live_add_to_favorites,
                ),
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
    serverDiagnostics: ServerDiagnosticsReport?,
    isRunningDiagnostics: Boolean,
    startScreen: StartScreen,
    themeMode: ThemeMode,
    radioAmbientGlowEnabled: Boolean,
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    backgroundPlaybackMode: BackgroundPlaybackMode,
    pipSupported: Boolean,
    appInfo: AppBuildInfo,
    bottomContentPadding: Dp,
    onSelectStartScreen: (StartScreen) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSetRadioAmbientGlowEnabled: (Boolean) -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
    onSelectBackgroundPlaybackMode: (BackgroundPlaybackMode) -> Unit,
    onRetryConnect: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onOpenServerSetup: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugReport by remember { mutableStateOf<String?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val backgroundModeOptions = if (pipSupported) {
        BackgroundPlaybackMode.entries
    } else {
        listOf(BackgroundPlaybackMode.OFF, BackgroundPlaybackMode.AUDIO)
    }
    val effectiveBackgroundMode = if (!pipSupported && backgroundPlaybackMode == BackgroundPlaybackMode.PIP) {
        BackgroundPlaybackMode.OFF
    } else {
        backgroundPlaybackMode
    }
    val onOpenUrl: (String) -> Unit = { url ->
        runCatching {
            uriHandler.openUri(url)
        }.onFailure {
            Toast.makeText(context, url, Toast.LENGTH_LONG).show()
        }
    }
    val onOpenGithub: () -> Unit = { onOpenUrl(appInfo.githubUrl) }
    val onOpenLicenses: () -> Unit = { showLicenses = true }
    val onOpenDebugInfo: () -> Unit = {
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
                        backgroundPlaybackMode = effectiveBackgroundMode,
                        serverDiagnostics = serverDiagnostics,
                    ),
                )
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTwoColumns = maxWidth >= 720.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (useTwoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MobileServerSettingsSection(
                            profile = profile,
                            connectionState = connectionState,
                            serverDiagnostics = serverDiagnostics,
                            isRunningDiagnostics = isRunningDiagnostics,
                            onRetryConnect = onRetryConnect,
                            onOpenDiagnostics = {
                                if (serverDiagnostics == null && !isRunningDiagnostics) {
                                    onRefreshDiagnostics()
                                }
                                showDiagnostics = true
                            },
                            onOpenServerSetup = onOpenServerSetup,
                        )
                        MobileGeneralSettingsSection(
                            startScreen = startScreen,
                            themeMode = themeMode,
                            radioAmbientGlowEnabled = radioAmbientGlowEnabled,
                            onSelectStartScreen = onSelectStartScreen,
                            onSelectThemeMode = onSelectThemeMode,
                            onSetRadioAmbientGlowEnabled = onSetRadioAmbientGlowEnabled,
                        )
                        MobileBackgroundPlaybackSection(
                            options = backgroundModeOptions,
                            selectedMode = effectiveBackgroundMode,
                            onSelectBackgroundPlaybackMode = onSelectBackgroundPlaybackMode,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MobileDecoderPresetSection(
                            hwDecPreset = hwDecPreset,
                            onSelectPreset = onSelectPreset,
                        )
                        MobileTimeshiftSection(
                            timeshiftWindow = timeshiftWindow,
                            onSelectTimeshiftWindow = onSelectTimeshiftWindow,
                        )
                        MobileAppInfoSection(
                            appInfo = appInfo,
                            onOpenGithub = onOpenGithub,
                            onOpenLicenses = onOpenLicenses,
                            onOpenDebugInfo = onOpenDebugInfo,
                        )
                    }
                }
            } else {
                MobileServerSettingsSection(
                    profile = profile,
                    connectionState = connectionState,
                    serverDiagnostics = serverDiagnostics,
                    isRunningDiagnostics = isRunningDiagnostics,
                    onRetryConnect = onRetryConnect,
                    onOpenDiagnostics = {
                        if (serverDiagnostics == null && !isRunningDiagnostics) {
                            onRefreshDiagnostics()
                        }
                        showDiagnostics = true
                    },
                    onOpenServerSetup = onOpenServerSetup,
                )
                MobileGeneralSettingsSection(
                    startScreen = startScreen,
                    themeMode = themeMode,
                    radioAmbientGlowEnabled = radioAmbientGlowEnabled,
                    onSelectStartScreen = onSelectStartScreen,
                    onSelectThemeMode = onSelectThemeMode,
                    onSetRadioAmbientGlowEnabled = onSetRadioAmbientGlowEnabled,
                )
                MobileBackgroundPlaybackSection(
                    options = backgroundModeOptions,
                    selectedMode = effectiveBackgroundMode,
                    onSelectBackgroundPlaybackMode = onSelectBackgroundPlaybackMode,
                )
                MobileDecoderPresetSection(
                    hwDecPreset = hwDecPreset,
                    onSelectPreset = onSelectPreset,
                )
                MobileTimeshiftSection(
                    timeshiftWindow = timeshiftWindow,
                    onSelectTimeshiftWindow = onSelectTimeshiftWindow,
                )
                MobileAppInfoSection(
                    appInfo = appInfo,
                    onOpenGithub = onOpenGithub,
                    onOpenLicenses = onOpenLicenses,
                    onOpenDebugInfo = onOpenDebugInfo,
                )
            }
        }
    }

    debugReport?.let { report ->
        val debugCopiedToast = stringResource(DesignR.string.settings_debug_info_copied)
        MuxGridModalSheet(
            onDismissRequest = { debugReport = null },
        ) {
            Text(
                text = stringResource(DesignR.string.settings_debug_info_title),
                style = MuxGridType.Title,
                fontSize = 18.sp,
            )
            Text(
                text = stringResource(DesignR.string.settings_debug_info_desc),
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
                MuxGridSecondaryButton(
                    text = stringResource(DesignR.string.common_close),
                    onClick = { debugReport = null },
                    modifier = Modifier.weight(1f),
                )
                MuxGridPrimaryActionButton(
                    text = stringResource(DesignR.string.common_copy),
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = "MuxGrid Debug Info",
                            text = report,
                            toastMessage = debugCopiedToast,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showLicenses) {
        MobileLicensesSheet(
            onDismiss = { showLicenses = false },
            onOpenUrl = onOpenUrl,
        )
    }

    if (showDiagnostics) {
        MuxGridModalSheet(
            onDismissRequest = { showDiagnostics = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(DesignR.string.settings_server_diagnostics_title),
                        style = MuxGridType.Title,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = stringResource(DesignR.string.settings_server_diagnostics_desc),
                        style = MuxGridType.CardSubtitle,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MuxGridShapes.Sm)
                        .background(MuxGridColors.CardSurface)
                        .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Sm)
                        .clickable(enabled = !isRunningDiagnostics, onClick = onRefreshDiagnostics),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRunningDiagnostics) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MuxGridColors.Accent,
                        )
                    } else {
                        Icon(
                            imageVector = MuxGridIcons.Refresh,
                            contentDescription = stringResource(DesignR.string.a11y_rerun_diagnostics),
                            tint = MuxGridColors.AccentSoft,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            ServerDiagnosticsDetailContent(
                report = serverDiagnostics,
                isRunning = isRunningDiagnostics,
            )
            val showEditServer = serverDiagnostics?.hasBlockers == true || serverDiagnostics?.hasDegraded == true
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MuxGridSecondaryButton(
                    text = stringResource(DesignR.string.common_close),
                    onClick = { showDiagnostics = false },
                    modifier = Modifier.weight(1f),
                )
                if (showEditServer) {
                    MuxGridPrimaryActionButton(
                        text = stringResource(DesignR.string.settings_edit_server),
                        onClick = {
                            showDiagnostics = false
                            onOpenServerSetup()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileServerSettingsSection(
    profile: ServerProfile,
    connectionState: ConnectionState,
    serverDiagnostics: ServerDiagnosticsReport?,
    isRunningDiagnostics: Boolean,
    onRetryConnect: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenServerSetup: () -> Unit,
) {
    val isServerError = connectionState is ConnectionState.Error
    val canRetryConnect = isServerError || connectionState is ConnectionState.Disconnected
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(DesignR.string.settings_section_server), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .muxGridCardSurface()
                .then(
                    if (isServerError) {
                        Modifier.border(
                            width = MuxGridBorders.Hairline,
                            color = MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.BorderSubtle),
                            shape = MuxGridShapes.Card,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    Text(
                        text = serverHeadline(profile, connectionState),
                        style = MuxGridType.TabularNumber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = localizedConnectionStatusText(profile, connectionState),
                        style = MuxGridType.TabularSmall,
                        color = connectionStatusColor(connectionState),
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
                                contentDescription = stringResource(DesignR.string.settings_reconnect),
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
                            contentDescription = stringResource(DesignR.string.settings_edit_server),
                            tint = MuxGridColors.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (profile.isConfigured) {
                MuxGridDivider()
                val healthSubtitle = when {
                    isRunningDiagnostics -> stringResource(DesignR.string.diagnostics_checking_short)
                    serverDiagnostics == null -> stringResource(DesignR.string.settings_server_diagnostics_tap_to_run)
                    serverDiagnostics.hasBlockers -> serverDiagnostics.blockerItems.firstOrNull()?.localizedSummary()
                        ?: stringResource(DesignR.string.settings_server_diagnostics_issue_detected)
                    serverDiagnostics.hasDegraded -> stringResource(
                        DesignR.string.settings_server_diagnostics_tap_details,
                        serverDiagnostics.localizedCapabilitySummary(),
                    )
                    else -> serverDiagnostics.localizedCapabilitySummary()
                }
                val healthColor = when {
                    isRunningDiagnostics || serverDiagnostics == null -> MuxGridColors.TextSecondary
                    serverDiagnostics.overallSeverity == DiagnosticSeverity.BLOCKER -> MuxGridColors.CoralLive
                    serverDiagnostics.overallSeverity == DiagnosticSeverity.DEGRADED -> MuxGridColors.AmberWarn
                    else -> MuxGridColors.EmeraldOk
                }
                MobileSettingsActionRow(
                    title = stringResource(DesignR.string.settings_server_diagnostics_title),
                    subtitle = healthSubtitle,
                    subtitleColor = healthColor,
                    contentDescription = stringResource(DesignR.string.a11y_view_server_diagnostics),
                    onClick = onOpenDiagnostics,
                )
            }
        }
    }
}

@Composable
private fun MobileGeneralSettingsSection(
    startScreen: StartScreen,
    themeMode: ThemeMode,
    radioAmbientGlowEnabled: Boolean,
    onSelectStartScreen: (StartScreen) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSetRadioAmbientGlowEnabled: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var selectedLanguageTag by remember { mutableStateOf(AppLocaleManager.currentLanguageTag(context)) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(DesignR.string.settings_section_general), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .muxGridCardSurface(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(DesignR.string.settings_start_screen),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MuxGridColors.TextPrimary,
                )
                MuxGridSegmentedControl(
                    options = StartScreen.entries,
                    selectedOption = startScreen,
                    labelFor = { stringResource(it.labelRes()) },
                    onSelect = onSelectStartScreen,
                )
                Text(
                    text = stringResource(startScreen.descriptionRes()),
                    style = MuxGridType.TabularSmall,
                    color = MuxGridColors.TextSecondary,
                )
            }
            MuxGridDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(DesignR.string.settings_theme),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MuxGridColors.TextPrimary,
                )
                MuxGridThemeSwatchPicker(
                    selectedTheme = themeMode,
                    onSelectTheme = onSelectThemeMode,
                )
            }
            MuxGridDivider()
            val languageOptions = AppLocaleManager.supportedLanguages
            val selectedLanguage = languageOptions.firstOrNull { it.tag == selectedLanguageTag }
                ?: languageOptions.first()
            var languageDialogOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { languageDialogOpen = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(DesignR.string.settings_language),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MuxGridColors.TextPrimary,
                )
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .background(MuxGridColors.CardSurfaceElevated, MuxGridShapes.Control)
                        .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Control)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = selectedLanguage.localizedLabel(),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MuxGridColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = MuxGridIcons.ExpandMore,
                        contentDescription = null,
                        tint = MuxGridColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (languageDialogOpen) {
                Dialog(
                    onDismissRequest = { languageDialogOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .widthIn(max = 380.dp)
                            .fillMaxWidth()
                            .clip(MuxGridShapes.Dialog)
                            .background(MuxGridColors.ModalSurface)
                            .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Dialog)
                            .muxGridTopSpecularHighlight(MuxGridRadii.Dialog, MuxGridColors.CardTopHighlight)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(DesignR.string.settings_language),
                            style = MuxGridType.Title,
                            fontSize = 18.sp,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            languageOptions.forEach { option ->
                                MuxGridRadioOptionRow(
                                    label = option.localizedLabel(),
                                    selected = option.tag == selectedLanguageTag,
                                    onClick = {
                                        selectedLanguageTag = option.tag
                                        languageDialogOpen = false
                                        AppLocaleManager.setLanguageTag(context, option.tag)
                                    },
                                )
                            }
                        }
                        MuxGridSecondaryButton(
                            text = stringResource(DesignR.string.common_cancel),
                            onClick = { languageDialogOpen = false },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            MuxGridDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetRadioAmbientGlowEnabled(!radioAmbientGlowEnabled) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(DesignR.string.settings_radio_ambient_glow),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MuxGridColors.TextPrimary,
                    )
                }
                MuxGridSwitch(
                    checked = radioAmbientGlowEnabled,
                    onCheckedChange = onSetRadioAmbientGlowEnabled,
                )
            }
        }
    }
}

@Composable
private fun MobileBackgroundPlaybackSection(
    options: List<BackgroundPlaybackMode>,
    selectedMode: BackgroundPlaybackMode,
    onSelectBackgroundPlaybackMode: (BackgroundPlaybackMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(DesignR.string.settings_section_background_playback), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .muxGridCardSurface()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MuxGridSegmentedControl(
                options = options,
                selectedOption = selectedMode,
                labelFor = { stringResource(it.labelRes()) },
                onSelect = onSelectBackgroundPlaybackMode,
            )
            Text(
                text = stringResource(selectedMode.descriptionRes()),
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun MobileDecoderPresetSection(
    hwDecPreset: HwDecPreset,
    onSelectPreset: (HwDecPreset) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(DesignR.string.settings_section_decoder_preset), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .muxGridCardSurface(),
        ) {
            val presets = listOf(
                HwDecPreset.MEDIACODEC_COPY_BWDIF to stringResource(DesignR.string.settings_decoder_copy_bwdif_mobile),
                HwDecPreset.MEDIACODEC_ZERO_COPY to stringResource(DesignR.string.settings_decoder_zero_copy_mobile),
            )
            presets.forEachIndexed { idx, (preset, label) ->
                if (idx > 0) MuxGridDivider()
                MuxGridRadioOptionRow(
                    label = label,
                    selected = preset == hwDecPreset,
                    onClick = { onSelectPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun MobileTimeshiftSection(
    timeshiftWindow: TimeshiftWindow,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(DesignR.string.settings_section_timeshift), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .muxGridCardSurface()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MuxGridSegmentedControl(
                options = TimeshiftWindow.entries,
                selectedOption = timeshiftWindow,
                labelFor = { it.localizedShortLabel() },
                onSelect = onSelectTimeshiftWindow,
            )
            Text(
                text = stringResource(DesignR.string.settings_timeshift_footer, stringResource(timeshiftWindow.descriptionRes())),
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun MobileAppInfoSection(
    appInfo: AppBuildInfo,
    onOpenGithub: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDebugInfo: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(DesignR.string.settings_section_app_info), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .muxGridCardSurface(),
        ) {
            SettingInfoRow(
                label = stringResource(DesignR.string.settings_version),
                value = appInfo.versionDisplay,
            )
            MuxGridDivider()
            SettingInfoRow(
                label = stringResource(DesignR.string.settings_build_sha),
                value = appInfo.shortSha,
            )
            MuxGridDivider()
            MobileSettingsActionRow(
                title = stringResource(DesignR.string.settings_github),
                subtitle = appInfo.githubDisplayUrl,
                subtitleColor = MuxGridColors.AccentSoft,
                contentDescription = stringResource(DesignR.string.a11y_open_github),
                onClick = onOpenGithub,
            )
            MuxGridDivider()
            MobileSettingsActionRow(
                title = stringResource(DesignR.string.settings_licenses_title),
                subtitle = stringResource(DesignR.string.settings_licenses_subtitle),
                contentDescription = stringResource(DesignR.string.a11y_show_licenses),
                onClick = onOpenLicenses,
            )
            MuxGridDivider()
            MobileSettingsActionRow(
                title = stringResource(DesignR.string.settings_debug_info_title),
                subtitle = stringResource(DesignR.string.settings_debug_info_subtitle),
                contentDescription = stringResource(DesignR.string.a11y_show_debug_info),
                onClick = onOpenDebugInfo,
            )
        }
    }
}

@Composable
private fun MobileLicensesSheet(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var selectedAttribution by remember { mutableStateOf<AppAttribution?>(null) }
    var documentText by remember { mutableStateOf("") }

    LaunchedEffect(selectedAttribution) {
        val selected = selectedAttribution
        if (selected == null) {
            documentText = ""
            return@LaunchedEffect
        }
        documentText = ""
        documentText = withContext(Dispatchers.IO) { AppNotices.readLicenseText(selected.document) }
    }

    BackHandler(enabled = selectedAttribution != null) { selectedAttribution = null }

    MuxGridModalSheet(onDismissRequest = onDismiss) {
        val current = selectedAttribution
        if (current == null) {
            Text(
                text = stringResource(DesignR.string.settings_licenses_title),
                style = MuxGridType.Title,
                fontSize = 18.sp,
            )
            Text(
                text = stringResource(DesignR.string.settings_licenses_distribution_note),
                style = MuxGridType.CardSubtitle,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .muxGridCardSurface()
                    .verticalScroll(rememberScrollState()),
            ) {
                AppNotices.attributions.forEachIndexed { idx, entry ->
                    if (idx > 0) MuxGridDivider()
                    MobileSettingsActionRow(
                        title = entry.name,
                        subtitle = "${entry.license} · ${entry.displayUrl}",
                        contentDescription = stringResource(DesignR.string.a11y_show_entry_license, entry.name),
                        onClick = { selectedAttribution = entry },
                    )
                }
            }
            MuxGridSecondaryButton(
                text = stringResource(DesignR.string.common_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = current.name,
                        style = MuxGridType.Title,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = current.license,
                        style = MuxGridType.CardSubtitle,
                    )
                }
                IconButton(onClick = { onOpenUrl(current.url) }) {
                    Icon(
                        imageVector = MuxGridIcons.OpenInNew,
                        contentDescription = stringResource(DesignR.string.a11y_open_entry_website, current.name),
                        tint = MuxGridColors.TextSecondary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(MuxGridShapes.Sm)
                    .background(MuxGridColors.CardSurface)
                    .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Sm)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = documentText.ifBlank { stringResource(DesignR.string.common_loading) },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MuxGridColors.TextPrimary,
                    )
                }
            }
            MuxGridSecondaryButton(
                text = stringResource(DesignR.string.common_back),
                onClick = { selectedAttribution = null },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MobileSettingsActionRow(
    title: String,
    subtitle: String,
    contentDescription: String,
    onClick: () -> Unit,
    subtitleColor: Color = MuxGridColors.TextSecondary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MuxGridColors.TextPrimary,
            )
            Text(
                text = subtitle,
                style = MuxGridType.TabularSmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = MuxGridIcons.ChevronRight,
            contentDescription = contentDescription,
            tint = MuxGridColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
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
