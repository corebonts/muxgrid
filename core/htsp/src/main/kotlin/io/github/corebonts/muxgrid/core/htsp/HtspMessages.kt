package io.github.corebonts.muxgrid.core.htsp

import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ChannelTag
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import io.github.corebonts.muxgrid.core.model.StreamTicket
import java.io.IOException

open class HtspException(message: String, cause: Throwable? = null) : IOException(message, cause)

class HtspAuthException(message: String) : HtspException(message)

class HtspNoAccessException(message: String) : HtspException(message)

typealias TicketResult = StreamTicket

data class SysTimeResult(
    val timeEpochSec: Long,
    val timezone: String?,
    val gmtOffsetSec: Long,
)

data class HtspFileInfo(
    val handleId: Long,
    val size: Long,
    val mtime: Long,
)

data class ChannelUpdateDelta(
    val id: Long,
    val number: Int? = null,
    val numberMinor: Int? = null,
    val name: String? = null,
    val icon: String? = null,
    val hasIconField: Boolean = false,
    val tagIds: List<Long>? = null,
    val isRadio: Boolean? = null,
    val eventId: Long? = null,
    val hasEventIdField: Boolean = false,
    val nextEventId: Long? = null,
    val hasNextEventIdField: Boolean = false,
)

data class TagUpdateDelta(
    val id: Long,
    val name: String? = null,
    val icon: String? = null,
    val index: Int? = null,
    val memberChannelIds: Set<Long>? = null,
)

data class EventUpdateDelta(
    val id: Long,
    val channelId: Long? = null,
    val startEpochSec: Long? = null,
    val stopEpochSec: Long? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val nextEventId: Long? = null,
    val dvrId: Long? = null,
)

data class DvrEntryUpdateDelta(
    val id: Long,
    val channelId: Long? = null,
    val startEpochSec: Long? = null,
    val stopEpochSec: Long? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val state: DvrState? = null,
    val error: String? = null,
    val dataSize: Long? = null,
    val playPositionSec: Long? = null,
    val playCount: Int? = null,
)

sealed interface HtspPushEvent {
    data object SyncStarting : HtspPushEvent
    data object InitialSyncCompleted : HtspPushEvent

    data class TagAdd(val tag: ChannelTag) : HtspPushEvent
    data class TagUpdate(val delta: TagUpdateDelta) : HtspPushEvent
    data class TagDelete(val tagId: Long) : HtspPushEvent

    data class ChannelAdd(val channel: Channel) : HtspPushEvent
    data class ChannelUpdate(val delta: ChannelUpdateDelta) : HtspPushEvent
    data class ChannelDelete(val channelId: Long) : HtspPushEvent

    data class EventAdd(val event: EpgEvent) : HtspPushEvent
    data class EventUpdate(val delta: EventUpdateDelta) : HtspPushEvent
    data class EventDelete(val eventId: Long) : HtspPushEvent

    data class DvrEntryAdd(val entry: DvrEntry) : HtspPushEvent
    data class DvrEntryUpdate(val delta: DvrEntryUpdateDelta) : HtspPushEvent
    data class DvrEntryDelete(val entryId: Long) : HtspPushEvent
}

internal object HtspMessageParser {

