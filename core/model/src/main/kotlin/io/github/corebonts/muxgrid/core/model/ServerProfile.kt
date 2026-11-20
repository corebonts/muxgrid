package io.github.corebonts.muxgrid.core.model

data class ServerProfile(
    val id: String = "default",
    val name: String = "Homelab Tvheadend",
    val host: String,
    val htspPort: Int = 9982,
    val httpPort: Int = 9981,
    val username: String = "",
    val password: String = "",
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && htspPort in 1..65535 && httpPort in 1..65535
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
