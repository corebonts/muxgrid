package io.github.corebonts.muxgrid.core.designsystem

import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.ServerProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DvrSeriesGroup(
    val key: String,
    val title: String,
    val entries: List<DvrEntry>,
    val latestStartEpochSec: Long,
)

fun groupCompletedDvrEntries(entries: Collection<DvrEntry>): List<DvrSeriesGroup> {
    val completed = entries.filter { it.state == DvrState.COMPLETED }
    if (completed.isEmpty()) return emptyList()

    val grouped = LinkedHashMap<String, MutableList<DvrEntry>>()
    for (entry in completed.sortedByDescending { it.startEpochSec }) {
        val normalizedKey = entry.title.trim().lowercase(Locale.getDefault()).ifEmpty { "recording-${entry.id}" }
        grouped.getOrPut(normalizedKey) { ArrayList() }.add(entry)
    }

    return grouped.entries.map { (key, list) ->
        val sorted = list.sortedByDescending { it.startEpochSec }
        DvrSeriesGroup(
            key = key,
            title = sorted.first().title.ifBlank { "Untitled Recording" },
            entries = sorted,
            latestStartEpochSec = sorted.first().startEpochSec,
        )
    }.sortedByDescending { it.latestStartEpochSec }
}

class DvrLookupIndex(
    private val dvrEntries: Map<Long, DvrEntry>,
) {
    private val byChannelAndStart: Map<Long, Map<Long, DvrEntry>> = buildMap {
        for (entry in dvrEntries.values) {
            if (entry.state == DvrState.INVALID) continue
            @Suppress("UNCHECKED_CAST")
            val byStart = getOrPut(entry.channelId) { HashMap<Long, DvrEntry>() } as MutableMap<Long, DvrEntry>
            val existing = byStart[entry.startEpochSec]
            if (existing == null || dvrMatchPriority(entry.state) > dvrMatchPriority(existing.state)) {
                byStart[entry.startEpochSec] = entry
            }
        }
    }

    fun findMatch(event: EpgEvent): DvrEntry? {
        event.dvrId?.let { id ->
            dvrEntries[id]?.let { return it }
        }
        return byChannelAndStart[event.channelId]?.get(event.startEpochSec)
    }
}

private fun dvrMatchPriority(state: DvrState): Int = when (state) {
    DvrState.RECORDING -> 4
    DvrState.SCHEDULED -> 3
    DvrState.COMPLETED -> 2
    DvrState.MISSED -> 1
    DvrState.INVALID -> 0
}

fun findMatchingDvrEntry(
    event: EpgEvent,
    dvrEntries: Map<Long, DvrEntry>,
): DvrEntry? {
    event.dvrId?.let { id ->
        dvrEntries[id]?.let { return it }
    }
    var best: DvrEntry? = null
    var bestPriority = 0
    for (entry in dvrEntries.values) {
        if (entry.channelId == event.channelId && entry.startEpochSec == event.startEpochSec) {
            val priority = dvrMatchPriority(entry.state)
            if (priority > bestPriority) {
                best = entry
                bestPriority = priority
            }
        }
    }
    return best
}

const val EPG_PAST_DAYS = 5L
const val EPG_FUTURE_WEEKS = 2L
const val EPG_FUTURE_DAYS = EPG_FUTURE_WEEKS * 7L

