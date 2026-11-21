package io.github.corebonts.muxgrid.feature.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.data.TvheadendRepository
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.ConnectionStatusDot
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridAlphas
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDurations
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridTheme
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.OnboardingServerScreen
import io.github.corebonts.muxgrid.core.designsystem.buildLiveFilters
import io.github.corebonts.muxgrid.core.designsystem.collectDebugEnvironmentSnapshot
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusColor
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusDotColor
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusText
import io.github.corebonts.muxgrid.core.designsystem.copyTextToClipboard
import io.github.corebonts.muxgrid.core.designsystem.filterChannels
import io.github.corebonts.muxgrid.core.designsystem.floorToHalfHour
import io.github.corebonts.muxgrid.core.designsystem.formatDebugInfoReport
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.icon
import io.github.corebonts.muxgrid.core.designsystem.label
import io.github.corebonts.muxgrid.core.designsystem.rememberNowEpochSec
import io.github.corebonts.muxgrid.core.designsystem.serverHeadline
import io.github.corebonts.muxgrid.core.model.AppBuildInfo
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ThemeMode
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.MainTab
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.StartScreen
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TvRoute {
    ONBOARDING,
    MAIN,
    PLAYER,
}

internal enum class TvSettingsFocusTarget {
    PRIMARY,
    EDIT_SERVER,
}

internal data class TvGuideFocusTarget(
    val channelId: Long,
    val eventId: Long? = null,
)

internal data class TvRecordingFocusTarget(
    val shelfKey: String,
    val entryId: Long,
)

/**
 * Non-observable focus memory so D-pad moves never trigger Compose recomposition.
 */
internal class TvFocusMemory {
    var liveChannelId: Long? = null
    var guideTarget: TvGuideFocusTarget? = null
    var recordingTarget: TvRecordingFocusTarget? = null
    var settingsTarget: TvSettingsFocusTarget = TvSettingsFocusTarget.PRIMARY
}

private val TV_RAIL_COLLAPSED_WIDTH = 68.dp
private val TV_RAIL_EXPANDED_WIDTH = 186.dp

@Composable
internal fun tvPrimaryButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.Accent,
    contentColor = MuxGridColors.OnAccent,
    focusedContainerColor = MuxGridColors.FocusBorder,
    focusedContentColor = MuxGridColors.OnFocusBorder,
)

@Composable
internal fun tvSecondaryButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.CardSurfaceElevated,
    contentColor = MuxGridColors.TextPrimary,
    focusedContainerColor = MuxGridColors.FocusBorder,
    focusedContentColor = MuxGridColors.OnFocusBorder,
)

@Composable
internal fun tvDialogSecondaryButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.CardSurface,
    contentColor = MuxGridColors.TextPrimary,
    focusedContainerColor = MuxGridColors.FocusBorder,
    focusedContentColor = MuxGridColors.OnFocusBorder,
)

@Composable
internal fun tvDialogDestructiveButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.CoralLive.copy(alpha = 0.16f),
    contentColor = MuxGridColors.CoralLive,
    focusedContainerColor = MuxGridColors.CoralLive,
    focusedContentColor = MuxGridColors.OnCoralLive,
)

@Composable
internal fun tvButtonShape() = ButtonDefaults.shape(shape = MuxGridShapes.Sm)

