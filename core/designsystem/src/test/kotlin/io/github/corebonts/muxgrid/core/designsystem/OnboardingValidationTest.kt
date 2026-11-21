package io.github.corebonts.muxgrid.core.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingValidationTest {

    @Test
    fun `validateServerProfileInput accepts hostnames IPv4 and IPv6 addresses`() {
        val ipv4 = validateServerProfileInput("default", " 192.168.1.10 ", "9982", "9981", " mux ", "pw")
        assertTrue(ipv4 is ServerValidationResult.Valid)
        val p1 = (ipv4 as ServerValidationResult.Valid).profile
        assertEquals("192.168.1.10", p1.host)
        assertEquals("mux", p1.username)
        assertEquals("http://192.168.1.10:9981", p1.httpBaseUrl)

        val mdns = validateServerProfileInput("default", "tvheadend.local", "9982", "9981", "", "")
        assertTrue(mdns is ServerValidationResult.Valid)

        val bracketedIpv6 = validateServerProfileInput("default", "[fd00::1]", "9982", "9981", "", "")
        assertTrue(bracketedIpv6 is ServerValidationResult.Valid)
        val p2 = (bracketedIpv6 as ServerValidationResult.Valid).profile
        assertEquals("fd00::1", p2.host)
        assertEquals("http://[fd00::1]:9981", p2.httpBaseUrl)

        val bareIpv6 = validateServerProfileInput("default", "fe80::1%eth0", "9982", "9981", "", "")
        assertTrue(bareIpv6 is ServerValidationResult.Valid)
    }

    @Test
    fun `validateServerProfileInput rejects schemes ports spaces and invalid addresses`() {
        val cases = listOf(
            "",
            "   ",
            "http://tv.local",
            "tv.local/path",
            "tv local",
            "192.168.1.10:9981",
            "999.0.0.1",
            "[::1",
            ":::1",
            "-badhost",
            "bad..host",
        )
        cases.forEach { badHost ->
            val res = validateServerProfileInput("default", badHost, "9982", "9981", "", "")
            assertTrue(res is ServerValidationResult.Invalid, "Expected '$badHost' to be rejected")
        }

        assertTrue(
            validateServerProfileInput("default", "tv.local", "0", "9981", "", "") is ServerValidationResult.Invalid,
        )
        assertTrue(
            validateServerProfileInput("default", "tv.local", "9982", "70000", "", "") is ServerValidationResult.Invalid,
        )
    }
}
