package io.github.corebonts.muxgrid.core.htsp

import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.ConnectionTestResult
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Persistent TCP client for Tvheadend's HTSP binary protocol (`9982/tcp`).
 *
 * Handles handshake (`hello` + SHA-1 `authenticate`), `enableAsyncMetadata` push sync,
 * periodic `getSysTime` heartbeats with clock-offset estimation, automatic reconnect with
 * exponential backoff, and serialized control/DVR RPCs.
 */
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
    private var activeSession: HtspSession? = null

    @Volatile
    private var lastProfile: ServerProfile? = null

    @Volatile
    var suppressTransientErrors: Boolean = false

    fun connect(profile: ServerProfile) {
        lastProfile = profile
        connectionJob?.cancel()
        activeSession?.close()
        activeSession = null

        if (!profile.isConfigured) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        // Publish Connecting synchronously so callers waiting on a resync never
        // observe the previous (stale) Connected state.
        _connectionState.value = ConnectionState.Connecting

        connectionJob = scope.launch(Dispatchers.IO) {
            var backoffMs = INITIAL_BACKOFF_MS
            var hadSuccessfulSync = false
            var consecutiveFailures = 0
            while (isActive) {
                var stopRetrying = false
                var fastRetry = false
                try {
                    _connectionState.value = ConnectionState.Connecting
                    runSession(profile) {
                        // Reset backoff once initial sync completes
                        backoffMs = INITIAL_BACKOFF_MS
                        hadSuccessfulSync = true
                        consecutiveFailures = 0
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
                    consecutiveFailures++
                    val isFirstDropAfterSync = hadSuccessfulSync && consecutiveFailures == 1
                    if (suppressTransientErrors || isFirstDropAfterSync) {
                        _connectionState.value = ConnectionState.Connecting
                        fastRetry = isFirstDropAfterSync && !suppressTransientErrors
                    } else {
                        _connectionState.value = ConnectionState.Error(
                            message = e.message ?: "Connection lost",
                            isAuthError = false,
                        )
                    }
                } finally {
                    activeSession?.close()
                    activeSession = null
                }

                if (stopRetrying || !isActive) break
                if (fastRetry) {
                    delay(400L)
                } else {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
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

    /**
     * Reconnects and waits for a fresh metadata sync.
     *
     * HTSP has no "list DVR entries" RPC: the server only sends a full snapshot in
     * response to `enableAsyncMetadata`, which is tied to a connection. Reconnecting
     * is therefore the supported way to force a refresh of channels, EPG and DVR.
     * Returns true when the resync completed and the client is Connected again.
     */
    suspend fun resync(timeoutMs: Long = RESYNC_TIMEOUT_MS): Boolean {
        val profile = lastProfile ?: return false
        if (!profile.isConfigured) return false

        val oldJob = connectionJob
        connectionJob = null
        // Closing the socket first unblocks the reader loop so cancelAndJoin cannot hang.
        activeSession?.close()
        activeSession = null
        oldJob?.cancelAndJoin()

        connect(profile)
        val result = withTimeoutOrNull(timeoutMs) {
            connectionState.first {
                it is ConnectionState.Connected || it is ConnectionState.Error
            }
        }
        return result is ConnectionState.Connected
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

    suspend fun fileStat(handleId: Long): HtspFileInfo {
        val reply = rpc(
            "fileStat",
            htsMap {
                put("id", handleId)
            },
        )
        return HtspFileInfo(
            handleId = handleId,
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

    suspend fun addDvrEntry(eventId: Long): Long {
        val reply = rpc(
            "addDvrEntry",
            htsMap {
                put("eventId", eventId)
            },
        )
        assertDvrSuccess("addDvrEntry", reply)
        return reply.getLong("id") ?: throw HtspException("Missing id in addDvrEntry reply")
    }

    suspend fun addManualDvrEntry(
        channelId: Long,
        startEpochSec: Long,
        stopEpochSec: Long,
        title: String,
        description: String? = null,
    ): Long {
        val reply = rpc(
            "addDvrEntry",
            htsMap {
                put("channelId", channelId)
                put("start", startEpochSec)
                put("stop", stopEpochSec)
                put("title", title)
                if (!description.isNullOrBlank()) {
                    put("description", description)
                }
            },
        )
        assertDvrSuccess("addDvrEntry", reply)
        return reply.getLong("id") ?: throw HtspException("Missing id in addDvrEntry reply")
    }

    suspend fun stopDvrEntry(id: Long) {
        val reply = rpc(
            "stopDvrEntry",
            htsMap {
                put("id", id)
            },
        )
        assertDvrSuccess("stopDvrEntry", reply)
    }

    suspend fun cancelDvrEntry(id: Long) {
        val reply = rpc(
            "cancelDvrEntry",
            htsMap {
                put("id", id)
            },
        )
        assertDvrSuccess("cancelDvrEntry", reply)
    }

    suspend fun deleteDvrEntry(id: Long) {
        val reply = rpc(
            "deleteDvrEntry",
            htsMap {
                put("id", id)
            },
        )
        assertDvrSuccess("deleteDvrEntry", reply)
    }

    suspend fun updateDvrEntry(
        id: Long,
        playPositionSec: Long? = null,
        playCount: Int? = null,
    ) {
        val reply = rpc(
            "updateDvrEntry",
            htsMap {
                put("id", id)
                if (playPositionSec != null) {
                    put("playposition", playPositionSec.coerceAtLeast(0L))
                }
                if (playCount != null) {
                    put("playcount", playCount.coerceAtLeast(0))
                }
            },
        )
        assertDvrSuccess("updateDvrEntry", reply)
    }

    private fun assertDvrSuccess(method: String, reply: HtsMap) {
        if (reply.containsKey("success") && (reply.getLong("success") ?: 1L) == 0L) {
            throw HtspException(reply.getString("error") ?: "Tvheadend rejected '$method'")
        }
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

        val session = HtspSession(socket, seqGenerator)
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
            val authResult = HtspSession.handshakeAndAuthenticate(session, profile, RPC_TIMEOUT_MS)
            negotiatedVersion = authResult.negotiatedVersion
            serverName = authResult.serverName
            serverVersion = authResult.serverVersion

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

    private suspend fun fetchClockOffsetOrZero(session: HtspSession): Long = try {
        val sysTime = session.rpc("getSysTime", htsMap {}, RPC_TIMEOUT_MS)
        val serverSec = sysTime.getLong("time") ?: return 0L
        (serverSec * 1000L) - System.currentTimeMillis()
    } catch (_: Exception) {
        0L
    }

    companion object {
        const val CLIENT_HTSP_VERSION = 44
        const val CLIENT_NAME = "MuxGrid"
        const val CLIENT_VERSION = "0.6.0"

        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val RPC_TIMEOUT_MS = 10_000L
        private const val INITIAL_SYNC_TIMEOUT_MS = 30_000L
        private const val RESYNC_TIMEOUT_MS = 30_000L
        private const val DEFAULT_EPG_WINDOW_SEC = 7L * 86_400L
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
