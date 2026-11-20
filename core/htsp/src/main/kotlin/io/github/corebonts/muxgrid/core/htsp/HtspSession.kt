package io.github.corebonts.muxgrid.core.htsp

import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
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
)

/**
 * Multiplexed HTSP frame session over a single TCP socket with dedicated reader/writer loops.
 */
internal class HtspSession(
    private val socket: Socket,
    private val seqGenerator: AtomicLong,
) {
    private val input = BufferedInputStream(socket.getInputStream(), 65_536)
    private val output = BufferedOutputStream(socket.getOutputStream(), 16_384)
    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<HtsMap>>()

    @Volatile
    var isClosed: Boolean = false
        private set

    suspend fun rpc(method: String, params: HtsMap, timeoutMs: Long): HtsMap {
        if (isClosed) throw HtspException("HTSP session closed")
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
            )
        }
    }
}
