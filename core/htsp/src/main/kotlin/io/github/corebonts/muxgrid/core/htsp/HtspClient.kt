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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

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
    private val connectTimeoutMs: Int = HtspSession.CONNECT_TIMEOUT_MS,
    private val rpcTimeoutMs: Long = HtspSession.RPC_TIMEOUT_MS,
    private val initialSyncTimeoutMs: Long = INITIAL_SYNC_TIMEOUT_MS,
    private val awaitConsumerSyncAck: Boolean = false,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val eventChannel = Channel<HtspPushEvent>(Channel.UNLIMITED)
    val events: Flow<HtspPushEvent> = eventChannel.receiveAsFlow()

    private val syncAckLock = Any()
    private val pendingSyncAcks = ArrayDeque<CompletableDeferred<Unit>>()

    private val seqGenerator = AtomicLong(1L)
    private val sessionLock = Any()
    private val connectionGeneration = AtomicLong(0L)
    private var connectionJob: Job? = null

    @Volatile
    private var activeSession: HtspSession? = null

    @Volatile
    private var lastProfile: ServerProfile? = null

    @Volatile
    var suppressTransientErrors: Boolean = false

    fun connect(profile: ServerProfile) {
        val generation: Long
        val oldJob: Job?
        val oldSession: HtspSession?
        synchronized(sessionLock) {
            lastProfile = profile
            generation = connectionGeneration.incrementAndGet()
            oldJob = connectionJob
            oldSession = activeSession
            activeSession = null
            if (!profile.isConfigured) {
                connectionJob = null
                _connectionState.value = ConnectionState.Disconnected
                oldJob?.cancel()
                oldSession?.close()
                return
            }
            // Publish Connecting synchronously so callers waiting on a resync never
            // observe the previous (stale) Connected state.
            _connectionState.value = ConnectionState.Connecting
            connectionJob = scope.launch(Dispatchers.IO) {
                runConnectionLoop(profile, generation)
            }
        }
        oldJob?.cancel()
        oldSession?.close()
    }

    private suspend fun CoroutineScope.runConnectionLoop(
        profile: ServerProfile,
        generation: Long,
    ) {
        var backoffMs = INITIAL_BACKOFF_MS
        var hadSuccessfulSync = false
        var consecutiveFailures = 0
        while (isActive && connectionGeneration.get() == generation) {
            var stopRetrying = false
            var fastRetry = false
            try {
                publishStateIfCurrent(generation, ConnectionState.Connecting)
                runSession(profile, generation) {
                    // Reset backoff once initial sync completes
                    backoffMs = INITIAL_BACKOFF_MS
                    hadSuccessfulSync = true
                    consecutiveFailures = 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HtspAuthException) {
                publishStateIfCurrent(
                    generation,
                    ConnectionState.Error(
                        message = e.message ?: "Authentication failed",
                        isAuthError = true,
                    ),
                )
                stopRetrying = true
            } catch (e: Exception) {
                if (!isActive || connectionGeneration.get() != generation) break
                consecutiveFailures++
                val isFirstDropAfterSync = hadSuccessfulSync && consecutiveFailures == 1
                if (suppressTransientErrors || isFirstDropAfterSync) {
                    publishStateIfCurrent(generation, ConnectionState.Connecting)
                    fastRetry = isFirstDropAfterSync && !suppressTransientErrors
                } else {
                    publishStateIfCurrent(
                        generation,
                        ConnectionState.Error(
                            message = e.message ?: "Connection lost",
                            isAuthError = false,
                        ),
                    )
                }
            }

            if (stopRetrying || !isActive || connectionGeneration.get() != generation) break
            if (fastRetry) {
                delay(400L)
            } else {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    fun disconnect() {
        val oldJob: Job?
        val oldSession: HtspSession?
        synchronized(sessionLock) {
            connectionGeneration.incrementAndGet()
            oldJob = connectionJob
            oldSession = activeSession
            connectionJob = null
            activeSession = null
            _connectionState.value = ConnectionState.Disconnected
        }
        oldJob?.cancel()
        oldSession?.close()
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

        val resyncGen: Long
        val oldJob: Job?
        val oldSession: HtspSession?
        synchronized(sessionLock) {
            resyncGen = connectionGeneration.incrementAndGet()
            oldJob = connectionJob
            oldSession = activeSession
            connectionJob = null
            activeSession = null
        }
        oldJob?.cancel()
        // Closing the socket first unblocks the reader loop so join cannot hang.
        oldSession?.close()
        oldJob?.join()

        if (connectionGeneration.get() != resyncGen) {
            return false
        }
        connect(profile)
        val result = withTimeoutOrNull(timeoutMs) {
            connectionState.first {
                it is ConnectionState.Connected || it is ConnectionState.Error || it is ConnectionState.Disconnected
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

    fun acknowledgeInitialSync() {
        val ack = synchronized(syncAckLock) {
            if (pendingSyncAcks.isEmpty()) null else pendingSyncAcks.removeFirst()
        }
        ack?.complete(Unit)
    }

    private fun publishStateIfCurrent(generation: Long, state: ConnectionState) {
        synchronized(sessionLock) {
            if (connectionGeneration.get() == generation) {
                _connectionState.value = state
            }
        }
    }

    private fun clearSession(session: HtspSession) {
        synchronized(sessionLock) {
            if (activeSession === session) {
                activeSession = null
            }
        }
        session.close()
    }

    private suspend fun rpc(method: String, params: HtsMap, timeoutMs: Long = rpcTimeoutMs): HtsMap {
        val session = activeSession ?: throw HtspException("HTSP is not connected")
        return session.rpc(method, params, timeoutMs)
    }

    private suspend fun runSession(
        profile: ServerProfile,
        generation: Long,
        onSyncCompleted: () -> Unit,
    ) = coroutineScope {
        val socket = Socket()
        val session = try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.connect(InetSocketAddress(profile.normalizedHost, profile.htspPort), connectTimeoutMs)
            HtspSession(socket, seqGenerator)
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }

        val registered = synchronized(sessionLock) {
            if (!isActive || connectionGeneration.get() != generation) {
                false
            } else {
                activeSession = session
                true
            }
        }
        if (!registered) {
            session.close()
            throw CancellationException("Connection superseded or cancelled")
        }

        val initialSyncDeferred = CompletableDeferred<Unit>()
        var syncAppliedAck: CompletableDeferred<Unit>? = null
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
                    if (connectionGeneration.get() != generation) return@readLoop
                    val event = HtspMessageParser.parsePushEvent(method, msg)
                    if (event != null) {
                        if (event is HtspPushEvent.InitialSyncCompleted) {
                            val ack = if (awaitConsumerSyncAck) CompletableDeferred<Unit>() else null
                            synchronized(syncAckLock) {
                                if (ack != null) {
                                    pendingSyncAcks.addLast(ack)
                                }
                                eventChannel.trySend(event)
                            }
                            syncAppliedAck = ack
                            initialSyncDeferred.complete(Unit)
                        } else {
                            eventChannel.trySend(event)
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
            val authResult = HtspSession.handshakeAndAuthenticate(session, profile, rpcTimeoutMs)
            negotiatedVersion = authResult.negotiatedVersion
            serverName = authResult.serverName
            serverVersion = authResult.serverVersion

            publishStateIfCurrent(
                generation,
                ConnectionState.Syncing(
                    serverName = serverName,
                    serverVersion = serverVersion,
                    htspVersion = negotiatedVersion,
                ),
            )

            if (connectionGeneration.get() != generation) {
                throw CancellationException("Connection superseded")
            }
            eventChannel.trySend(HtspPushEvent.SyncStarting)

            val epgMaxTime = (System.currentTimeMillis() / 1000L) + epgWindowSeconds
            session.rpc(
                "enableAsyncMetadata",
                htsMap {
                    put("epg", 1)
                    put("epgMaxTime", epgMaxTime)
                },
                rpcTimeoutMs,
            )

            withTimeoutOrNull(initialSyncTimeoutMs) {
                initialSyncDeferred.await()
            } ?: throw HtspException("HTSP initial sync timed out after ${initialSyncTimeoutMs}ms")
            // Keep state in Syncing until the store has finished applying and publishing the sync batch.
            syncAppliedAck?.await()
            onSyncCompleted()

            var clockOffsetMs = fetchClockOffsetOrZero(session)
            publishStateIfCurrent(
                generation,
                ConnectionState.Connected(
                    serverName = serverName,
                    serverVersion = serverVersion,
                    htspVersion = negotiatedVersion,
                    serverClockOffsetMs = clockOffsetMs,
                ),
            )

            // Heartbeat loop
            val heartbeatJob = launch(Dispatchers.IO) {
                try {
                    while (isActive && connectionGeneration.get() == generation) {
                        delay(heartbeatIntervalMs)
                        val sysTime = session.rpc("getSysTime", htsMap {}, rpcTimeoutMs)
                        val serverSec = sysTime.getLong("time")
                        if (serverSec != null) {
                            clockOffsetMs = (serverSec * 1000L) - System.currentTimeMillis()
                            publishStateIfCurrent(
                                generation,
                                ConnectionState.Connected(
                                    serverName = serverName,
                                    serverVersion = serverVersion,
                                    htspVersion = negotiatedVersion,
                                    serverClockOffsetMs = clockOffsetMs,
                                ),
                            )
                        }
                    }
                } catch (t: Throwable) {
                    if (t !is CancellationException) {
                        clearSession(session)
                    }
                    throw t
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
            clearSession(session)
        }
    }

    private suspend fun fetchClockOffsetOrZero(session: HtspSession): Long = try {
        val sysTime = session.rpc("getSysTime", htsMap {}, rpcTimeoutMs)
        val serverSec = sysTime.getLong("time") ?: return 0L
        (serverSec * 1000L) - System.currentTimeMillis()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        0L
    }

    companion object {
        const val CLIENT_HTSP_VERSION = 44
        const val CLIENT_NAME = "MuxGrid"
        const val CLIENT_VERSION = "0.6.0"

        private const val INITIAL_SYNC_TIMEOUT_MS = 30_000L
        private const val RESYNC_TIMEOUT_MS = 30_000L
        private const val DEFAULT_EPG_WINDOW_SEC = 2L * 7L * 86_400L // +2 weeks
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
                val session = try {
                    socket.tcpNoDelay = true
                    socket.connect(
                        InetSocketAddress(profile.normalizedHost, profile.htspPort),
                        HtspSession.CONNECT_TIMEOUT_MS,
                    )
                    HtspSession(socket, AtomicLong(1L))
                } catch (e: CancellationException) {
                    runCatching { socket.close() }
                    throw e
                } catch (e: Exception) {
                    runCatching { socket.close() }
                    return@withContext ConnectionTestResult.Failure(
                        message = e.message ?: "Unable to connect to ${profile.formattedHttpHost}:${profile.htspPort}",
                        isAuthError = false,
                    )
                }

                try {
                    val auth = coroutineScope {
                        val writeJob = launch(Dispatchers.IO) { session.writeLoop() }
                        val readJob = launch(Dispatchers.IO) {
                            try {
                                session.readLoop()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Throwable) {
                                session.close()
                            }
                        }
                        try {
                            HtspSession.handshakeAndAuthenticate(session, profile, HtspSession.RPC_TIMEOUT_MS)
                        } finally {
                            writeJob.cancel()
                            readJob.cancel()
                            session.close()
                        }
                    }
                    ConnectionTestResult.Success(
                        serverName = auth.serverName,
                        serverVersion = auth.serverVersion,
                        htspVersion = auth.negotiatedVersion,
                        hasDvrAccess = auth.hasDvrAccess,
                        hasStreamingAccess = auth.hasStreamingAccess,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: HtspAuthException) {
                    ConnectionTestResult.Failure(
                        message = "Authentication failed: check username or password",
                        isAuthError = true,
                    )
                } catch (e: Exception) {
                    ConnectionTestResult.Failure(
                        message = e.message ?: "Unable to connect to ${profile.formattedHttpHost}:${profile.htspPort}",
                        isAuthError = false,
                    )
                } finally {
                    session.close()
                }
            }
    }
}
