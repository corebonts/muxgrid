package io.github.corebonts.muxgrid.core.designsystem

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
}
