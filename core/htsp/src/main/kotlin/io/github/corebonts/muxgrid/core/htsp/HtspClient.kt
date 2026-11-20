package io.github.corebonts.muxgrid.core.htsp

import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.ConnectionTestResult
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class HtspClient(
    private val scope: CoroutineScope,
    private val epgWindowSeconds: Long = DEFAULT_EPG_WINDOW_SEC,
    private val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val eventChannel = Channel<HtspPushEvent>(Channel.UNLIMITED)
    val events: Flow<HtspPushEvent> = eventChannel.receiveAsFlow()

    private val seqGenerator = AtomicLong(1L)
    private var connectionJob: Job? = null

    @Volatile
    private var activeSession: Session? = null

    fun connect(profile: ServerProfile) {
        connectionJob?.cancel()
        activeSession?.close()
        activeSession = null

        if (!profile.isConfigured) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        connectionJob = scope.launch(Dispatchers.IO) {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                var stopRetrying = false
                try {
                    _connectionState.value = ConnectionState.Connecting
                    runSession(profile) {
                        // Reset backoff once initial sync completes
                        backoffMs = INITIAL_BACKOFF_MS
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HtspAuthException) {
                    _connectionState.value = ConnectionState.Error(
                        message = e.message ?: "Authentication failed",
                        isAuthError = true,
                    )
                    stopRetrying = true
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error(
                        message = e.message ?: "Connection lost",
                        isAuthError = false,
                    )
                } finally {
                    activeSession?.close()
                    activeSession = null
                }

                if (stopRetrying || !isActive) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        activeSession?.close()
        activeSession = null
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun getTicket(channelId: Long): TicketResult {
        val reply = rpc(
            "getTicket",
            htsMap {
                put("channelId", channelId)
            },
        )
        val path = reply.getString("path") ?: throw HtspException("Missing path in getTicket reply")
        val ticket = reply.getString("ticket") ?: throw HtspException("Missing ticket in getTicket reply")
        return TicketResult(path = path, ticket = ticket)
    }

    suspend fun getDvrTicket(dvrId: Long): TicketResult {
        val reply = rpc(
            "getTicket",
            htsMap {
                put("dvrId", dvrId)
            },
        )
        val path = reply.getString("path") ?: throw HtspException("Missing path in getTicket reply")
        val ticket = reply.getString("ticket") ?: throw HtspException("Missing ticket in getTicket reply")
        return TicketResult(path = path, ticket = ticket)
    }

    suspend fun getSysTime(): SysTimeResult {
        val reply = rpc("getSysTime", htsMap {})
        val time = reply.getLong("time") ?: throw HtspException("Missing time in getSysTime reply")
        return SysTimeResult(
            timeEpochSec = time,
            timezone = reply.getString("timezone"),
            gmtOffsetSec = reply.getLong("gmtoffset") ?: 0L,
        )
    }

    suspend fun fileOpen(file: String): HtspFileInfo {
        val reply = rpc(
            "fileOpen",
            htsMap {
                put("file", file)
            },
        )
        val id = reply.getLong("id") ?: throw HtspException("Missing handle id in fileOpen reply")
        return HtspFileInfo(
            handleId = id,
            size = reply.getLong("size") ?: 0L,
            mtime = reply.getLong("mtime") ?: 0L,
        )
    }

    suspend fun fileRead(handleId: Long, size: Long, offset: Long): ByteArray {
        val reply = rpc(
            "fileRead",
            htsMap {
                put("id", handleId)
                put("size", size)
                put("offset", offset)
            },
        )
        return reply.getBytes("data") ?: ByteArray(0)
    }

    suspend fun fileClose(handleId: Long) {
        rpc(
            "fileClose",
            htsMap {
                put("id", handleId)
            },
        )
    }

    private suspend fun rpc(method: String, params: HtsMap, timeoutMs: Long = RPC_TIMEOUT_MS): HtsMap {
        val session = activeSession ?: throw HtspException("HTSP is not connected")
        return session.rpc(method, params, timeoutMs)
    }

    private suspend fun runSession(
        profile: ServerProfile,
        onSyncCompleted: () -> Unit,
    ) = coroutineScope {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.connect(InetSocketAddress(profile.host, profile.htspPort), CONNECT_TIMEOUT_MS)

        val session = Session(socket)
        activeSession = session

        val initialSyncDeferred = CompletableDeferred<Unit>()
        var negotiatedVersion = CLIENT_HTSP_VERSION
        var serverName = "Tvheadend"
        var serverVersion = ""

        val writerJob = launch(Dispatchers.IO) {
            session.writeLoop()
        }
        var readFailure: Throwable? = null
        val readerJob = launch(Dispatchers.IO) {
            try {
                session.readLoop { method, msg ->
                    val event = HtspMessageParser.parsePushEvent(method, msg)
                    if (event != null) {
                        eventChannel.trySend(event)
                        if (event is HtspPushEvent.InitialSyncCompleted) {
                            initialSyncDeferred.complete(Unit)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                readFailure = e
                initialSyncDeferred.completeExceptionally(e)
            }
        }

        try {
            val helloReply = session.rpc(
                "hello",
                htsMap {
                    put("htspversion", CLIENT_HTSP_VERSION)
                    put("clientname", CLIENT_NAME)
                    put("clientversion", CLIENT_VERSION)
                },
                RPC_TIMEOUT_MS,
            )
            val serverHtspVersion = helloReply.getInt("htspversion") ?: CLIENT_HTSP_VERSION
            negotiatedVersion = min(CLIENT_HTSP_VERSION, serverHtspVersion)
            serverName = helloReply.getString("servername") ?: "Tvheadend"
            serverVersion = helloReply.getString("serverversion") ?: ""
            val challenge = helloReply.getBytes("challenge")

            if (profile.username.isNotBlank() || profile.password.isNotBlank()) {
                val authParams = htsMap {
                    put("username", profile.username)
                    if (challenge != null) {
                        put("digest", computeSha1Digest(profile.password, challenge))
                    }
                }
                val authReply = try {
                    session.rpc("authenticate", authParams, RPC_TIMEOUT_MS)
                } catch (e: HtspNoAccessException) {
                    throw HtspAuthException("Invalid username or password")
                }
                if ((authReply.getLong("noaccess") ?: 0L) != 0L) {
                    throw HtspAuthException("Invalid username or password")
                }
            }

            _connectionState.value = ConnectionState.Syncing(
                serverName = serverName,
                serverVersion = serverVersion,
                htspVersion = negotiatedVersion,
            )

            eventChannel.trySend(HtspPushEvent.SyncStarting)

            val epgMaxTime = (System.currentTimeMillis() / 1000L) + epgWindowSeconds
            session.rpc(
                "enableAsyncMetadata",
                htsMap {
                    put("epg", 1)
                    put("epgMaxTime", epgMaxTime)
                },
                RPC_TIMEOUT_MS,
            )

            withTimeout(INITIAL_SYNC_TIMEOUT_MS) {
                initialSyncDeferred.await()
            }
            onSyncCompleted()

            var clockOffsetMs = fetchClockOffsetOrZero(session)
            _connectionState.value = ConnectionState.Connected(
                serverName = serverName,
                serverVersion = serverVersion,
                htspVersion = negotiatedVersion,
                serverClockOffsetMs = clockOffsetMs,
            )

            // Heartbeat loop
            val heartbeatJob = launch(Dispatchers.IO) {
                while (isActive) {
                    delay(heartbeatIntervalMs)
                    val sysTime = session.rpc("getSysTime", htsMap {}, RPC_TIMEOUT_MS)
                    val serverSec = sysTime.getLong("time")
                    if (serverSec != null) {
                        clockOffsetMs = (serverSec * 1000L) - System.currentTimeMillis()
                        _connectionState.value = ConnectionState.Connected(
                            serverName = serverName,
                            serverVersion = serverVersion,
                            htspVersion = negotiatedVersion,
                            serverClockOffsetMs = clockOffsetMs,
                        )
                    }
                }
            }

            // Wait for reader to finish (disconnect/error) or heartbeat to throw
            try {
                readerJob.join()
                readFailure?.let { throw it }
            } finally {
                heartbeatJob.cancel()
            }
        } finally {
            writerJob.cancel()
            readerJob.cancel()
            session.close()
        }
    }

    private suspend fun fetchClockOffsetOrZero(session: Session): Long = try {
        val sysTime = session.rpc("getSysTime", htsMap {}, RPC_TIMEOUT_MS)
        val serverSec = sysTime.getLong("time") ?: return 0L
        (serverSec * 1000L) - System.currentTimeMillis()
    } catch (_: Exception) {
        0L
    }

    private inner class Session(
        private val socket: Socket,
    ) {
        private val input = BufferedInputStream(socket.getInputStream(), 65536)
        private val output = BufferedOutputStream(socket.getOutputStream(), 16384)
        private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
        private val pending = ConcurrentHashMap<Long, CompletableDeferred<HtsMap>>()

        @Volatile
        private var closed = false

        suspend fun rpc(method: String, params: HtsMap, timeoutMs: Long): HtsMap {
            if (closed) throw HtspException("HTSP session closed")
            val seq = seqGenerator.getAndIncrement()
            val deferred = CompletableDeferred<HtsMap>()
            pending[seq] = deferred

            val request = htsMap {
                put("method", method)
                put("seq", seq)
                for ((k, v) in params) {
                    when (v) {
                        is HtsValue.S64 -> put(k, v.value)
                        is HtsValue.Str -> put(k, v.value)
                        is HtsValue.Bin -> put(k, v.value)
                        is HtsValue.Bool -> put(k, v.value)
                        is HtsValue.MapVal -> put(k, v.value)
                        is HtsValue.ListVal -> put(k, v.items)
                        is HtsValue.Uuid -> putUuid(k, v.bytes)
                    }
                }
            }
            val frame = HtsMsgCodec.encodeFrame(request)
            if (writeQueue.trySend(frame).isFailure) {
                pending.remove(seq)
                throw HtspException("Failed to enqueue HTSP RPC '$method'")
            }

            return try {
                val reply = withTimeout(timeoutMs) { deferred.await() }
                if ((reply.getLong("noaccess") ?: 0L) != 0L) {
                    throw HtspNoAccessException("Access denied for HTSP method '$method'")
                }
                val error = reply.getString("error")
                if (!error.isNullOrBlank()) {
                    throw HtspException("HTSP error for '$method': $error")
                }
                reply
            } finally {
                pending.remove(seq)
            }
        }

        suspend fun writeLoop() {
            try {
                for (frame in writeQueue) {
                    output.write(frame)
                    // Drain any immediately queued frames before flushing
                    while (true) {
                        val next = writeQueue.tryReceive().getOrNull() ?: break
                        output.write(next)
                    }
                    output.flush()
                }
            } catch (_: Exception) {
                close()
            }
        }

        fun readLoop(onPush: (String, HtsMap) -> Unit) {
            try {
                while (!closed && !socket.isClosed) {
                    val msg = HtsMsgCodec.readFrame(input)
                    val seq = msg.getLong("seq")
                    if (seq != null) {
                        pending.remove(seq)?.complete(msg)
                    } else {
                        val method = msg.getString("method")
                        if (!method.isNullOrEmpty()) {
                            onPush(method, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                failAllPending(e)
                throw e
            }
        }

        private fun failAllPending(cause: Throwable) {
            val entries = pending.entries.toList()
            for ((seq, deferred) in entries) {
                pending.remove(seq)
                deferred.completeExceptionally(cause)
            }
        }

        fun close() {
            if (closed) return
            closed = true
            writeQueue.close()
            failAllPending(HtspException("HTSP session closed"))
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    companion object {
        const val CLIENT_HTSP_VERSION = 44
        const val CLIENT_NAME = "MuxGrid"
        const val CLIENT_VERSION = "0.1.0"

        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val RPC_TIMEOUT_MS = 10_000L
        private const val INITIAL_SYNC_TIMEOUT_MS = 30_000L
        private const val DEFAULT_EPG_WINDOW_SEC = 2L * 86_400L
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000L
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 15_000L

        fun computeSha1Digest(password: String, challenge: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-1")
            md.update(password.toByteArray(StandardCharsets.UTF_8))
            md.update(challenge)
            return md.digest()
        }

        suspend fun testConnection(profile: ServerProfile): ConnectionTestResult =
            withContext(Dispatchers.IO) {
                if (!profile.isConfigured) {
                    return@withContext ConnectionTestResult.Failure("Host and valid ports are required")
                }
                val socket = Socket()
                try {
                    socket.tcpNoDelay = true
                    socket.soTimeout = CONNECT_TIMEOUT_MS
                    socket.connect(InetSocketAddress(profile.host, profile.htspPort), CONNECT_TIMEOUT_MS)
                    val input = BufferedInputStream(socket.getInputStream())
                    val output = BufferedOutputStream(socket.getOutputStream())

                    val helloReq = htsMap {
                        put("method", "hello")
                        put("seq", 1)
                        put("htspversion", CLIENT_HTSP_VERSION)
                        put("clientname", CLIENT_NAME)
                        put("clientversion", CLIENT_VERSION)
                    }
                    output.write(HtsMsgCodec.encodeFrame(helloReq))
                    output.flush()

                    val helloReply = HtsMsgCodec.readFrame(input)
                    val serverVersionNum = helloReply.getInt("htspversion") ?: CLIENT_HTSP_VERSION
                    val negotiated = min(CLIENT_HTSP_VERSION, serverVersionNum)
                    val serverName = helloReply.getString("servername") ?: "Tvheadend"
                    val serverVer = helloReply.getString("serverversion") ?: ""
                    val challenge = helloReply.getBytes("challenge")

                    var hasStreaming = true
                    var hasDvr = false
                    if (profile.username.isNotBlank() || profile.password.isNotBlank()) {
                        val authReq = htsMap {
                            put("method", "authenticate")
                            put("seq", 2)
                            put("username", profile.username)
                            if (challenge != null) {
                                put("digest", computeSha1Digest(profile.password, challenge))
                            }
                        }
                        output.write(HtsMsgCodec.encodeFrame(authReq))
                        output.flush()
                        val authReply = HtsMsgCodec.readFrame(input)
                        if ((authReply.getLong("noaccess") ?: 0L) != 0L) {
                            return@withContext ConnectionTestResult.Failure(
                                message = "Authentication failed: check username or password",
                                isAuthError = true,
                            )
                        }
                        hasStreaming = (authReply.getLong("streaming") ?: 1L) != 0L
                        hasDvr = (authReply.getLong("dvr") ?: 0L) != 0L
                    }

                    ConnectionTestResult.Success(
                        serverName = serverName,
                        serverVersion = serverVer,
                        htspVersion = negotiated,
                        hasDvrAccess = hasDvr,
                        hasStreamingAccess = hasStreaming,
                    )
                } catch (e: Exception) {
                    ConnectionTestResult.Failure(
                        message = e.message ?: "Unable to connect to ${profile.host}:${profile.htspPort}",
                        isAuthError = false,
                    )
                } finally {
                    try {
                        socket.close()
                    } catch (_: IOException) {
                    }
                }
            }
    }
}
