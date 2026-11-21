package io.github.corebonts.muxgrid.core.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Process-level host connecting [MuxGridMediaSessionService] to the application's [PlaybackEngine]
 * without introducing a dependency from `:core:player` to `:app`.
 */
object PlaybackSessionHost {
    @Volatile
    internal var engine: PlaybackEngine? = null

    @Volatile
    internal var activityClass: Class<*>? = null

    @Volatile
    internal var smallIconResId: Int = 0

    @Volatile
    internal var canZap: () -> Boolean = { false }

    @Volatile
    internal var onZapNext: () -> Unit = {}

    @Volatile
    internal var onZapPrevious: () -> Unit = {}

    fun register(
        playbackEngine: PlaybackEngine,
        activityClass: Class<*>,
        @DrawableRes notificationIconResId: Int,
        canZap: () -> Boolean,
        onZapNext: () -> Unit,
        onZapPrevious: () -> Unit,
    ) {
        this.engine = playbackEngine
        this.activityClass = activityClass
        this.smallIconResId = notificationIconResId
        this.canZap = canZap
        this.onZapNext = onZapNext
        this.onZapPrevious = onZapPrevious
    }

    fun ensureServiceStarted(context: Context) {
        if (engine == null) return
        val appContext = context.applicationContext
        val intent = Intent(appContext, MuxGridMediaSessionService::class.java)
        // Use regular startService while foregrounded; MediaSessionService promotes itself via
        // MediaNotificationManager only while playback is ongoing, avoiding 5s FGS timeout crashes
        // if a stream immediately fails (e.g. TunerBusy).
        runCatching {
            appContext.startService(intent)
        }
    }
}

/**
 * Foreground Media3 [MediaSessionService] hosting the active [MediaSession] for lock-screen controls,
 * Bluetooth/headset media keys, and background Radio / Live TV audio playback.
 */
@OptIn(UnstableApi::class)
class MuxGridMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var sessionPlayer: MuxGridSessionPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val engine = PlaybackSessionHost.engine
        if (engine == null) {
            stopSelf()
            return
        }

        val notificationProvider = DefaultMediaNotificationProvider(this).apply {
            val icon = PlaybackSessionHost.smallIconResId
            if (icon != 0) {
                setSmallIcon(icon)
            }
        }
        setMediaNotificationProvider(notificationProvider)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)

        val player = MuxGridSessionPlayer(
            playbackEngine = engine,
            canZap = { PlaybackSessionHost.canZap() },
            onZapNext = { PlaybackSessionHost.onZapNext() },
            onZapPrevious = { PlaybackSessionHost.onZapPrevious() },
        )
        sessionPlayer = player

        val sessionBuilder = MediaSession.Builder(this, player)
        PlaybackSessionHost.activityClass?.let { cls ->
            val launchIntent = Intent(this, cls).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_PLAYER, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            sessionBuilder.setSessionActivity(pendingIntent)
        }
        mediaSession = sessionBuilder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = sessionPlayer
        if (player == null || !player.playWhenReady) {
            PlaybackSessionHost.engine?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        sessionPlayer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "io.github.corebonts.muxgrid.OPEN_PLAYER"
    }
}
