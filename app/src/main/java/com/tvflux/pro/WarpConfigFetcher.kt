package com.tvflux.pro

import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WarpConfigFetcher {
    private const val CACHED_WG_FILE = "live_pro_saved_wg_tunnel_direct_v26.conf"

    @Volatile
    var ENABLE_CLOUDFLARE_WARP_FALLBACK: Boolean = true

    @Volatile
    var activeDirectEndpoint: String = "162.159.192.1:2408"

    @Volatile
    private var memoryCachedConfig: String? = null

    val WARP_FAST_ENDPOINTS = listOf(
        "162.159.192.1:2408",
        "162.159.193.1:2408",
        "188.114.96.1:2408",
        "188.114.97.1:2408",
        "162.159.192.1:500",
        "162.159.193.1:4500",
        "188.114.96.1:1701",
        "162.159.192.1:854"
    )

    private val OFFICIAL_WARP_REG_URLS = listOf(
        "https://api.cloudflareclient.com/v0a2483/reg",
        "https://api.cloudflareclient.com/v0a2158/reg",
        "https://api.cloudflareclient.com/v0a2405/reg",
        "https://162.159.192.1/v0a2483/reg",
        "https://162.159.193.1/v0a2158/reg"
    )

    private val warpFetchHttpClient: OkHttpClient by lazy {
        ChannelRepository.bootstrapHttpClient.newBuilder()
            .dns(Dns.SYSTEM)
            .connectTimeout(4_000, TimeUnit.MILLISECONDS)
            .readTimeout(5_000, TimeUnit.MILLISECONDS)
            .callTimeout(6_000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun getOrFetchConfig(context: android.content.Context, forceRefresh: Boolean = false): String? {
        if (!forceRefresh) {
            val ramCfg = memoryCachedConfig
            if (!ramCfg.isNullOrBlank()) return ramCfg
        }
        return fetchFreshCloudflareWarpConfig(context, forceRefresh = forceRefresh)
    }

    suspend fun fetchFreshCloudflareWarpConfig(context: android.content.Context, forceRefresh: Boolean = true): String? =
        withContext(Dispatchers.IO) {
            val cacheFile = java.io.File(context.filesDir, CACHED_WG_FILE)
            if (!forceRefresh && cacheFile.exists()) {
                val saved = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
                val validSaved = sanitizeWireGuardConfig(saved)
                if (!validSaved.isNullOrBlank()) {
                    memoryCachedConfig = validSaved
                    return@withContext validSaved
                }
            }

            val directCfg = runCatching { registerDedicatedDeviceWarpConfig() }.getOrNull()
            if (!directCfg.isNullOrBlank()) {
                memoryCachedConfig = directCfg
                runCatching { cacheFile.writeText(directCfg, Charsets.UTF_8) }
                return@withContext directCfg
            }

            if (cacheFile.exists()) {
                val fallback = sanitizeWireGuardConfig(
                    runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
                )
                if (!fallback.isNullOrBlank()) memoryCachedConfig = fallback
                return@withContext fallback
            }
            null
        }

    fun invalidateCachedConfig(context: android.content.Context) {
        memoryCachedConfig = null
        runCatching { java.io.File(context.filesDir, CACHED_WG_FILE).delete() }
    }

    fun rewriteEndpoint(configText: String, newEndpoint: String): String {
        if (configText.isBlank()) return configText
        activeDirectEndpoint = newEndpoint
        return configText.lineSequence().map { line ->
            if (line.trim().startsWith("Endpoint", ignoreCase = true)) "Endpoint = $newEndpoint" else line
        }.joinToString("\n")
    }

    private fun registerDedicatedDeviceWarpConfig(): String? {
        for (regUrl in OFFICIAL_WARP_REG_URLS) {
            try {
                val keyPair = KeyPair()
                val privKeyB64 = keyPair.privateKey.toBase64()
                val pubKeyB64 = keyPair.publicKey.toBase64()
                val payload = JSONObject().apply {
                    put("key", pubKeyB64)
                    put("install_id", "")
                    put("fcm_token", "")
                    put("tos", "2025-01-01T00:00:00.000Z")
                    put("model", "Android")
                    put("type", "Android")
                    put("locale", "en_US")
                }.toString()

                val reqBuilder = Request.Builder()
                    .url(regUrl)
                    .header("User-Agent", "okhttp/4.12.0")
                    .header("Accept", "application/json")
                    .header("CF-Client-Version", "a-6.30-3596")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                if (!regUrl.contains("api.cloudflareclient.com")) {
                    reqBuilder.header("Host", "api.cloudflareclient.com")
                }

                val result = warpFetchHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string()?.trim().orEmpty()
                    if (!body.startsWith("{")) return@use null
                    val root = JSONObject(body)
                    val cfgObj = root.optJSONObject("config")
                        ?: root.optJSONObject("result")?.optJSONObject("config")
                        ?: return@use null
                    val addrsObj = cfgObj.optJSONObject("interface")?.optJSONObject("addresses")
                    val v4Addr = addrsObj?.optString("v4", "")?.trim().orEmpty()
                    val v6Addr = addrsObj?.optString("v6", "")?.trim().orEmpty()
                    val assignedAddresses = buildList {
                        if (v4Addr.isNotBlank()) add(if (v4Addr.contains("/")) v4Addr else "$v4Addr/32")
                        if (v6Addr.isNotBlank() && v6Addr.contains(":")) add(if (v6Addr.contains("/")) v6Addr else "$v6Addr/128")
                    }.ifEmpty { listOf("172.16.0.2/32") }.joinToString(", ")
                    val hasIpv6Address = assignedAddresses.contains(":")

                    val firstPeer = cfgObj.optJSONArray("peers")?.optJSONObject(0)
                    val peerPubKey = firstPeer?.optString("public_key", "")?.trim().orEmpty()
                        .ifBlank { "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" }
                    val peerV4Ep = firstPeer?.optJSONObject("endpoint")?.optString("v4", "")?.trim().orEmpty()
                        ?.takeIf { it.isNotBlank() && !it.startsWith("0.") }
                        ?: WARP_FAST_ENDPOINTS.first()
                    val finalEndpoint = if (peerV4Ep.contains(":")) peerV4Ep else "$peerV4Ep:2408"
                    activeDirectEndpoint = finalEndpoint

                    val builtRaw = buildString {
                        appendLine("[Interface]")
                        appendLine("PrivateKey = $privKeyB64")
                        appendLine("Address = $assignedAddresses")
                        appendLine(if (hasIpv6Address) "DNS = 1.1.1.1, 2606:4700:4700::1111" else "DNS = 1.1.1.1, 8.8.8.8")
                        appendLine("MTU = 1280")
                        appendLine("[Peer]")
                        appendLine("PublicKey = $peerPubKey")
                        appendLine(if (hasIpv6Address) "AllowedIPs = 0.0.0.0/0, ::/0" else "AllowedIPs = 0.0.0.0/0")
                        appendLine("Endpoint = $finalEndpoint")
                        appendLine("PersistentKeepalive = 15")
                    }
                    sanitizeWireGuardConfig(builtRaw)
                }
                if (!result.isNullOrBlank()) return result
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun sanitizeWireGuardConfig(raw: String): String? {
        if (raw.isBlank() || !raw.contains("[Interface]", ignoreCase = true)) return null
        val allowedInterfaceKeys = setOf("privatekey", "address", "dns", "mtu", "listenport")
        val allowedPeerKeys = setOf("publickey", "presharedkey", "allowedips", "endpoint", "persistentkeepalive")
        var currentSection = ""
        var hasPrivateKey = false
        var hasPublicKey = false
        var hasEndpoint = false
        var hasAddress = false
        val interfaceLines = mutableListOf<String>()
        val peerLines = mutableListOf<String>()

        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue
            if (trimmed.equals("[Interface]", ignoreCase = true)) { currentSection = "interface"; continue }
            if (trimmed.equals("[Peer]", ignoreCase = true)) { currentSection = "peer"; continue }
            if (trimmed.contains("=")) {
                val key = trimmed.substringBefore("=").trim().lowercase()
                var value = trimmed.substringAfter("=").trim()
                if (value.isEmpty()) continue
                if (currentSection == "interface" && key in allowedInterfaceKeys) {
                    if (key == "mtu" || key == "dns") continue
                    if (key == "privatekey") hasPrivateKey = true
                    if (key == "address") {
                        val validAddresses = value.split(",").map { it.trim() }
                            .filter { it.isNotBlank() && (it.contains(":") || it.matches(Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}(/\\d{1,2})?$"))) }
                        hasAddress = true
                        interfaceLines.add("Address = ${(validAddresses.ifEmpty { listOf("172.16.0.2/32") }).joinToString(", ")}")
                        continue
                    }
                    interfaceLines.add("${trimmed.substringBefore("=").trim()} = $value")
                } else if (currentSection == "peer" && key in allowedPeerKeys) {
                    if (key == "publickey") hasPublicKey = true
                    if (key == "persistentkeepalive" || key == "allowedips") continue
                    if (key == "endpoint") {
                        hasEndpoint = true
                        if (value.contains("engage.cloudflareclient.com", ignoreCase = true)) {
                            value = WARP_FAST_ENDPOINTS.first()
                        }
                    }
                    peerLines.add("${trimmed.substringBefore("=").trim()} = $value")
                }
            }
        }
        if (!hasPrivateKey || !hasPublicKey) return null
        if (!hasAddress) interfaceLines.add("Address = 172.16.0.2/32")
        val configHasIpv6 = interfaceLines.any { it.startsWith("Address =", true) && it.contains(":") }
        interfaceLines.add(if (configHasIpv6) "DNS = 1.1.1.1, 2606:4700:4700::1111" else "DNS = 1.1.1.1, 8.8.8.8")
        interfaceLines.add("MTU = 1280")
        if (!hasEndpoint) peerLines.add("Endpoint = ${WARP_FAST_ENDPOINTS.first()}")
        peerLines.add(if (configHasIpv6) "AllowedIPs = 0.0.0.0/0, ::/0" else "AllowedIPs = 0.0.0.0/0")
        peerLines.add("PersistentKeepalive = 15")
        return buildString {
            appendLine("[Interface]")
            interfaceLines.forEach { appendLine(it) }
            appendLine("[Peer]")
            peerLines.forEach { appendLine(it) }
        }.trim()
    }
}
