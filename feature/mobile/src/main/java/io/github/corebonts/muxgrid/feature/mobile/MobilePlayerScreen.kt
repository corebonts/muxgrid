package io.github.corebonts.muxgrid.feature.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.PlayerErrorToast
import io.github.corebonts.muxgrid.core.designsystem.TrackAndPresetPanel
import io.github.corebonts.muxgrid.core.designsystem.formatDurationMs
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.player.MuxGridVideoView
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.delay

@Composable
internal fun MobilePlayerScreen(
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
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }

    LaunchedEffect(playbackState.isCompleted) {
        if (playbackState.isCompleted) {
            onExit()
        }
    }

    LaunchedEffect(hudVisible, trackPanelOpen, playbackState.status, isScrubbing) {
        if (hudVisible && !trackPanelOpen && !isScrubbing && playbackState.status == PlaybackStatus.PLAYING) {
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
                    .padding(
                        top = if (hudVisible) 88.dp else 16.dp,
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
                val scrimBtnColors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MuxGridColors.ScrimSurface,
                    contentColor = Color.White,
                )
                val scrimBtnBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))

                // Top dark gradient scrim
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent),
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
                        OutlinedButton(
                            onClick = onExit,
                            colors = scrimBtnColors,
                            border = scrimBtnBorder,
                        ) {
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
                            val titleText = if (playbackState.isRecordingPlayback) {
                                playbackState.programTitle ?: "Recording"
                            } else {
                                listOfNotNull(
                                    playbackState.channelNumber?.takeIf { it != "0" },
                                    playbackState.channelName ?: activeChannel?.name,
                                ).joinToString(" · ")
                            }
                            val subtitleText = if (playbackState.isRecordingPlayback) {
                                playbackState.channelName ?: activeChannel?.name
                            } else {
                                nowNext?.now?.title
                            }
                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            subtitleText?.let { sub ->
                                Text(
                                    text = sub,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { trackPanelOpen = !trackPanelOpen },
                        colors = scrimBtnColors,
                        border = scrimBtnBorder,
                    ) {
                        Text("Tracks", color = Color.White, fontSize = 13.sp)
                    }
                }

                // Bottom dark gradient scrim
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (playbackState.isRecordingPlayback) {
                        val durationMs = playbackState.durationMs.coerceAtLeast(1L)
                        val displayPosMs = if (isScrubbing) scrubPositionMs else playbackState.positionMs.coerceIn(0L, durationMs)
                        val sliderFraction = (displayPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Slider(
                                value = sliderFraction,
                                onValueChange = { fraction ->
                                    isScrubbing = true
                                    scrubPositionMs = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
                                },
                                onValueChangeFinished = {
                                    isScrubbing = false
                                    playbackEngine.seekTo(scrubPositionMs)
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = MuxGridColors.AccentBlueLight,
                                    inactiveTrackColor = MuxGridColors.TrackBackground,
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = formatDurationMs(displayPosMs),
                                    style = MuxGridType.TabularSmall,
                                    color = Color.White,
                                )
                                if (playbackState.videoWidth > 0) {
                                    Text(
                                        text = "${playbackState.videoWidth}×${playbackState.videoHeight} · ${playbackState.hwdecCurrent ?: "sw"}",
                                        style = MuxGridType.TabularSmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                    )
                                }
                                Text(
                                    text = formatDurationMs(playbackState.durationMs),
                                    style = MuxGridType.TabularSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { playbackEngine.seekBy(-10_000L) },
                                colors = scrimBtnColors,
                                border = scrimBtnBorder,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("-10s", color = Color.White, fontSize = 13.sp)
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
                            OutlinedButton(
                                onClick = { playbackEngine.seekBy(30_000L) },
                                colors = scrimBtnColors,
                                border = scrimBtnBorder,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("+30s", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    } else {
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
                                        color = Color.White.copy(alpha = 0.85f),
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
                                    colors = scrimBtnColors,
                                    border = scrimBtnBorder,
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
                                    colors = scrimBtnColors,
                                    border = scrimBtnBorder,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Next Ch", color = Color.White, fontSize = 13.sp)
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
                    .background(Color.Black.copy(alpha = 0.55f))
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
                    onSelectAudio = { playbackEngine.setAudioTrack(it) },
                    onSelectSubtitle = { playbackEngine.setSubtitleTrack(it) },
                    onSelectPreset = onSelectPreset,
                    onClose = { trackPanelOpen = false },
                    modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth(),
                )
            }
        }
    }
}
