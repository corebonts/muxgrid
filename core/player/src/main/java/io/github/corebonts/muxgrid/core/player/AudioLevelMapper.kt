package io.github.corebonts.muxgrid.core.player

import io.github.corebonts.muxgrid.core.model.AudioLevels
import kotlin.math.abs
import kotlin.math.pow

/**
 * Maps FFmpeg `astats` dBFS metadata (`RMS_level`, `Peak_level`) into normalized macro-vibe
 * [AudioLevels] (`low` = sustained loudness/warmth, `mid` = fullness, `high` = rhythmic liveliness)
 * over ~1–2.5 second windows without requiring `RECORD_AUDIO` permission.
 */
internal object AudioLevelMapper {

    const val KEY_RMS_LEVEL = "lavfi.astats.Overall.RMS_level"
    const val KEY_PEAK_LEVEL = "lavfi.astats.Overall.Peak_level"

    private const val SILENCE_FLOOR_DB = -56f
    private const val RMS_MIN_DB = -52f
    private const val RMS_MAX_DB = -8f
    private const val PEAK_MIN_DB = -46f
    private const val PEAK_MAX_DB = -2f

    fun parseDb(raw: String?): Float? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.equals("-inf", ignoreCase = true) || trimmed.equals("inf", ignoreCase = true)) {
            return null
        }
        val value = trimmed.toFloatOrNull() ?: return null
        return value.takeIf { it.isFinite() }
    }

    fun fromDb(rmsDb: Float?, peakDb: Float?): AudioLevels {
        val rms = rmsDb?.takeIf { it > SILENCE_FLOOR_DB } ?: return AudioLevels.ZERO
        val rmsNorm = ((rms - RMS_MIN_DB) / (RMS_MAX_DB - RMS_MIN_DB)).coerceIn(0f, 1f)
        if (rmsNorm <= 0.001f) return AudioLevels.ZERO

        val rmsCurved = rmsNorm.pow(1.30f)
        val effectivePeakDb = peakDb?.takeIf { it > SILENCE_FLOOR_DB } ?: rms
        val peakNorm = ((effectivePeakDb - PEAK_MIN_DB) / (PEAK_MAX_DB - PEAK_MIN_DB))
            .coerceIn(0f, 1f)
            .pow(1.20f)
        val crest = ((effectivePeakDb - rms - 3f) / 14f).coerceIn(0f, 1f)
        val gate = (rmsNorm * 2.5f).coerceIn(0f, 1f)

        return AudioLevels(
            low = rmsCurved.coerceIn(0f, 1f),
            mid = (rmsCurved * 0.65f + peakNorm * 0.35f).coerceIn(0f, 1f),
            high = ((crest * 0.65f + peakNorm * 0.35f) * gate).coerceIn(0f, 1f),
        )
    }

    fun fromMetadata(rmsRaw: String?, peakRaw: String?): AudioLevels =
        fromDb(parseDb(rmsRaw), parseDb(peakRaw))
}

/**
 * Macro-energy and liveliness tracker polled at ~10 Hz (100 ms).
 *
 * Instead of chasing individual beats, integrates loudness and dynamic movement over ~1.5–2.5 s
 * so quiet passages / calm talk radio settle softly while energetic choruses glow warmer and
 * drift faster—completely immune to audio output buffer latency.
 */
internal class RadioVibeTracker {
    private var slowEnergy: Float = 0f
    private var slowFullness: Float = 0f
    private var liveliness: Float = 0f
    private var prevRmsDb: Float? = null

    fun update(rmsRaw: String?, peakRaw: String?): AudioLevels =
        updateDb(AudioLevelMapper.parseDb(rmsRaw), AudioLevelMapper.parseDb(peakRaw))

    fun updateDb(rmsDb: Float?, peakDb: Float?): AudioLevels {
        val instant = AudioLevelMapper.fromDb(rmsDb, peakDb)
        val validRms = rmsDb?.takeIf { it > SILENCE_FLOOR_DB }

        // Measure rhythmic/dynamic activity from chunk-to-chunk dB movement + transient crest
        val movement = if (validRms != null) {
            val prev = prevRmsDb
            prevRmsDb = validRms
            if (prev != null) (abs(validRms - prev) / 10f).coerceIn(0f, 1f) else 0f
        } else {
            prevRmsDb = null
            0f
        }
        val targetLiveliness = ((movement * 0.65f + instant.high * 0.55f) * (0.25f + 0.75f * instant.low))
            .coerceIn(0f, 1f)

        // Smooth macro time constants (~1.0s rise, ~1.8s fall at 10 Hz)
        slowEnergy = ema(slowEnergy, instant.low, riseAlpha = 0.14f, fallAlpha = 0.08f)
        slowFullness = ema(slowFullness, instant.mid, riseAlpha = 0.16f, fallAlpha = 0.09f)
        liveliness = ema(liveliness, targetLiveliness, riseAlpha = 0.15f, fallAlpha = 0.07f)

        return AudioLevels(
            low = slowEnergy.coerceIn(0f, 1f),
            mid = slowFullness.coerceIn(0f, 1f),
            high = liveliness.coerceIn(0f, 1f),
        )
    }

    fun reset() {
        slowEnergy = 0f
        slowFullness = 0f
        liveliness = 0f
        prevRmsDb = null
    }

    private fun ema(current: Float, target: Float, riseAlpha: Float, fallAlpha: Float): Float {
        val alpha = if (target > current) riseAlpha else fallAlpha
        return current + (target - current) * alpha
    }

    private companion object {
        private const val SILENCE_FLOOR_DB = -56f
    }
}
