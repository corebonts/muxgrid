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
    val liveEdgeOffsetMs: Long = 0L,
    val bufferWindowMs: Long = 0L,
    val isTimeshiftSeekable: Boolean = false,
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<MediaTrack> = emptyList(),
    val selectedAudioId: Int? = null,
    val selectedSubtitleId: Int? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val fps: Double = 0.0,
    val hwdecCurrent: String? = null,
    val deinterlaceActive: Boolean = false,
    val error: PlaybackError? = null,
) {
    val isRecordingPlayback: Boolean
        get() = dvrEntryId != null
}
