package io.github.corebonts.muxgrid.core.player

import android.content.Context
import android.util.Log
import android.view.Surface
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.MediaTrack
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.StreamTicket
import io.github.corebonts.muxgrid.core.model.TrackType
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class MpvPlaybackEngine(
    private val context: Context,
    serverBaseUrlProvider: () -> String?,
    ticketProvider: suspend (channelId: Long) -> StreamTicket,
    private val conflictFinder: (channelId: Long) -> Pair<DvrEntry, String?>?,
) : PlaybackEngine, MPV.EventObserver {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mpvExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "muxgrid-mpv").apply { isDaemon = true }
    }

    private val proxy = LocalMediaProxy(
        serverBaseUrlProvider = serverBaseUrlProvider,
        ticketProvider = ticketProvider,
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
    private var explicitlyStopped: Boolean = true

    @Volatile
    private var currentPreset: HwDecPreset = HwDecPreset.MEDIACODEC_ZERO_COPY

    init {
        mpvExecutor.execute {
            initMpvLocked()
        }
    }

    private fun initMpvLocked() {
        val cfgDir = File(context.filesDir, "mpv").apply { mkdirs() }.absolutePath
        val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }.absolutePath
        val instance = MPV(context.applicationContext, cfgDir, cacheDir).apply {
            setOptionString("profile", "fast")
            setOptionString("vo", "gpu")
            setOptionString("gpu-context", "android")
            setOptionString("opengl-es", "yes")
            setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            setOptionString("demuxer-lavf-o", "analyzeduration=500000,probesize=524288")
            setOptionString("cache", "yes")
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
            observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
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
        currentPreset = preset
        explicitlyStopped = false

        _state.update {
            PlaybackState(
                status = PlaybackStatus.BUFFERING,
                channelId = channel.id,
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                isRadio = channel.isRadio,
                isPaused = false,
                error = null,
            )
        }

        val url = liveStrategy.resolveLiveUrl(channel.id)
        Log.i(TAG, "playChannel id=${channel.id} (${channel.name}) url=$url")
        mpvExecutor.execute {
            val instance = ensureMpvLocked() ?: return@execute
            instance.command("stop")
            liveStrategy.stopActive()
            applyPresetOnInstance(instance, preset)
            instance.setPropertyString("idle", "yes")
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
    }

    override fun stop() {
        explicitlyStopped = true
        pendingUrl = null
        activeChannel = null
        _state.value = PlaybackState(status = PlaybackStatus.IDLE)

        mpvExecutor.execute {
            liveStrategy.stopActive()
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                instance.command("stop")
            }
        }
    }

    override fun togglePause() {
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                val paused = instance.getPropertyBoolean("pause") ?: false
                instance.setPropertyBoolean("pause", !paused)
            }
        }
    }

    override fun setPaused(paused: Boolean) {
        mpvExecutor.execute {
            val instance = mpv ?: return@execute
            if (instance.isInitialized) {
                instance.setPropertyBoolean("pause", paused)
            }
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

    override fun attachSurface(surface: Surface, width: Int, height: Int) {
        mpvExecutor.execute {
            hasSurface = true
            val instance = mpv ?: return@execute
            if (!instance.isInitialized) return@execute
            instance.attachSurface(surface)
            instance.setOptionString("force-window", "yes")
            instance.setPropertyString("vo", "gpu")
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
            hasSurface = false
            val instance = mpv ?: return@execute
            if (!instance.isInitialized) return@execute
            instance.setPropertyString("vo", "null")
            instance.setPropertyString("force-window", "no")
            instance.detachSurface()
        }
    }

    override fun destroy() {
        explicitlyStopped = true
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
                mpvExecutor.execute {
                    val instance = mpv ?: return@execute
                    if (instance.isInitialized) {
                        refreshTracksLocked(instance)
                    }
                }
                _state.update { current ->
                    if (explicitlyStopped || current.status == PlaybackStatus.ERROR) {
                        current
                    } else {
                        current.copy(
                            status = if (current.isPaused) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING,
                        )
                    }
                }
            }
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                val channelId = activeChannel?.id ?: return
                if (explicitlyStopped) return
                val currentError = _state.value.error
                if (currentError == null) {
                    val conflict = conflictFinder(channelId)
                    val err = if (conflict != null) {
                        PlaybackError.TunerBusy(conflict.first.title, conflict.second)
                    } else {
                        PlaybackError.PlaybackFailed("Stream ended unexpectedly")
                    }
                    _state.update { current ->
                        if (current.channelId == channelId && current.error == null) {
                            current.copy(status = PlaybackStatus.ERROR, error = err)
                        } else {
                            current
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
    }
}
