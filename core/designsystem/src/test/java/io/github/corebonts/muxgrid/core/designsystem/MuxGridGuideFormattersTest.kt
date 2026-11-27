package io.github.corebonts.muxgrid.core.designsystem

import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.ServerProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `buildAvailableGuideDays includes only days with events within today to +2w plus today`() {
        val events = listOf(
            ev(1L, -5L), // past day excluded
            ev(2L, -1L), // yesterday excluded
            ev(3L, 2L),  // +2d
            ev(4L, 14L), // +2w
            ev(5L, 20L), // outside +2w
        )

        val days = buildAvailableGuideDays(events, nowSec, zone)
        assertEquals(
            listOf(
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

    @Test
    fun `floorToHalfHour aligns epoch seconds down to 1800s boundaries`() {
        assertEquals(0L, floorToHalfHour(1_799L))
        assertEquals(1_800L, floorToHalfHour(1_800L))
        assertEquals(1_800L, floorToHalfHour(3_599L))
        assertEquals(3_600L, floorToHalfHour(3_600L))
    }

    @Test
    fun `formatByteCount and formatFileSize format byte ranges consistently`() {
        assertEquals("0 B", formatByteCount(-5L))
        assertEquals("512 B", formatByteCount(512L))
        assertEquals("2 KB", formatByteCount(2_048L))
        assertEquals("1.5 MB", formatByteCount(1_572_864L))
        assertEquals("2.0 GB", formatByteCount(2L * 1024L * 1024L * 1024L))
        assertEquals("", formatFileSize(0L))
        assertEquals("", formatFileSize(-10L))
        assertEquals("1.5 MB", formatFileSize(1_572_864L))
    }

    @Test
    fun `playback state and error formatters produce expected display strings`() {
        val live = PlaybackState(
            channelId = 1L,
            channelNumber = "1",
            channelName = "Channel One",
            videoWidth = 1920,
            videoHeight = 1080,
            hwdecCurrent = "mediacodec",
            deinterlaceActive = true,
        )
        assertEquals("1 · Channel One", live.displayTitle())
        assertEquals("1920×1080 · mediacodec", live.streamBadgeText())
        assertEquals("1920×1080 · mediacodec · deint=true", live.streamBadgeText(includeDeinterlace = true))

        val rec = PlaybackState(dvrEntryId = 5L, programTitle = "Doc", videoWidth = 0)
        assertEquals("Doc", rec.displayTitle())
        assertNull(rec.streamBadgeText())

        val busy = PlaybackError.TunerBusy("News", "BBC")
        assertEquals("Tuner Busy", busy.displayTitle())
        assertEquals("The single tuner is currently busy recording on another mux.", busy.displayDetail())
        val failed = PlaybackError.PlaybackFailed("boom")
        assertEquals("Playback Error", failed.displayTitle())
        assertNull(failed.displayDetail())
    }

    @Test
    fun `serverHeadline and connectionStatusText format server connection states`() {
        val profile = ServerProfile(host = "tvh.local", htspPort = 9982, httpPort = 9981)
        val connected = ConnectionState.Connected("Tvheadend", "4.3-2375", 34)
        assertEquals("Tvheadend · tvh.local:9982", serverHeadline(profile, connected))
        assertEquals("Connected · 4.3-2375 · HTSP v34", connectionStatusText(profile, connected))
        assertEquals("tvh.local:9982", serverHeadline(profile, ConnectionState.Disconnected))
        assertEquals("Disconnected · HTTP 9981", connectionStatusText(profile, ConnectionState.Disconnected))
        assertEquals("Timeout", connectionStatusText(profile, ConnectionState.Error("Timeout")))
    }

    @Test
    fun `buildRowTimelineCells clamps events and inserts placeholders for gaps of at least 5 minutes`() {
        val windowStart = today.atStartOfDay(zone).toEpochSecond() + 18L * 3600L
        val windowEnd = windowStart + 3L * 3600L

        val emptyCells = buildRowTimelineCells(1L, emptyList(), windowStart, windowEnd)
        assertEquals(1, emptyCells.size)
        assertNull(emptyCells[0].event)

        val e1 = EpgEvent(id = 1L, channelId = 1L, startEpochSec = windowStart - 1800L, stopEpochSec = windowStart + 3600L, title = "Early")
        val e2 = EpgEvent(id = 2L, channelId = 1L, startEpochSec = windowStart + 5400L, stopEpochSec = windowStart + 7200L, title = "Middle")
        val cells = buildRowTimelineCells(1L, listOf(e1, e2), windowStart, windowEnd)

        assertEquals(4, cells.size)
        assertEquals(1L, cells[0].event?.id)
        assertEquals(3600f, cells[0].weight)
        assertNull(cells[1].event) // 30m gap
        assertEquals(1800f, cells[1].weight)
        assertEquals(2L, cells[2].event?.id)
        assertEquals(1800f, cells[2].weight)
        assertNull(cells[3].event) // 1h tail gap
        assertEquals(3600f, cells[3].weight)
    }

    @Test
    fun `computeDayJumpWindowStart picks today half hour, prime time, or available day bounds`() {
        val todayHalfHour = floorToHalfHour(nowSec)
        val tomorrow = today.plusDays(1)
        val tomorrowStart = tomorrow.atStartOfDay(zone).toEpochSecond()
        val windowSec = 3L * 3600L

        assertEquals(
            todayHalfHour,
            computeDayJumpWindowStart(today, today, todayHalfHour, emptyMap(), windowSec, zone),
        )

        val primeEvent = EpgEvent(
            id = 10L,
            channelId = 1L,
            startEpochSec = tomorrowStart + 19L * 3600L,
            stopEpochSec = tomorrowStart + 20L * 3600L,
            title = "Prime",
        )
        assertEquals(
            tomorrowStart + 18L * 3600L,
            computeDayJumpWindowStart(tomorrow, today, todayHalfHour, mapOf(1L to listOf(primeEvent)), windowSec, zone),
        )

        val morningEvent = EpgEvent(
            id = 11L,
            channelId = 1L,
            startEpochSec = tomorrowStart + 8L * 3600L,
            stopEpochSec = tomorrowStart + 12L * 3600L,
            title = "Morning",
        )
        assertEquals(
            tomorrowStart + 9L * 3600L,
            computeDayJumpWindowStart(tomorrow, today, todayHalfHour, mapOf(1L to listOf(morningEvent)), windowSec, zone),
        )

        val lateEvent = EpgEvent(
            id = 12L,
            channelId = 1L,
            startEpochSec = tomorrowStart + 22L * 3600L,
            stopEpochSec = tomorrowStart + 23L * 3600L,
            title = "Late",
        )
        assertEquals(
            tomorrowStart + 22L * 3600L,
            computeDayJumpWindowStart(tomorrow, today, todayHalfHour, mapOf(1L to listOf(lateEvent)), windowSec, zone),
        )
    }

    @Test
    fun `buildContinuousTimelineCells preserves true event bounds and inserts gaps`() {
        val rangeStart = today.atStartOfDay(zone).toEpochSecond() + 18L * 3600L
        val rangeEnd = rangeStart + 4L * 3600L
        val e1 = EpgEvent(id = 1L, channelId = 1L, startEpochSec = rangeStart - 1800L, stopEpochSec = rangeStart + 3600L, title = "Early")
        val e2 = EpgEvent(id = 2L, channelId = 1L, startEpochSec = rangeStart + 5400L, stopEpochSec = rangeEnd + 1800L, title = "Late")

        val cells = buildContinuousTimelineCells(1L, listOf(e1, e2), rangeStart, rangeEnd)
        assertEquals(3, cells.size)
        assertEquals(rangeStart - 1800L, cells[0].startEpochSec)
        assertEquals(rangeStart + 3600L, cells[0].stopEpochSec)
        assertNull(cells[1].event)
        assertEquals(rangeStart + 3600L, cells[1].startEpochSec)
        assertEquals(rangeStart + 5400L, cells[1].stopEpochSec)
        assertEquals(2L, cells[2].event?.id)
        assertEquals(rangeEnd + 1800L, cells[2].stopEpochSec)
    }

    @Test
    fun `findAdjacentEpgEvent walks forward and backward within timeline bounds`() {
        val base = today.atStartOfDay(zone).toEpochSecond() + 12L * 3600L
        val e1 = EpgEvent(id = 1L, channelId = 1L, startEpochSec = base, stopEpochSec = base + 3600L, title = "A")
        val e2 = EpgEvent(id = 2L, channelId = 1L, startEpochSec = base + 7200L, stopEpochSec = base + 10_800L, title = "B")
        val e3 = EpgEvent(id = 3L, channelId = 1L, startEpochSec = base + 10_800L, stopEpochSec = base + 14_400L, title = "C")
        val list = listOf(e1, e2, e3)

        assertEquals(2L, findAdjacentEpgEvent(list, 1L, +1, base, base + 14_400L)?.id)
        assertEquals(1L, findAdjacentEpgEvent(list, 2L, -1, base, base + 14_400L)?.id)
        assertNull(findAdjacentEpgEvent(list, 1L, -1, base, base + 14_400L))
        assertNull(findAdjacentEpgEvent(list, 3L, +1, base, base + 14_400L))
    }

    @Test
    fun `computeEnsureVisibleScrollPx only scrolls when out of viewport and avoids ping-pong on wide shows`() {
        // Flush inside viewport -> no movement
        assertEquals(
            200f,
            computeEnsureVisibleScrollPx(
                startPx = 200f,
                stopPx = 800f,
                currentScrollPx = 200f,
                viewportWidthPx = 600f,
                maxScrollPx = 2000f,
            ),
        )
        // Partially off right on horizontal navigation -> scroll minimally to fit stopPx
        assertEquals(
            300f,
            computeEnsureVisibleScrollPx(
                startPx = 700f,
                stopPx = 900f,
                currentScrollPx = 200f,
                viewportWidthPx = 600f,
                maxScrollPx = 2000f,
                requireFullVisibility = true,
            ),
        )
        // Partially off left on vertical (Up/Down) navigation while comfortably visible -> no jerk
        assertEquals(
            200f,
            computeEnsureVisibleScrollPx(
                startPx = 100f,
                stopPx = 350f,
                currentScrollPx = 200f,
                viewportWidthPx = 600f,
                maxScrollPx = 2000f,
                requireFullVisibility = false,
            ),
        )
        // Wider than viewport and already spanning it -> no movement (no ping-pong)
        assertEquals(
            200f,
            computeEnsureVisibleScrollPx(
                startPx = 100f,
                stopPx = 1200f,
                currentScrollPx = 200f,
                viewportWidthPx = 600f,
                maxScrollPx = 2000f,
            ),
        )
        // Wider than viewport and mostly off to the right -> align start
        assertEquals(
            700f,
            computeEnsureVisibleScrollPx(
                startPx = 700f,
                stopPx = 1700f,
                currentScrollPx = 200f,
                viewportWidthPx = 600f,
                maxScrollPx = 2000f,
            ),
        )
        // Wider than viewport and mostly off to the left -> align end
        assertEquals(
            400f,
            computeEnsureVisibleScrollPx(
                startPx = 0f,
                stopPx = 1000f,
                currentScrollPx = 900f,
                viewportWidthPx = 600f,
                maxScrollPx = 2000f,
            ),
        )
    }

    @Test
    fun `EpgEvent progressFraction clamps elapsed progress within event bounds`() {
        val ev = EpgEvent(
            id = 1L,
            channelId = 1L,
            startEpochSec = 1_000L,
            stopEpochSec = 2_000L,
            title = "Show",
        )
        assertEquals(0f, ev.progressFraction(900L))
        assertEquals(0.5f, ev.progressFraction(1_500L))
        assertEquals(1f, ev.progressFraction(2_500L))

        val zeroDur = ev.copy(stopEpochSec = 1_000L)
        assertEquals(0f, zeroDur.progressFraction(1_000L))
    }

    @Test
    fun `EpgEvent remainingMinutes rounds partial minutes up and clamps at zero`() {
        val ev = EpgEvent(
            id = 1L,
            channelId = 1L,
            startEpochSec = 1_000L,
            stopEpochSec = 2_800L,
            title = "Show",
        )
        assertEquals(30, ev.remainingMinutes(1_000L))
        assertEquals(30, ev.remainingMinutes(1_001L))
        assertEquals(1, ev.remainingMinutes(2_799L))
        assertEquals(0, ev.remainingMinutes(2_800L))
        assertEquals(0, ev.remainingMinutes(3_000L))
    }

    @Test
    fun `buildContinuousTimelineCells clamps live start to minStartEpochSec when now is origin`() {
        val now = today.atStartOfDay(zone).toEpochSecond() + 18L * 3600L + 900L
        val rangeEnd = now + 4L * 3600L
        val ended = EpgEvent(id = 1L, channelId = 1L, startEpochSec = now - 3600L, stopEpochSec = now, title = "Ended")
        val live = EpgEvent(id = 2L, channelId = 1L, startEpochSec = now - 900L, stopEpochSec = now + 2700L, title = "Live")
        val next = EpgEvent(id = 3L, channelId = 1L, startEpochSec = now + 2700L, stopEpochSec = now + 6300L, title = "Next")

        val cells = buildContinuousTimelineCells(
            channelId = 1L,
            events = listOf(ended, live, next),
            rangeStart = now,
            rangeEnd = rangeEnd,
            minStartEpochSec = now,
        )
        assertEquals(3, cells.size)
        assertEquals(2L, cells[0].event?.id)
        assertEquals(now, cells[0].startEpochSec)
        assertEquals(now + 2700L, cells[0].stopEpochSec)
        assertEquals(3L, cells[1].event?.id)
        assertEquals(now + 2700L, cells[1].startEpochSec)
        assertNull(cells[2].event)
        assertEquals(now + 6300L, cells[2].startEpochSec)
        assertEquals(rangeEnd, cells[2].stopEpochSec)
    }

    @Test
    fun `TimelineScrollState limits left scroll to now origin and keeps origin pinned on tick`() {
        val origin = 10_000L
        val state = TimelineScrollState(initialEpochSec = origin)
        state.updateBounds(
            newOriginSec = origin,
            newEndSec = origin + 6L * 3600L,
            newViewportWidthPx = 600f,
            newPxPerSec = 0.1f,
        )

        // Cannot scroll left past origin
        state.scrollToEpoch(origin - 1_800L)
        assertEquals(0f, state.scrollOffsetPx)
        assertEquals(origin, state.visibleStartEpochSec)
        assertEquals(0f, state.scrollableState.dispatchRawDelta(-250f))
        assertEquals(0f, state.scrollOffsetPx)

        // Advancing now advances origin while keeping scrollOffsetPx at 0
        state.updateBounds(
            newOriginSec = origin + 30L,
            newEndSec = origin + 6L * 3600L,
            newViewportWidthPx = 600f,
            newPxPerSec = 0.1f,
        )
        assertEquals(0f, state.scrollOffsetPx)
        assertEquals(origin + 30L, state.visibleStartEpochSec)

        // Scrolled into future stays anchored to visibleStartEpochSec when now ticks
        state.scrollToEpoch(origin + 3_630L)
        assertEquals(360f, state.scrollOffsetPx)
        state.updateBounds(
            newOriginSec = origin + 60L,
            newEndSec = origin + 6L * 3600L,
            newViewportWidthPx = 600f,
            newPxPerSec = 0.1f,
        )
        assertEquals(357f, state.scrollOffsetPx)
        assertEquals(origin + 3_630L, state.visibleStartEpochSec)
    }

    @Test
    fun `computeTimelineBufferWindow preserves hysteresis within margins and clamps bounds`() {
        val origin = 10_800L
        val end = origin + 12L * 3600L
        val visibleDur = 7_200L

        // Initial build starts at origin and extends past visible end by 2h
        val initial = computeTimelineBufferWindow(
            visibleStartSec = origin + 600L,
            visibleDurationSec = visibleDur,
            timelineOriginSec = origin,
            timelineEndSec = end,
            prevStartSec = 0L,
            prevEndSec = 0L,
        )
        assertEquals(origin, initial.first)
        assertEquals(origin + visibleDur + 2L * 3_600L, initial.second)

        // Small horizontal scroll within hysteresis margin reuses identical range
        val reused = computeTimelineBufferWindow(
            visibleStartSec = origin + 2_400L,
            visibleDurationSec = visibleDur,
            timelineOriginSec = origin,
            timelineEndSec = end,
            prevStartSec = initial.first,
            prevEndSec = initial.second,
        )
        assertEquals(initial, reused)

        // Crossing inside the trailing half-hour margin shifts window forward cleanly
        val advanced = computeTimelineBufferWindow(
            visibleStartSec = origin + 6_000L,
            visibleDurationSec = visibleDur,
            timelineOriginSec = origin,
            timelineEndSec = end,
            prevStartSec = initial.first,
            prevEndSec = initial.second,
        )
        assertEquals(origin + 5_400L - 3_600L, advanced.first)
        assertEquals(origin + 5_400L + visibleDur + 2L * 3_600L, advanced.second)

        // Near end clamps cleanly to timelineEndSec
        val atEnd = computeTimelineBufferWindow(
            visibleStartSec = end - visibleDur,
            visibleDurationSec = visibleDur,
            timelineOriginSec = origin,
            timelineEndSec = end,
            prevStartSec = 0L,
            prevEndSec = 0L,
        )
        assertEquals(end, atEnd.second)
    }

    @Test
    fun `findAdjacentEpgEvent binary search handles matching startEpochSec duplicates`() {
        val base = today.atStartOfDay(zone).toEpochSecond() + 14L * 3600L
        val e1 = EpgEvent(id = 10L, channelId = 1L, startEpochSec = base, stopEpochSec = base + 1_800L, title = "First")
        val e2 = EpgEvent(id = 20L, channelId = 1L, startEpochSec = base + 1_800L, stopEpochSec = base + 3_600L, title = "DupA")
        val e3 = EpgEvent(id = 30L, channelId = 1L, startEpochSec = base + 1_800L, stopEpochSec = base + 5_400L, title = "DupB")
        val e4 = EpgEvent(id = 40L, channelId = 1L, startEpochSec = base + 5_400L, stopEpochSec = base + 7_200L, title = "Last")
        val list = listOf(e1, e2, e3, e4)

        assertEquals(
            30L,
            findAdjacentEpgEvent(list, 20L, +1, base, base + 7_200L, currentStartEpochSec = e2.startEpochSec)?.id,
        )
        assertEquals(
            20L,
            findAdjacentEpgEvent(list, 30L, -1, base, base + 7_200L, currentStartEpochSec = e3.startEpochSec)?.id,
        )
        assertEquals(
            40L,
            findAdjacentEpgEvent(list, 30L, +1, base, base + 7_200L, currentStartEpochSec = e3.startEpochSec)?.id,
        )
    }

    @Test
    fun `findActiveRecordingForChannel and buildActiveRecordingsByChannel prefer nowEvent match over earlier overlap`() {
        val earlierRec = DvrEntry(
            id = 201L,
            channelId = 7L,
            startEpochSec = nowSec - 1_800L,
            stopEpochSec = nowSec + 300L,
            title = "Earlier Show",
            state = DvrState.RECORDING,
        )
        val currentRec = DvrEntry(
            id = 202L,
            channelId = 7L,
            startEpochSec = nowSec - 60L,
            stopEpochSec = nowSec + 3_540L,
            title = "Current Show",
            state = DvrState.RECORDING,
        )
        val scheduledTimer = DvrEntry(
            id = 203L,
            channelId = 8L,
            startEpochSec = nowSec + 3_600L,
            stopEpochSec = nowSec + 7_200L,
            title = "Future Show",
            state = DvrState.SCHEDULED,
        )
        val nowEv = EpgEvent(
            id = 900L,
            channelId = 7L,
            startEpochSec = earlierRec.startEpochSec,
            stopEpochSec = earlierRec.stopEpochSec,
            title = "Earlier Show",
            dvrId = earlierRec.id,
        )
        val dvrMap = mapOf(
            earlierRec.id to earlierRec,
            currentRec.id to currentRec,
            scheduledTimer.id to scheduledTimer,
        )

        // When nowEvent points to earlierRec via dvrId, prefer earlierRec
        assertEquals(201L, findActiveRecordingForChannel(7L, dvrMap, nowEv)?.id)
        // Without nowEvent hint, prefer the latest active recording on that channel
        assertEquals(202L, findActiveRecordingForChannel(7L, dvrMap, null)?.id)
        // Channel with only a scheduled timer returns null
        assertNull(findActiveRecordingForChannel(8L, dvrMap, null))

        val byChannelWithNow = buildActiveRecordingsByChannel(
            dvrEntries = dvrMap,
            nowNextByChannel = mapOf(7L to NowNextProgram(now = nowEv)),
        )
        assertEquals(mapOf(7L to earlierRec), byChannelWithNow)

        val byChannelWithoutNow = buildActiveRecordingsByChannel(dvrEntries = dvrMap)
        assertEquals(mapOf(7L to currentRec), byChannelWithoutNow)
    }

    @Test
    fun `formatProgramModalMeta unifies channel, day-time range, duration, and optional file size`() {
        val start = today.atStartOfDay(zone).toEpochSecond() + 20L * 3600L
        val stop = start + 5400L // 1h 30m
        assertEquals(
            "BBC One · Today · 20:00–21:30 (1h 30m)",
            formatProgramModalMeta("BBC One", start, stop, nowSec, dataSizeBytes = 0L, zoneId = zone),
        )
        assertEquals(
            "BBC One · Today · 20:00–21:30 (1h 30m) · 1.5 GB",
            formatProgramModalMeta(
                channelName = "BBC One",
                startEpochSec = start,
                stopEpochSec = stop,
                nowEpochSec = nowSec,
                dataSizeBytes = (1.5 * 1024 * 1024 * 1024).toLong(),
                zoneId = zone,
            ),
        )
        assertEquals(
            "Today · 20:00–21:30 (1h 30m)",
            formatProgramModalMeta(null, start, stop, nowSec, dataSizeBytes = 0L, zoneId = zone),
        )
    }

    @Test
    fun `dvrConfirmSpecRes uses consistent action resources and destructive flags across all confirmation kinds`() {
        val stopSpec = dvrConfirmSpecRes(DvrConfirmKind.STOP_AND_SAVE)
        assertEquals(R.string.recordings_stop_and_save, stopSpec.confirmLabelRes)
        assertFalse(stopSpec.isDestructive)

        val abortSpec = dvrConfirmSpecRes(DvrConfirmKind.ABORT_RECORDING)
        assertEquals(R.string.recordings_abort_recording, abortSpec.confirmLabelRes)
        assertTrue(abortSpec.isDestructive)

        val cancelSpec = dvrConfirmSpecRes(DvrConfirmKind.CANCEL_TIMER)
        assertEquals(R.string.recordings_cancel_timer, cancelSpec.confirmLabelRes)
        assertTrue(cancelSpec.isDestructive)

        val deleteSpec = dvrConfirmSpecRes(DvrConfirmKind.DELETE_RECORDING)
        assertEquals(R.string.recordings_delete_recording, deleteSpec.confirmLabelRes)
        assertTrue(deleteSpec.isDestructive)
    }

    @Test
    fun `resolveModalSubtitleAndDescription promotes long subtitle when description is blank and deduplicates`() {
        assertEquals(
            "(magyar sportműsor, 2026)" to "Live match broadcast.",
            resolveModalSubtitleAndDescription("(magyar sportműsor, 2026)", "Live match broadcast."),
        )
        val longSub = "(magyar hírműsor, 2026) A műsor naponta többször jelentkezik a Kárpát-medence és a nagyvilág legfrissebb híreivel."
        assertEquals(
            null to longSub,
            resolveModalSubtitleAndDescription(longSub, null, null),
        )
        assertEquals(
            null to "same text",
            resolveModalSubtitleAndDescription("Same text", "same text"),
        )
    }
}
