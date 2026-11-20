package io.github.corebonts.muxgrid.core.player

import android.content.Context
import android.os.StatFs
import android.util.Log
import android.view.Surface
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.MediaTrack
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.StreamTicket
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import io.github.corebonts.muxgrid.core.model.TrackType
import io.github.corebonts.muxgrid.core.model.VideoAspectMode
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MpvPlaybackEngine(
    private val context: Context,
    serverBaseUrlProvider: () -> String?,
    ticketProvider: suspend (channelId: Long) -> StreamTicket,
    dvrTicketProvider: suspend (dvrId: Long) -> StreamTicket = {
        throw UnsupportedOperationException("DVR ticket provider is not configured")
    },
    growingFileOpener: suspend (dvrId: Long) -> GrowingDvrFile = {
        throw UnsupportedOperationException("Growing DVR file opener is not configured")
    },
    private val dvrEntryProvider: (dvrId: Long) -> DvrEntry? = { null },
    private val nowEpochSecProvider: () -> Long = { System.currentTimeMillis() / 1000L },
    initialTimeshiftWindow: TimeshiftWindow = TimeshiftWindow.GB_1,
    private val conflictFinder: (channelId: Long) -> Pair<DvrEntry, String?>?,
) : PlaybackEngine, MPV.EventObserver {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mpvExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "muxgrid-mpv").apply { isDaemon = true }
    }
    private val mpvCacheDir = File(context.cacheDir, "mpv-cache")

    private val proxy = LocalMediaProxy(
        serverBaseUrlProvider = serverBaseUrlProvider,
        ticketProvider = ticketProvider,
        dvrTicketProvider = dvrTicketProvider,
        growingFileOpener = growingFileOpener,
        isDvrRecordingProvider = { dvrId -> dvrEntryProvider(dvrId)?.state == DvrState.RECORDING },
        onLiveOpenFailed = { channelId, reason, isEmptyReply ->
            handleProxyFailure(channelId, reason, isEmptyReply)
        },
    )
    private val liveStrategy: LiveStreamStrategy = HttpPassStrategy(proxy)

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    @Volatile
    private var mpv: MPV? = null

    @Volatile
    private var hasSurface: Boolean = false

    @Volatile
    private var pendingUrl: String? = null

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
    private var effectiveForwardCapBytes: Long = DEFAULT_RAM_CACHE_BYTES

    @Volatile
    private var hadFileLoaded: Boolean = false

    @Volatile
    private var retriedLiveReconnect: Boolean = false

    @Volatile
    private var lastObservedMpvDurationMs: Long = 0L

    @Volatile
    private var explicitAudioOnly: Boolean = false

    @Volatile
    private var currentAspectMode: VideoAspectMode = VideoAspectMode.FIT

    private val platformLocks = PlaybackPlatformLocks(
        context = context,
        onPauseRequested = { setPaused(true) },
        onResumeRequested = { setPaused(false) },
        onDuckChanged = { ducked ->
            mpvExecutor.execute {
                val instance = mpv ?: return@execute
                if (instance.isInitialized) {
                    instance.setPropertyDouble("volume", if (ducked) 25.0 else 100.0)
                }
            }
        },
    )

    private var cacheMonitorJob: Job? = null
    private var noticeClearJob: Job? = null

    init {
        mpvExecutor.execute {
            initMpvLocked()
        }
        engineScope.launch {
            _state.collect { st ->
                when (st.status) {
                    PlaybackStatus.IDLE, PlaybackStatus.ERROR -> platformLocks.onPlaybackStopped()
                    PlaybackStatus.BUFFERING, PlaybackStatus.PLAYING, PlaybackStatus.PAUSED -> {
                        if (st.status == PlaybackStatus.PLAYING) {
                            PlaybackSessionHost.ensureServiceStarted(context)
                        }
                        val active = !st.isPaused &&
                            (st.status == PlaybackStatus.PLAYING || st.status == PlaybackStatus.BUFFERING)
                        platformLocks.onPlaybackActive(isRadio = st.isRadio, isPlayingOrBuffering = active)
                    }
                }
            }
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
            setOptionString("demuxer-lavf-o", "analyzeduration=500000,probesize=524288")
            setOptionString("cache", "yes")
            setOptionString("demuxer-seekable-cache", "yes")
            setOptionString("demuxer-donate-buffer", "yes")
            setOptionString("demuxer-cache-dir", mpvCacheDir.absolutePath)
            setOptionString("demuxer-max-bytes", "64MiB")
            setOptionString("demuxer-max-back-bytes", "64MiB")
            setOptionString("sub-visibility", "yes")
            setOptionString("force-window", "no")
            setOptionString("idle", "yes")
            setPropertyString("idle", "yes")
            setOptionString("pause", "no")
            setPropertyBoolean("pause", false)

            applyPresetOnInstance(this, currentPreset)

            observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
            observeProperty("core-idle", MPV.mpvFormat.MPV_FORMAT_FLAG)
            observeProperty("seekable", MPV.mpvFormat.MPV_FORMAT_FLAG)
            observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("hwdec-current", MPV.mpvFormat.MPV_FORMAT_STRING)
            observeProperty("deinterlace", MPV.mpvFormat.MPV_FORMAT_STRING)
            observeProperty("video-params/w", MPV.mpvFormat.MPV_FORMAT_INT64)
            observeProperty("video-params/h", MPV.mpvFormat.MPV_FORMAT_INT64)
            observeProperty("estimated-vf-fps", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("aid", MPV.mpvFormat.MPV_FORMAT_STRING)
            observeProperty("sid", MPV.mpvFormat.MPV_FORMAT_STRING)

            addObserver(this@MpvPlaybackEngine)
        }
        mpv = instance
    }

    override fun playChannel(channel: Channel, preset: HwDecPreset) {
        activeChannel = channel
        activeDvrEntry = null
        currentPreset = preset
        explicitlyStopped = false
        explicitAudioOnly = channel.isRadio
        hadFileLoaded = false
        retriedLiveReconnect = false
        lastObservedMpvDurationMs = 0L
        noticeClearJob?.cancel()

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
        Log.i(TAG, "playChannel id=${channel.id} (${channel.name}) timeshift=$requestedWindow url=$url")
        mpvExecutor.execute {
            val instance = ensureMpvLocked() ?: return@execute
            instance.command("stop")
            liveStrategy.stopActive()
            applyPresetOnInstance(instance, preset)
            applyAspectModeLocked(instance, currentAspectMode)
            configureLiveTimeshiftLocked(instance, requestedWindow)
            instance.setPropertyString("idle", "yes")
            instance.setPropertyString("start", "none")
            instance.setPropertyDouble("volume", 100.0)
            instance.setPropertyString("vid", if (explicitAudioOnly) "no" else "auto")
            instance.setPropertyBoolean("pause", false)
            if (hasSurface || channel.isRadio) {
                pendingUrl = null
                val rc = instance.command("loadfile", url)
                Log.i(TAG, "loadfile immediate channel=${channel.id} rc=$rc")
            } else {
                pendingUrl = url
                Log.i(TAG, "loadfile deferred until surfaceCreated channel=${channel.id}")
            }
        }
        startCacheMonitor()
    }

    override fun playDvrEntry(
        entry: DvrEntry,
        channel: Channel?,
        preset: HwDecPreset,
        startPositionSec: Long,
    ) {
        activeChannel = channel
        activeDvrEntry = entry
        currentPreset = preset
        explicitlyStopped = false
        explicitAudioOnly = channel?.isRadio == true
        hadFileLoaded = false
        retriedLiveReconnect = false
        lastObservedMpvDurationMs = 0L
        noticeClearJob?.cancel()

        platformLocks.requestFocusForStart(isRadio = explicitAudioOnly)

        val isGrowing = entry.state == DvrState.RECORDING
        val initialDurationMs = if (isGrowing) {
            ((nowEpochSecProvider() - entry.startEpochSec).coerceAtLeast(1L)) * 1000L
        } else {
            entry.durationSec * 1000L
        }
        val resumeSec = startPositionSec.coerceAtLeast(0L)
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

        val url = if (isGrowing) {
            proxy.growingDvrStreamUrl(entry.id)
        } else {
            proxy.dvrStreamUrl(entry.id)
        }
        Log.i(TAG, "playDvrEntry id=${entry.id} (${entry.title}) growing=$isGrowing start=${resumeSec}s url=$url")
        mpvExecutor.execute {
            val instance = ensureMpvLocked() ?: return@execute
            instance.command("stop")
            proxy.stopActiveStream()
            applyPresetOnInstance(instance, preset)
            applyAspectModeLocked(instance, currentAspectMode)
            configureDvrCacheLocked(instance, isGrowing)
            instance.setPropertyString("idle", "yes")
            instance.setPropertyString("start", if (resumeSec > 0L) "+$resumeSec" else "none")
            instance.setPropertyDouble("volume", 100.0)
            instance.setPropertyString("vid", if (explicitAudioOnly) "no" else "auto")
            instance.setPropertyBoolean("pause", false)
            if (hasSurface || (channel?.isRadio == true)) {
                pendingUrl = null
                val rc = instance.command("loadfile", url)
                Log.i(TAG, "loadfile DVR immediate id=${entry.id} rc=$rc")
            } else {
                pendingUrl = url
                Log.i(TAG, "loadfile DVR deferred until surfaceCreated id=${entry.id}")
            }
        }
        startCacheMonitor()
    }

    override fun stop() {
        explicitlyStopped = true
        explicitAudioOnly = false
        hadFileLoaded = false
        pendingUrl = null
        activeChannel = null
        activeDvrEntry = null
        cacheMonitorJob?.cancel()
        cacheMonitorJob = null
        noticeClearJob?.cancel()
        noticeClearJob = null
        platformLocks.onPlaybackStopped()
        _state.value = PlaybackState(status = PlaybackStatus.IDLE)

        mpvExecutor.execute {
            liveStrategy.stopActive()
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                instance.command("stop")
                instance.setPropertyString("start", "none")
                instance.setPropertyString("force-seekable", "no")
                instance.setPropertyString("vid", "auto")
            }
        }
    }

    override fun togglePause() {
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
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
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                instance.setPropertyBoolean("pause", paused)
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        val current = _state.value
        val clampedMs = clampSeekTargetMs(current, positionMs)
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                val secStr = String.format(Locale.US, "%.3f", clampedMs / 1000.0)
                instance.command("seek", secStr, "absolute")
            }
        }
    }

    override fun seekBy(deltaMs: Long) {
        val current = _state.value
        if (current.isGrowingRecording || (!current.isRecordingPlayback && current.isTimeshiftSeekable)) {
            seekTo(current.positionMs + deltaMs)
            return
        }
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                val secStr = String.format(Locale.US, "%.3f", deltaMs / 1000.0)
                instance.command("seek", secStr, "relative")
            }
        }
    }

    override fun seekToLiveEdge() {
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (!instance.isInitialized || activeChannel == null || activeDvrEntry != null) return@execute
            val (_, endSec) = readSeekableCacheRangeSecLocked(instance)
            val fallbackEnd = runCatching { instance.getPropertyDouble("demuxer-cache-time") }.getOrNull()
            val liveSec = endSec ?: fallbackEnd
            if (liveSec != null && liveSec > 1.5) {
                val target = (liveSec - 1.5).coerceAtLeast(0.0)
                val secStr = String.format(Locale.US, "%.3f", target)
                instance.command("seek", secStr, "absolute")
            }
            instance.setPropertyBoolean("pause", false)
            _state.update { it.copy(timeshiftNotice = null) }
        }
    }

    override fun setAudioTrack(trackId: Int) {
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                instance.setPropertyInt("aid", trackId)
                refreshTracksLocked(instance)
            }
        }
    }

    override fun setSubtitleTrack(trackId: Int?) {
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                if (trackId == null) {
                    instance.setPropertyString("sid", "no")
                } else {
                    instance.setPropertyInt("sid", trackId)
                }
                refreshTracksLocked(instance)
            }
        }
    }

    override fun applyPreset(preset: HwDecPreset) {
        currentPreset = preset
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                applyPresetOnInstance(instance, preset)
            }
        }
    }

    override fun setTimeshiftWindow(window: TimeshiftWindow) {
        currentTimeshiftWindow = window
    }

    override fun setAudioOnly(audioOnly: Boolean) {
        val effective = audioOnly || (activeChannel?.isRadio == true)
        explicitAudioOnly = effective
        _state.update { it.copy(isAudioOnly = effective) }
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (!instance.isInitialized) return@execute
            if (effective) {
                instance.setPropertyString("vid", "no")
                instance.setPropertyString("vo", "null")
                instance.setPropertyString("force-window", "no")
            } else if (hasSurface) {
                instance.setPropertyString("vid", "auto")
                instance.setOptionString("force-window", "yes")
                instance.setPropertyString("vo", "gpu")
            }
        }
    }

    override fun setVideoAspectMode(mode: VideoAspectMode) {
        currentAspectMode = mode
        _state.update { it.copy(aspectMode = mode) }
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                applyAspectModeLocked(instance, mode)
            }
        }
    }

    override fun attachSurface(surface: Surface, width: Int, height: Int) {
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (!instance.isInitialized) return@execute
            val audioOnly = explicitAudioOnly || (activeChannel?.isRadio == true)
            if (audioOnly) {
                hasSurface = false
                instance.setPropertyString("vid", "no")
                instance.setPropertyString("vo", "null")
                instance.setPropertyString("force-window", "no")
                return@execute
            }
            hasSurface = true
            instance.attachSurface(surface)
            instance.setPropertyString("vid", "auto")
            instance.setOptionString("force-window", "yes")
            instance.setPropertyString("vo", "gpu")
            applyAspectModeLocked(instance, currentAspectMode)
            if (width > 0 && height > 0) {
                instance.setPropertyString("android-surface-size", "${width}x${height}")
            }
            val urlToLoad = pendingUrl
            pendingUrl = null
            if (urlToLoad != null && !explicitlyStopped) {
                instance.setPropertyBoolean("pause", false)
                val rc = instance.command("loadfile", urlToLoad)
                Log.i(TAG, "loadfile on attachSurface rc=$rc url=$urlToLoad")
            }
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
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized && hasSurface) {
                instance.setPropertyString("android-surface-size", "${width}x${height}")
            }
        }
    }

    override fun detachSurface() {
        mpvExecutor.execute {
            if (!hasSurface) return@execute
            hasSurface = false
            val instance = mpv ?: return@execute
            if (!instance.isInitialized) return@execute
            // Disable video decode while backgrounded / audio-only so CPU & battery stay low
            instance.setPropertyString("vid", "no")
            instance.setPropertyString("vo", "null")
            instance.setPropertyString("force-window", "no")
            instance.detachSurface()
        }
    }

    override fun destroy() {
        explicitlyStopped = true
        cacheMonitorJob?.cancel()
        noticeClearJob?.cancel()
        platformLocks.destroy()
        liveStrategy.stopActive()
        proxy.close()
        engineScope.cancel()
        mpvExecutor.execute {
            val instance = mpv
            mpv = null
            if (instance != null) {
                runCatching { instance.removeObserver(this) }
                runCatching { instance.close() }
            }
        }
        mpvExecutor.shutdown()
    }

    private fun clampSeekTargetMs(state: PlaybackState, requestedMs: Long): Long {
        val nonNeg = requestedMs.coerceAtLeast(0L)
        return when {
            state.isGrowingRecording -> {
                val maxSafe = (state.durationMs - GROWING_WRITE_HEAD_SAFETY_MS).coerceAtLeast(0L)
                nonNeg.coerceAtMost(maxSafe)
            }
            !state.isRecordingPlayback && state.isTimeshiftSeekable -> {
                val minStart = state.bufferStartMs.coerceAtLeast(0L)
                val maxEnd = (state.bufferEndMs - LIVE_EDGE_SAFETY_MS).coerceAtLeast(minStart)
                nonNeg.coerceIn(minStart, maxEnd)
            }
            else -> nonNeg
        }
    }

    private fun configureLiveTimeshiftLocked(instance: MPV, requested: TimeshiftWindow) {
        val (bytesPerDir, useDisk) = resolveEffectiveTimeshiftBudget(requested)
        effectiveForwardCapBytes = bytesPerDir
        val mb = (bytesPerDir / (1024L * 1024L)).coerceAtLeast(64L)
        instance.setPropertyString("cache-on-disk", if (useDisk) "yes" else "no")
        instance.setPropertyString(
            "demuxer-seekable-cache",
            if (requested != TimeshiftWindow.OFF) "yes" else "auto",
        )
        instance.setPropertyString("demuxer-max-bytes", "${mb}MiB")
        instance.setPropertyString("demuxer-max-back-bytes", "${mb}MiB")
        instance.setPropertyString("force-seekable", "no")
        if (requested != TimeshiftWindow.OFF && !useDisk) {
            postTimeshiftNotice("Low storage: live timeshift using memory buffer", autoClearMs = 6_000L)
        }
    }

    private fun configureDvrCacheLocked(instance: MPV, isGrowing: Boolean) {
        effectiveForwardCapBytes = DEFAULT_RAM_CACHE_BYTES
        instance.setPropertyString("cache-on-disk", "no")
        instance.setPropertyString("demuxer-seekable-cache", "auto")
        instance.setPropertyString("demuxer-max-bytes", "64MiB")
        instance.setPropertyString("demuxer-max-back-bytes", "64MiB")
        instance.setPropertyString("force-seekable", if (isGrowing) "yes" else "no")
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

    private fun startCacheMonitor() {
        cacheMonitorJob?.cancel()
        cacheMonitorJob = engineScope.launch {
            while (isActive && !explicitlyStopped) {
                delay(CACHE_POLL_INTERVAL_MS)
                if (explicitlyStopped) break
                mpvExecutor.execute {
                    val instance = mpv ?: return@execute
                    if (!instance.isInitialized || explicitlyStopped) return@execute
                    val dvrEntry = activeDvrEntry
                    if (dvrEntry != null) {
                        updateGrowingDvrStateLocked(dvrEntry)
                    } else if (activeChannel != null) {
                        updateLiveTimeshiftStateLocked(instance)
                    }
                }
            }
        }
    }

    private fun updateGrowingDvrStateLocked(entry: DvrEntry) {
        val latest = dvrEntryProvider(entry.id) ?: entry
        val stillRecording = latest.state == DvrState.RECORDING
        val effectiveDurMs = if (stillRecording) {
            val elapsedMs = ((nowEpochSecProvider() - latest.startEpochSec).coerceAtLeast(1L)) * 1000L
            maxOf(lastObservedMpvDurationMs, elapsedMs)
        } else {
            maxOf(lastObservedMpvDurationMs, latest.durationSec * 1000L)
        }
        _state.update { current ->
            if (current.dvrEntryId != entry.id) {
                current
            } else {
                current.copy(
                    durationMs = effectiveDurMs,
                    isGrowingRecording = stillRecording,
                    isSeekable = stillRecording || current.isSeekable,
                )
            }
        }
    }

    private fun updateLiveTimeshiftStateLocked(instance: MPV) {
        val currentState = _state.value
        if (currentState.status == PlaybackStatus.IDLE || currentState.status == PlaybackStatus.ERROR) return

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

        val metrics = liveStrategy.computeTimeshiftMetrics(
            positionMs = (posSec * 1000.0).toLong(),
            cacheStartMs = (cacheStartSec * 1000.0).toLong(),
            cacheEndMs = (cacheEndSec * 1000.0).toLong(),
            timeshiftEnabled = currentTimeshiftWindow != TimeshiftWindow.OFF,
        )

        if (currentTimeshiftWindow != TimeshiftWindow.OFF &&
            currentState.isPaused &&
            effectiveForwardCapBytes > 0L
        ) {
            val fillRatio = fwBytes.toDouble() / effectiveForwardCapBytes.toDouble()
            if (fillRatio >= 0.98) {
                instance.setPropertyBoolean("pause", false)
                postTimeshiftNotice("Timeshift buffer full — playback resumed", autoClearMs = 6_000L)
            } else if (fillRatio >= 0.90) {
                postTimeshiftNotice("Timeshift pause buffer nearly full", autoClearMs = null)
            } else if (currentState.timeshiftNotice == "Timeshift pause buffer nearly full") {
                _state.update { it.copy(timeshiftNotice = null) }
            }
        } else if (!currentState.isPaused && currentState.timeshiftNotice == "Timeshift pause buffer nearly full") {
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

    private fun postTimeshiftNotice(message: String, autoClearMs: Long?) {
        noticeClearJob?.cancel()
        _state.update { it.copy(timeshiftNotice = message) }
        if (autoClearMs != null) {
            noticeClearJob = engineScope.launch {
                delay(autoClearMs)
                _state.update { current ->
                    if (current.timeshiftNotice == message) current.copy(timeshiftNotice = null) else current
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
        val error: PlaybackError = if (conflict != null && isEmptyReply) {
            PlaybackError.TunerBusy(
                recordingTitle = conflict.first.title,
                channelName = conflict.second,
            )
        } else if (conflict != null) {
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

    override fun eventProperty(property: String) {
        if (property == "aid" || property == "sid") {
            engineScope.launch {
                mpvExecutor.execute {
                    mpv?.takeIf { it.isInitialized }?.let { refreshTracksLocked(it) }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "video-params/w" -> _state.update { it.copy(videoWidth = value.toInt()) }
            "video-params/h" -> _state.update { it.copy(videoHeight = value.toInt()) }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> _state.update { current ->
                val nextStatus = when {
                    current.status == PlaybackStatus.ERROR || current.status == PlaybackStatus.IDLE -> current.status
                    value -> PlaybackStatus.PAUSED
                    else -> PlaybackStatus.PLAYING
                }
                current.copy(isPaused = value, status = nextStatus)
            }
            "core-idle" -> _state.update { current ->
                if (current.status == PlaybackStatus.ERROR || current.status == PlaybackStatus.IDLE) {
                    current
                } else if (!value && !current.isPaused) {
                    current.copy(status = PlaybackStatus.PLAYING)
                } else {
                    current
                }
            }
            "seekable" -> _state.update { it.copy(isSeekable = value) }
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "hwdec-current" -> _state.update { it.copy(hwdecCurrent = value) }
            "deinterlace" -> _state.update { it.copy(deinterlaceActive = value == "yes" || value == "auto") }
            "aid", "sid" -> {
                mpvExecutor.execute {
                    mpv?.takeIf { it.isInitialized }?.let { refreshTracksLocked(it) }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> _state.update { it.copy(positionMs = (value * 1000.0).toLong().coerceAtLeast(0L)) }
            "duration" -> {
                val durMs = (value * 1000.0).toLong().coerceAtLeast(0L)
                if (durMs > 0L) {
                    lastObservedMpvDurationMs = durMs
                    _state.update { current ->
                        if (current.isGrowingRecording) {
                            current.copy(durationMs = maxOf(current.durationMs, durMs))
                        } else {
                            current.copy(durationMs = durMs)
                        }
                    }
                }
            }
            "estimated-vf-fps" -> _state.update { it.copy(fps = value) }
        }
    }

    override fun eventProperty(property: String, value: `is`.xyz.mpv.MPVNode) = Unit

    override fun event(eventId: Int, data: `is`.xyz.mpv.MPVNode) {
        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_SHUTDOWN -> {
                Log.w(TAG, "MPV_EVENT_SHUTDOWN received; resetting instance")
                mpvExecutor.execute {
                    val dead = mpv
                    mpv = null
                    if (dead != null) {
                        runCatching { dead.removeObserver(this) }
                        runCatching { dead.close() }
                    }
                }
            }
            MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                _state.update { current ->
                    if (explicitlyStopped) current else current.copy(status = PlaybackStatus.BUFFERING, error = null)
                }
            }
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED,
            MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART,
            MPV.mpvEvent.MPV_EVENT_AUDIO_RECONFIG,
            MPV.mpvEvent.MPV_EVENT_VIDEO_RECONFIG,
            -> {
                if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
                    hadFileLoaded = true
                    retriedLiveReconnect = false
                }
                mpvExecutor.execute {
                    val instance = mpv ?: return@execute
                    if (instance.isInitialized) {
                        refreshTracksLocked(instance)
                        val realPaused = runCatching { instance.getPropertyBoolean("pause") }.getOrNull()
                        val realHwdec = runCatching { instance.getPropertyString("hwdec-current") }.getOrNull()
                        _state.update { current ->
                            if (explicitlyStopped || current.status == PlaybackStatus.ERROR) {
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
                if (explicitlyStopped) return
                val map = runCatching { data.asMap() }.getOrNull()
                val reasonNode = map?.get("reason")
                val reasonStr = runCatching { reasonNode?.asString() }.getOrNull()
                val reasonInt = runCatching { reasonNode?.asInt()?.toInt() }.getOrNull()
                val isErrorReason = reasonStr.equals("error", ignoreCase = true) || reasonInt == 4
                val isStoppedReason = reasonStr.equals("stop", ignoreCase = true) || reasonInt == 2

                val dvrEntry = activeDvrEntry
                if (dvrEntry != null) {
                    if (isErrorReason) {
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
                                    error = PlaybackError.PlaybackFailed(msg),
                                )
                            } else {
                                current
                            }
                        }
                    } else if (!isStoppedReason) {
                        activeDvrEntry = null
                        _state.update { current ->
                            if (current.dvrEntryId == dvrEntry.id) {
                                current.copy(
                                    status = PlaybackStatus.IDLE,
                                    isCompleted = true,
                                    isGrowingRecording = false,
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
                        postTimeshiftNotice("Live stream reconnected", autoClearMs = 4_000L)
                        val url = liveStrategy.resolveLiveUrl(channel.id)
                        mpvExecutor.execute {
                            val instance = mpv ?: return@execute
                            if (!instance.isInitialized || explicitlyStopped || activeChannel?.id != channel.id) return@execute
                            liveStrategy.stopActive()
                            configureLiveTimeshiftLocked(instance, currentTimeshiftWindow)
                            instance.setPropertyBoolean("pause", false)
                            instance.command("loadfile", url)
                        }
                    } else {
                        _state.update { current ->
                            if (current.channelId == channel.id && current.error == null) {
                                current.copy(
                                    status = PlaybackStatus.ERROR,
                                    error = PlaybackError.PlaybackFailed("Stream ended unexpectedly"),
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

    companion object {
        private const val TAG = "MuxGridPlayer"
        private const val CACHE_POLL_INTERVAL_MS = 500L
        private const val DEFAULT_RAM_CACHE_BYTES = 64L * 1024L * 1024L
        private const val DISK_SAFETY_RESERVE_BYTES = 512L * 1024L * 1024L
        private const val GROWING_WRITE_HEAD_SAFETY_MS = 2_000L
        private const val LIVE_EDGE_SAFETY_MS = 1_500L
    }
}
