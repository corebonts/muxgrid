package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertFalse
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
            htspPort = 9982,
            httpPort = 9981,
            username = "admin",
            password = secret,
        )

        val rendered = profile.toString()
        assertFalse(rendered.contains(secret), "toString() must not expose password")
        assertTrue(rendered.contains("password=<redacted>"))
        assertTrue(rendered.contains("host=tvheadend.local"))
        assertTrue(rendered.contains("username=admin"))
    }
}
