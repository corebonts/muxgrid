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
    val isSeeking: Boolean = false,
    val seekTargetMs: Long? = null,
    val bufferedPositionMs: Long = 0L,
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
            (liveEdgeOffsetMs >= 5_000L || (isPaused && liveEdgeOffsetMs >= PlaybackSeekDefaults.LIVE_EDGE_DISPLAY_THRESHOLD_MS))

    /**
     * Clamps a requested seek target in milliseconds to valid, non-stalling bounds for the
     * current playback mode (growing DVR write head, completed recording, or live timeshift buffer).
     */
    fun clampSeekPositionMs(requestedMs: Long): Long {
        val nonNeg = requestedMs.coerceAtLeast(0L)
        return when {
            isGrowingRecording -> {
                val maxSafe = (durationMs - PlaybackSeekDefaults.GROWING_WRITE_HEAD_SAFETY_MS).coerceAtLeast(0L)
                nonNeg.coerceAtMost(maxSafe)
            }
            isRecordingPlayback && durationMs > 0L -> {
                nonNeg.coerceAtMost(durationMs)
            }
            !isRecordingPlayback && isTimeshiftSeekable -> {
                val minStart = bufferStartMs.coerceAtLeast(0L)
                val maxEnd = (bufferEndMs - PlaybackSeekDefaults.LIVE_EDGE_SAFETY_MS).coerceAtLeast(minStart)
                nonNeg.coerceIn(minStart, maxEnd)
            }
            else -> nonNeg
        }
    }

    /**
     * Returns true when [requestedMs] would exceed the safe write-head margin of an ongoing recording.
     */
    fun isSeekClampedAtGrowingEdge(requestedMs: Long): Boolean {
        if (!isGrowingRecording || durationMs <= 0L) return false
        val maxSafe = (durationMs - PlaybackSeekDefaults.GROWING_WRITE_HEAD_SAFETY_MS).coerceAtLeast(0L)
        return requestedMs > maxSafe
    }
}

object PlaybackSeekDefaults {
    const val SEEK_BACK_STEP_MS = 10_000L
    const val SEEK_FORWARD_STEP_MS = 30_000L
    const val GROWING_WRITE_HEAD_SAFETY_MS = 2_000L
    const val LIVE_EDGE_SAFETY_MS = 1_500L
    const val LIVE_EDGE_SAFETY_SEC = LIVE_EDGE_SAFETY_MS / 1000.0
    const val LIVE_EDGE_DISPLAY_THRESHOLD_MS = 2_000L
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
 * Persisted subtitle playback preference.
 *
 * When [enabled] is false, `sid=no` is applied to streams. When true, `sid=auto` is used
 * (with [preferredLanguage] passed to `slang` when present and valid).
 */
data class SubtitlePreference(
    val enabled: Boolean = true,
    val preferredLanguage: String? = null,
) {
    val sanitizedLanguage: String?
        get() = sanitizeLanguage(preferredLanguage)

    companion object {
        private val LANGUAGE_REGEX = Regex("^[a-zA-Z]{2,3}(?:[-_][a-zA-Z0-9]{1,8})*$")

        fun sanitizeLanguage(raw: String?): String? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (trimmed.equals("und", ignoreCase = true)) return null
            return trimmed.takeIf { LANGUAGE_REGEX.matches(it) }
        }
    }
}

/**
 * Abstraction over an open growing DVR file (backed by HTSP VFS `fileOpen`/`fileStat`/`fileRead`/`fileClose`).
 */
interface GrowingDvrFile : AutoCloseable {
    val initialSize: Long
    suspend fun statSize(): Long
    suspend fun read(offset: Long, size: Int): ByteArray
    override fun close()
}
