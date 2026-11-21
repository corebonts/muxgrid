package io.github.corebonts.muxgrid.core.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Abstraction over Android audio-focus, wake/Wi-Fi lock, and noisy-receiver APIs for testing.
 */
internal interface PlatformLockBackend {
    fun requestAudioFocus(isRadio: Boolean, listener: AudioManager.OnAudioFocusChangeListener): Boolean
    fun abandonAudioFocus()
    fun acquireWifiLock()
    fun releaseWifiLock()
    fun acquireWakeLock(timeoutMs: Long)
    fun releaseWakeLock()
    fun registerNoisyReceiver(receiver: BroadcastReceiver)
    fun unregisterNoisyReceiver(receiver: BroadcastReceiver)
}

private class AndroidPlatformLockBackend(
    context: Context,
) : PlatformLockBackend {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val wifiLock: WifiManager.WifiLock = wifiManager.createWifiLock(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        },
        "MuxGrid:StreamWifiLock",
    ).apply { setReferenceCounted(false) }

    private val wakeLock: PowerManager.WakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "MuxGrid:StreamPartialWakeLock",
    ).apply { setReferenceCounted(false) }

    private var currentFocusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    override fun requestAudioFocus(
        isRadio: Boolean,
        listener: AudioManager.OnAudioFocusChangeListener,
    ): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(
                if (isRadio) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_MOVIE,
            )
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener)
            .build()
        val granted = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) {
            currentFocusRequest = req
        }
        return granted
    }

    override fun abandonAudioFocus() {
        currentFocusRequest?.let { req ->
            runCatching { audioManager.abandonAudioFocusRequest(req) }
        }
        currentFocusRequest = null
    }

    override fun acquireWifiLock() {
        runCatching {
            if (!wifiLock.isHeld) wifiLock.acquire()
        }
    }

    override fun releaseWifiLock() {
        runCatching {
            if (wifiLock.isHeld) wifiLock.release()
        }
    }

    override fun acquireWakeLock(timeoutMs: Long) {
        runCatching {
            wakeLock.acquire(timeoutMs)
        }
    }

    override fun releaseWakeLock() {
        runCatching {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    override fun registerNoisyReceiver(receiver: BroadcastReceiver) {
        if (noisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        }
    }

    override fun unregisterNoisyReceiver(receiver: BroadcastReceiver) {
        if (!noisyReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        noisyReceiverRegistered = false
    }
}

/**
 * Manages explicit Android platform locks & audio policy for [PlaybackEngine]:
 * - [AudioManager.requestAudioFocus] / [AudioManager.abandonAudioFocusRequest]
 * - [AudioManager.ACTION_AUDIO_BECOMING_NOISY] receiver (pause on headphone / BT disconnect)
 * - [WifiManager.WifiLock] + [PowerManager.PARTIAL_WAKE_LOCK] for uninterrupted screen-off streaming
 */
internal class PlaybackPlatformLocks internal constructor(
    private val backend: PlatformLockBackend,
    private val onPauseRequested: () -> Unit,
    private val onResumeRequested: () -> Unit,
    private val onDuckChanged: (ducked: Boolean) -> Unit,
) {
    constructor(
        context: Context,
        onPauseRequested: () -> Unit,
        onResumeRequested: () -> Unit,
        onDuckChanged: (ducked: Boolean) -> Unit,
    ) : this(
        backend = AndroidPlatformLockBackend(context),
        onPauseRequested = onPauseRequested,
        onResumeRequested = onResumeRequested,
        onDuckChanged = onDuckChanged,
    )

    private val lock = Any()

    private var hasAudioFocus = false
    private var resumeOnFocusGain = false
    private var activeIsRadio = false

    internal val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        var shouldPause = false
        var shouldResume = false
        var duckState: Boolean? = null
        synchronized(lock) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    duckState = false
                    if (resumeOnFocusGain) {
                        resumeOnFocusGain = false
                        shouldResume = true
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasAudioFocus = false
                    resumeOnFocusGain = false
                    duckState = false
                    shouldPause = true
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    hasAudioFocus = false
                    resumeOnFocusGain = true
                    duckState = false
                    shouldPause = true
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    duckState = true
                }
            }
        }
        duckState?.let { onDuckChanged(it) }
        if (shouldPause) onPauseRequested()
        if (shouldResume) onResumeRequested()
    }

    internal val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                synchronized(lock) {
                    resumeOnFocusGain = false
                }
                onPauseRequested()
            }
        }
    }

    fun onPlaybackActive(isRadio: Boolean, isPlayingOrBuffering: Boolean) {
        synchronized(lock) {
            if (isPlayingOrBuffering) {
                acquireNetworkLocksLocked()
                backend.registerNoisyReceiver(noisyReceiver)
                if (!hasAudioFocus || activeIsRadio != isRadio) {
                    requestFocusLocked(isRadio)
                }
            } else {
                releaseNetworkLocksLocked()
            }
        }
    }

    fun requestFocusForStart(isRadio: Boolean): Boolean {
        synchronized(lock) {
            resumeOnFocusGain = false
            return requestFocusLocked(isRadio)
        }
    }

    fun onPlaybackStopped() {
        synchronized(lock) {
            resumeOnFocusGain = false
            releaseNetworkLocksLocked()
            backend.unregisterNoisyReceiver(noisyReceiver)
            abandonFocusLocked()
        }
        onDuckChanged(false)
    }

    fun destroy() {
        onPlaybackStopped()
    }

    private fun requestFocusLocked(isRadio: Boolean): Boolean {
        if (hasAudioFocus && activeIsRadio == isRadio) return true
        abandonFocusLocked()
        activeIsRadio = isRadio
        val granted = backend.requestAudioFocus(isRadio, focusListener)
        hasAudioFocus = granted
        return granted
    }

    private fun abandonFocusLocked() {
        backend.abandonAudioFocus()
        hasAudioFocus = false
    }

    private fun acquireNetworkLocksLocked() {
        backend.acquireWifiLock()
        backend.acquireWakeLock(MAX_WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseNetworkLocksLocked() {
        backend.releaseWifiLock()
        backend.releaseWakeLock()
    }

    companion object {
        // 6 hour safety cap refreshed on playback activation/state transitions
        internal const val MAX_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L
    }
}
