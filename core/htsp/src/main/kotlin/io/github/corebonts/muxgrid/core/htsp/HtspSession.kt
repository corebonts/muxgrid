package io.github.corebonts.muxgrid.core.htsp

import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

internal data class HelloAuthResult(
    val negotiatedVersion: Int,
    val serverName: String,
    val serverVersion: String,
    val serverCapabilities: List<String> = emptyList(),
)

/**
 * Multiplexed HTSP frame session over a single TCP socket with dedicated reader/writer loops.
 */
internal class HtspSession(
    private val socket: Socket,
    private val seqGenerator: AtomicLong,
) {
    private val input = BufferedInputStream(socket.getInputStream(), 262_144)
    private val output = BufferedOutputStream(socket.getOutputStream(), 16_384)
    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val pending = ConcurrentHashMap<Long, PendingRpc>()

    internal class PendingRpc(
        val method: String,
        val seq: Long,
        val deferred: CompletableDeferred<HtsMap>,
        internal val onReply: ((HtsMap) -> Unit)? = null,
    )

    @Volatile
    var isClosed: Boolean = false
        private set

    internal fun enqueueRpc(
        method: String,
        params: HtsMap,
        onReply: ((HtsMap) -> Unit)? = null,
    ): PendingRpc {
        if (isClosed) throw HtspException("HTSP session closed")
        val seq = seqGenerator.getAndIncrement()
        val pendingRpc = PendingRpc(
            method = method,
            seq = seq,
            deferred = CompletableDeferred(),
            onReply = onReply,
        )
        pending[seq] = pendingRpc

        val request = htsMap {
            putAll(params)
            put("method", method)
            put("seq", seq)
        }
        val frame = HtsMsgCodec.encodeFrame(request)
        if (writeQueue.trySend(frame).isFailure) {
            pending.remove(seq, pendingRpc)
            throw HtspException("Failed to enqueue HTSP RPC '$method'")
        }
        return pendingRpc
    }

    internal suspend fun awaitRpc(pendingRpc: PendingRpc, timeoutMs: Long): HtsMap {
        return try {
            val reply = withTimeoutOrNull(timeoutMs) { pendingRpc.deferred.await() }
                ?: throw HtspException("HTSP RPC '${pendingRpc.method}' timed out after ${timeoutMs}ms")
            if ((reply.getLong("noaccess") ?: 0L) != 0L) {
                throw HtspNoAccessException("Access denied for HTSP method '${pendingRpc.method}'")
            }
            val error = reply.getString("error")
            if (!error.isNullOrBlank()) {
                throw HtspException("HTSP error for '${pendingRpc.method}': $error")
            }
            reply
        } finally {
            pending.remove(pendingRpc.seq, pendingRpc)
        }
    }

    suspend fun rpc(method: String, params: HtsMap, timeoutMs: Long): HtsMap =
        awaitRpc(enqueueRpc(method, params), timeoutMs)

    suspend fun writeLoop() {
        try {
            for (frame in writeQueue) {
                output.write(frame)
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

    fun readLoop(onPush: (String, HtsMap) -> Unit = { _, _ -> }) {
        try {
            while (!isClosed && !socket.isClosed) {
                val msg = HtsMsgCodec.readFrame(input)
                val seq = msg.getLong("seq")
                if (seq != null) {
                    pending.remove(seq)?.let { entry ->
                        entry.onReply?.invoke(msg)
                        entry.deferred.complete(msg)
                    }
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
        for ((seq, entry) in entries) {
            if (pending.remove(seq, entry)) {
                entry.deferred.completeExceptionally(cause)
            }
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        writeQueue.close()
        failAllPending(HtspException("HTSP session closed"))
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        internal const val CONNECT_TIMEOUT_MS = 6_000
        internal const val RPC_TIMEOUT_MS = 10_000L

        suspend fun handshakeAndAuthenticate(
            session: HtspSession,
            profile: ServerProfile,
            rpcTimeoutMs: Long,
        ): HelloAuthResult {
            val helloReply = session.rpc(
                "hello",
                htsMap {
                    put("htspversion", HtspClient.CLIENT_HTSP_VERSION)
                    put("clientname", HtspClient.CLIENT_NAME)
                    put("clientversion", HtspClient.CLIENT_VERSION)
                },
                rpcTimeoutMs,
            )
            val serverHtspVersion = helloReply.getInt("htspversion") ?: HtspClient.CLIENT_HTSP_VERSION
            val negotiatedVersion = min(HtspClient.CLIENT_HTSP_VERSION, serverHtspVersion)
            val serverName = helloReply.getString("servername") ?: "Tvheadend"
            val serverVersion = helloReply.getString("serverversion") ?: ""
            val serverCapabilities = helloReply.getStringList("servercapability")
            val challenge = helloReply.getBytes("challenge")

            if (profile.username.isNotBlank() || profile.password.isNotBlank()) {
                val authParams = htsMap {
                    put("username", profile.username)
                    if (challenge != null) {
                        put("digest", HtspClient.computeSha1Digest(profile.password, challenge))
                    }
                }
                val authReply = try {
                    session.rpc("authenticate", authParams, rpcTimeoutMs)
                } catch (_: HtspNoAccessException) {
                    throw HtspAuthException("Invalid username or password")
                }
                if ((authReply.getLong("noaccess") ?: 0L) != 0L) {
                    throw HtspAuthException("Invalid username or password")
                }
            }

            return HelloAuthResult(
                negotiatedVersion = negotiatedVersion,
                serverName = serverName,
                serverVersion = serverVersion,
                serverCapabilities = serverCapabilities,
            )
        }
    }
}
