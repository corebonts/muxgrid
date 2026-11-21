package io.github.corebonts.muxgrid.feature.tv

import io.github.corebonts.muxgrid.core.model.PlaybackState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvSeekControllerTest {

    private fun recordingState(
        positionMs: Long = 600_000L,
        durationMs: Long = 3_600_000L,
    ) = PlaybackState(
        dvrEntryId = 1L,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    @Test
    fun `5 quick taps reach exactly 10m via clean target ladder`() {
        val controller = TvSeekController()
        val state = recordingState(positionMs = 600_000L)

        val expectedDeltas = listOf(
            10_000L to "+10s",
            30_000L to "+30s",
            60_000L to "+1m",
            300_000L to "+5m",
            600_000L to "+10m",
            900_000L to "+15m",
        )

        var now = 1_000L
        for ((expectedDelta, expectedLabel) in expectedDeltas) {
            val res = controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = now)
            assertEquals(expectedDelta, res.uiState.pendingDeltaMs)
            assertEquals(expectedLabel, res.uiState.badgeLabel)
            assertEquals(600_000L + expectedDelta, res.uiState.pendingTargetMs)
            assertEquals(TvSeekController.TAP_COMMIT_DELAY_MS, res.commitDelayMs)
            assertEquals(TvSeekController.TAP_COMMIT_AFTER_KEY_UP_MS, controller.onKeyUp(1))
            now += 150L
        }

        val target = controller.consumeCommitTarget()
        assertEquals(600_000L + 900_000L, target)
        assertNull(controller.currentUiState().pendingTargetMs)
    }

    @Test
    fun `5 quick backward taps reach exactly minus 10m`() {
        val controller = TvSeekController()
        val state = recordingState(positionMs = 1_200_000L)

        var now = 0L
        var lastLabel = ""
        repeat(5) {
            val res = controller.onKeyDown(-1, isRepeat = false, playbackState = state, nowElapsedMs = now)
            lastLabel = res.uiState.badgeLabel
            controller.onKeyUp(-1)
            now += 120L
        }

        assertEquals("-10m", lastLabel)
        assertEquals(-600_000L, controller.currentUiState().pendingDeltaMs)
        assertEquals(600_000L, controller.consumeCommitTarget())
    }

    @Test
    fun `press and hold from rest scrubs slowly in 10s throttled steps`() {
        val controller = TvSeekController()
        val state = recordingState(positionMs = 100_000L)

        // Initial press -> +10s
        val down0 = controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 0L)
        assertEquals(10_000L, down0.uiState.pendingDeltaMs)
        assertFalse(down0.uiState.isHolding)

        // First OS repeat at 400ms -> +20s
        val rep1 = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 400L)
        assertEquals(20_000L, rep1.uiState.pendingDeltaMs)
        assertEquals("+20s", rep1.uiState.badgeLabel)
        assertTrue(rep1.uiState.isHolding)

        // 50ms later OS repeat is throttled (stays +20s, extends stale commit timer)
        val throttled = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 450L)
        assertEquals(20_000L, throttled.uiState.pendingDeltaMs)
        assertEquals(TvSeekController.HOLD_STALE_COMMIT_DELAY_MS, throttled.commitDelayMs)

        // Next accepted tick at 640ms -> +30s
        val rep2 = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 640L)
        assertEquals(30_000L, rep2.uiState.pendingDeltaMs)

        // Next accepted tick at 880ms -> +40s
        val rep3 = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 880L)
        assertEquals(40_000L, rep3.uiState.pendingDeltaMs)

        // KeyUp after hold schedules fast release commit
        assertEquals(TvSeekController.HOLD_RELEASE_COMMIT_DELAY_MS, controller.onKeyUp(1))
        assertFalse(controller.currentUiState().isHolding)
        assertEquals(140_000L, controller.consumeCommitTarget())
    }

    @Test
    fun `tap release then quick press and hold locks hold gear to 30s`() {
        val controller = TvSeekController()
        val state = recordingState(positionMs = 100_000L)

        // Tap 1 -> +10s, release
        controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 0L)
        assertEquals(TvSeekController.TAP_COMMIT_AFTER_KEY_UP_MS, controller.onKeyUp(1))

        // Quick 2nd press at 200ms -> +30s and gear=30s
        val tap2 = controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 200L)
        assertEquals(30_000L, tap2.uiState.pendingDeltaMs)
        assertEquals("+30s", tap2.uiState.badgeLabel)

        // Hold ticks -> +1m, +1m 30s, +2m
        val rep1 = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 600L)
        assertEquals(60_000L, rep1.uiState.pendingDeltaMs)
        assertEquals("+1m", rep1.uiState.badgeLabel)

        val rep2 = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 840L)
        assertEquals(90_000L, rep2.uiState.pendingDeltaMs)
        assertEquals("+1m 30s", rep2.uiState.badgeLabel)

        val rep3 = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 1_080L)
        assertEquals(120_000L, rep3.uiState.pendingDeltaMs)
        assertEquals("+2m", rep3.uiState.badgeLabel)
    }

    @Test
    fun `3 taps then hold caps hold increment at 30s`() {
        val controller = TvSeekController()
        val state = recordingState(positionMs = 100_000L)

        // 3 taps -> +1m
        controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 0L)
        controller.onKeyUp(1)
        controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 150L)
        controller.onKeyUp(1)
        val tap3 = controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 300L)
        assertEquals(60_000L, tap3.uiState.pendingDeltaMs)

        // Hold adds 30s (capped, not +1m or +5m)
        val rep1 = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 700L)
        assertEquals(90_000L, rep1.uiState.pendingDeltaMs)
        assertEquals("+1m 30s", rep1.uiState.badgeLabel)
    }

    @Test
    fun `opposite tap steps back down ladder and cancels cleanly at zero`() {
        val controller = TvSeekController()
        val state = recordingState(positionMs = 600_000L)

        // 4 taps forward -> +5m
        var now = 0L
        repeat(4) {
            controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = now)
            controller.onKeyUp(1)
            now += 100L
        }
        assertEquals(300_000L, controller.currentUiState().pendingDeltaMs)

        // Tap left -> +1m -> +30s -> +10s -> 0s
        val back1 = controller.onKeyDown(-1, isRepeat = false, playbackState = state, nowElapsedMs = 500L)
        assertEquals(60_000L, back1.uiState.pendingDeltaMs)
        controller.onKeyUp(-1)

        val back2 = controller.onKeyDown(-1, isRepeat = false, playbackState = state, nowElapsedMs = 650L)
        assertEquals(30_000L, back2.uiState.pendingDeltaMs)
        controller.onKeyUp(-1)

        val back3 = controller.onKeyDown(-1, isRepeat = false, playbackState = state, nowElapsedMs = 800L)
        assertEquals(10_000L, back3.uiState.pendingDeltaMs)
        controller.onKeyUp(-1)

        val back4 = controller.onKeyDown(-1, isRepeat = false, playbackState = state, nowElapsedMs = 950L)
        assertEquals(0L, back4.uiState.pendingDeltaMs)
        assertEquals("0s", back4.uiState.badgeLabel)
        assertEquals(0, back4.uiState.direction)

        // Committing at 0s is a no-op (returns null) and hides OSD
        assertNull(controller.consumeCommitTarget())
        assertFalse(controller.currentUiState().osdVisible)
    }

    @Test
    fun `formatSeekDeltaLabel formats seconds minutes and hours cleanly`() {
        assertEquals("0s", formatSeekDeltaLabel(0L))
        assertEquals("+10s", formatSeekDeltaLabel(10_000L))
        assertEquals("-30s", formatSeekDeltaLabel(-30_000L))
        assertEquals("+1m", formatSeekDeltaLabel(60_000L))
        assertEquals("+1m 30s", formatSeekDeltaLabel(90_000L))
        assertEquals("-10m", formatSeekDeltaLabel(-600_000L))
        assertEquals("+1h", formatSeekDeltaLabel(3_600_000L))
        assertEquals("+1h 5m 10s", formatSeekDeltaLabel(3_910_000L))
    }

    @Test
    fun `hold release commits immediately and next press starts fresh from committed position`() {
        val controller = TvSeekController()
        val state = recordingState(positionMs = 100_000L)

        // Hold from rest: +10s -> +20s -> +30s
        controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 0L)
        controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 400L)
        val tick2 = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 640L)
        assertEquals(30_000L, tick2.uiState.pendingDeltaMs)

        // KeyUp after hold schedules immediate commit (0ms)
        assertEquals(0L, controller.onKeyUp(1))
        assertEquals(130_000L, controller.consumeCommitTarget())

        // Next press starts a fresh session from the new committed position at +10s
        val updatedState = recordingState(positionMs = 130_000L)
        val nextPress = controller.onKeyDown(1, isRepeat = false, playbackState = updatedState, nowElapsedMs = 750L)
        assertEquals(10_000L, nextPress.uiState.pendingDeltaMs)
        assertEquals(140_000L, nextPress.uiState.pendingTargetMs)
    }

    @Test
    fun `clamps within live timeshift buffer and does not inflate delta at boundary`() {
        val controller = TvSeekController()
        val state = PlaybackState(
            channelId = 5L,
            isTimeshiftSeekable = true,
            positionMs = 200_000L,
            bufferStartMs = 180_000L,
            bufferEndMs = 250_000L,
        )

        // Tap backward 1 -> -10s (190s), tap 2 -> -30s clamped to bufferStartMs (180s => -20s)
        val t1 = controller.onKeyDown(-1, isRepeat = false, playbackState = state, nowElapsedMs = 0L)
        assertEquals(190_000L, t1.uiState.pendingTargetMs)
        controller.onKeyUp(-1)

        val t2 = controller.onKeyDown(-1, isRepeat = false, playbackState = state, nowElapsedMs = 150L)
        assertEquals(180_000L, t2.uiState.pendingTargetMs)
        assertEquals(-20_000L, t2.uiState.pendingDeltaMs)
        controller.onKeyUp(-1)

        // Extra tap at boundary stays at 180s and stepping forward returns to -10s immediately
        val t3 = controller.onKeyDown(-1, isRepeat = false, playbackState = state, nowElapsedMs = 300L)
        assertEquals(180_000L, t3.uiState.pendingTargetMs)
        controller.onKeyUp(-1)

        val stepBack = controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 450L)
        assertEquals(190_000L, stepBack.uiState.pendingTargetMs)
        assertEquals(-10_000L, stepBack.uiState.pendingDeltaMs)
    }

    @Test
    fun `opposite repeat event without keyUp reverses cleanly instead of continuing old direction`() {
        val controller = TvSeekController()
        val state = recordingState(positionMs = 600_000L)

        // Tap forward twice (+30s) then hold forward (+1m)
        controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 0L)
        controller.onKeyUp(1)
        controller.onKeyDown(1, isRepeat = false, playbackState = state, nowElapsedMs = 150L)
        val held = controller.onKeyDown(1, isRepeat = true, playbackState = state, nowElapsedMs = 550L)
        assertEquals(60_000L, held.uiState.pendingDeltaMs)

        // Opposite repeat arrives without keyUp -> treated as fresh reverse step in 10s gear (+50s)
        val reversed = controller.onKeyDown(-1, isRepeat = true, playbackState = state, nowElapsedMs = 600L)
        assertEquals(50_000L, reversed.uiState.pendingDeltaMs)

        // Subsequent opposite repeats continue backward in 10s steps
        val revHold = controller.onKeyDown(-1, isRepeat = true, playbackState = state, nowElapsedMs = 840L)
        assertEquals(40_000L, revHold.uiState.pendingDeltaMs)
    }
}
