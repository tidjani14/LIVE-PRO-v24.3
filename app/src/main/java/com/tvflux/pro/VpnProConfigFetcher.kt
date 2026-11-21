package com.tvflux.pro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class VpnProState {
    UNKNOWN,
    SUBSCRIBED,
    NOT_SUBSCRIBED,
    NETWORK_ERROR
}

data class VpnProFetchOutcome(
    val state: VpnProState,
    val configText: String? = null,
    val message: String = ""
)

/**
 * Disabled compatibility stub: external get_config verification was removed in favor of
 * direct Cloudflare WARP registration and connection without any intermediary server.
 */
object VpnProConfigFetcher {
    var isVpnProSubscribed by mutableStateOf(false)
    var isRechecking by mutableStateOf(false)
    var lastStatusMessage by mutableStateOf("")
    var activeEndpoint by mutableStateOf("")

    suspend fun fetchAndVerifyVpnProStatus(
        context: Context,
        deviceId: String = "",
        forceNetwork: Boolean = false
    ): VpnProFetchOutcome {
        isVpnProSubscribed = false
        return VpnProFetchOutcome(VpnProState.NOT_SUBSCRIBED, null, "Direct Cloudflare WARP active")
    }

    suspend fun getOrFetchConfig(context: Context, forceRefresh: Boolean = false): String? = null

    fun clearCachedConfig(context: Context) {
        activeEndpoint = ""
    }

    fun extractEndpoint(configText: String): String? {
        for (line in configText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Endpoint", ignoreCase = true) && trimmed.contains("=")) {
                val ep = trimmed.substringAfter("=").trim()
                if (ep.isNotBlank()) return ep
            }
        }
        return null
    }

    fun isVpnProConfig(configText: String): Boolean = false
}
