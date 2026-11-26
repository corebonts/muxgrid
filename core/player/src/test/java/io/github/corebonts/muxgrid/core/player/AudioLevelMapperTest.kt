package io.github.corebonts.muxgrid.core.player

import io.github.corebonts.muxgrid.core.model.AudioLevels
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioLevelMapperTest {

    @Test
    fun `parseDb handles numbers inf and malformed values safely`() {
        val parsed = AudioLevelMapper.parseDb(" -18.5 ")
        assertNotNull(parsed)
        assertEquals(-18.5f, parsed!!, 0.001f)
        assertNull(AudioLevelMapper.parseDb("-inf"))
        assertNull(AudioLevelMapper.parseDb("inf"))
        assertNull(AudioLevelMapper.parseDb(null))
        assertNull(AudioLevelMapper.parseDb("   "))
        assertNull(AudioLevelMapper.parseDb("NaN"))
        assertNull(AudioLevelMapper.parseDb("not-a-number"))
    }

    @Test
    fun `fromMetadata maps silence to zero and normalizes loud signals within bounds`() {
        assertEquals(AudioLevels.ZERO, AudioLevelMapper.fromMetadata("-inf", "-inf"))
        assertEquals(AudioLevels.ZERO, AudioLevelMapper.fromMetadata("-72.0", "-60.0"))

        val quiet = AudioLevelMapper.fromMetadata("-42.0", "-35.0")
        val loud = AudioLevelMapper.fromMetadata("-12.0", "-3.0")

        assertTrue(quiet.low > 0f && quiet.low < loud.low)
        assertTrue(loud.low in 0f..1f)
        assertTrue(loud.mid in 0f..1f)
        assertTrue(loud.high in 0f..1f)

        val clipped = AudioLevelMapper.fromMetadata("3.0", "6.0")
        assertEquals(1f, clipped.low, 0.001f)
        assertEquals(1f, clipped.mid, 0.001f)
        assertTrue(clipped.high in 0f..1f)
    }

    @Test
    fun `vibe tracker distinguishes calm steady speech from energetic dynamic music`() {
        val calmTracker = RadioVibeTracker()
        val energeticTracker = RadioVibeTracker()

        // Calm steady speech around -28 dBFS with small dynamics
        lateinit var calm: AudioLevels
        repeat(25) {
            calm = calmTracker.updateDb(rmsDb = -28f, peakDb = -24f)
        }

        // Energetic rhythmic music swinging between -22 dBFS and -12 dBFS with sharp peaks
        lateinit var energetic: AudioLevels
        repeat(25) { i ->
            val rms = if (i % 2 == 0) -12f else -22f
            val peak = if (i % 2 == 0) -2f else -14f
            energetic = energeticTracker.updateDb(rmsDb = rms, peakDb = peak)
        }

        assertTrue(energetic.low > calm.low + 0.15f, "Expected warmer energy: calm=$calm energetic=$energetic")
        assertTrue(energetic.high > calm.high + 0.20f, "Expected higher liveliness: calm=$calm energetic=$energetic")

        // Reset clears accumulated mood cleanly
        energeticTracker.reset()
        assertEquals(AudioLevels.ZERO, energeticTracker.updateDb(rmsDb = null, peakDb = null))
    }
}
