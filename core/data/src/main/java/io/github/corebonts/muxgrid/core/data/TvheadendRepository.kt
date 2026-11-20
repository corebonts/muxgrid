package io.github.corebonts.muxgrid.core.data

import android.content.Context
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.htsp.HtspClient
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ChannelTag
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.ConnectionTestResult
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.StreamTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TvheadendRepository(
    context: Context,
    formFactor: FormFactor,
    private val scope: CoroutineScope,
) {
    private val settings = SettingsRepository(context.applicationContext, formFactor)
    private val htspClient = HtspClient(scope = scope)
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
    val dvrEntries: StateFlow<Map<Long, DvrEntry>> = store.dvrEntries

    val serverProfile: StateFlow<ServerProfile?> = settings.serverProfile
        .stateIn(scope, SharingStarted.Eagerly, null)

    val favoriteChannelIds: StateFlow<Set<Long>> = settings.favoriteChannelIds
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val hwDecPreset: StateFlow<HwDecPreset> = settings.hwDecPreset
        .stateIn(scope, SharingStarted.Eagerly, HwDecPreset.defaultFor(formFactor))

    val imageLoader: ImageLoader = PiconFetcher.createImageLoader(
        context = context.applicationContext,
        htspClient = htspClient,
        profileProvider = { serverProfile.value ?: ServerProfile(host = "") },
    )

    init {
        scope.launch {
            htspClient.events.collect { event ->
                store.applyEvent(event)
            }
        }
        scope.launch {
            settings.serverProfile
                .distinctUntilChanged()
                .collect { profile ->
                    if (profile.isConfigured) {
                        htspClient.connect(profile)
                    } else {
                        htspClient.disconnect()
                    }
                }
        }
        // Refresh Now/Next progress & transitions every 15 s
        scope.launch {
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

    suspend fun toggleFavorite(channelId: Long) {
        settings.toggleFavorite(channelId)
    }

    suspend fun setHwDecPreset(preset: HwDecPreset) {
        settings.setHwDecPreset(preset)
    }

    suspend fun getLiveTicket(channelId: Long): StreamTicket =
        htspClient.getTicket(channelId)

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
