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
    private val activeByChannelAndStart: Map<Long, Map<Long, DvrEntry>> = buildMap {
        for (entry in dvrEntries.values) {
            if (entry.state == DvrState.SCHEDULED || entry.state == DvrState.RECORDING) {
                @Suppress("UNCHECKED_CAST")
                val byStart = getOrPut(entry.channelId) { HashMap<Long, DvrEntry>() } as MutableMap<Long, DvrEntry>
                byStart.putIfAbsent(entry.startEpochSec, entry)
            }
        }
    }

    fun findMatch(event: EpgEvent): DvrEntry? {
        event.dvrId?.let { id ->
            dvrEntries[id]?.let { return it }
        }
        return activeByChannelAndStart[event.channelId]?.get(event.startEpochSec)
    }
}

fun findMatchingDvrEntry(
    event: EpgEvent,
    dvrEntries: Map<Long, DvrEntry>,
): DvrEntry? {
    event.dvrId?.let { id ->
        dvrEntries[id]?.let { return it }
    }
    return dvrEntries.values.firstOrNull { entry ->
        entry.channelId == event.channelId &&
            entry.startEpochSec == event.startEpochSec &&
            (entry.state == DvrState.SCHEDULED || entry.state == DvrState.RECORDING)
    }
}

fun buildUpcomingDays(
    nowEpochSec: Long,
    count: Int = 7,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LocalDate> {
    val today = Instant.ofEpochSecond(nowEpochSec).atZone(zoneId).toLocalDate()
    return List(count) { offset -> today.plusDays(offset.toLong()) }
}

fun filterEpgEventsForDay(
    events: List<EpgEvent>,
    day: LocalDate,
    nowEpochSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<EpgEvent> {
    val today = Instant.ofEpochSecond(nowEpochSec).atZone(zoneId).toLocalDate()
    val dayStartSec = day.atStartOfDay(zoneId).toEpochSecond()
    val dayEndSec = day.plusDays(1).atStartOfDay(zoneId).toEpochSecond()
    return events.filter { ev ->
        val overlapsDay = ev.stopEpochSec > dayStartSec && ev.startEpochSec < dayEndSec
        if (!overlapsDay) return@filter false
        if (day == today) {
            // Keep currently airing and recently ended (up to 15 min) + upcoming today
            ev.stopEpochSec >= nowEpochSec - 900L
        } else {
            true
        }
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
