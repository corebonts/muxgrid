package io.github.corebonts.muxgrid.feature.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.PlayerErrorToast
import io.github.corebonts.muxgrid.core.designsystem.TimeshiftNoticeBanner
import io.github.corebonts.muxgrid.core.designsystem.TrackAndPresetPanel
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMs
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.player.MuxGridVideoView
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.delay

@Composable
internal fun TvPlayerScreen(
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
    var seekOsdTrigger by remember { mutableLongStateOf(0L) }
    var seekOsdVisible by remember { mutableStateOf(false) }
    var dismissedError by remember(playbackState.error) { mutableStateOf(false) }
    var suppressNextCenterKeyUp by remember { mutableStateOf(false) }

    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val canSeekTimeline = playbackState.isRecordingPlayback || playbackState.isTimeshiftSeekable

    LaunchedEffect(playbackState.isCompleted) {
        if (playbackState.isCompleted) {
            onExit()
        }
    }

    LaunchedEffect(playbackState.channelId, playbackState.dvrEntryId) {
        zapOsdVisible = true
        zapOsdTrigger = System.currentTimeMillis()
    }

    LaunchedEffect(zapOsdTrigger, zapOsdVisible) {
        if (zapOsdVisible) {
            delay(3_000L)
            zapOsdVisible = false
        }
    }

    LaunchedEffect(seekOsdTrigger, seekOsdVisible) {
        if (seekOsdVisible) {
            delay(2_500L)
            seekOsdVisible = false
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
                        playbackEngine.seekBy(-30_000L)
                        seekOsdVisible = true
                        seekOsdTrigger = System.currentTimeMillis()
                        true
                    }
                    canSeekTimeline && code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        playbackEngine.seekBy(30_000L)
                        seekOsdVisible = true
                        seekOsdTrigger = System.currentTimeMillis()
                        true
                    }
                    !hudVisible && !trackPanelOpen && !playbackState.isRecordingPlayback && code == KeyEvent.KEYCODE_DPAD_UP -> {
                        if (canZap) onZapDelta(-1)
                        true
                    }
                    !hudVisible && !trackPanelOpen && !playbackState.isRecordingPlayback && code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (canZap) onZapDelta(1)
                        true
                    }
                    !hudVisible && !trackPanelOpen && canSeekTimeline && code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        playbackEngine.seekBy(-10_000L)
                        seekOsdVisible = true
                        seekOsdTrigger = System.currentTimeMillis()
                        true
                    }
                    !hudVisible && !trackPanelOpen && canSeekTimeline && code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        playbackEngine.seekBy(30_000L)
                        seekOsdVisible = true
                        seekOsdTrigger = System.currentTimeMillis()
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

        playbackState.timeshiftNotice?.let { notice ->
            TimeshiftNoticeBanner(
                notice = notice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(28.dp),
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
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${formatDurationMs(playbackState.positionMs)} / ${formatDurationMs(playbackState.durationMs)}",
                                style = MuxGridType.TabularSmall,
                            )
                            if (playbackState.isGrowingRecording) {
                                DvrStateBadge(state = DvrState.RECORDING)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LiveProgressBar(
                            progress = frac,
                            fillColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.AccentBlueLight,
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

        // Minimal seek OSD for D-pad Left/Right on DVR & Live Timeshift
        AnimatedVisibility(
            visible = seekOsdVisible && canSeekTimeline && !hudVisible && !trackPanelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MuxGridColors.ScrimSurface)
                    .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (playbackState.isRecordingPlayback) {
                    val dur = playbackState.durationMs.coerceAtLeast(1L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = playbackState.programTitle ?: "Recording",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (playbackState.isGrowingRecording) {
                                DvrStateBadge(state = DvrState.RECORDING)
                            }
                            Text(
                                text = "${formatDurationMs(playbackState.positionMs)} / ${formatDurationMs(playbackState.durationMs)}",
                                style = MuxGridType.TabularNumber,
                            )
                        }
                    }
                    LiveProgressBar(
                        progress = (playbackState.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                        fillColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.AccentBlueLight,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val bufWindow = playbackState.bufferWindowMs.coerceAtLeast(1L)
                    val frac = ((playbackState.positionMs - playbackState.bufferStartMs).toFloat() / bufWindow.toFloat()).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = playbackState.channelName ?: activeChannel?.name ?: "Live TV",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (playbackState.isBehindLive) {
                                "-${formatDurationMs(playbackState.liveEdgeOffsetMs)} · Buffer ${formatDurationMs(bufWindow)}"
                            } else {
                                "LIVE · Buffer ${formatDurationMs(bufWindow)}"
                            },
                            style = MuxGridType.TabularNumber,
                            color = if (playbackState.isBehindLive) MuxGridColors.AccentBlueLight else MuxGridColors.CoralLive,
                        )
                    }
                    LiveProgressBar(
                        progress = frac,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LiveProgressBar(
                                progress = (playbackState.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                                fillColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.AccentBlueLight,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(formatDurationMs(playbackState.positionMs), style = MuxGridType.TabularSmall, color = Color.White)
                                Text(formatDurationMs(playbackState.durationMs), style = MuxGridType.TabularSmall)
                            }
                        }
                    } else if (playbackState.isTimeshiftSeekable) {
                        val bufWindow = playbackState.bufferWindowMs.coerceAtLeast(1L)
                        val frac = ((playbackState.positionMs - playbackState.bufferStartMs).toFloat() / bufWindow.toFloat()).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LiveProgressBar(
                                progress = frac,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (playbackState.isBehindLive) {
                                        "-${formatDurationMs(playbackState.liveEdgeOffsetMs)} behind live"
                                    } else {
                                        "LIVE"
                                    },
                                    style = MuxGridType.TabularSmall,
                                    color = if (playbackState.isBehindLive) MuxGridColors.AccentBlueLight else MuxGridColors.CoralLive,
                                )
                                Text(
                                    text = "Timeshift buffer ${formatDurationMs(bufWindow)}",
                                    style = MuxGridType.TabularSmall,
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canSeekTimeline) {
                            Button(
                                onClick = { playbackEngine.seekBy(-30_000L) },
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text("-30s")
                            }
                        }
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
                        if (canSeekTimeline) {
                            Button(
                                onClick = { playbackEngine.seekBy(30_000L) },
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text("+30s")
                            }
                        }
                        if (playbackState.isBehindLive) {
                            Button(
                                onClick = { playbackEngine.seekToLiveEdge() },
                                colors = tvPrimaryButtonColors(),
                            ) {
                                Text("Back to Live")
                            }
                        }
                        if (!playbackState.isRecordingPlayback && canZap) {
                            Button(
                                onClick = { onZapDelta(-1) },
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text("Ch -")
                            }
                            Button(
                                onClick = { onZapDelta(1) },
                                colors = tvSecondaryButtonColors(),
                            ) {
                                Text("Ch +")
                            }
                        }
                        Button(
                            onClick = { trackPanelOpen = !trackPanelOpen },
                            colors = tvSecondaryButtonColors(),
                        ) {
                            Text("Tracks & Decoder")
                        }
                        Button(
                            onClick = onExit,
                            colors = tvSecondaryButtonColors(),
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
