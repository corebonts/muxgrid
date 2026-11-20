package io.github.corebonts.muxgrid.core.data

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.Options
import io.github.corebonts.muxgrid.core.htsp.HtspClient
import io.github.corebonts.muxgrid.core.htsp.HtspNoAccessException
import io.github.corebonts.muxgrid.core.model.ServerProfile
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class PiconModel(
    val channelId: Long,
    val rawIcon: String?,
)

class PiconFetcher(
    private val rawIcon: String,
    private val htspClient: HtspClient,
    private val httpClient: OkHttpClient,
    private val profileProvider: () -> ServerProfile,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val normalized = normalizeIconPath(rawIcon) ?: return null
        if (normalized is NormalizedIcon.ImageCache) {
            // Primary path: HTSP fileOpen / fileRead / fileClose
            val bytes = tryHtspRead(normalized.htspPath)
            if (bytes != null) {
                val buffer = Buffer().write(bytes)
                return SourceFetchResult(
                    source = ImageSource(buffer, FileSystem.SYSTEM),
                    mimeType = null,
                    dataSource = DataSource.NETWORK,
                )
            }
        }

        // Fallback path: authenticated HTTP (Digest/Basic)
        val profile = profileProvider()
        val url = when (normalized) {
            is NormalizedIcon.ExternalUrl -> normalized.url
            is NormalizedIcon.ImageCache -> {
                if (!profile.isConfigured) return null
                "http://${profile.host}:${profile.httpPort}/${normalized.htspPath}"
            }
        }

        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch picon HTTP ${response.code} for $url")
            }
            val bodyBytes = response.body?.bytes() ?: return null
            val buffer = Buffer().write(bodyBytes)
            return SourceFetchResult(
                source = ImageSource(buffer, FileSystem.SYSTEM),
                mimeType = response.header("Content-Type"),
                dataSource = DataSource.NETWORK,
            )
        }
    }

    private suspend fun tryHtspRead(htspPath: String): ByteArray? {
        var handleId: Long? = null
        return try {
            val info = htspClient.fileOpen(htspPath)
            handleId = info.handleId
            val out = java.io.ByteArrayOutputStream(info.size.coerceIn(1024L, 512_000L).toInt())
            var offset = 0L
            while (true) {
                val chunk = htspClient.fileRead(info.handleId, CHUNK_SIZE, offset)
                if (chunk.isEmpty()) break
                out.write(chunk)
                offset += chunk.size
                if (info.size in 1..offset) break
            }
            out.toByteArray()
        } catch (_: HtspNoAccessException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            if (handleId != null) {
                runCatching { htspClient.fileClose(handleId) }
            }
        }
    }

    class Factory(
        private val htspClient: HtspClient,
        private val httpClient: OkHttpClient,
        private val profileProvider: () -> ServerProfile,
    ) : Fetcher.Factory<PiconModel> {
        override fun create(
            data: PiconModel,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val icon = data.rawIcon?.takeIf { it.isNotBlank() } ?: return null
            return PiconFetcher(
                rawIcon = icon,
                htspClient = htspClient,
                httpClient = httpClient,
                profileProvider = profileProvider,
            )
        }
    }

    sealed interface NormalizedIcon {
        data class ImageCache(val htspPath: String) : NormalizedIcon
        data class ExternalUrl(val url: String) : NormalizedIcon
    }

    companion object {
        private const val CHUNK_SIZE = 32_768L

        fun normalizeIconPath(rawIcon: String?): NormalizedIcon? {
            val trimmed = rawIcon?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return NormalizedIcon.ExternalUrl(trimmed)
            }
            val cleaned = trimmed.removePrefix("/")
            return if (cleaned.startsWith("imagecache/")) {
                NormalizedIcon.ImageCache(cleaned)
            } else {
                NormalizedIcon.ImageCache("imagecache/$cleaned")
            }
        }

        fun createImageLoader(
            context: Context,
            htspClient: HtspClient,
            profileProvider: () -> ServerProfile,
        ): ImageLoader {
            val okHttp = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .authenticator(TvheadendDigestAuthenticator(profileProvider))
                .build()

            return ImageLoader.Builder(context)
                .components {
                    add(Factory(htspClient, okHttp, profileProvider))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.15)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.cacheDir, "picon_cache").toOkioPath())
                        .maxSizeBytes(32L * 1024L * 1024L)
                        .build()
                }
                .build()
        }
    }
}

/**
 * Supports Tvheadend 4.3's default HTTP Digest (MD5 / SHA-256) and Basic auth fallback.
 */
internal class TvheadendDigestAuthenticator(
    private val profileProvider: () -> ServerProfile,
) : Authenticator {
    private val ncCounter = AtomicInteger(1)
    private val random = SecureRandom()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val profile = profileProvider()
        if (profile.username.isBlank() && profile.password.isBlank()) return null

        val challenges = response.challenges()
        val digestChallenge = challenges.firstOrNull { it.scheme.equals("Digest", ignoreCase = true) }
        if (digestChallenge != null) {
            val authParams = digestChallenge.authParams
            val realm = authParams["realm"] ?: return null
            val nonce = authParams["nonce"] ?: return null
            val qopRaw = authParams["qop"]
            val opaque = authParams["opaque"]
            val algo = authParams["algorithm"] ?: "MD5"
            val hashAlg = when {
                algo.equals("SHA-256", ignoreCase = true) -> "SHA-256"
                else -> "MD5"
            }

            val uri = response.request.url.encodedPath +
                (response.request.url.encodedQuery?.let { "?$it" } ?: "")
            val method = response.request.method
            val ha1 = hashHex(hashAlg, "${profile.username}:$realm:${profile.password}")
            val ha2 = hashHex(hashAlg, "$method:$uri")

            val headerValue = if (qopRaw != null && qopRaw.split(',').any { it.trim() == "auth" }) {
                val nc = "%08x".format(ncCounter.getAndIncrement())
                val cnonceBytes = ByteArray(8).also { random.nextBytes(it) }
                val cnonce = cnonceBytes.joinToString("") { "%02x".format(it) }
                val respDigest = hashHex(hashAlg, "$ha1:$nonce:$nc:$cnonce:auth:$ha2")
                buildString {
                    append("Digest username=\"${profile.username}\", ")
                    append("realm=\"$realm\", ")
                    append("nonce=\"$nonce\", ")
                    append("uri=\"$uri\", ")
                    append("algorithm=$algo, ")
                    append("qop=auth, ")
                    append("nc=$nc, ")
                    append("cnonce=\"$cnonce\", ")
                    append("response=\"$respDigest\"")
                    if (!opaque.isNullOrEmpty()) append(", opaque=\"$opaque\"")
                }
            } else {
                val respDigest = hashHex(hashAlg, "$ha1:$nonce:$ha2")
                buildString {
                    append("Digest username=\"${profile.username}\", ")
                    append("realm=\"$realm\", ")
                    append("nonce=\"$nonce\", ")
                    append("uri=\"$uri\", ")
                    append("algorithm=$algo, ")
                    append("response=\"$respDigest\"")
                    if (!opaque.isNullOrEmpty()) append(", opaque=\"$opaque\"")
                }
            }

            return response.request.newBuilder()
                .header("Authorization", headerValue)
                .build()
        }

        val basicChallenge = challenges.firstOrNull { it.scheme.equals("Basic", ignoreCase = true) }
        if (basicChallenge != null) {
            return response.request.newBuilder()
                .header("Authorization", Credentials.basic(profile.username, profile.password))
                .build()
        }

        return null
    }

    private fun hashHex(algorithm: String, input: String): String {
        val digest = MessageDigest.getInstance(algorithm)
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
