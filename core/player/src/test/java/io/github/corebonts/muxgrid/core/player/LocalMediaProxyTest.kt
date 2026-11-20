package io.github.corebonts.muxgrid.core.player

import io.github.corebonts.muxgrid.core.model.StreamTicket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
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

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        return conn.inputStream.readBytes().toString(StandardCharsets.UTF_8)
    }
}