@Composable
internal fun TvModalDialog(
    onDismissRequest: () -> Unit,
    minWidth: Dp = 440.dp,
    maxWidth: Dp = 680.dp,
    borderColor: Color = MuxGridColors.HairlineBorder,
    borderWidth: Dp = MuxGridBorders.Hairline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = minWidth, max = maxWidth)
                .clip(MuxGridShapes.Dialog)
                .background(MuxGridColors.CardSurfaceElevated)
                .border(borderWidth, borderColor, MuxGridShapes.Dialog)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    enabled: Boolean = true,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching {
            if (isDestructive) cancelFocusRequester.requestFocus() else confirmFocusRequester.requestFocus()
        }
    }
    TvModalDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        minWidth = 380.dp,
        maxWidth = 520.dp,
        borderColor = if (isDestructive) MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.BorderSubtle) else MuxGridColors.HairlineBorder,
    ) {
        Text(
            text = title,
            style = MuxGridType.Title,
            fontSize = 18.sp,
            color = MuxGridColors.TextPrimary,
        )
        Text(
            text = message,
            style = MuxGridType.CardSubtitle,
            fontSize = 14.sp,
            color = MuxGridColors.TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            Button(
                enabled = enabled,
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancelFocusRequester),
                shape = tvButtonShape(),
                colors = tvDialogSecondaryButtonColors(),
            ) {
                Text(dismissLabel, maxLines = 1, softWrap = false)
            }
            Button(
                enabled = enabled,
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirmFocusRequester),
                shape = tvButtonShape(),
                colors = if (isDestructive) tvDialogDestructiveButtonColors() else tvPrimaryButtonColors(),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun TvRoot(
    repository: TvheadendRepository,
    playbackEngine: PlaybackEngine,
    appInfo: AppBuildInfo = AppBuildInfo(versionName = "", versionCode = 0, buildSha = ""),
) {
    TvMaterialTheme(
        colorScheme = tvDarkColorScheme(
            primary = MuxGridColors.Accent,
            onPrimary = MuxGridColors.OnAccent,
            background = MuxGridColors.CanvasTv,
            onBackground = MuxGridColors.TextPrimary,
            surface = MuxGridColors.CardSurface,
            onSurface = MuxGridColors.TextPrimary,
            surfaceVariant = MuxGridColors.CardSurfaceElevated,
            onSurfaceVariant = MuxGridColors.TextSecondary,
        ),
    ) {
        TvContent(
            repository = repository,
            playbackEngine = playbackEngine,
            appInfo = appInfo,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvContent(
    repository: TvheadendRepository,
    playbackEngine: PlaybackEngine,
    appInfo: AppBuildInfo,
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
                .background(MuxGridColors.CanvasTv),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MuxGridColors.AccentSoft)
        }
        return
    }

    var route by remember(currentProfile.isConfigured) {
        mutableStateOf(if (currentProfile.isConfigured) TvRoute.MAIN else TvRoute.ONBOARDING)
    }
    var activeTab by remember { mutableStateOf(currentStartScreen.tab) }
    var activeFilter by remember { mutableStateOf(currentStartScreen.liveFilter) }

    val filteredChannels = remember(channels, activeFilter, favorites) {
        filterChannels(channels, activeFilter, favorites)
    }

    val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L

    // Hoisted scroll states & non-reactive focus memory survive MAIN <-> PLAYER and MAIN <-> ONBOARDING
    val liveGridState = rememberLazyGridState()
    val guideListState = rememberLazyListState()
    var guideWindowStartEpochSec by remember { mutableLongStateOf(0L) }
    var hasUserAdjustedGuideWindow by remember { mutableStateOf(false) }
    LaunchedEffect(activeTab, offsetMs) {
        if (activeTab == MainTab.GUIDE && !hasUserAdjustedGuideWindow) {
            guideWindowStartEpochSec = floorToHalfHour((System.currentTimeMillis() + offsetMs) / 1000L)
        }
    }
    val recordingsListState = rememberLazyListState()
    val recordingShelfStates = remember { mutableMapOf<String, LazyListState>() }

    val focusMemory = remember { TvFocusMemory() }
    var hasDeliveredInitialContentFocus by remember { mutableStateOf(false) }
    var contentFocusRequestNonce by remember { mutableIntStateOf(0) }
    var isGuideSearchOpen by remember { mutableStateOf(false) }

    when (route) {
        TvRoute.ONBOARDING -> {
            BackHandler(enabled = currentProfile.isConfigured) {
                route = TvRoute.MAIN
                contentFocusRequestNonce++
            }
            OnboardingServerScreen(
                initialProfile = currentProfile,
                isTv = true,
                canCancel = currentProfile.isConfigured,
                onTestConnection = { repository.testConnection(it) },
                onSaveAndConnect = { updated ->
                    repository.saveAndConnect(updated)
                    route = TvRoute.MAIN
                    contentFocusRequestNonce++
                },
                onCancel = {
                    route = TvRoute.MAIN
                    contentFocusRequestNonce++
                },
            )
        }
        TvRoute.MAIN -> {
            LaunchedEffect(activeTab) {
                if (activeTab != MainTab.GUIDE) {
                    isGuideSearchOpen = false
                }
            }
            if (!hasDeliveredInitialContentFocus) {
                when (activeTab) {
                    MainTab.LIVE, MainTab.GUIDE -> {
                        LaunchedEffect(channels.isNotEmpty()) {
                            if (channels.isNotEmpty()) {
                                hasDeliveredInitialContentFocus = true
                                contentFocusRequestNonce++
                            }
                        }
                    }
                    MainTab.RECORDINGS -> {
                        val dvrEntries by repository.dvrEntries.collectAsState()
                        LaunchedEffect(dvrEntries.isNotEmpty()) {
                            if (dvrEntries.isNotEmpty()) {
                                hasDeliveredInitialContentFocus = true
                                contentFocusRequestNonce++
                            }
                        }
                    }
                    MainTab.SETTINGS -> {
                        LaunchedEffect(Unit) {
                            hasDeliveredInitialContentFocus = true
                            contentFocusRequestNonce++
                        }
                    }
                }
            }
            var railHasFocus by remember { mutableStateOf(false) }
            BackHandler(enabled = activeTab != currentStartScreen.tab && !isGuideSearchOpen) {
                activeTab = currentStartScreen.tab
                contentFocusRequestNonce++
            }
            BackHandler(enabled = activeTab == currentStartScreen.tab && railHasFocus) {
                contentFocusRequestNonce++
            }
            var isRefreshing by remember { mutableStateOf(false) }
            val statusDotColor = connectionStatusDotColor(connectionState)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.CanvasTv),
            ) {
                // Keep content width fixed so expanding the nav rail overlays instead of
                // resizing and re-measuring the entire channel grid / EPG timeline every frame.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = TV_RAIL_COLLAPSED_WIDTH + 20.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)
                        .focusGroup()
                        .focusRestorer(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = activeTab.label(),
                            style = MuxGridType.ScreenTitle,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (activeTab == MainTab.GUIDE) {
                                TvIconButton(
                                    onClick = { isGuideSearchOpen = !isGuideSearchOpen },
                                    containerColor = if (isGuideSearchOpen) MuxGridColors.CardSurfaceElevated else Color.Transparent,
                                    contentColor = if (isGuideSearchOpen) MuxGridColors.AccentSoft else MuxGridColors.TextSecondary,
                                ) {
                                    Icon(
                                        imageVector = if (isGuideSearchOpen) MuxGridIcons.Close else MuxGridIcons.Search,
                                        contentDescription = if (isGuideSearchOpen) "Close search" else "Search programs",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            TvIconButton(
                                onClick = {
                                    if (!isRefreshing) {
                                        scope.launch {
                                            isRefreshing = true
                                            try {
                                                repository.refresh()
                                            } finally {
                                                isRefreshing = false
                                            }
                                        }
                                    }
                                },
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MuxGridColors.AccentSoft,
                                    )
                                } else {
                                    Icon(
                                        imageVector = MuxGridIcons.Refresh,
                                        contentDescription = "Refresh",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            MainTab.LIVE -> {
                                val tags by repository.tags.collectAsState()
                                val nowNextMap by repository.nowNextByChannel.collectAsState()
                                val nowEpochSec = rememberNowEpochSec(offsetMs)
                                TvLiveChannelsTab(
                                    connectionState = connectionState,
                                    channels = filteredChannels,
                                    hasAnyChannels = channels.isNotEmpty(),
                                    filters = remember(tags) { buildLiveFilters(tags) },
                                    activeFilter = activeFilter,
                                    favorites = favorites,
                                    nowNextMap = nowNextMap,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    gridState = liveGridState,
                                    focusMemory = focusMemory,
                                    focusRequestNonce = contentFocusRequestNonce,
                                    onSelectFilter = { activeFilter = it },
                                    onChannelClick = { channel ->
                                        focusMemory.liveChannelId = channel.id
                                        hasDeliveredInitialContentFocus = true
                                        playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                        route = TvRoute.PLAYER
                                    },
                                    onToggleFavorite = { channel ->
                                        scope.launch { repository.toggleFavorite(channel.id) }
                                    },
                                )
                            }
                            MainTab.GUIDE -> {
                                val epgByChannel by repository.epgByChannel.collectAsState()
                                val dvrEntries by repository.dvrEntries.collectAsState()
                                val nowEpochSec = rememberNowEpochSec(offsetMs)
                                TvGuideScreen(
                                    channels = channels,
                                    connectionState = connectionState,
                                    epgByChannel = epgByChannel,
                                    dvrEntries = dvrEntries,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    listState = guideListState,
                                    windowStartEpochSec = guideWindowStartEpochSec.takeIf { it > 0L } ?: floorToHalfHour(nowEpochSec),
                                    onWindowStartChange = {
                                        hasUserAdjustedGuideWindow = true
                                        guideWindowStartEpochSec = it
                                    },
                                    focusMemory = focusMemory,
                                    focusRequestNonce = contentFocusRequestNonce,
                                    searchOpen = isGuideSearchOpen,
                                    onCloseSearch = { isGuideSearchOpen = false },
                                    onWatchChannel = { channel ->
                                        focusMemory.guideTarget = TvGuideFocusTarget(
                                            channelId = channel.id,
                                            eventId = focusMemory.guideTarget?.takeIf { it.channelId == channel.id }?.eventId,
                                        )
                                        isGuideSearchOpen = false
                                        playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                        route = TvRoute.PLAYER
                                    },
                                    onPlayRecording = { entry, channel, startSec ->
                                        channel?.let { ch ->
                                            focusMemory.guideTarget = TvGuideFocusTarget(
                                                channelId = ch.id,
                                                eventId = focusMemory.guideTarget?.takeIf { it.channelId == ch.id }?.eventId,
                                            )
                                        }
                                        isGuideSearchOpen = false
                                        playbackEngine.playDvrEntry(entry, channel, repository.hwDecPreset.value, startSec)
                                        route = TvRoute.PLAYER
                                    },
                                    onScheduleRecording = { eventId -> repository.scheduleRecording(eventId) },
                                    onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                    onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                                )
                            }
                            MainTab.RECORDINGS -> {
                                val dvrEntries by repository.dvrEntries.collectAsState()
                                val nowEpochSec = rememberNowEpochSec(offsetMs)
                                TvRecordingsScreen(
                                    dvrEntries = dvrEntries,
                                    channels = channels,
                                    connectionState = connectionState,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    listState = recordingsListState,
                                    shelfStateFor = { key -> recordingShelfStates.getOrPut(key) { LazyListState() } },
                                    focusMemory = focusMemory,
                                    focusRequestNonce = contentFocusRequestNonce,
                                    onWatchLiveChannel = { channel ->
                                        playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                        route = TvRoute.PLAYER
                                    },
                                    onPlayRecording = { entry, channel, startSec ->
                                        playbackEngine.playDvrEntry(entry, channel, repository.hwDecPreset.value, startSec)
                                        route = TvRoute.PLAYER
                                    },
                                    onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                    onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                                    onDeleteRecording = { dvrId -> repository.deleteRecording(dvrId) },
                                    onToggleWatched = { dvrId, watched -> repository.markRecordingWatched(dvrId, watched) },
                                )
                            }
                            MainTab.SETTINGS -> {
                                val themeMode by repository.themeMode.collectAsState()
                                val hwDecPreset by repository.hwDecPreset.collectAsState()
                                val timeshiftWindow by repository.timeshiftWindow.collectAsState()
                                TvSettingsTab(
                                    profile = currentProfile,
                                    connectionState = connectionState,
                                    startScreen = currentStartScreen,
                                    themeMode = themeMode,
                                    hwDecPreset = hwDecPreset,
                                    timeshiftWindow = timeshiftWindow,
                                    appInfo = appInfo,
                                    focusMemory = focusMemory,
                                    focusRequestNonce = contentFocusRequestNonce,
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
                                    onRetryConnect = {
                                        focusMemory.settingsTarget = TvSettingsFocusTarget.PRIMARY
                                        repository.reconnect()
                                    },
                                    onOpenServerSetup = {
                                        focusMemory.settingsTarget = TvSettingsFocusTarget.EDIT_SERVER
                                        route = TvRoute.ONBOARDING
                                    },
                                )
                            }
                        }
                    }
                }

                TvNavigationRail(
                    activeTab = activeTab,
                    expanded = railHasFocus,
                    settingsStatusDotColor = statusDotColor,
                    onRailFocusChanged = { railHasFocus = it },
                    onSelectTab = { tab ->
                        hasDeliveredInitialContentFocus = true
                        activeTab = tab
                        contentFocusRequestNonce++
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .zIndex(1f),
                )
            }
        }
        TvRoute.PLAYER -> {
            val playbackState by playbackEngine.state.collectAsState()
            val nowNextMap by repository.nowNextByChannel.collectAsState()
            val hwDecPreset by repository.hwDecPreset.collectAsState()
            val nowEpochSec = rememberNowEpochSec(offsetMs)

            val zapList = filteredChannels.ifEmpty { channels }
            val activeChannel = channels.firstOrNull { it.id == playbackState.channelId }
            val activeNowNext = activeChannel?.let { nowNextMap[it.id] }

            MuxGridTheme(isTv = true, darkTheme = true) {
                TvPlayerScreen(
                    playbackEngine = playbackEngine,
                    playbackState = playbackState,
                    activeChannel = activeChannel,
                    nowNext = activeNowNext,
                    channels = zapList,
                    nowNextMap = nowNextMap,
                    nowEpochSec = nowEpochSec,
                    hwDecPreset = hwDecPreset,
                    imageLoader = repository.imageLoader,
                    canZap = zapList.size > 1,
                    onSelectChannel = { channel ->
                        playbackEngine.playChannel(channel, hwDecPreset)
                    },
                    onZapDelta = { delta ->
                        if (zapList.isNotEmpty()) {
                            val idx = zapList.indexOfFirst { it.id == playbackState.channelId }
                            val nextIdx = if (idx < 0) 0 else (idx + delta + zapList.size) % zapList.size
                            playbackEngine.playChannel(zapList[nextIdx], hwDecPreset)
                        }
                    },
                    onSelectPreset = { preset ->
                        scope.launch { repository.setHwDecPreset(preset) }
                        playbackEngine.applyPreset(preset)
                    },
                    onSelectAspectMode = { mode ->
                        playbackEngine.setVideoAspectMode(mode)
                    },
                    onExit = {
                        val finalChannelId = playbackState.channelId
                        val finalDvrId = playbackState.dvrEntryId
                        when (activeTab) {
                            MainTab.LIVE -> if (finalChannelId != null) {
                                focusMemory.liveChannelId = finalChannelId
                            }
                            MainTab.GUIDE -> if (finalChannelId != null && finalChannelId != focusMemory.guideTarget?.channelId) {
                                focusMemory.guideTarget = TvGuideFocusTarget(channelId = finalChannelId)
                            }
                            MainTab.RECORDINGS -> if (finalDvrId != null && finalDvrId != focusMemory.recordingTarget?.entryId) {
                                focusMemory.recordingTarget = TvRecordingFocusTarget(shelfKey = "", entryId = finalDvrId)
                            }
                            MainTab.SETTINGS -> Unit
                        }
                        playbackEngine.stop()
                        route = TvRoute.MAIN
                        contentFocusRequestNonce++
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvNavigationRail(
    activeTab: MainTab,
    expanded: Boolean,
    settingsStatusDotColor: Color?,
    onRailFocusChanged: (Boolean) -> Unit,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railWidth by animateDpAsState(
        targetValue = if (expanded) TV_RAIL_EXPANDED_WIDTH else TV_RAIL_COLLAPSED_WIDTH,
        animationSpec = tween(durationMillis = MuxGridDurations.RailExpand),
        label = "tvRailWidth",
    )
    val activeFocusRequester = remember { FocusRequester() }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(MuxGridColors.CardSurfaceAlt)
                .onFocusChanged { onRailFocusChanged(it.hasFocus) }
                .focusGroup()
                .focusRestorer(activeFocusRequester)
                .padding(vertical = 20.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(MainTab.LIVE, MainTab.GUIDE, MainTab.RECORDINGS).forEach { tab ->
                    val selected = tab == activeTab
                    val itemModifier = if (selected) Modifier.focusRequester(activeFocusRequester) else Modifier
                    TvRailItem(
                        icon = tab.icon(selected),
                        label = tab.label(),
                        expanded = expanded,
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        modifier = itemModifier,
                    )
                }
            }

            val settingsSelected = activeTab == MainTab.SETTINGS
            TvRailItem(
                icon = MainTab.SETTINGS.icon(settingsSelected),
                label = MainTab.SETTINGS.label(),
                expanded = expanded,
                selected = settingsSelected,
                statusDotColor = settingsStatusDotColor,
                onClick = { onSelectTab(MainTab.SETTINGS) },
                modifier = if (settingsSelected) Modifier.focusRequester(activeFocusRequester) else Modifier,
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
private fun TvRailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusDotColor: Color? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Sm),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MuxGridColors.CardSurfaceElevated else Color.Transparent,
            contentColor = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextMuted,
            focusedContainerColor = MuxGridColors.FocusBorder,
            focusedContentColor = MuxGridColors.OnFocusBorder,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder),
                shape = MuxGridShapes.Sm,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(MuxGridColors.Accent),
                )
                Spacer(Modifier.width(if (expanded) 9.dp else 4.dp))
            }
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                )
                if (statusDotColor != null) {
                    ConnectionStatusDot(
                        color = statusDotColor,
                        borderColor = if (selected) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurfaceAlt,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvLiveChannelsTab(
    connectionState: ConnectionState,
    channels: List<Channel>,
    hasAnyChannels: Boolean,
    filters: List<LiveFilter>,
    activeFilter: LiveFilter,
    favorites: Set<Long>,
    nowNextMap: Map<Long, NowNextProgram>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    gridState: LazyGridState,
    focusMemory: TvFocusMemory,
    focusRequestNonce: Int,
    onSelectFilter: (LiveFilter) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    val activeFilterFocusRequester = remember { FocusRequester() }
    val channelFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    LaunchedEffect(focusRequestNonce) {
        if (focusRequestNonce == 0) return@LaunchedEffect
        if (channels.isEmpty()) {
            runCatching { activeFilterFocusRequester.requestFocus() }
            return@LaunchedEffect
        }
        val targetIndex = focusMemory.liveChannelId
            ?.let { id -> channels.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val resolvedChannelId = channels.getOrNull(targetIndex)?.id ?: return@LaunchedEffect
        val isVisible = gridState.layoutInfo.visibleItemsInfo.any { it.key == resolvedChannelId }
        if (!isVisible) {
            runCatching { gridState.scrollToItem(targetIndex) }
        }
        repeat(2) {
            withFrameNanos { }
            val requester = channelFocusRequesters[resolvedChannelId]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
        val firstVisibleId = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Long
        val fallbackRequester = firstVisibleId?.let { channelFocusRequesters[it] }
        if (fallbackRequester != null) {
            runCatching { fallbackRequester.requestFocus() }
        } else {
            runCatching { activeFilterFocusRequester.requestFocus() }
        }
    }

    val chipShape = FilterChipDefaults.shape(shape = MuxGridShapes.Sm)
    val chipColors = FilterChipDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = MuxGridColors.TextMuted,
        selectedContainerColor = MuxGridColors.Accent,
        selectedContentColor = MuxGridColors.OnAccent,
        focusedContainerColor = MuxGridColors.FocusBorder,
        focusedContentColor = MuxGridColors.OnFocusBorder,
        focusedSelectedContainerColor = MuxGridColors.FocusBorder,
        focusedSelectedContentColor = MuxGridColors.OnFocusBorder,
    )
    val chipBorder = FilterChipDefaults.border(
        border = Border.None,
        selectedBorder = Border.None,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .focusRestorer(activeFilterFocusRequester)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { idx, filter ->
                val selected = filter == activeFilter
                val filterIcon = filter.icon()
                val isFallbackFilterTarget = selected || (idx == 0 && filters.none { it == activeFilter })
                FilterChip(
                    selected = selected,
                    onClick = { onSelectFilter(filter) },
                    modifier = if (isFallbackFilterTarget) {
                        Modifier.focusRequester(activeFilterFocusRequester)
                    } else {
                        Modifier
                    },
                    shape = chipShape,
                    leadingIcon = filterIcon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    },
                    colors = chipColors,
                    border = chipBorder,
                ) {
                    Text(
                        text = filter.label(),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
        MuxGridDivider(modifier = Modifier.padding(horizontal = 4.dp))

        Spacer(Modifier.height(6.dp))

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val emptyText = when {
                    hasAnyChannels -> "No channels match this filter"
                    connectionState is ConnectionState.Connecting ||
                        connectionState is ConnectionState.Syncing -> "Syncing channels from Tvheadend…"
                    connectionState is ConnectionState.Disconnected -> "Connecting to Tvheadend…"
                    connectionState is ConnectionState.Error -> connectionState.message
                    else -> "No channels synced yet"
                }
                Text(emptyText, style = MuxGridType.CardSubtitle, fontSize = 16.sp)
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = channels,
                    key = { it.id },
                    contentType = { "channel_card" },
                ) { channel ->
                    val cardFocusRequester = remember(channel.id) { FocusRequester() }
                    DisposableEffect(channel.id) {
                        channelFocusRequesters[channel.id] = cardFocusRequester
                        onDispose {
                            if (channelFocusRequesters[channel.id] === cardFocusRequester) {
                                channelFocusRequesters.remove(channel.id)
                            }
                        }
                    }
                    TvChannelCard(
                        channel = channel,
                        nowNext = nowNextMap[channel.id],
                        nowEpochSec = nowEpochSec,
                        isFavorite = channel.id in favorites,
                        imageLoader = imageLoader,
                        focusRequester = cardFocusRequester,
                        onFocused = { focusMemory.liveChannelId = channel.id },
                        onChannelClick = onChannelClick,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvChannelCard(
    channel: Channel,
    nowNext: NowNextProgram?,
    nowEpochSec: Long,
    isFavorite: Boolean,
    imageLoader: ImageLoader,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val piconModel = remember(channel.id, channel.icon) {
        channel.icon?.let { PiconModel(channel.id, it) }
    }
    val nextProgram = nowNext?.next
    val nextLabel = remember(nextProgram?.id, nextProgram?.startEpochSec, nextProgram?.title) {
        nextProgram?.let { "Next ${formatEpochHm(it.startEpochSec)} · ${it.title}" } ?: ""
    }
    val handleClick = remember(channel, onChannelClick) { { onChannelClick(channel) } }
    val handleLongClick = remember(channel, onToggleFavorite) { { onToggleFavorite(channel) } }
    val interactionSource = remember { MutableInteractionSource() }
    val now = nowNext?.now
    val cardDescription = remember(channel.formattedNumber, channel.name, now?.title) {
        if (now != null) "${channel.formattedNumber} ${channel.name}, ${now.title}" else "${channel.formattedNumber} ${channel.name}"
    }

    // Lightweight focusable card avoids tv.material3.Surface's SurfaceGlowNode shadow render targets
    // and multi-frame scale animations, while merging child semantics into one accessibility node.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = if (isFocused) 1.015f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(MuxGridShapes.Card)
            .background(if (isFocused) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurface)
            .then(
                if (isFocused) {
                    Modifier.border(MuxGridBorders.FocusedCard, MuxGridColors.FocusBorder, MuxGridShapes.Card)
                } else {
                    Modifier
                },
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                val focused = state.isFocused
                if (isFocused != focused) {
                    isFocused = focused
                }
                if (focused) {
                    onFocused()
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = handleClick,
                onLongClick = handleLongClick,
            )
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = cardDescription
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelPiconBadge(
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                piconModel = piconModel,
                imageLoader = imageLoader,
                size = 48.dp,
            )
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
                    if (isFavorite) {
                        Icon(
                            imageVector = MuxGridIcons.StarFilled,
                            contentDescription = null,
                            tint = MuxGridColors.AmberWarn,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = channel.name,
                    style = MuxGridType.CardTitle,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (now != null) {
            Text(
                text = now.title,
                style = MuxGridType.CardSubtitle,
                color = MuxGridColors.TextPrimary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LiveProgressBar(
                progress = nowNext.progressFraction(nowEpochSec),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = nextLabel,
                    style = MuxGridType.TabularSmall,
                    color = MuxGridColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                nowNext.remainingMinutes(nowEpochSec)?.let { mins ->
                    Spacer(Modifier.width(8.dp))
                    Text("${mins}m", style = MuxGridType.TabularSmall)
                }
            }
        } else {
            Text(
                text = if (channel.isRadio) "Live Radio" else "Live Stream",
                style = MuxGridType.CardSubtitle,
                color = MuxGridColors.TextMuted,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvSettingsTab(
    profile: ServerProfile,
    connectionState: ConnectionState,
    startScreen: StartScreen,
    themeMode: ThemeMode,
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    appInfo: AppBuildInfo,
    focusMemory: TvFocusMemory,
    focusRequestNonce: Int,
    onSelectStartScreen: (StartScreen) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
    onRetryConnect: () -> Unit,
    onOpenServerSetup: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugReport by remember { mutableStateOf<String?>(null) }
    val isServerError = connectionState is ConnectionState.Error
    val canRetryConnect = isServerError || connectionState is ConnectionState.Disconnected
    val reconnectFocusRequester = remember { FocusRequester() }
    val editServerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequestNonce) {
        if (focusRequestNonce == 0) return@LaunchedEffect
        withFrameNanos { }
        val primary = when {
            focusMemory.settingsTarget == TvSettingsFocusTarget.EDIT_SERVER -> editServerFocusRequester
            canRetryConnect -> reconnectFocusRequester
            else -> editServerFocusRequester
        }
        if (runCatching { primary.requestFocus() }.isFailure) {
            runCatching { editServerFocusRequester.requestFocus() }
        }
    }

    val onOpenGithub: () -> Unit = {
        runCatching {
            uriHandler.openUri(appInfo.githubUrl)
        }.onFailure {
            Toast.makeText(context, appInfo.githubUrl, Toast.LENGTH_LONG).show()
        }
    }
    val onOpenDebugInfo: () -> Unit = {
        scope.launch {
            debugReport = withContext(Dispatchers.IO) {
                formatDebugInfoReport(
                    collectDebugEnvironmentSnapshot(
                        context = context,
                        appInfo = appInfo,
                        isTv = true,
                        connectionState = connectionState,
                        hwDecPreset = hwDecPreset,
                        timeshiftWindow = timeshiftWindow,
                        themeMode = themeMode,
                    ),
                )
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTwoColumns = maxWidth >= 680.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 28.dp),
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
                        TvServerSettingsCard(
                            profile = profile,
                            connectionState = connectionState,
                            isServerError = isServerError,
                            canRetryConnect = canRetryConnect,
                            reconnectFocusRequester = reconnectFocusRequester,
                            editServerFocusRequester = editServerFocusRequester,
                            onRetryConnect = onRetryConnect,
                            onOpenServerSetup = onOpenServerSetup,
                        )
                        TvGeneralSettingsCard(
                            startScreen = startScreen,
                            themeMode = themeMode,
                            onSelectStartScreen = onSelectStartScreen,
                            onSelectThemeMode = onSelectThemeMode,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TvDecoderTimeshiftSettingsCard(
                            hwDecPreset = hwDecPreset,
                            timeshiftWindow = timeshiftWindow,
                            onSelectPreset = onSelectPreset,
                            onSelectTimeshiftWindow = onSelectTimeshiftWindow,
                        )
                        TvAppInfoSettingsCard(
                            appInfo = appInfo,
                            onOpenGithub = onOpenGithub,
                            onOpenDebugInfo = onOpenDebugInfo,
                        )
                    }
                }
            } else {
                TvServerSettingsCard(
                    profile = profile,
                    connectionState = connectionState,
                    isServerError = isServerError,
                    canRetryConnect = canRetryConnect,
                    reconnectFocusRequester = reconnectFocusRequester,
                    editServerFocusRequester = editServerFocusRequester,
                    onRetryConnect = onRetryConnect,
                    onOpenServerSetup = onOpenServerSetup,
                )
                TvGeneralSettingsCard(
                    startScreen = startScreen,
                    themeMode = themeMode,
                    onSelectStartScreen = onSelectStartScreen,
                    onSelectThemeMode = onSelectThemeMode,
                )
                TvDecoderTimeshiftSettingsCard(
                    hwDecPreset = hwDecPreset,
                    timeshiftWindow = timeshiftWindow,
                    onSelectPreset = onSelectPreset,
                    onSelectTimeshiftWindow = onSelectTimeshiftWindow,
                )
                TvAppInfoSettingsCard(
                    appInfo = appInfo,
                    onOpenGithub = onOpenGithub,
                    onOpenDebugInfo = onOpenDebugInfo,
                )
            }
        }
    }

    debugReport?.let { report ->
        val copyFocusRequester = remember { FocusRequester() }
        LaunchedEffect(report) {
            runCatching { copyFocusRequester.requestFocus() }
        }
        TvModalDialog(
            onDismissRequest = { debugReport = null },
            minWidth = 500.dp,
            maxWidth = 700.dp,
        ) {
            Text(
                text = "Debug Info",
                style = MuxGridType.Title,
                fontSize = 18.sp,
                color = MuxGridColors.TextPrimary,
            )
            Text(
                text = "Non-personal hardware and software environment for issue reports.",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
                color = MuxGridColors.TextSecondary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Sm)
                    .background(MuxGridColors.CardSurface)
                    .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Sm)
                    .padding(12.dp),
            ) {
                Text(
                    text = report,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MuxGridColors.TextPrimary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                Button(
                    onClick = { debugReport = null },
                    shape = tvButtonShape(),
                    colors = tvDialogSecondaryButtonColors(),
                ) {
                    Text("Close", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = "MuxGrid Debug Info",
                            text = report,
                            toastMessage = "Debug info copied",
                        )
                    },
                    modifier = Modifier.focusRequester(copyFocusRequester),
                    shape = tvButtonShape(),
                    colors = tvPrimaryButtonColors(),
                ) {
                    Text("Copy", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun TvServerSettingsCard(
    profile: ServerProfile,
    connectionState: ConnectionState,
    isServerError: Boolean,
    canRetryConnect: Boolean,
    reconnectFocusRequester: FocusRequester,
    editServerFocusRequester: FocusRequester,
    onRetryConnect: () -> Unit,
    onOpenServerSetup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .border(
                width = if (isServerError) MuxGridBorders.Hairline else MuxGridBorders.Divider,
                color = if (isServerError) MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.BorderSubtle) else MuxGridColors.HairlineBorder,
                shape = MuxGridShapes.Card,
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "TVHEADEND SERVER",
            style = MuxGridType.Eyebrow,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = serverHeadline(profile, connectionState),
                    style = MuxGridType.TabularNumber,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = connectionStatusText(profile, connectionState),
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
                    TvIconButton(
                        onClick = onRetryConnect,
                        modifier = Modifier.focusRequester(reconnectFocusRequester),
                        size = 44.dp,
                        containerColor = MuxGridColors.CardSurfaceElevated,
                        contentColor = MuxGridColors.AccentSoft,
                    ) {
                        Icon(
                            imageVector = MuxGridIcons.Refresh,
                            contentDescription = "Reconnect",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                TvIconButton(
                    onClick = onOpenServerSetup,
                    modifier = Modifier.focusRequester(editServerFocusRequester),
                    size = 44.dp,
                    containerColor = MuxGridColors.CardSurfaceElevated,
                    contentColor = MuxGridColors.TextPrimary,
                ) {
                    Icon(
                        imageVector = MuxGridIcons.Edit,
                        contentDescription = "Edit Server",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvGeneralSettingsCard(
    startScreen: StartScreen,
    themeMode: ThemeMode,
    onSelectStartScreen: (StartScreen) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "GENERAL",
            style = MuxGridType.Eyebrow,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Start Screen",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StartScreen.entries.forEach { option ->
                    TvUnderlineOption(
                        label = option.label,
                        selected = startScreen == option,
                        onClick = { onSelectStartScreen(option) },
                    )
                }
            }
            Text(
                text = startScreen.description,
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
            )
        }

        MuxGridDivider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Theme",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeMode.entries.forEach { option ->
                    TvUnderlineOption(
                        label = option.label,
                        selected = themeMode == option,
                        onClick = { onSelectThemeMode(option) },
                    )
                }
            }
            Text(
                text = themeMode.description,
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvDecoderTimeshiftSettingsCard(
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "VIDEO DECODER & TIMESHIFT",
            style = MuxGridType.Eyebrow,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Hardware Decode Preset",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf(
                    HwDecPreset.MEDIACODEC_ZERO_COPY to "Zero-Copy (TV Default)",
                    HwDecPreset.MEDIACODEC_COPY_BWDIF to "Copy + bwdif",
                ).forEach { (preset, label) ->
                    TvUnderlineOption(
                        label = label,
                        selected = hwDecPreset == preset,
                        onClick = { onSelectPreset(preset) },
                    )
                }
            }
        }

        MuxGridDivider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Live Timeshift Cache Limit",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TimeshiftWindow.entries.forEach { option ->
                    TvUnderlineOption(
                        label = option.shortLabel,
                        selected = timeshiftWindow == option,
                        onClick = { onSelectTimeshiftWindow(option) },
                    )
                }
            }
            Text(
                text = "${timeshiftWindow.description} · split pause/rewind · applies on next tune",
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
            )
        }
    }
}

@Composable
private fun TvAppInfoSettingsCard(
    appInfo: AppBuildInfo,
    onOpenGithub: () -> Unit,
    onOpenDebugInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "APP INFO",
            style = MuxGridType.Eyebrow,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Version",
                    style = MuxGridType.CardSubtitle,
                    fontSize = 12.5.sp,
                )
                Text(
                    text = appInfo.versionDisplay,
                    style = MuxGridType.TabularNumber,
                    fontSize = 14.5.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Build SHA",
                    style = MuxGridType.CardSubtitle,
                    fontSize = 12.5.sp,
                )
                Text(
                    text = appInfo.shortSha,
                    style = MuxGridType.TabularNumber,
                    fontSize = 14.5.sp,
                )
            }
        }

        MuxGridDivider()

        TvSettingsActionRow(
            title = "GitHub",
            subtitle = appInfo.githubDisplayUrl,
            contentDescription = "Open GitHub",
            onClick = onOpenGithub,
        )

        TvSettingsActionRow(
            title = "Debug Info",
            subtitle = "Device, OS, and playback environment",
            contentDescription = "Show Debug Info",
            onClick = onOpenDebugInfo,
        )
    }
}

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MuxGridColors.TextSecondary,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Sm),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = MuxGridColors.FocusBorder,
            focusedContentColor = MuxGridColors.OnFocusBorder,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder),
                shape = MuxGridShapes.Sm,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun TvSettingsActionRow(
    title: String,
    subtitle: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Sm),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MuxGridColors.CardSurfaceElevated,
            contentColor = MuxGridColors.TextPrimary,
            focusedContainerColor = MuxGridColors.FocusBorder,
            focusedContentColor = MuxGridColors.OnFocusBorder,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder),
                shape = MuxGridShapes.Sm,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = LocalContentColor.current.copy(alpha = MuxGridAlphas.SecondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = MuxGridIcons.ChevronRight,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TvUnderlineOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Sm),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextMuted,
            focusedContainerColor = MuxGridColors.FocusBorder,
            focusedContentColor = MuxGridColors.OnFocusBorder,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (selected) MuxGridColors.Accent else Color.Transparent),
            )
        }
    }
}
