package io.github.corebonts.muxgrid.core.htsp

import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
) : Closeable {
    private val vfsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val seqGenerator = AtomicLong(1L)
    private val openHandleCount = AtomicInteger(0)

    @Volatile
    private var activeSession: HtspSession? = null

    @Volatile
    private var connectedProfile: ServerProfile? = null

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
                RPC_TIMEOUT_MS,
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
                RPC_TIMEOUT_MS,
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
                if (openHandleCount.decrementAndGet() <= 0) {
                    openHandleCount.set(0)
                    scheduleIdleDisconnect()
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
        val session = ensureSession(profile)
        val reply = try {
            session.rpc("fileOpen", htsMap { put("file", path) }, RPC_TIMEOUT_MS)
        } catch (e: HtspNoAccessException) {
            throw e
        } catch (e: HtspException) {
            // Reconnect once if the cached VFS session went stale between seeks
            if (session.isClosed) {
                val freshSession = ensureSession(profile, forceReconnect = true)
                freshSession.rpc("fileOpen", htsMap { put("file", path) }, RPC_TIMEOUT_MS)
            } else {
                throw e
            }
        }
        val handleId = reply.getLong("id") ?: throw HtspException("Missing handle id in fileOpen reply")
        val boundSession = activeSession ?: session
        idleCloseJob?.cancel()
        idleCloseJob = null
        openHandleCount.incrementAndGet()
        return VfsFileHandle(
            session = boundSession,
            handleId = handleId,
            initialSize = reply.getLong("size") ?: 0L,
            mtime = reply.getLong("mtime") ?: 0L,
        )
    }

    fun disconnect() {
        idleCloseJob?.cancel()
        idleCloseJob = null
        closeActiveSession()
    }

    override fun close() {
        disconnect()
        vfsScope.cancel()
    }

    private suspend fun ensureSession(
        profile: ServerProfile,
        forceReconnect: Boolean = false,
    ): HtspSession = connectMutex.withLock {
        idleCloseJob?.cancel()
        idleCloseJob = null
        val existing = activeSession
        if (!forceReconnect && existing != null && !existing.isClosed && connectedProfile == profile) {
            return@withLock existing
        }
        closeActiveSession()

        val socket = withContext(Dispatchers.IO) {
            Socket().apply {
                tcpNoDelay = true
                keepAlive = true
                connect(InetSocketAddress(profile.host, profile.htspPort), CONNECT_TIMEOUT_MS)
            }
        }
        val session = HtspSession(socket, seqGenerator)
        val wJob = vfsScope.launch { session.writeLoop() }
        val rJob = vfsScope.launch {
            runCatching { session.readLoop() }
            session.close()
        }
        try {
            HtspSession.handshakeAndAuthenticate(session, profile, RPC_TIMEOUT_MS)
        } catch (t: Throwable) {
            wJob.cancel()
            rJob.cancel()
            session.close()
            throw t
        }

        activeSession = session
        connectedProfile = profile
        writerJob = wJob
        readerJob = rJob
        session
    }

    private fun scheduleIdleDisconnect() {
        if (idleDisconnectDelayMs <= 0L) {
            closeActiveSession()
            return
        }
        idleCloseJob?.cancel()
        idleCloseJob = vfsScope.launch {
            delay(idleDisconnectDelayMs)
            connectMutex.withLock {
                if (openHandleCount.get() <= 0) {
                    closeActiveSession()
                }
            }
        }
    }

    private fun closeActiveSession() {
        writerJob?.cancel()
        readerJob?.cancel()
        writerJob = null
        readerJob = null
        activeSession?.close()
        activeSession = null
        connectedProfile = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val RPC_TIMEOUT_MS = 10_000L
        private const val CLOSE_RPC_TIMEOUT_MS = 2_000L
        private const val DEFAULT_IDLE_DISCONNECT_MS = 5_000L
    }
}
