package io.github.corebonts.muxgrid.core.data

import android.content.Context
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.htsp.HtspClient
import io.github.corebonts.muxgrid.core.htsp.HtspNoAccessException
import io.github.corebonts.muxgrid.core.htsp.HtspPushEvent
import io.github.corebonts.muxgrid.core.htsp.HtspVfsClient
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
import io.github.corebonts.muxgrid.core.model.NowNextProgram
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

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
    private val htspClient = HtspClient(scope = scope)
    private val vfsClient = HtspVfsClient(profileProvider = { serverProfile.value ?: ServerProfile(host = "") })
    private val epgDiskCache = EpgDiskCache(File(context.applicationContext.filesDir, "epg_cache.dat"))
    private var backgroundDisconnectJob: Job? = null
    private val store = TvheadendStore(
        epochSecondsProvider = {
            val offsetMs = (connectionState.value as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L
            (System.currentTimeMillis() + offsetMs) / 1000L
        },
    )

    val connectionState: StateFlow<ConnectionState> = htspClient.connectionState
    val channels: StateFlow<List<Channel>> = store.channels
    val tags: StateFlow<List<ChannelTag>> = store.tags
    val nowNextByChannel: StateFlow<Map<Long, NowNextProgram>> = store.nowNextByChannel
    val epgByChannel: StateFlow<Map<Long, List<EpgEvent>>> = store.epgByChannel
    val dvrEntries: StateFlow<Map<Long, DvrEntry>> = combine(
        store.dvrEntries,
        settings.localDvrProgress,
    ) { serverMap, localFallback ->
        if (localFallback.isEmpty()) {
            serverMap
        } else {
            serverMap.mapValues { (id, entry) ->
                val local = localFallback[id]
                if (local != null && entry.playPositionSec == 0L && entry.playCount == 0) {
                    entry.copy(
                        playPositionSec = local.playPositionSec,
                        playCount = local.playCount,
                    )
                } else {
                    entry
                }
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val serverProfile: StateFlow<ServerProfile?> = settings.serverProfile
        .stateIn(scope, SharingStarted.Eagerly, null)

    val favoriteChannelIds: StateFlow<Set<Long>> = settings.favoriteChannelIds
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val hwDecPreset: StateFlow<HwDecPreset> = settings.hwDecPreset
        .stateIn(scope, SharingStarted.Eagerly, HwDecPreset.defaultFor(formFactor))

    val timeshiftWindow: StateFlow<TimeshiftWindow> = settings.timeshiftWindow
        .stateIn(scope, SharingStarted.Eagerly, TimeshiftWindow.defaultFor(formFactor))

    val backgroundPlayEnabled: StateFlow<Boolean> = settings.backgroundPlayEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    val pipEnabled: StateFlow<Boolean> = settings.pipEnabled
        .stateIn(scope, SharingStarted.Eagerly, formFactor == FormFactor.PHONE)

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
        scope.launch(Dispatchers.Default) {
            val pendingEpgBatch = ArrayList<HtspPushEvent>()
            var flushJob: Job? = null
            val batchLock = Any()

            fun flushEpgBatch() {
                val batch = synchronized(batchLock) {
                    if (pendingEpgBatch.isEmpty()) return
                    val copy = ArrayList(pendingEpgBatch)
                    pendingEpgBatch.clear()
                    copy
                }
                store.applyEvents(batch)
            }

            var inShadowSync = false
            htspClient.events.collect { event ->
                when (event) {
                    HtspPushEvent.SyncStarting -> {
                        inShadowSync = true
                        flushJob?.cancel()
                        flushJob = null
                        synchronized(batchLock) { pendingEpgBatch.clear() }
                        store.applyEvent(event)
                    }
                    HtspPushEvent.InitialSyncCompleted -> {
                        inShadowSync = false
                        flushJob?.cancel()
                        flushJob = null
                        synchronized(batchLock) { pendingEpgBatch.clear() }
                        store.applyEvent(event)
                    }
                    is HtspPushEvent.EventAdd,
                    is HtspPushEvent.EventUpdate,
                    is HtspPushEvent.EventDelete,
                    -> {
                        if (inShadowSync) {
                            store.applyEvent(event)
                        } else {
                            synchronized(batchLock) {
                                pendingEpgBatch.add(event)
                            }
                            if (flushJob?.isActive != true) {
                                flushJob = launch(Dispatchers.Default) {
                                    delay(150L)
                                    flushEpgBatch()
                                }
                            }
                        }
                    }
                    else -> {
                        if (!inShadowSync) {
                            flushJob?.cancel()
                            flushJob = null
                            flushEpgBatch()
                        }
                        store.applyEvent(event)
                    }
                }
            }
        }
        scope.launch {
            var previousServerKey: String? = null
            settings.serverProfile
                .distinctUntilChanged()
                .collect { profile ->
                    vfsClient.disconnect()
                    val serverKey = if (profile.isConfigured) "${profile.host}:${profile.htspPort}" else ""
                    if (serverKey != previousServerKey) {
                        val switchedServer = previousServerKey != null
                        previousServerKey = serverKey
                        scope.launch(Dispatchers.IO) {
                            val minStopSec = currentServerEpochSec() - TvheadendStore.EPG_PAST_RETENTION_SEC
                            val cached = if (serverKey.isNotEmpty()) {
                                epgDiskCache.load(serverKey, minStopSec)
                            } else {
                                emptyList()
                            }
                            store.seedCachedEvents(cached, clearExisting = switchedServer)
                        }
                    }
                    if (profile.isConfigured) {
                        htspClient.connect(profile)
                    } else {
                        htspClient.disconnect()
                    }
                }
        }
        // Persist recent EPG events (debounced) off the main thread so past shows survive restarts
        scope.launch(Dispatchers.IO) {
            store.epgByChannel.collectLatest { epgMap ->
                if (epgMap.isEmpty()) return@collectLatest
                delay(3_000L)
                val profile = serverProfile.value ?: return@collectLatest
                if (!profile.isConfigured) return@collectLatest
                val serverKey = "${profile.host}:${profile.htspPort}"
                val minStopSec = currentServerEpochSec() - TvheadendStore.EPG_PAST_RETENTION_SEC
                val allEvents = epgMap.values.flatten()
                epgDiskCache.save(serverKey, allEvents, minStopSec)
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

    suspend fun saveAndConnect(profile: ServerProfile) {
        settings.saveServerProfile(profile)
    }

    fun reconnect() {
        val profile = serverProfile.value ?: return
        if (profile.isConfigured) {
            htspClient.connect(profile)
        }
    }

    /**
     * Forces a fresh metadata sync (channels, EPG and DVR entries) from Tvheadend.
     * Used by pull-to-refresh so changes made by other clients show up immediately
     * and a stale connection can recover without leaving the screen.
     */
    suspend fun refresh(): Result<Unit> = runCatchingSuspend {
        store.refreshNowNext()
        val profile = serverProfile.value ?: return@runCatchingSuspend
        if (!profile.isConfigured) return@runCatchingSuspend

        if (!htspClient.resync()) {
            val message = (connectionState.value as? ConnectionState.Error)?.message
                ?: "Timed out refreshing from Tvheadend"
            throw IllegalStateException(message)
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

    suspend fun setBackgroundPlayEnabled(enabled: Boolean) {
        settings.setBackgroundPlayEnabled(enabled)
    }

    suspend fun setPipEnabled(enabled: Boolean) {
        settings.setPipEnabled(enabled)
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
        val existing = store.getDvrEntry(dvrId)
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

        if (!watchedNow && targetPos < 5L) return

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

    fun currentServerHttpBaseUrl(): String? {
        val profile = serverProfile.value ?: return null
        if (!profile.isConfigured) return null
        return "http://${profile.host}:${profile.httpPort}"
    }

    fun findConflictingRecording(targetChannelId: Long): Pair<DvrEntry, String?>? {
        val entry = store.findConflictingRecording(targetChannelId) ?: return null
        val chName = store.getChannel(entry.channelId)?.name
        return entry to chName
    }
}
