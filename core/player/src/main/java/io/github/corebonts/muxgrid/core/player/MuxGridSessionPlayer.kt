package io.github.corebonts.muxgrid.core.player

import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.corebonts.muxgrid.core.model.PlaybackSeekDefaults
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Media3 [SimpleBasePlayer] adapter exposing [PlaybackEngine] to [MuxGridMediaSessionService]
 * for system media notifications, lock-screen controls, and Bluetooth/headset media keys.
 */
@OptIn(UnstableApi::class)
internal class MuxGridSessionPlayer(
    private val playbackEngine: PlaybackEngine,
    private val canZap: () -> Boolean,
    private val onZapNext: () -> Unit,
    private val onZapPrevious: () -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    private data class SnapshotKey(
        val status: PlaybackStatus,
        val isPaused: Boolean,
        val channelId: Long?,
        val dvrEntryId: Long?,
        val channelName: String?,
        val programTitle: String?,
        val isRadio: Boolean,
        val seekable: Boolean,
        val durationSec: Long,
        val errorMsg: String?,
    )

    private var lastSnapshotKey: SnapshotKey? = null
    private var lastNotifiedPosMs: Long = 0L
    private var lastNotifiedElapsedMs: Long = 0L
    private var lastNotifiedPlaying: Boolean = false

    init {
        stateJob = scope.launch {
            playbackEngine.state.collect { st ->
                val isPlaying = st.status == PlaybackStatus.PLAYING && !st.isPaused
                val key = SnapshotKey(
                    status = st.status,
                    isPaused = st.isPaused,
                    channelId = st.channelId,
                    dvrEntryId = st.dvrEntryId,
                    channelName = st.channelName,
                    programTitle = st.programTitle,
                    isRadio = st.isRadio,
                    seekable = st.isRecordingPlayback || st.isTimeshiftSeekable,
                    durationSec = when {
                        st.isGrowingRecording -> (st.durationMs / 10_000L) * 10L
                        st.isRecordingPlayback -> st.durationMs / 1_000L
                        else -> 0L
                    },
                    errorMsg = st.error?.message,
                )
                val nowElapsed = SystemClock.elapsedRealtime()
                val expectedPosMs = if (lastNotifiedPlaying) {
                    lastNotifiedPosMs + (nowElapsed - lastNotifiedElapsedMs).coerceAtLeast(0L)
                } else {
                    lastNotifiedPosMs
                }
                val posJump = abs(st.positionMs - expectedPosMs) >= 3_000L
                if (key != lastSnapshotKey || posJump) {
                    lastSnapshotKey = key
                    lastNotifiedPosMs = st.positionMs
                    lastNotifiedElapsedMs = nowElapsed
                    lastNotifiedPlaying = isPlaying
                    invalidateState()
                }
            }
        }
    }

    override fun getState(): State {
        val st = playbackEngine.state.value
        val hasItem = st.channelId != null || st.dvrEntryId != null
        val isActive = hasItem && st.status != PlaybackStatus.IDLE && st.status != PlaybackStatus.ERROR
        val isSeekable = st.isRecordingPlayback || st.isTimeshiftSeekable
        val isLive = hasItem && !st.isRecordingPlayback

        val commandsBuilder = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE,
            )
        if (isSeekable) {
            commandsBuilder.addAll(
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
            )
        }
        if (isLive && canZap()) {
            commandsBuilder.addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
        }

        val media3State = when (st.status) {
            PlaybackStatus.IDLE -> Player.STATE_IDLE
            PlaybackStatus.BUFFERING -> Player.STATE_BUFFERING
            PlaybackStatus.PLAYING, PlaybackStatus.PAUSED -> Player.STATE_READY
            PlaybackStatus.ERROR -> Player.STATE_IDLE
        }

        val playWhenReady = isActive && !st.isPaused
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(if (st.isRadio) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val builder = State.Builder()
            .setAvailableCommands(commandsBuilder.build())
            .setPlaybackState(media3State)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setIsLoading(st.status == PlaybackStatus.BUFFERING)
            .setSeekBackIncrementMs(PlaybackSeekDefaults.SEEK_BACK_STEP_MS)
            .setSeekForwardIncrementMs(PlaybackSeekDefaults.SEEK_FORWARD_STEP_MS)
            .setAudioAttributes(audioAttributes)

        st.error?.let { err ->
            builder.setPlayerError(
                PlaybackException(
                    err.message,
                    null,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                ),
            )
        }

        if (hasItem) {
            val itemData = buildMediaItemData(st, isSeekable, isLive)
            builder.setPlaylist(listOf(itemData))
            builder.setCurrentMediaItemIndex(0)
            val speed = if (st.status == PlaybackStatus.PLAYING && !st.isPaused) 1f else 0f
            val basePosMs = st.positionMs.coerceAtLeast(0L)
            builder.setContentPositionMs(
                PositionSupplier.getExtrapolating(basePosMs, speed),
            )
        } else {
            builder.setPlaylist(emptyList())
        }

        return builder.build()
    }

    private fun buildMediaItemData(
        st: PlaybackState,
        isSeekable: Boolean,
        isLive: Boolean,
    ): MediaItemData {
        val channelLabel = listOfNotNull(
            st.channelNumber?.takeIf { it.isNotBlank() && it != "0" },
            st.channelName,
        ).joinToString(" · ").ifBlank { "MuxGrid" }

        val title = st.programTitle?.takeIf { it.isNotBlank() } ?: channelLabel
        val subtitle = if (st.programTitle.isNullOrBlank()) {
            if (st.isRadio) "Live Radio" else "Live TV"
        } else {
            channelLabel
        }

        val mediaType = when {
            st.isRadio -> MediaMetadata.MEDIA_TYPE_RADIO_STATION
            st.isRecordingPlayback -> MediaMetadata.MEDIA_TYPE_VIDEO
            else -> MediaMetadata.MEDIA_TYPE_TV_SHOW
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtist(subtitle)
            .setDisplayTitle(title)
            .setMediaType(mediaType)
            .setIsPlayable(true)
            .build()

        val mediaId = st.dvrEntryId?.let { "dvr:$it" } ?: "ch:${st.channelId ?: 0L}"
        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()

        val durationUs = if (st.isRecordingPlayback && st.durationMs > 0L) st.durationMs * 1000L else C.TIME_UNSET
        return MediaItemData.Builder(mediaId)
            .setMediaItem(mediaItem)
            .setMediaMetadata(metadata)
            .setIsSeekable(isSeekable)
            .setIsDynamic(isLive)
            .setDurationUs(durationUs)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playbackEngine.setPaused(!playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playbackEngine.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: @Player.Command Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> onZapNext()
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> onZapPrevious()
            Player.COMMAND_SEEK_BACK -> playbackEngine.seekBy(-PlaybackSeekDefaults.SEEK_BACK_STEP_MS)
            Player.COMMAND_SEEK_FORWARD -> playbackEngine.seekBy(PlaybackSeekDefaults.SEEK_FORWARD_STEP_MS)
            else -> {
                if (positionMs != C.TIME_UNSET) {
                    playbackEngine.seekTo(positionMs)
                }
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        stateJob?.cancel()
        scope.cancel()
        return Futures.immediateVoidFuture()
    }
}
