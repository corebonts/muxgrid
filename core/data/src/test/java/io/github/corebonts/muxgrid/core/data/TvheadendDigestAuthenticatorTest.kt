package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.model.ServerProfile
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

class TvheadendDigestAuthenticatorTest {

    private val defaultProfile = ServerProfile(
        host = "tvh.local",
        htspPort = 9982,
        httpPort = 9981,
        username = "alice",
        password = "secret",
    )

    private fun challengeResponse(
        url: String,
        wwwAuthenticate: List<String>,
        prior: Response? = null,
    ): Response {
        val request = Request.Builder().url(url).build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .apply {
                for (header in wwwAuthenticate) {
                    addHeader("WWW-Authenticate", header)
                }
            }
            .priorResponse(prior)
            .build()
    }

    @Test
    fun `refuses to send credentials to external host or wrong port`() {
        val auth = TvheadendDigestAuthenticator { defaultProfile }

        val externalHostResp = challengeResponse(
            url = "https://evil.example.com:9981/picon.png",
            wwwAuthenticate = listOf("Basic realm=\"evil\""),
        )
        assertNull(auth.authenticate(null, externalHostResp))

        val wrongPortResp = challengeResponse(
            url = "http://tvh.local:8080/imagecache/1",
            wwwAuthenticate = listOf("Digest realm=\"tvheadend\", nonce=\"abc\""),
        )
        assertNull(auth.authenticate(null, wrongPortResp))

        val unconfiguredAuth = TvheadendDigestAuthenticator {
            defaultProfile.copy(host = "")
        }
        val matchingAfterUnconfigured = challengeResponse(
            url = "http://tvh.local:9981/imagecache/1",
            wwwAuthenticate = listOf("Basic realm=\"tvheadend\""),
        )
        assertNull(unconfiguredAuth.authenticate(null, matchingAfterUnconfigured))
    }

    @Test
    fun `matches configured host case-insensitively and handles bracketed IPv6`() {
        val hostAuth = TvheadendDigestAuthenticator { defaultProfile }
        val caseResp = challengeResponse(
            url = "http://TVH.LOCAL:9981/imagecache/42",
            wwwAuthenticate = listOf("Basic realm=\"tvheadend\""),
        )
        assertNotNull(hostAuth.authenticate(null, caseResp))

        val ipv6Auth = TvheadendDigestAuthenticator {
            defaultProfile.copy(host = "[::1]")
        }
        val ipv6Resp = challengeResponse(
            url = "http://[::1]:9981/imagecache/42",
            wwwAuthenticate = listOf("Basic realm=\"tvheadend\""),
        )
        assertNotNull(ipv6Auth.authenticate(null, ipv6Resp))
    }

