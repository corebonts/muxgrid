package io.github.corebonts.muxgrid.core.designsystem

import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MuxGridGuideFormattersTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 21)
    private val nowSec = today.atStartOfDay(zone).toEpochSecond() + 12L * 3600L

    private fun ev(id: Long, startOffsetDays: Long, durationHours: Long = 1L): EpgEvent {
        val start = today.plusDays(startOffsetDays).atStartOfDay(zone).toEpochSecond() + 10L * 3600L
        return EpgEvent(
            id = id,
            channelId = 1L,
            startEpochSec = start,
            stopEpochSec = start + durationHours * 3600L,
            title = "Show $id",
        )
    }

    @Test
    fun `buildAvailableGuideDays includes only days with events within -5d to +2w plus today`() {
        val events = listOf(
            ev(1L, -7L), // outside -5d
            ev(2L, -5L), // included
            ev(3L, -1L), // yesterday
            ev(4L, 2L),  // +2d
            ev(5L, 14L), // +2w
            ev(6L, 20L), // outside +2w
        )

        val days = buildAvailableGuideDays(events, nowSec, zone)
        assertEquals(
            listOf(
                today.minusDays(5),
                today.minusDays(1),
                today,
                today.plusDays(2),
                today.plusDays(14),
            ),
            days,
        )
    }

    @Test
    fun `buildAvailableGuideDays returns today when no events exist`() {
        assertEquals(listOf(today), buildAvailableGuideDays(emptyList(), nowSec, zone))
    }

    @Test
    fun `filterEpgEventsForDay keeps past current and future shows on today`() {
        val dayStart = today.atStartOfDay(zone).toEpochSecond()
        val pastToday = EpgEvent(id = 1L, channelId = 1L, startEpochSec = dayStart + 3600L, stopEpochSec = dayStart + 7200L, title = "Morning")
        val liveNow = EpgEvent(id = 2L, channelId = 1L, startEpochSec = nowSec - 600L, stopEpochSec = nowSec + 1800L, title = "Now")
        val laterToday = EpgEvent(id = 3L, channelId = 1L, startEpochSec = nowSec + 7200L, stopEpochSec = nowSec + 10_800L, title = "Evening")
        val tomorrow = ev(4L, 1L)

        val filtered = filterEpgEventsForDay(listOf(pastToday, liveNow, laterToday, tomorrow), today, zone)
        assertEquals(listOf(1L, 2L, 3L), filtered.map { it.id })
    }

    @Test
    fun `dvr lookup matches completed recordings for past shows and prioritizes active over missed`() {
        val show = ev(10L, -2L)
        val missed = DvrEntry(
            id = 100L,
            channelId = show.channelId,
            startEpochSec = show.startEpochSec,
            stopEpochSec = show.stopEpochSec,
            title = show.title,
            state = DvrState.MISSED,
        )
        val completed = missed.copy(id = 101L, state = DvrState.COMPLETED)

        val dvrMap = mapOf(missed.id to missed, completed.id to completed)
        val index = DvrLookupIndex(dvrMap)
        assertEquals(101L, index.findMatch(show)?.id)
        assertEquals(101L, findMatchingDvrEntry(show, dvrMap)?.id)
        assertNull(index.findMatch(ev(11L, 1L)))
    }

    @Test
    fun `searchEpgEvents returns empty for blank query and matches across metadata fields`() {
        val ch1 = Channel(id = 1L, number = 1, name = "Channel One")
        val ch2 = Channel(id = 2L, number = 2, name = "Channel Two")
        val e1 = EpgEvent(id = 10L, channelId = 1L, startEpochSec = nowSec + 100L, stopEpochSec = nowSec + 1000L, title = "Evening News")
        val e2 = EpgEvent(id = 11L, channelId = 1L, startEpochSec = nowSec + 2000L, stopEpochSec = nowSec + 3000L, title = "Film", subtitle = "Special Report")
        val e3 = EpgEvent(id = 12L, channelId = 2L, startEpochSec = nowSec + 3000L, stopEpochSec = nowSec + 4000L, title = "Doc", summary = "Deep report on ocean")
        val e4 = EpgEvent(id = 13L, channelId = 2L, startEpochSec = nowSec + 4000L, stopEpochSec = nowSec + 5000L, title = "Late", description = "Weather REPORT")
        val e5 = EpgEvent(id = 14L, channelId = 2L, startEpochSec = nowSec + 5000L, stopEpochSec = nowSec + 6000L, title = "Unrelated")
        val epg = mapOf(1L to listOf(e1, e2), 2L to listOf(e3, e4, e5))

        assertEquals(emptyList<Pair<EpgEvent, Channel>>(), searchEpgEvents("   ", listOf(ch1, ch2), epg, nowSec))
        val matches = searchEpgEvents("  report ", listOf(ch1, ch2), epg, nowSec)
        assertEquals(listOf(11L to 1L, 12L to 2L, 13L to 2L), matches.map { it.first.id to it.second.id })
    }

    @Test
    fun `searchEpgEvents orders live and upcoming ascending before past descending and honors limit`() {
        val ch = Channel(id = 1L, number = 1, name = "BBC One")
        val pastOlder = EpgEvent(id = 1L, channelId = 1L, startEpochSec = nowSec - 7200L, stopEpochSec = nowSec - 3600L, title = "Match Old")
        val pastRecent = EpgEvent(id = 2L, channelId = 1L, startEpochSec = nowSec - 1800L, stopEpochSec = nowSec - 60L, title = "Match Recent Past")
        val liveNow = EpgEvent(id = 3L, channelId = 1L, startEpochSec = nowSec - 600L, stopEpochSec = nowSec + 600L, title = "Match Live")
        val upcoming = EpgEvent(id = 4L, channelId = 1L, startEpochSec = nowSec + 1800L, stopEpochSec = nowSec + 3600L, title = "Match Next")
        val epg = mapOf(1L to listOf(pastOlder, upcoming, pastRecent, liveNow))

        val ordered = searchEpgEvents("match", listOf(ch), epg, nowSec)
        assertEquals(listOf(3L, 4L, 2L, 1L), ordered.map { it.first.id })

        val capped = searchEpgEvents("match", listOf(ch), epg, nowSec, limit = 2)
        assertEquals(listOf(3L, 4L), capped.map { it.first.id })
    }

    @Test
    fun `formatEpochDayHm forwards zoneId to time formatting`() {
        val plusFive = ZoneOffset.ofHours(5)
        val utcEpoch = today.atStartOfDay(zone).toEpochSecond() + 12L * 3600L
        assertEquals("Today · 12:00", formatEpochDayHm(utcEpoch, utcEpoch, zone))
        assertEquals("Today · 17:00", formatEpochDayHm(utcEpoch, utcEpoch, plusFive))
    }

    @Test
    fun `stableDurationSec applies 700ms hysteresis around displayed second`() {
        assertEquals(10L, stableDurationSec(10_400L, 10L))
        assertEquals(10L, stableDurationSec(9_500L, 10L))
        assertEquals(11L, stableDurationSec(10_700L, 10L))
        assertEquals(9L, stableDurationSec(9_300L, 10L))
        assertEquals(0L, stableDurationSec(-500L, 0L))
    }
}
