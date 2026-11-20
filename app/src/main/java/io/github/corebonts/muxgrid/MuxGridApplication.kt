package io.github.corebonts.muxgrid

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import io.github.corebonts.muxgrid.core.data.TvheadendRepository
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.player.MpvPlaybackEngine
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import io.github.corebonts.muxgrid.core.player.PlaybackSessionHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        dvrTicketProvider = { dvrId -> repository.getDvrTicket(dvrId) },
        growingFileOpener = { dvrId -> repository.openGrowingDvrFile(dvrId) },
        dvrEntryProvider = { dvrId -> repository.getDvrEntry(dvrId) },
        nowEpochSecProvider = { repository.currentServerEpochSec() },
        initialTimeshiftWindow = repository.timeshiftWindow.value,
        conflictFinder = { channelId -> repository.findConflictingRecording(channelId) },
    )

    private var activeTrackedDvrId: Long? = null
    private var lastTrackedPosSec: Long = 0L
    private var lastTrackedDurSec: Long = 0L
    private var progressTickJob: Job? = null

    init {
        PlaybackSessionHost.register(
            playbackEngine = playbackEngine,
            activityClass = MainActivity::class.java,
            notificationIconResId = R.drawable.ic_notification,
            canZap = { repository.channels.value.size > 1 },
            onZapNext = { zapBy(1) },
            onZapPrevious = { zapBy(-1) },
        )
        applicationScope.launch {
            repository.timeshiftWindow.collect { window ->
                playbackEngine.setTimeshiftWindow(window)
            }
        }
        startDvrProgressTracking()
    }

    private fun zapBy(delta: Int) {
        val list = repository.channels.value
        if (list.isEmpty()) return
        val currentId = playbackEngine.state.value.channelId
        val idx = list.indexOfFirst { it.id == currentId }
        val nextIdx = if (idx < 0) 0 else (idx + delta + list.size) % list.size
        playbackEngine.playChannel(list[nextIdx], repository.hwDecPreset.value)
    }

    private fun startDvrProgressTracking() {
        applicationScope.launch {
            playbackEngine.state.collect { state ->
                val dvrId = state.dvrEntryId
                if (dvrId != activeTrackedDvrId) {
                    val prevId = activeTrackedDvrId
                    val prevPos = lastTrackedPosSec
                    val prevDur = lastTrackedDurSec
                    if (prevId != null && prevPos > 0L) {
                        launch {
                            repository.reportDvrPlaybackProgress(
                                dvrId = prevId,
                                positionSec = prevPos,
                                durationSec = prevDur,
                                markCompleted = false,
                            )
                        }
                    }
                    activeTrackedDvrId = dvrId
                    lastTrackedPosSec = state.positionMs / 1000L
                    lastTrackedDurSec = state.durationMs / 1000L
                    progressTickJob?.cancel()
                    progressTickJob = null
                }

                if (dvrId != null) {
                    if (state.positionMs > 0L) {
                        lastTrackedPosSec = state.positionMs / 1000L
                    }
                    if (state.durationMs > 0L) {
                        lastTrackedDurSec = state.durationMs / 1000L
                    }
                    if (state.isCompleted) {
                        progressTickJob?.cancel()
                        progressTickJob = null
                        activeTrackedDvrId = null
                        launch {
                            repository.reportDvrPlaybackProgress(
                                dvrId = dvrId,
                                positionSec = lastTrackedDurSec.coerceAtLeast(lastTrackedPosSec),
                                durationSec = lastTrackedDurSec,
                                markCompleted = true,
                            )
                        }
                    } else if (state.status == PlaybackStatus.PLAYING && progressTickJob == null) {
                        progressTickJob = launch {
                            while (isActive) {
                                delay(10_000L)
                                val current = playbackEngine.state.value
                                val curDvrId = current.dvrEntryId ?: break
                                if (current.status == PlaybackStatus.PLAYING) {
                                    repository.reportDvrPlaybackProgress(
                                        dvrId = curDvrId,
                                        positionSec = current.positionMs / 1000L,
                                        durationSec = current.durationMs / 1000L,
                                        markCompleted = false,
                                    )
                                }
                            }
                        }
                    } else if (state.status != PlaybackStatus.PLAYING && progressTickJob != null) {
                        progressTickJob?.cancel()
                        progressTickJob = null
                    }
                }
            }
        }
    }

    fun flushDvrProgress() {
        val dvrId = activeTrackedDvrId ?: return
        val current = playbackEngine.state.value
        val posSec = (current.positionMs / 1000L).takeIf { it > 0L } ?: lastTrackedPosSec
        val durSec = (current.durationMs / 1000L).takeIf { it > 0L } ?: lastTrackedDurSec
        if (posSec > 0L) {
            applicationScope.launch {
                repository.reportDvrPlaybackProgress(
                    dvrId = dvrId,
                    positionSec = posSec,
                    durationSec = durSec,
                    markCompleted = false,
                )
            }
        }
    }

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
        installCrashRecorder()
        container = MuxGridAppContainer(this)
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(throwable)
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "Thread: ${thread.name}\n$trace",
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    override fun onTerminate() {
        container.close()
        super.onTerminate()
    }
}
