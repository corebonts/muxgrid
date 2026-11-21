package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.htsp.ChannelUpdateDelta
import io.github.corebonts.muxgrid.core.htsp.DvrEntryUpdateDelta
import io.github.corebonts.muxgrid.core.htsp.EventUpdateDelta
import io.github.corebonts.muxgrid.core.htsp.HtspPushEvent
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import io.github.corebonts.muxgrid.core.model.LocalDvrProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvheadendStoreTest {

    @Test
    fun `shadow sync keeps live data intact until initialSyncCompleted and drops deleted items`() {
        var nowSec = 1_000L
        val store = TvheadendStore(epochSecondsProvider = { nowSec })

        // Initial sync: Ch1 + Ch2
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD", eventId = 100L)))
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 2L, number = 2, name = "M2 HD")))
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 100L, channelId = 1L, startEpochSec = 900L, stopEpochSec = 1200L, title = "Híradó"),
            ),
        )
        // Before InitialSyncCompleted, live channels remain empty
        assertEquals(0, store.channels.value.size)

        store.applyEvent(HtspPushEvent.InitialSyncCompleted)
        assertEquals(listOf("M1 HD", "M2 HD"), store.channels.value.map { it.name })
        assertEquals("Híradó", store.nowNextByChannel.value[1L]?.now?.title)

        // Reconnect shadow sync: Ch2 was removed on server while disconnected; Ch3 added
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD", eventId = 101L)))
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 3L, number = 3, name = "Duna HD")))
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 101L, channelId = 1L, startEpochSec = 950L, stopEpochSec = 1300L, title = "Sport"),
            ),
        )

        // UI does not blank during shadow sync!
        assertEquals(listOf("M1 HD", "M2 HD"), store.channels.value.map { it.name })

        // Atomic swap on InitialSyncCompleted
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)
        assertEquals(listOf("M1 HD", "Duna HD"), store.channels.value.map { it.name })
        assertEquals("Sport", store.nowNextByChannel.value[1L]?.now?.title)
    }

    @Test
    fun `channelUpdate merges partial fields without wiping name or number`() {
        val store = TvheadendStore(epochSecondsProvider = { 1_000L })
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 10L, number = 4, name = "M4 Sport HD")))
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 200L, channelId = 10L, startEpochSec = 900L, stopEpochSec = 1500L, title = "F1"),
            ),
        )
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)

        store.applyEvent(
            HtspPushEvent.ChannelUpdate(
                ChannelUpdateDelta(
                    id = 10L,
                    eventId = 200L,
                    hasEventIdField = true,
                ),
            ),
        )

        val updated = store.channels.value.single()
        assertEquals("M4 Sport HD", updated.name)
        assertEquals(4, updated.number)
        assertEquals(200L, updated.eventId)
        assertEquals("F1", store.nowNextByChannel.value[10L]?.now?.title)
    }

    @Test
    fun `findConflictingRecording detects active recording on another channel`() {
        val store = TvheadendStore()
        store.applyEvent(
            HtspPushEvent.DvrEntryAdd(
                DvrEntry(
                    id = 55L,
                    channelId = 7L,
                    startEpochSec = 100L,
                    stopEpochSec = 200L,
                    title = "Híradó",
                    state = DvrState.RECORDING,
                ),
            ),
        )

        assertNull(store.findConflictingRecording(targetChannelId = 7L))
        assertEquals("Híradó", store.findConflictingRecording(targetChannelId = 1L)?.title)
    }

    @Test
    fun `epgByChannel sorts events and clears dvrId on eventUpdate`() {
        val store = TvheadendStore(epochSecondsProvider = { 1_000L })
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD")))
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 2L, channelId = 1L, startEpochSec = 2_000L, stopEpochSec = 3_000L, title = "Later", dvrId = 50L),
            ),
        )
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 1L, channelId = 1L, startEpochSec = 900L, stopEpochSec = 1_500L, title = "Now"),
            ),
        )
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)

        val ch1Events = store.epgByChannel.value[1L].orEmpty()
        assertEquals(listOf(1L, 2L), ch1Events.map { it.id })
        assertEquals(50L, ch1Events[1].dvrId)

        store.applyEvent(
            HtspPushEvent.EventUpdate(
                EventUpdateDelta(id = 2L, dvrId = null, hasDvrIdField = true),
            ),
        )
        assertNull(store.epgByChannel.value[1L]?.get(1)?.dvrId)
    }

    @Test
    fun `dvrEntryUpdate applies playPositionSec playCount and state transitions`() {
        val store = TvheadendStore()
        store.applyEvent(
            HtspPushEvent.DvrEntryAdd(
                DvrEntry(
                    id = 88L,
                    channelId = 1L,
                    startEpochSec = 100L,
                    stopEpochSec = 3_700L,
                    title = "Documentary",
                    state = DvrState.RECORDING,
                ),
            ),
        )
        store.applyEvent(
            HtspPushEvent.DvrEntryUpdate(
                DvrEntryUpdateDelta(
                    id = 88L,
                    state = DvrState.COMPLETED,
                    playPositionSec = 1_200L,
                    playCount = 1,
                ),
            ),
        )
        val entry = store.dvrEntries.value[88L]!!
        assertEquals(DvrState.COMPLETED, entry.state)
        assertEquals(1_200L, entry.playPositionSec)
        assertEquals(1, entry.playCount)
    }

    @Test
    fun `applyEvents batches post-sync EPG updates and refreshNowNext advances from cached EPG`() {
        var nowSec = 1_000L
        val store = TvheadendStore(epochSecondsProvider = { nowSec })
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD")))
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)

        store.applyEvents(
            listOf(
                HtspPushEvent.EventAdd(
                    EpgEvent(id = 2L, channelId = 1L, startEpochSec = 1_500L, stopEpochSec = 2_000L, title = "Second"),
                ),
                HtspPushEvent.EventAdd(
                    EpgEvent(id = 1L, channelId = 1L, startEpochSec = 900L, stopEpochSec = 1_500L, title = "First"),
                ),
            ),
        )

        assertEquals(listOf(1L, 2L), store.epgByChannel.value[1L]?.map { it.id })
        assertEquals("First", store.nowNextByChannel.value[1L]?.now?.title)
        assertEquals("Second", store.nowNextByChannel.value[1L]?.next?.title)

        nowSec = 1_600L
        store.refreshNowNext()
        assertEquals("Second", store.nowNextByChannel.value[1L]?.now?.title)
        assertNull(store.nowNextByChannel.value[1L]?.next)
    }

    @Test
    fun `eventDelete removes both past and future events`() {
        val nowSec = 1_000_000L
        val store = TvheadendStore(epochSecondsProvider = { nowSec })
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD")))
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 10L, channelId = 1L, startEpochSec = nowSec - 7_200L, stopEpochSec = nowSec - 3_600L, title = "Past Show"),
            ),
        )
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 11L, channelId = 1L, startEpochSec = nowSec + 3_600L, stopEpochSec = nowSec + 7_200L, title = "Future Show"),
            ),
        )
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)

        store.applyEvents(
            listOf(
                HtspPushEvent.EventDelete(10L),
                HtspPushEvent.EventDelete(11L),
            ),
        )

        assertEquals(emptyList<Long>(), store.epgByChannel.value[1L].orEmpty().map { it.id })
    }

    @Test
    fun `shadow resync replaces EPG with fresh server snapshot without retaining omitted past events`() {
        val nowSec = 2_000_000L
        val store = TvheadendStore(epochSecondsProvider = { nowSec })
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD")))
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(
                    id = 2L,
                    channelId = 1L,
                    startEpochSec = nowSec - 2L * 86_400L,
                    stopEpochSec = nowSec - 2L * 86_400L + 3_600L,
                    title = "Two Days Ago",
                ),
            ),
        )
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)
        assertEquals(listOf(2L), store.epgByChannel.value[1L].orEmpty().map { it.id })

        // Resync omits past event 2 and provides current/future events
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD")))
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 30L, channelId = 1L, startEpochSec = nowSec - 1_800L, stopEpochSec = nowSec + 1_800L, title = "Current"),
            ),
        )
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 4L, channelId = 1L, startEpochSec = nowSec + 1_800L, stopEpochSec = nowSec + 5_400L, title = "Upcoming"),
            ),
        )
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)

        assertEquals(listOf(30L, 4L), store.epgByChannel.value[1L].orEmpty().map { it.id })
    }

    @Test
    fun `clear empties channels, EPG, nowNext, and DVR entries`() {
        val nowSec = 1_000_000L
        val store = TvheadendStore(epochSecondsProvider = { nowSec })
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD")))
        store.applyEvent(
            HtspPushEvent.EventAdd(
                EpgEvent(id = 10L, channelId = 1L, startEpochSec = nowSec - 600L, stopEpochSec = nowSec + 600L, title = "Live"),
            ),
        )
        store.applyEvent(
            HtspPushEvent.DvrEntryAdd(
                DvrEntry(id = 5L, channelId = 1L, startEpochSec = nowSec, stopEpochSec = nowSec + 600L, title = "Rec", state = DvrState.RECORDING),
            ),
        )
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)

        assertEquals(1, store.channels.value.size)
        assertEquals(1, store.epgByChannel.value.size)
        assertEquals(1, store.nowNextByChannel.value.size)
        assertEquals(1, store.dvrEntries.value.size)

        store.clear()

        assertTrue(store.channels.value.isEmpty())
        assertTrue(store.tags.value.isEmpty())
        assertTrue(store.epgByChannel.value.isEmpty())
        assertTrue(store.nowNextByChannel.value.isEmpty())
        assertTrue(store.dvrEntries.value.isEmpty())
    }

    @Test
    fun `no-op updates and deletes for missing IDs keep state untouched`() {
        val store = TvheadendStore(epochSecondsProvider = { 1_000L })
        store.applyEvent(HtspPushEvent.SyncStarting)
        store.applyEvent(HtspPushEvent.ChannelAdd(Channel(id = 1L, number = 1, name = "M1 HD")))
        store.applyEvent(HtspPushEvent.InitialSyncCompleted)

        val channelsBefore = store.channels.value
        val epgBefore = store.epgByChannel.value
        val dvrBefore = store.dvrEntries.value

        store.applyEvents(
            listOf(
                HtspPushEvent.ChannelUpdate(ChannelUpdateDelta(id = 999L, name = "Missing")),
                HtspPushEvent.ChannelDelete(999L),
                HtspPushEvent.EventUpdate(EventUpdateDelta(id = 999L, title = "Missing")),
                HtspPushEvent.DvrEntryUpdate(DvrEntryUpdateDelta(id = 999L, title = "Missing")),
                HtspPushEvent.DvrEntryDelete(999L),
            ),
        )

        assertEquals(channelsBefore, store.channels.value)
        assertEquals(epgBefore, store.epgByChannel.value)
        assertEquals(dvrBefore, store.dvrEntries.value)
    }

    @Test
    fun `mergeDvrEntriesWithLocalProgress applies local unwatched and watched overrides over server state`() {
        val serverWatched = DvrEntry(
            id = 1L,
            channelId = 10L,
            startEpochSec = 100L,
            stopEpochSec = 200L,
            title = "Watched on Server",
            state = DvrState.COMPLETED,
            playPositionSec = 0L,
            playCount = 1,
        )
        val serverResumable = DvrEntry(
            id = 2L,
            channelId = 10L,
            startEpochSec = 300L,
            stopEpochSec = 400L,
            title = "Resumable on Server",
            state = DvrState.COMPLETED,
            playPositionSec = 45L,
            playCount = 0,
        )
        val merged = mergeDvrEntriesWithLocalProgress(
            serverMap = mapOf(1L to serverWatched, 2L to serverResumable),
            localFallback = mapOf(
                1L to LocalDvrProgress(playPositionSec = 0L, playCount = 0),
                2L to LocalDvrProgress(playPositionSec = 0L, playCount = 1),
            ),
        )
        assertFalse(merged[1L]!!.isWatched)
        assertEquals(0, merged[1L]!!.playCount)
        assertEquals(1, merged[2L]!!.playCount)
        assertEquals(0L, merged[2L]!!.playPositionSec)
    }
}
