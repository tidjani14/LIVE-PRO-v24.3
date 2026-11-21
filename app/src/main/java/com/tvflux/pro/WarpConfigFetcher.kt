package com.tvflux.pro

import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object WarpConfigFetcher {
    private const val CACHED_WG_FILE = "live_pro_saved_wg_tunnel_smart_v28.conf"
    private const val SMART_PREFS_NAME = "live_pro_smart_warp_optimizer_v28"
    private const val KEY_BEST_ENDPOINT = "best_verified_endpoint"
    private const val KEY_BEST_RTT = "best_verified_rtt_ms"
    private const val KEY_BEST_TS = "best_verified_ts"
    private const val PENALTY_TTL_MS = 10 * 60 * 1000L // 10 دقائق تجنب للنقاط المزدحمة أو المحجوبة

    @Volatile
    var ENABLE_CLOUDFLARE_WARP_FALLBACK: Boolean = true

    @Volatile
    private var memoryCachedConfig: String? = null

    // خريطة مؤقتة لتجنب النقاط المزدحمة أو الفاشلة تلقائياً
    private val penalizedEndpoints = ConcurrentHashMap<String, Long>()

    // مجموعة متنوعة من شبكات Cloudflare WARP Anycast الرسمية لاكتشاف أقرب مسار وأقل ازدحاماً
    private val WARP_CANDIDATE_IPS = listOf(
        "162.159.192.1",
        "162.159.192.2",
        "162.159.192.5",
        "162.159.192.8",
        "162.159.193.1",
        "162.159.193.5",
        "162.159.193.10",
        "162.159.195.1",
        "162.159.195.5",
        "188.114.96.1",
        "188.114.96.5",
        "188.114.97.1",
        "188.114.97.5",
        "188.114.98.1",
        "188.114.99.1"
    )

    // منافذ UDP رسمية لـ Cloudflare WARP (منفذ 2408 + منافذ بديلة غير مزدحمة وقت المباريات)
    private val WARP_SMART_PORTS = listOf(2408, 500, 4500, 1701, 854, 2506, 4198)

    val WARP_FAST_ENDPOINTS = listOf(
        "162.159.192.1:2408",
        "188.114.96.1:2408",
        "162.159.193.1:2408",
        "188.114.97.1:2408",
        "162.159.195.1:2408",
        "162.159.192.1:500",
        "188.114.96.1:4500",
        "162.159.193.1:1701",
        "188.114.98.1:854",
        "188.114.99.1:2506"
    )

    private val OFFICIAL_WARP_REG_URLS: List<String> by lazy {
        listOf(
            "https://api.cloudflareclient.com/v0a2158/reg",
            "https://api.cloudflareclient.com/v0a2483/reg",
            "https://162.159.192.1/v0a2158/reg",
            "https://188.114.96.1/v0a2158/reg"
        )
    }

    private val warpFetchHttpClient: OkHttpClient by lazy {
        ChannelRepository.bootstrapHttpClient.newBuilder()
            .dns(Dns.SYSTEM)
            .connectTimeout(3_000, TimeUnit.MILLISECONDS)
            .readTimeout(3_500, TimeUnit.MILLISECONDS)
            .callTimeout(4_500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    data class EndpointProbeScore(
        val ip: String,
        val avgRttMs: Double,
        val jitterMs: Double,
        val successCount: Int,
        val score: Double
    )

    fun penalizeSlowOrFailedEndpoint(endpoint: String) {
        if (endpoint.isNotBlank()) {
            penalizedEndpoints[endpoint.trim()] = System.currentTimeMillis()
        }
    }

    private fun isPenalized(endpoint: String): Boolean {
        val ts = penalizedEndpoints[endpoint.trim()] ?: return false
        if (System.currentTimeMillis() - ts > PENALTY_TTL_MS) {
            penalizedEndpoints.remove(endpoint.trim())
            return false
        }
        return true
    }

    fun saveBestVerifiedEndpoint(
        context: android.content.Context,
        endpoint: String,
        rttMs: Long,
        colo: String
    ) {
        if (endpoint.isBlank()) return
        penalizedEndpoints.remove(endpoint.trim())
        runCatching {
            context.getSharedPreferences(SMART_PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BEST_ENDPOINT, endpoint.trim())
                .putLong(KEY_BEST_RTT, rttMs)
                .putLong(KEY_BEST_TS, System.currentTimeMillis())
                .putString("best_colo", colo)
                .apply()
        }
    }

    private fun loadRecentBestEndpoint(context: android.content.Context): String? {
        return runCatching {
            val sp = context.getSharedPreferences(SMART_PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val ep = sp.getString(KEY_BEST_ENDPOINT, "").orEmpty().trim()
            val ts = sp.getLong(KEY_BEST_TS, 0L)
            val ageMs = System.currentTimeMillis() - ts
            if (ep.isNotBlank() && ageMs in 0..(6 * 60 * 60 * 1000L) && !isPenalized(ep)) ep else null
        }.getOrNull()
    }

    /**
     * فحص سريع متوازي لـ 15 نقطة Cloudflare WARP عبر 3 نبضات لكل نقطة
     * لحساب (متوسط زمن الاستجابة RTT + التذبذب Jitter + فقدان الحزم) واختيار المسار الأقرب والأقل ازدحاماً.
     */
    private fun probeSingleIpSmart(ip: String): EndpointProbeScore {
        val samples = ArrayList<Long>(3)
        for (attempt in 0 until 3) {
            val startNs = System.nanoTime()
            val ok = runCatching {
                Socket().use { sock ->
                    sock.tcpNoDelay = true
                    sock.connect(InetSocketAddress(ip, 443), 550)
                }
                true
            }.getOrDefault(false)
            if (ok) {
                val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                samples.add(rttMs)
            }
        }
        if (samples.isEmpty()) {
            return EndpointProbeScore(ip, 9999.0, 9999.0, 0, 99999.0)
        }
        val avg = samples.average()
        val jitter = if (samples.size >= 2) {
            var diffSum = 0.0
            for (i in 1 until samples.size) {
                diffSum += abs(samples[i] - samples[i - 1]).toDouble()
            }
            diffSum / (samples.size - 1)
        } else {
            15.0
        }
        val lossPenalty = (3 - samples.size) * 250.0
        // النقاط ذات التذبذب المنخفض (غير مزدحمة) تحصل على أفضلية واضحة
        val score = avg + (jitter * 1.6) + lossPenalty
        return EndpointProbeScore(ip, avg, jitter, samples.size, score)
    }

    /**
     * يُرجع قائمة مرتبة ذكياً بأسرع نقاط Cloudflare WARP غير المزدحمة مع تنويع المنافذ (2408 + 500 + 4500 + 1701 + 854 + 2506).
     */
    suspend fun selectFastestUncongestedEndpoints(context: android.content.Context): List<String> =
        withContext(Dispatchers.IO) {
            val scores = coroutineScope {
                WARP_CANDIDATE_IPS.map { ip ->
                    async(Dispatchers.IO) { probeSingleIpSmart(ip) }
                }.awaitAll()
            }
            val rankedIps = scores
                .filter { it.successCount > 0 }
                .sortedBy { it.score }
                .map { it.ip }
                .ifEmpty { WARP_CANDIDATE_IPS }

            val ordered = LinkedHashSet<String>()

            // 1. إذا كانت آخر نقطة موثّقة لا تزال ضمن أسرع 4 مسارات، نضعها أولاً لتسريع الاتصال
            val cachedBest = loadRecentBestEndpoint(context)
            if (!cachedBest.isNullOrBlank()) {
                val cachedIp = cachedBest.substringBefore(":")
                if (rankedIps.take(4).contains(cachedIp)) {
                    ordered.add(cachedBest)
                }
            }

            // 2. أفضل 4 عناوين IP بأسرع منفذ رسمي (2408) ثم منافذ بديلة غير مزدحمة (500 / 4500 / 1701 / 854 / 2506)
            val topIps = rankedIps.take(5)
            for (ip in topIps) {
                val ep2408 = "$ip:2408"
                if (!isPenalized(ep2408)) ordered.add(ep2408)
            }
            for ((idx, ip) in topIps.withIndex()) {
                val altPort = WARP_SMART_PORTS[1 + (idx % (WARP_SMART_PORTS.size - 1))]
                val epAlt = "$ip:$altPort"
                if (!isPenalized(epAlt)) ordered.add(epAlt)
            }
            // 3. احتياطي من بقية المنافذ على أسرع IP في حال تقييد منفذ 2408 من مزود الإنترنت
            val fastestIp = topIps.first()
            for (port in WARP_SMART_PORTS) {
                val ep = "$fastestIp:$port"
                if (!isPenalized(ep)) ordered.add(ep)
            }
            for (fallback in WARP_FAST_ENDPOINTS) {
                if (!isPenalized(fallback)) ordered.add(fallback)
            }
            ordered.toList().ifEmpty { WARP_FAST_ENDPOINTS }
        }

    /**
     * يُجهّز إعداد WireGuard لنفق كامل سريع (AllowedIPs = 0.0.0.0/0) محصور بتطبيق PLAYER VIP فقط
     * مع Keepalive سريع (15s) و MTU=1280 و DNS 1.1.1.1 لضمان أعلى سرعة وثبات للبث المباشر.
     */
    fun applyFastFullTunnelTuning(context: android.content.Context, configText: String): String {
        if (configText.isBlank()) return configText
        val pkgName = context.packageName.trim()
        val outLines = mutableListOf<String>()
        var inInterface = false
        var addedIncludedApp = false

        for (rawLine in configText.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.equals("[Interface]", ignoreCase = true)) {
                inInterface = true
                outLines.add("[Interface]")
                continue
            }
            if (trimmed.equals("[Peer]", ignoreCase = true)) {
                if (inInterface && !addedIncludedApp && pkgName.isNotBlank()) {
                    outLines.add("IncludedApplications = $pkgName")
                    addedIncludedApp = true
                }
                inInterface = false
                outLines.add("[Peer]")
                continue
            }
            if (inInterface && trimmed.startsWith("IncludedApplications", ignoreCase = true)) {
                continue
            }
            outLines.add(rawLine)
        }
        return outLines.joinToString("\n")
    }

    suspend fun getOrFetchConfig(context: android.content.Context, forceRefresh: Boolean = false): String? {
        if (!ENABLE_CLOUDFLARE_WARP_FALLBACK) return null
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

            // تسجيل مباشر 100% مع Cloudflare WARP بدون أي خادم VPS
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
                if (!fallback.isNullOrBlank()) {
                    memoryCachedConfig = fallback
                }
                return@withContext fallback
            }
            null
        }

    fun invalidateCachedConfig(context: android.content.Context) {
        memoryCachedConfig = null
        penalizedEndpoints.clear()
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
                    val cfgObj = root.optJSONObject("config") ?: root.optJSONObject("result")?.optJSONObject("config") ?: return@use null
                    val ifaceObj = cfgObj.optJSONObject("interface")
                    val addrsObj = ifaceObj?.optJSONObject("addresses")
                    val v4Addr = addrsObj?.optString("v4", "")?.trim().orEmpty()
                    val ipv4Address = if (v4Addr.isNotBlank()) {
                        if (v4Addr.contains("/")) v4Addr else "$v4Addr/32"
                    } else {
                        "172.16.0.2/32"
                    }

                    val peersArr = cfgObj.optJSONArray("peers")
                    val firstPeer = peersArr?.optJSONObject(0)
                    val peerPubKey = firstPeer?.optString("public_key", "")?.trim().orEmpty()
                        .ifBlank { "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" }

                    val builtRaw = buildString {
                        appendLine("[Interface]")
                        appendLine("PrivateKey = $privKeyB64")
                        appendLine("Address = $ipv4Address")
                        appendLine("DNS = 1.1.1.1, 1.0.0.1")
                        appendLine("MTU = 1280")
                        appendLine("[Peer]")
                        appendLine("PublicKey = $peerPubKey")
                        appendLine("AllowedIPs = 0.0.0.0/0")
                        appendLine("Endpoint = ${WARP_FAST_ENDPOINTS.first()}")
                        appendLine("PersistentKeepalive = 15")
                    }
                    sanitizeWireGuardConfig(builtRaw)
                }
                if (!result.isNullOrBlank()) return result
            } catch (_: Throwable) {
            }
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
                            .filter { it.isNotBlank() && !it.contains(":") && it.matches(Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}(/\\d{1,2})?$")) }
                            .ifEmpty { listOf("172.16.0.2/32") }
                        hasAddress = true
                        interfaceLines.add("Address = ${ipv4Only.joinToString(", ")}")
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
        interfaceLines.add("DNS = 1.1.1.1, 1.0.0.1")
        interfaceLines.add("MTU = 1280")

        if (!hasEndpoint) {
            peerLines.add("Endpoint = ${WARP_FAST_ENDPOINTS.first()}")
        }
        peerLines.add("AllowedIPs = 0.0.0.0/0")
        peerLines.add("PersistentKeepalive = 15")

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
