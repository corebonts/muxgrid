package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.htsp.ChannelUpdateDelta
import io.github.corebonts.muxgrid.core.htsp.DvrEntryUpdateDelta
import io.github.corebonts.muxgrid.core.htsp.EventUpdateDelta
import io.github.corebonts.muxgrid.core.htsp.HtspPushEvent
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
