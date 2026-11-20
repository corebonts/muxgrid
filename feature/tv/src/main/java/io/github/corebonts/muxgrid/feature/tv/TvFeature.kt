package io.github.corebonts.muxgrid.feature.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import coil3.ImageLoader
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
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TvRoute {
    ONBOARDING,
    MAIN,
    PLAYER,
}

@Composable
internal fun tvPrimaryButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.AccentBlue,
    contentColor = Color.White,
    focusedContainerColor = MuxGridColors.FocusBorder,
    focusedContentColor = MuxGridColors.CanvasTv,
)

@Composable
internal fun tvSecondaryButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.CardSurface,
    contentColor = MuxGridColors.TextPrimary,
    focusedContainerColor = MuxGridColors.FocusBorder,
    focusedContentColor = MuxGridColors.CanvasTv,
)

@Composable
fun TvMvpRoot(
    repository: TvheadendRepository,
    playbackEngine: PlaybackEngine,
) {
    TvMaterialTheme(
        colorScheme = tvDarkColorScheme(
            primary = MuxGridColors.AccentBlue,
            onPrimary = MuxGridColors.TextPrimary,
            background = MuxGridColors.CanvasTv,
            onBackground = MuxGridColors.TextPrimary,
            surface = MuxGridColors.CardSurface,
            onSurface = MuxGridColors.TextPrimary,
            surfaceVariant = MuxGridColors.CardSurfaceElevated,
            onSurfaceVariant = MuxGridColors.TextSecondary,
        ),
    ) {
        TvMvpContent(repository = repository, playbackEngine = playbackEngine)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvMvpContent(
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
                .background(MuxGridColors.CanvasTv),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MuxGridColors.AccentBlueLight)
        }
        return
    }

    var route by remember(currentProfile.isConfigured) {
        mutableStateOf(if (currentProfile.isConfigured) TvRoute.MAIN else TvRoute.ONBOARDING)
    }
    var activeTab by remember { mutableStateOf(MainTab.LIVE) }
    var activeFilter by remember { mutableStateOf<LiveFilter>(LiveFilter.All) }
    var settingsOpen by remember { mutableStateOf(false) }

    val filteredChannels = remember(channels, activeFilter, favorites) {
        filterChannels(channels, activeFilter, favorites)
    }

    val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
    val nowEpochSec = (System.currentTimeMillis() + offsetMs) / 1000L

    when (route) {
        TvRoute.ONBOARDING -> {
            BackHandler(enabled = currentProfile.isConfigured) {
                route = TvRoute.MAIN
            }
            OnboardingServerScreen(
                initialProfile = currentProfile,
                isTv = true,
                canCancel = currentProfile.isConfigured,
                onTestConnection = { repository.testConnection(it) },
                onSaveAndConnect = { updated ->
                    repository.saveAndConnect(updated)
                    route = TvRoute.MAIN
                },
                onCancel = { route = TvRoute.MAIN },
            )
        }
        TvRoute.MAIN -> {
            BackHandler(enabled = activeTab != MainTab.LIVE) {
                activeTab = MainTab.LIVE
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            var visibleError by remember { mutableStateOf<ConnectionState.Error?>(null) }
            var railHasFocus by remember { mutableStateOf(false) }
            val railFocusRequester = remember { FocusRequester() }
            val contentFocusRequester = remember { FocusRequester() }

            LaunchedEffect(connectionState, lifecycleOwner) {
                visibleError = null
                val connErr = connectionState as? ConnectionState.Error
                if (connErr != null) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        if (!connErr.isAuthError) {
                            delay(1_200L)
                        }
                        visibleError = connErr
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.CanvasTv)
                    .padding(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
            ) {
                TvNavigationRail(
                    activeTab = activeTab,
                    expanded = railHasFocus,
                    railFocusRequester = railFocusRequester,
                    onRailFocusChanged = { railHasFocus = it },
                    onSelectTab = { activeTab = it },
                    onOpenSettings = { settingsOpen = true },
                )

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusGroup()
                        .focusRestorer(contentFocusRequester),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = when (activeTab) {
                                    MainTab.LIVE -> "Live TV & Radio"
                                    MainTab.GUIDE -> "Program Guide"
                                    MainTab.RECORDINGS -> "DVR Recordings"
                                },
                                style = MuxGridType.Title,
                                fontSize = 22.sp,
                            )
                        }
                        ConnectionStatusBadge(state = connectionState)
                    }

                    visibleError?.let { errState ->
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(MuxGridShapes.Row)
                                .background(MuxGridColors.CoralLive.copy(alpha = 0.16f))
                                .border(MuxGridBorders.Hairline, MuxGridColors.CoralLive.copy(alpha = 0.45f), MuxGridShapes.Row)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(errState.message, color = MuxGridColors.TextPrimary, fontSize = 14.sp)
                            Button(
                                onClick = if (errState.isAuthError) ({ route = TvRoute.ONBOARDING }) else ({ repository.reconnect() }),
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text(if (errState.isAuthError) "Edit Server" else "Retry")
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            MainTab.LIVE -> {
                                TvLiveChannelsTab(
                                    connectionState = connectionState,
                                    channels = filteredChannels,
                                    filters = remember(tags) { buildLiveFilters(tags) },
                                    activeFilter = activeFilter,
                                    favorites = favorites,
                                    nowNextMap = nowNextMap,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    initialContentFocusRequester = contentFocusRequester,
                                    onSelectFilter = { activeFilter = it },
                                    onChannelClick = { channel ->
                                        playbackEngine.playChannel(channel, hwDecPreset)
                                        route = TvRoute.PLAYER
                                    },
                                    onToggleFavorite = { channel ->
                                        scope.launch { repository.toggleFavorite(channel.id) }
                                    },
                                )
                            }
                            MainTab.GUIDE -> {
                                TvGuideScreen(
                                    channels = channels,
                                    epgByChannel = epgByChannel,
                                    dvrEntries = dvrEntries,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    onWatchChannel = { channel ->
                                        playbackEngine.playChannel(channel, hwDecPreset)
                                        route = TvRoute.PLAYER
                                    },
                                    onPlayRecording = { entry, channel, startSec ->
                                        playbackEngine.playDvrEntry(entry, channel, hwDecPreset, startSec)
                                        route = TvRoute.PLAYER
                                    },
                                    onScheduleRecording = { eventId -> repository.scheduleRecording(eventId) },
                                    onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                    onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                                )
                            }
                            MainTab.RECORDINGS -> {
                                TvRecordingsScreen(
                                    dvrEntries = dvrEntries,
                                    channels = channels,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    onWatchLiveChannel = { channel ->
                                        playbackEngine.playChannel(channel, hwDecPreset)
                                        route = TvRoute.PLAYER
                                    },
                                    onPlayRecording = { entry, channel, startSec ->
                                        playbackEngine.playDvrEntry(entry, channel, hwDecPreset, startSec)
                                        route = TvRoute.PLAYER
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
            }

            if (settingsOpen) {
                TvSettingsDialog(
                    profile = currentProfile,
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
                        route = TvRoute.ONBOARDING
                    },
                    onDismiss = { settingsOpen = false },
                )
            }
        }
        TvRoute.PLAYER -> {
            val zapList = filteredChannels.ifEmpty { channels }
            val activeChannel = channels.firstOrNull { it.id == playbackState.channelId }
            val activeNowNext = activeChannel?.let { nowNextMap[it.id] }

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
                    playbackEngine.stop()
                    route = TvRoute.MAIN
                },
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvNavigationRail(
    activeTab: MainTab,
    expanded: Boolean,
    railFocusRequester: FocusRequester,
    onRailFocusChanged: (Boolean) -> Unit,
    onSelectTab: (MainTab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val railWidth by animateDpAsState(targetValue = if (expanded) 190.dp else 72.dp, label = "tvRailWidth")
    val activeFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .clip(MuxGridShapes.Dialog)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Dialog)
            .onFocusChanged { onRailFocusChanged(it.hasFocus) }
            .focusGroup()
            .focusRestorer(activeFocusRequester)
            .padding(vertical = 16.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(MuxGridShapes.Control)
                        .background(MuxGridColors.AccentBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (expanded) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "MuxGrid",
                        style = MuxGridType.Title,
                        fontSize = 17.sp,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            listOf(
                Triple(MainTab.LIVE, MuxGridIcons.Live to MuxGridIcons.LiveFilled, "Live"),
                Triple(MainTab.GUIDE, MuxGridIcons.Guide to MuxGridIcons.GuideFilled, "Guide"),
                Triple(MainTab.RECORDINGS, MuxGridIcons.Recordings to MuxGridIcons.RecordingsFilled, "Recordings"),
            ).forEach { (tab, icons, label) ->
                val selected = tab == activeTab
                val itemModifier = when {
                    selected && tab == MainTab.LIVE -> Modifier.focusRequester(railFocusRequester).focusRequester(activeFocusRequester)
                    selected -> Modifier.focusRequester(activeFocusRequester)
                    else -> Modifier
                }
                TvRailItem(
                    icon = if (selected) icons.second else icons.first,
                    label = label,
                    expanded = expanded,
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    modifier = itemModifier,
                )
            }
        }

        TvRailItem(
            icon = MuxGridIcons.Settings,
            label = "Settings",
            expanded = expanded,
            selected = false,
            onClick = onOpenSettings,
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
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Row),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MuxGridColors.AccentBlue.copy(alpha = 0.25f) else Color.Transparent,
            contentColor = if (selected) MuxGridColors.OnAccent else MuxGridColors.TextSecondary,
            focusedContainerColor = MuxGridColors.FocusBorder,
            focusedContentColor = MuxGridColors.CanvasTv,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    MuxGridBorders.Hairline,
                    if (selected) MuxGridColors.AccentBlueLight.copy(alpha = 0.6f) else Color.Transparent,
                ),
                shape = MuxGridShapes.Row,
            ),
            focusedBorder = Border(
                border = BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder),
                shape = MuxGridShapes.Row,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
            )
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvLiveChannelsTab(
    connectionState: ConnectionState,
    channels: List<Channel>,
    filters: List<LiveFilter>,
    activeFilter: LiveFilter,
    favorites: Set<Long>,
    nowNextMap: Map<Long, NowNextProgram>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    initialContentFocusRequester: FocusRequester,
    onSelectFilter: (LiveFilter) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            filters.forEachIndexed { idx, filter ->
                val selected = filter == activeFilter
                val filterIcon = when (filter) {
                    LiveFilter.Tv -> MuxGridIcons.Tv
                    LiveFilter.Radio -> MuxGridIcons.Radio
                    LiveFilter.Favorites -> MuxGridIcons.StarFilled
                    else -> null
                }
                FilterChip(
                    selected = selected,
                    onClick = { onSelectFilter(filter) },
                    modifier = if (idx == 0) Modifier.focusRequester(initialContentFocusRequester) else Modifier,
                    leadingIcon = filterIcon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    colors = FilterChipDefaults.colors(
                        containerColor = MuxGridColors.CardSurface,
                        contentColor = MuxGridColors.TextPrimary,
                        selectedContainerColor = MuxGridColors.AccentBlue,
                        selectedContentColor = Color.White,
                        focusedContainerColor = MuxGridColors.FocusBorder,
                        focusedContentColor = MuxGridColors.CanvasTv,
                        focusedSelectedContainerColor = MuxGridColors.FocusBorder,
                        focusedSelectedContentColor = MuxGridColors.CanvasTv,
                    ),
                ) {
                    Text(filter.label(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val emptyText = when (connectionState) {
                    is ConnectionState.Connecting, is ConnectionState.Syncing -> "Syncing channels from Tvheadend…"
                    else -> "No channels match this filter"
                }
                Text(emptyText, style = MuxGridType.CardSubtitle, fontSize = 16.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(channels, key = { it.id }) { channel ->
                    TvChannelCard(
                        channel = channel,
                        nowNext = nowNextMap[channel.id],
                        nowEpochSec = nowEpochSec,
                        isFavorite = channel.id in favorites,
                        imageLoader = imageLoader,
                        onClick = { onChannelClick(channel) },
                        onLongClick = { onToggleFavorite(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvChannelCard(
    channel: Channel,
    nowNext: NowNextProgram?,
    nowEpochSec: Long,
    isFavorite: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape = MuxGridShapes.CardLg),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MuxGridColors.CardSurface,
            contentColor = MuxGridColors.TextPrimary,
            focusedContainerColor = MuxGridColors.CardSurfaceElevated,
            focusedContentColor = MuxGridColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder),
                shape = MuxGridShapes.CardLg,
            ),
            focusedBorder = Border(
                border = BorderStroke(MuxGridBorders.FocusedCard, MuxGridColors.FocusBorder),
                shape = MuxGridShapes.CardLg,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChannelPiconBadge(
                    channelNumber = channel.formattedNumber,
                    channelName = channel.name,
                    piconModel = channel.icon?.let { PiconModel(channel.id, it) },
                    imageLoader = imageLoader,
                    size = 52.dp,
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
                                color = MuxGridColors.AccentBlueLight,
                            )
                        }
                        if (isFavorite) {
                            Icon(
                                imageVector = MuxGridIcons.StarFilled,
                                contentDescription = "Favorite",
                                tint = MuxGridColors.AmberWarn,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    Text(
                        text = channel.name,
                        style = MuxGridType.CardTitle,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val now = nowNext?.now
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
                        text = nowNext.next?.let { "Next ${formatEpochHm(it.startEpochSec)} · ${it.title}" } ?: "",
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
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsDialog(
    profile: ServerProfile,
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
    onOpenServerSetup: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 440.dp, max = 640.dp)
                .clip(MuxGridShapes.Dialog)
                .background(MuxGridColors.CardSurfaceElevated)
                .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Dialog)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = MuxGridIcons.Settings,
                    contentDescription = null,
                    tint = MuxGridColors.AccentBlueLight,
                    modifier = Modifier.size(22.dp),
                )
                Text("Settings", style = MuxGridType.Title, fontSize = 20.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Video Decoder & Deinterlacer", style = MuxGridType.CardSubtitle, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        HwDecPreset.MEDIACODEC_ZERO_COPY to "Zero-Copy (TV Default)",
                        HwDecPreset.MEDIACODEC_COPY_BWDIF to "Copy + bwdif",
                    ).forEach { (preset, label) ->
                        FilterChip(
                            selected = hwDecPreset == preset,
                            onClick = { onSelectPreset(preset) },
                            colors = FilterChipDefaults.colors(
                                containerColor = MuxGridColors.CardSurface,
                                contentColor = MuxGridColors.TextPrimary,
                                selectedContainerColor = MuxGridColors.AccentBlue,
                                selectedContentColor = Color.White,
                                focusedContainerColor = MuxGridColors.FocusBorder,
                                focusedContentColor = MuxGridColors.CanvasTv,
                                focusedSelectedContainerColor = MuxGridColors.FocusBorder,
                                focusedSelectedContentColor = MuxGridColors.CanvasTv,
                            ),
                        ) {
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Live Timeshift Cache Limit", style = MuxGridType.CardSubtitle, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeshiftWindow.entries.forEach { option ->
                        FilterChip(
                            selected = timeshiftWindow == option,
                            onClick = { onSelectTimeshiftWindow(option) },
                            colors = FilterChipDefaults.colors(
                                containerColor = MuxGridColors.CardSurface,
                                contentColor = MuxGridColors.TextPrimary,
                                selectedContainerColor = MuxGridColors.AccentBlue,
                                selectedContentColor = Color.White,
                                focusedContainerColor = MuxGridColors.FocusBorder,
                                focusedContentColor = MuxGridColors.CanvasTv,
                                focusedSelectedContainerColor = MuxGridColors.FocusBorder,
                                focusedSelectedContentColor = MuxGridColors.CanvasTv,
                            ),
                        ) {
                            Text(option.shortLabel, fontSize = 13.sp)
                        }
                    }
                }
                Text(
                    text = "${timeshiftWindow.description} · split pause/rewind · applies on next tune",
                    style = MuxGridType.TabularSmall,
                    color = MuxGridColors.AccentBlueLight,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Server: ${profile.host}:${profile.htspPort} (HTTP ${profile.httpPort})",
                    style = MuxGridType.TabularSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onOpenServerSetup,
                        colors = tvPrimaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Server Connection")
                    }
                    Button(
                        onClick = onDismiss,
                        colors = tvSecondaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Close")
                    }
                }
            }
        }
    }
}
