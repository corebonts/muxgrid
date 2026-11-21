package io.github.corebonts.muxgrid.core.data

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.Options
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import io.github.corebonts.muxgrid.core.htsp.HtspClient
import io.github.corebonts.muxgrid.core.htsp.HtspNoAccessException
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PiconModel(
    val channelId: Long,
    val rawIcon: String?,
)

/**
 * Coil 3 [Fetcher] that normalizes Tvheadend channel icons (`imagecache/<id>`, `/imagecache/<id>`,
 * or external `http(s)://` URLs), checks Coil's [DiskCache], and otherwise fetches via HTSP VFS
 * first with authenticated HTTP (Digest/Basic) fallback.
 */
class PiconFetcher(
    private val rawIcon: String,
    private val htspClient: HtspClient,
    private val httpClient: OkHttpClient,
    private val profileProvider: () -> ServerProfile,
    private val diskCache: DiskCache?,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val normalized = normalizeIconPath(rawIcon) ?: return null
        val profile = profileProvider()
        val diskKey = cacheKeyFor(normalized, profile)

        // 1. Check Coil DiskCache first so scrolling/restarts never re-hit HTSP/HTTP
        val cache = diskCache
        if (cache != null) {
            val snapshot = runCatching { cache.openSnapshot(diskKey) }.getOrNull()
            if (snapshot != null) {
                return SourceFetchResult(
                    source = ImageSource(
                        file = snapshot.data,
                        fileSystem = cache.fileSystem,
                        diskCacheKey = diskKey,
                        closeable = snapshot,
                    ),
                    mimeType = null,
                    dataSource = DataSource.DISK,
                )
            }
        }

        // 2. Primary network path: HTSP fileOpen / fileRead / fileClose
        var fetchedBytes: ByteArray? = null
        var mimeType: String? = null
        if (normalized is NormalizedIcon.ImageCache) {
            fetchedBytes = tryHtspRead(normalized.htspPath)
        }

        // 3. Fallback path: authenticated HTTP (Digest/Basic)
        if (fetchedBytes == null) {
            val url = when (normalized) {
                is NormalizedIcon.ExternalUrl -> normalized.url
                is NormalizedIcon.ImageCache -> {
                    val baseUrl = profile.httpBaseUrl ?: return null
                    "$baseUrl/${normalized.htspPath}"
                }
            }
            val request = Request.Builder().url(url).build()
            val result = httpClient.fetchBytes(request) ?: return null
            fetchedBytes = result.bytes
            mimeType = result.mimeType
        }

        val bytes = fetchedBytes
        if (cache != null && bytes.isNotEmpty()) {
            val snapshot = writeToDiskCache(cache, diskKey, bytes)
            if (snapshot != null) {
                return SourceFetchResult(
                    source = ImageSource(
                        file = snapshot.data,
                        fileSystem = cache.fileSystem,
                        diskCacheKey = diskKey,
                        closeable = snapshot,
                    ),
                    mimeType = mimeType,
                    dataSource = DataSource.NETWORK,
                )
            }
        }

        val buffer = Buffer().write(bytes)
        return SourceFetchResult(
            source = ImageSource(buffer, FileSystem.SYSTEM),
            mimeType = mimeType,
            dataSource = DataSource.NETWORK,
        )
    }

    private fun writeToDiskCache(
        cache: DiskCache,
        key: String,
        bytes: ByteArray,
    ): DiskCache.Snapshot? {
        val editor = runCatching { cache.openEditor(key) }.getOrNull() ?: return null
        return try {
            cache.fileSystem.write(editor.data) {
                write(bytes)
            }
            editor.commitAndOpenSnapshot()
        } catch (_: Exception) {
            runCatching { editor.abort() }
            null
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
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: HtspNoAccessException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            if (handleId != null) {
                withContext(NonCancellable) {
                    runCatching { htspClient.fileClose(handleId) }
                }
            }
        }
    }

    private data class HttpBytesResult(
        val bytes: ByteArray,
        val mimeType: String?,
    )

    private suspend fun OkHttpClient.fetchBytes(request: Request): HttpBytesResult? =
        suspendCancellableCoroutine { cont ->
            val call = newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!cont.isCancelled) {
                            cont.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { res ->
                            if (!res.isSuccessful) {
                                if (!cont.isCancelled) {
                                    cont.resumeWithException(
                                        IOException("Failed to fetch picon HTTP ${res.code} for ${request.url}"),
                                    )
                                }
                                return
                            }
                            try {
                                val bytes = res.body?.bytes()?.takeIf { it.isNotEmpty() }
                                val mimeType = res.header("Content-Type")
                                if (!cont.isCancelled) {
                                    cont.resume(bytes?.let { HttpBytesResult(it, mimeType) })
                                }
                            } catch (e: IOException) {
                                if (!cont.isCancelled) {
                                    cont.resumeWithException(e)
                                }
                            }
                        }
                    }
                },
            )
        }

    class KeyerImpl(
        private val profileProvider: () -> ServerProfile,
    ) : Keyer<PiconModel> {
        override fun key(data: PiconModel, options: Options): String? {
            val normalized = normalizeIconPath(data.rawIcon) ?: return null
            return cacheKeyFor(normalized, profileProvider())
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
                diskCache = imageLoader.diskCache,
            )
        }
    }

    sealed interface NormalizedIcon {
        data class ImageCache(val htspPath: String) : NormalizedIcon
        data class ExternalUrl(val url: String) : NormalizedIcon
    }

    companion object {
        private const val CHUNK_SIZE = 32_768L

        internal fun cacheKeyFor(normalized: NormalizedIcon, profile: ServerProfile): String = when (normalized) {
            is NormalizedIcon.ExternalUrl -> "picon:url:${normalized.url}"
            is NormalizedIcon.ImageCache -> {
                val key = profile.serverKey.ifEmpty { "${profile.normalizedHost}:${profile.htspPort}" }
                "picon:tvh:$key:${normalized.htspPath}"
            }
        }

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
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .components {
                    add(KeyerImpl(profileProvider))
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
