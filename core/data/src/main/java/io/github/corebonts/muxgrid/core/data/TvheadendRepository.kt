package io.github.corebonts.muxgrid.core.data

import android.content.Context
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.htsp.HtspClient
import io.github.corebonts.muxgrid.core.htsp.HtspNoAccessException
import io.github.corebonts.muxgrid.core.htsp.HtspPushEvent
import io.github.corebonts.muxgrid.core.htsp.HtspVfsClient
import io.github.corebonts.muxgrid.core.model.BackgroundPlaybackMode
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ChannelTag
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.ConnectionTestResult
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LocalDvrProgress
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.PortProbeResult
import io.github.corebonts.muxgrid.core.model.ServerDiagnosticsReport
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.StartScreen
import io.github.corebonts.muxgrid.core.model.StreamTicket
import io.github.corebonts.muxgrid.core.model.SubtitlePreference
import io.github.corebonts.muxgrid.core.model.ThemeMode
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single source of truth coordinating [HtspClient], [HtspVfsClient], [TvheadendStore],
 * [SettingsRepository], and Coil's picon [ImageLoader] for UI and player layers.
 */
class TvheadendRepository(
    context: Context,
    formFactor: FormFactor,
    private val scope: CoroutineScope,
) {
    private val settings = SettingsRepository(context.applicationContext, formFactor)
    private val htspClient = HtspClient(scope = scope, awaitConsumerSyncAck = true)
    private val vfsClient = HtspVfsClient(profileProvider = { serverProfile.value ?: ServerProfile(host = "") })
    private var backgroundDisconnectJob: Job? = null
    private val store = TvheadendStore(
        epochSecondsProvider = {
            val offsetMs = (connectionState.value as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
            (System.currentTimeMillis() + offsetMs) / 1000L
        },
    )

    private val dvrMergeLock = Any()
    private var latestLocalDvrProgress: Map<Long, LocalDvrProgress> = emptyMap()
    private val _dvrEntries = MutableStateFlow<Map<Long, DvrEntry>>(emptyMap())
    private val _serverDiagnostics = MutableStateFlow<ServerDiagnosticsReport?>(null)
    private val _isRunningDiagnostics = MutableStateFlow(false)
    private val activeDiagnosticsRefreshCount = AtomicInteger(0)

    val connectionState: StateFlow<ConnectionState> = htspClient.connectionState
    val serverDiagnostics: StateFlow<ServerDiagnosticsReport?> = _serverDiagnostics.asStateFlow()
    val isRunningDiagnostics: StateFlow<Boolean> = _isRunningDiagnostics.asStateFlow()
    val channels: StateFlow<List<Channel>> = store.channels
    val tags: StateFlow<List<ChannelTag>> = store.tags
    val nowNextByChannel: StateFlow<Map<Long, NowNextProgram>> = store.nowNextByChannel
    val epgByChannel: StateFlow<Map<Long, List<EpgEvent>>> = store.epgByChannel
    val dvrEntries: StateFlow<Map<Long, DvrEntry>> = _dvrEntries.asStateFlow()

    val serverProfile: StateFlow<ServerProfile?> = settings.serverProfile
        .stateIn(scope, SharingStarted.Eagerly, null)

    val favoriteChannelIds: StateFlow<Set<Long>> = settings.favoriteChannelIds
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val hwDecPreset: StateFlow<HwDecPreset> = settings.hwDecPreset
        .stateIn(scope, SharingStarted.Eagerly, HwDecPreset.defaultFor(formFactor))

    val timeshiftWindow: StateFlow<TimeshiftWindow> = settings.timeshiftWindow
        .stateIn(scope, SharingStarted.Eagerly, TimeshiftWindow.defaultFor(formFactor))

    val backgroundPlaybackMode: StateFlow<BackgroundPlaybackMode> = settings.backgroundPlaybackMode
        .stateIn(scope, SharingStarted.Eagerly, BackgroundPlaybackMode.defaultFor(formFactor))

    val startScreen: StateFlow<StartScreen?> = settings.startScreen
        .stateIn(scope, SharingStarted.Eagerly, null)

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.defaultFor(formFactor))

    val subtitlePreference: StateFlow<SubtitlePreference> = settings.subtitlePreference
        .stateIn(scope, SharingStarted.Eagerly, SubtitlePreference())

    val imageLoader: ImageLoader = PiconFetcher.createImageLoader(
        context = context.applicationContext,
        htspClient = htspClient,
        profileProvider = { serverProfile.value ?: ServerProfile(host = "") },
    )

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            vfsClient.close()
        }
        // Clean up legacy epg_cache.dat files from older versions
        scope.launch(Dispatchers.IO) {
            val filesDir = context.applicationContext.filesDir
            runCatching { File(filesDir, "epg_cache.dat").delete() }
            runCatching { File(filesDir, "epg_cache.dat.tmp").delete() }
        }
        scope.launch {
            settings.localDvrProgress.collect { localProgress ->
                synchronized(dvrMergeLock) {
                    latestLocalDvrProgress = localProgress
                    _dvrEntries.value = mergeDvrEntriesWithLocalProgress(
                        serverMap = store.dvrEntries.value,
                        localFallback = localProgress,
                    )
                }
            }
        }
        scope.launch(Dispatchers.Default) {
            val pendingEpgBatch = ArrayList<HtspPushEvent>()
            val pendingShadowBatch = ArrayList<HtspPushEvent>()
            var flushJob: Job? = null
            var syncGeneration = 0L
            val batchLock = Any()

            var inShadowSync = false
            htspClient.events.collect { event ->
                when (event) {
                    HtspPushEvent.SyncStarting -> {
                        inShadowSync = true
                        flushJob?.cancel()
                        flushJob = null
                        pendingShadowBatch.clear()
                        synchronized(batchLock) {
                            syncGeneration++
                            pendingEpgBatch.clear()
                            store.applyEvent(event)
                        }
                    }
                    HtspPushEvent.InitialSyncCompleted -> {
                        inShadowSync = false
                        flushJob?.cancel()
                        flushJob = null
                        try {
                            synchronized(batchLock) {
                                syncGeneration++
                                pendingEpgBatch.clear()
                                if (pendingShadowBatch.isNotEmpty()) {
                                    pendingShadowBatch.add(event)
                                    store.applyEvents(pendingShadowBatch)
                                    pendingShadowBatch.clear()
                                } else {
                                    store.applyEvent(event)
                                }
                            }
                            syncMergedDvrEntries()
                        } finally {
                            htspClient.acknowledgeInitialSync()
                        }
                    }
                    is HtspPushEvent.EventAdd,
                    is HtspPushEvent.EventUpdate,
                    is HtspPushEvent.EventDelete,
                    -> {
                        if (inShadowSync) {
                            pendingShadowBatch.add(event)
                        } else {
                            val scheduledGen = synchronized(batchLock) {
                                pendingEpgBatch.add(event)
                                syncGeneration
                            }
                            if (flushJob?.isActive != true) {
                                flushJob = launch(Dispatchers.Default) {
                                    delay(150L)
                                    synchronized(batchLock) {
                                        if (!isActive || scheduledGen != syncGeneration || pendingEpgBatch.isEmpty()) {
                                            return@synchronized
                                        }
                                        val copy = ArrayList(pendingEpgBatch)
                                        pendingEpgBatch.clear()
                                        store.applyEvents(copy)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (!inShadowSync) {
                            flushJob?.cancel()
                            flushJob = null
                            synchronized(batchLock) {
                                if (pendingEpgBatch.isNotEmpty()) {
                                    val copy = ArrayList(pendingEpgBatch)
                                    pendingEpgBatch.clear()
                                    store.applyEvents(copy)
                                }
                                store.applyEvent(event)
                            }
                            syncMergedDvrEntries()
                        } else {
                            pendingShadowBatch.add(event)
                        }
                    }
                }
            }
        }
        scope.launch {
            var previousServerKey: String? = null
            settings.serverProfile
                .distinctUntilChanged()
                .collectLatest { profile ->
                    vfsClient.disconnect()
                    val serverKey = profile.serverKey
                    if (serverKey != previousServerKey) {
                        val switchedServer = previousServerKey != null
                        if (switchedServer) {
                            htspClient.disconnect()
                            store.clear()
                        }
                        previousServerKey = serverKey
                    }
                    if (_serverDiagnostics.value?.profileFingerprint != profile.diagnosticsFingerprint) {
                        _serverDiagnostics.value = null
                    }
                    if (profile.isConfigured) {
                        htspClient.connect(profile)
                    } else {
                        htspClient.disconnect()
                    }
                }
        }
        scope.launch {
            htspClient.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    val profile = serverProfile.value
                    if (profile != null &&
                        profile.isConfigured &&
                        _serverDiagnostics.value?.profileFingerprint != profile.diagnosticsFingerprint
                    ) {
                        runCatching { refreshServerDiagnostics() }
                    }
                }
            }
        }
        // Refresh Now/Next progress & transitions every 15 s off the main thread
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(15_000L)
                store.refreshNowNext()
            }
        }
    }

    suspend fun awaitInitialProfile(): ServerProfile {
        return settings.serverProfile.first()
    }

    suspend fun testConnection(profile: ServerProfile): ConnectionTestResult =
        HtspClient.testConnection(profile)

    suspend fun probeServerPorts(
        host: String,
        htspPort: Int,
        httpPort: Int,
    ): PortProbeResult = ServerDiagnosticsRunner.probePorts(host, htspPort, httpPort)

    suspend fun runPreflightDiagnostics(profile: ServerProfile): ServerDiagnosticsReport =
        ServerDiagnosticsRunner.runPreflight(profile)

    suspend fun refreshServerDiagnostics(): ServerDiagnosticsReport? {
        val profile = serverProfile.value ?: return null
        if (!profile.isConfigured) return null
        if (activeDiagnosticsRefreshCount.incrementAndGet() > 0) {
            _isRunningDiagnostics.value = true
        }
        return try {
            val activeProbe = htspClient.probeActiveSession()
            val report = if (activeProbe != null) {
                ServerDiagnosticsRunner.evaluateFromHtspProbe(profile, activeProbe)
            } else {
                ServerDiagnosticsRunner.runPreflight(profile)
            }
            val current = serverProfile.value
            if (current != null && current.diagnosticsFingerprint == profile.diagnosticsFingerprint) {
                _serverDiagnostics.value = report
            }
            report
        } finally {
            if (activeDiagnosticsRefreshCount.decrementAndGet() <= 0) {
                _isRunningDiagnostics.value = false
            }
        }
    }

    suspend fun saveAndConnect(
        profile: ServerProfile,
        preflightReport: ServerDiagnosticsReport? = null,
    ) {
        val fingerprint = profile.diagnosticsFingerprint
        if (preflightReport != null && preflightReport.profileFingerprint == fingerprint) {
            _serverDiagnostics.value = preflightReport
        } else if (_serverDiagnostics.value?.profileFingerprint != fingerprint) {
            _serverDiagnostics.value = null
        }
        settings.saveServerProfile(profile)
    }

    fun reconnect() {
        val profile = serverProfile.value ?: return
        if (profile.isConfigured) {
            htspClient.connect(profile)
        }
    }

    fun setForeground(isForeground: Boolean, keepAliveInBackground: Boolean = false) {
        if (isForeground) {
            backgroundDisconnectJob?.cancel()
            backgroundDisconnectJob = null
            htspClient.suppressTransientErrors = false
            val state = htspClient.connectionState.value
            val profile = serverProfile.value
            if (profile != null && profile.isConfigured &&
                (state is ConnectionState.Disconnected || (state is ConnectionState.Error && !state.isAuthError))
            ) {
                htspClient.connect(profile)
            }
        } else {
            htspClient.suppressTransientErrors = true
            backgroundDisconnectJob?.cancel()
            if (!keepAliveInBackground) {
                backgroundDisconnectJob = scope.launch {
                    delay(3_000L)
                    vfsClient.disconnect()
                    htspClient.disconnect()
                }
            }
        }
    }

    suspend fun toggleFavorite(channelId: Long) {
        settings.toggleFavorite(channelId)
    }

    suspend fun setHwDecPreset(preset: HwDecPreset) {
        settings.setHwDecPreset(preset)
    }

    suspend fun setTimeshiftWindow(window: TimeshiftWindow) {
        settings.setTimeshiftWindow(window)
    }

    suspend fun setBackgroundPlaybackMode(mode: BackgroundPlaybackMode) {
        settings.setBackgroundPlaybackMode(mode)
    }

    suspend fun setStartScreen(screen: StartScreen) {
        settings.setStartScreen(screen)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        settings.setThemeMode(mode)
    }

    suspend fun setSubtitlePreference(preference: SubtitlePreference) {
        settings.setSubtitlePreference(preference)
    }

    suspend fun openGrowingDvrFile(dvrId: Long): GrowingDvrFile =
        vfsClient.openFile("dvrfile/$dvrId")

    fun isDvrRecording(dvrId: Long): Boolean =
        dvrEntries.value[dvrId]?.state == DvrState.RECORDING

    fun getDvrEntry(dvrId: Long): DvrEntry? =
        dvrEntries.value[dvrId] ?: store.getDvrEntry(dvrId)

    fun currentServerEpochSec(): Long {
        val offsetMs = (connectionState.value as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
        return (System.currentTimeMillis() + offsetMs) / 1000L
    }

    suspend fun getLiveTicket(channelId: Long): StreamTicket =
        htspClient.getTicket(channelId)

    suspend fun getDvrTicket(dvrId: Long): StreamTicket =
        htspClient.getDvrTicket(dvrId)

    suspend fun scheduleRecording(eventId: Long): Result<Long> = runCatchingSuspend {
        htspClient.addDvrEntry(eventId)
    }

    suspend fun scheduleManualRecording(
        channelId: Long,
        startEpochSec: Long,
        stopEpochSec: Long,
        title: String,
        description: String? = null,
    ): Result<Long> = runCatchingSuspend {
        htspClient.addManualDvrEntry(channelId, startEpochSec, stopEpochSec, title, description)
    }

    suspend fun stopRecording(dvrId: Long): Result<Unit> = runCatchingSuspend {
        htspClient.stopDvrEntry(dvrId)
    }

    suspend fun cancelRecording(dvrId: Long): Result<Unit> = runCatchingSuspend {
        htspClient.cancelDvrEntry(dvrId)
        settings.removeLocalDvrProgress(dvrId)
    }

    suspend fun deleteRecording(dvrId: Long): Result<Unit> = runCatchingSuspend {
        htspClient.deleteDvrEntry(dvrId)
        settings.removeLocalDvrProgress(dvrId)
    }

    suspend fun markRecordingWatched(dvrId: Long, watched: Boolean): Result<Unit> = runCatchingSuspend {
        val existing = dvrEntries.value[dvrId] ?: store.getDvrEntry(dvrId)
        val newCount = if (watched) maxOf(1, (existing?.playCount ?: 0) + 1) else 0
        try {
            htspClient.updateDvrEntry(id = dvrId, playPositionSec = 0L, playCount = newCount)
            settings.removeLocalDvrProgress(dvrId)
        } catch (e: HtspNoAccessException) {
            settings.saveLocalDvrProgress(dvrId, playPositionSec = 0L, playCount = newCount)
        }
    }

    suspend fun reportDvrPlaybackProgress(
        dvrId: Long,
        positionSec: Long,
        durationSec: Long,
        markCompleted: Boolean = false,
    ) {
        val entry = dvrEntries.value[dvrId] ?: store.getDvrEntry(dvrId)
        val isStillRecording = entry?.state == DvrState.RECORDING
        val effectiveDur = if (durationSec > 0L) durationSec else (entry?.durationSec ?: 0L)
        val watchedNow = !isStillRecording && (
            markCompleted ||
                (effectiveDur > 0L && positionSec.toFloat() / effectiveDur.toFloat() >= WATCHED_THRESHOLD_FRACTION)
            )

        val targetPos = if (watchedNow) 0L else positionSec.coerceAtLeast(0L)
        val existingCount = entry?.playCount ?: 0
        val targetCount = if (watchedNow) maxOf(1, existingCount) else null

        if (!watchedNow && targetPos <= DvrEntry.MIN_RESUME_POSITION_SEC) return

        try {
            htspClient.updateDvrEntry(
                id = dvrId,
                playPositionSec = targetPos,
                playCount = targetCount,
            )
            settings.removeLocalDvrProgress(dvrId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fallback for read-only users without ACCESS_HTSP_RECORDER or HTSP < v26
            settings.saveLocalDvrProgress(
                dvrId = dvrId,
                playPositionSec = targetPos,
                playCount = targetCount ?: existingCount,
            )
        }
    }

    private fun syncMergedDvrEntries() {
        synchronized(dvrMergeLock) {
            _dvrEntries.value = mergeDvrEntriesWithLocalProgress(
                serverMap = store.dvrEntries.value,
                localFallback = latestLocalDvrProgress,
            )
        }
    }

    private inline fun <T> runCatchingSuspend(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        private const val WATCHED_THRESHOLD_FRACTION = 0.92f
    }

    fun currentServerHttpBaseUrl(): String? = serverProfile.value?.httpBaseUrl

    fun findConflictingRecording(targetChannelId: Long): Pair<DvrEntry, String?>? {
        val entry = store.findConflictingRecording(targetChannelId) ?: return null
        val chName = store.getChannel(entry.channelId)?.name
        return entry to chName
    }
}

internal fun mergeDvrEntriesWithLocalProgress(
    serverMap: Map<Long, DvrEntry>,
    localFallback: Map<Long, LocalDvrProgress>,
): Map<Long, DvrEntry> {
    if (localFallback.isEmpty()) return serverMap
    return serverMap.mapValues { (id, entry) ->
        val local = localFallback[id]
        if (local != null) {
            entry.copy(
                playPositionSec = local.playPositionSec,
                playCount = local.playCount,
            )
        } else {
            entry
        }
    }
}
