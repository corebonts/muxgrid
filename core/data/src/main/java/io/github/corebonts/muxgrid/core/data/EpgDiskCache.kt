package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.model.EpgEvent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Compact binary disk cache for EPG events so recently aired shows (up to 5 days in the past)
 * remain browsable across app restarts and HTSP resyncs even after Tvheadend prunes them.
 */
internal class EpgDiskCache(
    private val cacheFile: File,
) {
    fun load(serverKey: String, minStopEpochSec: Long): List<EpgEvent> {
        if (serverKey.isBlank() || !cacheFile.exists()) return emptyList()
        return runCatching {
            DataInputStream(BufferedInputStream(cacheFile.inputStream(), 32_768)).use { input ->
                val magic = input.readInt()
                val version = input.readInt()
                if (magic != MAGIC || version != VERSION) return emptyList()
                val storedServerKey = input.readUTF()
                if (storedServerKey != serverKey) return emptyList()

                val count = input.readInt().coerceIn(0, MAX_CACHED_EVENTS)
                val events = ArrayList<EpgEvent>(count)
                repeat(count) {
                    val id = input.readLong()
                    val channelId = input.readLong()
                    val startEpochSec = input.readLong()
                    val stopEpochSec = input.readLong()
                    val title = input.readUTF()
                    val subtitle = readNullableUtf(input)
                    val summary = readNullableUtf(input)
                    val description = readNullableUtf(input)
                    val nextEventId = readNullableLong(input)
                    val dvrId = readNullableLong(input)
                    if (stopEpochSec >= minStopEpochSec) {
                        events.add(
                            EpgEvent(
                                id = id,
                                channelId = channelId,
                                startEpochSec = startEpochSec,
                                stopEpochSec = stopEpochSec,
                                title = title,
                                subtitle = subtitle,
                                summary = summary,
                                description = description,
                                nextEventId = nextEventId,
                                dvrId = dvrId,
                            ),
                        )
                    }
                }
                events
            }
        }.getOrDefault(emptyList())
    }

    fun save(serverKey: String, events: Collection<EpgEvent>, minStopEpochSec: Long) {
        if (serverKey.isBlank()) return
        val filtered = events.filter { it.stopEpochSec >= minStopEpochSec }
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            DataOutputStream(BufferedOutputStream(tempFile.outputStream(), 32_768)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeUTF(serverKey)
                val limited = if (filtered.size > MAX_CACHED_EVENTS) {
                    filtered.sortedByDescending { it.startEpochSec }.take(MAX_CACHED_EVENTS)
                } else {
                    filtered
                }
                out.writeInt(limited.size)
                for (ev in limited) {
                    out.writeLong(ev.id)
                    out.writeLong(ev.channelId)
                    out.writeLong(ev.startEpochSec)
                    out.writeLong(ev.stopEpochSec)
                    out.writeUTF(ev.title)
                    writeNullableUtf(out, ev.subtitle)
                    writeNullableUtf(out, ev.summary)
                    writeNullableUtf(out, ev.description)
                    writeNullableLong(out, ev.nextEventId)
                    writeNullableLong(out, ev.dvrId)
                }
                out.flush()
            }
            if (!tempFile.renameTo(cacheFile)) {
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()
            }
        }
    }

    fun clear() {
        runCatching {
            if (cacheFile.exists()) cacheFile.delete()
        }
    }

    private fun readNullableUtf(input: DataInputStream): String? =
        if (input.readBoolean()) input.readUTF() else null

    private fun writeNullableUtf(out: DataOutputStream, value: String?) {
        if (value != null) {
            out.writeBoolean(true)
            out.writeUTF(value)
        } else {
            out.writeBoolean(false)
        }
    }

    private fun readNullableLong(input: DataInputStream): Long? =
        if (input.readBoolean()) input.readLong() else null

    private fun writeNullableLong(out: DataOutputStream, value: Long?) {
        if (value != null) {
            out.writeBoolean(true)
            out.writeLong(value)
        } else {
            out.writeBoolean(false)
        }
    }

    private companion object {
        const val MAGIC = 0x4D584550 // "MXEP"
        const val VERSION = 1
        const val MAX_CACHED_EVENTS = 100_000
    }
}
