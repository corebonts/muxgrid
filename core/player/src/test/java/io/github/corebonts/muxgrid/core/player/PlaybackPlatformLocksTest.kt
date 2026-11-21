package io.github.corebonts.muxgrid.core.player

import android.content.BroadcastReceiver
import android.media.AudioManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackPlatformLocksTest {

    private class FakeBackend : PlatformLockBackend {
        val focusRequests = mutableListOf<Boolean>()
        var abandonCount = 0
        var wifiAcquireCount = 0
        var wifiReleaseCount = 0
        val wakeAcquireTimeouts = mutableListOf<Long>()
        var wakeReleaseCount = 0
        var noisyRegistered = false
        var grantFocus = true

        override fun requestAudioFocus(
            isRadio: Boolean,
            listener: AudioManager.OnAudioFocusChangeListener,
        ): Boolean {
            focusRequests.add(isRadio)
            return grantFocus
        }

        override fun abandonAudioFocus() {
            abandonCount++
        }

        override fun acquireWifiLock() {
            wifiAcquireCount++
        }

        override fun releaseWifiLock() {
            wifiReleaseCount++
        }

        override fun acquireWakeLock(timeoutMs: Long) {
            wakeAcquireTimeouts.add(timeoutMs)
        }

        override fun releaseWakeLock() {
            wakeReleaseCount++
        }

        override fun registerNoisyReceiver(receiver: BroadcastReceiver) {
            noisyRegistered = true
        }

        override fun unregisterNoisyReceiver(receiver: BroadcastReceiver) {
            noisyRegistered = false
        }
    }

    @Test
    fun `switching between TV and Radio re-acquires audio focus with updated content type`() {
        val backend = FakeBackend()
        val locks = PlaybackPlatformLocks(
            backend = backend,
            onPauseRequested = {},
            onResumeRequested = {},
            onDuckChanged = {},
        )

        assertTrue(locks.requestFocusForStart(isRadio = false))
        assertEquals(listOf(false), backend.focusRequests)

        // Same content type while focus is already held should reuse existing request
        assertTrue(locks.requestFocusForStart(isRadio = false))
        assertEquals(listOf(false), backend.focusRequests)

        // Switching from TV to Radio must re-acquire with isRadio = true
        assertTrue(locks.requestFocusForStart(isRadio = true))
        assertEquals(listOf(false, true), backend.focusRequests)

        // onPlaybackActive with different content type also re-acquires
        locks.onPlaybackActive(isRadio = false, isPlayingOrBuffering = true)
        assertEquals(listOf(false, true, false), backend.focusRequests)
    }

    @Test
    fun `onPlaybackActive refreshes wake lock timeout on repeated activations`() {
        val backend = FakeBackend()
        val locks = PlaybackPlatformLocks(
            backend = backend,
            onPauseRequested = {},
            onResumeRequested = {},
            onDuckChanged = {},
        )

        locks.onPlaybackActive(isRadio = false, isPlayingOrBuffering = true)
        locks.onPlaybackActive(isRadio = false, isPlayingOrBuffering = true)

        assertEquals(
            listOf(
                PlaybackPlatformLocks.MAX_WAKE_LOCK_TIMEOUT_MS,
                PlaybackPlatformLocks.MAX_WAKE_LOCK_TIMEOUT_MS,
            ),
            backend.wakeAcquireTimeouts,
        )
        assertTrue(backend.noisyRegistered)
    }

    @Test
    fun `onPlaybackStopped invokes onDuckChanged outside internal lock`() {
        val backend = FakeBackend()
        val duckEvents = mutableListOf<Boolean>()
        lateinit var locks: PlaybackPlatformLocks
        locks = PlaybackPlatformLocks(
            backend = backend,
            onPauseRequested = {},
            onResumeRequested = {},
            onDuckChanged = { ducked ->
                duckEvents.add(ducked)
                // Re-entering locks from another thread inside the callback must not deadlock
                val worker = Thread {
                    locks.onPlaybackActive(isRadio = false, isPlayingOrBuffering = false)
                }
                worker.start()
                worker.join(1_000)
                assertFalse(worker.isAlive, "onDuckChanged should not be invoked while holding lock")
            },
        )

        locks.onPlaybackActive(isRadio = false, isPlayingOrBuffering = true)
        locks.onPlaybackStopped()

        assertEquals(listOf(false), duckEvents)
        assertFalse(backend.noisyRegistered)
        assertEquals(2, backend.wakeReleaseCount)
        assertEquals(2, backend.wifiReleaseCount)
    }

    @Test
    fun `focus listener pauses on transient loss and resumes on regain`() {
        val backend = FakeBackend()
        var pauses = 0
        var resumes = 0
        val duckEvents = mutableListOf<Boolean>()
        val locks = PlaybackPlatformLocks(
            backend = backend,
            onPauseRequested = { pauses++ },
            onResumeRequested = { resumes++ },
            onDuckChanged = { duckEvents.add(it) },
        )

        locks.requestFocusForStart(isRadio = false)
        locks.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        locks.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        locks.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(listOf(true, false, false), duckEvents)
        assertEquals(1, pauses)
        assertEquals(1, resumes)

        // Permanent loss clears resumeOnFocusGain
        locks.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        locks.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        locks.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, resumes)
    }
}
