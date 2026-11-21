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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.TimeshiftNoticeBanner
import io.github.corebonts.muxgrid.core.designsystem.TrackAndPresetPanel
import io.github.corebonts.muxgrid.core.designsystem.formatDurationSec
import io.github.corebonts.muxgrid.core.designsystem.rememberStableDurationSec
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.VideoAspectMode
import io.github.corebonts.muxgrid.core.player.MuxGridVideoView
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.delay

private val PROGRESSIVE_SEEK_STEPS_MS = listOf(10_000L, 30_000L, 60_000L, 300_000L)
private val PROGRESSIVE_SEEK_LABELS = listOf("10s", "30s", "1m", "5m")

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
    var progressFocused by remember { mutableStateOf(false) }
    var zapOsdTrigger by remember { mutableLongStateOf(0L) }
    var zapOsdVisible by remember { mutableStateOf(true) }
    var seekOsdTrigger by remember { mutableLongStateOf(0L) }
    var seekOsdVisible by remember { mutableStateOf(false) }
    var suppressNextCenterKeyUp by remember { mutableStateOf(false) }
    var suppressNextBackKeyUp by remember { mutableStateOf(false) }

    // Progressive seek state (10s -> 30s -> 1m -> 5m with short settle commit)
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var seekStepIndex by remember { mutableIntStateOf(0) }
    var lastSeekDirection by remember { mutableIntStateOf(0) }
    var lastSeekStepLabel by remember { mutableStateOf("") }
    var lastSeekPressElapsedMs by remember { mutableLongStateOf(0L) }

    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val progressFocusRequester = remember { FocusRequester() }
    val canSeekTimeline = playbackState.isRecordingPlayback || playbackState.isTimeshiftSeekable

    LaunchedEffect(playbackState.isCompleted) {
        if (playbackState.isCompleted) {
            onExit()
        }
    }

    LaunchedEffect(playbackState.channelId, playbackState.dvrEntryId) {
        pendingSeekTargetMs = null
        seekOsdVisible = false
        progressFocused = false
        zapOsdVisible = true
        zapOsdTrigger = System.currentTimeMillis()
    }

    LaunchedEffect(zapOsdTrigger, zapOsdVisible) {
        if (zapOsdVisible) {
            delay(3_000L)
            zapOsdVisible = false
        }
    }

    // Settle commit for progressive seeking + OSD timeout
    LaunchedEffect(seekOsdTrigger) {
        val target = pendingSeekTargetMs
        if (target != null) {
            delay(450L)
            if (pendingSeekTargetMs == target) {
                playbackEngine.seekTo(target)
                pendingSeekTargetMs = null
            }
        }
        if (seekOsdVisible) {
            delay(2_200L)
            seekOsdVisible = false
            lastSeekDirection = 0
            seekStepIndex = 0
        }
    }

    LaunchedEffect(hudVisible, trackPanelOpen) {
        if (hudVisible) {
            if (!trackPanelOpen && !progressFocused) {
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        } else {
            progressFocused = false
            seekOsdVisible = false
            runCatching { playerFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(canSeekTimeline) {
        if (!canSeekTimeline && progressFocused) {
            progressFocused = false
            if (hudVisible && !trackPanelOpen) {
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        }
    }

    // Keep the HUD on screen while the progress bar is focused or while navigating HUD controls.
    LaunchedEffect(hudVisible, trackPanelOpen, playbackState.status, progressFocused, hudInteractionNonce) {
        if (hudVisible && !trackPanelOpen && !progressFocused && playbackState.status == PlaybackStatus.PLAYING) {
            delay(6_000L)
            hudVisible = false
        }
    }

    fun triggerProgressiveSeek(direction: Int) {
        val nowMs = System.currentTimeMillis()
        if (lastSeekDirection == direction && (nowMs - lastSeekPressElapsedMs) <= 950L) {
            seekStepIndex = (seekStepIndex + 1).coerceAtMost(PROGRESSIVE_SEEK_STEPS_MS.lastIndex)
        } else {
            seekStepIndex = 0
        }
        lastSeekDirection = direction
        lastSeekPressElapsedMs = nowMs

        val stepMs = PROGRESSIVE_SEEK_STEPS_MS[seekStepIndex] * direction
        lastSeekStepLabel = if (direction < 0) {
            "-${PROGRESSIVE_SEEK_LABELS[seekStepIndex]}"
        } else {
            "+${PROGRESSIVE_SEEK_LABELS[seekStepIndex]}"
        }

        val baseMs = pendingSeekTargetMs ?: playbackState.positionMs
        val clamped = clampPreviewPositionMs(playbackState, baseMs + stepMs)
        pendingSeekTargetMs = clamped
        seekOsdVisible = true
        seekOsdTrigger = nowMs
    }

    fun confirmFromProgressBar() {
        val target = pendingSeekTargetMs
        if (target != null) {
            playbackEngine.seekTo(target)
            pendingSeekTargetMs = null
        } else {
            playbackEngine.togglePause()
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
            .onPreviewKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
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
                    return@onPreviewKeyEvent false
                }
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (hudVisible && !trackPanelOpen && !isBackKey) {
                    hudInteractionNonce++
                }
                when {
                    isBackKey && (trackPanelOpen || hudVisible) -> {
                        if (trackPanelOpen) {
                            trackPanelOpen = false
                        } else {
                            hudVisible = false
                        }
                        suppressNextBackKeyUp = true
                        true
                    }
                    (code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) -> {
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
                        triggerProgressiveSeek(-1)
                        true
                    }
                    canSeekTimeline && code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        triggerProgressiveSeek(1)
                        true
                    }
                    !hudVisible && !trackPanelOpen && code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        hudVisible = true
                        true
                    }
                    !hudVisible && !trackPanelOpen && code == KeyEvent.KEYCODE_DPAD_UP -> {
                        true
                    }
                    !hudVisible && !trackPanelOpen && canSeekTimeline && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        triggerProgressiveSeek(-1)
                        true
                    }
                    !hudVisible && !trackPanelOpen && canSeekTimeline && code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        triggerProgressiveSeek(1)
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
            }
            .focusRequester(playerFocusRequester)
            .focusable(enabled = !hudVisible && !trackPanelOpen && playbackState.error == null),
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
                runCatching { okFocusRequester.requestFocus() }
            }
            Dialog(onDismissRequest = onExit) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 360.dp, max = 520.dp)
                        .clip(MuxGridShapes.Dialog)
                        .background(MuxGridColors.CardSurfaceElevated)
                        .border(MuxGridBorders.Hairline, MuxGridColors.CoralLive.copy(alpha = 0.55f), MuxGridShapes.Dialog)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (err is PlaybackError.TunerBusy) "Tuner Busy" else "Playback Error",
                        style = MuxGridType.Title,
                        fontSize = 20.sp,
                        color = MuxGridColors.TextPrimary,
                    )
                    Text(
                        text = err.message,
                        style = MuxGridType.CardSubtitle,
                        fontSize = 15.sp,
                        color = MuxGridColors.TextSecondary,
                    )
                    if (err is PlaybackError.TunerBusy) {
                        Text(
                            text = "The single tuner is currently busy recording on another mux.",
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
            visible = zapOsdVisible && !hudVisible && !trackPanelOpen && !seekOsdVisible,
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
                    val primaryTitle = if (playbackState.isRecordingPlayback) {
                        playbackState.programTitle ?: "Recording"
                    } else {
                        listOfNotNull(
                            playbackState.channelNumber?.takeIf { it != "0" },
                            playbackState.channelName ?: activeChannel?.name,
                        ).joinToString(" · ")
                    }
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
            visible = seekOsdVisible && canSeekTimeline && !hudVisible && !trackPanelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
        ) {
            val shownPosMs = pendingSeekTargetMs ?: playbackState.positionMs
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
                            if (lastSeekStepLabel.isNotEmpty()) {
                                SeekStepBadge(direction = lastSeekDirection, label = lastSeekStepLabel)
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
                            if (lastSeekStepLabel.isNotEmpty()) {
                                SeekStepBadge(direction = lastSeekDirection, label = lastSeekStepLabel)
                            }
                        }
                        Text(
                            text = if (offsetMs >= 2_000L) {
                                "-${formatDurationSec(stableOffsetSec)} · Buffer ${formatDurationSec(stableBufSec)}"
                            } else {
                                "LIVE · Buffer ${formatDurationSec(stableBufSec)}"
                            },
                            style = MuxGridType.TabularNumber,
                            color = if (offsetMs >= 2_000L) MuxGridColors.AccentSoft else MuxGridColors.CoralLive,
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
                                val title = if (playbackState.isRecordingPlayback) {
                                    playbackState.programTitle ?: "Recording"
                                } else {
                                    listOfNotNull(
                                        playbackState.channelNumber?.takeIf { it != "0" },
                                        playbackState.channelName ?: activeChannel?.name,
                                    ).joinToString(" · ")
                                }
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
                            if (playbackState.videoWidth > 0) {
                                Text(
                                    text = "${playbackState.videoWidth}×${playbackState.videoHeight} · ${playbackState.hwdecCurrent ?: "sw"} · deint=${playbackState.deinterlaceActive}",
                                    style = MuxGridType.TabularSmall,
                                )
                            }
                        }
                    }

                    if (playbackState.isRecordingPlayback) {
                        val dur = playbackState.durationMs.coerceAtLeast(1L)
                        val shownPosMs = pendingSeekTargetMs ?: playbackState.positionMs
                        val stablePosSec = rememberStableDurationSec(shownPosMs, playbackState.dvrEntryId)
                        val stableDurSec = rememberStableDurationSec(playbackState.durationMs, playbackState.dvrEntryId)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TvSeekProgressBar(
                                progress = (shownPosMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                                fillColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.Accent,
                                focused = progressFocused,
                                focusRequester = progressFocusRequester,
                                onFocusChanged = { progressFocused = it },
                                onSeek = { triggerProgressiveSeek(it) },
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
                                    if (seekOsdVisible && lastSeekStepLabel.isNotEmpty()) {
                                        SeekStepBadge(direction = lastSeekDirection, label = lastSeekStepLabel)
                                    }
                                }
                                Text(formatDurationSec(stableDurSec), style = MuxGridType.TabularSmall)
                            }
                        }
                    } else if (playbackState.isTimeshiftSeekable) {
                        val bufWindow = playbackState.bufferWindowMs.coerceAtLeast(1L)
                        val shownPosMs = pendingSeekTargetMs ?: playbackState.positionMs
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
                                onSeek = { triggerProgressiveSeek(it) },
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
                                        text = if (shownOffsetMs >= 2_000L) {
                                            "-${formatDurationSec(stableOffsetSec)} behind live"
                                        } else {
                                            "LIVE"
                                        },
                                        style = MuxGridType.TabularSmall,
                                        color = if (shownOffsetMs >= 2_000L) MuxGridColors.AccentSoft else MuxGridColors.CoralLive,
                                    )
                                    if (seekOsdVisible && lastSeekStepLabel.isNotEmpty()) {
                                        SeekStepBadge(direction = lastSeekDirection, label = lastSeekStepLabel)
                                    }
                                }
                                Text(
                                    text = "Timeshift buffer ${formatDurationSec(stableBufSec)}",
                                    style = MuxGridType.TabularSmall,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                            ) {
                                if (canSeekTimeline) {
                                    runCatching { progressFocusRequester.requestFocus() }
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
                                onClick = { playbackEngine.seekBy(-30_000L) },
                                shape = tvButtonShape(),
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Icon(
                                    imageVector = MuxGridIcons.Replay30,
                                    contentDescription = "Seek back 30 seconds",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Button(
                            onClick = { playbackEngine.togglePause() },
                            modifier = Modifier.focusRequester(playPauseFocusRequester),
                            shape = tvButtonShape(),
                            colors = tvPrimaryButtonColors(),
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPaused) MuxGridIcons.Play else MuxGridIcons.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (playbackState.isPaused) "Play" else "Pause", fontWeight = FontWeight.SemiBold)
                        }
                        if (canSeekTimeline) {
                            Button(
                                onClick = { playbackEngine.seekBy(30_000L) },
                                shape = tvButtonShape(),
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Icon(
                                    imageVector = MuxGridIcons.Forward30,
                                    contentDescription = "Seek forward 30 seconds",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (playbackState.isBehindLive) {
                            Button(
                                onClick = { playbackEngine.seekToLiveEdge() },
                                shape = tvButtonShape(),
                                colors = tvPrimaryButtonColors(),
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
                        if (!playbackState.isRecordingPlayback && canZap) {
                            Button(
                                onClick = { onZapDelta(-1) },
                                shape = tvButtonShape(),
                                colors = tvSecondaryButtonColors(),
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
                                shape = tvButtonShape(),
                                colors = tvSecondaryButtonColors(),
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
                            onClick = { trackPanelOpen = !trackPanelOpen },
                            shape = tvButtonShape(),
                            colors = tvSecondaryButtonColors(),
                        ) {
                            Icon(
                                imageVector = MuxGridIcons.Tracks,
                                contentDescription = "Display Options",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Button(
                            onClick = onExit,
                            shape = tvButtonShape(),
                            colors = tvSecondaryButtonColors(),
                        ) {
                            Icon(
                                imageVector = MuxGridIcons.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                    }

                    // Horizontal Quick Channel Strip underneath transport controls
                    if (!playbackState.isRecordingPlayback && channels.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(channels, key = { it.id }) { ch ->
                                val isCurrent = ch.id == playbackState.channelId
                                val chNow = nowNextMap[ch.id]
                                Surface(
                                    onClick = {
                                        onSelectChannel(ch)
                                        hudVisible = false
                                    },
                                    modifier = Modifier.width(210.dp),
                                    shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Sm),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isCurrent) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurface,
                                        contentColor = MuxGridColors.TextPrimary,
                                        focusedContainerColor = MuxGridColors.FocusBorder,
                                        focusedContentColor = MuxGridColors.CanvasTv,
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        border = if (isCurrent) {
                                            Border(
                                                border = BorderStroke(MuxGridBorders.Hairline, MuxGridColors.Accent),
                                                shape = MuxGridShapes.Sm,
                                            )
                                        } else {
                                            Border.None
                                        },
                                        focusedBorder = Border(
                                            border = BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder),
                                            shape = MuxGridShapes.Sm,
                                        ),
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ChannelPiconBadge(
                                            channelNumber = ch.formattedNumber,
                                            channelName = ch.name,
                                            piconModel = ch.icon?.let { PiconModel(ch.id, it) },
                                            imageLoader = imageLoader,
                                            size = 32.dp,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (ch.number > 0) "${ch.formattedNumber} · ${ch.name}" else ch.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            chNow?.now?.title?.let { title ->
                                                Text(
                                                    text = title,
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
                    modifier = Modifier.widthIn(max = 560.dp),
                )
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
        Icon(
            imageVector = if (direction < 0) MuxGridIcons.FastRewind else MuxGridIcons.FastForward,
            contentDescription = null,
            tint = MuxGridColors.AccentSoft,
            modifier = Modifier.size(16.dp),
        )
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
    onSeek: (Int) -> Unit,
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
                    return@onPreviewKeyEvent isConfirmKey
                }
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onSeek(-1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onSeek(1)
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

private fun clampPreviewPositionMs(state: PlaybackState, requestedMs: Long): Long {
    val nonNeg = requestedMs.coerceAtLeast(0L)
    return when {
        state.isGrowingRecording -> {
            val maxSafe = (state.durationMs - 2_000L).coerceAtLeast(0L)
            nonNeg.coerceAtMost(maxSafe)
        }
        state.isRecordingPlayback && state.durationMs > 0L -> {
            nonNeg.coerceAtMost(state.durationMs)
        }
        !state.isRecordingPlayback && state.isTimeshiftSeekable -> {
            val minStart = state.bufferStartMs.coerceAtLeast(0L)
            val maxEnd = (state.bufferEndMs - 1_500L).coerceAtLeast(minStart)
            nonNeg.coerceIn(minStart, maxEnd)
        }
        else -> nonNeg
    }
}
