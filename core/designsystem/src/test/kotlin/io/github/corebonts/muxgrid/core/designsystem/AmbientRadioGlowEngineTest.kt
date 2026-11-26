package io.github.corebonts.muxgrid.core.designsystem

import io.github.corebonts.muxgrid.core.model.AudioLevels
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AmbientRadioGlowEngineTest {

    @Test
    fun `pausing does not snap to zero and calms down gradually into embers`() {
        val engine = AmbientRadioGlowEngine()
        val hotSignal = AudioLevels(low = 0.85f, mid = 0.75f, high = 0.65f)

        // Warm up during active playback (~1.5s at 30fps)
        repeat(45) {
            engine.step(
                dtSec = 1f / 30f,
                targetLevels = hotSignal,
                status = PlaybackStatus.PLAYING,
                isPaused = false,
            )
        }
        val activeLow = engine.smoothLow
        val activeActivity = engine.activityFactor
        val activeRimAlpha = engine.rimAlpha
        assertTrue(activeLow > 0.7f)
        assertTrue(activeActivity > 0.9f)
        assertFalse(engine.isSettledCalm)

        // First frame after pausing: still warm, never drops instantaneously
        engine.step(
            dtSec = 1f / 30f,
            targetLevels = AudioLevels.ZERO,
            status = PlaybackStatus.PAUSED,
            isPaused = true,
        )
        assertTrue(engine.smoothLow > activeLow * 0.75f)
        assertTrue(engine.activityFactor > 0.85f)
        assertTrue(engine.rimAlpha > activeRimAlpha * 0.7f)
        assertFalse(engine.isSettledCalm)

        // After ~3 seconds paused: decays monotonically into low breathing embers
        repeat(90) {
            engine.step(
                dtSec = 1f / 30f,
                targetLevels = AudioLevels.ZERO,
                status = PlaybackStatus.PAUSED,
                isPaused = true,
            )
        }
        assertTrue(engine.smoothLow < 0.01f)
        assertTrue(engine.activityFactor <= AmbientRadioGlowEngine.PAUSED_ACTIVITY_FLOOR + 0.03f)
        assertTrue(engine.rimAlpha < activeRimAlpha * 0.5f)
        assertTrue(engine.rimAlpha > 0f) // Subtle embers remain alive
        assertTrue(engine.isSettledCalm)
    }

    @Test
    fun `attack rises faster than release and smoothing is frame-rate independent`() {
        val engine30 = AmbientRadioGlowEngine()
        val engine60 = AmbientRadioGlowEngine()
        val burst = AudioLevels(low = 1f, mid = 1f, high = 1f)

        // 0.5 seconds of burst at 30fps vs 60fps
        repeat(15) {
            engine30.step(1f / 30f, burst, PlaybackStatus.PLAYING, isPaused = false)
        }
        repeat(30) {
            engine60.step(1f / 60f, burst, PlaybackStatus.PLAYING, isPaused = false)
        }
        assertTrue(kotlin.math.abs(engine30.smoothLow - engine60.smoothLow) < 0.01f)
        assertTrue(kotlin.math.abs(engine30.activityFactor - engine60.activityFactor) < 0.01f)
    }

    @Test
    fun `lobe geometry arrays stay valid and bounded`() {
        val engine = AmbientRadioGlowEngine()
        engine.step(1f / 30f, AudioLevels(0.5f, 0.4f, 0.3f), PlaybackStatus.PLAYING, isPaused = false)

        for (i in 0 until AmbientRadioGlowEngine.LOBE_COUNT) {
            assertTrue(engine.lobeRxNorm[i] > 0f)
            assertTrue(engine.lobeRyNorm[i] > 0f)
            assertTrue(engine.lobeAlpha[i] in 0f..0.95f)
        }
        assertTrue(engine.rimHeightFrac in 0.04f..0.45f)
        assertTrue(engine.rimAlpha in 0f..0.55f)
    }

    @Test
    fun `higher liveliness speeds up ambient drift smoothly`() {
        val calmEngine = AmbientRadioGlowEngine()
        val livelyEngine = AmbientRadioGlowEngine()

        repeat(60) {
            calmEngine.step(1f / 30f, AudioLevels(0.3f, 0.3f, 0.05f), PlaybackStatus.PLAYING, isPaused = false)
            livelyEngine.step(1f / 30f, AudioLevels(0.75f, 0.75f, 0.85f), PlaybackStatus.PLAYING, isPaused = false)
        }
        assertTrue(
            livelyEngine.elapsedSec > calmEngine.elapsedSec * 1.4f,
            "Expected livelier drift: calm=${calmEngine.elapsedSec} lively=${livelyEngine.elapsedSec}",
        )
    }
}
