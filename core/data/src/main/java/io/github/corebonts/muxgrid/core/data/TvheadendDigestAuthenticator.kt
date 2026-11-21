package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.model.ServerProfile
import okhttp3.Authenticator
import okhttp3.Challenge
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * Supports Tvheadend 4.3's default HTTP Digest (MD5 / SHA-256) and Basic auth fallback,
 * strictly scoped to the configured Tvheadend server host and HTTP port.
 */
internal class TvheadendDigestAuthenticator(
    private val profileProvider: () -> ServerProfile,
) : Authenticator {
    private val ncCounter = AtomicInteger(1)
    private val random = SecureRandom()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val profile = profileProvider()
        if (!profile.isConfigured) return null
        if (profile.username.isBlank() && profile.password.isBlank()) return null
        if (containsControlChars(profile.username)) return null

        val url = response.request.url
        val expectedHost = profile.host.trim().removePrefix("[").removeSuffix("]")
        if (!url.host.equals(expectedHost, ignoreCase = true) || url.port != profile.httpPort) {
            return null
        }

        val challenges = response.challenges()
        for (challenge in challenges) {
            if (!challenge.scheme.equals("Digest", ignoreCase = true)) continue
            val authHeader = buildDigestHeader(challenge, profile, response.request) ?: continue
            return response.request.newBuilder()
                .header("Authorization", authHeader)
                .build()
        }

        val basicChallenge = challenges.firstOrNull { it.scheme.equals("Basic", ignoreCase = true) }
        if (basicChallenge != null) {
            return response.request.newBuilder()
                .header("Authorization", Credentials.basic(profile.username, profile.password, StandardCharsets.UTF_8))
                .build()
        }

        return null
    }

    private fun buildDigestHeader(
        challenge: Challenge,
        profile: ServerProfile,
        request: Request,
    ): String? {
        val authParams = challenge.authParams
        val realm = authParams["realm"] ?: return null
        val nonce = authParams["nonce"] ?: return null
        val opaque = authParams["opaque"]
        if (containsControlChars(realm) || containsControlChars(nonce) ||
            (opaque != null && containsControlChars(opaque))
        ) {
            return null
        }

        val rawAlgo = authParams["algorithm"]?.trim()
        val (hashAlg, headerAlgo) = when {
            rawAlgo.isNullOrEmpty() || rawAlgo.equals("MD5", ignoreCase = true) -> "MD5" to "MD5"
            rawAlgo.equals("SHA-256", ignoreCase = true) -> "SHA-256" to "SHA-256"
            else -> return null
        }

        val qopRaw = authParams["qop"]
        val useQopAuth = if (qopRaw != null) {
            val tokens = qopRaw.split(',').map { it.trim() }
            if (tokens.none { it.equals("auth", ignoreCase = true) }) {
                return null
            }
            true
        } else {
            false
        }

        val uri = request.url.encodedPath +
            (request.url.encodedQuery?.let { "?$it" } ?: "")
        val method = request.method
        val ha1 = hashHex(hashAlg, "${profile.username}:$realm:${profile.password}")
        val ha2 = hashHex(hashAlg, "$method:$uri")

        val escapedUser = escapeQuotedString(profile.username)
        val escapedRealm = escapeQuotedString(realm)
        val escapedNonce = escapeQuotedString(nonce)
        val escapedUri = escapeQuotedString(uri)

        return if (useQopAuth) {
            val nc = "%08x".format(ncCounter.getAndIncrement())
            val cnonceBytes = ByteArray(8).also { random.nextBytes(it) }
            val cnonce = cnonceBytes.joinToString("") { "%02x".format(it) }
            val respDigest = hashHex(hashAlg, "$ha1:$nonce:$nc:$cnonce:auth:$ha2")
            buildString {
                append("Digest username=\"$escapedUser\", ")
                append("realm=\"$escapedRealm\", ")
                append("nonce=\"$escapedNonce\", ")
                append("uri=\"$escapedUri\", ")
                append("algorithm=$headerAlgo, ")
                append("qop=auth, ")
                append("nc=$nc, ")
                append("cnonce=\"$cnonce\", ")
                append("response=\"$respDigest\"")
                if (!opaque.isNullOrEmpty()) append(", opaque=\"${escapeQuotedString(opaque)}\"")
            }
        } else {
            val respDigest = hashHex(hashAlg, "$ha1:$nonce:$ha2")
            buildString {
                append("Digest username=\"$escapedUser\", ")
                append("realm=\"$escapedRealm\", ")
                append("nonce=\"$escapedNonce\", ")
                append("uri=\"$escapedUri\", ")
                append("algorithm=$headerAlgo, ")
                append("response=\"$respDigest\"")
                if (!opaque.isNullOrEmpty()) append(", opaque=\"${escapeQuotedString(opaque)}\"")
            }
        }
    }

    private fun escapeQuotedString(value: String): String =
        buildString(value.length) {
            for (ch in value) {
                if (ch == '\\' || ch == '"') {
                    append('\\')
                }
                append(ch)
            }
        }

    private fun containsControlChars(value: String): Boolean =
        value.any { it.code < 0x20 || it.code == 0x7F }

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
