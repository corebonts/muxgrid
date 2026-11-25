package io.github.corebonts.muxgrid.core.htsp

import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.ConnectionTestResult
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class HtspClientTest {

    @Test
    fun `connects authenticates syncs and correlates concurrent RPCs`() = runBlocking {
        val challenge = ByteArray(32) { (it + 1).toByte() }
        val expectedDigest = HtspClient.computeSha1Digest("secret", challenge)

        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())

                    val hello = HtsMsgCodec.readFrame(input)
                    assertEquals("hello", hello.getString("method"))
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 44)
                                put("servername", "MockTvh")
                                put("serverversion", "4.3-mock")
                                put("challenge", challenge)
                            },
                        ),
                    )
                    output.flush()

                    val auth = HtsMsgCodec.readFrame(input)
                    assertEquals("authenticate", auth.getString("method"))
                    assertEquals("user1", auth.getString("username"))
                    assertArrayEquals(expectedDigest, auth.getBytes("digest"))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", auth.getLong("seq")!!) }))
                    output.flush()

                    val sync = HtsMsgCodec.readFrame(input)
                    assertEquals("enableAsyncMetadata", sync.getString("method"))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("method", "channelAdd")
                                put("channelId", 10L)
                                put("channelNumber", 1)
                                put("channelName", "M1 HD")
                            },
                        ),
                    )
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                    output.flush()

                    // Handle initial getSysTime + two concurrent RPCs
                    repeat(3) {
                        val req = HtsMsgCodec.readFrame(input)
                        val seq = req.getLong("seq")!!
                        when (req.getString("method")) {
                            "getSysTime" -> {
                                output.write(
                                    HtsMsgCodec.encodeFrame(
                                        htsMap {
                                            put("seq", seq)
                                            put("time", System.currentTimeMillis() / 1000L)
                                            put("gmtoffset", 3600L)
                                        },
                                    ),
                                )
                            }
                            "getTicket" -> {
                                output.write(
                                    HtsMsgCodec.encodeFrame(
                                        htsMap {
                                            put("seq", seq)
                                            put("path", "/stream/channelid/10")
                                            put("ticket", "TICKET_ABC")
                                        },
                                    ),
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HtspClient(scope = scope)
            val receivedEvents = mutableListOf<HtspPushEvent>()
            val collector = scope.launch {
                client.events.collect { receivedEvents.add(it) }
            }

            client.connect(
                ServerProfile(
                    host = "127.0.0.1",
                    htspPort = server.localPort,
                    username = "user1",
                    password = "secret",
                ),
            )

            withTimeout(5_000) {
                client.connectionState.first { it is ConnectionState.Connected }
            }

            val t1 = async { client.getTicket(10L) }
            val t2 = async { client.getSysTime() }
            assertEquals("TICKET_ABC", t1.await().ticket)
            assertEquals(3600L, t2.await().gmtOffsetSec)

            assertTrue(receivedEvents.any { it is HtspPushEvent.ChannelAdd && it.channel.name == "M1 HD" })
            assertTrue(receivedEvents.any { it is HtspPushEvent.InitialSyncCompleted })

            client.disconnect()
            collector.cancel()
            scope.cancel()
            serverTask.await()
        }
    }

    @Test
    fun `auth failure stops reconnect loop with isAuthError`() = runBlocking {
        val attempts = AtomicInteger(0)
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    attempts.incrementAndGet()
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 44)
                                put("challenge", ByteArray(32))
                            },
                        ),
                    )
                    output.flush()
                    val auth = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", auth.getLong("seq")!!)
                                put("noaccess", 1)
                            },
                        ),
                    )
                    output.flush()
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HtspClient(scope = scope)
            client.connect(
                ServerProfile(
                    host = "127.0.0.1",
                    htspPort = server.localPort,
                    username = "bad",
                    password = "wrong",
                ),
            )

            val state = withTimeout(5_000) {
                client.connectionState.first { it is ConnectionState.Error }
            } as ConnectionState.Error

            assertTrue(state.isAuthError)
            assertEquals(1, attempts.get())
            client.disconnect()
            scope.cancel()
            serverTask.await()
        }
    }

    @Test
    fun `sends 2-week epgMaxTime and handles DVR RPCs`() = runBlocking {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val capturedMethods = mutableListOf<String>()
            var capturedEpgWindowSec = 0L

            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())

                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 44)
                                put("servername", "MockTvh")
                                put("serverversion", "4.3-mock")
                            },
                        ),
                    )
                    output.flush()

                    val sync = HtsMsgCodec.readFrame(input)
                    val epgMaxTime = sync.getLong("epgMaxTime")!!
                    capturedEpgWindowSec = epgMaxTime - (System.currentTimeMillis() / 1000L)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                    output.flush()

                    // Handle initial getSysTime + 5 DVR RPCs + getTicket(dvrId)
                    repeat(7) {
                        val req = HtsMsgCodec.readFrame(input)
                        val seq = req.getLong("seq")!!
                        val method = req.getString("method")!!
                        capturedMethods.add(method)
                        val reply = when (method) {
                            "getSysTime" -> htsMap {
                                put("seq", seq)
                                put("time", System.currentTimeMillis() / 1000L)
                            }
                            "addDvrEntry" -> {
                                assertEquals(999L, req.getLong("eventId"))
                                htsMap {
                                    put("seq", seq)
                                    put("id", 777L)
                                    put("success", 1L)
                                }
                            }
                            "updateDvrEntry" -> {
                                assertEquals(777L, req.getLong("id"))
                                assertEquals(123L, req.getLong("playposition"))
                                assertEquals(1L, req.getLong("playcount"))
                                htsMap {
                                    put("seq", seq)
                                    put("success", 1L)
                                }
                            }
                            "stopDvrEntry", "cancelDvrEntry", "deleteDvrEntry" -> {
                                assertEquals(777L, req.getLong("id"))
                                htsMap {
                                    put("seq", seq)
                                    put("success", 1L)
                                }
                            }
                            "getTicket" -> {
                                assertEquals(777L, req.getLong("dvrId"))
                                htsMap {
                                    put("seq", seq)
                                    put("path", "/dvrfile/777")
                                    put("ticket", "DVR_TKT")
                                }
                            }
                            else -> htsMap { put("seq", seq) }
                        }
                        output.write(HtsMsgCodec.encodeFrame(reply))
                        output.flush()
                    }
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HtspClient(scope = scope)
            client.connect(
                ServerProfile(
                    host = "127.0.0.1",
                    htspPort = server.localPort,
                ),
            )

            withTimeout(5_000) {
                client.connectionState.first { it is ConnectionState.Connected }
            }

            assertTrue(capturedEpgWindowSec >= 13L * 86_400L)
            val newId = client.addDvrEntry(999L)
            assertEquals(777L, newId)
            client.updateDvrEntry(777L, playPositionSec = 123L, playCount = 1)
            client.stopDvrEntry(777L)
            client.cancelDvrEntry(777L)
            client.deleteDvrEntry(777L)
            assertEquals("/dvrfile/777", client.getDvrTicket(777L).path)

            client.disconnect()
            scope.cancel()
            serverTask.await()
            assertTrue(capturedMethods.containsAll(listOf("addDvrEntry", "updateDvrEntry", "stopDvrEntry", "cancelDvrEntry", "deleteDvrEntry", "getTicket")))
        }
    }

    @Test
    fun `HtspVfsClient opens stats reads at growing head and closes file`() = runBlocking {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val fileClosed = java.util.concurrent.atomic.AtomicBoolean(false)
            val fileBytes = java.util.concurrent.atomic.AtomicReference("FIRST".toByteArray())

            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())

                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 44)
                                put("servername", "MockTvh")
                                put("serverversion", "4.3-mock")
                            },
                        ),
                    )
                    output.flush()

                    while (true) {
                        val req = try {
                            HtsMsgCodec.readFrame(input)
                        } catch (_: Exception) {
                            break
                        }
                        val seq = req.getLong("seq")!!
                        val reply = when (req.getString("method")) {
                            "fileOpen" -> {
                                assertEquals("dvrfile/55", req.getString("file"))
                                htsMap {
                                    put("seq", seq)
                                    put("id", 900L)
                                    put("size", fileBytes.get().size.toLong())
                                    put("mtime", 1000L)
                                }
                            }
                            "fileRead" -> {
                                val off = (req.getLong("offset") ?: 0L).toInt()
                                val sz = (req.getLong("size") ?: 0L).toInt()
                                val current = fileBytes.get()
                                val slice = if (off >= current.size) {
                                    ByteArray(0)
                                } else {
                                    current.copyOfRange(off, minOf(current.size, off + sz))
                                }
                                htsMap {
                                    put("seq", seq)
                                    put("data", slice)
                                }
                            }
                            "fileStat" -> htsMap {
                                put("seq", seq)
                                put("size", fileBytes.get().size.toLong())
                                put("mtime", 1001L)
                            }
                            "fileClose" -> {
                                fileClosed.set(true)
                                htsMap { put("seq", seq) }
                            }
                            else -> htsMap { put("seq", seq) }
                        }
                        output.write(HtsMsgCodec.encodeFrame(reply))
                        output.flush()
                        if (fileClosed.get()) break
                    }
                }
            }

            val vfs = HtspVfsClient(
                profileProvider = { ServerProfile(host = "127.0.0.1", htspPort = server.localPort) },
                idleDisconnectDelayMs = 0L,
            )

            try {
                val handle = vfs.openFile("dvrfile/55")
                assertEquals(5L, handle.initialSize)
                assertEquals("FIRST", handle.read(0L, 64).decodeToString())
                assertEquals(0, handle.read(5L, 64).size)

                fileBytes.set("FIRSTSECOND".toByteArray())
                assertEquals(11L, handle.statSize())
                assertEquals("SECOND", handle.read(5L, 64).decodeToString())

                handle.closeSuspend()
                assertTrue(fileClosed.get())
            } finally {
                vfs.close()
                serverTask.await()
            }
        }
    }

    @Test
    fun `resync reconnects and waits for a fresh metadata sync`() = runBlocking {
        val syncCount = AtomicInteger(0)

        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                repeat(2) {
                    server.accept().use { sock ->
                        val input = BufferedInputStream(sock.getInputStream())
                        val output = BufferedOutputStream(sock.getOutputStream())

                        val hello = HtsMsgCodec.readFrame(input)
                        output.write(
                            HtsMsgCodec.encodeFrame(
                                htsMap {
                                    put("seq", hello.getLong("seq")!!)
                                    put("htspversion", 44)
                                },
                            ),
                        )
                        output.flush()

                        val sync = HtsMsgCodec.readFrame(input)
                        assertEquals("enableAsyncMetadata", sync.getString("method"))
                        syncCount.incrementAndGet()
                        output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                        output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                        output.flush()

                        // Answer the clock-offset RPC, then keep reading until the client closes.
                        try {
                            while (true) {
                                val req = HtsMsgCodec.readFrame(input)
                                if (req.getString("method") == "getSysTime") {
                                    output.write(
                                        HtsMsgCodec.encodeFrame(
                                            htsMap {
                                                put("seq", req.getLong("seq")!!)
                                                put("time", System.currentTimeMillis() / 1000L)
                                            },
                                        ),
                                    )
                                    output.flush()
                                }
                            }
                        } catch (_: Exception) {
                            // Client closed the connection for the resync.
                        }
                    }
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HtspClient(scope = scope)
            try {
                client.connect(ServerProfile(host = "127.0.0.1", htspPort = server.localPort))
                withTimeout(5_000) {
                    client.connectionState.first { it is ConnectionState.Connected }
                }
                assertEquals(1, syncCount.get())

                assertTrue(client.resync())

                assertEquals(2, syncCount.get())
                assertTrue(client.connectionState.value is ConnectionState.Connected)
            } finally {
                client.disconnect()
                scope.cancel()
                serverTask.await()
            }
        }
    }

    @Test
    fun `initial sync timeout triggers reconnect instead of killing connectionJob`() = runBlocking {
        val attempts = AtomicInteger(0)
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                // Attempt 1: answer hello & enableAsyncMetadata, omit initialSyncCompleted so sync times out.
                server.accept().use { sock ->
                    attempts.incrementAndGet()
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                    output.flush()
                    val sync = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                    output.flush()
                    // Keep reading until client closes after timeout
                    runCatching { while (true) HtsMsgCodec.readFrame(input) }
                }

                // Attempt 2: complete initial sync normally.
                server.accept().use { sock ->
                    attempts.incrementAndGet()
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                    output.flush()
                    val sync = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                    output.flush()
                    runCatching {
                        while (true) {
                            val req = HtsMsgCodec.readFrame(input)
                            if (req.getString("method") == "getSysTime") {
                                output.write(
                                    HtsMsgCodec.encodeFrame(
                                        htsMap {
                                            put("seq", req.getLong("seq")!!)
                                            put("time", System.currentTimeMillis() / 1000L)
                                        },
                                    ),
                                )
                                output.flush()
                            }
                        }
                    }
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HtspClient(
                scope = scope,
                rpcTimeoutMs = 200L,
                initialSyncTimeoutMs = 150L,
            )
            try {
                client.connect(ServerProfile(host = "127.0.0.1", htspPort = server.localPort))
                withTimeout(5_000) {
                    client.connectionState.first { it is ConnectionState.Connected }
                }
                assertEquals(2, attempts.get())
            } finally {
                client.disconnect()
                scope.cancel()
                serverTask.await()
            }
        }
    }

    @Test
    fun `heartbeat timeout tears down half-open socket and reconnects`() = runBlocking {
        val syncCount = AtomicInteger(0)
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                // Connection 1: complete sync and initial getSysTime, then drop heartbeat getSysTime.
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                    output.flush()
                    val sync = HtsMsgCodec.readFrame(input)
                    syncCount.incrementAndGet()
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                    output.flush()

                    // Answer first getSysTime (clock offset), ignore subsequent heartbeat getSysTime
                    val firstTime = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", firstTime.getLong("seq")!!)
                                put("time", System.currentTimeMillis() / 1000L)
                            },
                        ),
                    )
                    output.flush()
                    runCatching { while (true) HtsMsgCodec.readFrame(input) }
                }

                // Connection 2: complete sync cleanly.
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                    output.flush()
                    val sync = HtsMsgCodec.readFrame(input)
                    syncCount.incrementAndGet()
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                    output.flush()
                    runCatching {
                        while (true) {
                            val req = HtsMsgCodec.readFrame(input)
                            if (req.getString("method") == "getSysTime") {
                                output.write(
                                    HtsMsgCodec.encodeFrame(
                                        htsMap {
                                            put("seq", req.getLong("seq")!!)
                                            put("time", System.currentTimeMillis() / 1000L)
                                        },
                                    ),
                                )
                                output.flush()
                            }
                        }
                    }
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HtspClient(
                scope = scope,
                heartbeatIntervalMs = 60L,
                rpcTimeoutMs = 150L,
            )
            try {
                client.connect(ServerProfile(host = "127.0.0.1", htspPort = server.localPort))
                withTimeout(5_000) {
                    client.connectionState.first { it is ConnectionState.Connected }
                }
                // Wait for connection #2 to sync after connection #1's heartbeat times out
                withTimeout(5_000) {
                    while (syncCount.get() < 2) {
                        delay(20L)
                    }
                    client.connectionState.first { it is ConnectionState.Connected }
                }
                assertEquals(2, syncCount.get())
            } finally {
                client.disconnect()
                scope.cancel()
                serverTask.await()
            }
        }
    }

    @Test
    fun `disconnect keeps Disconnected state without socket error overwrite`() = runBlocking {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                    output.flush()
                    val sync = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                    output.flush()
                    runCatching {
                        while (true) {
                            val req = HtsMsgCodec.readFrame(input)
                            if (req.getString("method") == "getSysTime") {
                                output.write(
                                    HtsMsgCodec.encodeFrame(
                                        htsMap {
                                            put("seq", req.getLong("seq")!!)
                                            put("time", System.currentTimeMillis() / 1000L)
                                        },
                                    ),
                                )
                                output.flush()
                            }
                        }
                    }
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HtspClient(scope = scope)
            try {
                client.connect(ServerProfile(host = "127.0.0.1", htspPort = server.localPort))
                withTimeout(5_000) {
                    client.connectionState.first { it is ConnectionState.Connected }
                }
                client.disconnect()
                delay(200L)
                assertEquals(ConnectionState.Disconnected, client.connectionState.value)
            } finally {
                client.disconnect()
                scope.cancel()
                serverTask.await()
            }
        }
    }

    @Test
    fun `testConnection reuses HtspSession handshake and reports access flags and auth errors`() = runBlocking {
        val challenge = ByteArray(32) { 7 }
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                // 1. Successful auth with streaming + dvr RPCs granted (Tvheadend returns {} on auth)
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 44)
                                put("servername", "MockTvh")
                                put("serverversion", "4.3")
                                put("challenge", challenge)
                            },
                        ),
                    )
                    output.flush()
                    val auth = HtsMsgCodec.readFrame(input)
                    assertEquals("alice", auth.getString("username"))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", auth.getLong("seq")!!) }))
                    output.flush()

                    while (true) {
                        val req = runCatching { HtsMsgCodec.readFrame(input) }.getOrNull() ?: break
                        val seq = req.getLong("seq")!!
                        val reply = when (req.getString("method")) {
                            "getSysTime" -> htsMap {
                                put("seq", seq)
                                put("time", System.currentTimeMillis() / 1000L)
                            }
                            "api" -> {
                                if (req.getString("path") == "profile/list") {
                                    htsMap {
                                        put("seq", seq)
                                        put(
                                            "response",
                                            htsMap {
                                                put(
                                                    "entries",
                                                    listOf(
                                                        HtsValue.MapVal(htsMap { put("key", "uuid-pass"); put("val", "pass") }),
                                                        HtsValue.MapVal(htsMap { put("key", "uuid-htsp"); put("val", "htsp") }),
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                } else {
                                    htsMap { put("seq", seq) }
                                }
                            }
                            "getDvrConfigs" -> htsMap {
                                put("seq", seq)
                                put("dvrconfigs", emptyList<HtsValue>())
                            }
                            "getDiskSpace" -> htsMap {
                                put("seq", seq)
                                put("freediskspace", 50_000_000_000L)
                                put("totaldiskspace", 100_000_000_000L)
                            }
                            else -> htsMap { put("seq", seq) }
                        }
                        output.write(HtsMsgCodec.encodeFrame(reply))
                        output.flush()
                    }
                }
                // 2. Auth failure
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 44)
                                put("challenge", challenge)
                            },
                        ),
                    )
                    output.flush()
                    val auth = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", auth.getLong("seq")!!)
                                put("noaccess", 1L)
                            },
                        ),
                    )
                    output.flush()
                }
            }

            val okResult = HtspClient.testConnection(
                ServerProfile(host = "127.0.0.1", htspPort = server.localPort, username = "alice", password = "secret"),
            )
            assertTrue(okResult is ConnectionTestResult.Success)
            okResult as ConnectionTestResult.Success
            assertEquals("MockTvh", okResult.serverName)
            assertEquals("4.3", okResult.serverVersion)
            assertTrue(okResult.hasDvrAccess)
            assertTrue(okResult.hasStreamingAccess)

            val failResult = HtspClient.testConnection(
                ServerProfile(host = "127.0.0.1", htspPort = server.localPort, username = "alice", password = "wrong"),
            )
            assertTrue(failResult is ConnectionTestResult.Failure)
            failResult as ConnectionTestResult.Failure
            assertTrue(failResult.isAuthError)

            serverTask.await()
        }
    }

    @Test
    fun `probeServer detects streaming denial read-only DVR missing pass profile and older HTSP`() = runBlocking {
        val challenge = ByteArray(32) { 9 }
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                // 1. Read-only user on older HTSP v24 without 'pass' profile
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 24)
                                put("servername", "Tvheadend")
                                put("serverversion", "4.2.8")
                                put("challenge", challenge)
                                put("servercapability", listOf(HtsValue.Str("timeshift")))
                            },
                        ),
                    )
                    output.flush()
                    val auth = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", auth.getLong("seq")!!) }))
                    output.flush()

                    while (true) {
                        val req = runCatching { HtsMsgCodec.readFrame(input) }.getOrNull() ?: break
                        val seq = req.getLong("seq")!!
                        val reply = when (req.getString("method")) {
                            "getSysTime" -> htsMap {
                                put("seq", seq)
                                put("time", (System.currentTimeMillis() / 1000L) + 120L)
                            }
                            "api" -> {
                                if (req.getString("path") == "profile/list") {
                                    htsMap {
                                        put("seq", seq)
                                        put(
                                            "response",
                                            htsMap {
                                                put(
                                                    "entries",
                                                    listOf(
                                                        HtsValue.MapVal(htsMap { put("key", "uuid-htsp"); put("val", "htsp") }),
                                                        HtsValue.MapVal(htsMap { put("key", "uuid-mkv"); put("val", "matroska") }),
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                } else {
                                    htsMap { put("seq", seq) }
                                }
                            }
                            "getDvrConfigs" -> htsMap {
                                put("seq", seq)
                                put("noaccess", 1L)
                            }
                            else -> htsMap { put("seq", seq) }
                        }
                        output.write(HtsMsgCodec.encodeFrame(reply))
                        output.flush()
                    }
                }
                // 2. Streaming permission denied
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 44)
                                put("challenge", challenge)
                            },
                        ),
                    )
                    output.flush()
                    val auth = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", auth.getLong("seq")!!) }))
                    output.flush()
                    while (true) {
                        val req = runCatching { HtsMsgCodec.readFrame(input) }.getOrNull() ?: break
                        val seq = req.getLong("seq")!!
                        val reply = when (req.getString("method")) {
                            "getSysTime", "getDvrConfigs", "getDiskSpace" -> htsMap {
                                put("seq", seq)
                                put("noaccess", 1L)
                            }
                            else -> htsMap { put("seq", seq) }
                        }
                        output.write(HtsMsgCodec.encodeFrame(reply))
                        output.flush()
                    }
                }
            }

            val readOnlyProbe = HtspClient.probeServer(
                ServerProfile(host = "127.0.0.1", htspPort = server.localPort, username = "viewer", password = "pw"),
            )
            assertTrue(readOnlyProbe.connected)
            assertTrue(readOnlyProbe.authSucceeded)
            assertEquals(24, readOnlyProbe.htspVersion)
            assertEquals(listOf("timeshift"), readOnlyProbe.serverCapabilities)
            assertEquals(ProbeOutcome.GRANTED, readOnlyProbe.streamingOutcome)
            assertEquals(ProbeOutcome.NO_ACCESS, readOnlyProbe.dvrOutcome)
            assertEquals(false, readOnlyProbe.passProfilePresent)
            assertEquals(listOf("htsp", "matroska"), readOnlyProbe.availableProfiles)
            assertTrue((readOnlyProbe.clockOffsetMs ?: 0L) > 60_000L)

            val noStreamProbe = HtspClient.probeServer(
                ServerProfile(host = "127.0.0.1", htspPort = server.localPort, username = "locked", password = "pw"),
            )
            assertTrue(noStreamProbe.connected)
            assertTrue(noStreamProbe.authSucceeded)
            assertEquals(ProbeOutcome.NO_ACCESS, noStreamProbe.streamingOutcome)
            assertEquals(ProbeOutcome.NO_ACCESS, noStreamProbe.dvrOutcome)

            serverTask.await()
        }
    }

    @Test
    fun `probeHello reports reachability and handshake metadata`() = runBlocking {
        val port = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    assertEquals("hello", hello.getString("method"))
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("seq", hello.getLong("seq")!!)
                                put("htspversion", 44)
                                put("servername", "MuxGrid Box")
                                put("serverversion", "4.3-2375")
                            },
                        ),
                    )
                    output.flush()
                }
            }

            val ok = HtspClient.probeHello("127.0.0.1", server.localPort)
            assertTrue(ok.reachable)
            assertEquals("MuxGrid Box", ok.serverName)
            assertEquals("4.3-2375", ok.serverVersion)
            assertEquals(44, ok.htspVersion)

            serverTask.await()
            server.localPort
        }

        val closed = HtspClient.probeHello("127.0.0.1", port)
        assertFalse(closed.reachable)
    }

    @Test
    fun `HtspVfsClient keeps session open across overlapping open and close handles`() = runBlocking {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val connectionCount = AtomicInteger(0)
            val closedIds = mutableListOf<Long>()
            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    connectionCount.incrementAndGet()
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                    output.flush()

                    var nextHandle = 100L
                    while (true) {
                        val req = runCatching { HtsMsgCodec.readFrame(input) }.getOrNull() ?: break
                        val seq = req.getLong("seq")!!
                        val reply = when (req.getString("method")) {
                            "fileOpen" -> htsMap {
                                put("seq", seq)
                                put("id", nextHandle++)
                                put("size", 10L)
                                put("mtime", 1L)
                            }
                            "fileStat" -> htsMap {
                                put("seq", seq)
                                put("size", 10L)
                                put("mtime", 1L)
                            }
                            "fileClose" -> {
                                closedIds.add(req.getLong("id")!!)
                                htsMap { put("seq", seq) }
                            }
                            else -> htsMap { put("seq", seq) }
                        }
                        output.write(HtsMsgCodec.encodeFrame(reply))
                        output.flush()
                    }
                }
            }

            val vfs = HtspVfsClient(
                profileProvider = { ServerProfile(host = "127.0.0.1", htspPort = server.localPort) },
                idleDisconnectDelayMs = 0L,
            )
            try {
                val h1 = vfs.openFile("dvrfile/1")
                val h2 = vfs.openFile("dvrfile/2")
                h1.closeSuspend()
                // Second handle must still operate on the same open session.
                assertEquals(10L, h2.statSize())
                assertEquals(1, connectionCount.get())
                h2.closeSuspend()
                assertEquals(listOf(100L, 101L), closedIds)
                assertFalse(closedIds.isEmpty())
            } finally {
                vfs.close()
                serverTask.await()
            }
        }
    }

    @Test
    fun `HtspSession rpc overwrites any caller method or seq keys in params`() = runBlocking {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val req = HtsMsgCodec.readFrame(input)
                    assertEquals("hello", req.getString("method"))
                    assertEquals(1L, req.getLong("seq"))
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", 1L); put("ok", 1L) }))
                    output.flush()
                }
            }

            val sock = java.net.Socket("127.0.0.1", server.localPort)
            val session = HtspSession(sock, AtomicLong(1L))
            val wJob = launch(Dispatchers.IO) { session.writeLoop() }
            val rJob = launch(Dispatchers.IO) { runCatching { session.readLoop() } }
            try {
                val reply = session.rpc(
                    "hello",
                    htsMap {
                        put("method", "spoofed")
                        put("seq", 999L)
                    },
                    2_000L,
                )
                assertEquals(1L, reply.getLong("ok"))
            } finally {
                wJob.cancel()
                rJob.cancel()
                session.close()
                serverTask.await()
            }
        }
    }

    @Test
    fun `awaitConsumerSyncAck keeps Syncing until consumer calls acknowledgeInitialSync`() = runBlocking {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val serverTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    val input = BufferedInputStream(sock.getInputStream())
                    val output = BufferedOutputStream(sock.getOutputStream())
                    val hello = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", hello.getLong("seq")!!); put("htspversion", 44) }))
                    output.flush()
                    val sync = HtsMsgCodec.readFrame(input)
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("seq", sync.getLong("seq")!!) }))
                    output.write(
                        HtsMsgCodec.encodeFrame(
                            htsMap {
                                put("method", "channelAdd")
                                put("channelId", 1L)
                                put("channelNumber", 1)
                                put("channelName", "M1 HD")
                            },
                        ),
                    )
                    output.write(HtsMsgCodec.encodeFrame(htsMap { put("method", "initialSyncCompleted") }))
                    output.flush()

                    runCatching {
                        while (true) {
                            val req = HtsMsgCodec.readFrame(input)
                            if (req.getString("method") == "getSysTime") {
                                output.write(
                                    HtsMsgCodec.encodeFrame(
                                        htsMap {
                                            put("seq", req.getLong("seq")!!)
                                            put("time", System.currentTimeMillis() / 1000L)
                                        },
                                    ),
                                )
                                output.flush()
                            }
                        }
                    }
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HtspClient(scope = scope, awaitConsumerSyncAck = true)
            val syncEventSeen = kotlinx.coroutines.CompletableDeferred<Unit>()
            val allowAck = kotlinx.coroutines.CompletableDeferred<Unit>()
            val collector = scope.launch {
                client.events.collect { event ->
                    if (event is HtspPushEvent.InitialSyncCompleted) {
                        syncEventSeen.complete(Unit)
                        allowAck.await()
                        client.acknowledgeInitialSync()
                    }
                }
            }
            try {
                client.connect(ServerProfile(host = "127.0.0.1", htspPort = server.localPort))
                withTimeout(5_000) {
                    syncEventSeen.await()
                }
                delay(100L)
                assertTrue(client.connectionState.value is ConnectionState.Syncing)

                allowAck.complete(Unit)
                withTimeout(5_000) {
                    client.connectionState.first { it is ConnectionState.Connected }
                }
            } finally {
                allowAck.complete(Unit)
                collector.cancel()
                client.disconnect()
                scope.cancel()
                serverTask.await()
            }
        }
    }
}
