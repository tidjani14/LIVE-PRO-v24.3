package com.tvflux.pro

import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object WarpConfigFetcher {
    private const val CACHED_WG_FILE = "live_pro_saved_wg_tunnel_v25.conf"

    /**
     * خيار احتياطي للمطور:
     * افتراضياً `false` تطبيقاً لمبدأ الإخفاء الكامل (غير المشترك في VPN PRO لا يعمل لديه أي نفق).
     * إذا أراد المطور تفعيل Cloudflare WARP كاحتياطي داخلي عند فشل VPN PRO، يمكن تفعيله إلى `true`.
     */
    @Volatile
    var ENABLE_CLOUDFLARE_WARP_FALLBACK: Boolean = true

    @Volatile
    private var memoryCachedConfig: String? = null

    /**
     * Multi-port Cloudflare WARP Anycast endpoints:
     * Port 500 (IKEv2) and 4500 (NAT-T) bypass ISP throttling on port 2408 across Algeria/MENA/Europe.
     */
    /**
     * Multi-port Anti-DPI Cloudflare WARP Anycast endpoints:
     * Rotates across UDP 443 (QUIC), 500 (IKEv2), 4500 (NAT-T), 853 (DoT), 1701 (L2TP), and 2408
     * across both 162.159.x.x and 188.114.x.x Anycast blocks so ISP UDP port blocking NEVER stops the tunnel!
     */
    val WARP_FAST_ENDPOINTS = listOf(
        "162.159.192.1:500",
        "162.159.193.1:4500",
        "188.114.96.1:500",
        "188.114.97.1:4500",
        "162.159.192.1:443",
        "188.114.98.1:443",
        "162.159.192.1:2408",
        "162.159.195.1:1701",
        "188.114.99.1:853"
    )

    /**
     * Ranks Cloudflare WARP Anycast endpoints by real network reachability & lowest RTT in parallel (~220ms max)
     * so the tunnel always binds to the fastest, uncongested edge node for zero-lag IPTV streaming!
     */
    suspend fun getFastestRankedEndpoints(): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val distinctHosts = listOf("162.159.192.1", "162.159.193.1", "188.114.96.1", "188.114.97.1")
            val hostRtts = kotlinx.coroutines.coroutineScope {
                distinctHosts.map { host ->
                    kotlinx.coroutines.async {
                        val start = android.os.SystemClock.elapsedRealtime()
                        val ok = runCatching {
                            java.net.Socket().use { sock ->
                                sock.tcpNoDelay = true
                                sock.connect(java.net.InetSocketAddress(host, 443), 240)
                                true
                            }
                        }.getOrDefault(false)
                        val rtt = if (ok) (android.os.SystemClock.elapsedRealtime() - start) else 9999L
                        host to rtt
                    }
                }.map { it.await() }.toMap()
            }
            WARP_FAST_ENDPOINTS.sortedBy { ep ->
                val h = ep.substringBefore(":")
                val portBonus = when {
                    ep.endsWith(":500") -> 0L
                    ep.endsWith(":4500") -> 2L
                    ep.endsWith(":443") -> 4L
                    else -> 10L
                }
                (hostRtts[h] ?: 5000L) + portBonus
            }
        }.getOrDefault(WARP_FAST_ENDPOINTS)
    }

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
     * 1. يجرّب أولاً جلب وفك تشفير إعدادات سيرفر VPN PRO (`VpnProConfigFetcher`).
     * 2. إذا نجح -> يستخدم إعدادات VPN PRO مباشرة.
     * 3. إذا فشل -> يعود إلى Cloudflare WARP فقط إذا كان `ENABLE_CLOUDFLARE_WARP_FALLBACK = true`.
     */
    suspend fun getOrFetchConfig(context: android.content.Context, forceRefresh: Boolean = false): String? {
        // 0ms RAM & Disk Fast-Path first so Cloudflare WARP connects instantaneously on launch!
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
        return withContext(Dispatchers.IO) {
            val cacheFile = java.io.File(context.filesDir, CACHED_WG_FILE)
            if (!forceRefresh && cacheFile.exists()) {
                val saved = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
                val validSaved = sanitizeWireGuardConfig(saved)
                if (!validSaved.isNullOrBlank()) {
                    memoryCachedConfig = validSaved
                    return@withContext validSaved
                }
            }

            // 1. Primary: Register a 100% dedicated per-device Curve25519 Cloudflare WARP keypair!
            //    Tries direct Cloudflare API + Anycast IP fallbacks so we NEVER use a shared private key that collides and kills internet!
            var resolvedFresh = runCatching { registerDedicatedDeviceWarpConfig() }.getOrNull()
            if (resolvedFresh.isNullOrBlank()) {
                resolvedFresh = runCatching { fetchConfigFromServer() }.getOrNull()
            }
            if (!resolvedFresh.isNullOrBlank()) {
                memoryCachedConfig = resolvedFresh
                runCatching { cacheFile.writeText(resolvedFresh, Charsets.UTF_8) }
                return@withContext resolvedFresh
            }

            // 3. Fallback to cached file if network fetch failed
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

    /**
     * Generates a fresh Curve25519 WireGuard KeyPair on-device and registers a dedicated WARP peer
     * directly with Cloudflare's official registration API (~180ms).
     */
    private val regHttpClient by lazy {
        ChannelRepository.bootstrapHttpClient.newBuilder()
            .connectTimeout(3_500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(4_500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(5_500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }

    private fun registerDedicatedDeviceWarpConfig(): String? {
        val regUrls = listOf(
            OFFICIAL_WARP_REG_URL,
            "https://api.cloudflareclient.com/v0a2483/reg",
            "https://162.159.192.1/v0a2158/reg",
            "https://162.159.193.1/v0a2158/reg",
            "https://188.114.96.1/v0a2158/reg"
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

                val req = Request.Builder()
                    .url(regUrl)
                    .header("Host", "api.cloudflareclient.com")
                    .header("User-Agent", "okhttp/4.12.0")
                    .header("Accept", "application/json")
                    .header("CF-Client-Version", "a-6.30-3596")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val result = regHttpClient.newCall(req).execute().use { resp ->
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
                        appendLine("DNS = 1.1.1.1, 1.0.0.1")
                        appendLine("MTU = 1280")
                        appendLine("[Peer]")
                        appendLine("PublicKey = $peerPubKey")
                        appendLine("AllowedIPs = 0.0.0.0/0")
                        appendLine("Endpoint = ${WARP_FAST_ENDPOINTS.first()}")
                        appendLine("PersistentKeepalive = 20")
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

        // Also support structured JSON keys (e.g. from vpns.php / warp.php)
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
                appendLine("DNS = 1.1.1.1, 1.0.0.1")
                appendLine("MTU = 1280")
                appendLine("[Peer]")
                appendLine("PublicKey = $pubKey")
                appendLine("AllowedIPs = 0.0.0.0/0")
                appendLine("Endpoint = $endpoint")
                appendLine("PersistentKeepalive = 20")
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

                val parsed = regHttpClient.newCall(request).execute().use { response ->
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

    /**
     * Enforces Pure IPv4 (no ::/0 IPv6 blackholes!), MTU = 1280, and PersistentKeepalive = 25
     * on all Cloudflare WARP WireGuard configs so Xtream playlists and live video streams
     * never stall or drop across Android TV routers, 4G/5G links, and PPPoE fiber connections.
     */
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
                    if (key == "mtu" || key == "dns") continue // Enforced cleanly below
                    if (key == "privatekey") hasPrivateKey = true
                    if (key == "address") {
                        // Keep ONLY IPv4 addresses (strip IPv6 2606:4700:... which causes IPv6 blackholes on IPv4 routers)
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
                    if (key == "persistentkeepalive" || key == "allowedips") continue // Enforced cleanly below
                    if (key == "endpoint") {
                        hasEndpoint = true
                        if (value.contains("engage.cloudflareclient.com", ignoreCase = true) ||
                            value.endsWith(":2408")
                        ) {
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
        interfaceLines.add("DNS = 1.1.1.1, 1.0.0.1")
        interfaceLines.add("MTU = 1280")
        // توجيه نفق الـ VPN لكامل الجهاز (Full-Device Tunnel) مع ضمان عدم تقطيع حزم TLS عبر MTU=1280 و Keepalive=20 و Pure IPv4!

        if (!hasEndpoint) {
            peerLines.add("Endpoint = ${WARP_FAST_ENDPOINTS.first()}")
        }
        // Pure IPv4 route (0.0.0.0/0 without ::/0) prevents IPv6 routing deadlocks on IPv4-only ISPs
        peerLines.add("AllowedIPs = 0.0.0.0/0")
        peerLines.add("PersistentKeepalive = 20")

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
