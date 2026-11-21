package io.github.corebonts.muxgrid.feature.tv

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridAlphas
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDurations
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.PlaybackInfoPanel
import io.github.corebonts.muxgrid.core.designsystem.TimeshiftNoticeBanner
import io.github.corebonts.muxgrid.core.designsystem.TrackAndPresetPanel
import io.github.corebonts.muxgrid.core.designsystem.displayDetail
import io.github.corebonts.muxgrid.core.designsystem.displayTitle
import io.github.corebonts.muxgrid.core.designsystem.formatDurationSec
import io.github.corebonts.muxgrid.core.designsystem.rememberStableDurationSec
import io.github.corebonts.muxgrid.core.designsystem.streamBadgeText
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.PlaybackSeekDefaults
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.VideoAspectMode
import io.github.corebonts.muxgrid.core.player.MuxGridVideoView
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvPlayerScreen(
    playbackEngine: PlaybackEngine,
    playbackState: PlaybackState,
    activeChannel: Channel?,
    nowNext: NowNextProgram?,
    channels: List<Channel>,
    nowNextMap: Map<Long, NowNextProgram>,
    nowEpochSec: Long,
    hwDecPreset: HwDecPreset,
    imageLoader: ImageLoader,
    canZap: Boolean,
    onSelectChannel: (Channel) -> Unit,
    onZapDelta: (Int) -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectAspectMode: (VideoAspectMode) -> Unit,
    onExit: () -> Unit,
) {
    var hudVisible by remember { mutableStateOf(false) }
    var hudInteractionNonce by remember { mutableIntStateOf(0) }
    var trackPanelOpen by remember { mutableStateOf(false) }
    var infoPanelOpen by remember { mutableStateOf(false) }
    var progressFocused by remember { mutableStateOf(false) }
    var zapOsdTrigger by remember { mutableIntStateOf(0) }
    var zapOsdVisible by remember { mutableStateOf(true) }
    var suppressNextCenterKeyUp by remember { mutableStateOf(false) }
    var suppressNextBackKeyUp by remember { mutableStateOf(false) }

    // Progressive seek state (exact-target tap ladder + gear-locked throttled hold)
    val seekController = remember { TvSeekController() }
    var seekUiState by remember { mutableStateOf(TvSeekUiState()) }
    var seekCommitNonce by remember { mutableIntStateOf(0) }
    var seekCommitDelayMs by remember { mutableStateOf<Long?>(null) }
    var seekOsdLingerNonce by remember { mutableIntStateOf(0) }

    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val progressFocusRequester = remember { FocusRequester() }
    val trackButtonFocusRequester = remember { FocusRequester() }
    val infoButtonFocusRequester = remember { FocusRequester() }
    val activeChannelChipFocusRequester = remember { FocusRequester() }
    val quickStripState = rememberLazyListState()
    var wasTrackPanelOpen by remember { mutableStateOf(false) }
    var wasInfoPanelOpen by remember { mutableStateOf(false) }
    var backToLiveFocused by remember { mutableStateOf(false) }
    val canSeekTimeline = playbackState.isRecordingPlayback || playbackState.isTimeshiftSeekable
    val lifecycleOwner = LocalLifecycleOwner.current

    fun cancelPendingSeek() {
        if (seekUiState.pendingTargetMs != null || seekUiState.osdVisible || seekCommitDelayMs != null) {
            seekController.reset()
            seekUiState = seekController.currentUiState()
            seekCommitDelayMs = null
            seekCommitNonce++
        }
    }

    LaunchedEffect(infoPanelOpen) {
        playbackEngine.setDiagnosticsEnabled(infoPanelOpen)
    }
    DisposableEffect(Unit) {
        onDispose {
            playbackEngine.setDiagnosticsEnabled(false)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                cancelPendingSeek()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(playbackState.isCompleted) {
        if (playbackState.isCompleted) {
            onExit()
        }
    }

    LaunchedEffect(playbackState.channelId, playbackState.dvrEntryId) {
        cancelPendingSeek()
        progressFocused = false
        zapOsdVisible = true
        zapOsdTrigger++
    }

    LaunchedEffect(zapOsdTrigger, zapOsdVisible) {
        if (zapOsdVisible) {
            delay(MuxGridDurations.TvZapOsdMs)
            zapOsdVisible = false
        }
    }

    fun commitPendingSeek(cancelPendingTimer: Boolean): Boolean {
        val hadPending = seekUiState.pendingTargetMs != null
        if (!hadPending) return false
        val target = seekController.consumeCommitTarget()
        seekUiState = seekController.currentUiState()
        seekCommitDelayMs = null
        if (cancelPendingTimer) {
            seekCommitNonce++
        }
        if (target != null) {
            playbackEngine.seekTo(target)
        }
        if (seekUiState.osdVisible) {
            seekOsdLingerNonce++
        }
        return true
    }

    // Settle commit for progressive seeking
    LaunchedEffect(seekCommitNonce) {
        val delayMs = seekCommitDelayMs ?: return@LaunchedEffect
        delay(delayMs)
        commitPendingSeek(cancelPendingTimer = false)
    }

    // OSD linger timeout after commit
    LaunchedEffect(seekOsdLingerNonce) {
        if (seekUiState.osdVisible && seekUiState.pendingTargetMs == null && !seekUiState.isHolding) {
            delay(TvSeekController.OSD_LINGER_MS)
            seekController.hideOsd()
            seekUiState = seekController.currentUiState()
        }
    }

    LaunchedEffect(hudVisible, trackPanelOpen, infoPanelOpen) {
        if (hudVisible) {
            if (!trackPanelOpen && !infoPanelOpen) {
                if (wasTrackPanelOpen) {
                    runCatching { trackButtonFocusRequester.requestFocus() }
                } else if (wasInfoPanelOpen) {
                    runCatching { infoButtonFocusRequester.requestFocus() }
                } else if (!progressFocused) {
                    runCatching { playPauseFocusRequester.requestFocus() }
                }
            }
        } else {
            progressFocused = false
            backToLiveFocused = false
            if (seekUiState.pendingTargetMs == null && !seekUiState.isHolding) {
                seekController.hideOsd()
                seekUiState = seekController.currentUiState()
            }
            runCatching { playerFocusRequester.requestFocus() }
        }
        wasTrackPanelOpen = trackPanelOpen
        wasInfoPanelOpen = infoPanelOpen
    }

    LaunchedEffect(hudVisible, playbackState.channelId, channels.size) {
        if (hudVisible && !playbackState.isRecordingPlayback && channels.size > 1) {
            val activeIdx = channels.indexOfFirst { it.id == playbackState.channelId }
            if (activeIdx >= 0) {
                runCatching { quickStripState.scrollToItem(activeIdx) }
            }
        }
    }

    LaunchedEffect(playbackState.isBehindLive) {
        if (!playbackState.isBehindLive && backToLiveFocused) {
            backToLiveFocused = false
            if (hudVisible && !trackPanelOpen && !infoPanelOpen) {
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(canSeekTimeline) {
        if (!canSeekTimeline) {
            cancelPendingSeek()
            if (progressFocused) {
                progressFocused = false
                if (hudVisible && !trackPanelOpen && !infoPanelOpen) {
                    runCatching { playPauseFocusRequester.requestFocus() }
                }
            }
        }
    }

    // Keep the HUD on screen while the progress bar is focused or while navigating HUD controls.
    LaunchedEffect(hudVisible, trackPanelOpen, infoPanelOpen, playbackState.status, progressFocused, hudInteractionNonce) {
        if (hudVisible && !trackPanelOpen && !infoPanelOpen && !progressFocused && playbackState.status == PlaybackStatus.PLAYING) {
            delay(MuxGridDurations.TvHudAutoHideMs)
            hudVisible = false
        }
    }

    fun handleSeekKeyDown(direction: Int, isRepeat: Boolean) {
        val result = seekController.onKeyDown(
            direction = direction,
            isRepeat = isRepeat,
            playbackState = playbackState,
            nowElapsedMs = SystemClock.elapsedRealtime(),
        )
        seekUiState = result.uiState
        if (result.commitDelayMs != null) {
            seekCommitDelayMs = result.commitDelayMs
            seekCommitNonce++
        }
    }

    fun handleSeekKeyUp(direction: Int) {
        val nextDelay = seekController.onKeyUp(direction)
        seekUiState = seekController.currentUiState()
        if (nextDelay != null) {
            if (nextDelay <= 0L) {
                commitPendingSeek(cancelPendingTimer = true)
            } else {
                seekCommitDelayMs = nextDelay
                seekCommitNonce++
            }
        }
    }

    fun confirmFromProgressBar() {
        if (!commitPendingSeek(cancelPendingTimer = true)) {
            playbackEngine.togglePause()
        }
    }

    BackHandler {
        val hadActiveSeek = seekUiState.pendingTargetMs != null || seekUiState.isHolding
        cancelPendingSeek()
        when {
            playbackState.error != null -> onExit()
            hadActiveSeek -> Unit
            infoPanelOpen -> infoPanelOpen = false
            trackPanelOpen -> trackPanelOpen = false
            hudVisible -> hudVisible = false
            else -> onExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                val isRepeat = keyEvent.nativeKeyEvent.repeatCount > 0
                val isBackKey = code == KeyEvent.KEYCODE_BACK ||
                    code == KeyEvent.KEYCODE_ESCAPE ||
                    code == KeyEvent.KEYCODE_BUTTON_B
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
                    if (suppressNextBackKeyUp && isBackKey) {
                        suppressNextBackKeyUp = false
                        return@onPreviewKeyEvent true
                    }
                    if (canSeekTimeline && (
                            code == KeyEvent.KEYCODE_MEDIA_REWIND ||
                                code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                            )
                    ) {
                        handleSeekKeyUp(if (code == KeyEvent.KEYCODE_MEDIA_REWIND) -1 else 1)
                        return@onPreviewKeyEvent true
                    }
                    if (!hudVisible && !trackPanelOpen && !infoPanelOpen && canSeekTimeline && (
                            code == KeyEvent.KEYCODE_DPAD_LEFT ||
                                code == KeyEvent.KEYCODE_DPAD_RIGHT
                            )
                    ) {
                        handleSeekKeyUp(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (hudVisible && !trackPanelOpen && !infoPanelOpen && !isBackKey && !isRepeat) {
                    hudInteractionNonce++
                }
                when {
                    isBackKey && suppressNextBackKeyUp -> true
                    isBackKey && (seekUiState.pendingTargetMs != null || seekUiState.isHolding) -> {
                        cancelPendingSeek()
                        suppressNextBackKeyUp = true
                        true
                    }
                    isBackKey && (infoPanelOpen || trackPanelOpen || hudVisible) -> {
                        cancelPendingSeek()
                        when {
                            infoPanelOpen -> infoPanelOpen = false
                            trackPanelOpen -> trackPanelOpen = false
                            else -> hudVisible = false
                        }
                        suppressNextBackKeyUp = true
                        true
                    }
                    (code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) -> {
                        cancelPendingSeek()
                        suppressNextBackKeyUp = true
                        onExit()
                        true
                    }
                    !playbackState.isRecordingPlayback && code == KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (canZap) onZapDelta(1)
                        true
                    }
                    !playbackState.isRecordingPlayback && code == KeyEvent.KEYCODE_CHANNEL_DOWN -> {
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
                    canSeekTimeline && code == KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        handleSeekKeyDown(-1, isRepeat = isRepeat)
                        true
                    }
                    canSeekTimeline && code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        handleSeekKeyDown(1, isRepeat = isRepeat)
                        true
                    }
                    !hudVisible && !trackPanelOpen && !infoPanelOpen && code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        hudVisible = true
                        true
                    }
                    !hudVisible && !trackPanelOpen && !infoPanelOpen && code == KeyEvent.KEYCODE_DPAD_UP -> {
                        true
                    }
                    !hudVisible && !trackPanelOpen && !infoPanelOpen && canSeekTimeline && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        handleSeekKeyDown(-1, isRepeat = isRepeat)
                        true
                    }
                    !hudVisible && !trackPanelOpen && !infoPanelOpen && canSeekTimeline && code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        handleSeekKeyDown(1, isRepeat = isRepeat)
                        true
                    }
                    !hudVisible && !trackPanelOpen && !infoPanelOpen && (
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
            }
            .focusRequester(playerFocusRequester)
            .focusable(enabled = !hudVisible && !trackPanelOpen && !infoPanelOpen && playbackState.error == null),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> MuxGridVideoView(ctx, playbackEngine) },
        )

        if (playbackState.status == PlaybackStatus.BUFFERING) {
            CircularProgressIndicator(
                color = MuxGridColors.AccentSoft,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        playbackState.timeshiftNotice?.let { notice ->
            TimeshiftNoticeBanner(
                notice = notice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(28.dp),
            )
        }

        val err = playbackState.error
        if (err != null) {
            val okFocusRequester = remember { FocusRequester() }
            LaunchedEffect(err) {
                hudVisible = false
                trackPanelOpen = false
                infoPanelOpen = false
                runCatching { okFocusRequester.requestFocus() }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.OverlayBackdrop)
                    .padding(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 360.dp, max = 520.dp)
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Dialog)
                        .background(MuxGridColors.CardSurfaceElevated)
                        .border(MuxGridBorders.Hairline, MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.BorderEmphasis), MuxGridShapes.Dialog)
                        .focusProperties { this.onExit = { cancelFocusChange() } }
                        .focusGroup()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = err.displayTitle(),
                        style = MuxGridType.Title,
                        fontSize = 18.sp,
                        color = MuxGridColors.TextPrimary,
                    )
                    Text(
                        text = err.message,
                        style = MuxGridType.CardSubtitle,
                        fontSize = 14.sp,
                        color = MuxGridColors.TextSecondary,
                    )
                    err.displayDetail()?.let { detail ->
                        Text(
                            text = detail,
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.TextMuted,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = onExit,
                            modifier = Modifier.focusRequester(okFocusRequester),
                            shape = tvButtonShape(),
                            colors = tvPrimaryButtonColors(),
                        ) {
                            Text("OK", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Minimal 3s zap/start OSD when full HUD is hidden
        AnimatedVisibility(
            visible = zapOsdVisible && !hudVisible && !trackPanelOpen && !infoPanelOpen && !seekUiState.osdVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.ScrimSurface)
                    .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activeChannel != null) {
                    ChannelPiconBadge(
                        channelNumber = activeChannel.formattedNumber,
                        channelName = activeChannel.name,
                        piconModel = activeChannel.icon?.let { PiconModel(activeChannel.id, it) },
                        imageLoader = imageLoader,
                        size = 52.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    val primaryTitle = playbackState.displayTitle(fallbackChannelName = activeChannel?.name)
                    Text(
                        text = primaryTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playbackState.isRecordingPlayback) {
                        val dur = playbackState.durationMs.coerceAtLeast(1L)
                        val frac = playbackState.positionMs.toFloat() / dur.toFloat()
                        val stablePosSec = rememberStableDurationSec(playbackState.positionMs, playbackState.dvrEntryId)
                        val stableDurSec = rememberStableDurationSec(playbackState.durationMs, playbackState.dvrEntryId)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${formatDurationSec(stablePosSec)} / ${formatDurationSec(stableDurSec)}",
                                style = MuxGridType.TabularSmall,
                            )
                            if (playbackState.isGrowingRecording) {
                                DvrStateBadge(state = DvrState.RECORDING)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LiveProgressBar(
                            progress = frac,
                            fillColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.ProgressFill,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
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
        }

        // Progressive seek OSD for D-pad Left/Right on DVR & Live Timeshift
        AnimatedVisibility(
            visible = seekUiState.osdVisible && canSeekTimeline && !hudVisible && !trackPanelOpen && !infoPanelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
        ) {
            val shownPosMs = seekUiState.pendingTargetMs ?: playbackState.positionMs
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 660.dp)
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.ScrimSurface)
                    .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (playbackState.isRecordingPlayback) {
                    val dur = playbackState.durationMs.coerceAtLeast(1L)
                    val stablePosSec = rememberStableDurationSec(shownPosMs, playbackState.dvrEntryId)
                    val stableDurSec = rememberStableDurationSec(playbackState.durationMs, playbackState.dvrEntryId)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = playbackState.programTitle ?: "Recording",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (seekUiState.badgeLabel.isNotEmpty()) {
                                SeekStepBadge(direction = seekUiState.direction, label = seekUiState.badgeLabel)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (playbackState.isGrowingRecording) {
                                DvrStateBadge(state = DvrState.RECORDING)
                            }
                            Text(
                                text = "${formatDurationSec(stablePosSec)} / ${formatDurationSec(stableDurSec)}",
                                style = MuxGridType.TabularNumber,
                            )
                        }
                    }
                    LiveProgressBar(
                        progress = (shownPosMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                        fillColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.Accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val bufWindow = playbackState.bufferWindowMs.coerceAtLeast(1L)
                    val frac = ((shownPosMs - playbackState.bufferStartMs).toFloat() / bufWindow.toFloat()).coerceIn(0f, 1f)
                    val offsetMs = (playbackState.bufferEndMs - shownPosMs).coerceAtLeast(0L)
                    val stableOffsetSec = rememberStableDurationSec(offsetMs, playbackState.channelId)
                    val stableBufSec = rememberStableDurationSec(bufWindow, playbackState.channelId)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = playbackState.channelName ?: activeChannel?.name ?: "Live TV",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (seekUiState.badgeLabel.isNotEmpty()) {
                                SeekStepBadge(direction = seekUiState.direction, label = seekUiState.badgeLabel)
                            }
                        }
                        Text(
                            text = if (offsetMs >= PlaybackSeekDefaults.LIVE_EDGE_DISPLAY_THRESHOLD_MS) {
                                "-${formatDurationSec(stableOffsetSec)} · Buffer ${formatDurationSec(stableBufSec)}"
                            } else {
                                "LIVE · Buffer ${formatDurationSec(stableBufSec)}"
                            },
                            style = MuxGridType.TabularNumber,
                            color = if (offsetMs >= PlaybackSeekDefaults.LIVE_EDGE_DISPLAY_THRESHOLD_MS) {
                                MuxGridColors.AccentSoft
                            } else {
                                MuxGridColors.CoralLive
                            },
                        )
                    }
                    LiveProgressBar(
                        progress = frac,
                        fillColor = MuxGridColors.Accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Full TV HUD overlay (toggled with OK/Select) + Horizontal Quick Channel Strip
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
                        .padding(horizontal = 36.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                val title = playbackState.displayTitle(fallbackChannelName = activeChannel?.name)
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val sub = if (playbackState.isRecordingPlayback) {
                                    playbackState.channelName ?: activeChannel?.name
                                } else {
                                    nowNext?.now?.title
                                }
                                sub?.let {
                                    Text(text = it, color = MuxGridColors.TextSecondary, fontSize = 14.sp)
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (playbackState.isGrowingRecording) {
                                DvrStateBadge(state = DvrState.RECORDING)
                            }
                            playbackState.streamBadgeText(includeDeinterlace = true)?.let { streamBadge ->
                                Text(
                                    text = streamBadge,
                                    style = MuxGridType.TabularSmall,
                                )
                            }
                        }
                    }

                    if (playbackState.isRecordingPlayback) {
                        val dur = playbackState.durationMs.coerceAtLeast(1L)
                        val shownPosMs = seekUiState.pendingTargetMs ?: playbackState.positionMs
                        val stablePosSec = rememberStableDurationSec(shownPosMs, playbackState.dvrEntryId)
                        val stableDurSec = rememberStableDurationSec(playbackState.durationMs, playbackState.dvrEntryId)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TvSeekProgressBar(
                                progress = (shownPosMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                                fillColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.Accent,
                                focused = progressFocused,
                                focusRequester = progressFocusRequester,
                                onFocusChanged = { progressFocused = it },
                                onSeekKeyDown = { dir, isRepeat -> handleSeekKeyDown(dir, isRepeat) },
                                onSeekKeyUp = { dir -> handleSeekKeyUp(dir) },
                                onConfirm = { confirmFromProgressBar() },
                                onDown = { runCatching { playPauseFocusRequester.requestFocus() } },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(formatDurationSec(stablePosSec), style = MuxGridType.TabularSmall, color = Color.White)
                                    if (seekUiState.osdVisible && seekUiState.badgeLabel.isNotEmpty()) {
                                        SeekStepBadge(direction = seekUiState.direction, label = seekUiState.badgeLabel)
                                    }
                                }
                                Text(formatDurationSec(stableDurSec), style = MuxGridType.TabularSmall)
                            }
                        }
                    } else if (playbackState.isTimeshiftSeekable) {
                        val bufWindow = playbackState.bufferWindowMs.coerceAtLeast(1L)
                        val shownPosMs = seekUiState.pendingTargetMs ?: playbackState.positionMs
                        val frac = ((shownPosMs - playbackState.bufferStartMs).toFloat() / bufWindow.toFloat()).coerceIn(0f, 1f)
                        val shownOffsetMs = (playbackState.bufferEndMs - shownPosMs).coerceAtLeast(0L)
                        val stableOffsetSec = rememberStableDurationSec(shownOffsetMs, playbackState.channelId)
                        val stableBufSec = rememberStableDurationSec(bufWindow, playbackState.channelId)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TvSeekProgressBar(
                                progress = frac,
                                fillColor = MuxGridColors.Accent,
                                focused = progressFocused,
                                focusRequester = progressFocusRequester,
                                onFocusChanged = { progressFocused = it },
                                onSeekKeyDown = { dir, isRepeat -> handleSeekKeyDown(dir, isRepeat) },
                                onSeekKeyUp = { dir -> handleSeekKeyUp(dir) },
                                onConfirm = { confirmFromProgressBar() },
                                onDown = { runCatching { playPauseFocusRequester.requestFocus() } },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (shownOffsetMs >= PlaybackSeekDefaults.LIVE_EDGE_DISPLAY_THRESHOLD_MS) {
                                            "-${formatDurationSec(stableOffsetSec)} behind live"
                                        } else {
                                            "LIVE"
                                        },
                                        style = MuxGridType.TabularSmall,
                                        color = if (shownOffsetMs >= PlaybackSeekDefaults.LIVE_EDGE_DISPLAY_THRESHOLD_MS) {
                                            MuxGridColors.AccentSoft
                                        } else {
                                            MuxGridColors.CoralLive
                                        },
                                    )
                                    if (seekUiState.osdVisible && seekUiState.badgeLabel.isNotEmpty()) {
                                        SeekStepBadge(direction = seekUiState.direction, label = seekUiState.badgeLabel)
                                    }
                                }
                                Text(
                                    text = "Timeshift buffer ${formatDurationSec(stableBufSec)}",
                                    style = MuxGridType.TabularSmall,
                                )
                            }
                        }
                    }

                    TvHudControlsRow(
                        canSeekTimeline = canSeekTimeline,
                        isPaused = playbackState.isPaused,
                        isBehindLive = playbackState.isBehindLive,
                        isRecordingPlayback = playbackState.isRecordingPlayback,
                        canZap = canZap,
                        playPauseFocusRequester = playPauseFocusRequester,
                        trackButtonFocusRequester = trackButtonFocusRequester,
                        infoButtonFocusRequester = infoButtonFocusRequester,
                        onFocusProgressBar = { runCatching { progressFocusRequester.requestFocus() } },
                        onSeekBy = { delta -> playbackEngine.seekBy(delta) },
                        onTogglePause = { playbackEngine.togglePause() },
                        onSeekToLiveEdge = { playbackEngine.seekToLiveEdge() },
                        onBackToLiveFocusChanged = { backToLiveFocused = it },
                        onZapDelta = onZapDelta,
                        onToggleTrackPanel = { trackPanelOpen = !trackPanelOpen },
                        onToggleInfoPanel = { infoPanelOpen = !infoPanelOpen },
                        onExit = onExit,
                    )

                    // Horizontal Quick Channel Strip underneath transport controls
                    if (!playbackState.isRecordingPlayback && channels.size > 1) {
                        TvQuickChannelStrip(
                            channels = channels,
                            currentChannelId = playbackState.channelId,
                            nowNextMap = nowNextMap,
                            imageLoader = imageLoader,
                            quickStripState = quickStripState,
                            activeChannelChipFocusRequester = activeChannelChipFocusRequester,
                            onSelectChannel = { ch ->
                                onSelectChannel(ch)
                                hudVisible = false
                            },
                        )
                    }
                }
            }
        }

        if (trackPanelOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.OverlayBackdrop)
                    .padding(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                TrackAndPresetPanel(
                    audioTracks = playbackState.audioTracks,
                    subtitleTracks = playbackState.subtitleTracks,
                    selectedAudioId = playbackState.selectedAudioId,
                    selectedSubtitleId = playbackState.selectedSubtitleId,
                    hwDecPreset = hwDecPreset,
                    aspectMode = playbackState.aspectMode,
                    onSelectAudio = { playbackEngine.setAudioTrack(it) },
                    onSelectSubtitle = { playbackEngine.setSubtitleTrack(it) },
                    onSelectPreset = onSelectPreset,
                    onSelectAspectMode = onSelectAspectMode,
                    onClose = { trackPanelOpen = false },
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                )
            }
        }

        if (infoPanelOpen) {
            val diagnostics by playbackEngine.diagnostics.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.OverlayBackdrop)
                    .padding(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                PlaybackInfoPanel(
                    playbackState = playbackState,
                    diagnostics = diagnostics,
                    hwDecPreset = hwDecPreset,
                    onClose = { infoPanelOpen = false },
                    modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TvHudControlsRow(
    canSeekTimeline: Boolean,
    isPaused: Boolean,
    isBehindLive: Boolean,
    isRecordingPlayback: Boolean,
    canZap: Boolean,
    playPauseFocusRequester: FocusRequester,
    trackButtonFocusRequester: FocusRequester,
    infoButtonFocusRequester: FocusRequester,
    onFocusProgressBar: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onTogglePause: () -> Unit,
    onSeekToLiveEdge: () -> Unit,
    onBackToLiveFocusChanged: (Boolean) -> Unit,
    onZapDelta: (Int) -> Unit,
    onToggleTrackPanel: () -> Unit,
    onToggleInfoPanel: () -> Unit,
    onExit: () -> Unit,
) {
    val buttonShape = tvButtonShape()
    val primaryColors = tvPrimaryButtonColors()
    val secondaryColors = tvSecondaryButtonColors()

    Row(
        modifier = Modifier.onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown &&
                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
            ) {
                if (canSeekTimeline) {
                    onFocusProgressBar()
                }
                true
            } else {
                false
            }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canSeekTimeline) {
            Button(
                onClick = { onSeekBy(-PlaybackSeekDefaults.SEEK_FORWARD_STEP_MS) },
                shape = buttonShape,
                colors = secondaryColors,
            ) {
                Icon(
                    imageVector = MuxGridIcons.Replay30,
                    contentDescription = "Seek back 30 seconds",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Button(
            onClick = onTogglePause,
            modifier = Modifier.focusRequester(playPauseFocusRequester),
            shape = buttonShape,
            colors = primaryColors,
        ) {
            Icon(
                imageVector = if (isPaused) MuxGridIcons.Play else MuxGridIcons.Pause,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isPaused) "Play" else "Pause", fontWeight = FontWeight.SemiBold)
        }
        if (canSeekTimeline) {
            Button(
                onClick = { onSeekBy(PlaybackSeekDefaults.SEEK_FORWARD_STEP_MS) },
                shape = buttonShape,
                colors = secondaryColors,
            ) {
                Icon(
                    imageVector = MuxGridIcons.Forward30,
                    contentDescription = "Seek forward 30 seconds",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Button(
            onClick = onExit,
            shape = buttonShape,
            colors = secondaryColors,
        ) {
            Icon(
                imageVector = MuxGridIcons.Stop,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Stop")
        }
        if (isBehindLive) {
            Button(
                onClick = onSeekToLiveEdge,
                modifier = Modifier.onFocusChanged { onBackToLiveFocusChanged(it.isFocused) },
                shape = buttonShape,
                colors = primaryColors,
            ) {
                Icon(
                    imageVector = MuxGridIcons.Sensors,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Back to Live", fontWeight = FontWeight.SemiBold)
            }
        }
        if (!isRecordingPlayback && canZap) {
            Button(
                onClick = { onZapDelta(-1) },
                shape = buttonShape,
                colors = secondaryColors,
            ) {
                Icon(
                    imageVector = MuxGridIcons.SkipPrevious,
                    contentDescription = "Previous channel",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Ch -")
            }
            Button(
                onClick = { onZapDelta(1) },
                shape = buttonShape,
                colors = secondaryColors,
            ) {
                Text("Ch +")
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = MuxGridIcons.SkipNext,
                    contentDescription = "Next channel",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Button(
            onClick = onToggleTrackPanel,
            modifier = Modifier.focusRequester(trackButtonFocusRequester),
            shape = buttonShape,
            colors = secondaryColors,
        ) {
            Icon(
                imageVector = MuxGridIcons.Tracks,
                contentDescription = "Display Options",
                modifier = Modifier.size(20.dp),
            )
        }
        Button(
            onClick = onToggleInfoPanel,
            modifier = Modifier.focusRequester(infoButtonFocusRequester),
            shape = buttonShape,
            colors = secondaryColors,
        ) {
            Icon(
                imageVector = MuxGridIcons.Info,
                contentDescription = "Playback info",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvQuickChannelStrip(
    channels: List<Channel>,
    currentChannelId: Long?,
    nowNextMap: Map<Long, NowNextProgram>,
    imageLoader: ImageLoader,
    quickStripState: LazyListState,
    activeChannelChipFocusRequester: FocusRequester,
    onSelectChannel: (Channel) -> Unit,
) {
    val hasCurrentInStrip = remember(channels, currentChannelId) {
        channels.any { it.id == currentChannelId }
    }
    LazyRow(
        state = quickStripState,
        modifier = Modifier
            .focusGroup()
            .focusRestorer(activeChannelChipFocusRequester),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(
            items = channels,
            key = { it.id },
            contentType = { "quick_strip_channel" },
        ) { ch ->
            val isCurrent = ch.id == currentChannelId
            val isStripTarget = isCurrent || (!hasCurrentInStrip && ch.id == channels.firstOrNull()?.id)
            val chNow = nowNextMap[ch.id]
            val piconModel = remember(ch.id, ch.icon) {
                ch.icon?.let { PiconModel(ch.id, it) }
            }
            var isFocused by remember(ch.id) { mutableStateOf(false) }
            val containerColor = when {
                isFocused -> MuxGridColors.FocusBorder
                isCurrent -> MuxGridColors.CardSurfaceElevated
                else -> MuxGridColors.CardSurface
            }
            val contentColor = if (isFocused) MuxGridColors.OnFocusBorder else MuxGridColors.TextPrimary

            Row(
                modifier = Modifier
                    .width(210.dp)
                    .then(
                        if (isStripTarget) {
                            Modifier.focusRequester(activeChannelChipFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                    .clip(MuxGridShapes.Sm)
                    .background(containerColor)
                    .then(
                        when {
                            isFocused -> Modifier.border(MuxGridBorders.Focused, MuxGridColors.FocusBorder, MuxGridShapes.Sm)
                            isCurrent -> Modifier.border(MuxGridBorders.Hairline, MuxGridColors.Accent, MuxGridShapes.Sm)
                            else -> Modifier
                        },
                    )
                    .onFocusChanged { state ->
                        val focused = state.isFocused
                        if (isFocused != focused) isFocused = focused
                    }
                    .clickable(
                        interactionSource = remember(ch.id) { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelectChannel(ch) },
                    )
                    .clearAndSetSemantics {
                        role = Role.Button
                        contentDescription = ch.name
                    }
                    .padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChannelPiconBadge(
                    channelNumber = ch.formattedNumber,
                    channelName = ch.name,
                    piconModel = piconModel,
                    imageLoader = imageLoader,
                    size = 32.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ch.number > 0) "${ch.formattedNumber} · ${ch.name}" else ch.name,
                        color = contentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    chNow?.now?.title?.let { title ->
                        Text(
                            text = title,
                            color = contentColor.copy(alpha = if (isFocused) 0.85f else 0.72f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekStepBadge(
    direction: Int,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (direction != 0) {
            Icon(
                imageVector = if (direction < 0) MuxGridIcons.FastRewind else MuxGridIcons.FastForward,
                contentDescription = null,
                tint = MuxGridColors.AccentSoft,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            color = MuxGridColors.AccentSoft,
            style = MuxGridType.TabularSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TvSeekProgressBar(
    progress: Float,
    fillColor: Color,
    focused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSeekKeyDown: (direction: Int, isRepeat: Boolean) -> Unit,
    onSeekKeyUp: (direction: Int) -> Unit,
    onConfirm: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onDispose { onFocusChanged(false) }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .onPreviewKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                val isConfirmKey = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                    code == KeyEvent.KEYCODE_ENTER ||
                    code == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (keyEvent.type == KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onSeekKeyUp(-1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSeekKeyUp(1)
                            true
                        }
                        else -> isConfirmKey
                    }
                }
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isRepeat = keyEvent.nativeKeyEvent.repeatCount > 0
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onSeekKeyDown(-1, isRepeat)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onSeekKeyDown(1, isRepeat)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDown()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> true
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        onConfirm()
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .focusRequester(focusRequester)
            .focusable()
            .clip(MuxGridShapes.Sm)
            .then(
                if (focused) {
                    Modifier
                        .background(MuxGridColors.CardSurfaceElevated)
                        .border(MuxGridBorders.Focused, MuxGridColors.FocusBorder, MuxGridShapes.Sm)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        LiveProgressBar(
            progress = progress,
            fillColor = fillColor,
            height = if (focused) 6.dp else 4.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
