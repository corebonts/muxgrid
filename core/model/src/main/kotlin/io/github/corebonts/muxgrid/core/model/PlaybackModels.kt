package io.github.corebonts.muxgrid.core.model

enum class TrackType {
    AUDIO,
    SUBTITLE,
}

data class MediaTrack(
    val id: Int,
    val type: TrackType,
    val title: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val isSelected: Boolean = false,
    val isDefault: Boolean = false,
) {
    val displayLabel: String
        get() {
            val parts = buildList {
                if (!language.isNullOrBlank()) add(language.uppercase())
                if (!title.isNullOrBlank() && !title.equals(language, ignoreCase = true)) add(title)
                if (!codec.isNullOrBlank()) add(codec.uppercase())
            }
            return if (parts.isNotEmpty()) parts.joinToString(" · ") else "Track $id"
        }
}

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR,
}

sealed interface PlaybackError {
    val message: String

    data class TunerBusy(
        val recordingTitle: String?,
        val channelName: String?,
    ) : PlaybackError {
        override val message: String
            get() = when {
                !recordingTitle.isNullOrBlank() && !channelName.isNullOrBlank() ->
                    "Tuner busy: recording \"$recordingTitle\" on $channelName"
                !recordingTitle.isNullOrBlank() ->
                    "Tuner busy: recording \"$recordingTitle\""
                else -> "Tuner busy: another recording is using the tuner"
            }
    }

    data class StreamOpenFailed(
        override val message: String,
    ) : PlaybackError

    data class PlaybackFailed(
        override val message: String,
    ) : PlaybackError
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val channelId: Long? = null,
    val dvrEntryId: Long? = null,
    val channelNumber: String? = null,
    val channelName: String? = null,
    val programTitle: String? = null,
    val isRadio: Boolean = false,
    val isPaused: Boolean = false,
    val isSeekable: Boolean = false,
    val isCompleted: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferStartMs: Long = 0L,
    val bufferEndMs: Long = 0L,
    val liveEdgeOffsetMs: Long = 0L,
    val bufferWindowMs: Long = 0L,
    val isTimeshiftSeekable: Boolean = false,
    val isGrowingRecording: Boolean = false,
    val timeshiftNotice: String? = null,
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<MediaTrack> = emptyList(),
    val selectedAudioId: Int? = null,
    val selectedSubtitleId: Int? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val fps: Double = 0.0,
    val hwdecCurrent: String? = null,
    val deinterlaceActive: Boolean = false,
    val isAudioOnly: Boolean = false,
    val aspectMode: VideoAspectMode = VideoAspectMode.FIT,
    val error: PlaybackError? = null,
) {
    val isRecordingPlayback: Boolean
        get() = dvrEntryId != null

    val isBehindLive: Boolean
        get() = !isRecordingPlayback && isTimeshiftSeekable &&
            (liveEdgeOffsetMs >= 5_000L || (isPaused && liveEdgeOffsetMs >= 2_000L))
}

data class PlaybackDiagnostics(
    val transportLabel: String? = null,
    val containerFormat: String? = null,
    val upstreamHost: String? = null,
    val upstreamStatus: String? = null,
    val sessionUptimeMs: Long = 0L,
    val playbackSpeed: Double = 1.0,
    val pausedForCache: Boolean = false,
    val cacheBufferingPercent: Int? = null,
    val videoCodec: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val pixelFormat: String? = null,
    val hwPixelFormat: String? = null,
    val colorInfo: String? = null,
    val containerFps: Double? = null,
    val actualFps: Double? = null,
    val videoBitrateBps: Long? = null,
    val hwdecCurrent: String? = null,
    val deinterlaceActive: Boolean = false,
    val videoOutput: String? = null,
    val droppedFramesVo: Long? = null,
    val droppedFramesDecoder: Long? = null,
    val delayedFramesVo: Long? = null,
    val avSyncMs: Double? = null,
    val audioCodec: String? = null,
    val audioChannels: String? = null,
    val audioSampleRateHz: Int? = null,
    val audioBitrateBps: Long? = null,
    val audioOutput: String? = null,
    val networkRateBps: Long? = null,
    val demuxerInputRateBps: Long? = null,
    val totalBytesReceived: Long = 0L,
    val cacheUsedBytes: Long? = null,
    val cacheForwardBytes: Long? = null,
    val cacheBackBytes: Long? = null,
    val cacheDurationSec: Double? = null,
    val cacheBudgetPerDirBytes: Long = 0L,
    val cacheOnDisk: Boolean = false,
    val diskCacheBytes: Long? = null,
    val cacheUnderrun: Boolean = false,
    val processCpuPercent: Double? = null,
    val cpuCores: Int = 0,
    val processPssMb: Double? = null,
    val nativePssMb: Double? = null,
    val jvmHeapUsedMb: Double? = null,
    val jvmHeapMaxMb: Double? = null,
)

/**
 * Abstraction over an open growing DVR file (backed by HTSP VFS `fileOpen`/`fileStat`/`fileRead`/`fileClose`).
 */
interface GrowingDvrFile : AutoCloseable {
    val initialSize: Long
    suspend fun statSize(): Long
    suspend fun read(offset: Long, size: Int): ByteArray
    override fun close()
}
