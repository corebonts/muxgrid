package io.github.corebonts.muxgrid.core.model

data class Channel(
    val id: Long,
    val number: Int,
    val numberMinor: Int = 0,
    val name: String,
    val icon: String? = null,
    val tagIds: List<Long> = emptyList(),
    val isRadio: Boolean = false,
    val eventId: Long? = null,
    val nextEventId: Long? = null,
) {
    val formattedNumber: String
        get() = if (numberMinor > 0) "$number.$numberMinor" else number.toString()
}

data class ChannelTag(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val index: Int = 0,
    val memberChannelIds: Set<Long> = emptySet(),
)

data class EpgEvent(
    val id: Long,
    val channelId: Long,
    val startEpochSec: Long,
    val stopEpochSec: Long,
    val title: String,
    val subtitle: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val nextEventId: Long? = null,
    val dvrId: Long? = null,
)

data class NowNextProgram(
    val now: EpgEvent? = null,
    val next: EpgEvent? = null,
) {
    fun progressFraction(nowEpochSec: Long): Float {
        val current = now ?: return 0f
        val duration = (current.stopEpochSec - current.startEpochSec).coerceAtLeast(1L)
        val elapsed = (nowEpochSec - current.startEpochSec).coerceIn(0L, duration)
        return elapsed.toFloat() / duration.toFloat()
    }

    fun remainingMinutes(nowEpochSec: Long): Int? {
        val current = now ?: return null
        val remSec = (current.stopEpochSec - nowEpochSec).coerceAtLeast(0L)
        return ((remSec + 59L) / 60L).toInt()
    }
}

enum class DvrState {
    SCHEDULED,
    RECORDING,
    COMPLETED,
    MISSED,
    INVALID,
    ;

    companion object {
        fun fromHtsp(raw: String?): DvrState = when (raw?.lowercase()) {
            "scheduled" -> SCHEDULED
            "recording" -> RECORDING
            "completed" -> COMPLETED
            "missed" -> MISSED
            else -> INVALID
        }
    }
}

data class DvrEntry(
    val id: Long,
    val channelId: Long,
    val startEpochSec: Long,
    val stopEpochSec: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val state: DvrState,
    val error: String? = null,
    val dataSize: Long = 0L,
    val playPositionSec: Long = 0L,
    val playCount: Int = 0,
) {
    val durationSec: Long
        get() = (stopEpochSec - startEpochSec).coerceAtLeast(0L)

    val isWatched: Boolean
        get() = playCount > 0

    val isFileMissing: Boolean
        get() = error?.contains("missing", ignoreCase = true) == true

    val hasResumePosition: Boolean
        get() = playCount == 0 && playPositionSec > MIN_RESUME_POSITION_SEC

    val effectiveStartSec: Long
        get() = if (hasResumePosition) playPositionSec else 0L

    fun recordingProgressFraction(nowEpochSec: Long): Float {
        val dur = durationSec
        if (dur <= 0L) return 0f
        val elapsed = (nowEpochSec - startEpochSec).coerceIn(0L, dur)
        return elapsed.toFloat() / dur.toFloat()
    }

    fun resumeProgressFraction(): Float {
        if (playCount > 0) return 1f
        val dur = durationSec
        if (dur <= 0L || playPositionSec <= 0L) return 0f
        return (playPositionSec.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        const val MIN_RESUME_POSITION_SEC = 10L
    }
}

data class LocalDvrProgress(
    val playPositionSec: Long,
    val playCount: Int,
)

enum class MainTab {
    LIVE,
    GUIDE,
    RECORDINGS,
    SETTINGS,
}

enum class StartScreen(
    val label: String,
    val description: String,
    val tab: MainTab,
    val liveFilter: LiveFilter = LiveFilter.All,
) {
    LIVE(
        label = "Live",
        description = "Opens Live channels (All) when the app starts",
        tab = MainTab.LIVE,
        liveFilter = LiveFilter.All,
    ),
    FAVORITES(
        label = "Favorites",
        description = "Opens Live channels filtered to Favorites when the app starts",
        tab = MainTab.LIVE,
        liveFilter = LiveFilter.Favorites,
    ),
    GUIDE(
        label = "Guide",
        description = "Opens the EPG Guide when the app starts",
        tab = MainTab.GUIDE,
    ),
    RECORDINGS(
        label = "Recordings",
        description = "Opens Recordings when the app starts",
        tab = MainTab.RECORDINGS,
    ),
    ;

    companion object {
        fun fromPersisted(raw: String?): StartScreen = when (raw) {
            "LIVE" -> LIVE
            "FAVORITES" -> FAVORITES
            "GUIDE" -> GUIDE
            "RECORDINGS" -> RECORDINGS
            else -> LIVE
        }
    }
}

sealed interface LiveFilter {
    data object All : LiveFilter
    data object Tv : LiveFilter
    data object Radio : LiveFilter
    data object Favorites : LiveFilter
    data class Tag(val tagId: Long, val name: String) : LiveFilter
}
