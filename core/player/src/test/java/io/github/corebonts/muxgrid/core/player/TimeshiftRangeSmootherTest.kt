package io.github.corebonts.muxgrid.core.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeshiftRangeSmootherTest {

    @Test
    fun `extrapolates live edge smoothly across 1s MPEG-TS packet bursts`() {
        val smoother = TimeshiftRangeSmoother()

        // Initial sample at t=0: buffer [0, 20_000], playback at 10_000 (-10s behind live)
        val (s0, e0) = smoother.update(rawStartMs = 0L, rawEndMs = 20_000L, nowRealtimeMs = 1_000L)
        assertEquals(0L, s0)
        assertEquals(20_000L, e0)

        // At t=+200ms, +400ms, +600ms, +800ms: rawEndMs hasn't jumped yet (still 20_000ms)
        // Smoothed end advances smoothly at 1x real-time so offset stays rock-solid at 10_000ms
        val (_, e200) = smoother.update(rawStartMs = 0L, rawEndMs = 20_000L, nowRealtimeMs = 1_200L)
        val (_, e400) = smoother.update(rawStartMs = 0L, rawEndMs = 20_000L, nowRealtimeMs = 1_400L)
        val (_, e600) = smoother.update(rawStartMs = 0L, rawEndMs = 20_000L, nowRealtimeMs = 1_600L)
        val (_, e800) = smoother.update(rawStartMs = 0L, rawEndMs = 20_000L, nowRealtimeMs = 1_800L)
        assertEquals(20_200L, e200)
        assertEquals(20_400L, e400)
        assertEquals(20_600L, e600)
        assertEquals(20_800L, e800)

        // At t=+1000ms, MPEG-TS burst arrives and rawEndMs jumps to 21_000ms
        val (_, e1000) = smoother.update(rawStartMs = 0L, rawEndMs = 21_000L, nowRealtimeMs = 2_000L)
        assertEquals(21_000L, e1000)

        // currentEstimatedEndMs interpolates between polls as well
        assertEquals(21_150L, smoother.currentEstimatedEndMs(nowRealtimeMs = 2_150L))
    }

    @Test
    fun `pulls back gently when upstream stalls beyond burst tolerance`() {
        val smoother = TimeshiftRangeSmoother()
        smoother.update(rawStartMs = 0L, rawEndMs = 20_000L, nowRealtimeMs = 0L)

        // Simulate 2s stall where rawEndMs stays at 20_000L -> predictedEnd is 22_000L (500ms past 1500ms gap)
        val (_, eStalled) = smoother.update(rawStartMs = 0L, rawEndMs = 20_000L, nowRealtimeMs = 2_000L)
        assertTrue(eStalled < 22_000L && eStalled >= 20_000L, "Expected gentle pullback, got $eStalled")
    }

    @Test
    fun `concurrent updates resets and reads remain consistent`() {
        val smoother = TimeshiftRangeSmoother()
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val writer = Thread {
            runCatching {
                for (i in 1..2_000) {
                    val (start, end) = smoother.update(
                        rawStartMs = (i * 10L),
                        rawEndMs = (i * 10L) + 5_000L,
                        nowRealtimeMs = i.toLong(),
                    )
                    assertTrue(start >= 0L)
                    assertTrue(end >= start)
                    if (i % 250 == 0) smoother.reset()
                }
            }.onFailure { errors.add(it) }
        }
        val reader = Thread {
            runCatching {
                for (i in 1..2_000) {
                    val est = smoother.currentEstimatedEndMs(nowRealtimeMs = i.toLong())
                    assertTrue(est >= 0L)
                }
            }.onFailure { errors.add(it) }
        }
        writer.start()
        reader.start()
        writer.join(3_000)
        reader.join(3_000)
        assertTrue(errors.isEmpty(), "Unexpected concurrent errors: $errors")
    }
}
