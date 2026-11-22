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
import java.net.Socket
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

    @Test
    fun `sanitizes CRLF in HTTP error status line from exception messages`() {
        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { "http://127.0.0.1:9981" },
            ticketProvider = { throw RuntimeException("bad\r\nX-Injected: evil") },
        )
        try {
            Socket("127.0.0.1", proxy.port).use { sock ->
                val out = sock.getOutputStream()
                out.write(
                    "GET /${proxy.sessionToken}/live/1 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n"
                        .toByteArray(StandardCharsets.US_ASCII),
                )
                out.flush()

                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII))
                val statusLine = reader.readLine()!!
                assertTrue(statusLine.startsWith("HTTP/1.1 502 "))
                val headers = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    headers.add(line)
                }
                assertFalse(headers.any { it.startsWith("X-Injected:", ignoreCase = true) })
            }
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `rejects oversized request header lines and excessive header count`() {
        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
        )
        try {
            // 1. Oversized header line (> MAX_HEADER_LINE_BYTES)
            Socket("127.0.0.1", proxy.port).use { sock ->
                val bigVal = "A".repeat(LocalMediaProxy.MAX_HEADER_LINE_BYTES + 64)
                val out = sock.getOutputStream()
                out.write(
                    "GET /${proxy.sessionToken}/live/1 HTTP/1.1\r\nX-Big: $bigVal\r\n\r\n"
                        .toByteArray(StandardCharsets.US_ASCII),
                )
                out.flush()
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII))
                val statusLine = reader.readLine()!!
                assertTrue(statusLine.startsWith("HTTP/1.1 431 "), "Unexpected status: $statusLine")
            }

            // 2. Too many headers (> MAX_HEADER_COUNT)
            Socket("127.0.0.1", proxy.port).use { sock ->
                val req = buildString {
                    append("GET /${proxy.sessionToken}/live/1 HTTP/1.1\r\n")
                    repeat(LocalMediaProxy.MAX_HEADER_COUNT + 2) { idx ->
                        append("X-H-$idx: 1\r\n")
                    }
                    append("\r\n")
                }
                val out = sock.getOutputStream()
                out.write(req.toByteArray(StandardCharsets.US_ASCII))
                out.flush()
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII))
                val statusLine = reader.readLine()!!
                assertTrue(statusLine.startsWith("HTTP/1.1 431 "), "Unexpected status: $statusLine")
            }
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `rejects upstream response with excessive headers`() {
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { upstream ->
            val thread = Thread {
                upstream.accept().use { sock ->
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                    reader.readLine()
                    val out = sock.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\n".toByteArray(StandardCharsets.US_ASCII))
                    repeat(LocalMediaProxy.MAX_HEADER_COUNT + 5) { idx ->
                        out.write("X-Extra-$idx: val\r\n".toByteArray(StandardCharsets.US_ASCII))
                    }
                    out.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
                    out.flush()
                }
            }.apply { start() }

            val proxy = LocalMediaProxy(
                serverBaseUrlProvider = { "http://127.0.0.1:${upstream.localPort}" },
                ticketProvider = { chId -> StreamTicket("/stream/channelid/$chId", "T1") },
            )
            try {
                val conn = URL(proxy.liveStreamUrl(1L)).openConnection() as HttpURLConnection
                assertEquals(503, conn.responseCode)
            } finally {
                proxy.close()
                thread.join(2_000)
            }
        }
    }

    @Test
    fun `decodes upstream Transfer-Encoding chunked for live and DVR streams`() {
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { upstream ->
            val thread = Thread {
                repeat(2) {
                    upstream.accept().use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                        val reqLine = reader.readLine() ?: ""
                        while (true) {
                            val l = reader.readLine() ?: break
                            if (l.isEmpty()) break
                        }
                        val out = sock.getOutputStream()
                        val ct = if (reqLine.contains("/dvrfile/")) "video/x-matroska" else "video/mp2t"
                        out.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: $ct\r\n" +
                                    "Transfer-Encoding: chunked\r\n" +
                                    "Connection: close\r\n\r\n" +
                                    "5;ext=1\r\nHELLO\r\n" +
                                    "6\r\n_WORLD\r\n" +
                                    "0\r\nX-Trailer: done\r\n\r\n"
                                ).toByteArray(StandardCharsets.US_ASCII),
                        )
                        out.flush()
                    }
                }
            }.apply { start() }

            val proxy = LocalMediaProxy(
                serverBaseUrlProvider = { "http://127.0.0.1:${upstream.localPort}" },
                ticketProvider = { chId -> StreamTicket("/stream/channelid/$chId", "L1") },
                dvrTicketProvider = { dvrId -> StreamTicket("/dvrfile/$dvrId", "D1") },
            )
            try {
                assertEquals("HELLO_WORLD", httpGet(proxy.liveStreamUrl(1L)))
                assertEquals("HELLO_WORLD", httpGet(proxy.dvrStreamUrl(2L)))
            } finally {
                proxy.close()
                thread.join(2_000)
            }
        }
    }

    @Test
    fun `growing DVR HEAD with zero bytes and Range returns 200 instead of invalid bytes 0-0`() {
        val isRecording = AtomicBoolean(true)
        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long = 0L
                    override suspend fun statSize(): Long = 0L
                    override suspend fun read(offset: Long, size: Int): ByteArray = ByteArray(0)
                    override fun close() = Unit
                }
            },
            isDvrRecordingProvider = { isRecording.get() },
        )
        try {
            val headLive = URL(proxy.growingDvrStreamUrl(55L)).openConnection() as HttpURLConnection
            headLive.requestMethod = "HEAD"
            headLive.setRequestProperty("Range", "bytes=0-")
            assertEquals(200, headLive.responseCode)
            assertNull(headLive.getHeaderField("Content-Range"))
            assertEquals("bytes", headLive.getHeaderField("Accept-Ranges"))

            isRecording.set(false)
            val headDone = URL(proxy.growingDvrStreamUrl(55L)).openConnection() as HttpURLConnection
            headDone.requestMethod = "HEAD"
            headDone.setRequestProperty("Range", "bytes=0-")
            assertEquals(416, headDone.responseCode)
            assertEquals("bytes */0", headDone.getHeaderField("Content-Range"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `resolves https upstream endpoint and connects via SSLSocketFactory`() {
        val capturedHosts = mutableListOf<String>()
        val capturedPorts = mutableListOf<Int>()
        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { "https://tvh.example.invalid/proxy" },
            ticketProvider = { chId -> StreamTicket("/stream/channelid/$chId", "S1") },
            sslSocketFactoryProvider = {
                object : javax.net.ssl.SSLSocketFactory() {
                    override fun getDefaultCipherSuites(): Array<String> = emptyArray()
                    override fun getSupportedCipherSuites(): Array<String> = emptyArray()
                    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
                        capturedHosts.add(host ?: "")
                        capturedPorts.add(port)
                        throw java.io.IOException("test handshake stopped")
                    }
                    override fun createSocket(host: String?, port: Int) = throw UnsupportedOperationException()
                    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int) =
                        throw UnsupportedOperationException()
                    override fun createSocket(host: InetAddress?, port: Int) = throw UnsupportedOperationException()
                    override fun createSocket(
                        address: InetAddress?,
                        port: Int,
                        localAddress: InetAddress?,
                        localPort: Int,
                    ) = throw UnsupportedOperationException()
                }
            },
        )
        // Open a local TCP listener on an ephemeral port? Instead test explicit https port so connect succeeds and hits SSLSocketFactory:
        proxy.close()

        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { upstream ->
            val thread = Thread {
                runCatching { upstream.accept().use { } }
            }.apply { start() }
            val httpsProxy = LocalMediaProxy(
                serverBaseUrlProvider = { "https://127.0.0.1:${upstream.localPort}/base" },
                ticketProvider = { chId -> StreamTicket("/stream/channelid/$chId", "S1") },
                sslSocketFactoryProvider = {
                    object : javax.net.ssl.SSLSocketFactory() {
                        override fun getDefaultCipherSuites(): Array<String> = emptyArray()
                        override fun getSupportedCipherSuites(): Array<String> = emptyArray()
                        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
                            capturedHosts.add(host ?: "")
                            capturedPorts.add(port)
                            s?.close()
                            throw java.io.IOException("wrapped TLS check")
                        }
                        override fun createSocket(host: String?, port: Int) = throw UnsupportedOperationException()
                        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int) =
                            throw UnsupportedOperationException()
                        override fun createSocket(host: InetAddress?, port: Int) = throw UnsupportedOperationException()
                        override fun createSocket(
                            address: InetAddress?,
                            port: Int,
                            localAddress: InetAddress?,
                            localPort: Int,
                        ) = throw UnsupportedOperationException()
                    }
                },
            )
            try {
                val conn = URL(httpsProxy.liveStreamUrl(9L)).openConnection() as HttpURLConnection
                assertEquals(503, conn.responseCode)
                assertEquals(listOf("127.0.0.1"), capturedHosts)
                assertEquals(listOf(upstream.localPort), capturedPorts)
            } finally {
                httpsProxy.close()
                thread.join(2_000)
            }
        }
    }

    @Test
    fun `growing DVR pipelines concurrent fileReads behind write head and preserves byte order`() {
        val totalBytes = 1_100_000
        val payload = ByteArray(totalBytes) { i -> (i * 31 + 7).toByte() }
        val inFlight = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long = payload.size.toLong()
                    override suspend fun statSize(): Long = payload.size.toLong()
                    override suspend fun read(offset: Long, size: Int): ByteArray {
                        val active = inFlight.incrementAndGet()
                        maxConcurrent.accumulateAndGet(active, ::maxOf)
                        try {
                            kotlinx.coroutines.delay(25L)
                            val off = offset.toInt()
                            if (off >= payload.size) return ByteArray(0)
                            return payload.copyOfRange(off, minOf(payload.size, off + size))
                        } finally {
                            inFlight.decrementAndGet()
                        }
                    }
                    override fun close() = Unit
                }
            },
            isDvrRecordingProvider = { false },
            growingPollIntervalMs = 20L,
        )

        try {
            val conn = URL(proxy.growingDvrStreamUrl(404L)).openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            val received = conn.inputStream.readBytes()
            assertEquals(payload.size, received.size)
            assertTrue(payload.contentEquals(received))
            assertTrue(maxConcurrent.get() >= 2, "Expected pipelined concurrent reads, max was ${maxConcurrent.get()}")
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `growing DVR adapts cleanly when server caps fileRead below requested chunk size`() {
        val totalBytes = 400_000
        val serverCap = 32_768
        val payload = ByteArray(totalBytes) { i -> ((i * 17) xor (i ushr 8)).toByte() }

        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long = payload.size.toLong()
                    override suspend fun statSize(): Long = payload.size.toLong()
                    override suspend fun read(offset: Long, size: Int): ByteArray {
                        val off = offset.toInt()
                        if (off >= payload.size) return ByteArray(0)
                        val capped = minOf(size, serverCap)
                        return payload.copyOfRange(off, minOf(payload.size, off + capped))
                    }
                    override fun close() = Unit
                }
            },
            isDvrRecordingProvider = { false },
            growingPollIntervalMs = 20L,
        )

        try {
            val conn = URL(proxy.growingDvrStreamUrl(505L)).openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            val received = conn.inputStream.readBytes()
            assertEquals(payload.size, received.size)
            assertTrue(payload.contentEquals(received))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `growing DVR spliceOffset serves cached MKV header followed by cluster bytes`() {
        val header = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x02, 0x03, 0x04)
        val cluster1 = "CLUSTER_ONE_DATA".toByteArray(StandardCharsets.US_ASCII)
        val cluster2 = "CLUSTER_TWO_TARGET".toByteArray(StandardCharsets.US_ASCII)
        val fullFile = header + cluster1 + cluster2
        val spliceOffset = (header.size + cluster1.size).toLong()

        val store = GrowingMkvIndexStore()
        val idx = store.getOrLoad(dvrId = 606L, startEpochSec = 1000L)
        idx.recordHeader(header, GrowingMkvIndex.DEFAULT_TIMESTAMP_SCALE_NS)
        idx.recordCluster(30_000L, spliceOffset)

        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long = fullFile.size.toLong()
                    override suspend fun statSize(): Long = fullFile.size.toLong()
                    override suspend fun read(offset: Long, size: Int): ByteArray {
                        val off = offset.toInt()
                        if (off >= fullFile.size) return ByteArray(0)
                        return fullFile.copyOfRange(off, minOf(fullFile.size, off + size))
                    }
                    override fun close() = Unit
                }
            },
            isDvrRecordingProvider = { false },
            growingIndexStore = store,
            dvrStartEpochProvider = { 1000L },
            growingPollIntervalMs = 20L,
        )

        try {
            val conn = URL(proxy.growingDvrStreamUrl(606L, spliceOffset = spliceOffset)).openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            val received = conn.inputStream.readBytes()
            val expected = header + cluster2
            assertTrue(expected.contentEquals(received))
            assertEquals("video/x-matroska", conn.getHeaderField("Content-Type"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `growing DVR sniffs video mp2t Content-Type for MPEG-TS recordings and caches NON_MKV`() {
        val tsPayload = ByteArray(376) { idx -> if (idx % 188 == 0) 0x47 else 0x11 }
        val store = GrowingMkvIndexStore()
        var readZeroCount = 0
        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long = tsPayload.size.toLong()
                    override suspend fun statSize(): Long = tsPayload.size.toLong()
                    override suspend fun read(offset: Long, size: Int): ByteArray {
                        if (offset == 0L) readZeroCount++
                        val off = offset.toInt()
                        if (off >= tsPayload.size) return ByteArray(0)
                        return tsPayload.copyOfRange(off, minOf(tsPayload.size, off + size))
                    }
                    override fun close() = Unit
                }
            },
            isDvrRecordingProvider = { false },
            growingIndexStore = store,
            dvrStartEpochProvider = { 1000L },
            growingPollIntervalMs = 20L,
        )
        try {
            val conn = URL(proxy.growingDvrStreamUrl(707L)).openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            assertEquals("video/mp2t", conn.getHeaderField("Content-Type"))
            assertTrue(tsPayload.contentEquals(conn.inputStream.readBytes()))
            assertEquals(1, readZeroCount, "Expected chunk 0 prefetch to avoid duplicate offset-0 read")
            assertEquals(
                GrowingMkvIndex.ContainerKind.NON_MKV,
                store.getOrLoad(707L, 1000L).containerKind,
            )
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `growing DVR captures MKV header on initial stream with single offset-0 read`() {
        val header = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x84.toByte(), 0x01, 0x02, 0x03, 0x04,
            0x2A, 0xD7.toByte(), 0xB1.toByte(), 0x83.toByte(), 0x0F, 0x42, 0x40,
        )
        val cluster = byteArrayOf(
            0x1F, 0x43, 0xB6.toByte(), 0x75, 0x01,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xE7.toByte(), 0x81.toByte(), 0x00, 0xA3.toByte(), 0x11, 0x22,
        )
        val mkvPayload = header + cluster
        val store = GrowingMkvIndexStore()
        var readZeroCount = 0
        val proxy = LocalMediaProxy(
            serverBaseUrlProvider = { null },
            ticketProvider = { StreamTicket("", "") },
            growingFileOpener = {
                object : GrowingDvrFile {
                    override val initialSize: Long = mkvPayload.size.toLong()
                    override suspend fun statSize(): Long = mkvPayload.size.toLong()
                    override suspend fun read(offset: Long, size: Int): ByteArray {
                        if (offset == 0L) readZeroCount++
                        val off = offset.toInt()
                        if (off >= mkvPayload.size) return ByteArray(0)
                        return mkvPayload.copyOfRange(off, minOf(mkvPayload.size, off + size))
                    }
                    override fun close() = Unit
                }
            },
            isDvrRecordingProvider = { false },
            growingIndexStore = store,
            dvrStartEpochProvider = { 1000L },
            growingPollIntervalMs = 20L,
        )
        try {
            val conn = URL(proxy.growingDvrStreamUrl(808L)).openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            assertEquals("video/x-matroska", conn.getHeaderField("Content-Type"))
            assertTrue(mkvPayload.contentEquals(conn.inputStream.readBytes()))
            assertEquals(1, readZeroCount)
            val idx = store.getOrLoad(808L, 1000L)
            assertTrue(idx.hasHeader)
            assertTrue(header.contentEquals(idx.headerBytes))
            assertEquals(header.size.toLong(), idx.findClusterAtOrBefore(0L)?.byteOffset)
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
