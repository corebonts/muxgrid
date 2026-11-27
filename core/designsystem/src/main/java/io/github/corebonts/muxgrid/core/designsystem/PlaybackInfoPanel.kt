package io.github.corebonts.muxgrid.core.designsystem

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.PlaybackDiagnostics
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Composable
fun PlaybackInfoPanel(
    playbackState: PlaybackState,
    diagnostics: PlaybackDiagnostics,
    hwDecPreset: HwDecPreset,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val closeFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { 80.dp.roundToPx() }

    LaunchedEffect(Unit) {
        runCatching { closeFocusRequester.requestFocus() }
    }

    val resources = LocalResources.current
    val sections = remember(playbackState, diagnostics, hwDecPreset, resources) {
        buildPlaybackInfoSections(playbackState, diagnostics, hwDecPreset, resources)
    }

    Column(
        modifier = modifier
            .heightIn(max = 540.dp)
            .clip(MuxGridShapes.Dialog)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Dialog)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        scope.launch { scrollState.scrollTo((scrollState.value + scrollStepPx).coerceAtMost(scrollState.maxValue)) }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        scope.launch { scrollState.scrollTo((scrollState.value - scrollStepPx).coerceAtLeast(0)) }
                        true
                    }
                    else -> false
                }
            }
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .verticalScroll(scrollState)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = MuxGridIcons.Info,
                    contentDescription = null,
                    tint = MuxGridColors.AccentSoft,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.playback_info_title),
                        style = MuxGridType.Title,
                        fontSize = 18.sp,
                    )
                    val recordingFallback = stringResource(R.string.common_recording)
                    val liveFallback = stringResource(R.string.live_stream)
                    val titleLine = buildString {
                        val name = playbackState.displayTitle(
                            recordingFallback = playbackState.channelName ?: recordingFallback,
                            liveFallback = liveFallback,
                        )
                        append(name)
                        if (diagnostics.sessionUptimeMs > 0L) {
                            append(" · ")
                            append(formatDurationSec(diagnostics.sessionUptimeMs / 1000L))
                        }
                    }
                    Text(
                        text = titleLine,
                        style = MuxGridType.TabularSmall,
                        color = MuxGridColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val copiedToast = stringResource(R.string.playback_info_copied)
                PanelActionPill(
                    label = stringResource(R.string.common_copy),
                    onClick = {
                        val report = formatPlaybackInfoReport(playbackState, diagnostics, hwDecPreset)
                        copyTextToClipboard(
                            context = context,
                            label = "MuxGrid Playback Info",
                            text = report,
                            toastMessage = copiedToast,
                        )
                    },
                )
                ModalCloseIconButton(
                    onClick = onClose,
                    modifier = Modifier.focusRequester(closeFocusRequester),
                )
            }
        }

        // Live pipeline summary badges
        PlaybackStatusStrip(playbackState = playbackState, diagnostics = diagnostics)

        // Responsive 1- or 2-column grid of telemetry cards
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useTwoColumns = maxWidth >= 500.dp
            if (useTwoColumns) {
                val left = sections.filterIndexed { index, _ -> index % 2 == 0 }
                val right = sections.filterIndexed { index, _ -> index % 2 == 1 }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        left.forEach { section ->
                            InfoSectionCard(section = section, diagnostics = diagnostics)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        right.forEach { section ->
                            InfoSectionCard(section = section, diagnostics = diagnostics)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    sections.forEach { section ->
                        InfoSectionCard(section = section, diagnostics = diagnostics)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelActionPill(
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(MuxGridShapes.Sm)
            .background(if (focused) MuxGridColors.FocusBorder else MuxGridColors.CardSurfaceElevated)
            .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) MuxGridColors.OnFocusBorder else MuxGridColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PlaybackStatusStrip(
    playbackState: PlaybackState,
    diagnostics: PlaybackDiagnostics,
) {
    val (statusColor, statusText) = when {
        diagnostics.pausedForCache -> MuxGridColors.AmberWarn to stringResource(R.string.playback_info_status_buffering_cache)
        playbackState.status == PlaybackStatus.BUFFERING -> {
            val pct = diagnostics.cacheBufferingPercent
            MuxGridColors.AmberWarn to if (pct != null) {
                stringResource(R.string.playback_info_status_buffering_percent, pct)
            } else {
                stringResource(R.string.playback_info_status_buffering)
            }
        }
        playbackState.status == PlaybackStatus.PAUSED -> MuxGridColors.TextSecondary to stringResource(R.string.playback_info_status_paused)
        playbackState.isRecordingPlayback && playbackState.isGrowingRecording ->
            MuxGridColors.CoralLive to stringResource(R.string.playback_info_status_recording_live_tail)
        playbackState.isRecordingPlayback -> MuxGridColors.AccentSoft to stringResource(R.string.playback_info_status_dvr)
        playbackState.isBehindLive -> MuxGridColors.AccentSoft to stringResource(R.string.playback_info_status_timeshift)
        playbackState.isRadio -> MuxGridColors.CoralLive to stringResource(R.string.live_radio)
        else -> MuxGridColors.EmeraldOk to stringResource(R.string.playback_info_status_playing_live)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CanvasPhone)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Text(
                text = statusText,
                color = MuxGridColors.TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val rightSummary = listOfNotNull(
            diagnostics.transportLabel,
            diagnostics.networkRateBps?.let { formatBitrateBps(it) },
        ).joinToString(" · ")
        if (rightSummary.isNotBlank()) {
            Text(
                text = rightSummary,
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.AccentSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class InfoTone {
    NORMAL,
    ACCENT,
    GOOD,
    WARN,
    BAD,
}

private data class InfoRow(
    val label: String,
    val value: String,
    val tone: InfoTone = InfoTone.NORMAL,
)

private data class InfoSection(
    val id: String,
    val title: String,
    val rows: List<InfoRow>,
    val bufferFillFraction: Float? = null,
)

@Composable
private fun InfoSectionCard(
    section: InfoSection,
    diagnostics: PlaybackDiagnostics,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CanvasPhone)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = section.title,
            style = MuxGridType.Eyebrow,
        )
        section.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    color = MuxGridColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = row.value,
                    style = MuxGridType.TabularSmall,
                    color = when (row.tone) {
                        InfoTone.NORMAL -> MuxGridColors.TextPrimary
                        InfoTone.ACCENT -> MuxGridColors.AccentSoft
                        InfoTone.GOOD -> MuxGridColors.EmeraldOk
                        InfoTone.WARN -> MuxGridColors.AmberWarn
                        InfoTone.BAD -> MuxGridColors.CoralLive
                    },
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (section.bufferFillFraction != null) {
            Spacer(Modifier.height(2.dp))
            LiveProgressBar(
                progress = section.bufferFillFraction,
                fillColor = if (diagnostics.cacheUnderrun) MuxGridColors.CoralLive else MuxGridColors.Accent,
                height = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun buildPlaybackInfoSections(
    state: PlaybackState,
    diag: PlaybackDiagnostics,
    hwDecPreset: HwDecPreset,
    resources: Resources? = null,
): List<InfoSection> {
    fun str(@StringRes id: Int, fallback: String): String = resources?.getString(id) ?: fallback
    val sections = ArrayList<InfoSection>(5)

    // 1. Stream & Transport
    val streamRows = buildList {
        add(
            InfoRow(
                label = str(R.string.playback_info_row_mode, "Mode"),
                value = when {
                    state.isRecordingPlayback && state.isGrowingRecording -> str(R.string.playback_info_mode_growing, "Growing Recording")
                    state.isRecordingPlayback -> str(R.string.playback_info_mode_dvr, "Recorded DVR")
                    state.isRadio -> str(R.string.live_radio, "Live Radio")
                    state.isAudioOnly -> str(R.string.playback_info_mode_bg_audio, "Background Audio")
                    else -> str(R.string.live_tv, "Live TV")
                },
            ),
        )
        diag.transportLabel?.let { add(InfoRow(str(R.string.playback_info_row_transport, "Transport"), it)) }
        diag.containerFormat?.let { add(InfoRow(str(R.string.playback_info_row_container, "Container"), it.uppercase(Locale.US))) }
        diag.upstreamHost?.let { host ->
            val statusSuffix = diag.upstreamStatus?.let { " ($it)" }.orEmpty()
            add(InfoRow(str(R.string.playback_info_row_upstream, "Upstream"), "$host$statusSuffix"))
        }
        if (abs(diag.playbackSpeed - 1.0) > 0.01) {
            add(InfoRow(str(R.string.playback_info_row_speed, "Speed"), String.format(Locale.US, "%.2fx", diag.playbackSpeed)))
        }
        val activeAudio = state.selectedAudioId?.let { id ->
            state.audioTracks.firstOrNull { it.id == id }
        } ?: state.audioTracks.firstOrNull { it.isSelected }
        activeAudio?.let { add(InfoRow(str(R.string.playback_info_row_audio_track, "Audio Track"), it.displayLabel)) }
        val activeSub = state.selectedSubtitleId?.let { id ->
            state.subtitleTracks.firstOrNull { it.id == id }
        }
        add(InfoRow(str(R.string.playback_info_row_subtitles, "Subtitles"), activeSub?.displayLabel ?: str(R.string.common_off, "Off")))
    }
    sections += InfoSection("stream", str(R.string.playback_info_section_stream, "STREAM & TRANSPORT"), streamRows)

    // 2. Video & Decoder
    if (!state.isRadio) {
        val videoRows = buildList {
            if (state.isAudioOnly) {
                add(InfoRow(str(R.string.playback_info_row_video, "Video"), str(R.string.playback_info_video_disabled, "Disabled (Audio-only)"), InfoTone.ACCENT))
            } else {
                diag.videoCodec?.let { add(InfoRow(str(R.string.playback_info_row_codec, "Codec"), it.uppercase(Locale.US))) }
                val w = diag.videoWidth.takeIf { it > 0 } ?: state.videoWidth
                val h = diag.videoHeight.takeIf { it > 0 } ?: state.videoHeight
                if (w > 0 && h > 0) {
                    val resText = if (diag.displayWidth > 0 && diag.displayHeight > 0 &&
                        (diag.displayWidth != w || diag.displayHeight != h)
                    ) {
                        "${w}x${h} -> ${diag.displayWidth}x${diag.displayHeight}"
                    } else {
                        "${w}x${h}"
                    }
                    add(InfoRow(str(R.string.playback_info_row_resolution, "Resolution"), resText))
                }
                val actualFps = diag.actualFps
                val containerFps = diag.containerFps
                val fpsValue = when {
                    actualFps != null && containerFps != null &&
                        abs(actualFps - containerFps) >= 0.5 ->
                        String.format(Locale.US, "%.2f / %.2f fps", actualFps, containerFps)
                    actualFps != null ->
                        String.format(Locale.US, "%.2f fps", actualFps)
                    containerFps != null ->
                        String.format(Locale.US, "%.2f fps", containerFps)
                    else -> null
                }
                fpsValue?.let { add(InfoRow(str(R.string.playback_info_row_frame_rate, "Frame Rate"), it)) }
                diag.videoBitrateBps?.let { add(InfoRow(str(R.string.playback_info_row_video_bitrate, "Video Bitrate"), formatBitrateBps(it))) }

                val hwdec = diag.hwdecCurrent ?: state.hwdecCurrent
                val hwLabel = when {
                    hwdec.isNullOrBlank() || hwdec == "no" -> str(R.string.playback_info_decoder_software, "Software (CPU)")
                    else -> hwdec
                }
                val hwTone = if (hwdec.isNullOrBlank() || hwdec == "no") InfoTone.WARN else InfoTone.GOOD
                add(InfoRow(str(R.string.playback_info_row_decoder, "Decoder"), "$hwLabel (${presetShortName(hwDecPreset)})", hwTone))

                val deintActive = diag.deinterlaceActive || state.deinterlaceActive
                add(
                    InfoRow(
                        label = str(R.string.playback_info_row_deinterlace, "Deinterlace"),
                        value = if (deintActive) str(R.string.common_active, "Active") else str(R.string.common_off, "Off"),
                        tone = if (deintActive) InfoTone.ACCENT else InfoTone.NORMAL,
                    ),
                )

                val fmtParts = listOfNotNull(
                    diag.hwPixelFormat ?: diag.pixelFormat,
                    diag.colorInfo,
                )
                if (fmtParts.isNotEmpty()) {
                    add(InfoRow(str(R.string.playback_info_row_pixel_color, "Pixel / Color"), fmtParts.joinToString(" · ")))
                }
                diag.videoOutput?.let { add(InfoRow(str(R.string.playback_info_row_video_out, "Video Out"), it)) }

                val voDrops = diag.droppedFramesVo ?: 0L
                val decDrops = diag.droppedFramesDecoder ?: 0L
                val delayed = diag.delayedFramesVo ?: 0L
                if (diag.droppedFramesVo != null || diag.droppedFramesDecoder != null) {
                    val dropText = buildString {
                        append("$voDrops VO · $decDrops dec")
                        if (delayed > 0L) append(" · $delayed delayed")
                    }
                    val totalDrops = voDrops + decDrops
                    val dropTone = when {
                        totalDrops == 0L -> InfoTone.GOOD
                        totalDrops < 10L -> InfoTone.WARN
                        else -> InfoTone.BAD
                    }
                    add(InfoRow(str(R.string.playback_info_row_dropped_frames, "Dropped Frames"), dropText, dropTone))
                }
                diag.avSyncMs?.let { syncMs ->
                    val syncTone = if (abs(syncMs) > 80.0) InfoTone.WARN else InfoTone.NORMAL
                    add(InfoRow(str(R.string.playback_info_row_av_sync, "A/V Sync"), String.format(Locale.US, "%+.1f ms", syncMs), syncTone))
                }
            }
        }
        if (videoRows.isNotEmpty()) {
            sections += InfoSection("video", str(R.string.playback_info_section_video, "VIDEO & DECODER"), videoRows)
        }
    }

    // 3. Audio
    val audioRows = buildList {
        diag.audioCodec?.let { add(InfoRow(str(R.string.playback_info_row_codec, "Codec"), it.uppercase(Locale.US))) }
        val chRate = listOfNotNull(
            diag.audioChannels,
            diag.audioSampleRateHz?.let { String.format(Locale.US, "%.1f kHz", it / 1000.0) },
        ).joinToString(" · ")
        if (chRate.isNotBlank()) add(InfoRow(str(R.string.playback_info_row_format, "Format"), chRate))
        diag.audioBitrateBps?.let { add(InfoRow(str(R.string.playback_info_row_audio_bitrate, "Audio Bitrate"), formatBitrateBps(it))) }
        diag.audioOutput?.let { add(InfoRow(str(R.string.playback_info_row_audio_out, "Audio Out"), it)) }
    }
    if (audioRows.isNotEmpty()) {
        sections += InfoSection("audio", str(R.string.playback_info_section_audio, "AUDIO"), audioRows)
    }

    // 4. Network & Cache
    val totalCacheBudgetBytes = (diag.cacheBudgetPerDirBytes * 2L).coerceAtLeast(0L)
    val ramCacheUsed = diag.cacheUsedBytes
    val diskBytes = diag.diskCacheBytes
    val effectiveCacheUsed = when {
        diag.cacheOnDisk && diskBytes != null && diskBytes > 0L -> maxOf(ramCacheUsed ?: 0L, diskBytes)
        else -> ramCacheUsed
    }
    val bufferFill = if (totalCacheBudgetBytes > 0L && effectiveCacheUsed != null) {
        (effectiveCacheUsed.toFloat() / totalCacheBudgetBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val netCacheRows = buildList {
        diag.networkRateBps?.let {
            add(InfoRow(str(R.string.playback_info_row_network_rate, "Network Rate"), formatBitrateBps(it), InfoTone.ACCENT))
        }
        diag.demuxerInputRateBps?.let {
            add(InfoRow(str(R.string.playback_info_row_demuxer_read, "Demuxer Read"), formatBitrateBps(it)))
        }
        add(InfoRow(str(R.string.playback_info_row_data_received, "Data Received"), formatByteCount(diag.totalBytesReceived)))
        add(
            InfoRow(
                str(R.string.playback_info_row_cache_mode, "Cache Mode"),
                if (diag.cacheOnDisk) str(R.string.playback_info_cache_mode_disk, "Disk Ring Buffer") else str(R.string.playback_info_cache_mode_ram, "RAM Buffer"),
            ),
        )
        if (diag.cacheOnDisk) {
            val diskText = when {
                diskBytes != null && diskBytes > 0L -> {
                    val budgetText = if (totalCacheBudgetBytes > 0L) " / ${formatByteCount(totalCacheBudgetBytes)}" else ""
                    "${formatByteCount(diskBytes)}$budgetText"
                }
                state.bufferWindowMs > 0L -> {
                    val budgetText = if (totalCacheBudgetBytes > 0L) " (budget ${formatByteCount(totalCacheBudgetBytes)})" else ""
                    "${str(R.string.common_active, "Active")}$budgetText"
                }
                else -> "0 B"
            }
            add(InfoRow(str(R.string.playback_info_row_disk_cache, "Disk Cache"), diskText))
            if (ramCacheUsed != null) {
                add(InfoRow(str(R.string.playback_info_row_ram_hot_cache, "RAM Hot Cache"), formatByteCount(ramCacheUsed)))
            }
        } else if (ramCacheUsed != null) {
            val budgetText = if (totalCacheBudgetBytes > 0L) " / ${formatByteCount(totalCacheBudgetBytes)}" else ""
            add(InfoRow(str(R.string.playback_info_row_cache_used, "Cache Used"), "${formatByteCount(ramCacheUsed)}$budgetText"))
        }
        if (diag.cacheForwardBytes != null || diag.cacheBackBytes != null) {
            val ahead = diag.cacheForwardBytes?.let { formatByteCount(it) } ?: "0 B"
            val rewind = diag.cacheBackBytes?.let { formatByteCount(it) } ?: "0 B"
            val label = if (diag.cacheOnDisk) {
                str(R.string.playback_info_row_ram_ahead_rewind, "RAM Ahead / Rewind")
            } else {
                str(R.string.playback_info_row_ahead_rewind, "Ahead / Rewind")
            }
            add(InfoRow(label, "$ahead / $rewind"))
        }
        val aheadSec = diag.cacheDurationSec?.toLong()?.coerceAtLeast(0L)
        val windowSec = (state.bufferWindowMs / 1000L).takeIf { it > 0L }
        val bufferedTimeText = when {
            aheadSec != null && windowSec != null && kotlin.math.abs(windowSec - aheadSec) > 1L ->
                "${formatDurationSec(aheadSec)} ahead · ${formatDurationSec(windowSec)} window"
            aheadSec != null -> formatDurationSec(aheadSec)
            windowSec != null -> formatDurationSec(windowSec)
            else -> null
        }
        bufferedTimeText?.let { text ->
            add(
                InfoRow(
                    label = str(R.string.playback_info_row_buffered_time, "Buffered Time"),
                    value = text,
                    tone = if (diag.cacheUnderrun) InfoTone.BAD else InfoTone.NORMAL,
                ),
            )
        }
        if (diag.cacheUnderrun) {
            add(InfoRow(str(R.string.playback_info_row_buffer_health, "Buffer Health"), str(R.string.playback_info_buffer_underrun, "Underrun"), InfoTone.BAD))
        }
    }
    sections += InfoSection("net_cache", str(R.string.playback_info_section_net_cache, "NETWORK & CACHE"), netCacheRows, bufferFillFraction = bufferFill)

    // 5. Device / App Usage
    val usageRows = buildList {
        diag.processCpuPercent?.let { cpu ->
            val coresLabel = if (diag.cpuCores > 0) " (${diag.cpuCores} cores)" else ""
            val cpuTone = when {
                cpu >= 75.0 -> InfoTone.WARN
                else -> InfoTone.NORMAL
            }
            add(InfoRow(str(R.string.playback_info_row_app_cpu, "App CPU"), String.format(Locale.US, "%.1f%%%s", cpu, coresLabel), cpuTone))
        }
        diag.processPssMb?.let { pss ->
            val nativeSuffix = diag.nativePssMb?.let { n ->
                String.format(Locale.US, " (native %.0f MB)", n)
            }.orEmpty()
            add(InfoRow(str(R.string.playback_info_row_process_memory, "Process Memory"), String.format(Locale.US, "%.0f MB PSS%s", pss, nativeSuffix)))
        }
        if (diag.jvmHeapUsedMb != null && diag.jvmHeapMaxMb != null) {
            add(
                InfoRow(
                    label = str(R.string.playback_info_row_jvm_heap, "JVM Heap"),
                    value = String.format(Locale.US, "%.1f / %.0f MB", diag.jvmHeapUsedMb, diag.jvmHeapMaxMb),
                ),
            )
        }
    }
    if (usageRows.isNotEmpty()) {
        sections += InfoSection("usage", str(R.string.playback_info_section_usage, "APP USAGE"), usageRows)
    }

    return sections
}

fun formatPlaybackInfoReport(
    playbackState: PlaybackState,
    diagnostics: PlaybackDiagnostics,
    hwDecPreset: HwDecPreset,
): String {
    val sections = buildPlaybackInfoSections(playbackState, diagnostics, hwDecPreset)
    return buildString {
        val headerName = playbackState.displayTitle(
            recordingFallback = playbackState.channelName ?: "Recording",
            liveFallback = "Live Stream",
        )
        appendLine("MuxGrid Playback Info — $headerName")
        if (diagnostics.sessionUptimeMs > 0L) {
            appendLine("Session Uptime: ${formatDurationSec(diagnostics.sessionUptimeMs / 1000L)}")
        }
        sections.forEach { section ->
            appendLine()
            appendLine("[${section.title}]")
            section.rows.forEach { row ->
                appendLine("${row.label}: ${row.value}")
            }
        }
    }.trimEnd()
}

private fun presetShortName(preset: HwDecPreset): String = when (preset) {
    HwDecPreset.MEDIACODEC_ZERO_COPY -> "zero-copy"
    HwDecPreset.MEDIACODEC_COPY_BWDIF -> "hw-copy + bwdif"
}

internal fun formatBitrateBps(bps: Long): String {
    val clamped = bps.coerceAtLeast(0L)
    return when {
        clamped < 1_000L -> "$clamped bps"
        clamped < 1_000_000L -> String.format(Locale.US, "%.0f kbps", clamped / 1_000.0)
        else -> String.format(Locale.US, "%.2f Mbps", clamped / 1_000_000.0)
    }
}
