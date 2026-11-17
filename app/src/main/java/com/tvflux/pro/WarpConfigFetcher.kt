package com.tvflux.pro

import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WarpConfigFetcher {
    private const val CACHED_WG_FILE = "live_pro_saved_wg_tunnel_v25.conf"

    @Volatile
    var ENABLE_CLOUDFLARE_WARP_FALLBACK: Boolean = true

    @Volatile
    private var memoryCachedConfig: String? = null

    /**
     * Multi-port Cloudflare WARP Anycast endpoints:
     * Port 2408 (Official WARP), 500 (IKEv2), 4500 (NAT-T), and 1701 (L2TP) across 162.159.x.x and 188.114.x.x.
     */
    val WARP_FAST_ENDPOINTS = listOf(
        "162.159.192.1:2408",
        "162.159.192.1:500",
        "162.159.193.1:4500",
        "188.114.96.1:2408",
        "188.114.96.1:500",
        "188.114.97.1:4500",
        "162.159.195.1:1701"
    )

    private val WARP_API_URL: String by lazy {
        listOf("https://", "tvflux4k", ".com", "/test/", "warp.php").joinToString("")
    }

    private val WARP_IBO_VPNS_URL: String by lazy {
        listOf("https://", "tvflux4k", ".com", "/test/ibo/", "vpns.php").joinToString("")
    }

    private val OFFICIAL_WARP_REG_URL: String by lazy {
        listOf("https://", "api.cloudflareclient.com", "/v0a2158/reg").joinToString("")
    }

    /**
     * Dedicated isolated OkHttpClient (Dns.SYSTEM, independent connection pool)
     * so `flushAllSocketsBeforeVpnSync()` NEVER cancels in-flight WARP config fetches!
     */
    private val warpFetchHttpClient: OkHttpClient by lazy {
        ChannelRepository.bootstrapHttpClient.newBuilder()
            .dns(Dns.SYSTEM)
            .connectTimeout(3_000, TimeUnit.MILLISECONDS)
            .readTimeout(3_500, TimeUnit.MILLISECONDS)
            .callTimeout(4_500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun getOrFetchConfig(context: android.content.Context, forceRefresh: Boolean = false): String? {
        if (!forceRefresh) {
            val ramCfg = memoryCachedConfig
            if (!ramCfg.isNullOrBlank()) return ramCfg
        }
        if (VpnProConfigFetcher.isVpnProSubscribed) {
            val vpnProConfig = VpnProConfigFetcher.getOrFetchConfig(context, forceRefresh = forceRefresh)
            if (!vpnProConfig.isNullOrBlank()) {
                memoryCachedConfig = vpnProConfig
                return vpnProConfig
            }
        }
        if (!ENABLE_CLOUDFLARE_WARP_FALLBACK) {
            return null
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

            // Race direct Cloudflare WARP keypair registration AND server warp.php / vpns.php concurrently!
            val (directCfg, serverCfg) = coroutineScope {
                val dDirect = async { runCatching { registerDedicatedDeviceWarpConfig() }.getOrNull() }
                val dServer = async { runCatching { fetchConfigFromServer() }.getOrNull() }
                val d = dDirect.await()
                if (!d.isNullOrBlank()) {
                    dServer.cancel()
                    d to null
                } else {
                    null to dServer.await()
                }
            }
            val resolvedFresh = directCfg?.takeIf { it.isNotBlank() } ?: serverCfg?.takeIf { it.isNotBlank() }
            if (!resolvedFresh.isNullOrBlank()) {
                memoryCachedConfig = resolvedFresh
                runCatching { cacheFile.writeText(resolvedFresh, Charsets.UTF_8) }
                return@withContext resolvedFresh
            }

            if (cacheFile.exists()) {
                val fallback = sanitizeWireGuardConfig(
                    runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
                )
                if (!fallback.isNullOrBlank()) {
                    memoryCachedConfig = fallback
                }
                return@withContext fallback
            }
            null
        }

    fun invalidateCachedConfig(context: android.content.Context) {
        memoryCachedConfig = null
        runCatching {
            java.io.File(context.filesDir, CACHED_WG_FILE).delete()
        }
    }

    fun rewriteEndpoint(configText: String, newEndpoint: String): String {
        if (configText.isBlank()) return configText
        return configText.lineSequence().map { line ->
            if (line.trim().startsWith("Endpoint", ignoreCase = true)) {
                "Endpoint = $newEndpoint"
            } else {
                line
            }
        }.joinToString("\n")
    }

    private fun registerDedicatedDeviceWarpConfig(): String? {
        val regUrls = listOf(
            OFFICIAL_WARP_REG_URL,
            "https://api.cloudflareclient.com/v0a2483/reg",
            "https://162.159.192.1/v0a2158/reg"
        )
        for (regUrl in regUrls) {
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
                    val cfgObj = root.optJSONObject("config") ?: root.optJSONObject("result")?.optJSONObject("config") ?: return@use null
                    val ifaceObj = cfgObj.optJSONObject("interface")
                    val addrsObj = ifaceObj?.optJSONObject("addresses")
                    val v4Addr = addrsObj?.optString("v4", "172.16.0.2")?.trim().orEmpty().ifBlank { "172.16.0.2" }
                    val cleanV4 = if (v4Addr.contains("/")) v4Addr else "$v4Addr/32"

                    val peersArr = cfgObj.optJSONArray("peers")
                    val firstPeer = peersArr?.optJSONObject(0)
                    val peerPubKey = firstPeer?.optString("public_key", "")?.trim().orEmpty()
                        .ifBlank { "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" }

                    val builtRaw = buildString {
                        appendLine("[Interface]")
                        appendLine("PrivateKey = $privKeyB64")
                        appendLine("Address = $cleanV4")
                        appendLine("DNS = 1.1.1.1, 8.8.8.8")
                        appendLine("MTU = 1280")
                        appendLine("[Peer]")
                        appendLine("PublicKey = $peerPubKey")
                        appendLine("AllowedIPs = 0.0.0.0/0")
                        appendLine("Endpoint = ${WARP_FAST_ENDPOINTS.first()}")
                        appendLine("PersistentKeepalive = 25")
                    }
                    sanitizeWireGuardConfig(builtRaw)
                }
                if (!result.isNullOrBlank()) return result
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun extractWireGuardFromJson(json: JSONObject): String {
        val c1 = json.optString("config_text").trim()
        val c2 = json.optString("config").trim()
        val c3 = json.optString("data").trim()
        val directText = when {
            c1.contains("[Interface]", ignoreCase = true) -> c1
            c2.contains("[Interface]", ignoreCase = true) -> c2
            c3.contains("[Interface]", ignoreCase = true) -> c3
            else -> ""
        }
        if (directText.isNotBlank()) return directText

        val subObj = json.optJSONObject("config") ?: json.optJSONObject("data") ?: json.optJSONObject("wireguard") ?: json
        val privKey = subObj.optString("PrivateKey", subObj.optString("private_key", subObj.optString("privateKey", ""))).trim()
        val pubKey = subObj.optString("PublicKey", subObj.optString("public_key", subObj.optString("publicKey", "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="))).trim()
        val address = subObj.optString("Address", subObj.optString("address", "172.16.0.2/32")).trim().ifBlank { "172.16.0.2/32" }
        val endpoint = subObj.optString("Endpoint", subObj.optString("endpoint", WARP_FAST_ENDPOINTS.first())).trim().ifBlank { WARP_FAST_ENDPOINTS.first() }
        if (privKey.isNotBlank() && pubKey.isNotBlank()) {
            return buildString {
                appendLine("[Interface]")
                appendLine("PrivateKey = $privKey")
                appendLine("Address = $address")
                appendLine("DNS = 1.1.1.1, 8.8.8.8")
                appendLine("MTU = 1280")
                appendLine("[Peer]")
                appendLine("PublicKey = $pubKey")
                appendLine("AllowedIPs = 0.0.0.0/0")
                appendLine("Endpoint = $endpoint")
                appendLine("PersistentKeepalive = 25")
            }
        }
        return ""
    }

    suspend fun fetchConfigFromServer(): String? = withContext(Dispatchers.IO) {
        val serverUrls = listOf(WARP_API_URL, WARP_IBO_VPNS_URL)
        for (targetUrl in serverUrls) {
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "LIVE-PRO/24.1")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Cache-Control", "no-cache, no-store, max-age=0")
                    .build()

                val parsed = warpFetchHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string()?.trim() ?: return@use null
                    if (body.isEmpty()) return@use null

                    val rawConfig = if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        extractWireGuardFromJson(json)
                    } else if (body.contains("[Interface]", ignoreCase = true)) {
                        body
                    } else {
                        ""
                    }
                    sanitizeWireGuardConfig(rawConfig)
                }
                if (!parsed.isNullOrBlank()) return@withContext parsed
            } catch (_: Exception) {
            }
        }
        null
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

            if (trimmed.equals("[Interface]", ignoreCase = true)) {
                currentSection = "interface"
                continue
            }
            if (trimmed.equals("[Peer]", ignoreCase = true)) {
                currentSection = "peer"
                continue
            }

            if (trimmed.contains("=")) {
                val key = trimmed.substringBefore("=").trim().lowercase()
                var value = trimmed.substringAfter("=").trim()
                if (value.isEmpty()) continue

                if (currentSection == "interface" && key in allowedInterfaceKeys) {
                    if (key == "mtu" || key == "dns") continue
                    if (key == "privatekey") hasPrivateKey = true
                    if (key == "address") {
                        val ipv4Only = value.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() && !it.contains(":") }
                            .joinToString(", ")
                            .ifBlank { "172.16.0.2/32" }
                        hasAddress = true
                        interfaceLines.add("Address = $ipv4Only")
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
        if (!hasAddress) {
            interfaceLines.add("Address = 172.16.0.2/32")
        }
        interfaceLines.add("DNS = 1.1.1.1, 8.8.8.8")
        interfaceLines.add("MTU = 1280")

        if (!hasEndpoint) {
            peerLines.add("Endpoint = ${WARP_FAST_ENDPOINTS.first()}")
        }
        peerLines.add("AllowedIPs = 0.0.0.0/0")
        peerLines.add("PersistentKeepalive = 25")

        val cleanLines = mutableListOf<String>()
        cleanLines.add("[Interface]")
        cleanLines.addAll(interfaceLines)
        cleanLines.add("[Peer]")
        cleanLines.addAll(peerLines)

        val result = cleanLines.joinToString("\n")
        return result.takeIf {
            it.contains("[Interface]") && it.contains("[Peer]")
        }
    }
}
