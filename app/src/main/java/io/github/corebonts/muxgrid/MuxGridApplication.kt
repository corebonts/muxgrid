package io.github.corebonts.muxgrid

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import io.github.corebonts.muxgrid.core.data.TvheadendRepository
import io.github.corebonts.muxgrid.core.designsystem.isDebuggableBuild
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.player.MpvPlaybackEngine
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import io.github.corebonts.muxgrid.core.player.PlaybackSessionHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

internal const val LAST_CRASH_FILENAME = "last_crash.txt"

internal fun lastCrashFile(context: Context): File = File(context.filesDir, LAST_CRASH_FILENAME)

/**
 * Tracks active DVR playback state and reports resume position / completion back to Tvheadend.
 */
internal class DvrProgressTracker(
    private val scope: CoroutineScope,
    private val stateFlow: StateFlow<PlaybackState>,
    private val reportProgress: suspend (
        dvrId: Long,
        positionSec: Long,
        durationSec: Long,
        markCompleted: Boolean,
    ) -> Unit,
    private val tickIntervalMs: Long = 10_000L,
) {
    private var activeTrackedDvrId: Long? = null
    private var reportedCompletion: Boolean = false
    private var lastTrackedPosSec: Long = 0L
    private var lastTrackedDurSec: Long = 0L
    private var progressTickJob: Job? = null

    fun start(): Job = scope.launch {
        stateFlow.collect { state ->
            onStateChanged(state)
        }
    }

    internal fun onStateChanged(state: PlaybackState) {
        val dvrId = state.dvrEntryId
        if (dvrId != activeTrackedDvrId) {
            val prevId = activeTrackedDvrId
            val prevPos = lastTrackedPosSec
            val prevDur = lastTrackedDurSec
            val prevCompleted = reportedCompletion
            if (prevId != null && !prevCompleted && prevPos > 0L) {
                scope.launch {
                    runCatchingNonFatal {
                        reportProgress(prevId, prevPos, prevDur, false)
                    }
                }
            }
            activeTrackedDvrId = dvrId
            reportedCompletion = false
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
                if (!reportedCompletion) {
                    reportedCompletion = true
                    val finalPos = lastTrackedDurSec.coerceAtLeast(lastTrackedPosSec)
                    val finalDur = lastTrackedDurSec
                    scope.launch {
                        runCatchingNonFatal {
                            reportProgress(dvrId, finalPos, finalDur, true)
                        }
                    }
                }
            } else {
                reportedCompletion = false
                if (state.status == PlaybackStatus.PLAYING && progressTickJob == null) {
                    val job = scope.launch {
                        try {
                            while (isActive) {
                                delay(tickIntervalMs)
                                val current = stateFlow.value
                                val curDvrId = current.dvrEntryId ?: break
                                if (current.status == PlaybackStatus.PLAYING) {
                                    runCatchingNonFatal {
                                        reportProgress(
                                            curDvrId,
                                            current.positionMs / 1000L,
                                            current.durationMs / 1000L,
                                            false,
                                        )
                                    }
                                }
                            }
                        } finally {
                            if (progressTickJob === coroutineContext[Job]) {
                                progressTickJob = null
                            }
                        }
                    }
                    if (job.isActive) {
                        progressTickJob = job
                    }
                } else if (state.status != PlaybackStatus.PLAYING && progressTickJob != null) {
                    progressTickJob?.cancel()
                    progressTickJob = null
                }
            }
        }
    }

    fun flush() {
        val dvrId = activeTrackedDvrId ?: return
        if (reportedCompletion) return
        val current = stateFlow.value
        val posSec = (current.positionMs / 1000L).takeIf { it > 0L } ?: lastTrackedPosSec
        val durSec = (current.durationMs / 1000L).takeIf { it > 0L } ?: lastTrackedDurSec
        if (posSec > 0L) {
            scope.launch {
                runCatchingNonFatal {
                    reportProgress(dvrId, posSec, durSec, false)
                }
            }
        }
    }

    private suspend inline fun runCatchingNonFatal(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

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
        initialSubtitlePreference = repository.subtitlePreference.value,
        initialRadioAmbientGlowEnabled = repository.radioAmbientGlowEnabled.value,
        onSubtitlePreferenceChanged = { preference ->
            applicationScope.launch {
                runCatching { repository.setSubtitlePreference(preference) }
            }
        },
        conflictFinder = { channelId -> repository.findConflictingRecording(channelId) },
    )

    private val dvrProgressTracker = DvrProgressTracker(
        scope = applicationScope,
        stateFlow = playbackEngine.state,
        reportProgress = { dvrId, posSec, durSec, markCompleted ->
            repository.reportDvrPlaybackProgress(
                dvrId = dvrId,
                positionSec = posSec,
                durationSec = durSec,
                markCompleted = markCompleted,
            )
        },
    )

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
        applicationScope.launch {
            repository.subtitlePreference.collect { preference ->
                playbackEngine.setSubtitlePreference(preference)
            }
        }
        applicationScope.launch {
            repository.radioAmbientGlowEnabled.collect { enabled ->
                playbackEngine.setRadioAmbientGlowEnabled(enabled)
            }
        }
        dvrProgressTracker.start()
    }

    private fun zapBy(delta: Int) {
        val list = repository.channels.value
        if (list.isEmpty()) return
        val currentId = playbackEngine.state.value.channelId
        val idx = list.indexOfFirst { it.id == currentId }
        val nextIdx = if (idx < 0) 0 else (idx + delta + list.size) % list.size
        playbackEngine.playChannel(list[nextIdx], repository.hwDecPreset.value)
    }

    fun flushDvrProgress() {
        dvrProgressTracker.flush()
    }

    fun close() {
        playbackEngine.destroy()
        applicationScope.cancel()
    }

    companion object {
        fun detectFormFactor(context: Context): FormFactor {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val isTvUi = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
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
        if (isDebuggableBuild()) {
            installCrashRecorder()
        }
        container = MuxGridAppContainer(this)
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(throwable)
                lastCrashFile(this).writeText(
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
