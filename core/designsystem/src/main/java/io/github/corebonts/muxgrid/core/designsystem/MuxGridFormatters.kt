package io.github.corebonts.muxgrid.core.designsystem

import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
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
        today -> "Today · ${formatEpochHm(epochSec)}"
        today.plusDays(1) -> "Tomorrow · ${formatEpochHm(epochSec)}"
        today.minusDays(1) -> "Yesterday · ${formatEpochHm(epochSec)}"
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

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.1f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.0f MB", mb.coerceAtLeast(1.0))
    }
}
