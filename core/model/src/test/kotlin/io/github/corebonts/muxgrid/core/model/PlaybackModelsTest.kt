package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackModelsTest {

    @Test
    fun `clampSeekPositionMs clamps completed recording between zero and duration`() {
        val state = PlaybackState(
            dvrEntryId = 42L,
            durationMs = 1_800_000L,
            isGrowingRecording = false,
        )
        assertEquals(0L, state.clampSeekPositionMs(-5_000L))
        assertEquals(600_000L, state.clampSeekPositionMs(600_000L))
        assertEquals(1_800_000L, state.clampSeekPositionMs(2_000_000L))
    }

    @Test
    fun `clampSeekPositionMs leaves write head safety margin on growing recording`() {
        val state = PlaybackState(
            dvrEntryId = 42L,
            durationMs = 120_000L,
            isGrowingRecording = true,
        )
        val expectedMax = 120_000L - PlaybackSeekDefaults.GROWING_WRITE_HEAD_SAFETY_MS
        assertEquals(expectedMax, state.clampSeekPositionMs(120_000L))
        assertEquals(0L, state.clampSeekPositionMs(-1_000L))
    }

    @Test
    fun `clampSeekPositionMs clamps live timeshift within buffer with live edge safety margin`() {
        val state = PlaybackState(
            channelId = 7L,
            isTimeshiftSeekable = true,
            bufferStartMs = 60_000L,
            bufferEndMs = 300_000L,
        )
        assertEquals(60_000L, state.clampSeekPositionMs(10_000L))
        assertEquals(
            300_000L - PlaybackSeekDefaults.LIVE_EDGE_SAFETY_MS,
            state.clampSeekPositionMs(300_000L),
        )
        assertEquals(150_000L, state.clampSeekPositionMs(150_000L))
    }

    @Test
    fun `isBehindLive respects paused threshold`() {
        val pausedSlightlyBehind = PlaybackState(
            channelId = 1L,
            isTimeshiftSeekable = true,
            isPaused = true,
            liveEdgeOffsetMs = PlaybackSeekDefaults.LIVE_EDGE_DISPLAY_THRESHOLD_MS,
        )
        val playingSlightlyBehind = pausedSlightlyBehind.copy(isPaused = false)
        assertTrue(pausedSlightlyBehind.isBehindLive)
        assertFalse(playingSlightlyBehind.isBehindLive)
    }

    @Test
    fun `isSeekClampedAtGrowingEdge detects seeks past write head margin`() {
        val growing = PlaybackState(
            dvrEntryId = 42L,
            durationMs = 120_000L,
            isGrowingRecording = true,
        )
        val completed = growing.copy(isGrowingRecording = false)
        assertTrue(growing.isSeekClampedAtGrowingEdge(120_000L))
        assertTrue(growing.isSeekClampedAtGrowingEdge(119_000L))
        assertFalse(growing.isSeekClampedAtGrowingEdge(118_000L))
        assertFalse(completed.isSeekClampedAtGrowingEdge(130_000L))
    }
}
