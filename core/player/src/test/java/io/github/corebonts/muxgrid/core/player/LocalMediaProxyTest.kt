package io.github.corebonts.muxgrid.core.player

import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import io.github.corebonts.muxgrid.core.model.StreamTicket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class LocalMediaProxyTest {

    @Test
    fun `caches ticket refreshes after TTL and retries once on 401`() {
        val fakeNow = AtomicLong(1_000L)
        val ticketCounter = AtomicInteger(0)
        val rejectFirstTicket = AtomicBoolean(true)

        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { upstream ->
            val thread = Thread {
                repeat(4) {
                    upstream.accept().use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                        val reqLine = reader.readLine() ?: ""
                        while (true) {
                            val l = reader.readLine() ?: break
                            if (l.isEmpty()) break
                        }
                        val out = sock.getOutputStream()
                        if (reqLine.contains("ticket=T1") && rejectFirstTicket.compareAndSet(true, false)) {
                            out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        } else if (reqLine.contains("ticket=T1")) {
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nConnection: close\r\n\r\nSTREAM1".toByteArray())
                        } else if (reqLine.contains("ticket=T2")) {
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nConnection: close\r\n\r\nSTREAM2".toByteArray())
                        } else {
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nConnection: close\r\n\r\nSTREAM3".toByteArray())
                        }
                        out.flush()
                    }
                }
            }.apply { start() }

            val proxy = LocalMediaProxy(
                serverBaseUrlProvider = { "http://127.0.0.1:${upstream.localPort}" },
                ticketProvider = { chId ->
                    val n = ticketCounter.incrementAndGet()
                    StreamTicket(path = "/stream/channelid/$chId", ticket = "T$n")
                },
                ticketTtlMs = 240_000L,
                nowMsProvider = { fakeNow.get() },
            )

            try {
                // 1st request gets T1 -> 401 -> refreshes to T2 -> 200 STREAM2
                val body1 = httpGet(proxy.liveStreamUrl(10L))
                assertEquals("STREAM2", body1)
                assertEquals(2, ticketCounter.get())

                // 2nd request within TTL reuses cached T2 -> 200 STREAM2
                fakeNow.addAndGet(60_000L)
                val body2 = httpGet(proxy.liveStreamUrl(10L))
                assertEquals("STREAM2", body2)
                assertEquals(2, ticketCounter.get())

                // 3rd request after TTL expires refreshes to T3 -> 200 STREAM3
                fakeNow.addAndGet(250_000L)
                val body3 = httpGet(proxy.liveStreamUrl(10L))
                assertEquals("STREAM3", body3)
                assertEquals(3, ticketCounter.get())
            } finally {
                proxy.close()
                thread.join(2_000)
            }
        }
    }

    @Test
    fun `detects 0-byte empty upstream reply and fires failure callback`() {
        val failureLatch = CountDownLatch(1)
        val sawEmptyReply = AtomicBoolean(false)
        val failedChannelId = AtomicLong(-1L)

        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { upstream ->
            val thread = Thread {
                upstream.accept().use { sock ->
                    // Read request headers briefly, then close immediately with 0 bytes (Tvheadend single-tuner behavior)
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                    reader.readLine()
                    sock.close()
                }
            }.apply { start() }

            val proxy = LocalMediaProxy(
                serverBaseUrlProvider = { "http://127.0.0.1:${upstream.localPort}" },
                ticketProvider = { chId -> StreamTicket("/stream/channelid/$chId", "T1") },
                onLiveOpenFailed = { chId, _, isEmpty ->
                    failedChannelId.set(chId)
                    sawEmptyReply.set(isEmpty)
                    failureLatch.countDown()
                },
            )

            try {
                val conn = URL(proxy.liveStreamUrl(42L)).openConnection() as HttpURLConnection
                assertEquals(503, conn.responseCode)
                assertTrue(failureLatch.await(3, TimeUnit.SECONDS))
                assertEquals(42L, failedChannelId.get())
                assertTrue(sawEmptyReply.get())
            } finally {
                proxy.close()
                thread.join(2_000)
            }
        }
    }

    @Test
    fun `forwards DVR Range requests preserves 206 headers and refreshes expired ticket on 401`() {
        val dvrTicketCounter = AtomicInteger(0)
        val rejectFirst = AtomicBoolean(true)
        val capturedRequests = mutableListOf<String>()
        val capturedRanges = mutableListOf<String?>()

        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { upstream ->
            val thread = Thread {
                repeat(3) {
                    upstream.accept().use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                        val reqLine = reader.readLine() ?: ""
                        capturedRequests.add(reqLine)
                        var range: String? = null
                        while (true) {
                            val l = reader.readLine() ?: break
                            if (l.isEmpty()) break
                            if (l.startsWith("Range:", ignoreCase = true)) {
                                range = l.substringAfter(':').trim()
                            }
                        }
                        capturedRanges.add(range)

                        val out = sock.getOutputStream()
                        if (reqLine.contains("ticket=DT1") && rejectFirst.compareAndSet(true, false)) {
                            out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        } else if (range == "bytes=10-19") {
                            val slice = "0123456789".toByteArray(StandardCharsets.US_ASCII)
                            out.write(
                                (
                                    "HTTP/1.1 206 Partial Content\r\n" +
                                        "Content-Type: video/x-matroska\r\n" +
                                        "Content-Range: bytes 10-19/100\r\n" +
                                        "Accept-Ranges: bytes\r\n" +
                                        "Content-Length: ${slice.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII),
                            )
                            out.write(slice)
                        } else {
                            out.write(
                                (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: video/x-matroska\r\n" +
                                        "Accept-Ranges: bytes\r\n" +
                                        "Content-Length: 100\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII),
                            )
                        }
                        out.flush()
                    }
                }
            }.apply { start() }

            val proxy = LocalMediaProxy(
                serverBaseUrlProvider = { "http://127.0.0.1:${upstream.localPort}" },
                ticketProvider = { chId -> StreamTicket("/stream/channelid/$chId", "LIVE") },
                dvrTicketProvider = { dvrId ->
                    val n = dvrTicketCounter.incrementAndGet()
                    StreamTicket("/dvrfile/$dvrId", "DT$n")
                },
            )

            try {
                // Range request hits DT1 -> 401 -> refreshes to DT2 -> 206 Partial Content
                val conn = URL(proxy.dvrStreamUrl(99L)).openConnection() as HttpURLConnection
                conn.setRequestProperty("Range", "bytes=10-19")
                assertEquals(206, conn.responseCode)
                assertEquals("bytes 10-19/100", conn.getHeaderField("Content-Range"))
                assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
                assertEquals("0123456789", conn.inputStream.readBytes().toString(StandardCharsets.US_ASCII))
                assertEquals(2, dvrTicketCounter.get())

                // HEAD request reuses cached DT2
                val headConn = URL(proxy.dvrStreamUrl(99L)).openConnection() as HttpURLConnection
                headConn.requestMethod = "HEAD"
                assertEquals(200, headConn.responseCode)
                assertEquals("100", headConn.getHeaderField("Content-Length"))
                assertEquals(2, dvrTicketCounter.get())

                assertTrue(capturedRequests.all { !it.contains("profile=pass") })
                assertEquals(listOf("bytes=10-19", "bytes=10-19", null), capturedRanges)
            } finally {
                proxy.close()
                thread.join(2_000)
            }
        }
    }

    @Test
    fun `growing DVR reads at write head transitions recording to completed and calls fileClose`() {
        val data = AtomicReference("0123456789".toByteArray(StandardCharsets.US_ASCII))
        val isRecording = AtomicBoolean(true)
        val closeCount = AtomicInteger(0)
        val closeLatch = CountDownLatch(1)

        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long get() = data.get().size.toLong()
                    override suspend fun statSize(): Long = data.get().size.toLong()
                    override suspend fun read(offset: Long, size: Int): ByteArray {
                        val buf = data.get()
                        val off = offset.toInt()
                        if (off >= buf.size) return ByteArray(0)
                        return buf.copyOfRange(off, minOf(buf.size, off + size))
                    }
                    override fun close() {
                        closeCount.incrementAndGet()
                        closeLatch.countDown()
                    }
                }
            },
            isDvrRecordingProvider = { isRecording.get() },
            growingPollIntervalMs = 30L,
        )

        val appender = Thread {
            Thread.sleep(80L)
            data.set("0123456789ABCDEF".toByteArray(StandardCharsets.US_ASCII))
            Thread.sleep(80L)
            data.set("0123456789ABCDEF_TAIL".toByteArray(StandardCharsets.US_ASCII))
            isRecording.set(false)
        }.apply { start() }

        try {
            val conn = URL(proxy.growingDvrStreamUrl(101L)).openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
            assertNull(conn.getHeaderField("Content-Length"))

            val body = conn.inputStream.readBytes().toString(StandardCharsets.US_ASCII)
            assertEquals("0123456789ABCDEF_TAIL", body)
            assertTrue(closeLatch.await(3, TimeUnit.SECONDS))
            assertEquals(1, closeCount.get())
        } finally {
            appender.join(2_000)
            proxy.close()
        }
    }

    @Test
    fun `growing DVR range past current size waits without 416 while recording and returns 416 once completed`() {
        val data = AtomicReference("0123456789".toByteArray(StandardCharsets.US_ASCII))
        val isRecording = AtomicBoolean(true)
        val closeCount = AtomicInteger(0)
        val closeLatch = CountDownLatch(2)

        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long get() = data.get().size.toLong()
                    override suspend fun statSize(): Long = data.get().size.toLong()
                    override suspend fun read(offset: Long, size: Int): ByteArray {
                        val buf = data.get()
                        val off = offset.toInt()
                        if (off >= buf.size) return ByteArray(0)
                        return buf.copyOfRange(off, minOf(buf.size, off + size))
                    }
                    override fun close() {
                        closeCount.incrementAndGet()
                        closeLatch.countDown()
                    }
                }
            },
            isDvrRecordingProvider = { isRecording.get() },
            growingPollIntervalMs = 30L,
        )

        val appender = Thread {
            Thread.sleep(90L)
            data.set("012345678901234abcdefghij".toByteArray(StandardCharsets.US_ASCII))
            Thread.sleep(80L)
            isRecording.set(false)
        }.apply { start() }

        try {
            // Range starts at byte 15 while size is 10 -> must NOT return 416; waits and serves 206 with /*
            val conn = URL(proxy.growingDvrStreamUrl(202L)).openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=15-")
            assertEquals(206, conn.responseCode)
            assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
            val contentRange = conn.getHeaderField("Content-Range") ?: ""
            assertTrue(contentRange.startsWith("bytes 15-") && contentRange.endsWith("/*"), "Unexpected Content-Range: $contentRange")
            assertNull(conn.getHeaderField("Content-Length"))
            assertEquals("abcdefghij", conn.inputStream.readBytes().toString(StandardCharsets.US_ASCII))
            appender.join(2_000)

            // Once recording completed, out-of-bounds range returns 416
            val completedConn = URL(proxy.growingDvrStreamUrl(202L)).openConnection() as HttpURLConnection
            completedConn.setRequestProperty("Range", "bytes=100-")
            assertEquals(416, completedConn.responseCode)
            assertEquals("bytes */25", completedConn.getHeaderField("Content-Range"))
            assertTrue(closeLatch.await(3, TimeUnit.SECONDS))
            assertTrue(closeCount.get() >= 2)
        } finally {
            appender.join(2_000)
            proxy.close()
        }
    }

    @Test
    fun `growing DVR mid-read client disconnect immediately invokes fileClose`() {
        val data = "0123456789".toByteArray(StandardCharsets.US_ASCII)
        val closeLatch = CountDownLatch(1)

        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long = data.size.toLong()
                    override suspend fun statSize(): Long = data.size.toLong()
                    override suspend fun read(offset: Long, size: Int): ByteArray {
                        val off = offset.toInt()
                        if (off >= data.size) return ByteArray(0)
                        return data.copyOfRange(off, minOf(data.size, off + size))
                    }
                    override fun close() {
                        closeLatch.countDown()
                    }
                }
            },
            isDvrRecordingProvider = { true },
            growingPollIntervalMs = 200L,
        )

        try {
            val conn = URL(proxy.growingDvrStreamUrl(303L)).openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            val input = conn.inputStream
            val collected = ByteArrayOutputStream()
            val buf = ByteArray(10)
            while (collected.size() < 10) {
                val n = input.read(buf, 0, 10 - collected.size())
                if (n < 0) break
                collected.write(buf, 0, n)
            }
            assertEquals("0123456789", collected.toString(StandardCharsets.US_ASCII.name()))

            // Disconnect mid-stream while proxy is polling at the write head
            input.close()
            conn.disconnect()
            assertTrue(closeLatch.await(2, TimeUnit.SECONDS))
            val stats = proxy.snapshotStreamStats()
            assertEquals("HTSP VFS (growing)", stats.transportMode)
            assertEquals("HTSP VFS", stats.upstreamHost)
            assertEquals("Tail-follow", stats.upstreamStatus)
            assertEquals(10L, stats.totalBytesRead)

            proxy.resetStreamStats()
            val cleared = proxy.snapshotStreamStats()
            assertNull(cleared.transportMode)
            assertEquals(0L, cleared.totalBytesRead)
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `HttpPassStrategy computes transport-agnostic live timeshift metrics`() {
        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
        )
        try {
            val strategy = HttpPassStrategy(proxy)
            // Live HTTP MPEG-TS streams report seekable=false in libmpv even when seekable-ranges are cached
            val live = strategy.computeTimeshiftMetrics(
                positionMs = 120_000L,
                cacheStartMs = 10_000L,
                cacheEndMs = 150_000L,
                timeshiftEnabled = true,
            )
            assertTrue(live.isTimeshiftSeekable)
            assertEquals(10_000L, live.bufferStartMs)
            assertEquals(150_000L, live.bufferEndMs)
            assertEquals(140_000L, live.bufferWindowMs)
            assertEquals(30_000L, live.liveEdgeOffsetMs)

            val disabled = strategy.computeTimeshiftMetrics(
                positionMs = 120_000L,
                cacheStartMs = 10_000L,
                cacheEndMs = 150_000L,
                timeshiftEnabled = false,
            )
            assertFalse(disabled.isTimeshiftSeekable)
        } finally {
            proxy.close()
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        return conn.inputStream.readBytes().toString(StandardCharsets.UTF_8)
    }
}
