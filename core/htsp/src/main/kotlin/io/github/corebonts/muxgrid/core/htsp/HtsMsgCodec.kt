package io.github.corebonts.muxgrid.core.htsp

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class HtsCodecException(message: String, cause: Throwable? = null) : IOException(message, cause)

object HtsMsgCodec {
    const val TYPE_MAP: Int = 1
    const val TYPE_S64: Int = 2
    const val TYPE_STR: Int = 3
    const val TYPE_BIN: Int = 4
    const val TYPE_LIST: Int = 5
    const val TYPE_DBL: Int = 6 // Never emit on wire (Tvheadend calls abort())
    const val TYPE_BOOL: Int = 7
    const val TYPE_UUID: Int = 8

    private const val MAX_FRAME_BYTES = 64 * 1024 * 1024 // 64 MiB safety cap

    fun encodeFrame(map: HtsMap): ByteArray {
        val body = encodeMapBody(map)
        val frame = ByteArray(4 + body.size)
        writeU32BE(frame, 0, body.size)
        System.arraycopy(body, 0, frame, 4, body.size)
        return frame
    }

    fun encodeMapBody(map: HtsMap): ByteArray {
        val out = ByteArrayOutputStream(128)
        for ((name, value) in map) {
            writeField(out, name, value)
        }
        return out.toByteArray()
    }

    fun readFrame(input: InputStream): HtsMap {
        val header = readExact(input, 4)
        val totalLen = readU32BE(header, 0)
        if (totalLen < 0 || totalLen > MAX_FRAME_BYTES) {
            throw HtsCodecException("Invalid HTSP frame length: $totalLen")
        }
        val payload = readExact(input, totalLen)
        return decodeMapBody(payload, 0, payload.size)
    }

    fun decodeMapBody(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): HtsMap {
        val result = LinkedHashMap<String, HtsValue>()
        var pos = offset
        val end = offset + length
        if (end > bytes.size || offset < 0 || length < 0) {
            throw HtsCodecException("Buffer bounds out of range: offset=$offset len=$length size=${bytes.size}")
        }
        while (pos < end) {
            if (pos + 6 > end) {
                throw HtsCodecException("Truncated HTSP field header at offset $pos")
            }
            val type = bytes[pos].toInt() and 0xFF
            val nameLen = bytes[pos + 1].toInt() and 0xFF
            val dataLen = readU32BE(bytes, pos + 2)
            pos += 6

            if (dataLen < 0 || pos + nameLen + dataLen > end) {
                throw HtsCodecException("Invalid field bounds: type=$type nameLen=$nameLen dataLen=$dataLen remaining=${end - pos}")
            }
            val name = if (nameLen > 0) {
                String(bytes, pos, nameLen, StandardCharsets.UTF_8)
            } else {
                ""
            }
            pos += nameLen

            val value = decodeFieldValue(type, bytes, pos, dataLen)
            pos += dataLen
            if (value != null) {
                result[name] = value
            }
        }
        return HtsMap(result)
    }

    private fun decodeListBody(bytes: ByteArray, offset: Int, length: Int): List<HtsValue> {
        val items = ArrayList<HtsValue>()
        var pos = offset
        val end = offset + length
        while (pos < end) {
            if (pos + 6 > end) {
                throw HtsCodecException("Truncated HTSP list field header at offset $pos")
            }
            val type = bytes[pos].toInt() and 0xFF
            val nameLen = bytes[pos + 1].toInt() and 0xFF
            val dataLen = readU32BE(bytes, pos + 2)
            pos += 6

            if (dataLen < 0 || pos + nameLen + dataLen > end) {
                throw HtsCodecException("Invalid list field bounds: type=$type nameLen=$nameLen dataLen=$dataLen")
            }
            pos += nameLen // List entries are nameless, skip if non-zero
            val value = decodeFieldValue(type, bytes, pos, dataLen)
            pos += dataLen
            if (value != null) {
                items.add(value)
            }
        }
        return items
    }

