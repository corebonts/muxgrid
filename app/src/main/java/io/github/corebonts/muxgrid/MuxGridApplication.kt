package io.github.corebonts.muxgrid

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import io.github.corebonts.muxgrid.core.data.TvheadendRepository
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.player.MpvPlaybackEngine
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MuxGridAppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val formFactor: FormFactor = detectFormFactor(context)

    val repository = TvheadendRepository(
        context = context.applicationContext,
        formFactor = formFactor,
        scope = applicationScope,
    )

    val playbackEngine: PlaybackEngine = MpvPlaybackEngine(
        context = context.applicationContext,
        serverBaseUrlProvider = { repository.currentServerHttpBaseUrl() },
        ticketProvider = { channelId -> repository.getLiveTicket(channelId) },
        conflictFinder = { channelId -> repository.findConflictingRecording(channelId) },
    )

    fun close() {
        playbackEngine.destroy()
        applicationScope.cancel()
    }

    companion object {
        fun detectFormFactor(context: Context): FormFactor {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val isTvUi = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            return if (isTvUi || hasLeanback) FormFactor.TV else FormFactor.PHONE
        }
    }
}

class MuxGridApplication : Application() {
    lateinit var container: MuxGridAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = MuxGridAppContainer(this)
    }

    override fun onTerminate() {
        container.close()
        super.onTerminate()
    }
}
