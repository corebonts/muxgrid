package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.htsp.HtspPushEvent
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ChannelTag
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive in-memory HTSP state store.
 *
 * During initial sync or reconnect (`SyncStarting` -> `InitialSyncCompleted`), incoming
 * snapshots accumulate in shadow maps and swap atomically into the live StateFlows so
 * deletions during downtime are never missed and the UI never blanks out.
 */
class TvheadendStore(
    private val epochSecondsProvider: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val lock = Any()

    private val liveChannels = LinkedHashMap<Long, Channel>()
    private val liveTags = LinkedHashMap<Long, ChannelTag>()
    private val liveEvents = LinkedHashMap<Long, EpgEvent>()
    private val liveDvrEntries = LinkedHashMap<Long, DvrEntry>()

    private var shadowSyncActive = false
    private val shadowChannels = LinkedHashMap<Long, Channel>()
    private val shadowTags = LinkedHashMap<Long, ChannelTag>()
    private val shadowEvents = LinkedHashMap<Long, EpgEvent>()
    private val shadowDvrEntries = LinkedHashMap<Long, DvrEntry>()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _tags = MutableStateFlow<List<ChannelTag>>(emptyList())
    val tags: StateFlow<List<ChannelTag>> = _tags.asStateFlow()

    private val _nowNextByChannel = MutableStateFlow<Map<Long, NowNextProgram>>(emptyMap())
    val nowNextByChannel: StateFlow<Map<Long, NowNextProgram>> = _nowNextByChannel.asStateFlow()

    private val _epgByChannel = MutableStateFlow<Map<Long, List<EpgEvent>>>(emptyMap())
    val epgByChannel: StateFlow<Map<Long, List<EpgEvent>>> = _epgByChannel.asStateFlow()

    private val _dvrEntries = MutableStateFlow<Map<Long, DvrEntry>>(emptyMap())
    val dvrEntries: StateFlow<Map<Long, DvrEntry>> = _dvrEntries.asStateFlow()

    private var cachedEpgByChannel: Map<Long, List<EpgEvent>> = emptyMap()

    fun applyEvent(event: HtspPushEvent) {
        applyEvents(listOf(event))
    }

    fun applyEvents(events: List<HtspPushEvent>) {
        if (events.isEmpty()) return
        synchronized(lock) {
            var dirtyChannels = false
            var dirtyTags = false
            var dirtyEvents = false
            var dirtyDvr = false

            for (event in events) {
                when (event) {
                    HtspPushEvent.SyncStarting -> {
                        shadowSyncActive = true
                        shadowChannels.clear()
                        shadowTags.clear()
                        shadowEvents.clear()
                        shadowDvrEntries.clear()
                    }
                    HtspPushEvent.InitialSyncCompleted -> {
                        if (shadowSyncActive) {
                            liveChannels.clear()
                            liveChannels.putAll(shadowChannels)
                            liveTags.clear()
                            liveTags.putAll(shadowTags)
                            liveEvents.clear()
                            liveEvents.putAll(shadowEvents)
                            liveDvrEntries.clear()
                            liveDvrEntries.putAll(shadowDvrEntries)

                            shadowChannels.clear()
                            shadowTags.clear()
                            shadowEvents.clear()
                            shadowDvrEntries.clear()
                            shadowSyncActive = false
                        }
                        publishAllLocked()
                        dirtyChannels = false
                        dirtyTags = false
                        dirtyEvents = false
                        dirtyDvr = false
                    }
                    else -> {
                        val targetChannels = if (shadowSyncActive) shadowChannels else liveChannels
                        val targetTags = if (shadowSyncActive) shadowTags else liveTags
                        val targetEvents = if (shadowSyncActive) shadowEvents else liveEvents
                        val targetDvr = if (shadowSyncActive) shadowDvrEntries else liveDvrEntries

                        val changed = applyMutation(
                            event = event,
                            channelsMap = targetChannels,
                            tagsMap = targetTags,
                            eventsMap = targetEvents,
                            dvrMap = targetDvr,
                        )

                        if (!shadowSyncActive) {
                            when (changed) {
                                ChangeScope.CHANNELS -> dirtyChannels = true
                                ChangeScope.TAGS -> {
                                    dirtyTags = true
                                    dirtyChannels = true
                                }
                                ChangeScope.EVENTS -> dirtyEvents = true
                                ChangeScope.DVR -> dirtyDvr = true
                                ChangeScope.NONE -> Unit
                            }
                        }
                    }
                }
            }

            if (!shadowSyncActive) {
                if (dirtyTags) publishTagsLocked()
                if (dirtyChannels) publishChannelsLocked()
                if (dirtyEvents) {
                    publishNowNextLocked(rebuildEpg = true)
                } else if (dirtyChannels) {
                    publishNowNextLocked(rebuildEpg = false)
                }
                if (dirtyDvr) publishDvrLocked()
            }
        }
    }

    fun refreshNowNext() {
        synchronized(lock) {
            if (!shadowSyncActive) {
                publishNowNextLocked(rebuildEpg = false)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            shadowSyncActive = false
            shadowChannels.clear()
            shadowTags.clear()
            shadowEvents.clear()
            shadowDvrEntries.clear()
            liveChannels.clear()
            liveTags.clear()
            liveEvents.clear()
            liveDvrEntries.clear()
            cachedEpgByChannel = emptyMap()
            publishAllLocked()
        }
    }

    fun findConflictingRecording(targetChannelId: Long): DvrEntry? = synchronized(lock) {
        liveDvrEntries.values.firstOrNull { entry ->
            entry.state == DvrState.RECORDING && entry.channelId != targetChannelId
        }
    }

    fun getChannel(channelId: Long): Channel? = synchronized(lock) {
        liveChannels[channelId]
    }

    fun getDvrEntry(dvrId: Long): DvrEntry? = synchronized(lock) {
        liveDvrEntries[dvrId]
    }

    private enum class ChangeScope {
        NONE,
        CHANNELS,
        TAGS,
        EVENTS,
        DVR,
    }

    private fun applyMutation(
        event: HtspPushEvent,
        channelsMap: MutableMap<Long, Channel>,
        tagsMap: MutableMap<Long, ChannelTag>,
        eventsMap: MutableMap<Long, EpgEvent>,
        dvrMap: MutableMap<Long, DvrEntry>,
    ): ChangeScope = when (event) {
        is HtspPushEvent.TagAdd -> {
            tagsMap[event.tag.id] = event.tag
            ChangeScope.TAGS
        }
        is HtspPushEvent.TagUpdate -> {
            val d = event.delta
            val existing = tagsMap[d.id] ?: return ChangeScope.NONE
            tagsMap[d.id] = existing.copy(
                name = d.name ?: existing.name,
                icon = d.icon ?: existing.icon,
                index = d.index ?: existing.index,
                memberChannelIds = d.memberChannelIds ?: existing.memberChannelIds,
            )
            ChangeScope.TAGS
        }
        is HtspPushEvent.TagDelete -> {
            if (tagsMap.remove(event.tagId) != null) ChangeScope.TAGS else ChangeScope.NONE
        }
        is HtspPushEvent.ChannelAdd -> {
            channelsMap[event.channel.id] = event.channel
            ChangeScope.CHANNELS
        }
        is HtspPushEvent.ChannelUpdate -> {
            val d = event.delta
            val existing = channelsMap[d.id] ?: return ChangeScope.NONE
            channelsMap[d.id] = existing.copy(
                number = d.number ?: existing.number,
                numberMinor = d.numberMinor ?: existing.numberMinor,
                name = d.name ?: existing.name,
                icon = if (d.hasIconField) d.icon else existing.icon,
                tagIds = d.tagIds ?: existing.tagIds,
                isRadio = d.isRadio ?: existing.isRadio,
                eventId = if (d.hasEventIdField) d.eventId else existing.eventId,
                nextEventId = if (d.hasNextEventIdField) d.nextEventId else existing.nextEventId,
            )
            ChangeScope.CHANNELS
        }
        is HtspPushEvent.ChannelDelete -> {
            if (channelsMap.remove(event.channelId) != null) ChangeScope.CHANNELS else ChangeScope.NONE
        }
        is HtspPushEvent.EventAdd -> {
            eventsMap[event.event.id] = event.event
            ChangeScope.EVENTS
        }
        is HtspPushEvent.EventUpdate -> {
            val d = event.delta
            val existing = eventsMap[d.id] ?: return ChangeScope.NONE
            eventsMap[d.id] = existing.copy(
                channelId = d.channelId ?: existing.channelId,
                startEpochSec = d.startEpochSec ?: existing.startEpochSec,
                stopEpochSec = d.stopEpochSec ?: existing.stopEpochSec,
                title = d.title ?: existing.title,
                subtitle = d.subtitle ?: existing.subtitle,
                summary = d.summary ?: existing.summary,
                description = d.description ?: existing.description,
                nextEventId = d.nextEventId ?: existing.nextEventId,
                dvrId = if (d.hasDvrIdField) d.dvrId else existing.dvrId,
            )
            ChangeScope.EVENTS
        }
        is HtspPushEvent.EventDelete -> {
            if (eventsMap.remove(event.eventId) != null) ChangeScope.EVENTS else ChangeScope.NONE
        }
        is HtspPushEvent.DvrEntryAdd -> {
            dvrMap[event.entry.id] = event.entry
            ChangeScope.DVR
        }
        is HtspPushEvent.DvrEntryUpdate -> {
            val d = event.delta
            val existing = dvrMap[d.id] ?: return ChangeScope.NONE
            dvrMap[d.id] = existing.copy(
                channelId = d.channelId ?: existing.channelId,
                startEpochSec = d.startEpochSec ?: existing.startEpochSec,
                stopEpochSec = d.stopEpochSec ?: existing.stopEpochSec,
                title = d.title ?: existing.title,
                subtitle = d.subtitle ?: existing.subtitle,
                description = d.description ?: existing.description,
                state = d.state ?: existing.state,
                error = if (d.hasErrorField) d.error else existing.error,
                dataSize = d.dataSize ?: existing.dataSize,
                playPositionSec = d.playPositionSec ?: existing.playPositionSec,
                playCount = d.playCount ?: existing.playCount,
            )
            ChangeScope.DVR
        }
        is HtspPushEvent.DvrEntryDelete -> {
            if (dvrMap.remove(event.entryId) != null) ChangeScope.DVR else ChangeScope.NONE
        }
        HtspPushEvent.SyncStarting, HtspPushEvent.InitialSyncCompleted -> ChangeScope.NONE
    }

    private fun publishAllLocked() {
        publishTagsLocked()
        publishChannelsLocked()
        publishNowNextLocked(rebuildEpg = true)
        publishDvrLocked()
    }

    private fun publishTagsLocked() {
        _tags.value = liveTags.values.sortedWith(compareBy<ChannelTag> { it.index }.thenBy { it.name })
    }

    private fun publishChannelsLocked() {
        val radioTagIds = liveTags.values
            .filter { it.name.contains("radio", ignoreCase = true) }
            .mapTo(HashSet()) { it.id }

        _channels.value = liveChannels.values
            .map { ch ->
                val isRadio = ch.isRadio || ch.tagIds.any { it in radioTagIds }
                if (isRadio != ch.isRadio) ch.copy(isRadio = isRadio) else ch
            }
            .sortedWith(
                compareBy<Channel> { if (it.number > 0) it.number else Int.MAX_VALUE }
                    .thenBy { it.numberMinor }
                    .thenBy { it.name },
            )
    }

    private fun publishNowNextLocked(rebuildEpg: Boolean = true) {
        val nowSec = epochSecondsProvider()
        val eventsByChannel: Map<Long, List<EpgEvent>> = if (rebuildEpg) {
            val grouped = HashMap<Long, MutableList<EpgEvent>>()
            for (ev in liveEvents.values) {
                grouped.getOrPut(ev.channelId) { ArrayList() }.add(ev)
            }
            for (list in grouped.values) {
                list.sortBy { it.startEpochSec }
            }
            val frozen = grouped.mapValues { (_, list) -> list.toList() }
            cachedEpgByChannel = frozen
            _epgByChannel.value = frozen
            frozen
        } else {
            cachedEpgByChannel
        }

        val result = HashMap<Long, NowNextProgram>(liveChannels.size)
        for (ch in liveChannels.values) {
            val channelEvents = eventsByChannel[ch.id].orEmpty()
            val linkedNow = ch.eventId?.let { liveEvents[it] }
                ?.takeIf { nowSec < it.stopEpochSec }
            val nowEvent = linkedNow ?: channelEvents.firstOrNull { nowSec in it.startEpochSec until it.stopEpochSec }

            val linkedNext = (ch.nextEventId ?: nowEvent?.nextEventId)?.let { liveEvents[it] }
                ?.takeIf { nowEvent == null || it.id != nowEvent.id }
            val nextEvent = linkedNext ?: channelEvents.firstOrNull { ev ->
                val threshold = nowEvent?.stopEpochSec ?: nowSec
                ev.startEpochSec >= threshold && ev.id != nowEvent?.id
            }

            if (nowEvent != null || nextEvent != null) {
                result[ch.id] = NowNextProgram(now = nowEvent, next = nextEvent)
            }
        }
        _nowNextByChannel.value = result
    }

    private fun publishDvrLocked() {
        _dvrEntries.value = HashMap(liveDvrEntries)
    }
}