fun buildUpcomingDays(
    nowEpochSec: Long,
    count: Int = 7,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LocalDate> {
    val today = Instant.ofEpochSecond(nowEpochSec).atZone(zoneId).toLocalDate()
    return List(count) { offset -> today.plusDays(offset.toLong()) }
}

/**
 * Returns chronologically sorted dates within `[today - 5 days, today + 2 weeks]` that have
 * at least one overlapping EPG event, always including `today`.
 */
fun buildAvailableGuideDays(
    events: Iterable<EpgEvent>,
    nowEpochSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LocalDate> = buildAvailableGuideDaysInternal(sequenceOf(events), nowEpochSec, zoneId)

/**
 * Overload scanning across all channels' EPG lists without intermediate list flattening.
 */
fun buildAvailableGuideDays(
    epgByChannel: Map<Long, List<EpgEvent>>,
    nowEpochSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LocalDate> = buildAvailableGuideDaysInternal(epgByChannel.values.asSequence(), nowEpochSec, zoneId)

private fun buildAvailableGuideDaysInternal(
    eventLists: Sequence<Iterable<EpgEvent>>,
    nowEpochSec: Long,
    zoneId: ZoneId,
): List<LocalDate> {
    val today = Instant.ofEpochSecond(nowEpochSec).atZone(zoneId).toLocalDate()
    val minDate = today.minusDays(EPG_PAST_DAYS)
    val totalDays = (EPG_PAST_DAYS + 1L + EPG_FUTURE_DAYS).toInt()
    val dayStarts = LongArray(totalDays + 1) { idx ->
        minDate.plusDays(idx.toLong()).atStartOfDay(zoneId).toEpochSecond()
    }
    val hasEvents = BooleanArray(totalDays)
    hasEvents[EPG_PAST_DAYS.toInt()] = true

    val windowMin = dayStarts[0]
    val windowMax = dayStarts[totalDays]

    for (list in eventLists) {
        for (ev in list) {
            if (ev.stopEpochSec <= windowMin || ev.startEpochSec >= windowMax) continue
            // Binary search for the first day i where dayStarts[i + 1] > ev.startEpochSec
            var lo = 0
            var hi = totalDays - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (dayStarts[mid + 1] <= ev.startEpochSec) {
                    lo = mid + 1
                } else {
                    hi = mid
                }
            }
            var idx = lo
            while (idx < totalDays && dayStarts[idx] < ev.stopEpochSec) {
                hasEvents[idx] = true
                idx++
            }
        }
    }

    val result = ArrayList<LocalDate>(totalDays)
    for (i in 0 until totalDays) {
        if (hasEvents[i]) {
            result.add(minDate.plusDays(i.toLong()))
        }
    }
    return result
}

/**
 * Returns `(earliestStartEpochSec, latestStopEpochSec)` clamped to `[today - 5 days, today + 2 weeks]`,
 * or `null` if no events fall within that range.
 */
fun computeGuideEventBounds(
    epgByChannel: Map<Long, List<EpgEvent>>,
    nowEpochSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Pair<Long, Long>? {
    val today = Instant.ofEpochSecond(nowEpochSec).atZone(zoneId).toLocalDate()
    val minEpoch = today.minusDays(EPG_PAST_DAYS).atStartOfDay(zoneId).toEpochSecond()
    val maxEpoch = today.plusDays(EPG_FUTURE_DAYS + 1L).atStartOfDay(zoneId).toEpochSecond()
    var earliest = Long.MAX_VALUE
    var latest = Long.MIN_VALUE
    for (list in epgByChannel.values) {
        for (ev in list) {
            if (ev.stopEpochSec <= minEpoch || ev.startEpochSec >= maxEpoch) continue
            val s = maxOf(ev.startEpochSec, minEpoch)
            val e = minOf(ev.stopEpochSec, maxEpoch)
            if (s < earliest) earliest = s
            if (e > latest) latest = e
        }
    }
    return if (earliest <= latest) earliest to latest else null
}

fun filterEpgEventsForDay(
    events: List<EpgEvent>,
    day: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<EpgEvent> {
    val dayStartSec = day.atStartOfDay(zoneId).toEpochSecond()
    val dayEndSec = day.plusDays(1).atStartOfDay(zoneId).toEpochSecond()
    return events.filter { ev ->
        ev.stopEpochSec > dayStartSec && ev.startEpochSec < dayEndSec
    }
}

const val MAX_EPG_SEARCH_RESULTS = 120

/**
 * Case-insensitive EPG search across title, subtitle, summary, and description.
 * Live and upcoming programs appear first in chronological order, followed by past programs
 * ordered most-recent-first, capped at [limit].
 */
fun searchEpgEvents(
    query: String,
    channels: List<Channel>,
    epgByChannel: Map<Long, List<EpgEvent>>,
    nowEpochSec: Long,
    limit: Int = MAX_EPG_SEARCH_RESULTS,
): List<Pair<EpgEvent, Channel>> {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || limit <= 0) return emptyList()
    val results = ArrayList<Pair<EpgEvent, Channel>>()
    for (ch in channels) {
        val list = epgByChannel[ch.id].orEmpty()
        for (ev in list) {
            if (ev.title.contains(trimmed, ignoreCase = true) ||
                (ev.subtitle?.contains(trimmed, ignoreCase = true) == true) ||
                (ev.summary?.contains(trimmed, ignoreCase = true) == true) ||
                (ev.description?.contains(trimmed, ignoreCase = true) == true)
            ) {
                results.add(ev to ch)
            }
        }
    }
    return results.sortedWith(
        compareBy<Pair<EpgEvent, Channel>> { it.first.stopEpochSec < nowEpochSec }
            .thenBy { if (it.first.stopEpochSec < nowEpochSec) -it.first.startEpochSec else it.first.startEpochSec },
    ).take(limit)
}

private val DAY_SHORT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val DATE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")

fun formatDayLabel(
    day: LocalDate,
    nowEpochSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val today = Instant.ofEpochSecond(nowEpochSec).atZone(zoneId).toLocalDate()
    return when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DAY_SHORT_FMT)
    }
}

fun formatEpochDayHm(
    epochSec: Long,
    nowEpochSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val date = Instant.ofEpochSecond(epochSec).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochSecond(nowEpochSec).atZone(zoneId).toLocalDate()
    return when (date) {
        today -> "Today · ${formatEpochHm(epochSec, zoneId)}"
        today.plusDays(1) -> "Tomorrow · ${formatEpochHm(epochSec, zoneId)}"
        today.minusDays(1) -> "Yesterday · ${formatEpochHm(epochSec, zoneId)}"
        else -> Instant.ofEpochSecond(epochSec).atZone(zoneId).format(DATE_TIME_FMT)
    }
}

fun formatDurationMs(ms: Long): String =
    formatDurationSec((ms / 1000L).coerceAtLeast(0L))

fun formatDurationSec(totalSec: Long): String {
    val clamped = totalSec.coerceAtLeast(0L)
    val hours = clamped / 3600L
    val minutes = (clamped % 3600L) / 60L
    val seconds = clamped % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatDurationMinutes(startEpochSec: Long, stopEpochSec: Long): String {
    val mins = ((stopEpochSec - startEpochSec).coerceAtLeast(0L) / 60L).toInt()
    val h = mins / 60
    val m = mins % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${mins}m"
    }
}

fun formatByteCount(bytes: Long): String {
    val clamped = bytes.coerceAtLeast(0L)
    return when {
        clamped < 1024L -> "$clamped B"
        clamped < 1024L * 1024L -> String.format(Locale.US, "%.0f KB", clamped / 1024.0)
        clamped < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", clamped / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.1f GB", clamped / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatFileSize(bytes: Long): String =
    if (bytes <= 0L) "" else formatByteCount(bytes)

const val HALF_HOUR_SEC = 1800L

fun floorToHalfHour(epochSec: Long): Long = (epochSec / HALF_HOUR_SEC) * HALF_HOUR_SEC

data class TimelineCell(
    val key: String,
    val weight: Float,
    val event: EpgEvent?,
    val startEpochSec: Long,
    val stopEpochSec: Long,
)

/**
 * Slices a channel's chronologically sorted [events] into weighted horizontal cells covering
 * `[windowStart, windowEnd)`, inserting placeholder cells for gaps of at least 5 minutes.
 */
fun buildRowTimelineCells(
    channelId: Long,
    events: List<EpgEvent>,
    windowStart: Long,
    windowEnd: Long,
): List<TimelineCell> {
    if (events.isEmpty()) {
        return listOf(
            TimelineCell(
                key = "empty_${channelId}_$windowStart",
                weight = 1f,
                event = null,
                startEpochSec = windowStart,
                stopEpochSec = windowEnd,
            ),
        )
    }

    // Events in TvheadendStore are already sorted by startEpochSec; scan directly without re-sorting
    val cells = ArrayList<TimelineCell>()
    var cursor = windowStart
    for (ev in events) {
        if (ev.startEpochSec >= windowEnd) break
        if (ev.stopEpochSec <= windowStart) continue
        val clampedStart = maxOf(ev.startEpochSec, cursor)
        val clampedEnd = minOf(ev.stopEpochSec, windowEnd)
        if (clampedEnd <= clampedStart) continue
        if (clampedStart > cursor) {
            val gapSec = clampedStart - cursor
            if (gapSec >= 300L) {
                cells.add(
                    TimelineCell(
                        key = "gap_${channelId}_$cursor",
                        weight = gapSec.toFloat().coerceAtLeast(300f),
                        event = null,
                        startEpochSec = cursor,
                        stopEpochSec = clampedStart,
                    ),
                )
            }
        }
        val spanSec = (clampedEnd - clampedStart).coerceAtLeast(300L)
        cells.add(
            TimelineCell(
                key = "ev_${ev.id}",
                weight = spanSec.toFloat(),
                event = ev,
                startEpochSec = clampedStart,
                stopEpochSec = clampedEnd,
            ),
        )
        cursor = clampedEnd
    }
    if (cells.isEmpty()) {
        return listOf(
            TimelineCell(
                key = "empty_${channelId}_$windowStart",
                weight = 1f,
                event = null,
                startEpochSec = windowStart,
                stopEpochSec = windowEnd,
            ),
        )
    }
    if (cursor < windowEnd && (windowEnd - cursor) >= 300L) {
        cells.add(
            TimelineCell(
                key = "tail_${channelId}_$cursor",
                weight = (windowEnd - cursor).toFloat(),
                event = null,
                startEpochSec = cursor,
                stopEpochSec = windowEnd,
            ),
        )
    }
    return cells
}

/**
 * Computes the best timeline window start when jumping to [dayDate]:
 * - Today jumps to [todayStartHalfHour].
 * - Other days prefer prime time (18:00) if any event overlaps `[18:00, 18:00 + windowDurationSec)`,
 *   otherwise fall back to the end or start of that day's available events.
 */
fun computeDayJumpWindowStart(
    dayDate: LocalDate,
    todayDate: LocalDate,
    todayStartHalfHour: Long,
    epgByChannel: Map<Long, List<EpgEvent>>,
    windowDurationSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    if (dayDate == todayDate) return todayStartHalfHour

    val dayStartSec = dayDate.atStartOfDay(zoneId).toEpochSecond()
    val dayEndSec = dayDate.plusDays(1).atStartOfDay(zoneId).toEpochSecond()
    val primeTime = dayStartSec + 18L * 3600L
    var hasPrimeTimeEvent = false
    var dayEarliestStart = Long.MAX_VALUE
    var dayLatestStop = Long.MIN_VALUE
    for (list in epgByChannel.values) {
        for (ev in list) {
            if (ev.stopEpochSec <= dayStartSec || ev.startEpochSec >= dayEndSec) continue
            val cs = maxOf(ev.startEpochSec, dayStartSec)
            val ce = minOf(ev.stopEpochSec, dayEndSec)
            if (cs < dayEarliestStart) dayEarliestStart = cs
            if (ce > dayLatestStop) dayLatestStop = ce
            if (ev.stopEpochSec > primeTime && ev.startEpochSec < primeTime + windowDurationSec) {
                hasPrimeTimeEvent = true
            }
        }
    }
    return when {
        hasPrimeTimeEvent -> primeTime
        dayEarliestStart <= dayLatestStop && dayLatestStop <= primeTime ->
            floorToHalfHour(dayLatestStop - windowDurationSec).coerceAtLeast(dayStartSec)
        dayEarliestStart <= dayLatestStop ->
            floorToHalfHour(dayEarliestStart)
        else -> primeTime
    }
}

fun PlaybackState.displayTitle(
    fallbackChannelName: String? = null,
    recordingFallback: String = "Recording",
    liveFallback: String = "",
): String = if (isRecordingPlayback) {
    programTitle ?: recordingFallback
} else {
    listOfNotNull(
        channelNumber?.takeIf { it.isNotBlank() && it != "0" },
        channelName ?: fallbackChannelName,
    ).joinToString(" · ").ifBlank { liveFallback }
}

fun PlaybackState.streamBadgeText(includeDeinterlace: Boolean = false): String? {
    if (videoWidth <= 0) return null
    val base = "${videoWidth}×${videoHeight} · ${hwdecCurrent ?: "sw"}"
    return if (includeDeinterlace) "$base · deint=$deinterlaceActive" else base
}

fun PlaybackError.displayTitle(): String = when (this) {
    is PlaybackError.TunerBusy -> "Tuner Busy"
    is PlaybackError.StreamOpenFailed,
    is PlaybackError.PlaybackFailed,
    -> "Playback Error"
}

fun PlaybackError.displayDetail(): String? = when (this) {
    is PlaybackError.TunerBusy -> "The single tuner is currently busy recording on another mux."
    is PlaybackError.StreamOpenFailed,
    is PlaybackError.PlaybackFailed,
    -> null
}

fun serverHeadline(
    profile: ServerProfile,
    connectionState: ConnectionState,
): String = if (connectionState is ConnectionState.Connected) {
    "${connectionState.serverName} · ${profile.host}:${profile.htspPort}"
} else {
    "${profile.host}:${profile.htspPort}"
}

fun connectionStatusText(
    profile: ServerProfile,
    connectionState: ConnectionState,
): String = when (connectionState) {
    is ConnectionState.Connected ->
        "Connected · ${connectionState.serverVersion} · HTSP v${connectionState.htspVersion}"
    ConnectionState.Connecting -> "Connecting…"
    is ConnectionState.Syncing -> "Syncing…"
    is ConnectionState.Error -> connectionState.message
    ConnectionState.Disconnected -> "Disconnected · HTTP ${profile.httpPort}"
}
