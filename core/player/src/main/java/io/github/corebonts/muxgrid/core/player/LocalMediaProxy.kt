package io.github.corebonts.muxgrid.core.player

import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import io.github.corebonts.muxgrid.core.model.StreamTicket
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
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
 * - Serves live `/live/<channelId>` (`?profile=pass`) and completed DVR `/dvr/<dvrId>` with `Range` forwarding.
 * - Serves in-progress (growing) DVR `/growing/<dvrId>` via HTSP VFS tail-follow (`fileOpen`/`fileStat`/`fileRead`/`fileClose`).
 * - Enforces single-tuner safety by tearing down any active live upstream socket before opening the next.
 * - Uses direct HTTP/1.1 sockets (never JDK HttpURLConnection auto-retry) so Tvheadend's ~2s 0-byte
 *   empty reply on tuner conflict is detected immediately on the first EOF.
 */
class LocalMediaProxy(
    private val serverBaseUrlProvider: () -> String?,
    private val ticketProvider: suspend (channelId: Long) -> StreamTicket,
    private val dvrTicketProvider: suspend (dvrId: Long) -> StreamTicket = {
        throw UnsupportedOperationException("DVR ticket provider is not configured")
    },
    private val growingFileOpener: suspend (dvrId: Long) -> GrowingDvrFile = {
        throw UnsupportedOperationException("Growing DVR file opener is not configured")
    },
    private val isDvrRecordingProvider: (dvrId: Long) -> Boolean = { false },
    private val onLiveOpenFailed: (channelId: Long, reason: String, isEmptyReply: Boolean) -> Unit = { _, _, _ -> },
    private val ticketTtlMs: Long = DEFAULT_TICKET_TTL_MS,
    private val growingPollIntervalMs: Long = DEFAULT_GROWING_POLL_INTERVAL_MS,
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() },
) {
    private data class TicketCacheKey(
        val isDvr: Boolean,
        val id: Long,
    )

    private data class CachedTicket(
        val ticket: StreamTicket,
        val fetchedAtMs: Long,
    )

    private class ActiveStreamHandle(
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

    private data class UpstreamEndpoint(
        val host: String,
        val port: Int,
        val basePath: String,
    )

    private data class UpstreamResponse(
        val socket: Socket,
        val input: BufferedInputStream,
        val statusCode: Int,
        val statusReason: String,
        val headers: Map<String, String>,
    )

    val sessionToken: String = UUID.randomUUID().toString().replace("-", "")

    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort

    private val running = AtomicBoolean(true)
    private val ticketCache = ConcurrentHashMap<TicketCacheKey, CachedTicket>()
    private val activeLiveUpstream = AtomicReference<ActiveStreamHandle?>(null)
    private val activeDvrUpstreams = ConcurrentHashMap.newKeySet<ActiveStreamHandle>()
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "muxgrid-media-proxy").apply { isDaemon = true }
    }

    init {
        executor.submit { acceptLoop() }
    }

    fun liveStreamUrl(channelId: Long): String =
        "http://127.0.0.1:$port/$sessionToken/live/$channelId"

    fun dvrStreamUrl(dvrId: Long): String =
        "http://127.0.0.1:$port/$sessionToken/dvr/$dvrId"

    fun growingDvrStreamUrl(dvrId: Long): String =
        "http://127.0.0.1:$port/$sessionToken/growing/$dvrId"

    fun stopActiveStream() {
        activeLiveUpstream.getAndSet(null)?.close()
        val dvrHandles = activeDvrUpstreams.toList()
        activeDvrUpstreams.clear()
        dvrHandles.forEach { it.close() }
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
        var liveHandle: ActiveStreamHandle? = null
        var dvrHandle: ActiveStreamHandle? = null
        try {
            clientSocket.soTimeout = 15_000
            val clientIn = clientSocket.getInputStream()
            val requestLine = readHeaderLine(clientIn) ?: return
            var rangeHeader: String? = null
            while (true) {
                val line = readHeaderLine(clientIn) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(':').trim()
                }
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

            val id = segments[2].toLongOrNull()
            if (id == null) {
                writeHttpError(clientSocket.getOutputStream(), 400, "Invalid stream id")
                return
            }

            when (segments[1]) {
                "live" -> {
                    // Deterministic single-tuner teardown before opening the next upstream connection
                    stopActiveStream()
                    val handle = ActiveStreamHandle(clientSocket = clientSocket)
                    liveHandle = handle
                    activeLiveUpstream.set(handle)
                    relayLiveStream(method, id, handle, clientSocket.getOutputStream())
                }
                "dvr" -> {
                    // Tear down any active live tuner stream before starting DVR playback
                    activeLiveUpstream.getAndSet(null)?.close()
                    val handle = ActiveStreamHandle(clientSocket = clientSocket)
                    dvrHandle = handle
                    activeDvrUpstreams.add(handle)
                    relayDvrStream(method, id, rangeHeader, handle, clientSocket.getOutputStream())
                }
                "growing" -> {
                    activeLiveUpstream.getAndSet(null)?.close()
                    val handle = ActiveStreamHandle(clientSocket = clientSocket)
                    dvrHandle = handle
                    activeDvrUpstreams.add(handle)
                    relayGrowingDvrStream(method, id, rangeHeader, clientSocket, handle, clientSocket.getOutputStream())
                }
                else -> writeHttpError(clientSocket.getOutputStream(), 404, "Not Found")
            }
        } catch (_: IOException) {
            // Client disconnected or stream aborted
        } finally {
            if (liveHandle != null) {
                activeLiveUpstream.compareAndSet(liveHandle, null)
                liveHandle.close()
            } else if (dvrHandle != null) {
                activeDvrUpstreams.remove(dvrHandle)
                dvrHandle.close()
            } else {
                runCatching { clientSocket.close() }
            }
        }
    }

    private fun resolveEndpoint(): UpstreamEndpoint? {
        val baseUrl = serverBaseUrlProvider()?.trimEnd('/') ?: return null
        if (baseUrl.isEmpty()) return null
        val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return null
        val host = baseUri.host ?: return null
        val port = if (baseUri.port > 0) baseUri.port else 9981
        val basePath = baseUri.rawPath?.trimEnd('/') ?: ""
        return UpstreamEndpoint(host = host, port = port, basePath = basePath)
    }

    private fun relayLiveStream(
        method: String,
        channelId: Long,
        handle: ActiveStreamHandle,
        clientOut: OutputStream,
    ) {
        val endpoint = resolveEndpoint()
        if (endpoint == null) {
            onLiveOpenFailed(channelId, "No server configured or invalid host", false)
            writeHttpError(clientOut, 503, "No server configured")
            return
        }

        val key = TicketCacheKey(isDvr = false, id = channelId)
        val httpMethod = if (method == "HEAD") "HEAD" else "GET"

        var forceRefresh = false
        for (attempt in 0..1) {
            if (handle.cancelled.get()) return
            val ticket = try {
                getOrFetchTicket(key, forceRefresh) { ticketProvider(channelId) }
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to obtain stream ticket"
                onLiveOpenFailed(channelId, msg, false)
                writeHttpError(clientOut, 502, msg)
                return
            }

            val reqPath = buildUpstreamPath(endpoint.basePath, ticket, appendPassProfile = true)
            val response = try {
                openUpstream(endpoint, httpMethod, reqPath, rangeHeader = null, handle = handle)
            } catch (e: IOException) {
                if (!handle.cancelled.get()) {
                    val isEmpty = e is EOFException ||
                        (e.message?.contains("reset", ignoreCase = true) == true) ||
                        (e.message?.contains("closed", ignoreCase = true) == true)
                    onLiveOpenFailed(channelId, e.message ?: "Upstream connection failed", isEmpty)
                    writeHttpError(clientOut, 503, "Upstream failed")
                }
                return
            }

            if (response == null) {
                // Tvheadend closes TCP with 0 bytes / empty reply when no tuner is free
                if (!handle.cancelled.get()) {
                    onLiveOpenFailed(channelId, "Upstream closed with 0 bytes (empty reply)", true)
                    writeHttpError(clientOut, 503, "Empty upstream reply")
                }
                return
            }

            if (response.statusCode == 401 && attempt == 0) {
                runCatching { response.socket.close() }
                ticketCache.remove(key)
                forceRefresh = true
                continue
            }

            if (response.statusCode !in 200..299) {
                runCatching { response.socket.close() }
                if (!handle.cancelled.get()) {
                    val msg = "Upstream returned HTTP ${response.statusCode}"
                    onLiveOpenFailed(channelId, msg, false)
                    writeHttpError(clientOut, response.statusCode.coerceIn(400, 599), msg)
                }
                return
            }

            val contentType = response.headers["content-type"] ?: "video/mp2t"
            val respHeader = "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nConnection: close\r\n\r\n"
            clientOut.write(respHeader.toByteArray(StandardCharsets.US_ASCII))
            clientOut.flush()

            if (httpMethod == "HEAD") {
                runCatching { response.socket.close() }
                return
            }

            var totalCopied = 0L
            try {
                totalCopied = copyStream(response.input, clientOut, handle, maxBytes = null)
            } catch (_: IOException) {
                if (totalCopied == 0L && !handle.cancelled.get()) {
                    onLiveOpenFailed(channelId, "Stream closed with 0 bytes", true)
                }
            } finally {
                runCatching { response.socket.close() }
            }
            return
        }
    }

    private fun relayDvrStream(
        method: String,
        dvrId: Long,
        rangeHeader: String?,
        handle: ActiveStreamHandle,
        clientOut: OutputStream,
    ) {
        val endpoint = resolveEndpoint()
        if (endpoint == null) {
            writeHttpError(clientOut, 503, "No server configured")
            return
        }

        val key = TicketCacheKey(isDvr = true, id = dvrId)
        val httpMethod = if (method == "HEAD") "HEAD" else "GET"

        var forceRefresh = false
        for (attempt in 0..1) {
            if (handle.cancelled.get()) return
            val ticket = try {
                getOrFetchTicket(key, forceRefresh) { dvrTicketProvider(dvrId) }
            } catch (e: Exception) {
                writeHttpError(clientOut, 502, e.message ?: "Failed to obtain DVR ticket")
                return
            }

            val reqPath = buildUpstreamPath(endpoint.basePath, ticket, appendPassProfile = false)
            val response = try {
                openUpstream(endpoint, httpMethod, reqPath, rangeHeader = rangeHeader, handle = handle)
            } catch (_: IOException) {
                if (!handle.cancelled.get()) {
                    writeHttpError(clientOut, 503, "Upstream DVR connection failed")
                }
                return
            } ?: run {
                if (!handle.cancelled.get()) {
                    writeHttpError(clientOut, 502, "Empty upstream reply")
                }
                return
            }

            if (response.statusCode == 401 && attempt == 0) {
                runCatching { response.socket.close() }
                ticketCache.remove(key)
                forceRefresh = true
                continue
            }

            if (response.statusCode !in 200..299) {
                runCatching { response.socket.close() }
                if (!handle.cancelled.get()) {
                    writeHttpError(
                        clientOut,
                        response.statusCode.coerceIn(400, 599),
                        "Upstream returned HTTP ${response.statusCode}",
                    )
                }
                return
            }

            val respHeaders = buildString {
                append("HTTP/1.1 ${response.statusCode} ${response.statusReason}\r\n")
                response.headers["content-type"]?.let { append("Content-Type: $it\r\n") }
                response.headers["content-length"]?.let { append("Content-Length: $it\r\n") }
                response.headers["content-range"]?.let { append("Content-Range: $it\r\n") }
                response.headers["accept-ranges"]?.let { append("Accept-Ranges: $it\r\n") }
                append("Connection: close\r\n\r\n")
            }
            clientOut.write(respHeaders.toByteArray(StandardCharsets.US_ASCII))
            clientOut.flush()

            if (httpMethod == "HEAD") {
                runCatching { response.socket.close() }
                return
            }

            val expectedLength = response.headers["content-length"]?.toLongOrNull()
            try {
                copyStream(response.input, clientOut, handle, maxBytes = expectedLength)
            } catch (_: IOException) {
                // Client disconnected or upstream ended
            } finally {
                runCatching { response.socket.close() }
            }
            return
        }
    }

    private sealed interface ByteRangeSpec {
        data class FromTo(val start: Long, val endInclusive: Long?) : ByteRangeSpec
        data class Suffix(val suffixLength: Long) : ByteRangeSpec
    }

    private fun parseByteRange(rangeHeader: String?): ByteRangeSpec? {
        val raw = rangeHeader?.trim() ?: return null
        if (!raw.startsWith("bytes=", ignoreCase = true)) return null
        val spec = raw.substring(6).substringBefore(',').trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startPart = spec.substring(0, dash).trim()
        val endPart = spec.substring(dash + 1).trim()
        return if (startPart.isEmpty()) {
            val suffix = endPart.toLongOrNull()?.takeIf { it > 0L } ?: return null
            ByteRangeSpec.Suffix(suffix)
        } else {
            val start = startPart.toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val end = if (endPart.isEmpty()) null else endPart.toLongOrNull()?.takeIf { it >= start } ?: return null
            ByteRangeSpec.FromTo(start, end)
        }
    }

    private fun relayGrowingDvrStream(
        method: String,
        dvrId: Long,
        rangeHeader: String?,
        clientSocket: Socket,
        handle: ActiveStreamHandle,
        clientOut: OutputStream,
    ) {
        val file = try {
            runBlocking { growingFileOpener(dvrId) }
        } catch (e: Exception) {
            if (!handle.cancelled.get()) {
                writeHttpError(clientOut, 502, e.message ?: "Failed to open growing DVR file")
            }
            return
        }

        try {
            var currentSize = file.initialSize.coerceAtLeast(0L)
            val rangeSpec = parseByteRange(rangeHeader)
            var hasRange = false
            var startOffset = 0L
            var requestedEnd: Long? = null
            if (rangeSpec != null) {
                hasRange = true
                when (rangeSpec) {
                    is ByteRangeSpec.FromTo -> {
                        startOffset = rangeSpec.start
                        requestedEnd = rangeSpec.endInclusive
                    }
                    is ByteRangeSpec.Suffix -> {
                        startOffset = (currentSize - rangeSpec.suffixLength).coerceAtLeast(0L)
                    }
                }
            }

            if (method == "HEAD") {
                if (hasRange && startOffset >= currentSize && !isDvrRecordingProvider(dvrId)) {
                    val headers = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$currentSize\r\nConnection: close\r\n\r\n"
                    clientOut.write(headers.toByteArray(StandardCharsets.US_ASCII))
                    clientOut.flush()
                    return
                }
                val headers = buildString {
                    if (hasRange) {
                        val headerEnd = requestedEnd ?: (currentSize - 1L).coerceAtLeast(startOffset)
                        append("HTTP/1.1 206 Partial Content\r\n")
                        append("Content-Range: bytes $startOffset-$headerEnd/*\r\n")
                    } else {
                        append("HTTP/1.1 200 OK\r\n")
                    }
                    append("Content-Type: video/x-matroska\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Connection: close\r\n\r\n")
                }
                clientOut.write(headers.toByteArray(StandardCharsets.US_ASCII))
                clientOut.flush()
                return
            }

            if (hasRange && startOffset >= currentSize) {
                while (!handle.cancelled.get()) {
                    val stillRecording = isDvrRecordingProvider(dvrId)
                    currentSize = runBlocking { file.statSize() }.coerceAtLeast(0L)
                    if (currentSize > startOffset) break
                    if (!stillRecording) {
                        val headers = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$currentSize\r\nConnection: close\r\n\r\n"
                        clientOut.write(headers.toByteArray(StandardCharsets.US_ASCII))
                        clientOut.flush()
                        return
                    }
                    if (!waitForPollOrDisconnect(clientSocket, growingPollIntervalMs, handle)) {
                        return
                    }
                }
            }

            if (handle.cancelled.get()) return

            val respHeaders = buildString {
                if (hasRange) {
                    val headerEnd = if (requestedEnd != null) {
                        minOf(requestedEnd, (currentSize - 1L).coerceAtLeast(startOffset))
                    } else {
                        (currentSize - 1L).coerceAtLeast(startOffset)
                    }
                    append("HTTP/1.1 206 Partial Content\r\n")
                    append("Content-Range: bytes $startOffset-$headerEnd/*\r\n")
                } else {
                    append("HTTP/1.1 200 OK\r\n")
                }
                append("Content-Type: video/x-matroska\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Transfer-Encoding: chunked\r\n")
                append("Connection: close\r\n\r\n")
            }
            clientOut.write(respHeaders.toByteArray(StandardCharsets.US_ASCII))
            clientOut.flush()

            var offset = startOffset
            while (!handle.cancelled.get()) {
                if (requestedEnd != null && offset > requestedEnd) break
                val remaining = if (requestedEnd != null) (requestedEnd - offset + 1L) else Long.MAX_VALUE
                val toRead = minOf(GROWING_READ_CHUNK_BYTES.toLong(), remaining).toInt()
                val chunk = runBlocking { file.read(offset, toRead) }
                if (chunk.isNotEmpty()) {
                    writeHttpChunk(clientOut, chunk)
                    offset += chunk.size
                    continue
                }

                val stillRecording = isDvrRecordingProvider(dvrId)
                val latestSize = runBlocking { file.statSize() }.coerceAtLeast(0L)
                if (latestSize > offset) {
                    continue
                }
                if (!stillRecording) {
                    break
                }
                if (!waitForPollOrDisconnect(clientSocket, growingPollIntervalMs, handle)) {
                    return
                }
            }

            if (!handle.cancelled.get()) {
                clientOut.write(FINAL_CHUNK_BYTES)
                clientOut.flush()
            }
        } catch (_: IOException) {
            // Client disconnected or VFS read aborted
        } finally {
            runCatching { file.close() }
        }
    }

    private fun waitForPollOrDisconnect(
        clientSocket: Socket,
        pollMs: Long,
        handle: ActiveStreamHandle,
    ): Boolean {
        if (handle.cancelled.get() || clientSocket.isClosed) return false
        val prevTimeout = runCatching { clientSocket.soTimeout }.getOrDefault(15_000)
        return try {
            clientSocket.soTimeout = pollMs.toInt().coerceAtLeast(10)
            clientSocket.getInputStream().read()
            false
        } catch (_: SocketTimeoutException) {
            !handle.cancelled.get() && !clientSocket.isClosed
        } catch (_: IOException) {
            false
        } finally {
            runCatching {
                if (!clientSocket.isClosed) {
                    clientSocket.soTimeout = prevTimeout
                }
            }
        }
    }

    private fun writeHttpChunk(out: OutputStream, bytes: ByteArray) {
        val header = "${bytes.size.toString(16)}\r\n".toByteArray(StandardCharsets.US_ASCII)
        out.write(header)
        out.write(bytes)
        out.write(CRLF_BYTES)
        out.flush()
    }

    private fun openUpstream(
        endpoint: UpstreamEndpoint,
        httpMethod: String,
        reqPath: String,
        rangeHeader: String?,
        handle: ActiveStreamHandle,
    ): UpstreamResponse? {
        val upstreamSock = Socket()
        handle.upstreamSocket.set(upstreamSock)
        return try {
            upstreamSock.soTimeout = 15_000
            upstreamSock.connect(InetSocketAddress(endpoint.host, endpoint.port), 5_000)

            val reqBytes = buildString {
                append("$httpMethod $reqPath HTTP/1.1\r\n")
                append("Host: ${endpoint.host}:${endpoint.port}\r\n")
                if (!rangeHeader.isNullOrBlank()) {
                    append("Range: $rangeHeader\r\n")
                }
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
                runCatching { upstreamSock.close() }
                return null
            }

            val statusCode = parseStatusCode(statusLine)
            val statusReason = parseStatusReason(statusLine, statusCode)
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val headerLine = readHeaderLine(upstreamIn) ?: break
                if (headerLine.isEmpty()) break
                val colon = headerLine.indexOf(':')
                if (colon > 0) {
                    val name = headerLine.substring(0, colon).trim().lowercase()
                    val value = headerLine.substring(colon + 1).trim()
                    headers[name] = value
                }
            }
            UpstreamResponse(
                socket = upstreamSock,
                input = upstreamIn,
                statusCode = statusCode,
                statusReason = statusReason,
                headers = headers,
            )
        } catch (e: IOException) {
            runCatching { upstreamSock.close() }
            throw e
        }
    }

    private fun copyStream(
        input: InputStream,
        output: OutputStream,
        handle: ActiveStreamHandle,
        maxBytes: Long?,
    ): Long {
        val buffer = ByteArray(32_768)
        var totalCopied = 0L
        while (!handle.cancelled.get()) {
            val toRead = if (maxBytes != null) {
                val remaining = maxBytes - totalCopied
                if (remaining <= 0L) break
                minOf(buffer.size.toLong(), remaining).toInt()
            } else {
                buffer.size
            }
            val read = input.read(buffer, 0, toRead)
            if (read < 0) break
            if (read > 0) {
                totalCopied += read
                output.write(buffer, 0, read)
                output.flush()
            }
        }
        return totalCopied
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

    private fun parseStatusReason(statusLine: String, statusCode: Int): String {
        val parts = statusLine.split(' ', limit = 3)
        return if (parts.size >= 3 && parts[2].isNotBlank()) parts[2].trim() else when (statusCode) {
            200 -> "OK"
            206 -> "Partial Content"
            401 -> "Unauthorized"
            404 -> "Not Found"
            416 -> "Range Not Satisfiable"
            else -> "Response"
        }
    }

    private fun getOrFetchTicket(
        key: TicketCacheKey,
        forceRefresh: Boolean,
        fetcher: suspend () -> StreamTicket,
    ): StreamTicket {
        val now = nowMsProvider()
        if (!forceRefresh) {
            val cached = ticketCache[key]
            if (cached != null && (now - cached.fetchedAtMs) < ticketTtlMs) {
                return cached.ticket
            }
        }
        val fresh = runBlocking { fetcher() }
        ticketCache[key] = CachedTicket(ticket = fresh, fetchedAtMs = now)
        return fresh
    }

    private fun buildUpstreamPath(
        basePath: String,
        ticket: StreamTicket,
        appendPassProfile: Boolean,
    ): String {
        val normalizedPath = if (ticket.path.startsWith('/')) ticket.path else "/${ticket.path}"
        val sep = if ('?' in normalizedPath) '&' else '?'
        return if (appendPassProfile) {
            "$basePath$normalizedPath${sep}ticket=${ticket.ticket}&profile=pass"
        } else {
            "$basePath$normalizedPath${sep}ticket=${ticket.ticket}"
        }
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
        const val DEFAULT_GROWING_POLL_INTERVAL_MS: Long = 500L
        private const val GROWING_READ_CHUNK_BYTES: Int = 65_536
        private val CRLF_BYTES = "\r\n".toByteArray(StandardCharsets.US_ASCII)
        private val FINAL_CHUNK_BYTES = "0\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
    }
}
