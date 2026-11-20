package io.github.corebonts.muxgrid.feature.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import io.github.corebonts.muxgrid.core.player.MuxGridVideoView
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TvRoute {
    ONBOARDING,
    LIVE,
    PLAYER,
}

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
        mutableStateOf(if (currentProfile.isConfigured) TvRoute.LIVE else TvRoute.ONBOARDING)
    }
    var activeFilter by remember { mutableStateOf<LiveFilter>(LiveFilter.All) }

    val filteredChannels = remember(channels, activeFilter, favorites) {
        filterChannels(channels, activeFilter, favorites)
    }

    when (route) {
        TvRoute.ONBOARDING -> {
            BackHandler(enabled = currentProfile.isConfigured) {
                route = TvRoute.LIVE
            }
            OnboardingServerScreen(
                initialProfile = currentProfile,
                isTv = true,
                canCancel = currentProfile.isConfigured,
                onTestConnection = { repository.testConnection(it) },
                onSaveAndConnect = { updated ->
                    repository.saveAndConnect(updated)
                    route = TvRoute.LIVE
                },
                onCancel = { route = TvRoute.LIVE },
            )
        }
        TvRoute.LIVE -> {
            val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
            val nowEpochSec = (System.currentTimeMillis() + offsetMs) / 1000L
            TvLiveChannelsScreen(
                connectionState = connectionState,
                channels = filteredChannels,
                filters = remember(tags) { buildLiveFilters(tags) },
                activeFilter = activeFilter,
                favorites = favorites,
                nowNextMap = nowNextMap,
                nowEpochSec = nowEpochSec,
                hwDecPreset = hwDecPreset,
                imageLoader = repository.imageLoader,
                onSelectFilter = { activeFilter = it },
                onChannelClick = { channel ->
                    playbackEngine.playChannel(channel, hwDecPreset)
                    route = TvRoute.PLAYER
                },
                onToggleFavorite = { channel ->
                    scope.launch { repository.toggleFavorite(channel.id) }
                },
                onTogglePreset = {
                    val next = if (hwDecPreset == HwDecPreset.MEDIACODEC_ZERO_COPY) {
                        HwDecPreset.MEDIACODEC_COPY_BWDIF
                    } else {
                        HwDecPreset.MEDIACODEC_ZERO_COPY
                    }
                    scope.launch { repository.setHwDecPreset(next) }
                },
                onOpenServerSetup = { route = TvRoute.ONBOARDING },
                onRetryConnect = { repository.reconnect() },
            )
        }
        TvRoute.PLAYER -> {
            val zapList = filteredChannels.ifEmpty { channels }
            val activeChannel = channels.firstOrNull { it.id == playbackState.channelId }
            val activeNowNext = activeChannel?.let { nowNextMap[it.id] }
            val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
            val nowEpochSec = (System.currentTimeMillis() + offsetMs) / 1000L

            TvLivePlayerScreen(
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
                    route = TvRoute.LIVE
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvLiveChannelsScreen(
    connectionState: ConnectionState,
    channels: List<Channel>,
    filters: List<LiveFilter>,
    activeFilter: LiveFilter,
    favorites: Set<Long>,
    nowNextMap: Map<Long, NowNextProgram>,
    nowEpochSec: Long,
    hwDecPreset: HwDecPreset,
    imageLoader: ImageLoader,
    onSelectFilter: (LiveFilter) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onTogglePreset: () -> Unit,
    onOpenServerSetup: () -> Unit,
    onRetryConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MuxGridColors.CanvasTv)
            .padding(start = 24.dp, end = 24.dp, top = 20.dp),
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
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("MuxGrid", style = MuxGridType.Title, fontSize = 26.sp)
                ConnectionStatusBadge(state = connectionState)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onTogglePreset,
                    colors = ButtonDefaults.colors(
                        containerColor = MuxGridColors.CardSurface,
                        contentColor = MuxGridColors.TextPrimary,
                        focusedContainerColor = MuxGridColors.FocusBorder,
                        focusedContentColor = MuxGridColors.CanvasTv,
                    ),
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
                    onClick = onOpenServerSetup,
                    colors = ButtonDefaults.colors(
                        containerColor = MuxGridColors.CardSurface,
                        contentColor = MuxGridColors.TextPrimary,
                        focusedContainerColor = MuxGridColors.FocusBorder,
                        focusedContentColor = MuxGridColors.CanvasTv,
                    ),
                ) {
                    Text("Server", fontSize = 13.sp)
                }
            }
        }

        if (connectionState is ConnectionState.Error) {
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
                Text(connectionState.message, color = MuxGridColors.TextPrimary, fontSize = 14.sp)
                Button(onClick = if (connectionState.isAuthError) onOpenServerSetup else onRetryConnect) {
                    Text(if (connectionState.isAuthError) "Edit Server" else "Retry")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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

@Composable
private fun TvLivePlayerScreen(
    playbackEngine: PlaybackEngine,
    playbackState: PlaybackState,
    activeChannel: Channel?,
    nowNext: NowNextProgram?,
    nowEpochSec: Long,
    hwDecPreset: HwDecPreset,
    imageLoader: ImageLoader,
    canZap: Boolean,
    onZapDelta: (Int) -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onExit: () -> Unit,
) {
    var hudVisible by remember { mutableStateOf(false) }
    var trackPanelOpen by remember { mutableStateOf(false) }
    var zapOsdTrigger by remember { mutableLongStateOf(0L) }
    var zapOsdVisible by remember { mutableStateOf(true) }
    var dismissedError by remember(playbackState.error) { mutableStateOf(false) }
    var suppressNextCenterKeyUp by remember { mutableStateOf(false) }

    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(playbackState.channelId) {
        zapOsdVisible = true
        zapOsdTrigger = System.currentTimeMillis()
    }

    LaunchedEffect(zapOsdTrigger, zapOsdVisible) {
        if (zapOsdVisible) {
            delay(3_000L)
            zapOsdVisible = false
        }
    }

    LaunchedEffect(hudVisible, trackPanelOpen, playbackState.status) {
        if (hudVisible) {
            runCatching { playPauseFocusRequester.requestFocus() }
            if (!trackPanelOpen && playbackState.status == PlaybackStatus.PLAYING) {
                delay(5_000L)
                hudVisible = false
            }
        } else {
            runCatching { playerFocusRequester.requestFocus() }
        }
    }

    BackHandler {
        when {
            trackPanelOpen -> trackPanelOpen = false
            hudVisible -> hudVisible = false
            else -> onExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                if (keyEvent.type == KeyEventType.KeyUp) {
                    if (suppressNextCenterKeyUp && (
                            code == KeyEvent.KEYCODE_DPAD_CENTER ||
                                code == KeyEvent.KEYCODE_ENTER ||
                                code == KeyEvent.KEYCODE_NUMPAD_ENTER
                            )
                    ) {
                        suppressNextCenterKeyUp = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    code == KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (canZap) onZapDelta(1)
                        true
                    }
                    code == KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (canZap) onZapDelta(-1)
                        true
                    }
                    code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                        code == KeyEvent.KEYCODE_MEDIA_PLAY ||
                        code == KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        playbackEngine.togglePause()
                        hudVisible = true
                        true
                    }
                    !hudVisible && !trackPanelOpen && (code == KeyEvent.KEYCODE_DPAD_UP) -> {
                        if (canZap) onZapDelta(-1)
                        true
                    }
                    !hudVisible && !trackPanelOpen && (code == KeyEvent.KEYCODE_DPAD_DOWN) -> {
                        if (canZap) onZapDelta(1)
                        true
                    }
                    !hudVisible && !trackPanelOpen && (
                        code == KeyEvent.KEYCODE_DPAD_CENTER ||
                            code == KeyEvent.KEYCODE_ENTER ||
                            code == KeyEvent.KEYCODE_NUMPAD_ENTER
                        ) -> {
                        suppressNextCenterKeyUp = true
                        hudVisible = true
                        true
                    }
                    else -> false
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
                    .padding(28.dp),
            )
        }

        // Minimal 3s zap OSD when full HUD is hidden
        AnimatedVisibility(
            visible = zapOsdVisible && !hudVisible && !trackPanelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MuxGridColors.ScrimSurface)
                    .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activeChannel != null) {
                    ChannelPiconBadge(
                        channelNumber = activeChannel.formattedNumber,
                        channelName = activeChannel.name,
                        piconModel = activeChannel.icon?.let { PiconModel(activeChannel.id, it) },
                        imageLoader = imageLoader,
                        size = 54.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(
                            playbackState.channelNumber?.takeIf { it != "0" },
                            playbackState.channelName ?: activeChannel?.name,
                        ).joinToString(" · "),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    nowNext?.now?.let { now ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = now.title,
                            color = MuxGridColors.TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        LiveProgressBar(
                            progress = nowNext.progressFraction(nowEpochSec),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Full TV HUD overlay (toggled with OK/Select)
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MuxGridColors.ScrimSurface),
                            ),
                        )
                        .padding(horizontal = 36.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeChannel != null) {
                                ChannelPiconBadge(
                                    channelNumber = activeChannel.formattedNumber,
                                    channelName = activeChannel.name,
                                    piconModel = activeChannel.icon?.let { PiconModel(activeChannel.id, it) },
                                    imageLoader = imageLoader,
                                    size = 48.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(
                                    text = listOfNotNull(
                                        playbackState.channelNumber?.takeIf { it != "0" },
                                        playbackState.channelName ?: activeChannel?.name,
                                    ).joinToString(" · "),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                nowNext?.now?.let { now ->
                                    Text(
                                        text = now.title,
                                        color = MuxGridColors.TextSecondary,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }

                        if (playbackState.videoWidth > 0) {
                            Text(
                                text = "${playbackState.videoWidth}×${playbackState.videoHeight} · ${playbackState.hwdecCurrent ?: "sw"} · deint=${playbackState.deinterlaceActive}",
                                style = MuxGridType.TabularSmall,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { playbackEngine.togglePause() },
                            modifier = Modifier.focusRequester(playPauseFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = MuxGridColors.AccentBlue,
                                contentColor = Color.White,
                                focusedContainerColor = MuxGridColors.FocusBorder,
                                focusedContentColor = MuxGridColors.CanvasTv,
                            ),
                        ) {
                            Text(if (playbackState.isPaused) "Play" else "Pause")
                        }
                        if (canZap) {
                            Button(
                                onClick = { onZapDelta(-1) },
                                colors = ButtonDefaults.colors(
                                    containerColor = MuxGridColors.CardSurface,
                                    contentColor = Color.White,
                                    focusedContainerColor = MuxGridColors.FocusBorder,
                                    focusedContentColor = MuxGridColors.CanvasTv,
                                ),
                            ) {
                                Text("Ch -")
                            }
                            Button(
                                onClick = { onZapDelta(1) },
                                colors = ButtonDefaults.colors(
                                    containerColor = MuxGridColors.CardSurface,
                                    contentColor = Color.White,
                                    focusedContainerColor = MuxGridColors.FocusBorder,
                                    focusedContentColor = MuxGridColors.CanvasTv,
                                ),
                            ) {
                                Text("Ch +")
                            }
                        }
                        Button(
                            onClick = { trackPanelOpen = !trackPanelOpen },
                            colors = ButtonDefaults.colors(
                                containerColor = MuxGridColors.CardSurface,
                                contentColor = Color.White,
                                focusedContainerColor = MuxGridColors.FocusBorder,
                                focusedContentColor = MuxGridColors.CanvasTv,
                            ),
                        ) {
                            Text("Tracks & Decoder")
                        }
                        Button(
                            onClick = onExit,
                            colors = ButtonDefaults.colors(
                                containerColor = MuxGridColors.CardSurface,
                                contentColor = Color.White,
                                focusedContainerColor = MuxGridColors.FocusBorder,
                                focusedContentColor = MuxGridColors.CanvasTv,
                            ),
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }
        }

        if (trackPanelOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(36.dp),
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
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
        }
    }
}