    private fun decodeFieldValue(type: Int, bytes: ByteArray, offset: Int, dataLen: Int): HtsValue? =
        when (type) {
            TYPE_MAP -> HtsValue.MapVal(decodeMapBody(bytes, offset, dataLen))
            TYPE_S64 -> HtsValue.S64(decodeS64(bytes, offset, dataLen))
            TYPE_STR -> HtsValue.Str(String(bytes, offset, dataLen, StandardCharsets.UTF_8))
            TYPE_BIN -> HtsValue.Bin(bytes.copyOfRange(offset, offset + dataLen))
            TYPE_LIST -> HtsValue.ListVal(decodeListBody(bytes, offset, dataLen))
            TYPE_BOOL -> HtsValue.Bool(dataLen >= 1 && bytes[offset] != 0.toByte())
            TYPE_UUID -> {
                if (dataLen != 16) {
                    throw HtsCodecException("Expected 16-byte UUID, got $dataLen")
                }
                HtsValue.Uuid(bytes.copyOfRange(offset, offset + 16))
            }
            else -> null // Safely skip unrecognized future field types by dataLen
        }

    private fun writeField(out: ByteArrayOutputStream, name: String, value: HtsValue) {
        val nameBytes = if (name.isEmpty()) ByteArray(0) else name.toByteArray(StandardCharsets.UTF_8)
        require(nameBytes.size <= 255) { "HTSP field name exceeds 255 UTF-8 bytes: $name" }

        val type: Int
        val payload: ByteArray
        when (value) {
            is HtsValue.MapVal -> {
                type = TYPE_MAP
                payload = encodeMapBody(value.value)
            }
            is HtsValue.S64 -> {
                type = TYPE_S64
                payload = encodeS64(value.value)
            }
            is HtsValue.Str -> {
                type = TYPE_STR
                payload = value.value.toByteArray(StandardCharsets.UTF_8)
            }
            is HtsValue.Bin -> {
                type = TYPE_BIN
                payload = value.value
            }
            is HtsValue.ListVal -> {
                type = TYPE_LIST
                val listOut = ByteArrayOutputStream(64)
                for (item in value.items) {
                    writeField(listOut, "", item)
                }
                payload = listOut.toByteArray()
            }
            is HtsValue.Bool -> {
                type = TYPE_BOOL
                payload = if (value.value) byteArrayOf(1) else ByteArray(0)
            }
            is HtsValue.Uuid -> {
                type = TYPE_UUID
                payload = value.bytes
            }
        }

        out.write(type)
        out.write(nameBytes.size)
        writeU32BEStream(out, payload.size)
        if (nameBytes.isNotEmpty()) {
            out.write(nameBytes)
        }
        if (payload.isNotEmpty()) {
            out.write(payload)
        }
    }

    internal fun encodeS64(value: Long): ByteArray {
        if (value == 0L) return ByteArray(0)
        if (value < 0L) {
            // Negative S64 MUST be encoded as full 8-byte two's complement LE
            return ByteArray(8) { i -> ((value ushr (i * 8)) and 0xFFL).toByte() }
        }
        // Positive S64: LE with trailing 0x00 bytes stripped
        val full = ByteArray(8) { i -> ((value ushr (i * 8)) and 0xFFL).toByte() }
        var len = 8
        while (len > 0 && full[len - 1] == 0.toByte()) {
            len--
        }
        return full.copyOf(len)
    }

    internal fun decodeS64(bytes: ByteArray, offset: Int, dataLen: Int): Long {
        if (dataLen == 0) return 0L
        if (dataLen > 8) {
            throw HtsCodecException("S64 dataLen exceeds 8 bytes: $dataLen")
        }
        var result = 0L
        for (i in 0 until dataLen) {
            // No sign extension on short lengths (< 8); 8-byte values fill bit 63 naturally
            result = result or ((bytes[offset + i].toLong() and 0xFFL) shl (i * 8))
        }
        return result
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buf, read, count - read)
            if (n < 0) throw EOFException("Unexpected EOF while reading HTSP frame ($read/$count)")
            read += n
        }
        return buf
    }

    private fun readU32BE(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun writeU32BE(dest: ByteArray, offset: Int, value: Int) {
        dest[offset] = ((value ushr 24) and 0xFF).toByte()
        dest[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        dest[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        dest[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeU32BEStream(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
