package io.github.corebonts.muxgrid.core.player

import io.github.corebonts.muxgrid.core.model.StreamTicket
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * In-process HTTP/1.1 loopback proxy bound strictly to 127.0.0.1:<ephemeralPort>/<sessionToken>/...
 *
 * Responsibilities:
 * - Obtains and caches HTSP `getTicket` tokens, refreshing before expiry and retrying once on 401.
 * - Enforces single-tuner safety by tearing down any active upstream socket before opening the next.
 * - Uses direct HTTP/1.1 sockets (never JDK HttpURLConnection auto-retry) so Tvheadend's ~2s 0-byte
 *   empty reply on tuner conflict is detected immediately on the first EOF.
 */
class LocalMediaProxy(
    private val serverBaseUrlProvider: () -> String?,
    private val ticketProvider: suspend (channelId: Long) -> StreamTicket,
    private val onLiveOpenFailed: (channelId: Long, reason: String, isEmptyReply: Boolean) -> Unit = { _, _, _ -> },
    private val ticketTtlMs: Long = DEFAULT_TICKET_TTL_MS,
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() },
) {
    private data class CachedTicket(
        val ticket: StreamTicket,
        val fetchedAtMs: Long,
    )

    private class ActiveUpstream(
        val channelId: Long,
        val upstreamSocket: AtomicReference<Socket?> = AtomicReference(null),
        val clientSocket: Socket,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
    ) {
        fun close() {
            cancelled.set(true)
            runCatching { upstreamSocket.getAndSet(null)?.close() }
            runCatching { clientSocket.close() }
        }
    }

    val sessionToken: String = UUID.randomUUID().toString().replace("-", "")

    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort

    private val running = AtomicBoolean(true)
    private val ticketCache = ConcurrentHashMap<Long, CachedTicket>()
    private val activeUpstream = AtomicReference<ActiveUpstream?>(null)
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "muxgrid-media-proxy").apply { isDaemon = true }
    }

    init {
        executor.submit { acceptLoop() }
    }

    fun liveStreamUrl(channelId: Long): String =
        "http://127.0.0.1:$port/$sessionToken/live/$channelId"

    fun stopActiveStream() {
        activeUpstream.getAndSet(null)?.close()
    }

    fun close() {
        if (!running.compareAndSet(true, false)) return
        stopActiveStream()
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    private fun acceptLoop() {
        while (running.get() && !serverSocket.isClosed) {
            try {
                val client = serverSocket.accept()
                executor.submit { handleClient(client) }
            } catch (_: IOException) {
                if (!running.get()) break
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        var currentHandle: ActiveUpstream? = null
        try {
            clientSocket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), StandardCharsets.US_ASCII))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                writeHttpError(clientSocket.getOutputStream(), 400, "Bad Request")
                return
            }
            val method = parts[0].uppercase()
            val rawPath = parts[1].substringBefore('?')
            val segments = rawPath.trimStart('/').split('/')
            if (segments.size != 3 || segments[0] != sessionToken) {
                writeHttpError(clientSocket.getOutputStream(), 403, "Forbidden")
                return
            }

            if (segments[1] == "live") {
                val channelId = segments[2].toLongOrNull()
                if (channelId == null) {
                    writeHttpError(clientSocket.getOutputStream(), 400, "Invalid channelId")
                    return
                }
                // Deterministic single-tuner teardown before opening the next upstream connection
                stopActiveStream()
                val handle = ActiveUpstream(channelId = channelId, clientSocket = clientSocket)
                currentHandle = handle
                activeUpstream.set(handle)
                relayLiveStream(method, channelId, handle, clientSocket.getOutputStream())
            } else {
                writeHttpError(clientSocket.getOutputStream(), 404, "Not Found")
            }
        } catch (_: IOException) {
            // Client disconnected or stream aborted
        } finally {
            if (currentHandle != null) {
                activeUpstream.compareAndSet(currentHandle, null)
                currentHandle.close()
            } else {
                runCatching { clientSocket.close() }
            }
        }
    }

    private fun relayLiveStream(
        method: String,
        channelId: Long,
        handle: ActiveUpstream,
        clientOut: OutputStream,
    ) {
        val baseUrl = serverBaseUrlProvider()?.trimEnd('/')
        if (baseUrl.isNullOrEmpty()) {
            onLiveOpenFailed(channelId, "No server configured", false)
            writeHttpError(clientOut, 503, "No server configured")
            return
        }

        val baseUri = URI(baseUrl)
        val upstreamHost = baseUri.host ?: run {
            onLiveOpenFailed(channelId, "Invalid server host", false)
            writeHttpError(clientOut, 502, "Invalid server host")
            return
        }
        val upstreamPort = if (baseUri.port > 0) baseUri.port else 9981
        val basePath = baseUri.rawPath?.trimEnd('/') ?: ""

        var forceRefresh = false
        for (attempt in 0..1) {
            if (handle.cancelled.get()) return
            val ticket = try {
                getOrFetchTicket(channelId, forceRefresh)
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to obtain stream ticket"
                onLiveOpenFailed(channelId, msg, false)
                writeHttpError(clientOut, 502, msg)
                return
            }

            val reqPath = buildUpstreamPath(basePath, ticket)
            val httpMethod = if (method == "HEAD") "HEAD" else "GET"

            val upstreamSock = Socket()
            handle.upstreamSocket.set(upstreamSock)
            try {
                upstreamSock.soTimeout = 15_000
                upstreamSock.connect(InetSocketAddress(upstreamHost, upstreamPort), 5_000)

                val reqBytes = buildString {
                    append("$httpMethod $reqPath HTTP/1.1\r\n")
                    append("Host: $upstreamHost:$upstreamPort\r\n")
                    append("Connection: close\r\n")
                    append("Accept: */*\r\n")
                    append("\r\n")
                }.toByteArray(StandardCharsets.US_ASCII)
                upstreamSock.getOutputStream().apply {
                    write(reqBytes)
                    flush()
                }

                val upstreamIn = BufferedInputStream(upstreamSock.getInputStream(), 32_768)
                val statusLine = readHeaderLine(upstreamIn)
                if (statusLine == null) {
                    // Tvheadend closes TCP with 0 bytes / empty reply when no tuner is free
                    runCatching { upstreamSock.close() }
                    if (!handle.cancelled.get()) {
                        onLiveOpenFailed(channelId, "Upstream closed with 0 bytes (empty reply)", true)
                        writeHttpError(clientOut, 503, "Empty upstream reply")
                    }
                    return
                }

                val statusCode = parseStatusCode(statusLine)
                var contentType = "video/mp2t"
                while (true) {
                    val headerLine = readHeaderLine(upstreamIn) ?: break
                    if (headerLine.isEmpty()) break
                    val colon = headerLine.indexOf(':')
                    if (colon > 0) {
                        val name = headerLine.substring(0, colon).trim()
                        if (name.equals("Content-Type", ignoreCase = true)) {
                            contentType = headerLine.substring(colon + 1).trim()
                        }
                    }
                }

                if (statusCode == 401 && attempt == 0) {
                    runCatching { upstreamSock.close() }
                    ticketCache.remove(channelId)
                    forceRefresh = true
                    continue
                }

                if (statusCode !in 200..299) {
                    runCatching { upstreamSock.close() }
                    if (!handle.cancelled.get()) {
                        val msg = "Upstream returned HTTP $statusCode"
                        onLiveOpenFailed(channelId, msg, false)
                        writeHttpError(clientOut, statusCode.coerceIn(400, 599), msg)
                    }
                    return
                }

                val respHeader = "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nConnection: close\r\n\r\n"
                clientOut.write(respHeader.toByteArray(StandardCharsets.US_ASCII))
                clientOut.flush()

                if (httpMethod == "HEAD") {
                    runCatching { upstreamSock.close() }
                    return
                }

                val buffer = ByteArray(32_768)
                var totalCopied = 0L
                try {
                    while (!handle.cancelled.get()) {
                        val read = upstreamIn.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            totalCopied += read
                            clientOut.write(buffer, 0, read)
                            clientOut.flush()
                        }
                    }
                } catch (_: IOException) {
                    if (totalCopied == 0L && !handle.cancelled.get()) {
                        onLiveOpenFailed(channelId, "Stream closed with 0 bytes", true)
                    }
                } finally {
                    runCatching { upstreamSock.close() }
                }
                return
            } catch (e: IOException) {
                runCatching { upstreamSock.close() }
                if (!handle.cancelled.get()) {
                    val isEmpty = e is EOFException ||
                        (e.message?.contains("reset", ignoreCase = true) == true) ||
                        (e.message?.contains("closed", ignoreCase = true) == true)
                    onLiveOpenFailed(channelId, e.message ?: "Upstream connection failed", isEmpty)
                    writeHttpError(clientOut, 503, "Upstream failed")
                }
                return
            }
        }
    }

    private fun readHeaderLine(input: InputStream): String? {
        val out = ByteArrayOutputStream(128)
        var b = input.read()
        if (b < 0) return null
        while (b >= 0) {
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                out.write(b)
            }
            b = input.read()
        }
        return String(out.toByteArray(), StandardCharsets.US_ASCII)
    }

    private fun parseStatusCode(statusLine: String): Int {
        val parts = statusLine.split(' ')
        return if (parts.size >= 2) parts[1].toIntOrNull() ?: 502 else 502
    }

    private fun getOrFetchTicket(channelId: Long, forceRefresh: Boolean): StreamTicket {
        val now = nowMsProvider()
        if (!forceRefresh) {
            val cached = ticketCache[channelId]
            if (cached != null && (now - cached.fetchedAtMs) < ticketTtlMs) {
                return cached.ticket
            }
        }
        val fresh = runBlocking { ticketProvider(channelId) }
        ticketCache[channelId] = CachedTicket(ticket = fresh, fetchedAtMs = now)
        return fresh
    }

    private fun buildUpstreamPath(basePath: String, ticket: StreamTicket): String {
        val normalizedPath = if (ticket.path.startsWith('/')) ticket.path else "/${ticket.path}"
        val sep = if ('?' in normalizedPath) '&' else '?'
        return "$basePath$normalizedPath${sep}ticket=${ticket.ticket}&profile=pass"
    }

    private fun writeHttpError(out: OutputStream, code: Int, message: String) {
        val body = message.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 $code $message\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        runCatching {
            out.write(headers.toByteArray(StandardCharsets.US_ASCII))
            out.write(body)
            out.flush()
        }
    }

    companion object {
        const val DEFAULT_TICKET_TTL_MS: Long = 240_000L
    }
}
