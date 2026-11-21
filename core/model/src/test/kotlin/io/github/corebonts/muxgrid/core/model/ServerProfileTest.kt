package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerProfileTest {

    @Test
    fun `toString redacts password while keeping server metadata`() {
        val secret = "super-secret-pass-123"
        val profile = ServerProfile(
            id = "default",
            name = "Home TVH",
            host = "tvheadend.local",
            htspPort = ServerProfile.DEFAULT_HTSP_PORT,
            httpPort = ServerProfile.DEFAULT_HTTP_PORT,
            username = "admin",
            password = secret,
        )

        val rendered = profile.toString()
        assertFalse(rendered.contains(secret), "toString() must not expose password")
        assertTrue(rendered.contains("password=<redacted>"))
        assertTrue(rendered.contains("host=tvheadend.local"))
        assertTrue(rendered.contains("username=admin"))
    }

    @Test
    fun `serverKey and httpBaseUrl handle IPv4 hostnames IPv6 and unconfigured profiles`() {
        val ipv4 = ServerProfile(host = " 192.168.1.50 ", htspPort = 9982, httpPort = 9981)
        assertEquals("192.168.1.50:9982", ipv4.serverKey)
        assertEquals("http://192.168.1.50:9981", ipv4.httpBaseUrl)

        val ipv6Raw = ServerProfile(host = "fd00::10", htspPort = 9982, httpPort = 9981)
        assertEquals("[fd00::10]:9982", ipv6Raw.serverKey)
        assertEquals("http://[fd00::10]:9981", ipv6Raw.httpBaseUrl)

        val ipv6Bracketed = ServerProfile(host = "[::1]", htspPort = 9992, httpPort = 9991)
        assertEquals("::1", ipv6Bracketed.normalizedHost)
        assertEquals("[::1]:9992", ipv6Bracketed.serverKey)
        assertEquals("http://[::1]:9991", ipv6Bracketed.httpBaseUrl)

        val blank = ServerProfile(host = "  ")
        assertFalse(blank.isConfigured)
        assertEquals("", blank.serverKey)
        assertNull(blank.httpBaseUrl)

        val emptyBrackets = ServerProfile(host = "[]")
        assertFalse(emptyBrackets.isConfigured)
        assertNull(emptyBrackets.httpBaseUrl)
    }
}
