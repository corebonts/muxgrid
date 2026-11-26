package io.github.corebonts.muxgrid.core.designsystem

import io.github.corebonts.muxgrid.core.model.AudioLevels
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Frame-rate-independent simulation engine for [AmbientRadioGlow].
 *
 * Separates audio energy smoothing from stage [activityFactor] so pausing playback never
 * snaps or freezes the visual; instead, active levels decay and the glow settles into
 * low breathing embers over ~2–2.5 seconds.
 */
internal class AmbientRadioGlowEngine(
    val reactivity: Float = 1.0f,
    val glowHeight: Float = 0.55f,
    val amberWarmth: Float = 0.75f,
    val smoothness: Float = 0.94f,
) {
    internal data class LobeSpec(
        val isAmber: Boolean,
        val r: Float,
        val g: Float,
        val b: Float,
        val baseX: Float,
        val driftX: Float,
        val speedX: Float,
        val phaseX: Float,
        val radiusX: Float,
        val radiusY: Float,
        val baseAlpha: Float,
        val bandIndex: Int, // 0 = low, 1 = mid, 2 = high
        val bandLift: Float,
        val bandScale: Float,
    )

    var smoothLow: Float = 0f
        private set
    var smoothMid: Float = 0f
        private set
    var smoothHigh: Float = 0f
        private set
    var activityFactor: Float = 0.28f
        private set
    var elapsedSec: Float = 0f
        private set
    var isSettledCalm: Boolean = true
        private set

    // Pre-allocated normalized geometry outputs per lobe (zero per-frame allocations):
    // [cxNorm, cyNorm, rxNorm, ryNorm, alpha]
    val lobeCxNorm = FloatArray(LOBE_COUNT)
    val lobeCyNorm = FloatArray(LOBE_COUNT)
    val lobeRxNorm = FloatArray(LOBE_COUNT)
    val lobeRyNorm = FloatArray(LOBE_COUNT)
    val lobeAlpha = FloatArray(LOBE_COUNT)

    var rimHeightFrac: Float = 0.1f
        private set
    var rimAlpha: Float = 0.05f
        private set
    var topFadeFrac: Float = 0.45f
        private set

    fun step(
        dtSec: Float,
        targetLevels: AudioLevels,
        status: PlaybackStatus,
        isPaused: Boolean,
    ) {
        val clampedDt = dtSec.coerceIn(0f, 0.1f)
        val isPlaying = !isPaused && status == PlaybackStatus.PLAYING
        val isBuffering = !isPaused && status == PlaybackStatus.BUFFERING

        val targetLow = if (isPlaying) targetLevels.low.coerceIn(0f, 1f) else 0f
        val targetMid = if (isPlaying) targetLevels.mid.coerceIn(0f, 1f) else 0f
        val targetHigh = if (isPlaying) targetLevels.high.coerceIn(0f, 1f) else 0f

        val baseSmooth = when {
            isPlaying -> smoothness.coerceIn(0f, 0.985f)
            isBuffering -> 0.93f
            else -> 0.94f // Gentle calm-down release on pause
        }
        val frameScale = clampedDt * 60f

        smoothLow = smoothBand(smoothLow, targetLow, baseSmooth, frameScale)
        smoothMid = smoothBand(smoothMid, targetMid, baseSmooth, frameScale)
        smoothHigh = smoothBand(smoothHigh, targetHigh, baseSmooth, frameScale)

        val targetActivity = when {
            isPlaying -> 1.0f
            isBuffering -> 0.55f
            else -> PAUSED_ACTIVITY_FLOOR
        }
        val activityCoeff = if (targetActivity > activityFactor) {
            0.94f.pow(frameScale)
        } else {
            0.975f.pow(frameScale)
        }
        activityFactor = activityFactor * activityCoeff + targetActivity * (1f - activityCoeff)

        val totalEnergy = (smoothLow * 0.50f + smoothMid * 0.35f + smoothHigh * 0.15f) * reactivity
        val liveliness = smoothHigh * reactivity
        val driftSpeedScale = 0.35f + 0.65f * activityFactor
        // Livelier/more energetic audio makes the ambient drift noticeably more animated
        elapsedSec += clampedDt * (0.45f + totalEnergy * 0.65f + liveliness * 1.15f) * driftSpeedScale

        isSettledCalm = !isPlaying &&
            !isBuffering &&
            smoothLow < 0.01f &&
            smoothMid < 0.01f &&
            smoothHigh < 0.01f &&
            abs(activityFactor - PAUSED_ACTIVITY_FLOOR) < 0.02f

        val t = elapsedSec
        val verticalSpan = glowHeight.coerceIn(0.15f, 1.2f) * (0.62f + 0.38f * activityFactor)
        val idlePulse = 0.055f * (0.5f + 0.5f * sin(t * 0.75f)) * activityFactor
        val warmth = amberWarmth.coerceIn(0f, 1.5f) * (0.35f + 0.65f * activityFactor)

        for (i in 0 until LOBE_COUNT) {
            val lobe = LOBES[i]
            val bandVal = when (lobe.bandIndex) {
                0 -> smoothLow
                1 -> smoothMid
                else -> smoothHigh
            } * reactivity
            val response = bandVal + idlePulse

            val cx = lobe.baseX +
                sin(t * lobe.speedX + lobe.phaseX) * lobe.driftX +
                cos(t * lobe.speedX * 0.57f + lobe.phaseX * 1.7f) * (lobe.driftX * 0.35f)

            val lift = verticalSpan * (0.08f + response * lobe.bandLift)
            val cy = 1.0f + verticalSpan * 0.14f - lift

            val rx = lobe.radiusX * (1.0f + response * lobe.bandScale * 0.7f)
            val ry = verticalSpan * lobe.radiusY * (0.85f + response * lobe.bandScale * 1.15f)

            val warmthScale = if (lobe.isAmber) warmth else (0.55f + 0.45f * activityFactor)
            val alpha = (lobe.baseAlpha * warmthScale * (0.42f + response * 0.92f)).coerceIn(0f, 0.95f)

            lobeCxNorm[i] = cx
            lobeCyNorm[i] = cy
            lobeRxNorm[i] = max(0.01f, rx)
            lobeRyNorm[i] = max(0.01f, ry)
            lobeAlpha[i] = alpha
        }

        rimHeightFrac = (verticalSpan * (0.18f + totalEnergy * 0.16f)).coerceIn(0.04f, 0.45f)
        rimAlpha = ((0.12f + totalEnergy * 0.24f) * warmth).coerceIn(0f, 0.55f)
        topFadeFrac = 0.45f
    }

    private fun smoothBand(
        current: Float,
        target: Float,
        baseSmooth: Float,
        frameScale: Float,
    ): Float {
        val coeff = if (target > current) {
            (baseSmooth * 0.92f).pow(frameScale)
        } else {
            baseSmooth.pow(frameScale)
        }
        return current * coeff + target * (1f - coeff)
    }

    companion object {
        const val LOBE_COUNT = 7
        const val PAUSED_ACTIVITY_FLOOR = 0.28f

        val LOBES: Array<LobeSpec> = arrayOf(
            // 3 charcoal / warm-gray smoke layers (normal blend)
            LobeSpec(
                isAmber = false,
                r = 31f / 255f,
                g = 31f / 255f,
                b = 35f / 255f,
                baseX = 0.22f,
                driftX = 0.11f,
                speedX = 0.13f,
                phaseX = 0.4f,
                radiusX = 0.56f,
                radiusY = 0.62f,
                baseAlpha = 0.88f,
                bandIndex = 0,
                bandLift = 0.34f,
                bandScale = 0.26f,
            ),
            LobeSpec(
                isAmber = false,
                r = 46f / 255f,
                g = 43f / 255f,
                b = 41f / 255f,
                baseX = 0.52f,
                driftX = 0.14f,
                speedX = 0.10f,
                phaseX = 2.1f,
                radiusX = 0.62f,
                radiusY = 0.58f,
                baseAlpha = 0.82f,
                bandIndex = 1,
                bandLift = 0.30f,
                bandScale = 0.24f,
            ),
            LobeSpec(
                isAmber = false,
                r = 61f / 255f,
                g = 56f / 255f,
                b = 52f / 255f,
                baseX = 0.80f,
                driftX = 0.12f,
                speedX = 0.15f,
                phaseX = 4.3f,
                radiusX = 0.52f,
                radiusY = 0.56f,
                baseAlpha = 0.76f,
                bandIndex = 0,
                bandLift = 0.28f,
                bandScale = 0.22f,
            ),
            // 4 warm amber / copper reactive highlights (screen blend)
            LobeSpec(
                isAmber = true,
                r = 146f / 255f,
                g = 64f / 255f,
                b = 14f / 255f,
                baseX = 0.28f,
                driftX = 0.16f,
                speedX = 0.18f,
                phaseX = 1.2f,
                radiusX = 0.46f,
                radiusY = 0.50f,
                baseAlpha = 0.42f,
                bandIndex = 0,
                bandLift = 0.46f,
                bandScale = 0.38f,
            ),
            LobeSpec(
                isAmber = true,
                r = 180f / 255f,
                g = 83f / 255f,
                b = 9f / 255f,
                baseX = 0.54f,
                driftX = 0.18f,
                speedX = 0.14f,
                phaseX = 3.0f,
                radiusX = 0.42f,
                radiusY = 0.46f,
                baseAlpha = 0.36f,
                bandIndex = 1,
                bandLift = 0.48f,
                bandScale = 0.42f,
            ),
            LobeSpec(
                isAmber = true,
                r = 217f / 255f,
                g = 119f / 255f,
                b = 6f / 255f,
                baseX = 0.74f,
                driftX = 0.15f,
                speedX = 0.21f,
                phaseX = 5.1f,
                radiusX = 0.36f,
                radiusY = 0.40f,
                baseAlpha = 0.28f,
                bandIndex = 2,
                bandLift = 0.42f,
                bandScale = 0.36f,
            ),
            LobeSpec(
                isAmber = true,
                r = 245f / 255f,
                g = 158f / 255f,
                b = 11f / 255f,
                baseX = 0.44f,
                driftX = 0.22f,
                speedX = 0.25f,
                phaseX = 0.8f,
                radiusX = 0.28f,
                radiusY = 0.32f,
                baseAlpha = 0.20f,
                bandIndex = 2,
                bandLift = 0.50f,
                bandScale = 0.44f,
            ),
        )
    }
}
