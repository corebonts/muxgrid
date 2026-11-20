package io.github.corebonts.muxgrid.feature.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
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

@OptIn(ExperimentalTvMaterial3Api::class)
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.CanvasTv)
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("MuxGrid", style = MuxGridType.Title, fontSize = 24.sp)
                        listOf(
                            MainTab.LIVE to "Live",
                            MainTab.GUIDE to "Guide",
                            MainTab.RECORDINGS to "Recordings",
                        ).forEach { (tab, label) ->
                            FilterChip(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                colors = FilterChipDefaults.colors(
                                    containerColor = MuxGridColors.CardSurface,
                                    contentColor = MuxGridColors.TextSecondary,
                                    selectedContainerColor = MuxGridColors.AccentBlue,
                                    selectedContentColor = Color.White,
                                    focusedContainerColor = MuxGridColors.FocusBorder,
                                    focusedContentColor = MuxGridColors.CanvasTv,
                                    focusedSelectedContainerColor = MuxGridColors.FocusBorder,
                                    focusedSelectedContentColor = MuxGridColors.CanvasTv,
                                ),
                            ) {
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConnectionStatusBadge(state = connectionState)
                        Button(
                            onClick = {
                                val next = if (hwDecPreset == HwDecPreset.MEDIACODEC_ZERO_COPY) {
                                    HwDecPreset.MEDIACODEC_COPY_BWDIF
                                } else {
                                    HwDecPreset.MEDIACODEC_ZERO_COPY
                                }
                                scope.launch { repository.setHwDecPreset(next) }
                            },
                            colors = tvSecondaryButtonColors(),
                        ) {
                            Text(
                                text = if (hwDecPreset == HwDecPreset.MEDIACODEC_ZERO_COPY) {
                                    "HwDec: mediacodec"
                                } else {
                                    "HwDec: copy + bwdif"
                                },
                                fontSize = 13.sp,
                            )
                        }
                        Button(
                            onClick = { route = TvRoute.ONBOARDING },
                            colors = tvSecondaryButtonColors(),
                        ) {
                            Text("Server", fontSize = 13.sp)
                        }
                    }
                }

                visibleError?.let { errState ->
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MuxGridColors.CoralLive.copy(alpha = 0.16f))
                            .border(1.dp, MuxGridColors.CoralLive.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(errState.message, color = MuxGridColors.TextPrimary, fontSize = 14.sp)
                        Button(onClick = if (errState.isAuthError) ({ route = TvRoute.ONBOARDING }) else ({ repository.reconnect() })) {
                            Text(if (errState.isAuthError) "Edit Server" else "Retry")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

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
        TvRoute.PLAYER -> {
            val zapList = filteredChannels.ifEmpty { channels }
            val activeChannel = channels.firstOrNull { it.id == playbackState.channelId }
            val activeNowNext = activeChannel?.let { nowNextMap[it.id] }

            TvPlayerScreen(
                playbackEngine = playbackEngine,
                playbackState = playbackState,
                activeChannel = activeChannel,
                nowNext = activeNowNext,
                nowEpochSec = nowEpochSec,
                hwDecPreset = hwDecPreset,
                imageLoader = repository.imageLoader,
                canZap = zapList.size > 1,
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
                onExit = {
                    playbackEngine.stop()
                    route = TvRoute.MAIN
                },
            )
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
    onSelectFilter: (LiveFilter) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            filters.forEach { filter ->
                val selected = filter == activeFilter
                FilterChip(
                    selected = selected,
                    onClick = { onSelectFilter(filter) },
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
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MuxGridColors.CardSurface,
            contentColor = MuxGridColors.TextPrimary,
            focusedContainerColor = MuxGridColors.CardSurfaceElevated,
            focusedContentColor = MuxGridColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, MuxGridColors.HairlineBorder),
                shape = RoundedCornerShape(18.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, MuxGridColors.FocusBorder),
                shape = RoundedCornerShape(18.dp),
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
                            Text("★", color = MuxGridColors.AmberWarn, fontSize = 13.sp)
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
