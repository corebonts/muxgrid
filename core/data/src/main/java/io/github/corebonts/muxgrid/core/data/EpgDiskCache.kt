package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.model.EpgEvent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Compact binary disk cache for EPG events so recently aired shows (up to 5 days in the past)
 * remain browsable across app restarts and HTSP resyncs even after Tvheadend prunes them.
 */
internal class EpgDiskCache(
    private val cacheFile: File,
) {
    private val ioLock = Any()

    fun load(serverKey: String, minStopEpochSec: Long): List<EpgEvent> {
        if (serverKey.isBlank()) return emptyList()
        return synchronized(ioLock) {
            if (!cacheFile.exists()) return@synchronized emptyList()
            runCatching {
                DataInputStream(BufferedInputStream(cacheFile.inputStream(), 32_768)).use { input ->
                    val magic = input.readInt()
                    val version = input.readInt()
                    if (magic != MAGIC || version != VERSION) return@synchronized emptyList()
                    val storedServerKey = readString(input)
                    if (storedServerKey != serverKey) return@synchronized emptyList()

                    val count = input.readInt().coerceIn(0, MAX_CACHED_EVENTS)
                    val events = ArrayList<EpgEvent>(count)
                    repeat(count) {
                        val id = input.readLong()
                        val channelId = input.readLong()
                        val startEpochSec = input.readLong()
                        val stopEpochSec = input.readLong()
                        val title = readString(input)
                        val subtitle = readNullableString(input)
                        val summary = readNullableString(input)
                        val description = readNullableString(input)
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
    }

    fun save(serverKey: String, events: Collection<EpgEvent>, minStopEpochSec: Long) {
        if (serverKey.isBlank()) return
        val filtered = events.filter { it.stopEpochSec >= minStopEpochSec }
        synchronized(ioLock) {
            val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            try {
                cacheFile.parentFile?.mkdirs()
                DataOutputStream(BufferedOutputStream(tempFile.outputStream(), 32_768)).use { out ->
                    out.writeInt(MAGIC)
                    out.writeInt(VERSION)
                    writeString(out, serverKey)
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
                        writeString(out, ev.title)
                        writeNullableString(out, ev.subtitle)
                        writeNullableString(out, ev.summary)
                        writeNullableString(out, ev.description)
                        writeNullableLong(out, ev.nextEventId)
                        writeNullableLong(out, ev.dvrId)
                    }
                    out.flush()
                }
                if (!tempFile.renameTo(cacheFile)) {
                    tempFile.copyTo(cacheFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (_: Throwable) {
                // Ignore disk cache write errors; temp cleanup runs in finally.
            } finally {
                if (tempFile.exists()) {
                    runCatching { tempFile.delete() }
                }
            }
        }
    }

    fun clear() {
        synchronized(ioLock) {
            runCatching {
                if (cacheFile.exists()) cacheFile.delete()
            }
        }
    }

    private fun readString(input: DataInputStream): String {
        val len = input.readInt()
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw IOException("Invalid cached string length: $len")
        }
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readNullableString(input: DataInputStream): String? {
        val len = input.readInt()
        if (len == -1) return null
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw IOException("Invalid cached string length: $len")
        }
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun writeNullableString(out: DataOutputStream, value: String?) {
        if (value == null) {
            out.writeInt(-1)
        } else {
            writeString(out, value)
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
        const val VERSION = 2
        const val MAX_CACHED_EVENTS = 100_000
        const val MAX_STRING_BYTES = 1_048_576
    }
}
