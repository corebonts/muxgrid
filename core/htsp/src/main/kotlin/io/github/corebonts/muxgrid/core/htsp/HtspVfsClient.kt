package io.github.corebonts.muxgrid.core.htsp

import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated HTSP VFS connection (`hello` + `authenticate`, without `enableAsyncMetadata`)
 * so bulk `fileOpen` / `fileStat` / `fileRead` / `fileClose` calls for growing recordings
 * never block or delay metadata push events and control RPCs on [HtspClient].
 */
class HtspVfsClient(
    private val profileProvider: () -> ServerProfile,
    private val idleDisconnectDelayMs: Long = DEFAULT_IDLE_DISCONNECT_MS,
    private val connectTimeoutMs: Int = HtspSession.CONNECT_TIMEOUT_MS,
    private val rpcTimeoutMs: Long = HtspSession.RPC_TIMEOUT_MS,
) : Closeable {
    private val vfsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val sessionLock = Any()
    private val seqGenerator = AtomicLong(1L)
    private val openHandleCount = AtomicInteger(0)
    private val disconnectGeneration = AtomicLong(0L)

    @Volatile
    private var activeSession: HtspSession? = null

    @Volatile
    private var connectedProfile: ServerProfile? = null

    private var connectingSocket: Socket? = null
    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private var idleCloseJob: Job? = null

    inner class VfsFileHandle internal constructor(
        private val session: HtspSession,
        val handleId: Long,
        override val initialSize: Long,
        val mtime: Long,
    ) : GrowingDvrFile {
        private val closed = AtomicBoolean(false)

        override suspend fun statSize(): Long = stat().size

        suspend fun stat(): HtspFileInfo {
            val reply = session.rpc(
                "fileStat",
                htsMap { put("id", handleId) },
                rpcTimeoutMs,
            )
            return HtspFileInfo(
                handleId = handleId,
                size = reply.getLong("size") ?: 0L,
                mtime = reply.getLong("mtime") ?: 0L,
            )
        }

        override suspend fun read(offset: Long, size: Int): ByteArray {
            val reply = session.rpc(
                "fileRead",
                htsMap {
                    put("id", handleId)
                    put("size", size.toLong())
                    put("offset", offset.coerceAtLeast(0L))
                },
                rpcTimeoutMs,
            )
            return reply.getBytes("data") ?: ByteArray(0)
        }

        suspend fun closeSuspend() {
            if (!closed.compareAndSet(false, true)) return
            try {
                withContext(NonCancellable) {
                    runCatching {
                        session.rpc(
                            "fileClose",
                            htsMap { put("id", handleId) },
                            CLOSE_RPC_TIMEOUT_MS,
                        )
                    }
                }
            } finally {
                if (openHandleCount.updateAndGet { (it - 1).coerceAtLeast(0) } == 0) {
                    if (idleDisconnectDelayMs <= 0L) {
                        withContext(NonCancellable) {
                            connectMutex.withLock {
                                if (openHandleCount.get() == 0) {
                                    closeActiveSessionLocked()
                                }
                            }
                        }
                    } else {
                        scheduleIdleDisconnect()
                    }
                }
            }
        }

        override fun close() {
            runBlocking { closeSuspend() }
        }
    }

    suspend fun openFile(path: String): VfsFileHandle {
        val profile = profileProvider()
        if (!profile.isConfigured) {
            throw HtspException("Server profile is not configured")
        }
        var session = acquireSessionForOpen(profile)
        var handleReserved = true
        try {
            val reply = try {
                session.rpc("fileOpen", htsMap { put("file", path) }, rpcTimeoutMs)
            } catch (e: HtspNoAccessException) {
                throw e
            } catch (e: HtspException) {
                // Reconnect once if the cached VFS session went stale between seeks
                if (session.isClosed) {
                    releaseOpenHandleReservation()
                    handleReserved = false
                    session = acquireSessionForOpen(profile, forceReconnect = true)
                    handleReserved = true
                    session.rpc("fileOpen", htsMap { put("file", path) }, rpcTimeoutMs)
                } else {
                    throw e
                }
            }
            val handleId = reply.getLong("id") ?: throw HtspException("Missing handle id in fileOpen reply")
            val handle = VfsFileHandle(
                session = session,
                handleId = handleId,
                initialSize = reply.getLong("size") ?: 0L,
                mtime = reply.getLong("mtime") ?: 0L,
            )
            handleReserved = false
            return handle
        } finally {
            if (handleReserved) {
                releaseOpenHandleReservation()
            }
        }
    }

    fun disconnect() {
        val oldW: Job?
        val oldR: Job?
        val oldSession: HtspSession?
        val oldConnecting: Socket?
        synchronized(sessionLock) {
            disconnectGeneration.incrementAndGet()
            idleCloseJob?.cancel()
            idleCloseJob = null
            oldW = writerJob
            oldR = readerJob
            oldSession = activeSession
            oldConnecting = connectingSocket
            writerJob = null
            readerJob = null
            activeSession = null
            connectedProfile = null
            connectingSocket = null
        }
        oldW?.cancel()
        oldR?.cancel()
        oldSession?.close()
        oldConnecting?.let { runCatching { it.close() } }
    }

    override fun close() {
        disconnect()
        vfsScope.cancel()
    }

    private suspend fun acquireSessionForOpen(
        profile: ServerProfile,
        forceReconnect: Boolean = false,
    ): HtspSession = connectMutex.withLock {
        val session = ensureSessionLocked(profile, forceReconnect)
        openHandleCount.incrementAndGet()
        session
    }

    private fun releaseOpenHandleReservation() {
        if (openHandleCount.updateAndGet { (it - 1).coerceAtLeast(0) } == 0) {
            scheduleIdleDisconnect()
        }
    }

    private suspend fun ensureSessionLocked(
        profile: ServerProfile,
        forceReconnect: Boolean = false,
    ): HtspSession {
        synchronized(sessionLock) {
            idleCloseJob?.cancel()
            idleCloseJob = null
            val existing = activeSession
            if (!forceReconnect && existing != null && !existing.isClosed && connectedProfile == profile) {
                return existing
            }
        }
        closeActiveSessionLocked()

        val gen = disconnectGeneration.get()
        val socket = Socket()
        synchronized(sessionLock) {
            if (disconnectGeneration.get() != gen) {
                throw HtspException("VFS session disconnected")
            }
            connectingSocket = socket
        }
        val session = try {
            withContext(Dispatchers.IO) {
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(profile.normalizedHost, profile.htspPort), connectTimeoutMs)
            }
            HtspSession(socket, seqGenerator)
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        } finally {
            synchronized(sessionLock) {
                if (connectingSocket === socket) {
                    connectingSocket = null
                }
            }
        }
        val wJob = vfsScope.launch { session.writeLoop() }
        val rJob = vfsScope.launch {
            runCatching { session.readLoop() }
            session.close()
        }
        try {
            HtspSession.handshakeAndAuthenticate(session, profile, rpcTimeoutMs)
        } catch (t: Throwable) {
            wJob.cancel()
            rJob.cancel()
            session.close()
            throw t
        }

        val active = currentCoroutineContext().isActive
        val published = synchronized(sessionLock) {
            if (!active || disconnectGeneration.get() != gen) {
                false
            } else {
                activeSession = session
                connectedProfile = profile
                writerJob = wJob
                readerJob = rJob
                true
            }
        }
        if (!published) {
            wJob.cancel()
            rJob.cancel()
            session.close()
            if (!active) {
                throw CancellationException("VFS openFile cancelled")
            }
            throw HtspException("VFS session disconnected")
        }
        return session
    }

    private fun scheduleIdleDisconnect() {
        synchronized(sessionLock) {
            idleCloseJob?.cancel()
            if (!vfsScope.isActive) {
                idleCloseJob = null
                return
            }
            idleCloseJob = vfsScope.launch {
                if (idleDisconnectDelayMs > 0L) {
                    delay(idleDisconnectDelayMs)
                }
                connectMutex.withLock {
                    if (openHandleCount.get() == 0) {
                        closeActiveSessionLocked()
                    }
                }
            }
        }
    }

    private fun closeActiveSessionLocked() {
        val oldW: Job?
        val oldR: Job?
        val oldSession: HtspSession?
        synchronized(sessionLock) {
            oldW = writerJob
            oldR = readerJob
            oldSession = activeSession
            writerJob = null
            readerJob = null
            activeSession = null
            connectedProfile = null
        }
        oldW?.cancel()
        oldR?.cancel()
        oldSession?.close()
    }

    companion object {
        private const val CLOSE_RPC_TIMEOUT_MS = 2_000L
        private const val DEFAULT_IDLE_DISCONNECT_MS = 5_000L
    }
}
