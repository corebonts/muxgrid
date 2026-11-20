package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ChannelTag
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.MediaTrack
import io.github.corebonts.muxgrid.core.model.PlaybackError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChannelPiconBadge(
    channelNumber: String,
    channelName: String,
    piconModel: Any?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF202433))
            .border(1.dp, MuxGridColors.HairlineBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (piconModel != null) {
            SubcomposeAsyncImage(
                model = piconModel,
                imageLoader = imageLoader,
                contentDescription = channelName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                error = {
                    PiconFallbackLabel(channelNumber, channelName)
                },
                loading = {
                    PiconFallbackLabel(channelNumber, channelName)
                },
            )
        } else {
            PiconFallbackLabel(channelNumber, channelName)
        }
    }
}

@Composable
private fun PiconFallbackLabel(
    channelNumber: String,
    channelName: String,
) {
    val text = if (channelNumber.isNotBlank() && channelNumber != "0") {
        channelNumber
    } else {
        channelName.take(3).uppercase()
    }
    Text(
        text = text,
        style = MuxGridType.TabularNumber,
        color = MuxGridColors.TextSecondary,
        maxLines = 1,
    )
}

@Composable
fun LiveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    fillColor: Color = MuxGridColors.AccentBlueLight,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(MuxGridColors.TrackBackground),
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(fillColor),
            )
        }
    }
}

@Composable
fun ConnectionStatusBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val (dotColor, label) = when (state) {
        ConnectionState.Disconnected -> MuxGridColors.TextMuted to "Offline"
        ConnectionState.Connecting -> MuxGridColors.AmberWarn to "Connecting…"
        is ConnectionState.Syncing -> MuxGridColors.AmberWarn to "Syncing…"
        is ConnectionState.Connected -> MuxGridColors.EmeraldOk to state.serverName
        is ConnectionState.Error -> MuxGridColors.CoralLive to "Error"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MuxGridColors.CardSurface)
            .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MuxGridColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlayerErrorToast(
    error: PlaybackError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTunerBusy = error is PlaybackError.TunerBusy
    val title = if (isTunerBusy) "Tuner Busy" else "Playback Error"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MuxGridColors.ScrimSurface)
            .border(
                width = 1.dp,
                color = MuxGridColors.CoralLive.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MuxGridColors.CoralLive),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = MuxGridColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = error.message,
                color = MuxGridColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TrackAndPresetPanel(
    audioTracks: List<MediaTrack>,
    subtitleTracks: List<MediaTrack>,
    selectedAudioId: Int?,
    selectedSubtitleId: Int?,
    hwDecPreset: HwDecPreset,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeFocusRequester = remember { FocusRequester() }
    var closeFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { closeFocusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .heightIn(max = 460.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MuxGridColors.ScrimSurface)
            .border(1.dp, MuxGridColors.HairlineBorder, RoundedCornerShape(18.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tracks & Decoder", style = MuxGridType.Title, fontSize = 17.sp)
            Text(
                text = "Close",
                color = if (closeFocused) MuxGridColors.CanvasTv else MuxGridColors.AccentBlueLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .focusRequester(closeFocusRequester)
                    .onFocusChanged { closeFocused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (closeFocused) MuxGridColors.FocusBorder else Color.Transparent)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Text("Audio", style = MuxGridType.CardSubtitle, fontWeight = FontWeight.SemiBold)
        if (audioTracks.isEmpty()) {
            Text("No audio tracks detected yet", style = MuxGridType.TabularSmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                audioTracks.forEach { track ->
                    val selected = track.id == selectedAudioId || track.isSelected
                    SelectableOptionRow(
                        label = track.displayLabel,
                        selected = selected,
                        onClick = { onSelectAudio(track.id) },
                    )
                }
            }
        }

        Text("Subtitles (DVB / Text)", style = MuxGridType.CardSubtitle, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectableOptionRow(
                label = "Off",
                selected = selectedSubtitleId == null,
                onClick = { onSelectSubtitle(null) },
            )
            subtitleTracks.forEach { track ->
                val selected = track.id == selectedSubtitleId || track.isSelected
                SelectableOptionRow(
                    label = track.displayLabel,
                    selected = selected,
                    onClick = { onSelectSubtitle(track.id) },
                )
            }
        }

        Text("Hardware Decode & Deinterlacer", style = MuxGridType.CardSubtitle, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectableOptionRow(
                label = "Hardware Zero-Copy (mediacodec — TV Default)",
                selected = hwDecPreset == HwDecPreset.MEDIACODEC_ZERO_COPY,
                onClick = { onSelectPreset(HwDecPreset.MEDIACODEC_ZERO_COPY) },
            )
            SelectableOptionRow(
                label = "Hardware Copy + bwdif Deinterlace (Phone Default)",
                selected = hwDecPreset == HwDecPreset.MEDIACODEC_COPY_BWDIF,
                onClick = { onSelectPreset(HwDecPreset.MEDIACODEC_COPY_BWDIF) },
            )
        }
    }
}

@Composable
private fun SelectableOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MuxGridColors.CardSurfaceElevated
        selected -> MuxGridColors.AccentBlue.copy(alpha = 0.25f)
        else -> MuxGridColors.CardSurface
    }
    val borderColor = when {
        focused -> MuxGridColors.FocusBorder
        selected -> MuxGridColors.AccentBlueLight
        else -> MuxGridColors.HairlineBorder
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MuxGridColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Active",
                color = MuxGridColors.AccentBlueLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

fun filterChannels(
    channels: List<Channel>,
    filter: LiveFilter,
    favorites: Set<Long>,
): List<Channel> = when (filter) {
    LiveFilter.All -> channels
    LiveFilter.Tv -> channels.filter { !it.isRadio }
    LiveFilter.Radio -> channels.filter { it.isRadio }
    LiveFilter.Favorites -> channels.filter { it.id in favorites }
    is LiveFilter.Tag -> channels.filter { filter.tagId in it.tagIds }
}

fun buildLiveFilters(tags: List<ChannelTag>): List<LiveFilter> {
    val base = mutableListOf<LiveFilter>(
        LiveFilter.All,
        LiveFilter.Tv,
        LiveFilter.Radio,
        LiveFilter.Favorites,
    )
    tags.forEach { tag ->
        base.add(LiveFilter.Tag(tag.id, tag.name))
    }
    return base
}

fun LiveFilter.label(): String = when (this) {
    LiveFilter.All -> "All"
    LiveFilter.Tv -> "TV"
    LiveFilter.Radio -> "Radio"
    LiveFilter.Favorites -> "Favorites"
    is LiveFilter.Tag -> name
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatEpochHm(epochSec: Long): String =
    Instant.ofEpochSecond(epochSec)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FMT)

@Composable
fun DvrStateBadge(
    state: DvrState,
    isWatched: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when {
        state == DvrState.RECORDING -> MuxGridColors.CoralLive to "REC"
        state == DvrState.SCHEDULED -> MuxGridColors.AccentBlueLight to "Scheduled"
        state == DvrState.COMPLETED && isWatched -> MuxGridColors.EmeraldOk to "Watched"
        state == DvrState.MISSED -> MuxGridColors.AmberWarn to "Missed"
        state == DvrState.INVALID -> MuxGridColors.CoralLive to "Failed"
        else -> return
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (state == DvrState.RECORDING) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun TimeshiftNoticeBanner(
    notice: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MuxGridColors.ScrimSurface)
            .border(1.dp, MuxGridColors.AmberWarn.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MuxGridColors.AmberWarn),
        )
        Text(
            text = notice,
            color = MuxGridColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
