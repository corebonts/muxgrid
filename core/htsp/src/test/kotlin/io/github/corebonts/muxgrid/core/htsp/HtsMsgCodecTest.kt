package io.github.corebonts.muxgrid.core.htsp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class HtsMsgCodecTest {

    @Test
    fun `s64 encodes zero positive and negative values according to HTSP spec`() {
        assertArrayEquals(byteArrayOf(), HtsMsgCodec.encodeS64(0L))
        assertArrayEquals(byteArrayOf(0x39, 0x05), HtsMsgCodec.encodeS64(1337L))
        assertArrayEquals(byteArrayOf(0xFF.toByte()), HtsMsgCodec.encodeS64(255L))
        assertArrayEquals(ByteArray(8) { 0xFF.toByte() }, HtsMsgCodec.encodeS64(-1L))
        assertArrayEquals(
            byteArrayOf(
                0xC7.toByte(),
                0xFA.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
            ),
            HtsMsgCodec.encodeS64(-1337L),
        )

        // Roundtrip boundary values
        val samples = listOf(0L, 1L, 127L, 128L, 255L, 1337L, Int.MAX_VALUE.toLong(), Long.MAX_VALUE, -1L, -128L, -1337L, Long.MIN_VALUE)
        for (value in samples) {
            val encoded = HtsMsgCodec.encodeS64(value)
            assertEquals(value, HtsMsgCodec.decodeS64(encoded, 0, encoded.size), "Failed roundtrip for $value")
        }
    }

    @Test
    fun `bool encodes true as 1 byte and false as empty payload`() {
        val msg = htsMap {
            put("yes", true)
            put("no", false)
        }
        val body = HtsMsgCodec.encodeMapBody(msg)
        val decoded = HtsMsgCodec.decodeMapBody(body)
        assertEquals(true, decoded.getBoolean("yes"))
        assertEquals(false, decoded.getBoolean("no"))

        // Verify raw wire bytes for "no" have type=7, nameLen=2, dataLen=0
        val noHeader = byteArrayOf(7, 2, 0, 0, 0, 0, 'n'.code.toByte(), 'o'.code.toByte())
        val bodyHex = body.joinToString("") { "%02x".format(it) }
        val noHex = noHeader.joinToString("") { "%02x".format(it) }
        assertTrue(bodyHex.contains(noHex), "Expected false BOOL field with dataLen=0")
    }

    @Test
    fun `roundtrips Hungarian UTF-8 nested maps lists bin and uuid`() {
        val uuid = ByteArray(16) { it.toByte() }
        val bin = byteArrayOf(0x01, 0x02, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val hungarian = "Műsorújság — Petőfi Rádió — Árvíztűrő tükörfúrógép"

        val msg = htsMap {
            put("title", hungarian)
            put("channelId", 691304913L)
            put("offset", -42L)
            put("digest", bin)
            putUuid("uuid", uuid)
            put(
                "services",
                listOf(
                    HtsValue.MapVal(
                        htsMap {
                            put("name", "MindigTV/514MHz/M1 HD")
                            put("type", "HDTV")
                        },
                    ),
                    HtsValue.Str("secondary"),
                    HtsValue.S64(1080L),
                ),
            )
        }

        val frame = HtsMsgCodec.encodeFrame(msg)
        val decoded = HtsMsgCodec.readFrame(ByteArrayInputStream(frame))

        assertEquals(hungarian, decoded.getString("title"))
        assertEquals(691304913L, decoded.getLong("channelId"))
        assertEquals(-42L, decoded.getLong("offset"))
        assertArrayEquals(bin, decoded.getBytes("digest"))
        assertEquals(HtsValue.Uuid(uuid), decoded["uuid"])
        val services = decoded.getList("services")!!
        assertEquals(3, services.size)
        assertEquals("HDTV", (services[0] as HtsValue.MapVal).value.getString("type"))
        assertEquals("secondary", (services[1] as HtsValue.Str).value)
        assertEquals(1080L, (services[2] as HtsValue.S64).value)
    }

    @Test
    fun `skips unknown future field types safely by dataLen`() {
        val out = ByteArrayOutputStream()
        // Field 1: known string "a" = "ok"
        out.write(HtsMsgCodec.encodeMapBody(htsMap { put("a", "ok") }))
        // Field 2: unknown type 99, name "future", dataLen 4, payload [1,2,3,4]
        val name = "future".toByteArray(StandardCharsets.UTF_8)
        out.write(99)
        out.write(name.size)
        out.write(byteArrayOf(0, 0, 0, 4))
        out.write(name)
        out.write(byteArrayOf(1, 2, 3, 4))
        // Field 3: known s64 "b" = 77
        out.write(HtsMsgCodec.encodeMapBody(htsMap { put("b", 77L) }))

        val decoded = HtsMsgCodec.decodeMapBody(out.toByteArray())
        assertEquals("ok", decoded.getString("a"))
        assertEquals(77L, decoded.getLong("b"))
        assertFalse(decoded.containsKey("future"))
    }
}