    @Test
    fun `computes valid MD5 and SHA-256 Digest headers for matching host and port`() {
        val auth = TvheadendDigestAuthenticator { defaultProfile }
        val shaResp = challengeResponse(
            url = "http://tvh.local:9981/imagecache/7?size=1",
            wwwAuthenticate = listOf(
                "Digest realm=\"tvheadend\", nonce=\"nonce123\", qop=\"auth\", algorithm=SHA-256, opaque=\"op1\"",
            ),
        )
        val authedReq = auth.authenticate(null, shaResp)
        assertNotNull(authedReq)
        val header = authedReq!!.header("Authorization")!!
        assertTrue(header.startsWith("Digest "))
        assertTrue(header.contains("algorithm=SHA-256"))
        assertTrue(header.contains("qop=auth"))
        assertTrue(header.contains("uri=\"/imagecache/7?size=1\""))
        assertTrue(header.contains("opaque=\"op1\""))

        // Verify response digest matches RFC 7616 SHA-256 calculation
        val nc = Regex("""nc=([0-9a-f]+)""").find(header)!!.groupValues[1]
        val cnonce = Regex("""cnonce="([^"]+)"""").find(header)!!.groupValues[1]
        val actualDigest = Regex("""response="([0-9a-f]+)"""").find(header)!!.groupValues[1]
        val ha1 = sha256Hex("alice:tvheadend:secret")
        val ha2 = sha256Hex("GET:/imagecache/7?size=1")
        val expectedDigest = sha256Hex("$ha1:nonce123:$nc:$cnonce:auth:$ha2")
        assertEquals(expectedDigest, actualDigest)
    }

    @Test
    fun `rejects unsupported Digest algorithms and unsupported qop`() {
        val auth = TvheadendDigestAuthenticator { defaultProfile }

        val sha512Resp = challengeResponse(
            url = "http://tvh.local:9981/imagecache/1",
            wwwAuthenticate = listOf("Digest realm=\"tvh\", nonce=\"n1\", algorithm=SHA-512-256"),
        )
        assertNull(auth.authenticate(null, sha512Resp))

        val md5SessResp = challengeResponse(
            url = "http://tvh.local:9981/imagecache/1",
            wwwAuthenticate = listOf("Digest realm=\"tvh\", nonce=\"n1\", algorithm=MD5-sess"),
        )
        assertNull(auth.authenticate(null, md5SessResp))

        val authIntOnlyResp = challengeResponse(
            url = "http://tvh.local:9981/imagecache/1",
            wwwAuthenticate = listOf("Digest realm=\"tvh\", nonce=\"n1\", qop=\"auth-int\""),
        )
        assertNull(auth.authenticate(null, authIntOnlyResp))

        // Picks the supported challenge when server advertises unsupported then supported
        val multiResp = challengeResponse(
            url = "http://tvh.local:9981/imagecache/1",
            wwwAuthenticate = listOf(
                "Digest realm=\"tvh\", nonce=\"n1\", algorithm=SHA-512-256",
                "Digest realm=\"tvh\", nonce=\"n2\", algorithm=MD5",
            ),
        )
        val req = auth.authenticate(null, multiResp)
        assertNotNull(req)
        assertTrue(req!!.header("Authorization")!!.contains("algorithm=MD5"))
    }

    @Test
    fun `escapes quotes and backslashes in Digest params and rejects control chars`() {
        val auth = TvheadendDigestAuthenticator {
            defaultProfile.copy(username = """bob"a\b""")
        }
        val resp = challengeResponse(
            url = "http://tvh.local:9981/imagecache/1",
            wwwAuthenticate = listOf(
                """Digest realm="tvh\"realm", nonce="n\\1", opaque="op\"q"""",
            ),
        )
        val req = auth.authenticate(null, resp)
        assertNotNull(req)
        val header = req!!.header("Authorization")!!
        assertTrue(header.contains("""username="bob\"a\\b""""))
        assertTrue(header.contains("""realm="tvh\"realm""""))
        assertTrue(header.contains("""nonce="n\\1""""))
        assertTrue(header.contains("""opaque="op\"q""""))

        // Rejects control characters in username
        val badUserAuth = TvheadendDigestAuthenticator {
            defaultProfile.copy(username = "bob\r\nX-Injected: 1")
        }
        assertNull(
            badUserAuth.authenticate(
                null,
                challengeResponse(
                    url = "http://tvh.local:9981/imagecache/1",
                    wwwAuthenticate = listOf("Digest realm=\"tvh\", nonce=\"n1\""),
                ),
            ),
        )
    }

    @Test
    fun `does not send credentials to external URL in end-to-end OkHttp 401 exchange`() {
        val seenAuthHeader = AtomicReference<String?>(null)
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { externalServer ->
            val thread = Thread {
                externalServer.accept().use { sock ->
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                    reader.readLine()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) {
                            seenAuthHeader.set(line)
                        }
                    }
                    sock.getOutputStream().write(
                        (
                            "HTTP/1.1 401 Unauthorized\r\n" +
                                "WWW-Authenticate: Basic realm=\"external\"\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(StandardCharsets.US_ASCII),
                    )
                    sock.getOutputStream().flush()
                }
            }.apply { start() }

            val client = OkHttpClient.Builder()
                .authenticator(
                    TvheadendDigestAuthenticator {
                        defaultProfile.copy(host = "192.0.2.10", httpPort = externalServer.localPort)
                    },
                )
                .build()

            try {
                val resp = client.newCall(
                    Request.Builder()
                        .url("http://127.0.0.1:${externalServer.localPort}/logo.png")
                        .build(),
                ).execute()
                assertEquals(401, resp.code)
                resp.close()
                assertNull(seenAuthHeader.get())
                assertFalse(seenAuthHeader.get() != null)
            } finally {
                thread.join(2_000)
            }
        }
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
