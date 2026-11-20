package io.github.corebonts.muxgrid.feature.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.data.TvheadendRepository
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.ConnectionStatusBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.OnboardingServerScreen
import io.github.corebonts.muxgrid.core.designsystem.PlayerErrorToast
import io.github.corebonts.muxgrid.core.designsystem.TrackAndPresetPanel
import io.github.corebonts.muxgrid.core.designsystem.buildLiveFilters
import io.github.corebonts.muxgrid.core.designsystem.filterChannels
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.label
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.player.MuxGridVideoView
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MobileRoute {
    ONBOARDING,
    LIVE,
    PLAYER,
}

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
    val favorites by repository.favoriteChannelIds.collectAsState()
    val hwDecPreset by repository.hwDecPreset.collectAsState()
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
        mutableStateOf(if (currentProfile.isConfigured) MobileRoute.LIVE else MobileRoute.ONBOARDING)
    }
    var activeFilter by remember { mutableStateOf<LiveFilter>(LiveFilter.All) }

    val filteredChannels = remember(channels, activeFilter, favorites) {
        filterChannels(channels, activeFilter, favorites)
    }

    when (route) {
        MobileRoute.ONBOARDING -> {
            BackHandler(enabled = currentProfile.isConfigured) {
                route = MobileRoute.LIVE
            }
            OnboardingServerScreen(
                initialProfile = currentProfile,
                isTv = false,
                canCancel = currentProfile.isConfigured,
                onTestConnection = { repository.testConnection(it) },
                onSaveAndConnect = { updated ->
                    repository.saveAndConnect(updated)
                    route = MobileRoute.LIVE
                },
                onCancel = { route = MobileRoute.LIVE },
            )
        }
        MobileRoute.LIVE -> {
            val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
            val nowEpochSec = (System.currentTimeMillis() + offsetMs) / 1000L
            MobileLiveChannelsScreen(
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
                onOpenServerSetup = { route = MobileRoute.ONBOARDING },
                onRetryConnect = { repository.reconnect() },
            )
        }
        MobileRoute.PLAYER -> {
            val activeChannel = channels.firstOrNull { it.id == playbackState.channelId }
            val activeNowNext = activeChannel?.let { nowNextMap[it.id] }
            BackHandler {
                playbackEngine.stop()
                route = MobileRoute.LIVE
            }
            MobileLivePlayerScreen(
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
                    route = MobileRoute.LIVE
                },
            )
        }
    }
}

@Composable
private fun MobileLiveChannelsScreen(
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
    onOpenServerSetup: () -> Unit,
    onRetryConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MuxGridColors.CanvasPhone)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("MuxGrid", style = MuxGridType.Title)
                Text("Live TV & Radio", style = MuxGridType.TabularSmall)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConnectionStatusBadge(state = connectionState)
                Text(
                    text = "Server",
                    color = MuxGridColors.AccentBlueLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MuxGridColors.CardSurface)
                        .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(50))
                        .clickable(onClick = onOpenServerSetup)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (connectionState is ConnectionState.Error) {
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
                    text = connectionState.message,
                    color = MuxGridColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (connectionState.isAuthError) "Edit" else "Retry",
                    color = MuxGridColors.AccentBlueLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (connectionState.isAuthError) onOpenServerSetup() else onRetryConnect()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                        onToggleFavorite = { onToggleFavorite(channel) },
                    )
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
                onLongClick = onToggleFavorite,
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

@Composable
private fun MobileLivePlayerScreen(
    playbackEngine: PlaybackEngine,
    playbackState: PlaybackState,
    activeChannel: Channel?,
    nowNext: NowNextProgram?,
    hwDecPreset: HwDecPreset,
    imageLoader: ImageLoader,
    canZap: Boolean,
    onZapPrevious: () -> Unit,
    onZapNext: () -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onExit: () -> Unit,
) {
    var hudVisible by remember { mutableStateOf(true) }
    var trackPanelOpen by remember { mutableStateOf(false) }
    var dismissedError by remember(playbackState.error) { mutableStateOf(false) }

    LaunchedEffect(hudVisible, trackPanelOpen, playbackState.status) {
        if (hudVisible && !trackPanelOpen && playbackState.status == PlaybackStatus.PLAYING) {
            delay(4_000L)
            hudVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (trackPanelOpen) {
                    trackPanelOpen = false
                } else {
                    hudVisible = !hudVisible
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> MuxGridVideoView(ctx, playbackEngine) },
        )

        if (playbackState.status == PlaybackStatus.BUFFERING) {
            CircularProgressIndicator(
                color = MuxGridColors.AccentBlueLight,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val err = playbackState.error
        if (err != null && !dismissedError) {
            PlayerErrorToast(
                error = err,
                onDismiss = { dismissedError = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            )
        }

        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top dark gradient scrim
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(MuxGridColors.ScrimSurface, Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedButton(onClick = onExit) {
                            Text("Back", color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        if (activeChannel != null) {
                            ChannelPiconBadge(
                                channelNumber = activeChannel.formattedNumber,
                                channelName = activeChannel.name,
                                piconModel = activeChannel.icon?.let { PiconModel(activeChannel.id, it) },
                                imageLoader = imageLoader,
                                size = 38.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                text = listOfNotNull(
                                    playbackState.channelNumber?.takeIf { it != "0" },
                                    playbackState.channelName ?: activeChannel?.name,
                                ).joinToString(" · "),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            nowNext?.now?.let { now ->
                                Text(
                                    text = now.title,
                                    color = MuxGridColors.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { trackPanelOpen = !trackPanelOpen },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MuxGridColors.ScrimLight,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Tracks", fontSize = 13.sp)
                    }
                }

                // Bottom dark gradient scrim
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MuxGridColors.ScrimSurface),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MuxGridColors.CoralLive),
                            )
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (playbackState.videoWidth > 0) {
                                Text(
                                    text = "· ${playbackState.videoWidth}×${playbackState.videoHeight} · ${playbackState.hwdecCurrent ?: "sw"}",
                                    style = MuxGridType.TabularSmall,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canZap) {
                            OutlinedButton(
                                onClick = onZapPrevious,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Prev Ch", color = Color.White, fontSize = 13.sp)
                            }
                        }
                        Button(
                            onClick = { playbackEngine.togglePause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MuxGridColors.AccentBlue,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.weight(1.2f),
                        ) {
                            Text(
                                text = if (playbackState.isPaused) "Play" else "Pause",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (canZap) {
                            OutlinedButton(
                                onClick = onZapNext,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Next Ch", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        if (trackPanelOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                TrackAndPresetPanel(
                    audioTracks = playbackState.audioTracks,
                    subtitleTracks = playbackState.subtitleTracks,
                    selectedAudioId = playbackState.selectedAudioId,
                    selectedSubtitleId = playbackState.selectedSubtitleId,
                    hwDecPreset = hwDecPreset,
                    onSelectAudio = { playbackEngine.setAudioTrack(it) },
                    onSelectSubtitle = { playbackEngine.setSubtitleTrack(it) },
                    onSelectPreset = onSelectPreset,
                    onClose = { trackPanelOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
