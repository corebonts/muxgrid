package io.github.corebonts.muxgrid.core.model

import java.security.MessageDigest

data class ServerProfile(
    val id: String = "default",
    val name: String = "Tvheadend",
    val host: String,
    val htspPort: Int = DEFAULT_HTSP_PORT,
    val httpPort: Int = DEFAULT_HTTP_PORT,
    val username: String = "",
    val password: String = "",
) {
    val normalizedHost: String
        get() = host.trim().removePrefix("[").removeSuffix("]")

    val formattedHttpHost: String
        get() {
            val raw = normalizedHost
            return if (':' in raw) "[$raw]" else raw
        }

    val isConfigured: Boolean
        get() = normalizedHost.isNotBlank() && htspPort in 1..65535 && httpPort in 1..65535

    val serverKey: String
        get() = if (isConfigured) "$formattedHttpHost:$htspPort" else ""

    val diagnosticsFingerprint: String
        get() {
            if (!isConfigured) return ""
            val payload = "$formattedHttpHost\u0000$htspPort\u0000$httpPort\u0000${username.trim()}\u0000$password"
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

    val httpBaseUrl: String?
        get() = if (isConfigured) "http://$formattedHttpHost:$httpPort" else null

    override fun toString(): String =
        "ServerProfile(id=$id, name=$name, host=$host, htspPort=$htspPort, httpPort=$httpPort, " +
            "username=$username, password=<redacted>)"

    companion object {
        const val DEFAULT_HTSP_PORT = 9982
        const val DEFAULT_HTTP_PORT = 9981
    }
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Syncing(
        val serverName: String,
        val serverVersion: String,
        val htspVersion: Int,
    ) : ConnectionState

    data class Connected(
        val serverName: String,
        val serverVersion: String,
        val htspVersion: Int,
        val serverClockOffsetMs: Long = 0L,
    ) : ConnectionState

    data class Error(
        val message: String,
        val isAuthError: Boolean = false,
    ) : ConnectionState
}

sealed interface ConnectionTestResult {
    data class Success(
        val serverName: String,
        val serverVersion: String,
        val htspVersion: Int,
        val hasDvrAccess: Boolean,
        val hasStreamingAccess: Boolean,
    ) : ConnectionTestResult

    data class Failure(
        val message: String,
        val isAuthError: Boolean = false,
    ) : ConnectionTestResult
}

data class StreamTicket(
    val path: String,
    val ticket: String,
)
