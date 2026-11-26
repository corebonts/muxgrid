package io.github.corebonts.muxgrid.feature.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.designsystem.AmbientRadioGlow
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridAlphas
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridCompactButton
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDurations
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSeekBar
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
import io.github.corebonts.muxgrid.core.model.DvrEntry
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
import kotlin.math.roundToInt

private enum class DragFeedbackKind {
    BRIGHTNESS,
    VOLUME,
}

private data class DragFeedback(
    val kind: DragFeedbackKind,
    val percent: Int,
)

private data class SeekTapFeedback(
    val deltaSec: Int,
    val nonce: Int,
)

@Composable
internal fun MobilePlayerScreen(
    playbackEngine: PlaybackEngine,
    playbackState: PlaybackState,
    activeChannel: Channel?,
    nowNext: NowNextProgram?,
    channels: List<Channel>,
    nowNextMap: Map<Long, NowNextProgram>,
    nowEpochSec: Long,
    hwDecPreset: HwDecPreset,
    imageLoader: ImageLoader,
    isInPip: Boolean,
    canEnterPip: Boolean,
    canZap: Boolean,
    radioAmbientGlowEnabled: Boolean = true,
    onEnterPip: () -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onZapPrevious: () -> Unit,
    onZapNext: () -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectAspectMode: (VideoAspectMode) -> Unit,
    onPlaybackCompleted: () -> Unit,
    onStop: () -> Unit,
    onExit: () -> Unit,
    activeRecording: DvrEntry? = null,
    onPlayRecording: ((DvrEntry, Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var hudVisible by remember { mutableStateOf(true) }
    var hudInteractionNonce by remember { mutableIntStateOf(0) }
    var trackPanelOpen by remember { mutableStateOf(false) }
    var infoPanelOpen by remember { mutableStateOf(false) }
    var quickZapOpen by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    var dragFeedback by remember { mutableStateOf<DragFeedback?>(null) }
    var seekFeedback by remember { mutableStateOf<SeekTapFeedback?>(null) }
    var seekFeedbackNonce by remember { mutableIntStateOf(0) }
    var accumulatedSeekDelta by remember { mutableIntStateOf(0) }
    var lastSeekTapElapsedMs by remember { mutableLongStateOf(0L) }
    var brightnessFraction by remember {
        mutableFloatStateOf(readInitialBrightness(activity, context))
    }
    var volumeAccumulator by remember { mutableFloatStateOf(0f) }

    val canSeekTimeline = playbackState.isRecordingPlayback || playbackState.isTimeshiftSeekable
    val isAudioStage = playbackState.isRadio || playbackState.isAudioOnly
    val hasError = playbackState.error != null
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val audioMeteringActive = radioAmbientGlowEnabled &&
        isAudioStage &&
        !isInPip &&
        lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    LaunchedEffect(playbackEngine, audioMeteringActive) {
        playbackEngine.setAudioMeteringEnabled(audioMeteringActive)
    }
    DisposableEffect(playbackEngine) {
        onDispose {
            playbackEngine.setAudioMeteringEnabled(false)
        }
    }

    BackHandler(enabled = trackPanelOpen || infoPanelOpen || quickZapOpen || playbackState.isSeeking) {
        when {
            playbackState.isSeeking -> playbackEngine.cancelSeek()
            infoPanelOpen -> infoPanelOpen = false
            trackPanelOpen -> trackPanelOpen = false
            quickZapOpen -> quickZapOpen = false
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

    // Immersive system bars on phone player
    LaunchedEffect(hudVisible, isInPip, trackPanelOpen, infoPanelOpen, quickZapOpen, hasError) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!hudVisible && !isInPip && !trackPanelOpen && !infoPanelOpen && !quickZapOpen && !hasError) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(activity, view) {
        onDispose {
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(playbackState.channelId, playbackState.dvrEntryId) {
        isScrubbing = false
        scrubPositionMs = 0L
        dragFeedback = null
        seekFeedback = null
        accumulatedSeekDelta = 0
        lastSeekTapElapsedMs = 0L
    }

    LaunchedEffect(playbackState.isCompleted) {
        if (playbackState.isCompleted) {
            onPlaybackCompleted()
        }
    }

    LaunchedEffect(hudVisible, hudInteractionNonce, trackPanelOpen, infoPanelOpen, quickZapOpen, playbackState.status, playbackState.isSeeking, isScrubbing, isInPip, isAudioStage) {
        if (hudVisible && !trackPanelOpen && !infoPanelOpen && !quickZapOpen && !isScrubbing && !isInPip &&
            !playbackState.isSeeking && playbackState.status == PlaybackStatus.PLAYING && !isAudioStage
        ) {
            delay(MuxGridDurations.MobileHudAutoHideMs)
            hudVisible = false
        }
    }

    LaunchedEffect(seekFeedback?.nonce) {
        if (seekFeedback != null) {
            delay(MuxGridDurations.SeekFeedbackMs)
            seekFeedback = null
            accumulatedSeekDelta = 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (isInPip) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(canSeekTimeline, trackPanelOpen, infoPanelOpen, quickZapOpen) {
                            detectTapGestures(
                                onTap = {
                                    when {
                                        infoPanelOpen -> infoPanelOpen = false
                                        trackPanelOpen -> trackPanelOpen = false
                                        quickZapOpen -> quickZapOpen = false
                                        else -> hudVisible = !hudVisible
                                    }
                                },
                                onDoubleTap = { offset ->
                                    if (!canSeekTimeline || trackPanelOpen || infoPanelOpen || quickZapOpen) return@detectTapGestures
                                    val isRight = offset.x >= size.width / 2f
                                    val deltaMs = if (isRight) {
                                        PlaybackSeekDefaults.SEEK_FORWARD_STEP_MS
                                    } else {
                                        -PlaybackSeekDefaults.SEEK_BACK_STEP_MS
                                    }
                                    val basePosMs = if (playbackState.isSeeking) {
                                        playbackState.seekTargetMs ?: playbackState.positionMs
                                    } else {
                                        playbackState.positionMs
                                    }
                                    val clampedTargetMs = playbackState.clampSeekPositionMs(basePosMs + deltaMs)
                                    val actualDeltaMs = clampedTargetMs - basePosMs
                                    val stepSec = if (actualDeltaMs == 0L) {
                                        0
                                    } else {
                                        ((actualDeltaMs + if (actualDeltaMs > 0L) 500L else -500L) / 1_000L).toInt()
                                    }
                                    playbackEngine.seekBy(deltaMs)
                                    if (stepSec != 0) {
                                        val nowMs = SystemClock.elapsedRealtime()
                                        val sameDirection = (stepSec > 0 && accumulatedSeekDelta > 0) ||
                                            (stepSec < 0 && accumulatedSeekDelta < 0)
                                        accumulatedSeekDelta = if (nowMs - lastSeekTapElapsedMs <= 1_200L && sameDirection) {
                                            accumulatedSeekDelta + stepSec
                                        } else {
                                            stepSec
                                        }
                                        lastSeekTapElapsedMs = nowMs
                                        seekFeedbackNonce++
                                        seekFeedback = SeekTapFeedback(accumulatedSeekDelta, seekFeedbackNonce)
                                    }
                                },
                            )
                        }
                        .pointerInput(trackPanelOpen, infoPanelOpen, quickZapOpen, isAudioStage) {
                            if (trackPanelOpen || infoPanelOpen || quickZapOpen || isAudioStage) return@pointerInput
                            var isLeftZone = false
                            var isRightZone = false
                            detectVerticalDragGestures(
                                onDragStart = { startOffset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    isLeftZone = startOffset.x < w / 3f
                                    isRightZone = startOffset.x > w * 2f / 3f
                                    if (isLeftZone) {
                                        brightnessFraction = readInitialBrightness(activity, context)
                                    } else if (isRightZone) {
                                        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        volumeAccumulator = cur.toFloat() / max.toFloat()
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    if (!isLeftZone && !isRightZone) return@detectVerticalDragGestures
                                    change.consume()
                                    val h = size.height.toFloat().coerceAtLeast(200f)
                                    val delta = -dragAmount / (h * 0.7f)
                                    if (isLeftZone) {
                                        val next = (brightnessFraction + delta).coerceIn(0.05f, 1.0f)
                                        brightnessFraction = next
                                        activity?.window?.let { win ->
                                            val lp = win.attributes
                                            lp.screenBrightness = next
                                            win.attributes = lp
                                        }
                                        dragFeedback = DragFeedback(
                                            kind = DragFeedbackKind.BRIGHTNESS,
                                            percent = (next * 100f).roundToInt(),
                                        )
                                    } else if (isRightZone) {
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val nextFrac = (volumeAccumulator + delta).coerceIn(0f, 1f)
                                        volumeAccumulator = nextFrac
                                        val targetVol = (nextFrac * maxVol).roundToInt().coerceIn(0, maxVol)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        dragFeedback = DragFeedback(
                                            kind = DragFeedbackKind.VOLUME,
                                            percent = (nextFrac * 100f).roundToInt(),
                                        )
                                    }
                                },
                                onDragEnd = { dragFeedback = null },
                                onDragCancel = { dragFeedback = null },
                            )
                        }
                },
            ),
    ) {
        if (!isAudioStage) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> MuxGridVideoView(ctx, playbackEngine) },
            )
        } else {
            // Calm audio/radio stage with optional zero-permission ambient audio glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (radioAmbientGlowEnabled) {
                            Modifier.background(Color(0xFF040405))
                        } else {
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(MuxGridColors.AudioStageTop, MuxGridColors.CanvasPhone, Color.Black),
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (radioAmbientGlowEnabled && !isInPip) {
                    AmbientRadioGlow(
                        audioLevelsProvider = { playbackEngine.audioLevels.value },
                        playbackStatus = playbackState.status,
                        isPaused = playbackState.isPaused,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .padding(bottom = if (hudVisible && !isInPip) 170.dp else 32.dp, top = 32.dp),
                ) {
                    if (activeChannel != null) {
                        ChannelPiconBadge(
                            channelNumber = activeChannel.formattedNumber,
                            channelName = activeChannel.name,
                            piconModel = activeChannel.icon?.let { PiconModel(activeChannel.id, it) },
                            imageLoader = imageLoader,
                            size = 96.dp,
                        )
                    }
                    Text(
                        text = playbackState.channelName ?: activeChannel?.name ?: "Audio Stream",
                        style = MuxGridType.Title,
                        color = Color.White,
                    )
                    (playbackState.programTitle ?: nowNext?.now?.title)?.let { show ->
                        Text(
                            text = show,
                            style = MuxGridType.CardSubtitle,
                            color = MuxGridColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(MuxGridShapes.Sm)
                            .background(MuxGridColors.Accent.copy(alpha = 0.16f))
                            .border(MuxGridBorders.Divider, MuxGridColors.AccentSoft.copy(alpha = 0.45f), MuxGridShapes.Sm)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (playbackState.isRadio) "LIVE RADIO" else "BACKGROUND AUDIO",
                            color = MuxGridColors.AccentSoft,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (playbackState.status == PlaybackStatus.BUFFERING && (!hudVisible || isInPip)) {
            CircularProgressIndicator(
                color = MuxGridColors.AccentSoft,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (isInPip) {
            return@Box
        }

        // Double-tap seek feedback badge
        seekFeedback?.let { fb ->
            val isForward = fb.deltaSec > 0
            Row(
                modifier = Modifier
                    .align(if (isForward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 36.dp)
                    .clip(MuxGridShapes.Sm)
                    .background(MuxGridColors.ScrimSurface)
                    .border(MuxGridBorders.Hairline, MuxGridColors.ScrimControlBorder, MuxGridShapes.Sm)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!isForward) {
                    Icon(
                        imageVector = MuxGridIcons.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = if (isForward) "+${fb.deltaSec}s" else "${fb.deltaSec}s",
                    style = MuxGridType.TabularNumber,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (isForward) {
                    Icon(
                        imageVector = MuxGridIcons.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Brightness / Volume vertical drag indicator
        dragFeedback?.let { fb ->
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = if (hudVisible) 150.dp else 0.dp)
                    .clip(MuxGridShapes.Sm)
                    .background(MuxGridColors.ScrimSurface)
                    .border(MuxGridBorders.Hairline, MuxGridColors.ScrimControlBorder, MuxGridShapes.Sm)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (fb.kind == DragFeedbackKind.BRIGHTNESS) MuxGridIcons.Brightness else MuxGridIcons.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "${fb.percent}%",
                    style = MuxGridType.TabularNumber,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        playbackState.timeshiftNotice?.let { notice ->
            TimeshiftNoticeBanner(
                notice = notice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(
                        top = if (hudVisible) 84.dp else 16.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
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
                                listOf(Color.Black.copy(alpha = 0.84f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        TopHudIconButton(
                            icon = MuxGridIcons.ExpandMore,
                            contentDescription = "Minimize player",
                            iconSize = 22.dp,
                            onClick = onExit,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (activeChannel != null) {
                            ChannelPiconBadge(
                                channelNumber = activeChannel.formattedNumber,
                                channelName = activeChannel.name,
                                piconModel = activeChannel.icon?.let { PiconModel(activeChannel.id, it) },
                                imageLoader = imageLoader,
                                size = 36.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Column {
                            val titleText = playbackState.displayTitle(fallbackChannelName = activeChannel?.name)
                            val subtitleText = if (playbackState.isRecordingPlayback) {
                                playbackState.channelName ?: activeChannel?.name
                            } else {
                                nowNext?.now?.title
                            }
                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            subtitleText?.let { sub ->
                                Text(
                                    text = sub,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canEnterPip && !isAudioStage) {
                            TopHudIconButton(
                                icon = MuxGridIcons.Pip,
                                contentDescription = "Picture-in-Picture",
                                onClick = onEnterPip,
                            )
                        }
                        TopHudIconButton(
                            icon = MuxGridIcons.Close,
                            contentDescription = "Stop playback",
                            onClick = onStop,
                        )
                    }
                }

                // Centered transport controls (Prev, -10s, Play/Pause, +30s, Next)
                val canZapChannels = !playbackState.isRecordingPlayback && canZap
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CenterTransportButton(
                        icon = MuxGridIcons.SkipPrevious,
                        contentDescription = "Previous channel",
                        enabled = canZapChannels,
                        size = 44.dp,
                        iconSize = 22.dp,
                        onClick = {
                            hudInteractionNonce++
                            onZapPrevious()
                        },
                    )
                    CenterTransportButton(
                        icon = MuxGridIcons.Replay10,
                        contentDescription = "Seek back 10 seconds",
                        enabled = canSeekTimeline,
                        size = 46.dp,
                        iconSize = 24.dp,
                        onClick = {
                            hudInteractionNonce++
                            playbackEngine.seekBy(-PlaybackSeekDefaults.SEEK_BACK_STEP_MS)
                        },
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.56f))
                            .border(MuxGridBorders.Hairline, Color.White.copy(alpha = 0.18f), CircleShape)
                            .clickable {
                                hudInteractionNonce++
                                playbackEngine.togglePause()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playbackState.status == PlaybackStatus.BUFFERING) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPaused) MuxGridIcons.Play else MuxGridIcons.Pause,
                                contentDescription = if (playbackState.isPaused) "Play" else "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    CenterTransportButton(
                        icon = MuxGridIcons.Forward30,
                        contentDescription = "Seek forward 30 seconds",
                        enabled = canSeekTimeline,
                        size = 46.dp,
                        iconSize = 24.dp,
                        onClick = {
                            hudInteractionNonce++
                            playbackEngine.seekBy(PlaybackSeekDefaults.SEEK_FORWARD_STEP_MS)
                        },
                    )
                    CenterTransportButton(
                        icon = MuxGridIcons.SkipNext,
                        contentDescription = "Next channel",
                        enabled = canZapChannels,
                        size = 44.dp,
                        iconSize = 22.dp,
                        onClick = {
                            hudInteractionNonce++
                            onZapNext()
                        },
                    )
                }

                // Bottom dark gradient scrim
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (playbackState.isRecordingPlayback) {
                        val durationMs = playbackState.durationMs.coerceAtLeast(1L)
                        val seekTargetMs = playbackState.seekTargetMs
                        val displayPosMs = if (isScrubbing) scrubPositionMs else playbackState.positionMs.coerceIn(0L, durationMs)
                        val sliderFraction = (displayPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        val bufferedFraction = playbackState.bufferedPositionMs
                            .takeIf { it > 0L }
                            ?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
                        val seekTargetFraction = if (!isScrubbing && playbackState.isSeeking && seekTargetMs != null) {
                            (seekTargetMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        val animatedFraction by animateFloatAsState(
                            targetValue = sliderFraction,
                            animationSpec = tween(durationMillis = MuxGridDurations.Progress, easing = LinearEasing),
                            label = "recSliderProgress",
                        )
                        val stablePosSec = rememberStableDurationSec(displayPosMs, playbackState.dvrEntryId)
                        val stableDurSec = rememberStableDurationSec(playbackState.durationMs, playbackState.dvrEntryId)
                        val shownPosSec = if (isScrubbing) (displayPosMs + 500L) / 1000L else stablePosSec
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (playbackState.isGrowingRecording) {
                                    DvrStateBadge(state = DvrState.RECORDING)
                                    if (activeChannel != null) {
                                        MuxGridCompactButton(
                                            text = "Watch Live",
                                            leadingIcon = MuxGridIcons.Live,
                                            containerColor = MuxGridColors.ScrimSurface,
                                            contentColor = Color.White,
                                            borderColor = Color.White.copy(alpha = MuxGridAlphas.ScrimBorder),
                                            onClick = {
                                                hudInteractionNonce++
                                                onSelectChannel(activeChannel)
                                            },
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(1.dp))
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CompactHudButton(
                                    icon = MuxGridIcons.Info,
                                    label = "Playback info",
                                    showLabel = !isPortrait,
                                    onClick = { infoPanelOpen = !infoPanelOpen },
                                )
                                CompactHudButton(
                                    icon = MuxGridIcons.Tracks,
                                    label = "Options",
                                    contentDescription = "Display Options",
                                    showLabel = !isPortrait,
                                    onClick = { trackPanelOpen = !trackPanelOpen },
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            MuxGridSeekBar(
                                value = if (isScrubbing) sliderFraction else animatedFraction,
                                onValueChange = { fraction ->
                                    isScrubbing = true
                                    val rawMs = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
                                    scrubPositionMs = playbackState.clampSeekPositionMs(rawMs)
                                },
                                onValueChangeFinished = {
                                    isScrubbing = false
                                    playbackEngine.seekTo(scrubPositionMs)
                                },
                                activeTrackColor = if (playbackState.isGrowingRecording) MuxGridColors.CoralLive else MuxGridColors.Accent,
                                inactiveTrackColor = MuxGridColors.TrackBackground,
                                bufferedFraction = bufferedFraction,
                                seekTargetFraction = seekTargetFraction,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatDurationSec(shownPosSec),
                                    style = MuxGridType.TabularSmall,
                                    color = Color.White,
                                )
                                val recStreamStatsText = if (!isAudioStage) playbackState.streamBadgeText() else null
                                if (recStreamStatsText != null) {
                                    Text(
                                        text = recStreamStatsText,
                                        style = MuxGridType.TabularSmall,
                                        color = Color.White.copy(alpha = MuxGridAlphas.SecondaryText),
                                    )
                                }
                                Text(
                                    text = formatDurationSec(stableDurSec),
                                    style = MuxGridType.TabularSmall,
                                    color = Color.White.copy(alpha = MuxGridAlphas.BodyText),
                                )
                            }
                        }
                    } else {
                        val streamStatsText = if (!isAudioStage) playbackState.streamBadgeText() else null

                        // Live status / Back to Live button + Chase-Play handoff + Channels drawer button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (playbackState.isBehindLive) {
                                    val stableLiveOffsetSec = rememberStableDurationSec(
                                        playbackState.liveEdgeOffsetMs,
                                        playbackState.channelId,
                                    )
                                    MuxGridCompactButton(
                                        text = "-${formatDurationSec(stableLiveOffsetSec)} · Back to Live",
                                        leadingIcon = MuxGridIcons.Sensors,
                                        containerColor = MuxGridColors.Accent,
                                        contentColor = MuxGridColors.OnAccent,
                                        onClick = { playbackEngine.seekToLiveEdge() },
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MuxGridColors.CoralLive),
                                        )
                                        Text(
                                            text = if (playbackState.isRadio) "RADIO" else "LIVE",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (activeRecording != null) {
                                            DvrStateBadge(state = DvrState.RECORDING)
                                        }
                                    }
                                }
                                if (activeRecording != null && onPlayRecording != null) {
                                    MuxGridCompactButton(
                                        text = if (activeRecording.hasResumePosition) "Resume Chase Play" else "Watch from Start",
                                        leadingIcon = if (activeRecording.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                                        containerColor = MuxGridColors.ScrimSurface,
                                        contentColor = MuxGridColors.AccentSoft,
                                        borderColor = MuxGridColors.AccentSoft.copy(alpha = MuxGridAlphas.BorderSubtle),
                                        onClick = {
                                            hudInteractionNonce++
                                            onPlayRecording(activeRecording, activeRecording.effectiveStartSec)
                                        },
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CompactHudButton(
                                    icon = MuxGridIcons.Info,
                                    label = "Playback info",
                                    showLabel = !isPortrait,
                                    onClick = { infoPanelOpen = !infoPanelOpen },
                                )
                                CompactHudButton(
                                    icon = MuxGridIcons.Tracks,
                                    label = "Options",
                                    contentDescription = "Display Options",
                                    showLabel = !isPortrait,
                                    onClick = { trackPanelOpen = !trackPanelOpen },
                                )
                                if (canZap) {
                                    MuxGridCompactButton(
                                        text = "Channels",
                                        leadingIcon = MuxGridIcons.ChannelsList,
                                        containerColor = MuxGridColors.ScrimSurface,
                                        contentColor = Color.White,
                                        borderColor = Color.White.copy(alpha = MuxGridAlphas.ScrimBorder),
                                        onClick = { quickZapOpen = true },
                                    )
                                }
                            }
                        }

                        if (playbackState.isTimeshiftSeekable) {
                            val bufStart = playbackState.bufferStartMs
                            val bufWindow = playbackState.bufferWindowMs.coerceAtLeast(1L)
                            val bufEnd = playbackState.bufferEndMs.coerceAtLeast(bufStart + bufWindow)
                            val displayPosMs = if (isScrubbing) {
                                scrubPositionMs.coerceIn(bufStart, bufEnd)
                            } else {
                                playbackState.positionMs.coerceIn(bufStart, bufEnd)
                            }
                            val sliderFraction = ((displayPosMs - bufStart).toFloat() / bufWindow.toFloat()).coerceIn(0f, 1f)
                            val animatedFraction by animateFloatAsState(
                                targetValue = sliderFraction,
                                animationSpec = tween(durationMillis = MuxGridDurations.Progress, easing = LinearEasing),
                                label = "liveSliderProgress",
                            )
                            val displayOffsetMs = (bufEnd - displayPosMs).coerceAtLeast(0L)
                            val stableOffsetSec = rememberStableDurationSec(displayOffsetMs, playbackState.channelId)
                            val stableBufSec = rememberStableDurationSec(bufWindow, playbackState.channelId)
                            val shownOffsetSec = if (isScrubbing) (displayOffsetMs + 500L) / 1000L else stableOffsetSec

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                MuxGridSeekBar(
                                    value = if (isScrubbing) sliderFraction else animatedFraction,
                                    onValueChange = { fraction ->
                                        isScrubbing = true
                                        scrubPositionMs = bufStart + (fraction.coerceIn(0f, 1f) * bufWindow).toLong()
                                    },
                                    onValueChangeFinished = {
                                        isScrubbing = false
                                        playbackEngine.seekTo(scrubPositionMs)
                                    },
                                    activeTrackColor = MuxGridColors.Accent,
                                    inactiveTrackColor = MuxGridColors.TrackBackground,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (displayOffsetMs >= PlaybackSeekDefaults.LIVE_EDGE_DISPLAY_THRESHOLD_MS) {
                                            "-${formatDurationSec(shownOffsetSec)}"
                                        } else {
                                            "Live edge"
                                        },
                                        style = MuxGridType.TabularSmall,
                                        color = Color.White,
                                    )
                                    if (streamStatsText != null) {
                                        Text(
                                            text = streamStatsText,
                                            style = MuxGridType.TabularSmall,
                                            color = Color.White.copy(alpha = 0.78f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                        )
                                    }
                                    Text(
                                        text = "Buffer ${formatDurationSec(stableBufSec)}",
                                        style = MuxGridType.TabularSmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                    )
                                }
                            }
                        } else if (streamStatsText != null) {
                            Text(
                                text = streamStatsText,
                                style = MuxGridType.TabularSmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                }
            }

            // Translucent Quick-Zap Channel Drawer over video (non-blurred dark scrim)
            AnimatedVisibility(
                visible = quickZapOpen,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { quickZapOpen = false },
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .widthIn(max = 330.dp)
                            .fillMaxWidth(0.84f)
                            .background(MuxGridColors.ScrimSurface)
                            .border(MuxGridBorders.Divider, MuxGridColors.Divider)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {}
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Quick Zap", style = MuxGridType.Title, fontSize = 17.sp)
                            Box(
                                modifier = Modifier
                                    .clip(MuxGridShapes.Sm)
                                    .clickable { quickZapOpen = false }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MuxGridIcons.Close,
                                    contentDescription = "Close",
                                    tint = MuxGridColors.TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(channels, key = { it.id }) { ch ->
                                val isCurrent = ch.id == playbackState.channelId
                                val chNow = nowNextMap[ch.id]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MuxGridShapes.Sm)
                                        .background(
                                            if (isCurrent) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurface,
                                        )
                                        .then(
                                            if (isCurrent) {
                                                Modifier.border(MuxGridBorders.Hairline, MuxGridColors.Accent.copy(alpha = 0.7f), MuxGridShapes.Sm)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable {
                                            quickZapOpen = false
                                            onSelectChannel(ch)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ChannelPiconBadge(
                                        channelNumber = ch.formattedNumber,
                                        channelName = ch.name,
                                        piconModel = ch.icon?.let { PiconModel(ch.id, it) },
                                        imageLoader = imageLoader,
                                        size = 36.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (ch.number > 0) "${ch.formattedNumber} · ${ch.name}" else ch.name,
                                            color = if (isCurrent) MuxGridColors.AccentSoft else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        chNow?.now?.let { nowEv ->
                                            Text(
                                                text = nowEv.title,
                                                color = MuxGridColors.TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            LiveProgressBar(
                                                progress = chNow.progressFraction(nowEpochSec),
                                                modifier = Modifier.fillMaxWidth(),
                                            )
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { trackPanelOpen = false },
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
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
                    modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth(),
                )
            }
        }

        if (infoPanelOpen) {
            val diagnostics by playbackEngine.diagnostics.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.OverlayBackdrop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { infoPanelOpen = false },
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                PlaybackInfoPanel(
                    playbackState = playbackState,
                    diagnostics = diagnostics,
                    hwDecPreset = hwDecPreset,
                    onClose = { infoPanelOpen = false },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                )
            }
        }

        // Render error modal in-window instead of a separate Dialog window so showing/dismissing
        // over SurfaceView does not trigger WindowManagerImpl.updateViewLayout on HyperOS 4.
        val err = playbackState.error
        if (err != null) {
            BackHandler(onBack = onPlaybackCompleted)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.OverlayBackdrop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlaybackCompleted,
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 420.dp)
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Dialog)
                        .background(MuxGridColors.CardSurfaceElevated)
                        .border(
                            width = MuxGridBorders.Hairline,
                            color = MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.BorderEmphasis),
                            shape = MuxGridShapes.Dialog,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
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
                        TextButton(onClick = onPlaybackCompleted) {
                            Text(
                                text = "OK",
                                color = MuxGridColors.AccentSoft,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopHudIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun CompactHudButton(
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    contentDescription: String = label,
) {
    val borderColor = Color.White.copy(alpha = MuxGridAlphas.ScrimBorder)
    if (showLabel) {
        MuxGridCompactButton(
            text = label,
            leadingIcon = icon,
            containerColor = MuxGridColors.ScrimSurface,
            contentColor = Color.White,
            borderColor = borderColor,
            onClick = onClick,
        )
    } else {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(MuxGridShapes.Sm)
                .background(MuxGridColors.ScrimSurface)
                .border(MuxGridBorders.Hairline, borderColor, MuxGridShapes.Sm)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CenterTransportButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (enabled) 0.48f else 0.28f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.36f),
            modifier = Modifier.size(iconSize),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun readInitialBrightness(activity: Activity?, context: Context): Float {
    val winVal = activity?.window?.attributes?.screenBrightness ?: -1f
    if (winVal in 0.01f..1.0f) return winVal
    val sysVal = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (sysVal / 255f).coerceIn(0.05f, 1.0f)
}
