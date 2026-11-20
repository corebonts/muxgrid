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
 * Manages explicit Android platform locks & audio policy for [PlaybackEngine]:
 * - [AudioManager.requestAudioFocus] / [AudioManager.abandonAudioFocusRequest]
 * - [AudioManager.ACTION_AUDIO_BECOMING_NOISY] receiver (pause on headphone / BT disconnect)
 * - [WifiManager.WifiLock] + [PowerManager.PARTIAL_WAKE_LOCK] for uninterrupted screen-off streaming
 */
internal class PlaybackPlatformLocks(
    private val context: Context,
    private val onPauseRequested: () -> Unit,
    private val onResumeRequested: () -> Unit,
    private val onDuckChanged: (ducked: Boolean) -> Unit,
) {
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

    @Volatile
    private var hasAudioFocus = false

    @Volatile
    private var resumeOnFocusGain = false

    @Volatile
    private var noisyReceiverRegistered = false

    @Volatile
    private var activeIsRadio = false

    private var currentFocusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                onDuckChanged(false)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    onResumeRequested()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnFocusGain = false
                onDuckChanged(false)
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                resumeOnFocusGain = true
                onDuckChanged(false)
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onDuckChanged(true)
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeOnFocusGain = false
                onPauseRequested()
            }
        }
    }

    @Synchronized
    fun onPlaybackActive(isRadio: Boolean, isPlayingOrBuffering: Boolean) {
        activeIsRadio = isRadio
        if (isPlayingOrBuffering) {
            acquireNetworkLocks()
            ensureNoisyReceiverRegistered()
            if (!hasAudioFocus) {
                requestFocus(isRadio)
            }
        } else {
            releaseNetworkLocks()
        }
    }

    @Synchronized
    fun requestFocusForStart(isRadio: Boolean): Boolean {
        activeIsRadio = isRadio
        resumeOnFocusGain = false
        return requestFocus(isRadio)
    }

    @Synchronized
    fun onPlaybackStopped() {
        resumeOnFocusGain = false
        onDuckChanged(false)
        releaseNetworkLocks()
        unregisterNoisyReceiver()
        abandonFocus()
    }

    @Synchronized
    fun destroy() {
        onPlaybackStopped()
    }

    private fun requestFocus(isRadio: Boolean): Boolean {
        if (hasAudioFocus && activeIsRadio == isRadio && currentFocusRequest != null) return true
        abandonFocus()
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
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        val granted = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) {
            currentFocusRequest = req
            hasAudioFocus = true
        }
        return granted
    }

    private fun abandonFocus() {
        currentFocusRequest?.let { req ->
            runCatching { audioManager.abandonAudioFocusRequest(req) }
        }
        currentFocusRequest = null
        hasAudioFocus = false
    }

    private fun acquireNetworkLocks() {
        runCatching {
            if (!wifiLock.isHeld) wifiLock.acquire()
        }
        runCatching {
            if (!wakeLock.isHeld) wakeLock.acquire(MAX_WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseNetworkLocks() {
        runCatching {
            if (wifiLock.isHeld) wifiLock.release()
        }
        runCatching {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun ensureNoisyReceiverRegistered() {
        if (noisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                noisyReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
        noisyReceiverRegistered = false
    }

    companion object {
        // 6 hour safety cap refreshed whenever playback state ticks
        private const val MAX_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L
    }
}
