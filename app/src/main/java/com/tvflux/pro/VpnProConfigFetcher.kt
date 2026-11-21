package com.tvflux.pro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class VpnProState {
    IDLE,
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
 * تم إلغاء الاتصال بخادم VPS الخارجي نهائياً.
 * يعمل التطبيق الآن بالاتصال المباشر مع Cloudflare WARP فقط مع استثناء سيرفر الماين (LB Only).
 */
object VpnProConfigFetcher {
    var isVpnProSubscribed by mutableStateOf(false)
    var isRechecking by mutableStateOf(false)
    var lastStatusMessage by mutableStateOf("Cloudflare WARP Direct (LB Only)")
    var activeEndpoint by mutableStateOf(WarpConfigFetcher.WARP_FAST_ENDPOINTS.first())

    suspend fun fetchAndVerifyVpnProStatus(
        context: Context,
        deviceId: String = "",
        forceNetwork: Boolean = false
    ): VpnProFetchOutcome {
        isVpnProSubscribed = false
        lastStatusMessage = "Cloudflare WARP Direct (LB Only)"
        return VpnProFetchOutcome(VpnProState.NOT_SUBSCRIBED, null, lastStatusMessage)
    }

    suspend fun getOrFetchConfig(context: Context, forceRefresh: Boolean = false): String? = null

    fun clearCachedConfig(context: Context) {
        activeEndpoint = WarpConfigFetcher.WARP_FAST_ENDPOINTS.first()
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