    fun parsePushEvent(method: String, msg: HtsMap): HtspPushEvent? = when (method) {
        "initialSyncCompleted" -> HtspPushEvent.InitialSyncCompleted
        "tagAdd" -> {
            val id = msg.getLong("tagId") ?: return null
            val name = msg.getString("tagName") ?: return null
            HtspPushEvent.TagAdd(
                ChannelTag(
                    id = id,
                    name = name,
                    icon = msg.getString("tagIcon"),
                    index = msg.getInt("tagIndex") ?: 0,
                    memberChannelIds = msg.getLongList("tagMembers").toSet(),
                ),
            )
        }
        "tagUpdate" -> {
            val id = msg.getLong("tagId") ?: return null
            HtspPushEvent.TagUpdate(
                TagUpdateDelta(
                    id = id,
                    name = msg.getString("tagName"),
                    icon = msg.getString("tagIcon"),
                    index = msg.getInt("tagIndex"),
                    memberChannelIds = if (msg.containsKey("tagMembers")) msg.getLongList("tagMembers").toSet() else null,
                ),
            )
        }
        "tagDelete" -> msg.getLong("tagId")?.let { HtspPushEvent.TagDelete(it) }
        "channelAdd" -> {
            val id = msg.getLong("channelId") ?: return null
            val services = msg.getMapList("services")
            val isRadio = services.any { svc ->
                svc.getString("type").equals("Radio", ignoreCase = true) ||
                    svc.getLong("content") == 2L
            }
            HtspPushEvent.ChannelAdd(
                Channel(
                    id = id,
                    number = msg.getInt("channelNumber") ?: 0,
                    numberMinor = msg.getInt("channelNumberMinor") ?: 0,
                    name = msg.getString("channelName") ?: "",
                    icon = msg.getString("channelIcon"),
                    tagIds = msg.getLongList("tags"),
                    isRadio = isRadio,
                    eventId = msg.getLong("eventId"),
                    nextEventId = msg.getLong("nextEventId"),
                ),
            )
        }
        "channelUpdate" -> {
            val id = msg.getLong("channelId") ?: return null
            val hasServices = msg.containsKey("services")
            val isRadio = if (hasServices) {
                msg.getMapList("services").any { svc ->
                    svc.getString("type").equals("Radio", ignoreCase = true) ||
                        svc.getLong("content") == 2L
                }
            } else {
                null
            }
            HtspPushEvent.ChannelUpdate(
                ChannelUpdateDelta(
                    id = id,
                    number = msg.getInt("channelNumber"),
                    numberMinor = msg.getInt("channelNumberMinor"),
                    name = msg.getString("channelName"),
                    icon = msg.getString("channelIcon"),
                    hasIconField = msg.containsKey("channelIcon"),
                    tagIds = if (msg.containsKey("tags")) msg.getLongList("tags") else null,
                    isRadio = isRadio,
                    eventId = msg.getLong("eventId"),
                    hasEventIdField = msg.containsKey("eventId"),
                    nextEventId = msg.getLong("nextEventId"),
                    hasNextEventIdField = msg.containsKey("nextEventId"),
                ),
            )
        }
        "channelDelete" -> msg.getLong("channelId")?.let { HtspPushEvent.ChannelDelete(it) }
        "eventAdd" -> {
            val id = msg.getLong("eventId") ?: return null
            val channelId = msg.getLong("channelId") ?: return null
            val start = msg.getLong("start") ?: return null
            val stop = msg.getLong("stop") ?: return null
            HtspPushEvent.EventAdd(
                EpgEvent(
                    id = id,
                    channelId = channelId,
                    startEpochSec = start,
                    stopEpochSec = stop,
                    title = msg.getString("title") ?: "",
                    subtitle = msg.getString("subtitle"),
                    summary = msg.getString("summary"),
                    description = msg.getString("description"),
                    nextEventId = msg.getLong("nextEventId"),
                    dvrId = msg.getLong("dvrId"),
                ),
            )
        }
        "eventUpdate" -> {
            val id = msg.getLong("eventId") ?: return null
            HtspPushEvent.EventUpdate(
                EventUpdateDelta(
                    id = id,
                    channelId = msg.getLong("channelId"),
                    startEpochSec = msg.getLong("start"),
                    stopEpochSec = msg.getLong("stop"),
                    title = msg.getString("title"),
                    subtitle = msg.getString("subtitle"),
                    summary = msg.getString("summary"),
                    description = msg.getString("description"),
                    nextEventId = msg.getLong("nextEventId"),
                    dvrId = msg.getLong("dvrId"),
                ),
            )
        }
        "eventDelete" -> msg.getLong("eventId")?.let { HtspPushEvent.EventDelete(it) }
        "dvrEntryAdd" -> {
            val id = msg.getLong("id") ?: return null
            HtspPushEvent.DvrEntryAdd(
                DvrEntry(
                    id = id,
                    channelId = msg.getLong("channel") ?: 0L,
                    startEpochSec = msg.getLong("start") ?: 0L,
                    stopEpochSec = msg.getLong("stop") ?: 0L,
                    title = msg.getString("title") ?: "",
                    subtitle = msg.getString("subtitle"),
                    description = msg.getString("description"),
                    state = DvrState.fromHtsp(msg.getString("state")),
                    error = msg.getString("error"),
                    dataSize = msg.getLong("dataSize") ?: 0L,
                    playPositionSec = msg.getLong("playposition") ?: 0L,
                    playCount = msg.getInt("playcount") ?: 0,
                ),
            )
        }
        "dvrEntryUpdate" -> {
            val id = msg.getLong("id") ?: return null
            HtspPushEvent.DvrEntryUpdate(
                DvrEntryUpdateDelta(
                    id = id,
                    channelId = msg.getLong("channel"),
                    startEpochSec = msg.getLong("start"),
                    stopEpochSec = msg.getLong("stop"),
                    title = msg.getString("title"),
                    subtitle = msg.getString("subtitle"),
                    description = msg.getString("description"),
                    state = msg.getString("state")?.let { DvrState.fromHtsp(it) },
                    error = msg.getString("error"),
                    dataSize = msg.getLong("dataSize"),
                    playPositionSec = msg.getLong("playposition"),
                    playCount = msg.getInt("playcount"),
                ),
            )
        }
        "dvrEntryDelete" -> msg.getLong("id")?.let { HtspPushEvent.DvrEntryDelete(it) }
        else -> null
    }
}
