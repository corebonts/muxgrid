package io.github.corebonts.muxgrid.core.data

import coil3.fetch.SourceFetchResult
import io.github.corebonts.muxgrid.core.htsp.HtsMsgCodec
import io.github.corebonts.muxgrid.core.htsp.HtspClient
import io.github.corebonts.muxgrid.core.htsp.htsMap
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PiconFetcherTest {

    @Test
    fun `falls back to HTTP when HTSP fileRead returns zero bytes`() = runBlocking {
        val httpPayload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { htspServer ->
            ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { httpServer ->
                val htspTask = async(Dispatchers.IO) {
                    htspServer.accept().use { sock ->
                        val input = BufferedInputStream(sock.getInputStream())
                        val output = BufferedOutputStream(sock.getOutputStream())
                        val hello = HtsMsgCodec.readFrame(input)
                        output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                        output.flush()
                        val sync = HtsMsgCodec.readFrame(input)
                        output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                        output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                        output.flush()

                        while (true) {
                            val req = runCatching { HtsMsgCodec.readFrame(input) }.getOrNull() ?: break
                            val seq = req.getLong("seq")!!
                            val reply = when (req.getString("method")) {
                                "getSysTime" -> htsMap { put("seq", seq); put("time", 1_000L) }
                                "fileOpen" -> htsMap { put("seq", seq); put("id", 42L); put("size", 0L); put("mtime", 1L) }
                                "fileRead" -> htsMap { put("seq", seq); put("data", ByteArray(0)) }
                                "fileClose" -> htsMap { put("seq", seq) }
                                else -> htsMap { put("seq", seq) }
                            }
                            output.write(HtsMsgCodec.encodeFrame(reply))
                            output.flush()
                        }
                    }
                }

                val httpTask = async(Dispatchers.IO) {
                    httpServer.accept().use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val out = sock.getOutputStream()
                        val headers = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${httpPayload.size}\r\nConnection: close\r\n\r\n"
                        out.write(headers.toByteArray())
                        out.write(httpPayload)
                        out.flush()
                    }
                }

                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val htspClient = HtspClient(scope = scope)
                val profile = ServerProfile(
                    host = "127.0.0.1",
                    htspPort = htspServer.localPort,
                    httpPort = httpServer.localPort,
                )
                try {
                    htspClient.connect(profile)
                    withTimeout(5_000) {
                        htspClient.connectionState.first { it is ConnectionState.Connected }
                    }

                    val fetcher = PiconFetcher(
                        rawIcon = "imagecache/7",
                        htspClient = htspClient,
                        httpClient = OkHttpClient(),
                        profileProvider = { profile },
                        diskCache = null,
                    )
                    val result = fetcher.fetch() as SourceFetchResult
                    assertArrayEquals(httpPayload, result.source.source().readByteArray())
                    assertEquals("image/png", result.mimeType)
                } finally {
                    htspClient.disconnect()
                    scope.cancel()
                    htspTask.await()
                    httpTask.await()
                }
            }
        }
    }

    @Test
    fun `cancelling during tryHtspRead rethrows CancellationException skips HTTP and closes HTSP handle`() = runBlocking {
        val fileReadStarted = CompletableDeferred<Unit>()
        val fileClosed = AtomicBoolean(false)
        val httpHitCount = AtomicInteger(0)

        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { htspServer ->
            ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { httpServer ->
                val htspTask = async(Dispatchers.IO) {
                    htspServer.accept().use { sock ->
                        val input = BufferedInputStream(sock.getInputStream())
                        val output = BufferedOutputStream(sock.getOutputStream())
                        val hello = HtsMsgCodec.readFrame(input)
                        output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                        output.flush()
                        val sync = HtsMsgCodec.readFrame(input)
                        output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                        output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                        output.flush()

                        while (true) {
                            val req = runCatching { HtsMsgCodec.readFrame(input) }.getOrNull() ?: break
                            val seq = req.getLong("seq")!!
                            when (req.getString("method")) {
                                "getSysTime" -> {
                                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", seq); put("time", 1_000L) }))
                                    output.flush()
                                }
                                "fileOpen" -> {
                                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", seq); put("id", 77L); put("size", 1024L); put("mtime", 1L) }))
                                    output.flush()
                                }
                                "fileRead" -> {
                                    // Stall fileRead until cancelled; subsequent fileClose must still arrive.
                                    fileReadStarted.complete(Unit)
                                }
                                "fileClose" -> {
                                    assertEquals(77L, req.getLong("id"))
                                    fileClosed.set(true)
                                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", seq) }))
                                    output.flush()
                                    break
                                }
                            }
                        }
                    }
                }

                val httpWatchJob = launch(Dispatchers.IO) {
                    runCatching {
                        httpServer.accept().use { httpHitCount.incrementAndGet() }
                    }
                }

                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val htspClient = HtspClient(scope = scope)
                val profile = ServerProfile(
                    host = "127.0.0.1",
                    htspPort = htspServer.localPort,
                    httpPort = httpServer.localPort,
                )
                try {
                    htspClient.connect(profile)
                    withTimeout(5_000) {
                        htspClient.connectionState.first { it is ConnectionState.Connected }
                    }

                    val fetcher = PiconFetcher(
                        rawIcon = "imagecache/9",
                        htspClient = htspClient,
                        httpClient = OkHttpClient(),
                        profileProvider = { profile },
                        diskCache = null,
                    )
                    var caughtCancellation = false
                    val fetchJob = launch(Dispatchers.IO) {
                        try {
                            fetcher.fetch()
                        } catch (e: CancellationException) {
                            caughtCancellation = true
                            throw e
                        }
                    }
                    withTimeout(5_000) { fileReadStarted.await() }
                    fetchJob.cancel()
                    fetchJob.join()

                    assertTrue(caughtCancellation)
                    assertEquals(0, httpHitCount.get())
                    withTimeout(5_000) { htspTask.await() }
                    assertTrue(fileClosed.get())
                } finally {
                    httpWatchJob.cancel()
                    htspClient.disconnect()
                    scope.cancel()
                }
            }
        }
    }
}
