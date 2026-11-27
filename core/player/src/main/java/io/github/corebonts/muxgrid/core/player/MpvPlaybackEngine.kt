package io.github.corebonts.muxgrid.core.player

import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import android.view.Surface
import io.github.corebonts.muxgrid.core.model.AudioLevels
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.MediaTrack
import io.github.corebonts.muxgrid.core.model.PlaybackDiagnostics
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.PlaybackSeekDefaults
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.StreamTicket
import io.github.corebonts.muxgrid.core.model.SubtitlePreference
import io.github.corebonts.muxgrid.core.model.TimeshiftNotice
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import io.github.corebonts.muxgrid.core.model.TrackType
import io.github.corebonts.muxgrid.core.model.VideoAspectMode
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * [PlaybackEngine] backed by `libmpv` (`mpv-android-lib`) and [LocalMediaProxy].
 *
 * Uses `vo=gpu` on a `SurfaceView` so bitmap `DVBSUB` subtitles and OSD composite cleanly,
 * applies device-appropriate hardware decode/deinterlace presets, manages a disk-backed live
 * timeshift ring buffer, and coordinates platform audio focus and Wi-Fi/wake locks.
 */
class MpvPlaybackEngine(
    private val context: Context,
    serverBaseUrlProvider: () -> String?,
    ticketProvider: suspend (channelId: Long) -> StreamTicket,
    dvrTicketProvider: suspend (dvrId: Long) -> StreamTicket = {
        throw UnsupportedOperationException("DVR ticket provider is not configured")
    },
    private val growingFileOpener: suspend (dvrId: Long) -> GrowingDvrFile = {
        throw UnsupportedOperationException("Growing DVR file opener is not configured")
    },
    private val dvrEntryProvider: (dvrId: Long) -> DvrEntry? = { null },
    private val nowEpochSecProvider: () -> Long = { System.currentTimeMillis() / 1000L },
    initialTimeshiftWindow: TimeshiftWindow = TimeshiftWindow.GB_1,
    initialSubtitlePreference: SubtitlePreference = SubtitlePreference(),
    initialRadioAmbientGlowEnabled: Boolean = true,
    private val onSubtitlePreferenceChanged: (SubtitlePreference) -> Unit = {},
    private val conflictFinder: (channelId: Long) -> Pair<DvrEntry, String?>?,
) : PlaybackEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mpvExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "muxgrid-mpv").apply { isDaemon = true }
    }
    private val mpvCacheDir = File(context.cacheDir, "mpv-cache")
    private val growingIndexStore = GrowingMkvIndexStore()

    private val proxy = LocalMediaProxy(
        serverBaseUrlProvider = serverBaseUrlProvider,
        ticketProvider = ticketProvider,
        dvrTicketProvider = dvrTicketProvider,
        growingFileOpener = growingFileOpener,
        isDvrRecordingProvider = { dvrId -> dvrEntryProvider(dvrId)?.state == DvrState.RECORDING },
        growingIndexStore = growingIndexStore,
        dvrStartEpochProvider = { dvrId -> dvrEntryProvider(dvrId)?.startEpochSec ?: 0L },
        onLiveOpenFailed = { channelId, reason, isEmptyReply ->
            handleProxyFailure(channelId, reason, isEmptyReply)
        },
        onLiveOpened = { channelId, statusCode, ticketMs, headersMs ->
            Log.i(TAG, "proxy live open ch=$channelId status=$statusCode ticketMs=$ticketMs headersMs=$headersMs")
        },
    )
    private val liveStrategy: LiveStreamStrategy = HttpPassStrategy(proxy)
    private val timeshiftSmoother = TimeshiftRangeSmoother()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(PlaybackDiagnostics())
    override val diagnostics: StateFlow<PlaybackDiagnostics> = _diagnostics.asStateFlow()

    private val _audioLevels = MutableStateFlow(AudioLevels.ZERO)
    override val audioLevels: StateFlow<AudioLevels> = _audioLevels.asStateFlow()

    private val _audioMeteringRequested = MutableStateFlow(false)
    private val _radioAmbientGlowEnabled = MutableStateFlow(initialRadioAmbientGlowEnabled)

    @Volatile
    private var audioMeteringEnabled: Boolean = false

    @Volatile
    private var radioAmbientGlowEnabled: Boolean = initialRadioAmbientGlowEnabled

    private var audioMeterFilterAttached: Boolean = false
    private val audioMeterPollInFlight = AtomicBoolean(false)
    private val radioVibeTracker = RadioVibeTracker()

    private data class MemorySnapshot(
        val totalPssMb: Double?,
        val nativePssMb: Double?,
        val jvmUsedMb: Double,
        val jvmMaxMb: Double,
    )

    @Volatile
    private var diagnosticsEnabled: Boolean = false

    @Volatile
    private var sessionStartElapsedMs: Long = 0L

    @Volatile
    private var currentCacheOnDisk: Boolean = false

    private var videoOutputDisabled: Boolean = false

    private var prevProxyBytes: Long = 0L
    private var prevProxySampleElapsedMs: Long = 0L
    private var smoothedNetworkRateBps: Long? = null
    private var prevCpuTimeMs: Long = -1L
    private var prevCpuWallMs: Long = 0L
    private var lastCpuPercent: Double? = null
    @Volatile
    private var lastMemorySampleElapsedMs: Long = 0L
    @Volatile
    private var cachedMemoryStats: MemorySnapshot? = null
    @Volatile
    private var lastDiskCacheSampleElapsedMs: Long = 0L
    @Volatile
    private var cachedDiskCacheBytes: Long? = null
    @Volatile
    private var mpvReportsFileCacheBytes: Boolean = false

    private val destroyed = AtomicBoolean(false)
    private val playbackGeneration = AtomicLong(0L)
    private val seekRequestSeq = AtomicLong(0L)

    @Volatile
    private var completedSeekSeq: Long = 0L

    @Volatile
    private var seekGuardUntilElapsedMs: Long = 0L

    @Volatile
    private var mpvSeeking: Boolean = false

    @Volatile
    private var mpvPausedForCache: Boolean = false

    @Volatile
    private var activeSeekTargetMs: Long? = null

    @Volatile
    private var seekOriginPositionMs: Long = 0L

    @Volatile
    private var lastDemuxerCacheStartMs: Long = 0L

    @Volatile
    private var lastDemuxerCacheTimeMs: Long = 0L

    @Volatile
    private var activeSpliceStartMs: Long = 0L

    @Volatile
    private var spliceTimeOffsetMs: Long = 0L

    @Volatile
    private var spliceOffsetResolved: Boolean = true

    @Volatile
    private var pendingLoadGeneration: Long = 0L

    @Volatile
    private var startedMpvGeneration: Long = 0L

    @Volatile
    private var mpv: MPV? = null

    @Volatile
    private var hasSurface: Boolean = false

    private var attachedSurface: Surface? = null
    private var attachedSurfaceWidth: Int = 0
    private var attachedSurfaceHeight: Int = 0
    private var surfaceAttachedToMpv: Boolean = false

    @Volatile
    private var pendingUrl: String? = null

    @Volatile
    private var pendingUrlGeneration: Long = 0L

    @Volatile
    private var activeChannel: Channel? = null

    @Volatile
    private var activeDvrEntry: DvrEntry? = null

    @Volatile
    private var explicitlyStopped: Boolean = true

    @Volatile
    private var currentPreset: HwDecPreset = HwDecPreset.MEDIACODEC_ZERO_COPY

    @Volatile
    private var currentTimeshiftWindow: TimeshiftWindow = initialTimeshiftWindow

    @Volatile
    private var currentSubtitlePreference: SubtitlePreference = initialSubtitlePreference

    @Volatile
    private var effectiveForwardCapBytes: Long = DEFAULT_RAM_CACHE_BYTES

    @Volatile
    private var hadFileLoaded: Boolean = false

    @Volatile
    private var retriedLiveReconnect: Boolean = false

    @Volatile
    private var lastObservedMpvDurationMs: Long = 0L

    @Volatile
    private var lastPositionEmitElapsedMs: Long = 0L

    @Volatile
    private var lastEmittedPosMs: Long = -1L

    @Volatile
    private var lastGrowingServerSec: Long = 0L

    @Volatile
    private var lastGrowingTickElapsedMs: Long = 0L

    @Volatile
    private var explicitAudioOnly: Boolean = false

    @Volatile
    private var currentAspectMode: VideoAspectMode = VideoAspectMode.FIT

    private val platformLocks = PlaybackPlatformLocks(
        context = context,
        onPauseRequested = { setPaused(true) },
        onResumeRequested = { setPaused(false) },
        onDuckChanged = { ducked ->
            submitMpvTask {
                val instance = mpv ?: return@submitMpvTask
                if (instance.isInitialized) {
                    instance.setPropertyDouble(
                        "volume",
                        if (ducked) MPV_VOLUME_DUCKED else MPV_VOLUME_FULL,
                    )
                }
            }
        },
    )

    private val systemDiagnosticsLock = Any()
    private var cacheMonitorJob: Job? = null
    private var audioMeterJob: Job? = null
    private var audioMeterJobGen: Long = 0L
    private var systemDiagnosticsJob: Job? = null
    private var noticeClearJob: Job? = null
    private var activeBinarySeekJob: Job? = null

    private data class AudioMeterTriggerState(
        val requested: Boolean,
        val glowEnabled: Boolean,
        val status: PlaybackStatus,
        val isPaused: Boolean,
        val isRadio: Boolean,
        val isAudioStage: Boolean,
    )

    init {
        submitMpvTask {
            initMpvLocked()
        }
        engineScope.launch {
            _state
                .map { Triple(it.status, it.isPaused, it.isRadio) }
                .distinctUntilChanged()
                .collect { (status, isPaused, isRadio) ->
                    when (status) {
                        PlaybackStatus.IDLE, PlaybackStatus.ERROR -> platformLocks.onPlaybackStopped()
                        PlaybackStatus.BUFFERING, PlaybackStatus.PLAYING, PlaybackStatus.PAUSED -> {
                            if (status == PlaybackStatus.BUFFERING || status == PlaybackStatus.PLAYING) {
                                PlaybackSessionHost.ensureServiceStarted(context)
                            }
                            val active = !isPaused &&
                                (status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING)
                            platformLocks.onPlaybackActive(isRadio = isRadio, isPlayingOrBuffering = active)
                        }
                    }
                }
        }
        engineScope.launch {
            combine(
                _audioMeteringRequested,
                _radioAmbientGlowEnabled,
                _state.map {
                    Triple(it.status, it.isPaused, it.isRadio to (it.isRadio || it.isAudioOnly))
                }.distinctUntilChanged(),
            ) { requested, glowEnabled, (status, isPaused, audioFlags) ->
                val (isRadio, isAudioStage) = audioFlags
                AudioMeterTriggerState(
                    requested = requested,
                    glowEnabled = glowEnabled,
                    status = status,
                    isPaused = isPaused,
                    isRadio = isRadio,
                    isAudioStage = isAudioStage,
                )
            }
                .distinctUntilChanged()
                .collect { trigger ->
                    val isPlaybackActive = !explicitlyStopped &&
                        trigger.status != PlaybackStatus.IDLE &&
                        trigger.status != PlaybackStatus.ERROR
                    val shouldAttachFilter = shouldAttachAudioMeterFilter(
                        isPlaybackActive = isPlaybackActive,
                        isRadio = trigger.isRadio,
                        isAudioStage = trigger.isAudioStage,
                        radioAmbientGlowEnabled = trigger.glowEnabled,
                        audioMeteringRequested = trigger.requested,
                    )
                    val shouldPoll = shouldAttachFilter &&
                        trigger.requested &&
                        trigger.isAudioStage &&
                        trigger.status == PlaybackStatus.PLAYING &&
                        !trigger.isPaused

                    if (!shouldPoll) {
                        stopAudioMeterPolling()
                    }
                    submitMpvTask {
                        val instance = mpv ?: return@submitMpvTask
                        if (instance.isInitialized) {
                            syncAudioMeterFilterLocked(instance, shouldAttachFilter)
                        }
                    }
                    if (shouldPoll) {
                        startAudioMeterPolling(playbackGeneration.get())
                    }
                }
        }
    }

    private inline fun submitMpvTask(crossinline block: () -> Unit) {
        if (destroyed.get() || mpvExecutor.isShutdown) return
        try {
            mpvExecutor.execute {
                if (!destroyed.get()) {
                    block()
                }
            }
        } catch (_: RejectedExecutionException) {
            // Engine was destroyed concurrently
        }
    }

    private fun initMpvLocked() {
        val cfgDir = File(context.filesDir, "mpv").apply { mkdirs() }.absolutePath
        mpvCacheDir.mkdirs()
        // Prune any orphaned temp cache files left behind by an earlier process kill
        mpvCacheDir.listFiles()?.forEach { file ->
            runCatching { file.deleteRecursively() }
        }
        val instance = MPV(context.applicationContext, cfgDir, mpvCacheDir.absolutePath).apply {
            setOptionString("profile", "fast")
            setOptionString("vo", "gpu")
            setOptionString("gpu-context", "android")
            setOptionString("opengl-es", "yes")
            setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            // Give DVB MPEG-TS video streams enough probe budget to catch an H.264/HEVC SPS/PPS
            // keyframe so h264_mediacodec opens with valid dimensions instead of falling back
            // to software decode (FFmpeg returns early as soon as stream info is complete).
            setOptionString("demuxer-lavf-o", VIDEO_DEMUXER_LAVF_O)
            setOptionString("cache", "yes")
            setOptionString("cache-pause", "yes")
            setOptionString("cache-pause-initial", "yes")
            setOptionString("cache-pause-wait", DEFAULT_CACHE_PAUSE_WAIT_ARG)
            setOptionString("demuxer-seekable-cache", "yes")
            setOptionString("demuxer-donate-buffer", "yes")
            setOptionString("demuxer-cache-dir", mpvCacheDir.absolutePath)
            setOptionString("demuxer-max-bytes", DEFAULT_RAM_CACHE_ARG)
            setOptionString("demuxer-max-back-bytes", DEFAULT_RAM_CACHE_ARG)
            setOptionString("sub-visibility", "yes")
            setOptionString("force-window", "no")
            setOptionString("idle", "yes")
            setPropertyString("idle", "yes")
            // Prevent mpv's built-in ytdl_hook from spawning yt-dlp subprocesses via clone/fork
            // on HTTP error responses (e.g. 503 on TunerBusy), which corrupts Android fdsan state.
            setOptionString("ytdl", "no")
            setPropertyString("ytdl", "no")
            setOptionString("load-scripts", "no")
            setPropertyString("load-scripts", "no")
            setOptionString("script-opts-append", "ytdl_hook-exclude=.")
            setOptionString("pause", "no")
            setPropertyBoolean("pause", false)

            applyPresetOnInstance(this, currentPreset)
            applySubtitlePreferenceLocked(this, currentSubtitlePreference)

            observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
            observeProperty("core-idle", MPV.mpvFormat.MPV_FORMAT_FLAG)
            observeProperty("seekable", MPV.mpvFormat.MPV_FORMAT_FLAG)
            observeProperty("seeking", MPV.mpvFormat.MPV_FORMAT_FLAG)
            observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG)
            observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("hwdec-current", MPV.mpvFormat.MPV_FORMAT_STRING)
            observeProperty("deinterlace", MPV.mpvFormat.MPV_FORMAT_STRING)
            observeProperty("video-params/w", MPV.mpvFormat.MPV_FORMAT_INT64)
            observeProperty("video-params/h", MPV.mpvFormat.MPV_FORMAT_INT64)
            observeProperty("aid", MPV.mpvFormat.MPV_FORMAT_STRING)
            observeProperty("sid", MPV.mpvFormat.MPV_FORMAT_STRING)

            addObserver(mpvEventObserver)
        }
        mpv = instance
    }

    override fun playChannel(channel: Channel, preset: HwDecPreset) {
        val gen = playbackGeneration.incrementAndGet()
        activeChannel = channel
        activeDvrEntry = null
        currentPreset = preset
        explicitlyStopped = false
        explicitAudioOnly = channel.isRadio
        hadFileLoaded = false
        retriedLiveReconnect = false
        lastObservedMpvDurationMs = 0L
        lastPositionEmitElapsedMs = 0L
        lastEmittedPosMs = -1L
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        resetSeekTracking()
        lastGrowingServerSec = 0L
        lastGrowingTickElapsedMs = 0L
        timeshiftSmoother.reset()
        noticeClearJob?.cancel()
        resetDiagnosticsSession()

        platformLocks.requestFocusForStart(isRadio = channel.isRadio)

        _state.update {
            PlaybackState(
                status = PlaybackStatus.BUFFERING,
                channelId = channel.id,
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                isRadio = channel.isRadio,
                isAudioOnly = explicitAudioOnly,
                aspectMode = currentAspectMode,
                isPaused = false,
                error = null,
            )
        }

        val url = liveStrategy.resolveLiveUrl(channel.id)
        val requestedWindow = currentTimeshiftWindow
        Log.i(TAG, "playChannel id=${channel.id} (${channel.name}) timeshift=$requestedWindow mode=live")
        submitMpvTask {
            if (playbackGeneration.get() != gen || explicitlyStopped) return@submitMpvTask
            val instance = ensureMpvLocked() ?: return@submitMpvTask
            if (instance.isInitialized) {
                instance.command("stop")
            }
            liveStrategy.stopActive()
            applyPresetOnInstance(instance, preset)
            applyAspectModeLocked(instance, currentAspectMode)
            applySubtitlePreferenceLocked(instance, currentSubtitlePreference)
            configureLiveTimeshiftLocked(instance, requestedWindow)
            applyCachePauseSettingsLocked(instance, isRadio = channel.isRadio)
            applyDemuxerProbeSettingsLocked(instance, isRadio = channel.isRadio)
            instance.setPropertyString("idle", "yes")
            instance.setPropertyString("start", "none")
            instance.setPropertyDouble("volume", MPV_VOLUME_FULL)
            configureVideoModeForStartLocked(instance, audioOnly = explicitAudioOnly)
            syncAudioMeterFilterLocked(
                instance,
                shouldAttachAudioMeterFilter(
                    isPlaybackActive = true,
                    isRadio = channel.isRadio,
                    isAudioStage = explicitAudioOnly,
                    radioAmbientGlowEnabled = radioAmbientGlowEnabled,
                    audioMeteringRequested = audioMeteringEnabled,
                ),
            )
            instance.setPropertyBoolean("pause", false)
            if (hasSurface || channel.isRadio) {
                pendingUrl = null
                pendingUrlGeneration = 0L
                pendingLoadGeneration = gen
                val rc = instance.command("loadfile", url)
                Log.i(TAG, "loadfile immediate channel=${channel.id} rc=$rc")
            } else {
                pendingUrl = url
                pendingUrlGeneration = gen
                Log.i(TAG, "loadfile deferred until surfaceCreated channel=${channel.id}")
            }
        }
        startCacheMonitor(gen)
    }

    override fun playDvrEntry(
        entry: DvrEntry,
        channel: Channel?,
        preset: HwDecPreset,
        startPositionSec: Long,
    ) {
        val gen = playbackGeneration.incrementAndGet()
        activeChannel = channel
        activeDvrEntry = entry
        currentPreset = preset
        explicitlyStopped = false
        explicitAudioOnly = channel?.isRadio == true
        hadFileLoaded = false
        retriedLiveReconnect = false
        lastObservedMpvDurationMs = 0L
        lastPositionEmitElapsedMs = 0L
        lastEmittedPosMs = -1L
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        resetSeekTracking()
        lastGrowingServerSec = 0L
        lastGrowingTickElapsedMs = 0L
        timeshiftSmoother.reset()
        noticeClearJob?.cancel()
        resetDiagnosticsSession()

        platformLocks.requestFocusForStart(isRadio = explicitAudioOnly)

        val isGrowing = entry.state == DvrState.RECORDING
        val initialDurationMs = if (isGrowing) {
            ((nowEpochSecProvider() - entry.startEpochSec).coerceAtLeast(1L)) * 1000L
        } else {
            entry.durationSec * 1000L
        }
        val resumeSec = startPositionSec.coerceAtLeast(0L)
        var spliceOffset = 0L
        var needsColdBinarySearch = false
        activeSpliceStartMs = 0L
        spliceTimeOffsetMs = 0L
        spliceOffsetResolved = true
        if (isGrowing && resumeSec > 0L) {
            val cachedIndex = growingIndexStore.getOrLoad(entry.id, entry.startEpochSec)
            val resumeMs = resumeSec * 1000L
            val cluster = if (cachedIndex.hasHeader) cachedIndex.findClusterAtOrBefore(resumeMs) else null
            if (cluster != null &&
                cluster.byteOffset > 0L &&
                (resumeMs - cluster.timestampMs) <= INDEX_SPLICE_MAX_DELTA_MS
            ) {
                spliceOffset = cluster.byteOffset
                activeSpliceStartMs = cluster.timestampMs
                spliceOffsetResolved = false
            } else if (resumeSec > COLD_RESUME_BINARY_SEARCH_MIN_SEC &&
                cachedIndex.containerKind != GrowingMkvIndex.ContainerKind.NON_MKV
            ) {
                needsColdBinarySearch = true
            }
        }
        val mpvStartArg = when {
            spliceOffset > 0L -> "none"
            resumeSec > 0L -> "+$resumeSec"
            else -> "none"
        }
        _state.update {
            PlaybackState(
                status = PlaybackStatus.BUFFERING,
                channelId = channel?.id ?: entry.channelId,
                dvrEntryId = entry.id,
                channelNumber = channel?.formattedNumber,
                channelName = channel?.name,
                programTitle = entry.title,
                isRadio = channel?.isRadio ?: false,
                isAudioOnly = explicitAudioOnly,
                aspectMode = currentAspectMode,
                isPaused = false,
                isSeekable = true,
                isGrowingRecording = isGrowing,
                positionMs = resumeSec * 1000L,
                durationMs = initialDurationMs,
                error = null,
            )
        }

        val isRadio = channel?.isRadio == true
        val url = if (isGrowing) {
            proxy.growingDvrStreamUrl(entry.id, spliceOffset = spliceOffset)
        } else {
            proxy.dvrStreamUrl(entry.id)
        }
        val mode = if (isGrowing) "growing" else "dvr"
        Log.i(TAG, "playDvrEntry id=${entry.id} (${entry.title}) mode=$mode start=${resumeSec}s spliceOffset=$spliceOffset")
        submitMpvTask {
            if (playbackGeneration.get() != gen || explicitlyStopped) return@submitMpvTask
            val instance = ensureMpvLocked() ?: return@submitMpvTask
            if (instance.isInitialized) {
                instance.command("stop")
            }
            proxy.stopActiveStream()
            pendingUrl = null
            pendingUrlGeneration = 0L
            applyPresetOnInstance(instance, preset)
            applyAspectModeLocked(instance, currentAspectMode)
            applySubtitlePreferenceLocked(instance, currentSubtitlePreference)
            configureDvrCacheLocked(instance, isGrowing)
            applyCachePauseSettingsLocked(instance, isRadio = isRadio)
            applyDemuxerProbeSettingsLocked(instance, isRadio = isRadio)
            instance.setPropertyString("idle", "yes")
            instance.setPropertyDouble("volume", MPV_VOLUME_FULL)
            configureVideoModeForStartLocked(instance, audioOnly = explicitAudioOnly)
            syncAudioMeterFilterLocked(
                instance,
                shouldAttachAudioMeterFilter(
                    isPlaybackActive = true,
                    isRadio = isRadio,
                    isAudioStage = explicitAudioOnly,
                    radioAmbientGlowEnabled = radioAmbientGlowEnabled,
                    audioMeteringRequested = audioMeteringEnabled,
                ),
            )
            instance.setPropertyBoolean("pause", false)
            if (needsColdBinarySearch) {
                return@submitMpvTask
            }
            instance.setPropertyString("start", mpvStartArg)
            if (hasSurface || isRadio) {
                pendingLoadGeneration = gen
                val rc = instance.command("loadfile", url)
                Log.i(TAG, "loadfile DVR immediate id=${entry.id} rc=$rc")
            } else {
                pendingUrl = url
                pendingUrlGeneration = gen
                Log.i(TAG, "loadfile DVR deferred until surfaceCreated id=${entry.id}")
            }
        }
        if (needsColdBinarySearch) {
            activeBinarySeekJob = engineScope.launch(Dispatchers.IO) {
                val index = growingIndexStore.getOrLoad(entry.id, entry.startEpochSec)
                val found = runCatching {
                    growingFileOpener(entry.id).use { file ->
                        GrowingMkvBinarySearcher.findClusterForTarget(
                            file = file,
                            index = index,
                            targetMs = resumeSec * 1000L,
                            durationMs = initialDurationMs,
                        )
                    }
                }.getOrNull()
                if (playbackGeneration.get() != gen || explicitlyStopped) return@launch
                val resolvedOffset = found?.byteOffset ?: 0L
                val resolvedSpliceMs = found?.timestampMs ?: 0L
                val resolvedStartArg = if (resolvedOffset > 0L) "none" else "+$resumeSec"
                val resolvedUrl = proxy.growingDvrStreamUrl(entry.id, spliceOffset = resolvedOffset)
                activeSpliceStartMs = resolvedSpliceMs
                spliceTimeOffsetMs = 0L
                spliceOffsetResolved = resolvedSpliceMs == 0L
                submitMpvTask {
                    if (playbackGeneration.get() != gen || explicitlyStopped) return@submitMpvTask
                    val instance = mpv ?: return@submitMpvTask
                    if (!instance.isInitialized) return@submitMpvTask
                    instance.setPropertyString("start", resolvedStartArg)
                    if (hasSurface || isRadio) {
                        pendingUrl = null
                        pendingUrlGeneration = 0L
                        pendingLoadGeneration = gen
                        val rc = instance.command("loadfile", resolvedUrl)
                        Log.i(TAG, "loadfile DVR cold-splice id=${entry.id} spliceOffset=$resolvedOffset rc=$rc")
                    } else {
                        pendingUrl = resolvedUrl
                        pendingUrlGeneration = gen
                        Log.i(TAG, "loadfile DVR cold-splice deferred until surfaceCreated id=${entry.id}")
                    }
                }
            }
        }
        startCacheMonitor(gen)
    }

    override fun stop() {
        playbackGeneration.incrementAndGet()
        explicitlyStopped = true
        explicitAudioOnly = false
        hadFileLoaded = false
        pendingUrl = null
        pendingUrlGeneration = 0L
        activeChannel = null
        activeDvrEntry = null
        lastPositionEmitElapsedMs = 0L
        lastEmittedPosMs = -1L
        resetSeekTracking()
        lastGrowingServerSec = 0L
        lastGrowingTickElapsedMs = 0L
        timeshiftSmoother.reset()
        cacheMonitorJob?.cancel()
        cacheMonitorJob = null
        stopAudioMeterPolling()
        noticeClearJob?.cancel()
        noticeClearJob = null
        platformLocks.onPlaybackStopped()
        _state.value = PlaybackState(status = PlaybackStatus.IDLE)
        proxy.resetStreamStats()
        _diagnostics.value = PlaybackDiagnostics()
        sessionStartElapsedMs = 0L

        submitMpvTask {
            val instance = mpv
            if (instance != null && instance.isInitialized) {
                syncAudioMeterFilterLocked(instance, false)
                instance.command("stop")
                instance.setPropertyString("start", "none")
                instance.setPropertyString("force-seekable", "no")
                instance.setPropertyString("vid", "auto")
                videoOutputDisabled = false
            }
            liveStrategy.stopActive()
        }
    }

    override fun togglePause() {
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                val paused = instance.getPropertyBoolean("pause") ?: false
                if (paused) {
                    platformLocks.requestFocusForStart(isRadio = activeChannel?.isRadio == true)
                }
                instance.setPropertyBoolean("pause", !paused)
            }
        }
    }

    override fun setPaused(paused: Boolean) {
        if (!paused) {
            platformLocks.requestFocusForStart(isRadio = activeChannel?.isRadio == true)
        }
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                instance.setPropertyBoolean("pause", paused)
            }
        }
    }

    private fun resetSeekTracking() {
        activeBinarySeekJob?.cancel()
        activeBinarySeekJob = null
        seekGuardUntilElapsedMs = 0L
        completedSeekSeq = seekRequestSeq.incrementAndGet()
        mpvSeeking = false
        mpvPausedForCache = false
        activeSeekTargetMs = null
        seekOriginPositionMs = 0L
        lastDemuxerCacheStartMs = 0L
        lastDemuxerCacheTimeMs = 0L
        activeSpliceStartMs = 0L
        spliceTimeOffsetMs = 0L
        spliceOffsetResolved = true
    }

    private fun resolveIndexedSpliceForGrowingSeek(
        dvrEntry: DvrEntry,
        targetMs: Long,
        currentPositionMs: Long? = null,
    ): Pair<Long, Long>? {
        val index = growingIndexStore.getOrLoad(dvrEntry.id, dvrEntry.startEpochSec)
        if (!index.hasHeader) return null
        val cluster = index.findClusterAtOrBefore(targetMs) ?: return null
        if (cluster.byteOffset <= 0L) return null
        if ((targetMs - cluster.timestampMs) > INDEX_SPLICE_MAX_DELTA_MS) return null
        if (currentPositionMs != null &&
            targetMs > currentPositionMs + INDEX_SPLICE_JUMP_THRESHOLD_MS &&
            cluster.timestampMs <= currentPositionMs
        ) {
            return null
        }
        return cluster.byteOffset to cluster.timestampMs
    }

    private fun reloadGrowingStreamAtSplice(
        gen: Long,
        reqSeq: Long,
        dvrId: Long,
        spliceOffset: Long,
        spliceStartMs: Long,
        mpvStartArg: String,
    ) {
        activeSpliceStartMs = spliceStartMs
        spliceTimeOffsetMs = 0L
        spliceOffsetResolved = spliceStartMs == 0L
        lastDemuxerCacheStartMs = 0L
        lastDemuxerCacheTimeMs = 0L
        val url = proxy.growingDvrStreamUrl(dvrId, spliceOffset = spliceOffset)
        submitMpvTask {
            if (playbackGeneration.get() != gen || explicitlyStopped) return@submitMpvTask
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                val wasPaused = runCatching { instance.getPropertyBoolean("pause") }.getOrNull() == true
                proxy.stopActiveStream()
                instance.setPropertyString("start", mpvStartArg)
                instance.setPropertyBoolean("pause", wasPaused)
                pendingLoadGeneration = gen
                instance.command("loadfile", url, "replace")
            }
            completedSeekSeq = reqSeq
            if (seekRequestSeq.get() == reqSeq && !mpvSeeking) {
                seekGuardUntilElapsedMs = SystemClock.elapsedRealtime() + SEEK_GUARD_SETTLE_MS
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        val gen = playbackGeneration.get()
        val current = _state.value
        val clampedMs = current.clampSeekPositionMs(positionMs)
        if (current.isSeekClampedAtGrowingEdge(positionMs)) {
            postTimeshiftNotice(TimeshiftNotice.AT_LIVE_EDGE, autoClearMs = LIVE_EDGE_NOTICE_MS)
        }
        val reqSeq = seekRequestSeq.incrementAndGet()
        if (!current.isSeeking) {
            seekOriginPositionMs = current.positionMs
        }
        activeSeekTargetMs = clampedMs
        val cachedStartMs = lastDemuxerCacheStartMs
        val cachedMs = lastDemuxerCacheTimeMs
        val dvrEntry = activeDvrEntry
        seekGuardUntilElapsedMs = SystemClock.elapsedRealtime() + SEEK_GUARD_MAX_MS
        lastPositionEmitElapsedMs = 0L
        lastEmittedPosMs = clampedMs

        // If seeking in a growing MKV recording outside mpv's active RAM buffer (before active
        // splice/back-cache or far beyond forward cache), jump via cached MKV index or on-demand
        // binary search. Non-MKV (e.g. MPEG-TS) streams skip straight to native mpv seeking.
        activeBinarySeekJob?.cancel()
        activeBinarySeekJob = null
        if (current.isGrowingRecording && dvrEntry != null) {
            val index = growingIndexStore.getOrLoad(dvrEntry.id, dvrEntry.startEpochSec)
            val beforeSplice = activeSpliceStartMs > 0L && clampedMs < activeSpliceStartMs
            val beforeBackCache = cachedStartMs > 0L && clampedMs < (cachedStartMs - BACK_CACHE_SEEK_MARGIN_MS)
            val knownForwardEdgeMs = maxOf(cachedMs, current.positionMs, activeSpliceStartMs)
            val farBeyondCache = knownForwardEdgeMs > 0L && clampedMs > knownForwardEdgeMs + INDEX_SPLICE_JUMP_THRESHOLD_MS
            if ((beforeSplice || beforeBackCache || farBeyondCache) &&
                index.containerKind != GrowingMkvIndex.ContainerKind.NON_MKV
            ) {
                val splice = if (clampedMs <= 5_000L) {
                    0L to 0L
                } else {
                    resolveIndexedSpliceForGrowingSeek(dvrEntry, clampedMs, current.positionMs)
                }
                if (splice != null) {
                    val targetSpliceOffset = splice.first
                    val targetSpliceMs = splice.second
                    val startArg = if (targetSpliceOffset == 0L && clampedMs > 0L) "+${clampedMs / 1000L}" else "none"
                    _state.update { state ->
                        if (state.status == PlaybackStatus.IDLE || state.status == PlaybackStatus.ERROR) {
                            state
                        } else {
                            state.copy(
                                status = PlaybackStatus.BUFFERING,
                                positionMs = clampedMs,
                                isSeeking = true,
                                seekTargetMs = clampedMs,
                            )
                        }
                    }
                    reloadGrowingStreamAtSplice(
                        gen = gen,
                        reqSeq = reqSeq,
                        dvrId = dvrEntry.id,
                        spliceOffset = targetSpliceOffset,
                        spliceStartMs = targetSpliceMs,
                        mpvStartArg = startArg,
                    )
                    return
                }
                // Not yet in index -> binary-search the growing MKV via bounded HTSP fileRead probes
                // (immediately falls back to native mpv seek for non-MKV / MPEG-TS files).
                _state.update { state ->
                    if (state.status == PlaybackStatus.IDLE || state.status == PlaybackStatus.ERROR) {
                        state
                    } else {
                        state.copy(
                            status = PlaybackStatus.BUFFERING,
                            positionMs = clampedMs,
                            isSeeking = true,
                            seekTargetMs = clampedMs,
                        )
                    }
                }
                val searchDurationMs = current.durationMs
                activeBinarySeekJob = engineScope.launch(Dispatchers.IO) {
                    val found = runCatching {
                        growingFileOpener(dvrEntry.id).use { file ->
                            GrowingMkvBinarySearcher.findClusterForTarget(
                                file = file,
                                index = index,
                                targetMs = clampedMs,
                                durationMs = searchDurationMs,
                            )
                        }
                    }.getOrNull()
                    if (seekRequestSeq.get() != reqSeq || playbackGeneration.get() != gen || explicitlyStopped) {
                        return@launch
                    }
                    if (found != null) {
                        reloadGrowingStreamAtSplice(
                            gen = gen,
                            reqSeq = reqSeq,
                            dvrId = dvrEntry.id,
                            spliceOffset = found.byteOffset,
                            spliceStartMs = found.timestampMs,
                            mpvStartArg = "none",
                        )
                    } else if (beforeSplice) {
                        reloadGrowingStreamAtSplice(
                            gen = gen,
                            reqSeq = reqSeq,
                            dvrId = dvrEntry.id,
                            spliceOffset = 0L,
                            spliceStartMs = 0L,
                            mpvStartArg = if (clampedMs > 0L) "+${clampedMs / 1000L}" else "none",
                        )
                    } else {
                        submitMpvTask {
                            if (playbackGeneration.get() != gen || explicitlyStopped || seekRequestSeq.get() != reqSeq) {
                                return@submitMpvTask
                            }
                            val instance = mpv ?: return@submitMpvTask
                            if (instance.isInitialized) {
                                val mpvTargetMs = (clampedMs - spliceTimeOffsetMs).coerceAtLeast(0L)
                                val secStr = String.format(Locale.US, "%.3f", mpvTargetMs / 1000.0)
                                instance.command("seek", secStr, "absolute+keyframes")
                            }
                            completedSeekSeq = reqSeq
                            if (seekRequestSeq.get() == reqSeq && !mpvSeeking) {
                                seekGuardUntilElapsedMs = SystemClock.elapsedRealtime() + SEEK_GUARD_SETTLE_MS
                            }
                        }
                    }
                }
                return
            }
        }

        _state.update { state ->
            if (state.status == PlaybackStatus.IDLE || state.status == PlaybackStatus.ERROR) {
                state
            } else if (!state.isRecordingPlayback && state.isTimeshiftSeekable && state.bufferEndMs > 0L) {
                state.copy(
                    positionMs = clampedMs,
                    liveEdgeOffsetMs = (state.bufferEndMs - clampedMs).coerceAtLeast(0L),
                    isSeeking = true,
                    seekTargetMs = clampedMs,
                )
            } else {
                state.copy(
                    positionMs = clampedMs,
                    isSeeking = true,
                    seekTargetMs = clampedMs,
                )
            }
        }
        val seekFlags = if (current.isGrowingRecording) "absolute+keyframes" else "absolute"
        submitMpvTask {
            if (playbackGeneration.get() != gen || explicitlyStopped) return@submitMpvTask
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                val mpvTargetMs = (clampedMs - spliceTimeOffsetMs).coerceAtLeast(0L)
                val secStr = String.format(Locale.US, "%.3f", mpvTargetMs / 1000.0)
                instance.command("seek", secStr, seekFlags)
            }
            completedSeekSeq = reqSeq
            if (seekRequestSeq.get() == reqSeq && !mpvSeeking) {
                seekGuardUntilElapsedMs = SystemClock.elapsedRealtime() + SEEK_GUARD_SETTLE_MS
            }
        }
    }

    override fun seekBy(deltaMs: Long) {
        val current = _state.value
        if (current.isGrowingRecording || current.isSeeking || (!current.isRecordingPlayback && current.isTimeshiftSeekable)) {
            val basePosMs = if (current.isSeeking) (current.seekTargetMs ?: current.positionMs) else current.positionMs
            seekTo(basePosMs + deltaMs)
            return
        }
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                val secStr = String.format(Locale.US, "%.3f", deltaMs / 1000.0)
                instance.command("seek", secStr, "relative")
            }
        }
    }

    override fun cancelSeek() {
        val gen = playbackGeneration.get()
        val current = _state.value
        if (!current.isSeeking && activeSeekTargetMs == null) return
        activeBinarySeekJob?.cancel()
        activeBinarySeekJob = null
        val originMs = current.clampSeekPositionMs(seekOriginPositionMs)
        val reqSeq = seekRequestSeq.incrementAndGet()
        activeSeekTargetMs = null
        mpvSeeking = false
        seekGuardUntilElapsedMs = SystemClock.elapsedRealtime() + SEEK_GUARD_SUBMIT_MS
        lastPositionEmitElapsedMs = 0L
        lastEmittedPosMs = originMs
        _state.update { state ->
            if (state.status == PlaybackStatus.IDLE || state.status == PlaybackStatus.ERROR) {
                state
            } else {
                state.copy(
                    status = if (state.isPaused) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING,
                    positionMs = originMs,
                    isSeeking = false,
                    seekTargetMs = null,
                )
            }
        }
        val dvrEntry = activeDvrEntry
        if (current.isGrowingRecording && dvrEntry != null) {
            val needBackSplice = activeSpliceStartMs > 0L && originMs < activeSpliceStartMs
            val needOutOfCacheSplice = (lastDemuxerCacheStartMs > 0L && originMs < lastDemuxerCacheStartMs - BACK_CACHE_SEEK_MARGIN_MS) ||
                (originMs > maxOf(lastDemuxerCacheTimeMs, activeSpliceStartMs) + INDEX_SPLICE_JUMP_THRESHOLD_MS)
            if (needBackSplice || needOutOfCacheSplice) {
                val splice = if (originMs <= 5_000L) 0L to 0L else resolveIndexedSpliceForGrowingSeek(dvrEntry, originMs)
                if (splice != null || needBackSplice) {
                    val targetSpliceOffset = splice?.first ?: 0L
                    val targetSpliceMs = splice?.second ?: 0L
                    val startArg = if (targetSpliceOffset == 0L && originMs > 0L) "+${originMs / 1000L}" else "none"
                    reloadGrowingStreamAtSplice(
                        gen = gen,
                        reqSeq = reqSeq,
                        dvrId = dvrEntry.id,
                        spliceOffset = targetSpliceOffset,
                        spliceStartMs = targetSpliceMs,
                        mpvStartArg = startArg,
                    )
                    return
                }
            }
        }
        submitMpvTask {
            if (playbackGeneration.get() != gen || explicitlyStopped) return@submitMpvTask
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                val mpvOriginMs = (originMs - spliceTimeOffsetMs).coerceAtLeast(0L)
                val secStr = String.format(Locale.US, "%.3f", mpvOriginMs / 1000.0)
                instance.command("seek", secStr, "absolute+keyframes")
            }
            completedSeekSeq = reqSeq
            if (seekRequestSeq.get() == reqSeq && !mpvSeeking) {
                seekGuardUntilElapsedMs = SystemClock.elapsedRealtime() + SEEK_GUARD_SETTLE_MS
            }
        }
    }

    override fun seekToLiveEdge() {
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (!instance.isInitialized || activeChannel == null || activeDvrEntry != null) return@submitMpvTask
            val (_, endSec) = readSeekableCacheRangeSecLocked(instance)
            val fallbackEnd = runCatching { instance.getPropertyDouble("demuxer-cache-time") }.getOrNull()
            val liveSec = endSec ?: fallbackEnd
            if (liveSec != null && liveSec > PlaybackSeekDefaults.LIVE_EDGE_SAFETY_SEC) {
                val target = (liveSec - PlaybackSeekDefaults.LIVE_EDGE_SAFETY_SEC).coerceAtLeast(0.0)
                val secStr = String.format(Locale.US, "%.3f", target)
                instance.command("seek", secStr, "absolute")
            }
            instance.setPropertyBoolean("pause", false)
            _state.update { it.copy(timeshiftNotice = null) }
        }
    }

    override fun setAudioTrack(trackId: Int) {
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                instance.setPropertyInt("aid", trackId)
                refreshTracksLocked(instance)
            }
        }
    }

    override fun setSubtitleTrack(trackId: Int?) {
        val selectedTrackLanguage = if (trackId != null) {
            _state.value.subtitleTracks.firstOrNull { it.id == trackId }?.language
        } else {
            null
        }
        val nextPref = if (trackId == null) {
            currentSubtitlePreference.copy(enabled = false)
        } else {
            SubtitlePreference(
                enabled = true,
                preferredLanguage = SubtitlePreference.sanitizeLanguage(selectedTrackLanguage)
                    ?: currentSubtitlePreference.preferredLanguage,
            )
        }
        currentSubtitlePreference = nextPref
        runCatching { onSubtitlePreferenceChanged(nextPref) }

        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                runCatching {
                    if (trackId == null) {
                        instance.setPropertyString("sid", "no")
                    } else {
                        instance.setPropertyInt("sid", trackId)
                    }
                    refreshTracksLocked(instance)
                }.onFailure { err ->
                    Log.w(TAG, "Failed to set subtitle track=$trackId", err)
                }
            }
        }
    }

    override fun applyPreset(preset: HwDecPreset) {
        currentPreset = preset
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                applyPresetOnInstance(instance, preset)
            }
        }
    }

    override fun setTimeshiftWindow(window: TimeshiftWindow) {
        currentTimeshiftWindow = window
    }

    override fun setSubtitlePreference(preference: SubtitlePreference) {
        currentSubtitlePreference = preference
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized && !hadFileLoaded && activeChannel == null && activeDvrEntry == null) {
                applySubtitlePreferenceLocked(instance, preference)
            }
        }
    }

    private fun applySubtitlePreferenceLocked(instance: MPV, preference: SubtitlePreference) {
        if (!preference.enabled) {
            runCatching { instance.setPropertyString("sid", "no") }
                .onFailure { Log.w(TAG, "Failed to disable subtitles (sid=no)", it) }
            return
        }
        runCatching { instance.setPropertyString("slang", preference.sanitizedLanguage.orEmpty()) }
            .onFailure { Log.w(TAG, "Failed to set subtitle language=${preference.sanitizedLanguage}", it) }
        runCatching { instance.setPropertyString("sid", "auto") }
            .onFailure { Log.w(TAG, "Failed to enable auto subtitles (sid=auto)", it) }
    }

    override fun setAudioOnly(audioOnly: Boolean) {
        val effective = audioOnly || (activeChannel?.isRadio == true)
        explicitAudioOnly = effective
        _state.update { it.copy(isAudioOnly = effective) }
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (!instance.isInitialized) return@submitMpvTask
            if (effective) {
                videoOutputDisabled = true
                instance.setPropertyString("vid", "no")
                instance.setPropertyString("vo", "null")
                instance.setPropertyString("force-window", "no")
            } else {
                val surface = attachedSurface
                if (surface != null && surface.isValid) {
                    attachVideoOutputLocked(instance, surface, attachedSurfaceWidth, attachedSurfaceHeight)
                }
            }
        }
    }

    override fun setVideoAspectMode(mode: VideoAspectMode) {
        currentAspectMode = mode
        _state.update { it.copy(aspectMode = mode) }
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized) {
                applyAspectModeLocked(instance, mode)
            }
        }
    }

    override fun setDiagnosticsEnabled(enabled: Boolean) {
        diagnosticsEnabled = enabled
        if (enabled && !explicitlyStopped) {
            val gen = playbackGeneration.get()
            scheduleSystemDiagnosticsRefresh(gen = gen, refreshMpvOnCompletion = true)
            submitMpvTask {
                if (explicitlyStopped || playbackGeneration.get() != gen || !diagnosticsEnabled) return@submitMpvTask
                val instance = mpv ?: return@submitMpvTask
                if (instance.isInitialized) {
                    collectDiagnosticsLocked(instance, preloadedCacheMap = null)
                }
            }
        }
    }

    override fun setAudioMeteringEnabled(enabled: Boolean) {
        audioMeteringEnabled = enabled
        _audioMeteringRequested.value = enabled
        if (!enabled) {
            stopAudioMeterPolling()
        }
    }

    override fun setRadioAmbientGlowEnabled(enabled: Boolean) {
        radioAmbientGlowEnabled = enabled
        _radioAmbientGlowEnabled.value = enabled
    }

    override fun attachSurface(surface: Surface, width: Int, height: Int) {
        submitMpvTask {
            val instance = mpv ?: return@submitMpvTask
            if (!instance.isInitialized) return@submitMpvTask
            if (attachedSurface != null && attachedSurface !== surface && surfaceAttachedToMpv) {
                instance.detachSurface()
                surfaceAttachedToMpv = false
            }
            attachedSurface = surface
            attachedSurfaceWidth = width
            attachedSurfaceHeight = height
            hasSurface = true
            val audioOnly = explicitAudioOnly || (activeChannel?.isRadio == true)
            if (audioOnly) {
                videoOutputDisabled = true
                instance.setPropertyString("vid", "no")
                instance.setPropertyString("vo", "null")
                instance.setPropertyString("force-window", "no")
            } else {
                attachVideoOutputLocked(instance, surface, width, height)
            }
            val urlToLoad = pendingUrl
            val urlGen = pendingUrlGeneration
            if (urlToLoad != null && !explicitlyStopped && playbackGeneration.get() == urlGen) {
                pendingUrl = null
                pendingUrlGeneration = 0L
                instance.setPropertyBoolean("pause", false)
                pendingLoadGeneration = urlGen
                val rc = instance.command("loadfile", urlToLoad)
                Log.i(TAG, "loadfile on attachSurface rc=$rc")
            }
        }
    }

    private fun attachVideoOutputLocked(instance: MPV, surface: Surface, width: Int, height: Int) {
        val wasVideoDisabled = videoOutputDisabled ||
            runCatching { instance.getPropertyString("vid") }.getOrNull() == "no" ||
            runCatching { instance.getPropertyString("vo") }.getOrNull() == "null"
        if (!surfaceAttachedToMpv) {
            instance.attachSurface(surface)
            surfaceAttachedToMpv = true
        }
        // Configure surface dimensions, window, and vo=gpu BEFORE re-enabling vid=auto;
        // otherwise mpv initializes the video chain against vo=null and leaves the Surface blank.
        if (width > 0 && height > 0) {
            instance.setPropertyString("android-surface-size", "${width}x${height}")
        }
        instance.setPropertyString("force-window", "yes")
        instance.setPropertyString("vo", "gpu")
        applyAspectModeLocked(instance, currentAspectMode)
        instance.setPropertyString("vid", "auto")
        videoOutputDisabled = false

        if (wasVideoDisabled && hadFileLoaded && pendingUrl == null && activeChannel?.isRadio != true) {
            resyncVideoAfterRestoreLocked(instance)
        }
    }

    private fun resyncVideoAfterRestoreLocked(instance: MPV) {
        val paused = runCatching { instance.getPropertyBoolean("pause") }.getOrNull() == true
        val isDvr = activeDvrEntry != null
        val isBehindLive = _state.value.isBehindLive
        val (_, rangeEndSec) = readSeekableCacheRangeSecLocked(instance)
        if (isDvr || isBehindLive) {
            instance.command("seek", "0", "relative+exact")
            return
        }
        val posSec = runCatching { instance.getPropertyDouble("time-pos") }.getOrNull()
        val cacheEndSec = rangeEndSec ?: runCatching { instance.getPropertyDouble("demuxer-cache-time") }.getOrNull()
        val liveJumpSec = computeLiveVideoRestoreSeekSec(posSec, cacheEndSec)
        if (!paused && liveJumpSec != null) {
            // While vid=no, mpv discards incoming video packets but buffers audio ahead;
            // skip the audio-only cache gap to the live head so video frames start immediately.
            instance.command("seek", String.format(Locale.US, "%.3f", liveJumpSec), "absolute")
        } else if (rangeEndSec != null) {
            instance.command("seek", "0", "relative+exact")
        }
    }

    private fun ensureMpvLocked(): MPV? {
        val current = mpv
        if (current != null && current.isInitialized) return current
        initMpvLocked()
        return mpv
    }

    override fun updateSurfaceSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        submitMpvTask {
            attachedSurfaceWidth = width
            attachedSurfaceHeight = height
            val instance = mpv ?: return@submitMpvTask
            if (instance.isInitialized && hasSurface && surfaceAttachedToMpv) {
                instance.setPropertyString("android-surface-size", "${width}x${height}")
            }
        }
    }

    override fun detachSurface() {
        if (destroyed.get() || mpvExecutor.isShutdown) return
        // SurfaceHolder.Callback.surfaceDestroyed requires the Surface to stop being used
        // before returning; wait briefly for mpv to disable vo=gpu and detach the ANativeWindow.
        val task = runCatching {
            mpvExecutor.submit {
                if (!hasSurface && attachedSurface == null) return@submit
                hasSurface = false
                attachedSurface = null
                attachedSurfaceWidth = 0
                attachedSurfaceHeight = 0
                val instance = mpv ?: return@submit
                if (!instance.isInitialized) return@submit
                // Disable video decode while backgrounded / audio-only so CPU & battery stay low
                videoOutputDisabled = true
                instance.setPropertyString("vid", "no")
                instance.setPropertyString("vo", "null")
                instance.setPropertyString("force-window", "no")
                if (surfaceAttachedToMpv) {
                    instance.detachSurface()
                    surfaceAttachedToMpv = false
                }
            }
        }.getOrNull() ?: return
        runCatching {
            task.get(SURFACE_DETACH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        playbackGeneration.incrementAndGet()
        explicitlyStopped = true
        cacheMonitorJob?.cancel()
        stopAudioMeterPolling()
        cancelSystemDiagnosticsRefresh()
        noticeClearJob?.cancel()
        platformLocks.destroy()
        liveStrategy.stopActive()
        proxy.close()
        engineScope.cancel()
        try {
            mpvExecutor.execute {
                val instance = mpv
                mpv = null
                attachedSurface = null
                surfaceAttachedToMpv = false
                if (instance != null) {
                    runCatching { instance.removeObserver(mpvEventObserver) }
                    runCatching { instance.close() }
                }
            }
        } catch (_: RejectedExecutionException) {
        }
        mpvExecutor.shutdown()
    }

    private fun configureLiveTimeshiftLocked(instance: MPV, requested: TimeshiftWindow) {
        val (bytesPerDir, useDisk) = resolveEffectiveTimeshiftBudget(requested)
        effectiveForwardCapBytes = bytesPerDir
        currentCacheOnDisk = useDisk
        val mb = (bytesPerDir / BYTES_PER_MIB).coerceAtLeast(DEFAULT_RAM_CACHE_MIB)
        instance.setPropertyString("cache-on-disk", if (useDisk) "yes" else "no")
        instance.setPropertyString(
            "demuxer-seekable-cache",
            if (requested != TimeshiftWindow.OFF) "yes" else "auto",
        )
        instance.setPropertyString("demuxer-max-bytes", "${mb}MiB")
        instance.setPropertyString("demuxer-max-back-bytes", "${mb}MiB")
        instance.setPropertyString("force-seekable", "no")
        if (requested != TimeshiftWindow.OFF && !useDisk) {
            postTimeshiftNotice(
                TimeshiftNotice.LOW_STORAGE_MEMORY_BUFFER,
                autoClearMs = TIMESHIFT_NOTICE_LONG_MS,
            )
        }
    }

    private fun configureDvrCacheLocked(instance: MPV, isGrowing: Boolean) {
        if (isGrowing) {
            // Keep growing DVR playback light on RAM/GC and zero eMMC flash writes:
            // 24 MiB forward/back RAM buffer handles smooth 1080i playback + short rewinds,
            // while longer jumps use instant interpolated binary-search MKV splicing.
            effectiveForwardCapBytes = GROWING_LIGHT_RAM_CACHE_BYTES
            currentCacheOnDisk = false
            instance.setPropertyString("cache-on-disk", "no")
            instance.setPropertyString("demuxer-seekable-cache", "yes")
            instance.setPropertyString("demuxer-max-bytes", GROWING_LIGHT_RAM_CACHE_ARG)
            instance.setPropertyString("demuxer-max-back-bytes", GROWING_LIGHT_RAM_CACHE_ARG)
            instance.setPropertyString("force-seekable", "yes")
        } else {
            effectiveForwardCapBytes = DEFAULT_RAM_CACHE_BYTES
            currentCacheOnDisk = false
            instance.setPropertyString("cache-on-disk", "no")
            instance.setPropertyString("demuxer-seekable-cache", "auto")
            instance.setPropertyString("demuxer-max-bytes", DEFAULT_RAM_CACHE_ARG)
            instance.setPropertyString("demuxer-max-back-bytes", DEFAULT_RAM_CACHE_ARG)
            instance.setPropertyString("force-seekable", "no")
        }
    }

    private fun resolveEffectiveTimeshiftBudget(requested: TimeshiftWindow): Pair<Long, Boolean> {
        if (requested == TimeshiftWindow.OFF) {
            return DEFAULT_RAM_CACHE_BYTES to false
        }
        val availableBytes = runCatching {
            mpvCacheDir.mkdirs()
            StatFs(mpvCacheDir.absolutePath).availableBytes
        }.getOrDefault(0L)
        val usableBytes = (availableBytes - DISK_SAFETY_RESERVE_BYTES).coerceAtLeast(0L)
        val maxPerDirection = usableBytes / 2L
        if (maxPerDirection < DEFAULT_RAM_CACHE_BYTES) {
            return DEFAULT_RAM_CACHE_BYTES to false
        }
        return minOf(requested.maxBytesPerDirection, maxPerDirection) to true
    }

    private fun startCacheMonitor(gen: Long) {
        cacheMonitorJob?.cancel()
        cacheMonitorJob = engineScope.launch {
            while (isActive && !explicitlyStopped && playbackGeneration.get() == gen) {
                val pollMs = if (mpvSeeking || _state.value.isSeeking) SEEK_POLL_INTERVAL_MS else CACHE_POLL_INTERVAL_MS
                delay(pollMs)
                if (explicitlyStopped || playbackGeneration.get() != gen) break
                if (diagnosticsEnabled) {
                    scheduleSystemDiagnosticsRefresh(gen = gen, refreshMpvOnCompletion = false)
                }
                submitMpvTask {
                    if (explicitlyStopped || playbackGeneration.get() != gen) return@submitMpvTask
                    val instance = mpv ?: return@submitMpvTask
                    if (!instance.isInitialized) return@submitMpvTask
                    val dvrEntry = activeDvrEntry
                    var preloadedCacheMap: Map<String, `is`.xyz.mpv.MPVNode>? = null
                    if (dvrEntry != null) {
                        updateGrowingDvrStateLocked(instance, dvrEntry)
                    } else if (activeChannel != null) {
                        preloadedCacheMap = updateLiveTimeshiftStateLocked(instance)
                    }
                    if (diagnosticsEnabled) {
                        collectDiagnosticsLocked(instance, preloadedCacheMap)
                    } else {
                        updateNetworkRateSampleLocked(SystemClock.elapsedRealtime())
                    }
                }
            }
        }
    }

    private fun cancelSystemDiagnosticsRefresh() {
        val job = synchronized(systemDiagnosticsLock) {
            systemDiagnosticsJob.also { systemDiagnosticsJob = null }
        }
        job?.cancel()
    }

    private fun scheduleSystemDiagnosticsRefresh(
        gen: Long,
        refreshMpvOnCompletion: Boolean,
    ) {
        if (explicitlyStopped || playbackGeneration.get() != gen) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val needMem = cachedMemoryStats == null ||
            nowElapsed - lastMemorySampleElapsedMs >= MEMORY_STATS_SAMPLE_INTERVAL_MS
        val needDisk = currentCacheOnDisk && !mpvReportsFileCacheBytes &&
            (cachedDiskCacheBytes == null || nowElapsed - lastDiskCacheSampleElapsedMs >= DISK_CACHE_SAMPLE_INTERVAL_MS)
        if (!needMem && !needDisk) return
        synchronized(systemDiagnosticsLock) {
            if (explicitlyStopped || playbackGeneration.get() != gen || systemDiagnosticsJob?.isActive == true) {
                return
            }
            engineScope.launch(start = CoroutineStart.LAZY) {
                try {
                    refreshSystemDiagnosticsOffMpvThread()
                    if (refreshMpvOnCompletion && diagnosticsEnabled && !explicitlyStopped && playbackGeneration.get() == gen) {
                        submitMpvTask {
                            if (explicitlyStopped || playbackGeneration.get() != gen || !diagnosticsEnabled) return@submitMpvTask
                            val instance = mpv ?: return@submitMpvTask
                            if (instance.isInitialized) {
                                collectDiagnosticsLocked(instance, preloadedCacheMap = null)
                            }
                        }
                    }
                } finally {
                    synchronized(systemDiagnosticsLock) {
                        if (systemDiagnosticsJob === coroutineContext[Job]) {
                            systemDiagnosticsJob = null
                        }
                    }
                }
            }.also { created ->
                systemDiagnosticsJob = created
                created.start()
            }
        }
    }

    private suspend fun refreshSystemDiagnosticsOffMpvThread() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val needMem = cachedMemoryStats == null ||
            nowElapsed - lastMemorySampleElapsedMs >= MEMORY_STATS_SAMPLE_INTERVAL_MS
        val needDisk = currentCacheOnDisk && !mpvReportsFileCacheBytes &&
            (cachedDiskCacheBytes == null || nowElapsed - lastDiskCacheSampleElapsedMs >= DISK_CACHE_SAMPLE_INTERVAL_MS)
        if (!needMem && !needDisk) return
        withContext(Dispatchers.IO) {
            if (needMem) {
                val stats = sampleMemoryStats()
                coroutineContext.ensureActive()
                cachedMemoryStats = stats
                lastMemorySampleElapsedMs = SystemClock.elapsedRealtime()
            }
            if (needDisk) {
                val diskBytes = runCatching { measureDiskCacheBytes(mpvCacheDir) }.getOrNull()
                coroutineContext.ensureActive()
                cachedDiskCacheBytes = diskBytes
                lastDiskCacheSampleElapsedMs = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun updateGrowingDvrStateLocked(instance: MPV, entry: DvrEntry) {
        val latest = dvrEntryProvider(entry.id) ?: entry
        val stillRecording = latest.state == DvrState.RECORDING
        val effectiveDurMs = if (stillRecording) {
            val serverSec = nowEpochSecProvider()
            val nowElapsed = SystemClock.elapsedRealtime()
            if (serverSec != lastGrowingServerSec || lastGrowingTickElapsedMs == 0L) {
                lastGrowingServerSec = serverSec
                lastGrowingTickElapsedMs = nowElapsed
            }
            val subSecMs = (nowElapsed - lastGrowingTickElapsedMs).coerceIn(0L, 950L)
            val elapsedMs = ((serverSec - latest.startEpochSec).coerceAtLeast(1L)) * 1000L + subSecMs
            maxOf(lastObservedMpvDurationMs, elapsedMs)
        } else {
            maxOf(lastObservedMpvDurationMs, latest.durationSec * 1000L)
        }

        val (rangeStartSec, rangeEndSec) = readSeekableCacheRangeSecLocked(instance)
        val cacheTimeSec = rangeEndSec ?: runCatching { instance.getPropertyDouble("demuxer-cache-time") }.getOrNull()
        val timeOffsetMs = if (spliceOffsetResolved) spliceTimeOffsetMs else 0L
        val cacheStartMs = rangeStartSec?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            (it * 1000.0).toLong() + timeOffsetMs
        }
        val cacheTimeMs = cacheTimeSec?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            (it * 1000.0).toLong() + timeOffsetMs
        }
        if (cacheStartMs != null && spliceOffsetResolved) {
            lastDemuxerCacheStartMs = cacheStartMs
        }
        if (cacheTimeMs != null && spliceOffsetResolved) {
            lastDemuxerCacheTimeMs = cacheTimeMs
        }

        _state.update { current ->
            if (current.dvrEntryId != entry.id) {
                current
            } else {
                current.copy(
                    durationMs = effectiveDurMs,
                    isGrowingRecording = stillRecording,
                    isSeekable = stillRecording || current.isSeekable,
                    bufferedPositionMs = cacheTimeMs?.takeIf { it > 0L } ?: current.bufferedPositionMs,
                )
            }
        }
    }

    private fun updateLiveTimeshiftStateLocked(instance: MPV): Map<String, `is`.xyz.mpv.MPVNode>? {
        val currentState = _state.value
        if (currentState.status == PlaybackStatus.IDLE || currentState.status == PlaybackStatus.ERROR) return null

        val cacheNode = runCatching { instance.getPropertyNode("demuxer-cache-state") }.getOrNull()
        val cacheMap = runCatching { cacheNode?.asMap() }.getOrNull()
        val (rangeStartSec, rangeEndSec) = readSeekableCacheRangeSecLocked(instance, cacheMap)
        val fwBytes = cacheMap?.get("fw-bytes")?.asInt() ?: 0L

        val posSec = runCatching { instance.getPropertyDouble("time-pos") }.getOrNull()
            ?: (currentState.positionMs / 1000.0)
        val cacheEndSec = rangeEndSec
            ?: runCatching { instance.getPropertyDouble("demuxer-cache-time") }.getOrNull()
            ?: posSec
        val cacheStartSec = rangeStartSec ?: posSec.coerceAtMost(cacheEndSec)
        val seekable = runCatching { instance.getPropertyBoolean("seekable") }.getOrNull()
            ?: currentState.isSeekable

        val (smoothedStartMs, smoothedEndMs) = timeshiftSmoother.update(
            rawStartMs = (cacheStartSec * 1000.0).toLong(),
            rawEndMs = (cacheEndSec * 1000.0).toLong(),
            nowRealtimeMs = SystemClock.elapsedRealtime(),
        )
        val metrics = liveStrategy.computeTimeshiftMetrics(
            positionMs = (posSec * 1000.0).toLong(),
            cacheStartMs = smoothedStartMs,
            cacheEndMs = smoothedEndMs,
            timeshiftEnabled = currentTimeshiftWindow != TimeshiftWindow.OFF,
        )

        if (currentTimeshiftWindow != TimeshiftWindow.OFF &&
            currentState.isPaused &&
            effectiveForwardCapBytes > 0L
        ) {
            val fillRatio = fwBytes.toDouble() / effectiveForwardCapBytes.toDouble()
            if (fillRatio >= TIMESHIFT_FULL_RESUME_RATIO) {
                instance.setPropertyBoolean("pause", false)
                postTimeshiftNotice(
                    TimeshiftNotice.BUFFER_FULL_RESUMED,
                    autoClearMs = TIMESHIFT_NOTICE_LONG_MS,
                )
            } else if (fillRatio >= TIMESHIFT_WARN_RATIO) {
                postTimeshiftNotice(TimeshiftNotice.BUFFER_NEARLY_FULL, autoClearMs = null)
            } else if (currentState.timeshiftNotice == TimeshiftNotice.BUFFER_NEARLY_FULL) {
                _state.update { it.copy(timeshiftNotice = null) }
            }
        } else if (!currentState.isPaused && currentState.timeshiftNotice == TimeshiftNotice.BUFFER_NEARLY_FULL) {
            _state.update { it.copy(timeshiftNotice = null) }
        }

        _state.update { current ->
            if (current.isRecordingPlayback || current.channelId != activeChannel?.id) {
                current
            } else {
                current.copy(
                    isSeekable = seekable || metrics.isTimeshiftSeekable,
                    bufferStartMs = metrics.bufferStartMs,
                    bufferEndMs = metrics.bufferEndMs,
                    bufferWindowMs = metrics.bufferWindowMs,
                    liveEdgeOffsetMs = metrics.liveEdgeOffsetMs,
                    isTimeshiftSeekable = metrics.isTimeshiftSeekable,
                )
            }
        }
        return cacheMap
    }

    private fun readSeekableCacheRangeSecLocked(
        instance: MPV,
        preloadedMap: Map<String, `is`.xyz.mpv.MPVNode>? = null,
    ): Pair<Double?, Double?> {
        val map = preloadedMap ?: runCatching {
            instance.getPropertyNode("demuxer-cache-state")?.asMap()
        }.getOrNull() ?: return null to null
        val ranges = runCatching { map["seekable-ranges"]?.asArray() }.getOrNull() ?: return null to null
        if (ranges.isEmpty()) return null to null
        val firstStart = runCatching { ranges.first().asMap()?.get("start")?.asDouble() }.getOrNull()
        val lastEnd = runCatching { ranges.last().asMap()?.get("end")?.asDouble() }.getOrNull()
        return firstStart to lastEnd
    }

    private fun postTimeshiftNotice(notice: TimeshiftNotice, autoClearMs: Long?) {
        noticeClearJob?.cancel()
        _state.update { it.copy(timeshiftNotice = notice) }
        if (autoClearMs != null) {
            noticeClearJob = engineScope.launch {
                delay(autoClearMs)
                _state.update { current ->
                    if (current.timeshiftNotice == notice) current.copy(timeshiftNotice = null) else current
                }
            }
        }
    }

    private fun applyPresetOnInstance(instance: MPV, preset: HwDecPreset) {
        when (preset) {
            HwDecPreset.MEDIACODEC_ZERO_COPY -> {
                instance.setPropertyString("deinterlace", "no")
                instance.setPropertyString("hwdec", "mediacodec")
            }
            HwDecPreset.MEDIACODEC_COPY_BWDIF -> {
                instance.setPropertyString("hwdec", "mediacodec-copy")
                instance.setPropertyString("deinterlace", "yes")
            }
        }
    }

    private fun applyAspectModeLocked(instance: MPV, mode: VideoAspectMode) {
        when (mode) {
            VideoAspectMode.FIT -> {
                instance.setPropertyString("keepaspect", "yes")
                instance.setPropertyDouble("panscan", 0.0)
                instance.setPropertyString("video-aspect-override", "-1")
            }
            VideoAspectMode.FILL -> {
                instance.setPropertyString("keepaspect", "yes")
                instance.setPropertyDouble("panscan", 1.0)
                instance.setPropertyString("video-aspect-override", "-1")
            }
            VideoAspectMode.RATIO_16_9 -> {
                instance.setPropertyString("keepaspect", "yes")
                instance.setPropertyDouble("panscan", 0.0)
                instance.setPropertyString("video-aspect-override", "16:9")
            }
            VideoAspectMode.RATIO_4_3 -> {
                instance.setPropertyString("keepaspect", "yes")
                instance.setPropertyDouble("panscan", 0.0)
                instance.setPropertyString("video-aspect-override", "4:3")
            }
            VideoAspectMode.STRETCH -> {
                instance.setPropertyString("keepaspect", "no")
                instance.setPropertyDouble("panscan", 0.0)
                instance.setPropertyString("video-aspect-override", "-1")
            }
        }
    }

    private fun handleProxyFailure(channelId: Long, reason: String, isEmptyReply: Boolean) {
        if (explicitlyStopped || activeChannel?.id != channelId) return
        val conflict = conflictFinder(channelId)
        val error: PlaybackError = if (conflict != null) {
            PlaybackError.TunerBusy(
                recordingTitle = conflict.first.title,
                channelName = conflict.second,
            )
        } else {
            PlaybackError.StreamOpenFailed(reason)
        }
        _state.update { current ->
            if (current.channelId != channelId) current
            else current.copy(status = PlaybackStatus.ERROR, error = error)
        }
    }

    private fun isCurrentPlaybackEvent(): Boolean {
        if (explicitlyStopped) return false
        val started = startedMpvGeneration
        return started != 0L && started == playbackGeneration.get()
    }

    private val mpvEventObserver = object : MPV.EventObserver {
        override fun eventProperty(property: String) = onMpvProperty(property)
        override fun eventProperty(property: String, value: Long) = onMpvProperty(property, value)
        override fun eventProperty(property: String, value: Boolean) = onMpvProperty(property, value)
        override fun eventProperty(property: String, value: String) = onMpvProperty(property, value)
        override fun eventProperty(property: String, value: Double) = onMpvProperty(property, value)
        override fun eventProperty(property: String, value: `is`.xyz.mpv.MPVNode) = Unit
        override fun event(eventId: Int, data: `is`.xyz.mpv.MPVNode) = onMpvEvent(eventId, data)
    }

    private fun onMpvProperty(property: String) {
        if (!isCurrentPlaybackEvent()) return
        val eventGen = startedMpvGeneration
        if (property == "aid" || property == "sid") {
            submitMpvTask {
                if (explicitlyStopped || playbackGeneration.get() != eventGen) return@submitMpvTask
                mpv?.takeIf { it.isInitialized }?.let { refreshTracksLocked(it) }
            }
        }
    }

    private fun onMpvProperty(property: String, value: Long) {
        if (!isCurrentPlaybackEvent()) return
        when (property) {
            "video-params/w" -> _state.update { current ->
                if (current.status == PlaybackStatus.IDLE || current.status == PlaybackStatus.ERROR) current
                else current.copy(videoWidth = value.toInt())
            }
            "video-params/h" -> _state.update { current ->
                if (current.status == PlaybackStatus.IDLE || current.status == PlaybackStatus.ERROR) current
                else current.copy(videoHeight = value.toInt())
            }
        }
    }

    private fun onMpvProperty(property: String, value: Boolean) {
        if (!isCurrentPlaybackEvent()) return
        when (property) {
            "pause" -> _state.update { current ->
                if (current.status == PlaybackStatus.ERROR || current.status == PlaybackStatus.IDLE) {
                    current
                } else {
                    current.copy(
                        isPaused = value,
                        status = if (value) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING,
                    )
                }
            }
            "core-idle" -> _state.update { current ->
                if (current.status == PlaybackStatus.ERROR || current.status == PlaybackStatus.IDLE) {
                    current
                } else if (!value && !current.isPaused && !mpvPausedForCache) {
                    current.copy(status = PlaybackStatus.PLAYING)
                } else {
                    current
                }
            }
            "seekable" -> _state.update { current ->
                if (current.status == PlaybackStatus.IDLE || current.status == PlaybackStatus.ERROR) current
                else current.copy(isSeekable = value)
            }
            "seeking" -> {
                mpvSeeking = value
                if (value) {
                    seekGuardUntilElapsedMs = SystemClock.elapsedRealtime() + SEEK_GUARD_MAX_MS
                    _state.update { current ->
                        if (current.status == PlaybackStatus.IDLE || current.status == PlaybackStatus.ERROR) {
                            current
                        } else {
                            current.copy(
                                isSeeking = true,
                                seekTargetMs = activeSeekTargetMs ?: current.seekTargetMs,
                            )
                        }
                    }
                } else if (completedSeekSeq >= seekRequestSeq.get()) {
                    activeSeekTargetMs = null
                    seekGuardUntilElapsedMs = 0L
                    lastPositionEmitElapsedMs = 0L
                    _state.update { current ->
                        if (!current.isSeeking && current.seekTargetMs == null) {
                            current
                        } else {
                            current.copy(
                                isSeeking = false,
                                seekTargetMs = null,
                            )
                        }
                    }
                }
            }
            "paused-for-cache" -> {
                mpvPausedForCache = value
                Log.d(TAG, "paused-for-cache=$value radio=${activeChannel?.isRadio == true}")
                _state.update { current ->
                    if (current.status == PlaybackStatus.IDLE || current.status == PlaybackStatus.ERROR) {
                        current
                    } else if (value && !current.isPaused) {
                        current.copy(status = PlaybackStatus.BUFFERING)
                    } else if (!value) {
                        current.copy(status = if (current.isPaused) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun onMpvProperty(property: String, value: String) {
        if (!isCurrentPlaybackEvent()) return
        val eventGen = startedMpvGeneration
        when (property) {
            "hwdec-current" -> _state.update { current ->
                if (current.status == PlaybackStatus.IDLE || current.status == PlaybackStatus.ERROR) current
                else current.copy(hwdecCurrent = value)
            }
            "deinterlace" -> _state.update { current ->
                if (current.status == PlaybackStatus.IDLE || current.status == PlaybackStatus.ERROR) current
                else current.copy(deinterlaceActive = value == "yes" || value == "auto")
            }
            "aid", "sid" -> {
                submitMpvTask {
                    if (explicitlyStopped || playbackGeneration.get() != eventGen) return@submitMpvTask
                    mpv?.takeIf { it.isInitialized }?.let { refreshTracksLocked(it) }
                }
            }
        }
    }

    private fun onMpvProperty(property: String, value: Double) {
        if (!isCurrentPlaybackEvent()) return
        when (property) {
            "time-pos" -> {
                val rawPosMs = (value * 1000.0).toLong().coerceAtLeast(0L)
                if (activeSpliceStartMs > 0L && !spliceOffsetResolved) {
                    spliceTimeOffsetMs = if (rawPosMs >= activeSpliceStartMs - 5_000L) 0L else activeSpliceStartMs
                    spliceOffsetResolved = true
                }
                val posMs = rawPosMs + spliceTimeOffsetMs
                val nowElapsed = SystemClock.elapsedRealtime()
                if (nowElapsed < seekGuardUntilElapsedMs) {
                    return
                }
                val prevPos = lastEmittedPosMs
                val jumped = prevPos < 0L || kotlin.math.abs(posMs - prevPos) >= POSITION_JUMP_THRESHOLD_MS
                if (!jumped && (nowElapsed - lastPositionEmitElapsedMs) < POSITION_EMIT_INTERVAL_MS) {
                    return
                }
                lastPositionEmitElapsedMs = nowElapsed
                lastEmittedPosMs = posMs

                val estEndMs = timeshiftSmoother.currentEstimatedEndMs(nowElapsed)
                _state.update { current ->
                    if (current.status == PlaybackStatus.IDLE || current.status == PlaybackStatus.ERROR) {
                        current
                    } else if (!current.isRecordingPlayback && current.isTimeshiftSeekable && estEndMs > 0L) {
                        val nextEndMs = maxOf(current.bufferEndMs, estEndMs)
                        val nextWindowMs = (nextEndMs - current.bufferStartMs).coerceAtLeast(0L)
                        val nextOffsetMs = (nextEndMs - posMs).coerceAtLeast(0L)
                        current.copy(
                            positionMs = posMs,
                            bufferEndMs = nextEndMs,
                            bufferWindowMs = nextWindowMs,
                            liveEdgeOffsetMs = nextOffsetMs,
                        )
                    } else {
                        current.copy(positionMs = posMs)
                    }
                }
            }
            "duration" -> {
                // Live streams grow duration continuously with cache fill; only track duration for DVR recordings.
                if (activeDvrEntry == null) return
                val durMs = (value * 1000.0).toLong().coerceAtLeast(0L)
                if (durMs > 0L && kotlin.math.abs(durMs - lastObservedMpvDurationMs) >= 500L) {
                    lastObservedMpvDurationMs = durMs
                    _state.update { current ->
                        if (current.status == PlaybackStatus.IDLE ||
                            current.status == PlaybackStatus.ERROR ||
                            !current.isRecordingPlayback
                        ) {
                            current
                        } else if (current.isGrowingRecording) {
                            current.copy(durationMs = maxOf(current.durationMs, durMs))
                        } else {
                            current.copy(durationMs = durMs)
                        }
                    }
                }
            }
        }
    }

    private fun onMpvEvent(eventId: Int, data: `is`.xyz.mpv.MPVNode) {
        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_SHUTDOWN -> {
                Log.w(TAG, "MPV_EVENT_SHUTDOWN received; resetting instance")
                submitMpvTask {
                    val dead = mpv
                    mpv = null
                    surfaceAttachedToMpv = false
                    audioMeterFilterAttached = false
                    if (dead != null) {
                        runCatching { dead.removeObserver(mpvEventObserver) }
                        runCatching { dead.close() }
                    }
                }
            }
            MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                startedMpvGeneration = pendingLoadGeneration
                if (!isCurrentPlaybackEvent()) return
                _state.update { current ->
                    if (explicitlyStopped || current.status == PlaybackStatus.ERROR) {
                        current
                    } else {
                        current.copy(status = PlaybackStatus.BUFFERING, error = null)
                    }
                }
            }
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED,
            MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART,
            MPV.mpvEvent.MPV_EVENT_AUDIO_RECONFIG,
            MPV.mpvEvent.MPV_EVENT_VIDEO_RECONFIG,
            -> {
                if (!isCurrentPlaybackEvent()) return
                val eventGen = startedMpvGeneration
                if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
                    hadFileLoaded = true
                    retriedLiveReconnect = false
                    val elapsed = if (sessionStartElapsedMs > 0L) SystemClock.elapsedRealtime() - sessionStartElapsedMs else -1L
                    Log.i(TAG, "event=FILE_LOADED elapsedMs=$elapsed radio=${activeChannel?.isRadio == true}")
                } else if (eventId == MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART &&
                    completedSeekSeq >= seekRequestSeq.get()
                ) {
                    mpvSeeking = false
                    activeSeekTargetMs = null
                    seekGuardUntilElapsedMs = 0L
                    lastPositionEmitElapsedMs = 0L
                    _state.update { current ->
                        if (!current.isSeeking && current.seekTargetMs == null) {
                            current
                        } else {
                            current.copy(
                                isSeeking = false,
                                seekTargetMs = null,
                            )
                        }
                    }
                }
                submitMpvTask {
                    if (explicitlyStopped || playbackGeneration.get() != eventGen) return@submitMpvTask
                    val instance = mpv ?: return@submitMpvTask
                    if (instance.isInitialized) {
                        if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED && activeSpliceStartMs > 0L && !spliceOffsetResolved) {
                            val initialPosSec = runCatching { instance.getPropertyDouble("time-pos") }
                                .getOrNull()
                                ?.takeIf { it.isFinite() && it >= 0.0 }
                            if (initialPosSec != null) {
                                val initialPosMs = (initialPosSec * 1000.0).toLong()
                                spliceTimeOffsetMs = if (initialPosMs >= activeSpliceStartMs - 5_000L) {
                                    0L
                                } else {
                                    activeSpliceStartMs
                                }
                                spliceOffsetResolved = true
                            }
                        }
                        refreshTracksLocked(instance)
                        val realPaused = runCatching { instance.getPropertyBoolean("pause") }.getOrNull()
                        val realHwdec = runCatching { instance.getPropertyString("hwdec-current") }.getOrNull()
                        _state.update { current ->
                            if (explicitlyStopped ||
                                playbackGeneration.get() != eventGen ||
                                current.status == PlaybackStatus.ERROR ||
                                current.status == PlaybackStatus.IDLE
                            ) {
                                current
                            } else {
                                val paused = realPaused ?: current.isPaused
                                current.copy(
                                    isPaused = paused,
                                    hwdecCurrent = realHwdec ?: current.hwdecCurrent,
                                    status = if (paused) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING,
                                )
                            }
                        }
                    }
                }
            }
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                if (!isCurrentPlaybackEvent()) return
                val eventGen = startedMpvGeneration
                val map = runCatching { data.asMap() }.getOrNull()
                val reasonNode = map?.get("reason")
                val reasonStr = runCatching { reasonNode?.asString() }.getOrNull()
                val reasonInt = runCatching { reasonNode?.asInt()?.toInt() }.getOrNull()
                val isErrorReason = reasonStr.equals("error", ignoreCase = true) || reasonInt == 4
                val isStoppedReason = reasonStr.equals("stop", ignoreCase = true) || reasonInt == 2

                val dvrEntry = activeDvrEntry
                if (dvrEntry != null) {
                    if (isErrorReason) {
                        cacheMonitorJob?.cancel()
                        cacheMonitorJob = null
                        resetSeekTracking()
                        val fileErr = runCatching { map?.get("file_error")?.asString() }.getOrNull()
                        val msg = if (!fileErr.isNullOrBlank()) {
                            "Recording playback error: $fileErr"
                        } else {
                            "Unable to open recording"
                        }
                        _state.update { current ->
                            if (current.dvrEntryId == dvrEntry.id && current.error == null) {
                                current.copy(
                                    status = PlaybackStatus.ERROR,
                                    isSeeking = false,
                                    seekTargetMs = null,
                                    error = PlaybackError.PlaybackFailed(msg),
                                )
                            } else {
                                current
                            }
                        }
                    } else if (!isStoppedReason) {
                        cacheMonitorJob?.cancel()
                        cacheMonitorJob = null
                        resetSeekTracking()
                        playbackGeneration.incrementAndGet()
                        activeDvrEntry = null
                        _state.update { current ->
                            if (current.dvrEntryId == dvrEntry.id) {
                                current.copy(
                                    status = PlaybackStatus.IDLE,
                                    isCompleted = true,
                                    isGrowingRecording = false,
                                    isSeeking = false,
                                    seekTargetMs = null,
                                )
                            } else {
                                current
                            }
                        }
                    }
                    return
                }

                if (isStoppedReason) return

                val channel = activeChannel ?: return
                val currentError = _state.value.error
                if (currentError == null) {
                    val conflict = conflictFinder(channel.id)
                    if (conflict != null) {
                        cacheMonitorJob?.cancel()
                        cacheMonitorJob = null
                        val err = PlaybackError.TunerBusy(conflict.first.title, conflict.second)
                        _state.update { current ->
                            if (current.channelId == channel.id && current.error == null) {
                                current.copy(status = PlaybackStatus.ERROR, error = err)
                            } else {
                                current
                            }
                        }
                    } else if (hadFileLoaded && !retriedLiveReconnect) {
                        retriedLiveReconnect = true
                        timeshiftSmoother.reset()
                        postTimeshiftNotice(TimeshiftNotice.LIVE_STREAM_RECONNECTED, autoClearMs = TIMESHIFT_NOTICE_SHORT_MS)
                        val url = liveStrategy.resolveLiveUrl(channel.id)
                        submitMpvTask {
                            val instance = mpv ?: return@submitMpvTask
                            if (!instance.isInitialized ||
                                explicitlyStopped ||
                                playbackGeneration.get() != eventGen ||
                                activeChannel?.id != channel.id
                            ) {
                                return@submitMpvTask
                            }
                            instance.command("stop")
                            liveStrategy.stopActive()
                            configureLiveTimeshiftLocked(instance, currentTimeshiftWindow)
                            applyCachePauseSettingsLocked(instance, isRadio = channel.isRadio)
                            applyDemuxerProbeSettingsLocked(instance, isRadio = channel.isRadio)
                            instance.setPropertyBoolean("pause", false)
                            pendingLoadGeneration = eventGen
                            instance.command("loadfile", url)
                        }
                    } else {
                        cacheMonitorJob?.cancel()
                        cacheMonitorJob = null
                        _state.update { current ->
                            if (current.channelId == channel.id && current.error == null) {
                                current.copy(
                                    status = PlaybackStatus.ERROR,
                                    error = PlaybackError.StreamEndedUnexpectedly,
                                )
                            } else {
                                current
                            }
                        }
                    }
                }
            }
        }
    }

    private fun refreshTracksLocked(instance: MPV) {
        val node = runCatching { instance.getPropertyNode("track-list") }.getOrNull() ?: return
        val items = runCatching { node.asArray() }.getOrNull() ?: return
        val audio = ArrayList<MediaTrack>()
        val subs = ArrayList<MediaTrack>()
        var selectedAudio: Int? = null
        var selectedSub: Int? = null

        for (item in items) {
            val map = runCatching { item.asMap() }.getOrNull() ?: continue
            val id = map["id"]?.asInt()?.toInt() ?: continue
            val typeStr = map["type"]?.asString() ?: continue
            val title = map["title"]?.asString()
            val lang = map["lang"]?.asString()
            val codec = map["codec"]?.asString()
            val selected = map["selected"]?.asBoolean() ?: false
            val isDefault = map["default"]?.asBoolean() ?: false

            when (typeStr) {
                "audio" -> {
                    if (selected) selectedAudio = id
                    audio.add(
                        MediaTrack(
                            id = id,
                            type = TrackType.AUDIO,
                            title = title,
                            language = lang,
                            codec = codec,
                            isSelected = selected,
                            isDefault = isDefault,
                        ),
                    )
                }
                "sub" -> {
                    if (selected) selectedSub = id
                    subs.add(
                        MediaTrack(
                            id = id,
                            type = TrackType.SUBTITLE,
                            title = title,
                            language = lang,
                            codec = codec,
                            isSelected = selected,
                            isDefault = isDefault,
                        ),
                    )
                }
            }
        }

        _state.update { current ->
            current.copy(
                audioTracks = audio,
                subtitleTracks = subs,
                selectedAudioId = selectedAudio,
                selectedSubtitleId = selectedSub,
            )
        }
    }

    private fun resetDiagnosticsSession() {
        cancelSystemDiagnosticsRefresh()
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        prevProxyBytes = 0L
        prevProxySampleElapsedMs = sessionStartElapsedMs
        smoothedNetworkRateBps = null
        prevCpuTimeMs = Process.getElapsedCpuTime()
        prevCpuWallMs = sessionStartElapsedMs
        lastCpuPercent = null
        lastMemorySampleElapsedMs = 0L
        cachedMemoryStats = null
        lastDiskCacheSampleElapsedMs = 0L
        cachedDiskCacheBytes = null
        mpvReportsFileCacheBytes = false
        proxy.resetStreamStats()
        _diagnostics.value = PlaybackDiagnostics()
    }

    private fun updateNetworkRateSampleLocked(nowElapsed: Long): LocalMediaProxy.ProxyStreamStats {
        val proxyStats = proxy.snapshotStreamStats()
        val prevMs = prevProxySampleElapsedMs
        if (prevMs > 0L) {
            val dtMs = nowElapsed - prevMs
            if (dtMs >= 250L) {
                val deltaBytes = (proxyStats.totalBytesRead - prevProxyBytes).coerceAtLeast(0L)
                val instantBps = (deltaBytes * 8_000L) / dtMs
                smoothedNetworkRateBps = smoothedNetworkRateBps?.let { prev ->
                    ((prev * 0.6) + (instantBps * 0.4)).toLong()
                } ?: instantBps
                prevProxyBytes = proxyStats.totalBytesRead
                prevProxySampleElapsedMs = nowElapsed
            }
        } else {
            prevProxyBytes = proxyStats.totalBytesRead
            prevProxySampleElapsedMs = nowElapsed
        }
        return proxyStats
    }

    private fun collectDiagnosticsLocked(
        instance: MPV,
        preloadedCacheMap: Map<String, `is`.xyz.mpv.MPVNode>?,
    ) {
        val currentState = _state.value
        if (currentState.status == PlaybackStatus.IDLE || currentState.status == PlaybackStatus.ERROR) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val proxyStats = updateNetworkRateSampleLocked(nowElapsed)

        val fileFormat = runCatching { instance.getPropertyString("file-format") }.getOrNull()?.takeIf { it.isNotBlank() }
        val speed = runCatching { instance.getPropertyDouble("speed") }.getOrNull() ?: 1.0
        val pausedForCache = runCatching { instance.getPropertyBoolean("paused-for-cache") }.getOrNull() ?: false
        val cacheBufferingPercent = runCatching { instance.getPropertyLong("cache-buffering-state")?.toInt() }.getOrNull()

        val videoCodec = (
            runCatching { instance.getPropertyString("video-format") }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching { instance.getPropertyString("video-codec") }.getOrNull()?.takeIf { it.isNotBlank() }
            )
        val displayWidth = runCatching { instance.getPropertyLong("video-params/dw")?.toInt() }.getOrNull() ?: 0
        val displayHeight = runCatching { instance.getPropertyLong("video-params/dh")?.toInt() }.getOrNull() ?: 0
        val pixelFormat = runCatching { instance.getPropertyString("video-params/pixelformat") }.getOrNull()?.takeIf { it.isNotBlank() }
        val hwPixelFormat = runCatching { instance.getPropertyString("video-params/hw-pixelformat") }.getOrNull()?.takeIf { it.isNotBlank() }
        val colorMatrix = runCatching { instance.getPropertyString("video-params/colormatrix") }.getOrNull()?.takeIf { it.isNotBlank() }
        val primaries = runCatching { instance.getPropertyString("video-params/primaries") }.getOrNull()?.takeIf { it.isNotBlank() }
        val gamma = runCatching { instance.getPropertyString("video-params/gamma") }.getOrNull()?.takeIf { it.isNotBlank() }
        val colorInfo = buildList {
            colorMatrix?.let { add(it) }
            if (primaries != null && primaries != colorMatrix) add(primaries)
            if (gamma != null && gamma != "bt.1886" && gamma != "auto") add(gamma)
        }.joinToString(" · ").takeIf { it.isNotBlank() }

        val containerFps = runCatching { instance.getPropertyDouble("container-fps") }.getOrNull()?.takeIf { it > 0.0 }
        val actualFps = runCatching { instance.getPropertyDouble("estimated-vf-fps") }.getOrNull()?.takeIf { it > 0.0 }
        val videoBitrateBps = runCatching { instance.getPropertyDouble("video-bitrate")?.toLong() }.getOrNull()?.takeIf { it > 0L }
        val hwdecCurrent = runCatching { instance.getPropertyString("hwdec-current") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: currentState.hwdecCurrent
        val videoOutput = runCatching { instance.getPropertyString("current-vo") }.getOrNull()?.takeIf { it.isNotBlank() }
        val droppedVo = runCatching { instance.getPropertyLong("frame-drop-count") }.getOrNull()
        val droppedDec = runCatching { instance.getPropertyLong("decoder-frame-drop-count") }.getOrNull()
        val delayedVo = runCatching { instance.getPropertyLong("vo-delayed-frame-count") }.getOrNull()
        val avSyncMs = runCatching { instance.getPropertyDouble("avsync")?.times(1000.0) }.getOrNull()

        val audioCodec = (
            runCatching { instance.getPropertyString("audio-codec-name") }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching { instance.getPropertyString("audio-codec") }.getOrNull()?.takeIf { it.isNotBlank() }
            )
        val audioChannels = runCatching { instance.getPropertyString("audio-params/hr-channels") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { instance.getPropertyLong("audio-params/channel-count")?.toString() }.getOrNull()
        val audioSampleRate = runCatching { instance.getPropertyLong("audio-params/samplerate")?.toInt() }.getOrNull()?.takeIf { it > 0 }
        val audioBitrateBps = runCatching { instance.getPropertyDouble("audio-bitrate")?.toLong() }.getOrNull()?.takeIf { it > 0L }
        val audioOutput = runCatching { instance.getPropertyString("current-ao") }.getOrNull()?.takeIf { it.isNotBlank() }

        val cacheMap = preloadedCacheMap
            ?: runCatching { instance.getPropertyNode("demuxer-cache-state")?.asMap() }.getOrNull()
        val cacheTotalBytes = runCatching { cacheMap?.get("total-bytes")?.asInt() }.getOrNull()
        val cacheFwBytes = runCatching { cacheMap?.get("fw-bytes")?.asInt() }.getOrNull()
        val cacheBackBytes = if (cacheTotalBytes != null && cacheFwBytes != null) {
            (cacheTotalBytes - cacheFwBytes).coerceAtLeast(0L)
        } else {
            null
        }
        val rawInputRateBytes = runCatching { cacheMap?.get("raw-input-rate")?.asInt() }.getOrNull()
        val demuxerInputRateBps = rawInputRateBytes?.takeIf { it >= 0L }?.times(8L)
        val cacheUnderrun = runCatching { cacheMap?.get("underrun")?.asBoolean() }.getOrNull() ?: false
        val cacheDurationSec = runCatching { cacheMap?.get("cache-duration")?.asDouble() }.getOrNull()
            ?: runCatching { instance.getPropertyDouble("demuxer-cache-duration") }.getOrNull()
        val mpvFileCacheBytes = runCatching { cacheMap?.get("file-cache-bytes")?.asInt() }.getOrNull()
            ?.takeIf { it > 0L }
        mpvReportsFileCacheBytes = mpvFileCacheBytes != null

        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val nowCpuMs = Process.getElapsedCpuTime()
        if (prevCpuTimeMs >= 0L && nowElapsed - prevCpuWallMs >= 400L) {
            val cpuDelta = (nowCpuMs - prevCpuTimeMs).coerceAtLeast(0L)
            val wallDelta = (nowElapsed - prevCpuWallMs).coerceAtLeast(1L)
            lastCpuPercent = ((cpuDelta.toDouble() / (wallDelta.toDouble() * cpuCores)) * 100.0).coerceIn(0.0, 100.0)
            prevCpuTimeMs = nowCpuMs
            prevCpuWallMs = nowElapsed
        } else if (prevCpuTimeMs < 0L) {
            prevCpuTimeMs = nowCpuMs
            prevCpuWallMs = nowElapsed
        }

        val diskCacheBytes = if (currentCacheOnDisk) {
            mpvFileCacheBytes ?: cachedDiskCacheBytes
        } else {
            null
        }

        val mem = cachedMemoryStats
        _diagnostics.value = PlaybackDiagnostics(
            transportLabel = proxyStats.transportMode,
            containerFormat = fileFormat,
            upstreamHost = proxyStats.upstreamHost,
            upstreamStatus = proxyStats.upstreamStatus,
            sessionUptimeMs = if (sessionStartElapsedMs > 0L) (nowElapsed - sessionStartElapsedMs).coerceAtLeast(0L) else 0L,
            playbackSpeed = speed,
            pausedForCache = pausedForCache,
            cacheBufferingPercent = cacheBufferingPercent,
            videoCodec = videoCodec,
            videoWidth = currentState.videoWidth,
            videoHeight = currentState.videoHeight,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            pixelFormat = pixelFormat,
            hwPixelFormat = hwPixelFormat,
            colorInfo = colorInfo,
            containerFps = containerFps,
            actualFps = actualFps,
            videoBitrateBps = videoBitrateBps,
            hwdecCurrent = hwdecCurrent,
            deinterlaceActive = currentState.deinterlaceActive,
            videoOutput = videoOutput,
            droppedFramesVo = droppedVo,
            droppedFramesDecoder = droppedDec,
            delayedFramesVo = delayedVo,
            avSyncMs = avSyncMs,
            audioCodec = audioCodec,
            audioChannels = audioChannels,
            audioSampleRateHz = audioSampleRate,
            audioBitrateBps = audioBitrateBps,
            audioOutput = audioOutput,
            networkRateBps = smoothedNetworkRateBps,
            demuxerInputRateBps = demuxerInputRateBps,
            totalBytesReceived = proxyStats.totalBytesRead,
            cacheUsedBytes = cacheTotalBytes,
            cacheForwardBytes = cacheFwBytes,
            cacheBackBytes = cacheBackBytes,
            cacheDurationSec = cacheDurationSec,
            cacheBudgetPerDirBytes = effectiveForwardCapBytes,
            cacheOnDisk = currentCacheOnDisk,
            diskCacheBytes = diskCacheBytes,
            cacheUnderrun = cacheUnderrun,
            processCpuPercent = lastCpuPercent,
            cpuCores = cpuCores,
            processPssMb = mem?.totalPssMb,
            nativePssMb = mem?.nativePssMb,
            jvmHeapUsedMb = mem?.jvmUsedMb,
            jvmHeapMaxMb = mem?.jvmMaxMb,
        )
    }

    private fun sampleMemoryStats(): MemorySnapshot {
        val memInfo = runCatching {
            Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        }.getOrNull()
        val rt = Runtime.getRuntime()
        val jvmUsedMb = (rt.totalMemory() - rt.freeMemory()).coerceAtLeast(0L) / (1024.0 * 1024.0)
        val jvmMaxMb = rt.maxMemory().coerceAtLeast(1L) / (1024.0 * 1024.0)
        return MemorySnapshot(
            totalPssMb = memInfo?.totalPss?.takeIf { it > 0 }?.div(1024.0),
            nativePssMb = memInfo?.nativePss?.takeIf { it > 0 }?.div(1024.0),
            jvmUsedMb = jvmUsedMb,
            jvmMaxMb = jvmMaxMb,
        )
    }

    /**
     * Measures disk space used by mpv's cache directory, including unlinked temporary files
     * held open via `/proc/self/fd` (mpv unlinks `cache-on-disk` files immediately on creation).
     */
    private fun measureDiskCacheBytes(root: File): Long {
        val seenInodes = HashSet<Pair<Long, Long>>()
        var total = 0L
        if (root.exists() && !Files.isSymbolicLink(root.toPath())) {
            val stack = ArrayDeque<File>()
            stack.addLast(root)
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                val children = current.listFiles() ?: continue
                for (child in children) {
                    if (Files.isSymbolicLink(child.toPath())) continue
                    if (child.isDirectory) {
                        stack.addLast(child)
                    } else if (child.isFile) {
                        val st = runCatching { Os.stat(child.absolutePath) }.getOrNull()
                        if (st != null) {
                            if (seenInodes.add(st.st_dev to st.st_ino)) {
                                total += st.st_size.coerceAtLeast(0L)
                            }
                        } else {
                            total += child.length().coerceAtLeast(0L)
                        }
                    }
                }
            }
        }
        val cachePrefix = root.absolutePath.trimEnd('/') + "/"
        val fdDir = File("/proc/self/fd")
        val fds = runCatching { fdDir.listFiles() }.getOrNull()
        if (fds != null) {
            for (fd in fds) {
                val target = runCatching { Os.readlink(fd.absolutePath) }.getOrNull() ?: continue
                if (!target.startsWith(cachePrefix)) continue
                val st = runCatching { Os.stat(fd.absolutePath) }.getOrNull() ?: continue
                if (OsConstants.S_ISREG(st.st_mode) && seenInodes.add(st.st_dev to st.st_ino)) {
                    total += st.st_size.coerceAtLeast(0L)
                }
            }
        }
        return total
    }

    private fun startAudioMeterPolling(gen: Long) {
        if (audioMeterJob?.isActive == true && audioMeterJobGen == gen) return
        audioMeterJob?.cancel()
        audioMeterJobGen = gen
        audioMeterJob = engineScope.launch {
            while (isActive && audioMeteringEnabled && !explicitlyStopped && playbackGeneration.get() == gen) {
                if (audioMeterPollInFlight.compareAndSet(false, true)) {
                    submitMpvTask {
                        try {
                            if (audioMeteringEnabled && !explicitlyStopped && playbackGeneration.get() == gen) {
                                mpv?.let { pollAudioMeterLocked(it) }
                            }
                        } finally {
                            audioMeterPollInFlight.set(false)
                        }
                    }
                }
                delay(AUDIO_METER_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopAudioMeterPolling() {
        audioMeterJob?.cancel()
        audioMeterJob = null
        audioMeterPollInFlight.set(false)
        radioVibeTracker.reset()
        _audioLevels.value = AudioLevels.ZERO
    }

    private fun syncAudioMeterFilterLocked(instance: MPV, shouldAttach: Boolean) {
        if (shouldAttach == audioMeterFilterAttached) return
        if (shouldAttach) {
            val rc = runCatching {
                instance.command("af", "add", ASTATS_FILTER_SPEC)
            }.getOrElse { err ->
                Log.w(TAG, "Failed to attach audio meter filter", err)
                -1
            }
            if (rc >= 0) {
                audioMeterFilterAttached = true
            }
        } else {
            runCatching {
                instance.command("af", "remove", ASTATS_FILTER_LABEL)
            }.onFailure { err ->
                Log.w(TAG, "Failed to remove audio meter filter", err)
            }
            audioMeterFilterAttached = false
        }
    }

    private fun pollAudioMeterLocked(instance: MPV) {
        if (!audioMeteringEnabled || explicitlyStopped || !audioMeterFilterAttached || !instance.isInitialized) {
            return
        }
        val metaMap = runCatching {
            instance.getPropertyNode(ASTATS_METADATA_PROP)?.asMap()
        }.getOrNull() ?: return
        val rmsRaw = readMetadataValue(metaMap[AudioLevelMapper.KEY_RMS_LEVEL])
        val peakRaw = readMetadataValue(metaMap[AudioLevelMapper.KEY_PEAK_LEVEL])
        if (rmsRaw == null && peakRaw == null) return
        _audioLevels.value = radioVibeTracker.update(rmsRaw, peakRaw)
    }

    private fun readMetadataValue(node: `is`.xyz.mpv.MPVNode?): String? {
        if (node == null) return null
        return runCatching { node.asString() }.getOrNull()
            ?: runCatching { node.asDouble()?.toString() }.getOrNull()
    }

    companion object {
        private const val TAG = "MuxGridPlayer"
        private const val AUDIO_METER_POLL_INTERVAL_MS = 100L
        private const val ASTATS_FILTER_LABEL = "@muxgrid_meter"
        private const val ASTATS_FILTER_SPEC =
            "$ASTATS_FILTER_LABEL:lavfi=[astats=metadata=1:reset=6:measure_overall=RMS_level+Peak_level:measure_perchannel=none]"
        private const val ASTATS_METADATA_PROP = "af-metadata/muxgrid_meter"
        private const val POSITION_EMIT_INTERVAL_MS = 250L
        private const val POSITION_JUMP_THRESHOLD_MS = 1_500L
        private const val CACHE_POLL_INTERVAL_MS = 500L
        private const val SEEK_POLL_INTERVAL_MS = 200L
        private const val MEMORY_STATS_SAMPLE_INTERVAL_MS = 1_500L
        private const val DISK_CACHE_SAMPLE_INTERVAL_MS = 2_000L
        private const val BYTES_PER_MIB = 1024L * 1024L
        private const val DEFAULT_RAM_CACHE_MIB = 64L
        private const val DEFAULT_RAM_CACHE_BYTES = DEFAULT_RAM_CACHE_MIB * BYTES_PER_MIB
        private const val DEFAULT_RAM_CACHE_ARG = "${DEFAULT_RAM_CACHE_MIB}MiB"
        private const val GROWING_LIGHT_RAM_CACHE_MIB = 24L
        private const val GROWING_LIGHT_RAM_CACHE_BYTES = GROWING_LIGHT_RAM_CACHE_MIB * BYTES_PER_MIB
        private const val GROWING_LIGHT_RAM_CACHE_ARG = "${GROWING_LIGHT_RAM_CACHE_MIB}MiB"
        private const val DISK_SAFETY_RESERVE_BYTES = 512L * BYTES_PER_MIB
        private const val SURFACE_DETACH_TIMEOUT_MS = 1_500L
        private const val SEEK_GUARD_SUBMIT_MS = 1_200L
        private const val SEEK_GUARD_SETTLE_MS = 450L
        private const val SEEK_GUARD_MAX_MS = 30_000L
        private const val MPV_VOLUME_DUCKED = 25.0
        private const val MPV_VOLUME_FULL = 100.0
        private const val TIMESHIFT_WARN_RATIO = 0.90
        private const val TIMESHIFT_FULL_RESUME_RATIO = 0.98
        private const val TIMESHIFT_NOTICE_SHORT_MS = 4_000L
        private const val TIMESHIFT_NOTICE_LONG_MS = 6_000L
        private const val LIVE_EDGE_NOTICE_MS = 2_000L
        private const val INDEX_SPLICE_MAX_DELTA_MS = 5_000L
        private const val INDEX_SPLICE_JUMP_THRESHOLD_MS = 15_000L
        private const val BACK_CACHE_SEEK_MARGIN_MS = 2_000L
        private const val COLD_RESUME_BINARY_SEARCH_MIN_SEC = 15L
        internal const val RADIO_CACHE_PAUSE_WAIT_ARG = "0.95"
        internal const val DEFAULT_CACHE_PAUSE_WAIT_ARG = "1.0"
        internal const val RADIO_DEMUXER_LAVF_O = "analyzeduration=500000,probesize=262144"
        internal const val VIDEO_DEMUXER_LAVF_O = "analyzeduration=2500000,probesize=2097152"
        internal const val RADIO_ANALYZE_DURATION_SEC = 0.5
        internal const val VIDEO_ANALYZE_DURATION_SEC = 2.5
        internal const val RADIO_PROBE_SIZE_BYTES = 262144L
        internal const val VIDEO_PROBE_SIZE_BYTES = 2097152L
    }

    private fun applyCachePauseSettingsLocked(instance: MPV, isRadio: Boolean) {
        instance.setPropertyString("cache-pause", "yes")
        instance.setPropertyString("cache-pause-initial", "yes")
        instance.setPropertyString("cache-pause-wait", resolveCachePauseWaitArg(isRadio))
    }

    private fun applyDemuxerProbeSettingsLocked(instance: MPV, isRadio: Boolean) {
        val lavfOpts = resolveDemuxerLavfOptions(isRadio)
        val analyzeSec = if (isRadio) RADIO_ANALYZE_DURATION_SEC else VIDEO_ANALYZE_DURATION_SEC
        val probeBytes = if (isRadio) RADIO_PROBE_SIZE_BYTES else VIDEO_PROBE_SIZE_BYTES
        val rc1 = instance.setPropertyString("demuxer-lavf-o", lavfOpts)
        val rc2 = instance.setPropertyDouble("demuxer-lavf-analyzeduration", analyzeSec)
        val rc3 = instance.setPropertyLong("demuxer-lavf-probesize", probeBytes)
        if (rc1 < 0 || rc2 < 0 || rc3 < 0) {
            Log.w(TAG, "demuxer probe props radio=$isRadio rc=($rc1,$rc2,$rc3)")
        }
    }

    private fun configureVideoModeForStartLocked(instance: MPV, audioOnly: Boolean) {
        if (audioOnly) {
            instance.setPropertyString("vid", "no")
            instance.setPropertyString("vo", "null")
            instance.setPropertyString("force-window", "no")
        } else {
            instance.setPropertyString("vid", "auto")
        }
        videoOutputDisabled = audioOnly
    }
}

internal fun resolveCachePauseWaitArg(isRadio: Boolean): String =
    if (isRadio) MpvPlaybackEngine.RADIO_CACHE_PAUSE_WAIT_ARG else MpvPlaybackEngine.DEFAULT_CACHE_PAUSE_WAIT_ARG

internal fun resolveDemuxerLavfOptions(isRadio: Boolean): String =
    if (isRadio) MpvPlaybackEngine.RADIO_DEMUXER_LAVF_O else MpvPlaybackEngine.VIDEO_DEMUXER_LAVF_O

internal fun shouldAttachAudioMeterFilter(
    isPlaybackActive: Boolean,
    isRadio: Boolean,
    isAudioStage: Boolean,
    radioAmbientGlowEnabled: Boolean,
    audioMeteringRequested: Boolean,
): Boolean = isPlaybackActive && (
    (isRadio && radioAmbientGlowEnabled) ||
        (audioMeteringRequested && isAudioStage)
    )

internal fun computeLiveVideoRestoreSeekSec(
    posSec: Double?,
    cacheEndSec: Double?,
): Double? {
    if (posSec == null || cacheEndSec == null || !posSec.isFinite() || !cacheEndSec.isFinite()) return null
    val gapSec = cacheEndSec - posSec
    if (gapSec <= 0.5) return null
    return (cacheEndSec - 0.15).coerceAtLeast(posSec)
}
