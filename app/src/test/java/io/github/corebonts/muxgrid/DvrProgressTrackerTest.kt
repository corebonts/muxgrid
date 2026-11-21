package io.github.corebonts.muxgrid

import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DvrProgressTrackerTest {

    private data class ProgressReport(
        val dvrId: Long,
        val positionSec: Long,
        val durationSec: Long,
        val markCompleted: Boolean,
    )

    @Test
    fun `completion is reported only once across repeated completed state emissions`() = runTest {
        val state = MutableStateFlow(PlaybackState())
        val reports = mutableListOf<ProgressReport>()
        val tracker = DvrProgressTracker(
            scope = backgroundScope,
            stateFlow = state,
            reportProgress = { id, pos, dur, completed ->
                reports.add(ProgressReport(id, pos, dur, completed))
            },
        )
        tracker.start()
        runCurrent()

        state.value = PlaybackState(
            status = PlaybackStatus.PLAYING,
            dvrEntryId = 42L,
            positionMs = 120_000L,
            durationMs = 300_000L,
        )
        runCurrent()

        // Natural EOF sets IDLE + isCompleted = true while dvrEntryId stays non-null
        state.value = state.value.copy(status = PlaybackStatus.IDLE, isCompleted = true)
        runCurrent()

        // Subsequent unrelated state emissions must not re-report completion
        state.value = state.value.copy(hwdecCurrent = "no")
        runCurrent()
        tracker.flush()
        runCurrent()

        // Switching to null after completion must not send markCompleted = false
        state.value = PlaybackState(status = PlaybackStatus.IDLE)
        runCurrent()

        assertEquals(
            listOf(ProgressReport(dvrId = 42L, positionSec = 300L, durationSec = 300L, markCompleted = true)),
            reports,
        )
    }

    @Test
    fun `replaying same recording after completion resets completion guard`() = runTest {
        val state = MutableStateFlow(PlaybackState())
        val reports = mutableListOf<ProgressReport>()
        val tracker = DvrProgressTracker(
            scope = backgroundScope,
            stateFlow = state,
            reportProgress = { id, pos, dur, completed ->
                reports.add(ProgressReport(id, pos, dur, completed))
            },
        )
        tracker.start()
        runCurrent()

        state.value = PlaybackState(
            status = PlaybackStatus.PLAYING,
            dvrEntryId = 7L,
            positionMs = 60_000L,
            durationMs = 100_000L,
        )
        runCurrent()
        state.value = state.value.copy(status = PlaybackStatus.IDLE, isCompleted = true)
        runCurrent()

        // Replay the same DVR entry
        state.value = PlaybackState(
            status = PlaybackStatus.PLAYING,
            dvrEntryId = 7L,
            positionMs = 10_000L,
            durationMs = 100_000L,
            isCompleted = false,
        )
        runCurrent()
        state.value = state.value.copy(status = PlaybackStatus.IDLE, isCompleted = true)
        runCurrent()

        assertEquals(2, reports.count { it.markCompleted && it.dvrId == 7L })
    }

    @Test
    fun `periodic tick continues even if a report call throws`() = runTest {
        val state = MutableStateFlow(PlaybackState())
        var calls = 0
        val tracker = DvrProgressTracker(
            scope = backgroundScope,
            stateFlow = state,
            reportProgress = { _, _, _, _ ->
                calls++
                if (calls == 1) throw RuntimeException("transient failure")
            },
            tickIntervalMs = 1_000L,
        )
        tracker.start()
        runCurrent()

        state.value = PlaybackState(
            status = PlaybackStatus.PLAYING,
            dvrEntryId = 99L,
            positionMs = 30_000L,
            durationMs = 120_000L,
        )
        runCurrent()

        advanceTimeBy(1_000L)
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(2, calls)
    }
}
