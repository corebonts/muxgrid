package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DvrEntryTest {

    @Test
    fun `effectiveStartSec returns playPositionSec only when entry has valid resume position`() {
        val base = DvrEntry(
            id = 1L,
            channelId = 10L,
            startEpochSec = 1_000L,
            stopEpochSec = 4_600L,
            title = "Evening News",
            state = DvrState.COMPLETED,
        )

        assertEquals(0L, base.copy(playPositionSec = 5L, playCount = 0).effectiveStartSec)
        assertEquals(0L, base.copy(playPositionSec = DvrEntry.MIN_RESUME_POSITION_SEC, playCount = 0).effectiveStartSec)
        assertEquals(120L, base.copy(playPositionSec = 120L, playCount = 0).effectiveStartSec)
        assertEquals(0L, base.copy(playPositionSec = 120L, playCount = 1).effectiveStartSec)
    }

    @Test
    fun `recordingProgressFraction clamps between zero and one across elapsed time`() {
        val entry = DvrEntry(
            id = 2L,
            channelId = 10L,
            startEpochSec = 1_000L,
            stopEpochSec = 2_000L,
            title = "Live Recording",
            state = DvrState.RECORDING,
        )

        assertEquals(0f, entry.recordingProgressFraction(900L), 0.0001f)
        assertEquals(0.25f, entry.recordingProgressFraction(1_250L), 0.0001f)
        assertEquals(1f, entry.recordingProgressFraction(2_500L), 0.0001f)

        val zeroDur = entry.copy(stopEpochSec = 1_000L)
        assertEquals(0f, zeroDur.recordingProgressFraction(1_500L), 0.0001f)
    }
}
