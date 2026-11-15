package com.tvflux.pro


import kotlinx.coroutines.launch
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.StringReader
import java.util.zip.GZIPInputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Customer Bouquet & Channel Ordering Helper (D-Pad friendly up/down/top/hide).
 * Preserves 100% exact native server order when customOrder is empty.
 */
object CustomOrderHelper {
    const val HIDE_FAV_BOUQUET_KEY = "__HIDE_FAV_BOUQUET__"
    const val HIDE_MQ_BOUQUET_KEY = "__HIDE_MQ_BOUQUET__"

    fun isFavoritesBouquetHidden(hiddenBouquets: Set<String>): Boolean {
        return HIDE_FAV_BOUQUET_KEY in hiddenBouquets ||
            "★ المفضلة" in hiddenBouquets ||
            "★ Favoris" in hiddenBouquets ||
            "★ Favorites" in hiddenBouquets
    }

    fun isMultiQualityBouquetHidden(hiddenBouquets: Set<String>): Boolean {
        return MultiQualityEngine.BOUQUET_NAME in hiddenBouquets ||
            HIDE_MQ_BOUQUET_KEY in hiddenBouquets
    }

    fun toggleFavoritesBouquetHidden(hiddenBouquets: Set<String>, favLabel: String = "★ المفضلة"): Set<String> {
        return if (isFavoritesBouquetHidden(hiddenBouquets)) {
            hiddenBouquets - setOf(HIDE_FAV_BOUQUET_KEY, favLabel, "★ المفضلة", "★ Favoris", "★ Favorites")
        } else {
            hiddenBouquets + setOf(HIDE_FAV_BOUQUET_KEY, favLabel, "★ المفضلة")
        }
    }

    fun toggleMultiQualityBouquetHidden(hiddenBouquets: Set<String>): Set<String> {
        return if (isMultiQualityBouquetHidden(hiddenBouquets)) {
            hiddenBouquets - setOf(MultiQualityEngine.BOUQUET_NAME, HIDE_MQ_BOUQUET_KEY)
        } else {
            hiddenBouquets + setOf(MultiQualityEngine.BOUQUET_NAME, HIDE_MQ_BOUQUET_KEY)
        }
    }

    fun sortBouquets(
        rawBouquets: List<BouquetSummary>,
        customOrder: List<String>,
        hiddenBouquets: Set<String>,
        includeHidden: Boolean = false
    ): List<BouquetSummary> {
        val safeBouquets = rawBouquets
        val favHidden = isFavoritesBouquetHidden(hiddenBouquets)
        val mqHidden = isMultiQualityBouquetHidden(hiddenBouquets)
        val visible = if (includeHidden) {
            safeBouquets
        } else {
            safeBouquets.filter { bq ->
                when {
                    bq.name == "الكل" || bq.name == "Tout" || bq.name == "All" -> true
                    bq.isFavoritesSpecial -> !favHidden && bq.name !in hiddenBouquets
                    bq.name == MultiQualityEngine.BOUQUET_NAME -> !mqHidden && bq.name !in hiddenBouquets
                    else -> bq.name !in hiddenBouquets
                }
            }
        }
        if (customOrder.isEmpty()) return visible
        val orderMap = customOrder.withIndex().associate { it.value to it.index }
        return visible.sortedWith(
            compareBy<BouquetSummary> { bq ->
                when {
                    bq.name == "الكل" || bq.name == "Tout" || bq.name == "All" -> -2
                    else -> orderMap[bq.name] ?: (if (bq.isFavoritesSpecial) -1 else (10_000 + rawBouquets.indexOf(bq)))
                }
            }
        )
    }

    fun sortChannels(
        rawChannels: List<TvChannel>,
        customChannelOrder: List<String>,
        hiddenBouquets: Set<String>
    ): List<TvChannel> {
        if (customChannelOrder.isEmpty() && hiddenBouquets.isEmpty()) return rawChannels
        val filtered = if (hiddenBouquets.isEmpty()) {
            rawChannels
        } else {
            rawChannels.filter { it.group !in hiddenBouquets }
        }
        if (customChannelOrder.isEmpty()) return filtered
        val orderMap = customChannelOrder.withIndex().associate { it.value to it.index }
        return filtered.sortedWith(
            compareBy<TvChannel> { ch ->
                orderMap[ch.stableKey] ?: (100_000 + ch.number)
            }
        )
    }

    fun <T> moveItem(list: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return list
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }
}

/**
 * Universal High-Capacity Streaming Xtream Codes, Panel API & M3U Engine (v12.0):
 * - Preserves exact native Xtream server category and stream order via LinkedHashMap.
 * - Uses streaming JsonReader & BufferedReader directly over GZIP network sockets
 *   so servers with 150,000+ channels/movies/series load quickly with zero OOM.
 * - Supports player_api.php, panel_api.php, get.php, M3U/M3U8/MPD/TS/VOD, #EXTVLCOPT & #KODIPROP DRM.
 */
object XtreamRepository {

    /**
     * Unlimited-duration HTTP/1.1 OkHttp client for Xtream catalog downloads:
     * - Enforces HTTP/1.1 (Protocol.HTTP_1_1) like IBO Player Pro so Nginx/Apache Xtream panels
     *   never reset streams with HTTP/2 framing errors.
     * - callTimeout(0) and readTimeout(120s) ensure large 200MB+ catalogs never cut off mid-download.
     */
    val xtreamHttpClient: OkHttpClient by lazy {
        ChannelRepository.xtreamHttpClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val fastProbeHttpClient: OkHttpClient by lazy {
        ChannelRepository.bootstrapHttpClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Strips invisible Unicode control/directional characters (zero-width spaces, RTL/LTR marks
     * injected when copying credentials or URLs from WhatsApp/Telegram) and trims quotes/spaces.
     */
    fun cleanInvisibleInput(raw: String): String {
        if (raw.isEmpty()) return ""
        return buildString(raw.length) {
            for (ch in raw) {
                if (ch in '\u200B'..'\u200F' || ch in '\u202A'..'\u202E' || ch in '\u2060'..'\u2069' || ch == '\uFEFF') continue
                if (ch == '\u00A0') append(' ') else append(ch)
            }
        }.trim().trim('"', '\'')
    }

    /**
     * O(N) Linear-Time Category Bucket Sort:
     * Preserves 100% exact server category order and in-category stream order in a single linear pass
     * without O(N log N) comparisons or boxed Integer allocations across 150,000+ items.
     */
    private fun fastBucketSortByCategoryOrder(
        items: MutableList<TvChannel>,
        cats: Map<String, String>
    ) {
        if (cats.isEmpty() || items.size <= 1) return
        val buckets = LinkedHashMap<String, ArrayList<TvChannel>>(cats.size * 2 + 16)
        for (catName in cats.values) {
            if (!buckets.containsKey(catName)) {
                buckets[catName] = ArrayList()
            }
        }
        val overflow = LinkedHashMap<String, ArrayList<TvChannel>>()
        for (item in items) {
            val b = buckets[item.group]
            if (b != null) {
                b.add(item)
            } else {
                overflow.getOrPut(item.group) { ArrayList() }.add(item)
            }
        }
        items.clear()
        for (b in buckets.values) {
            if (b.isNotEmpty()) items.addAll(b)
        }
        for (b in overflow.values) {
            if (b.isNotEmpty()) items.addAll(b)
        }
    }

    private val IPTV_USER_AGENTS = IboPlayerProfileSpoofer.IBO_USER_AGENTS

    /**
     * Auto-detects GZIP magic bytes (0x1f 0x8b) on any stream so whether the Xtream panel honors
     * Accept-Encoding: identity or forces GZIP compression, reading never fails on Android TV.
     */
    private fun wrapAutoDecompressStream(raw: InputStream): InputStream {
        val pb = PushbackInputStream(raw, 2)
        val b1 = pb.read()
        if (b1 == -1) return pb
        val b2 = pb.read()
        if (b2 != -1) pb.unread(b2)
        pb.unread(b1)
        return if (b1 == 0x1f && b2 == 0x8b) {
            GZIPInputStream(pb, 131072)
        } else {
            pb
        }
    }

    fun normalizeServerUrl(raw: String): String {
        var clean = cleanInvisibleInput(raw)
            .replace(Regex("\\s*://\\s*"), "://")
            .removeSuffix("/")
        if (clean.isBlank()) return ""

        // Unwrap accidental Google redirect links
        if (clean.contains("google.", ignoreCase = true) && clean.contains("q=")) {
            val qVal = clean.substringAfter("q=").substringBefore("&")
            val decoded = runCatching { java.net.URLDecoder.decode(qVal, "UTF-8") }.getOrDefault(qVal).trim()
            if (decoded.isNotBlank()) clean = decoded.removeSuffix("/")
        }

        // Only look up by server brand name if the input is NOT already a URL, IP, or domain!
        val looksLikeUrlOrDomain = clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) ||
            clean.contains(".") || clean.contains(":") || clean.contains("/")
        if (!looksLikeUrlOrDomain) {
            val catalogMatch = KnownIptvServersCatalog.resolveHostByServerName(clean)
            if (catalogMatch.isNotBlank()) {
                clean = catalogMatch.trim().removeSuffix("/")
            }
        }

        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "http://$clean"
        }

        for (suffix in listOf(
            "/player_api.php", "/panel_api.php", "/get.php", "/xmltv.php",
            "/enigma2.php", "/portal.php", "/c/", "/c", "/stalker_portal/c",
            "/live/", "/movie/", "/series/"
        )) {
            if (clean.contains(suffix, ignoreCase = true)) {
                clean = clean.substringBefore(suffix)
            }
        }
        clean = clean.substringBefore("?").substringBefore("#").trim().removeSuffix("/")
        return clean
    }

    private fun swapScheme(url: String): String {
        return when {
            url.startsWith("https://", ignoreCase = true) -> "http://" + url.substring(8)
            url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
            else -> url
        }
    }

    /**
     * Generates candidate base URLs in priority order (IBO Player Pro failover style):
     * 1) User's normalized baseUrl
     * 2) Any active domain migration from ChannelRepository.latestDomainMigrations
     * 3) Scheme swap (http <-> https)
     * 4) Common Xtream ports (:8080, :80, :2095, :25461, :8880, :2052, :2082, :2086) if no port or default port failed
     * 5) Alternative hosts for the same brand in KnownIptvServersCatalog
     */
    private fun buildCandidateBaseUrls(primaryBaseUrl: String, serverLabel: String): List<String> {
        val candidates = LinkedHashSet<String>()
        val normPrimary = normalizeServerUrl(primaryBaseUrl)
        if (normPrimary.isNotBlank()) {
            candidates.add(normPrimary)
            // Check domain migrations
            val primaryHost = normPrimary.substringAfter("://").substringBefore("/").lowercase(Locale.ROOT)
            for ((oldDom, newDom, _) in ChannelRepository.latestDomainMigrations) {
                val oldH = oldDom.substringAfter("://").substringBefore("/").lowercase(Locale.ROOT)
                if (oldH.isNotBlank() && (primaryHost == oldH || normPrimary.contains(oldH, ignoreCase = true))) {
                    val migrated = normalizeServerUrl(newDom)
                    if (migrated.isNotBlank()) candidates.add(migrated)
                }
            }
            candidates.add(swapScheme(normPrimary))

            // Add common Xtream port variants on the same hostname if primary fails to connect
            runCatching {
                val uri = java.net.URI(normPrimary)
                val scheme = uri.scheme ?: "http"
                val host = uri.host
                val path = uri.path.orEmpty().removeSuffix("/")
                if (!host.isNullOrBlank()) {
                    val currentPort = uri.port
                    val commonPorts = if (currentPort == -1) {
                        listOf(8080, 80, 2095, 25461, 8880, 2052, 2082)
                    } else {
                        listOf(80, 8080, 2095, 25461, 8880).filter { it != currentPort }
                    }
                    for (p in commonPorts) {
                        val portStr = if ((scheme.equals("http", true) && p == 80) || (scheme.equals("https", true) && p == 443)) "" else ":$p"
                        candidates.add("$scheme://$host$portStr$path")
                    }
                }
            }
        }

        // Brand-level fallback hosts
        for (alt in KnownIptvServersCatalog.findAlternativeHostsForAccount(serverLabel, normPrimary)) {
            val normAlt = normalizeServerUrl(alt)
            if (normAlt.isNotBlank()) {
                candidates.add(normAlt)
            }
        }
        return candidates.toList()
    }

    private const val XTREAM_EXPIRY_PROBE_TTL_MS = 30 * 60 * 1000L
    private data class CachedXtreamProbe(val expDate: String, val isActive: Boolean, val timestampMs: Long)
    private val probeCache = java.util.concurrent.ConcurrentHashMap<String, CachedXtreamProbe>()

    /**
     * Lightweight real-time Xtream account validity & expiry date check (< 1 KB).
     */
    suspend fun probeAccountExpiryQuick(
        config: XtreamAccountConfig,
        forceNetwork: Boolean = false
    ): Pair<String, Boolean> =
        withContext(Dispatchers.IO) {
            val autoSplit = SmartM3uParser.tryExtractXtreamCredentials(config.serverUrl)
                ?: SmartM3uParser.tryExtractXtreamCredentials(config.m3uUrl)
            val baseUrl = normalizeServerUrl(autoSplit?.serverUrl ?: config.serverUrl)
            val user = cleanInvisibleInput(autoSplit?.username ?: config.username)
            val pass = cleanInvisibleInput(autoSplit?.password ?: config.password)
            if (baseUrl.isBlank() || user.isBlank() || pass.isBlank()) {
                return@withContext Pair(config.expDate, true)
            }
            val cacheKey = "$baseUrl|$user|$pass"
            val now = System.currentTimeMillis()
            val cached = probeCache[cacheKey]
            if (!forceNetwork && cached != null && (now - cached.timestampMs) < XTREAM_EXPIRY_PROBE_TTL_MS) {
                return@withContext Pair(cached.expDate, cached.isActive)
            }

            val uEnc = safeEncodeParam(user)
            val pEnc = safeEncodeParam(pass)
            val apiBase = "$baseUrl/player_api.php?username=$uEnc&password=$pEnc"
            val raw = fetchUrlTextFast(apiBase).ifBlank {
                fetchUrlTextFast("${swapScheme(baseUrl)}/player_api.php?username=$uEnc&password=$pEnc")
            }
            val clean = extractJsonPayload(raw)
            if (clean.startsWith("{")) {
                runCatching {
                    val root = JSONObject(clean)
                    val userInfo = root.optJSONObject("user_info")
                        ?: root.optJSONObject("data")?.optJSONObject("user_info")
                    if (userInfo != null) {
                        val auth = userInfo.optInt("auth", 1)
                        val status = userInfo.optString("status", "Active")
                        val isActive = auth != 0 && !status.equals("Expired", ignoreCase = true) && !status.equals("Banned", ignoreCase = true)
                        val expDateStr = parseXtreamExpDate(userInfo, root).ifBlank { config.expDate }
                        probeCache[cacheKey] = CachedXtreamProbe(expDateStr, isActive, now)
                        return@withContext Pair(expDateStr, isActive)
                    }
                }
            }
            Pair(config.expDate, true)
        }

    private fun safeEncodeParam(raw: String): String {
        val clean = cleanInvisibleInput(raw)
        if (clean.isEmpty()) return ""
        return runCatching {
            val decoded = if (clean.contains("%")) java.net.URLDecoder.decode(clean, "UTF-8") else clean
            URLEncoder.encode(decoded, "UTF-8").replace("+", "%20")
        }.getOrElse {
            runCatching { URLEncoder.encode(clean, "UTF-8") }.getOrDefault(clean)
        }
    }

    private fun parseXtreamExpDate(userInfo: JSONObject, root: JSONObject): String {
        val rawExp = userInfo.optString("exp_date", "")
            .ifBlank { userInfo.optString("expire_date", "") }
            .ifBlank { userInfo.optString("expiration_date", "") }
            .ifBlank { root.optString("exp_date", "") }
            .trim()
        if (rawExp.isBlank() || rawExp.equals("null", true) || rawExp.equals("unlimited", true) || rawExp == "0") {
            return ""
        }
        val expSec = rawExp.toLongOrNull()
        if (expSec != null && expSec > 100_000_000L) {
            val ms = if (expSec > 10_000_000_000L) expSec else expSec * 1000L
            return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms)) }.getOrDefault("")
        }
        if (rawExp.length >= 10 && rawExp[4] == '-') {
            return rawExp.take(10)
        }
        return ""
    }

    private suspend fun fetchProbeTextCancellable(
        url: String,
        userAgent: String = IPTV_USER_AGENTS.first(),
        useIdentityEncoding: Boolean = true,
        includeRefererHeader: Boolean = true
    ): String =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val request = runCatching {
                IboPlayerProfileSpoofer.applyIboApiHeaders(
                    builder = Request.Builder()
                        .url(url)
                        .header("Cache-Control", "no-cache, no-store, max-age=0"),
                    targetUrl = url,
                    userAgent = userAgent,
                    useIdentityEncoding = useIdentityEncoding,
                    includeRefererHeader = includeRefererHeader
                ).build()
            }.getOrElse {
                continuation.resumeWith(Result.success(""))
                return@suspendCancellableCoroutine
            }

            val call = fastProbeHttpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(""))
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val bodyText = runCatching {
                        response.use { resp ->
                            if (resp.isSuccessful) {
                                val rawTxt = readResponseBodyAutoDecompress(resp)
                                if (!IboPlayerProfileSpoofer.isBlockedResponseBody(rawTxt)) rawTxt else ""
                            } else ""
                        }
                    }.getOrDefault("")
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(bodyText))
                    }
                }
            })
        }

    /**
     * Bounded race-to-first-success for automatic host discovery.
     */
    suspend fun probeServersForAccountOnDevice(
        username: String,
        password: String,
        candidates: List<Pair<String, String>>,
        minSuccessHosts: Int = 1
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()

        val uEnc = safeEncodeParam(username)
        val pEnc = safeEncodeParam(password)
        val successes = ArrayList<Pair<String, String>>(2)
        val seenUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val target = minSuccessHosts.coerceIn(1, 2)

        for (batch in candidates.chunked(16)) {
            if (successes.size >= target) break

            coroutineScope {
                val results = kotlinx.coroutines.channels.Channel<Pair<String, String>?>(16)
                val jobs = batch.map { (srvName, srvUrl) ->
                    async {
                        var hit: Pair<String, String>? = null
                        try {
                            val normUrl = normalizeServerUrl(srvUrl)
                            if (normUrl.isNotBlank() &&
                                seenUrls.add(normUrl.lowercase(java.util.Locale.ROOT))
                            ) {
                                val apiUrl = "$normUrl/player_api.php?username=$uEnc&password=$pEnc"
                                var raw = kotlinx.coroutines.withTimeoutOrNull(14_000L) {
                                    fetchProbeTextCancellable(apiUrl, IPTV_USER_AGENTS[0], useIdentityEncoding = true, includeRefererHeader = true)
                                }.orEmpty()
                                if (raw.isBlank() && IPTV_USER_AGENTS.size > 2) {
                                    raw = kotlinx.coroutines.withTimeoutOrNull(14_000L) {
                                        fetchProbeTextCancellable(apiUrl, IPTV_USER_AGENTS[2], useIdentityEncoding = true, includeRefererHeader = false)
                                    }.orEmpty()
                                }

                                val cleanJson = extractJsonPayload(raw)
                                if (cleanJson.startsWith("{")) {
                                    val valid = runCatching {
                                        val root = JSONObject(cleanJson)
                                        val ui = root.optJSONObject("user_info")
                                            ?: root.optJSONObject("data")?.optJSONObject("user_info")
                                        if (ui != null) {
                                            val auth = ui.optString("auth", "0").trim()
                                            val status = ui.optString("status", "Active").trim()
                                            val authOk = auth == "1" ||
                                                auth.equals("true", ignoreCase = true) ||
                                                (ui.has("username") && status.equals("Active", ignoreCase = true))
                                            val blocked = status.equals("Expired", true) ||
                                                status.equals("Banned", true) ||
                                                status.equals("Disabled", true) ||
                                                status.equals("Blocked", true)
                                            authOk && !blocked
                                        } else {
                                            root.has("server_info")
                                        }
                                    }.getOrDefault(false)

                                    if (valid) {
                                        val resolvedName = srvName.ifBlank {
                                            KnownIptvServersCatalog.detectServerNameByHost(normUrl)
                                                .ifBlank { "XTREAM VIP" }
                                        }
                                        hit = normUrl to resolvedName
                                    }
                                }
                            }
                        } finally {
                            results.trySend(hit)
                        }
                    }
                }

                var completed = 0
                while (completed < jobs.size && successes.size < target) {
                    val hit = results.receive()
                    completed++
                    if (hit != null &&
                        successes.none { it.first.equals(hit.first, ignoreCase = true) }
                    ) {
                        successes.add(hit)
                    }
                }

                jobs.forEach { it.cancel() }
                results.close()
            }
        }

        successes
    }

    suspend fun syncXtreamAccount(
        config: XtreamAccountConfig,
        onLiveReady: (List<TvChannel>, String) -> Unit = { _, _ -> },
        onMoviesReady: (List<TvChannel>) -> Unit = {},
        onSeriesReady: (List<TvChannel>) -> Unit = {},
        onProgress: (SyncProgressState) -> Unit = {}
    ): XtreamFullSyncResult =
        withContext(Dispatchers.IO) {
            syncXtreamAccountInternal(config, onLiveReady, onMoviesReady, onSeriesReady, onProgress)
        }

    private data class EndpointProbeResult(
        val effectiveBaseUrl: String,
        val authJson: String,
        val effectiveUserParam: String,
        val effectivePassParam: String,
        val isReachable: Boolean
    )

    /**
     * Probes candidate base URLs and User-Agents like IBO Player Pro:
     * - Follows HTTP 301/302/307/308 redirects and captures the final redirected baseUrl so all
     *   subsequent get_live_streams / get_vod_streams / get_series calls hit the active server node.
     * - Tests both URL-encoded and raw credentials in case password contains special chars (@, !, *).
     * - Returns whether the host responded (even if bare player_api.php returned non-JSON, so Stage 2/3 can run).
     */
    private fun probeAndResolveXtreamEndpoint(
        candidates: List<String>,
        userRaw: String,
        passRaw: String,
        uEnc: String,
        pEnc: String
    ): EndpointProbeResult {
        var firstReachableBase = ""
        var smartBypassBase = ""
        var firstAuthFailureJson = ""
        var firstAuthFailureBase = candidates.firstOrNull().orEmpty()

        val credPairs = if (uEnc != userRaw || pEnc != passRaw) {
            listOf(uEnc to pEnc, userRaw to passRaw)
        } else {
            listOf(uEnc to pEnc)
        }

        // Layer 1 + Layer 2 + Layer 3 + Layer 4 (Smart Bypass):
        // Rotate across User-Agents (IBOPlayer MI 8 Lite, IBOPlayerPro, IPTVSmarters, TiviMate, VLC)
        // with Origin/Referer and identity encoding to bypass WAF / anti-bot shields and support slow servers.
        for ((candIdx, candBase) in candidates.take(8).withIndex()) {
            if (candBase.isBlank()) continue
            val maxUasForCand = if (candIdx <= 1) IPTV_USER_AGENTS.size.coerceAtMost(5) else 3
            for ((uParam, pParam) in credPairs) {
                val testUrl = "$candBase/player_api.php?username=$uParam&password=$pParam"
                for ((uaIdx, ua) in IPTV_USER_AGENTS.take(maxUasForCand).withIndex()) {
                    // Try with Origin + Referer + identity first, and also without Referer on alternate UAs
                    val useIdEnc = true
                    val incRef = (uaIdx % 2 == 0)
                    try {
                        val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                            builder = Request.Builder().url(testUrl).header("Cache-Control", "no-cache"),
                            targetUrl = testUrl,
                            serverBaseUrl = candBase,
                            userAgent = ua,
                            useIdentityEncoding = useIdEnc,
                            includeRefererHeader = incRef
                        ).build()
                        val client = if (candIdx == 0 && uaIdx >= 2) xtreamHttpClient else fastProbeHttpClient
                        client.newCall(req).execute().use { resp ->
                            val finalReqUrl = resp.request.url.toString()
                            val redirectedBase = if (finalReqUrl.contains("/player_api.php", ignoreCase = true)) {
                                finalReqUrl.substringBefore("/player_api.php").trim().removeSuffix("/")
                            } else candBase
                            val effBase = redirectedBase.ifBlank { candBase }
                            val httpCode = resp.code

                            if (httpCode == 200 || httpCode == 461 || httpCode in 200..499) {
                                if (firstReachableBase.isBlank()) firstReachableBase = effBase
                                if (httpCode == 200 || httpCode == 461) {
                                    if (smartBypassBase.isBlank()) smartBypassBase = effBase
                                }
                            }

                            val txt = if (resp.body != null) readResponseBodyAutoDecompress(resp) else ""
                            if (txt.isNotBlank() && !IboPlayerProfileSpoofer.isBlockedResponseBody(txt)) {
                                val clean = extractJsonPayload(txt)
                                if (clean.startsWith("{")) {
                                    val root = runCatching { JSONObject(clean) }.getOrNull()
                                    val ui = root?.optJSONObject("user_info")
                                        ?: root?.optJSONObject("data")?.optJSONObject("user_info")
                                        ?: root?.optJSONObject("result")?.optJSONObject("user_info")
                                        ?: root?.optJSONObject("user")
                                    if (ui != null) {
                                        val authVal = ui.optString("auth", "1").trim()
                                        val statusVal = ui.optString("status", ui.optString("account_status", "")).trim().lowercase(Locale.ROOT)
                                        val isAuthZero = (authVal == "0" || authVal.equals("false", ignoreCase = true)) &&
                                            statusVal !in setOf("active", "trial", "enabled", "ok", "valid")
                                        if (!isAuthZero) {
                                            IboPlayerProfileSpoofer.recordWorkingHostProfile(effBase, ua, useIdEnc, incRef)
                                            return EndpointProbeResult(effBase, clean, uParam, pParam, true)
                                        } else if (firstAuthFailureJson.isBlank()) {
                                            firstAuthFailureJson = clean
                                            firstAuthFailureBase = effBase
                                        }
                                    } else if (root != null) {
                                        IboPlayerProfileSpoofer.recordWorkingHostProfile(effBase, ua, useIdEnc, incRef)
                                        return EndpointProbeResult(effBase, clean, uParam, pParam, true)
                                    }
                                }
                            }

                            // Protected servers that hide/block bare player_api.php (returning empty, HTML, 403, or 461)
                            // often respond immediately when action=get_live_categories is passed!
                            if (httpCode in listOf(200, 403, 404, 461, 500) || IboPlayerProfileSpoofer.isBlockedResponseBody(txt)) {
                                val actionTestUrl = "$effBase/player_api.php?username=$uParam&password=$pParam&action=get_live_categories"
                                val actionReq = IboPlayerProfileSpoofer.applyIboApiHeaders(
                                    builder = Request.Builder().url(actionTestUrl).header("Cache-Control", "no-cache"),
                                    targetUrl = actionTestUrl,
                                    serverBaseUrl = effBase,
                                    userAgent = ua,
                                    useIdentityEncoding = true,
                                    includeRefererHeader = true
                                ).build()
                                runCatching {
                                    fastProbeHttpClient.newCall(actionReq).execute().use { actResp ->
                                        if (actResp.isSuccessful) {
                                            val actTxt = readResponseBodyAutoDecompress(actResp)
                                            if (actTxt.isNotBlank() && !IboPlayerProfileSpoofer.isBlockedResponseBody(actTxt)) {
                                                val actClean = extractJsonPayload(actTxt)
                                                if (actClean.startsWith("[") || actClean.startsWith("{")) {
                                                    IboPlayerProfileSpoofer.recordWorkingHostProfile(effBase, ua, true, true)
                                                    val syntheticAuth = "{\"user_info\":{\"auth\":1,\"status\":\"Active\"}}"
                                                    return EndpointProbeResult(effBase, syntheticAuth, uParam, pParam, true)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Connection timeout or refused on this candidate/UA; try next
                    }
                }
            }
        }

        if (firstAuthFailureJson.isNotBlank()) {
            return EndpointProbeResult(firstAuthFailureBase, firstAuthFailureJson, uEnc, pEnc, true)
        }
        val resolvedReachable = smartBypassBase.ifBlank { firstReachableBase }.ifBlank { candidates.firstOrNull().orEmpty() }
        val isReach = smartBypassBase.isNotBlank() || firstReachableBase.isNotBlank()
        return EndpointProbeResult(resolvedReachable, "", uEnc, pEnc, isReach)
    }

    private suspend fun syncXtreamAccountInternal(
        config: XtreamAccountConfig,
        onLiveReady: (List<TvChannel>, String) -> Unit = { _, _ -> },
        onMoviesReady: (List<TvChannel>) -> Unit = {},
        onSeriesReady: (List<TvChannel>) -> Unit = {},
        onProgress: (SyncProgressState) -> Unit = {}
    ): XtreamFullSyncResult =
        withContext(Dispatchers.IO) {
            val serverLabel = cleanInvisibleInput(config.serverName).ifBlank { "XTREAM" }
            val playlistId = config.id.ifBlank { "XTREAM_1" }

            val cleanServerUrlInput = cleanInvisibleInput(config.serverUrl)
            val cleanM3uUrlInput = cleanInvisibleInput(config.m3uUrl)
            val cleanUserInput = cleanInvisibleInput(config.username)
            val cleanPassInput = cleanInvisibleInput(config.password)

            val autoSplit = SmartM3uParser.tryExtractXtreamCredentials(cleanServerUrlInput)
                ?: SmartM3uParser.tryExtractXtreamCredentials(cleanM3uUrlInput)
                ?: SmartM3uParser.tryExtractXtreamCredentials(cleanUserInput)
                ?: SmartM3uParser.tryExtractXtreamCredentials(cleanPassInput)

            // Direct M3U URL mode (when no Xtream username/password can be extracted)
            if (autoSplit == null && cleanM3uUrlInput.isNotBlank() && (cleanServerUrlInput.isBlank() || cleanUserInput.isBlank())) {
                val parsed = fetchAndParseM3uStream(cleanM3uUrlInput, serverLabel, playlistId, serverLabel)
                val live = parsed.filter { it.contentType == StreamContentType.LIVE_TV }
                val movies = parsed.filter { it.contentType == StreamContentType.MOVIE }
                val series = parsed.filter { it.contentType == StreamContentType.SERIES }
                if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                    error("تعذّر قراءة ملف M3U من الرابط المحدد")
                }
                if (live.isNotEmpty()) runCatching { onLiveReady(live, config.expDate) }
                if (movies.isNotEmpty()) runCatching { onMoviesReady(movies) }
                if (series.isNotEmpty()) runCatching { onSeriesReady(series) }
                return@withContext XtreamFullSyncResult(
                    account = config.copy(
                        enabled = true,
                        liveCount = live.size,
                        movieCount = movies.size,
                        seriesCount = series.size,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        statusNote = "متصل ✓ (${live.size} قناة • ${movies.size} فيلم)"
                    ),
                    liveChannels = live,
                    movies = movies,
                    series = series
                )
            }

            var baseUrl = normalizeServerUrl(
                (autoSplit?.serverUrl ?: cleanServerUrlInput).ifBlank {
                    KnownIptvServersCatalog.resolveHostByServerName(serverLabel)
                }
            )
            val user = cleanInvisibleInput(autoSplit?.username ?: cleanUserInput)
            val pass = cleanInvisibleInput(autoSplit?.password ?: cleanPassInput)

            // If user pasted a direct non-Xtream M3U URL into serverUrl without username/password
            if (baseUrl.isNotBlank() && (user.isBlank() || pass.isBlank()) &&
                (cleanServerUrlInput.contains(".m3u", ignoreCase = true) || cleanServerUrlInput.contains("playlist", ignoreCase = true))
            ) {
                val parsed = fetchAndParseM3uStream(cleanServerUrlInput, serverLabel, playlistId, serverLabel)
                val live = parsed.filter { it.contentType == StreamContentType.LIVE_TV }
                val movies = parsed.filter { it.contentType == StreamContentType.MOVIE }
                val series = parsed.filter { it.contentType == StreamContentType.SERIES }
                if (live.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()) {
                    if (live.isNotEmpty()) runCatching { onLiveReady(live, config.expDate) }
                    if (movies.isNotEmpty()) runCatching { onMoviesReady(movies) }
                    if (series.isNotEmpty()) runCatching { onSeriesReady(series) }
                    return@withContext XtreamFullSyncResult(
                        account = config.copy(
                            enabled = true,
                            liveCount = live.size,
                            movieCount = movies.size,
                            seriesCount = series.size,
                            lastSyncTimestamp = System.currentTimeMillis(),
                            statusNote = "متصل ✓ (${live.size} قناة • ${movies.size} فيلم)"
                        ),
                        liveChannels = live,
                        movies = movies,
                        series = series
                    )
                }
            }

            // If baseUrl is still blank but username & password are provided, auto-discover from catalog!
            if (baseUrl.isBlank() && user.isNotBlank() && pass.isNotBlank()) {
                val discovered = probeServersForAccountOnDevice(user, pass, KnownIptvServersCatalog.getAllServers(), 1)
                if (discovered.isNotEmpty()) {
                    baseUrl = discovered.first().first
                }
            }

            require(baseUrl.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                "يرجى إدخال رابط السيرفر واسم المستخدم وكلمة المرور"
            }

            var uEnc = safeEncodeParam(user)
            var pEnc = safeEncodeParam(pass)

            onProgress(
                SyncProgressState(
                    isSyncing = true,
                    sourceId = playlistId,
                    sourceName = serverLabel,
                    phaseLabel = "🔎 اختبار اتصال السيرفر والتحقق من الحساب ($serverLabel)...",
                    progressPercent = 16
                )
            )

            val candidateBaseUrls = buildCandidateBaseUrls(baseUrl, serverLabel)
            var probeResult = probeAndResolveXtreamEndpoint(candidateBaseUrls, user, pass, uEnc, pEnc)

            // Only scan catalog servers if the configured candidate hosts were not reachable at all
            if (probeResult.authJson.isBlank() && !probeResult.isReachable && KnownIptvServersCatalog.getAllServers().isNotEmpty()) {
                val catalogHits = probeServersForAccountOnDevice(user, pass, KnownIptvServersCatalog.getAllServers(), 1)
                if (catalogHits.isNotEmpty()) {
                    val foundBase = catalogHits.first().first
                    probeResult = probeAndResolveXtreamEndpoint(listOf(foundBase), user, pass, uEnc, pEnc)
                }
            }

            if (probeResult.effectiveBaseUrl.isNotBlank()) {
                baseUrl = probeResult.effectiveBaseUrl
            }
            uEnc = probeResult.effectiveUserParam
            pEnc = probeResult.effectivePassParam
            val apiBase = "$baseUrl/player_api.php?username=$uEnc&password=$pEnc"

            var expLabel = config.expDate
            var serverDefaultLiveExt = ""
            val cleanAuthJson = extractJsonPayload(probeResult.authJson)
            if (cleanAuthJson.startsWith("{")) {
                runCatching {
                    val root = JSONObject(cleanAuthJson)
                    val userInfo = root.optJSONObject("user_info")
                        ?: root.optJSONObject("data")?.optJSONObject("user_info")
                    if (userInfo != null) {
                        val authVal = userInfo.optString("auth", "1").trim()
                        val status = userInfo.optString("status", "Active").trim()
                        val statusLower = status.lowercase(Locale.ROOT)
                        if ((authVal == "0" || authVal.equals("false", ignoreCase = true)) &&
                            statusLower !in setOf("expired", "banned", "disabled", "blocked", "suspended")
                        ) {
                            error("AUTH_INVALID|🔑 اسم المستخدم أو كلمة المرور غير صحيحة.\nتأكد من كتابة Username و Password بدقة ثم أعد المحاولة.")
                        }
                        if (statusLower in setOf("expired", "banned", "disabled", "blocked", "suspended") ||
                            (authVal == "0" && statusLower == "expired")
                        ) {
                            error("ACCOUNT_EXPIRED|⚠️ حساب XTREAM غير نشط أو منتهي الصلاحية ($status).\nيرجى تجديد الاشتراك لدى مزود الخدمة.")
                        }
                        val parsedExp = parseXtreamExpDate(userInfo, root)
                        if (parsedExp.isNotBlank()) expLabel = parsedExp

                        // قراءة الصيغة المعتمدة من السيرفر حرفياً من allowed_output_formats في user_info
                        val formatsArr = userInfo.optJSONArray("allowed_output_formats")
                        if (formatsArr != null && formatsArr.length() > 0) {
                            for (i in 0 until formatsArr.length()) {
                                val fmt = formatsArr.optString(i, "").trim().lowercase(Locale.ROOT).removePrefix(".")
                                if (fmt == "m3u8" || fmt == "ts" || fmt == "rtmp") {
                                    serverDefaultLiveExt = if (fmt == "rtmp") "ts" else fmt
                                    break
                                }
                            }
                        }
                    }
                }.onFailure { e ->
                    val msg = e.message.orEmpty()
                    if (msg.startsWith("AUTH_INVALID") || msg.startsWith("ACCOUNT_EXPIRED")) throw e
                }
            }

            // =========================================================================
            // PARALLEL TRI-STREAM LIGHTNING ENGINE (Live + Movies + Series at the exact same time!):
            // - Launches Live TV, Movies, and Series pipelines concurrently in parallel.
            // - Updates atomic counters (liveCount, movieCount, seriesCount) in real time every 150 items
            //   so the user immediately sees all 3 counters ticking up simultaneously!
            // - Automatically falls back on any individual stream if the panel throttles concurrent calls.
            // =========================================================================
            val liveCountAtomic = java.util.concurrent.atomic.AtomicInteger(0)
            val movieCountAtomic = java.util.concurrent.atomic.AtomicInteger(0)
            val seriesCountAtomic = java.util.concurrent.atomic.AtomicInteger(0)
            val liveDoneAtomic = java.util.concurrent.atomic.AtomicBoolean(false)
            val movieDoneAtomic = java.util.concurrent.atomic.AtomicBoolean(false)
            val seriesDoneAtomic = java.util.concurrent.atomic.AtomicBoolean(false)
            val lastProgressEmitMs = java.util.concurrent.atomic.AtomicLong(0L)

            fun emitRealTimeParallelProgress(forceEmit: Boolean = false) {
                val nowMs = android.os.SystemClock.elapsedRealtime()
                val prevMs = lastProgressEmitMs.get()
                if (!forceEmit && (nowMs - prevMs) < 75L) return
                lastProgressEmitMs.set(nowMs)

                val lCnt = liveCountAtomic.get()
                val mCnt = movieCountAtomic.get()
                val sCnt = seriesCountAtomic.get()
                val lDone = liveDoneAtomic.get()
                val mDone = movieDoneAtomic.get()
                val sDone = seriesDoneAtomic.get()

                val liveScore = if (lDone) 34 else (lCnt / 450).coerceAtMost(30)
                val movieScore = if (mDone) 28 else (mCnt / 1800).coerceAtMost(25)
                val seriesScore = if (sDone) 20 else (sCnt / 600).coerceAtMost(18)
                val pct = (16 + liveScore + movieScore + seriesScore).coerceIn(16, 94)

                val statusLabel = buildString {
                    append("📥 سحب متسلسل ($serverLabel) • ")
                    append("📺 مباشر: $lCnt")
                    if (lDone) append(" ✓")
                    append(" | 🎬 أفلام: $mCnt")
                    if (mDone) append(" ✓")
                    append(" | 📂 مسلسلات: $sCnt")
                    if (sDone) append(" ✓")
                }

                onProgress(
                    SyncProgressState(
                        isSyncing = true,
                        sourceId = playlistId,
                        sourceName = serverLabel,
                        phaseLabel = statusLabel,
                        progressPercent = pct,
                        fetchedLiveCount = lCnt,
                        fetchedMovieCount = mCnt,
                        fetchedSeriesCount = sCnt
                    )
                )
            }

            emitRealTimeParallelProgress(forceEmit = true)

            // SEQUENTIAL ONLY (Live → Movies → Series). Parallel confused throttling servers and caused partial restarts.
            val liveCatsSeq = runCatching {
                fetchCategoryMapStreaming("$apiBase&action=get_live_categories", baseUrl)
            }.getOrDefault(LinkedHashMap())
            val liveItemsSeq = runCatching {
                streamParseLiveChannels(
                    url = "$apiBase&action=get_live_streams",
                    liveCats = liveCatsSeq,
                    serverLabel = serverLabel,
                    playlistId = playlistId,
                    baseUrl = baseUrl,
                    uEnc = uEnc,
                    pEnc = pEnc,
                    serverDefaultLiveExt = serverDefaultLiveExt,
                    onBatchProgress = { currentLiveCount, snapshotChunk ->
                        liveCountAtomic.set(currentLiveCount)
                        emitRealTimeParallelProgress(forceEmit = (snapshotChunk != null))
                        if (snapshotChunk != null && snapshotChunk.isNotEmpty()) {
                            runCatching { onLiveReady(snapshotChunk, expLabel) }
                        }
                    }
                ).toMutableList()
            }.getOrDefault(mutableListOf())
            fastBucketSortByCategoryOrder(liveItemsSeq, liveCatsSeq)
            liveCountAtomic.set(liveItemsSeq.size)
            liveDoneAtomic.set(true)
            emitRealTimeParallelProgress(forceEmit = true)
            if (liveItemsSeq.isNotEmpty()) {
                runCatching { onLiveReady(liveItemsSeq.toList(), expLabel) }
            }
            val liveResultPair = liveCatsSeq to liveItemsSeq

            // Movies only after Live completed fully
            ChannelRepository.evictIdleApiConnections()
            val vodCatsSeq = runCatching {
                fetchCategoryMapStreaming("$apiBase&action=get_vod_categories", baseUrl)
            }.getOrDefault(LinkedHashMap())
            val movieItemsSeq = runCatching {
                streamParseVodMovies(
                    url = "$apiBase&action=get_vod_streams",
                    vodCats = vodCatsSeq,
                    serverLabel = serverLabel,
                    playlistId = playlistId,
                    baseUrl = baseUrl,
                    uEnc = uEnc,
                    pEnc = pEnc,
                    onBatchProgress = { currentMovieCount, snapshotChunk ->
                        movieCountAtomic.set(currentMovieCount)
                        emitRealTimeParallelProgress(forceEmit = (snapshotChunk != null))
                        if (snapshotChunk != null && snapshotChunk.isNotEmpty()) {
                            runCatching { onMoviesReady(snapshotChunk) }
                        }
                    }
                ).toMutableList()
            }.getOrDefault(mutableListOf())
            fastBucketSortByCategoryOrder(movieItemsSeq, vodCatsSeq)
            movieCountAtomic.set(movieItemsSeq.size)
            movieDoneAtomic.set(true)
            emitRealTimeParallelProgress(forceEmit = true)
            if (movieItemsSeq.isNotEmpty()) {
                runCatching { onMoviesReady(movieItemsSeq.toList()) }
            }
            val movieResultPair = vodCatsSeq to movieItemsSeq

            // Series only after Movies completed fully
            ChannelRepository.evictIdleApiConnections()
            val seriesCatsSeq = runCatching {
                fetchCategoryMapStreaming("$apiBase&action=get_series_categories", baseUrl)
            }.getOrDefault(LinkedHashMap())
            val seriesItemsSeq = runCatching {
                streamParseSeriesItems(
                    url = "$apiBase&action=get_series",
                    seriesCats = seriesCatsSeq,
                    serverLabel = serverLabel,
                    playlistId = playlistId,
                    apiBase = apiBase,
                    baseUrl = baseUrl,
                    uEnc = uEnc,
                    pEnc = pEnc,
                    onBatchProgress = { currentSeriesCount, snapshotChunk ->
                        seriesCountAtomic.set(currentSeriesCount)
                        emitRealTimeParallelProgress(forceEmit = (snapshotChunk != null))
                        if (snapshotChunk != null && snapshotChunk.isNotEmpty()) {
                            runCatching { onSeriesReady(snapshotChunk) }
                        }
                    }
                ).toMutableList()
            }.getOrDefault(mutableListOf())
            fastBucketSortByCategoryOrder(seriesItemsSeq, seriesCatsSeq)
            seriesCountAtomic.set(seriesItemsSeq.size)
            seriesDoneAtomic.set(true)
            emitRealTimeParallelProgress(forceEmit = true)
            if (seriesItemsSeq.isNotEmpty()) {
                runCatching { onSeriesReady(seriesItemsSeq.toList()) }
            }
            val seriesResultPair = seriesCatsSeq to seriesItemsSeq

            var liveCats = liveResultPair.first
            val liveList = liveResultPair.second
            var vodCats = movieResultPair.first
            val movieList = movieResultPair.second
            var seriesCats = seriesResultPair.first
            val seriesList = seriesResultPair.second

            // Smart Anti-Truncation & Anti-Throttle Recovery:
            // إذا انقطع أي قسم في المنتصف (مثلاً سحب 32,000 فيلم من أصل 225,000 فيلم بسبب ضغط السحب المتوازي أو رمشة شبكة)
            // أو أعاد فارغاً، نعيد سحبه منفرداً بكامل عرض النطاق الترددي لضمان اكتمال JSON بنسبة 100%!
            val liveIncomplete = !wasSectionSyncedCompletely(playlistId, StreamContentType.LIVE_TV)
            if ((liveList.isEmpty() || liveIncomplete) && (movieList.isNotEmpty() || seriesList.isNotEmpty() || liveIncomplete)) {
                liveDoneAtomic.set(false)
                ChannelRepository.evictIdleApiConnections()
                if (liveCats.isEmpty()) {
                    liveCats = runCatching { fetchCategoryMapStreaming("$apiBase&action=get_live_categories", baseUrl) }.getOrDefault(LinkedHashMap())
                }
                val retryLive = runCatching {
                    streamParseLiveChannels(
                        url = "$apiBase&action=get_live_streams",
                        liveCats = liveCats,
                        serverLabel = serverLabel,
                        playlistId = playlistId,
                        baseUrl = baseUrl,
                        uEnc = uEnc,
                        pEnc = pEnc,
                        serverDefaultLiveExt = serverDefaultLiveExt,
                        onBatchProgress = { currentLiveCount, snapshotChunk ->
                            liveCountAtomic.set(maxOf(liveList.size, currentLiveCount))
                            emitRealTimeParallelProgress(forceEmit = (snapshotChunk != null))
                            if (snapshotChunk != null && snapshotChunk.size > liveList.size) {
                                runCatching { onLiveReady(snapshotChunk, expLabel) }
                            }
                        }
                    ).toMutableList()
                }.getOrDefault(mutableListOf())
                if (retryLive.size >= liveList.size && retryLive.isNotEmpty()) {
                    fastBucketSortByCategoryOrder(retryLive, liveCats)
                    liveList.clear()
                    liveList.addAll(retryLive)
                }
                liveCountAtomic.set(liveList.size)
                liveDoneAtomic.set(true)
                emitRealTimeParallelProgress(forceEmit = true)
                if (liveList.isNotEmpty()) runCatching { onLiveReady(liveList.toList(), expLabel) }
            }

            val moviesIncomplete = !wasSectionSyncedCompletely(playlistId, StreamContentType.MOVIE)
            if ((movieList.isEmpty() || moviesIncomplete) && (liveList.isNotEmpty() || seriesList.isNotEmpty() || moviesIncomplete)) {
                movieDoneAtomic.set(false)
                ChannelRepository.evictIdleApiConnections()
                if (vodCats.isEmpty()) {
                    vodCats = runCatching { fetchCategoryMapStreaming("$apiBase&action=get_vod_categories", baseUrl) }.getOrDefault(LinkedHashMap())
                }
                val retryMovies = runCatching {
                    streamParseVodMovies(
                        url = "$apiBase&action=get_vod_streams",
                        vodCats = vodCats,
                        serverLabel = serverLabel,
                        playlistId = playlistId,
                        baseUrl = baseUrl,
                        uEnc = uEnc,
                        pEnc = pEnc,
                        onBatchProgress = { currentMovieCount, snapshotChunk ->
                            movieCountAtomic.set(maxOf(movieList.size, currentMovieCount))
                            emitRealTimeParallelProgress(forceEmit = (snapshotChunk != null))
                            if (snapshotChunk != null && snapshotChunk.size > movieList.size) {
                                runCatching { onMoviesReady(snapshotChunk) }
                            }
                        }
                    ).toMutableList()
                }.getOrDefault(mutableListOf())
                if (retryMovies.size >= movieList.size && retryMovies.isNotEmpty()) {
                    fastBucketSortByCategoryOrder(retryMovies, vodCats)
                    movieList.clear()
                    movieList.addAll(retryMovies)
                }
                movieCountAtomic.set(movieList.size)
                movieDoneAtomic.set(true)
                emitRealTimeParallelProgress(forceEmit = true)
                if (movieList.isNotEmpty()) runCatching { onMoviesReady(movieList.toList()) }
            }

            val seriesIncomplete = !wasSectionSyncedCompletely(playlistId, StreamContentType.SERIES)
            if ((seriesList.isEmpty() || seriesIncomplete) && (liveList.isNotEmpty() || movieList.isNotEmpty() || seriesIncomplete)) {
                seriesDoneAtomic.set(false)
                ChannelRepository.evictIdleApiConnections()
                if (seriesCats.isEmpty()) {
                    seriesCats = runCatching { fetchCategoryMapStreaming("$apiBase&action=get_series_categories", baseUrl) }.getOrDefault(LinkedHashMap())
                }
                val retrySeries = runCatching {
                    streamParseSeriesItems(
                        url = "$apiBase&action=get_series",
                        seriesCats = seriesCats,
                        serverLabel = serverLabel,
                        playlistId = playlistId,
                        apiBase = apiBase,
                        baseUrl = baseUrl,
                        uEnc = uEnc,
                        pEnc = pEnc,
                        onBatchProgress = { currentSeriesCount, snapshotChunk ->
                            seriesCountAtomic.set(maxOf(seriesList.size, currentSeriesCount))
                            emitRealTimeParallelProgress(forceEmit = (snapshotChunk != null))
                            if (snapshotChunk != null && snapshotChunk.size > seriesList.size) {
                                runCatching { onSeriesReady(snapshotChunk) }
                            }
                        }
                    ).toMutableList()
                }.getOrDefault(mutableListOf())
                if (retrySeries.size >= seriesList.size && retrySeries.isNotEmpty()) {
                    fastBucketSortByCategoryOrder(retrySeries, seriesCats)
                    seriesList.clear()
                    seriesList.addAll(retrySeries)
                }
                seriesCountAtomic.set(seriesList.size)
                seriesDoneAtomic.set(true)
                emitRealTimeParallelProgress(forceEmit = true)
                if (seriesList.isNotEmpty()) runCatching { onSeriesReady(seriesList.toList()) }
            }

            // Stage 1.5 Fallback: Per-Category Fetching for Protected/Heavy Xtream Servers
            // Some protected Xtream panels block or time out on bulk get_live_streams / get_vod_streams / get_series
            // without a category_id parameter, but return all items when queried per category!
            if (liveList.isEmpty() && liveCats.isNotEmpty()) {
                for ((catId, _) in liveCats.entries) {
                    if (!isValidCategoryIdToken(catId)) continue
                    val catItems = runCatching {
                        streamParseLiveChannels(
                            url = "$apiBase&action=get_live_streams&category_id=${URLEncoder.encode(catId, "UTF-8")}",
                            liveCats = liveCats,
                            serverLabel = serverLabel,
                            playlistId = playlistId,
                            baseUrl = baseUrl,
                            uEnc = uEnc,
                            pEnc = pEnc,
                            serverDefaultLiveExt = serverDefaultLiveExt
                        )
                    }.getOrDefault(emptyList())
                    if (catItems.isNotEmpty()) {
                        liveList.addAll(catItems)
                        liveCountAtomic.set(liveList.size)
                        emitRealTimeParallelProgress(forceEmit = false)
                    }
                }
                if (liveList.isNotEmpty()) {
                    fastBucketSortByCategoryOrder(liveList, liveCats)
                    recordSectionCompletion(playlistId, StreamContentType.LIVE_TV, true)
                    emitRealTimeParallelProgress(forceEmit = true)
                    runCatching { onLiveReady(liveList.toList(), expLabel) }
                }
            }

            if (movieList.isEmpty() && vodCats.isNotEmpty()) {
                for ((catId, _) in vodCats.entries) {
                    if (!isValidCategoryIdToken(catId)) continue
                    val catItems = runCatching {
                        streamParseVodMovies(
                            url = "$apiBase&action=get_vod_streams&category_id=${URLEncoder.encode(catId, "UTF-8")}",
                            vodCats = vodCats,
                            serverLabel = serverLabel,
                            playlistId = playlistId,
                            baseUrl = baseUrl,
                            uEnc = uEnc,
                            pEnc = pEnc
                        )
                    }.getOrDefault(emptyList())
                    if (catItems.isNotEmpty()) {
                        movieList.addAll(catItems)
                        movieCountAtomic.set(movieList.size)
                        emitRealTimeParallelProgress(forceEmit = false)
                    }
                }
                if (movieList.isNotEmpty()) {
                    fastBucketSortByCategoryOrder(movieList, vodCats)
                    recordSectionCompletion(playlistId, StreamContentType.MOVIE, true)
                    emitRealTimeParallelProgress(forceEmit = true)
                    runCatching { onMoviesReady(movieList.toList()) }
                }
            }

            if (seriesList.isEmpty() && seriesCats.isNotEmpty()) {
                for ((catId, _) in seriesCats.entries) {
                    if (!isValidCategoryIdToken(catId)) continue
                    val catItems = runCatching {
                        streamParseSeriesItems(
                            url = "$apiBase&action=get_series&category_id=${URLEncoder.encode(catId, "UTF-8")}",
                            seriesCats = seriesCats,
                            serverLabel = serverLabel,
                            playlistId = playlistId,
                            apiBase = apiBase,
                            baseUrl = baseUrl,
                            uEnc = uEnc,
                            pEnc = pEnc
                        )
                    }.getOrDefault(emptyList())
                    if (catItems.isNotEmpty()) {
                        seriesList.addAll(catItems)
                        seriesCountAtomic.set(seriesList.size)
                        emitRealTimeParallelProgress(forceEmit = false)
                    }
                }
                if (seriesList.isNotEmpty()) {
                    fastBucketSortByCategoryOrder(seriesList, seriesCats)
                    recordSectionCompletion(playlistId, StreamContentType.SERIES, true)
                    emitRealTimeParallelProgress(forceEmit = true)
                    runCatching { onSeriesReady(seriesList.toList()) }
                }
            }

            // Stage 2 Fallback: Try panel_api.php across candidate hosts if player_api.php returned no content
            if (liveList.isEmpty() && movieList.isEmpty() && seriesList.isEmpty()) {
                onProgress(
                    SyncProgressState(
                        isSyncing = true,
                        sourceId = playlistId,
                        sourceName = serverLabel,
                        phaseLabel = "🔄 تجربة بروتوكول panel_api.php المتوافق لـ ($serverLabel)...",
                        progressPercent = 78
                    )
                )
                for (candBase in listOf(baseUrl) + candidateBaseUrls.filter { it != baseUrl }.take(3)) {
                    val panelUrl = "$candBase/panel_api.php?username=$uEnc&password=$pEnc"
                    val (panelLive, panelMovies) = parsePanelApiFallback(panelUrl, serverLabel, playlistId, candBase, uEnc, pEnc)
                    if (panelLive.isNotEmpty() || panelMovies.isNotEmpty()) {
                        baseUrl = candBase
                        liveList.addAll(panelLive)
                        movieList.addAll(panelMovies)
                        if (liveList.isNotEmpty()) runCatching { onLiveReady(liveList.toList(), expLabel) }
                        if (movieList.isNotEmpty()) runCatching { onMoviesReady(movieList.toList()) }
                        break
                    }
                }
            }

            // Stage 3 Fallback: Stream M3U via get.php (ts first for IBO/VLC parity, then m3u8, then raw get.php)
            if (liveList.isEmpty() && movieList.isEmpty() && seriesList.isEmpty()) {
                onProgress(
                    SyncProgressState(
                        isSyncing = true,
                        sourceId = playlistId,
                        sourceName = serverLabel,
                        phaseLabel = "🔄 سحب القائمة عبر بروتوكول M3U المباشر (get.php) لـ ($serverLabel)...",
                        progressPercent = 82
                    )
                )
                val m3uCandidates = LinkedHashSet<String>()
                for (candBase in listOf(baseUrl, swapScheme(baseUrl)) + candidateBaseUrls.take(3)) {
                    if (candBase.isBlank()) continue
                    // طلب رابط get.php الافتراضي للسيرفر أولاً ليقرر السيرفر نفسه صيغة الروابط (ts أو m3u8)
                    m3uCandidates.add("$candBase/get.php?username=$uEnc&password=$pEnc&type=m3u_plus")
                    if (serverDefaultLiveExt.isNotBlank()) {
                        m3uCandidates.add("$candBase/get.php?username=$uEnc&password=$pEnc&type=m3u_plus&output=$serverDefaultLiveExt")
                    }
                    m3uCandidates.add("$candBase/get.php?username=$uEnc&password=$pEnc")
                }
                for (m3uUrl in m3uCandidates) {
                    val parsedM3u = fetchAndParseM3uStream(m3uUrl, serverLabel, playlistId, serverLabel)
                    if (parsedM3u.isNotEmpty()) {
                        liveList.addAll(parsedM3u.filter { it.contentType == StreamContentType.LIVE_TV })
                        movieList.addAll(parsedM3u.filter { it.contentType == StreamContentType.MOVIE })
                        seriesList.addAll(parsedM3u.filter { it.contentType == StreamContentType.SERIES })
                        if (liveList.isNotEmpty()) runCatching { onLiveReady(liveList.toList(), expLabel) }
                        if (movieList.isNotEmpty()) runCatching { onMoviesReady(movieList.toList()) }
                        if (seriesList.isNotEmpty()) runCatching { onSeriesReady(seriesList.toList()) }
                        break
                    }
                }
            }

            if (liveList.isEmpty() && movieList.isEmpty() && seriesList.isEmpty()) {
                if (cleanAuthJson.isBlank()) {
                    error("NETWORK_OR_HOST|⛔ تعذّر الوصول إلى السيرفر ($baseUrl) أو الرابط غير صالح.\nتحقق من الإنترنت ورابط الهوست ثم أعد المحاولة.")
                } else {
                    error("تم الاتصال بالسيرفر ($serverLabel) ولكن لم يتم العثور على قوائم متاحة لهذا الحساب.")
                }
            }

            onProgress(
                SyncProgressState(
                    isSyncing = true,
                    sourceId = playlistId,
                    sourceName = serverLabel,
                    phaseLabel = "جارٍ الفهرسة التفاضلية السريعة في قاعدة بيانات SQLite...",
                    progressPercent = 88,
                    fetchedLiveCount = liveList.size,
                    fetchedMovieCount = movieList.size,
                    fetchedSeriesCount = seriesList.size
                )
            )
            // Evict idle API keep-alive connections so max_connections=1 servers immediately allow video streaming!
            ChannelRepository.evictIdleApiConnections()
            val detectedBrand = KnownIptvServersCatalog.detectServerNameByHost(baseUrl)
            val finalServerName = if (serverLabel == "XTREAM" || serverLabel == "XTREAM VIP" || serverLabel == "VIP Server" || serverLabel.startsWith("قائمة") || serverLabel.startsWith("حساب")) {
                detectedBrand.ifBlank { serverLabel }
            } else serverLabel
            val updatedConfig = config.copy(
                serverName = finalServerName,
                serverUrl = baseUrl,
                username = user,
                password = pass,
                enabled = true,
                liveCount = liveList.size,
                movieCount = movieList.size,
                seriesCount = seriesList.size,
                expDate = expLabel,
                lastSyncTimestamp = System.currentTimeMillis(),
                statusNote = "متصل ✓ (${liveList.size} قناة • ${movieList.size} فيلم • ${seriesList.size} مسلسل)"
            )

            XtreamFullSyncResult(
                account = updatedConfig,
                liveChannels = liveList,
                movies = movieList,
                series = seriesList
            )
        }

    /**
     * Wrapper keys for stream lists vs category lists.
     */
    private val STREAM_WRAPPER_KEYS = setOf(
        "data", "streams", "channels", "live", "live_streams",
        "movies", "vod", "vod_streams", "movie_data", "series",
        "available_channels", "result", "results", "items", "list", "response", "output"
    )

    private val CATEGORY_WRAPPER_KEYS = setOf(
        "data", "categories", "live", "movie", "movies", "vod", "series",
        "live_categories", "vod_categories", "series_categories",
        "result", "results", "items", "list", "response", "output"
    )

    private val IGNORED_META_KEYS = setOf(
        "user_info", "server_info", "categories",
        "live_categories", "vod_categories", "series_categories"
    )

    private fun extractJsonPayload(raw: String): String {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) return ""
        val firstBrace = trimmed.indexOf('{')
        val firstBracket = trimmed.indexOf('[')
        val startIdx = when {
            firstBrace >= 0 && firstBracket >= 0 -> minOf(firstBrace, firstBracket)
            firstBrace >= 0 -> firstBrace
            firstBracket >= 0 -> firstBracket
            else -> return trimmed
        }
        return if (startIdx > 0) trimmed.substring(startIdx).trim() else trimmed
    }

    /**
     * Skips any leading BOM, whitespace, or PHP notices/warnings until the real JSON `[` or `{` begins.
     */
    private fun skipToFirstJsonToken(br: BufferedReader): Boolean {
        return try {
            while (true) {
                br.mark(8)
                val ch = br.read()
                if (ch == -1) return false
                val c = ch.toChar()
                if (c == '[' || c == '{') {
                    // Peek next non-whitespace char to ensure it is valid JSON (not a random brace in a PHP notice)
                    var nextNonWs = -1
                    for (i in 0 until 5) {
                        val n = br.read()
                        if (n == -1) break
                        if (!n.toChar().isWhitespace()) {
                            nextNonWs = n
                            break
                        }
                    }
                    br.reset()
                    if (nextNonWs == -1) {
                        return true
                    }
                    val nc = nextNonWs.toChar()
                    if ((c == '[' && (nc == '{' || nc == ']' || nc == '[' || nc == '"')) ||
                        (c == '{' && (nc == '"' || nc == '}'))
                    ) {
                        return true
                    }
                    // Otherwise advance past this false `{`/`[` and continue scanning
                    br.read()
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun safeReadString(reader: JsonReader): String {
        return try {
            when (reader.peek()) {
                JsonToken.STRING -> reader.nextString().trim()
                JsonToken.NUMBER -> reader.nextString().trim()
                JsonToken.BOOLEAN -> {
                    reader.nextBoolean()
                    ""
                }
                JsonToken.NULL -> {
                    reader.nextNull()
                    ""
                }
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    var firstVal = ""
                    while (reader.hasNext()) {
                        val candidate = safeReadString(reader)
                        if (firstVal.isEmpty() && candidate.isNotBlank() && candidate != "0" && !candidate.equals("null", true)) {
                            firstVal = candidate
                        }
                    }
                    reader.endArray()
                    firstVal
                }
                JsonToken.BEGIN_OBJECT -> {
                    reader.skipValue()
                    ""
                }
                else -> {
                    reader.skipValue()
                    ""
                }
            }
        } catch (ioe: java.io.IOException) {
            throw ioe
        } catch (_: Throwable) {
            runCatching { reader.skipValue() }
            ""
        }
    }

    private fun safeReadInt(reader: JsonReader, defaultVal: Int = 0): Int {
        return try {
            when (reader.peek()) {
                JsonToken.NUMBER -> {
                    runCatching { reader.nextInt() }.getOrElse {
                        runCatching { reader.nextLong().toInt() }.getOrElse {
                            runCatching { reader.nextDouble().toInt() }.getOrElse {
                                reader.nextString().filter { it.isDigit() }.take(9).toIntOrNull() ?: defaultVal
                            }
                        }
                    }
                }
                JsonToken.STRING -> {
                    val s = reader.nextString().trim()
                    s.toIntOrNull()
                        ?: s.substringBefore(".").filter { it.isDigit() }.take(9).toIntOrNull()
                        ?: defaultVal
                }
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    var firstInt = defaultVal
                    var gotFirst = false
                    while (reader.hasNext()) {
                        if (!gotFirst) {
                            firstInt = safeReadInt(reader, defaultVal)
                            gotFirst = true
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endArray()
                    firstInt
                }
                JsonToken.BOOLEAN -> {
                    reader.nextBoolean()
                    defaultVal
                }
                JsonToken.NULL -> {
                    reader.nextNull()
                    defaultVal
                }
                else -> {
                    reader.skipValue()
                    defaultVal
                }
            }
        } catch (ioe: java.io.IOException) {
            throw ioe
        } catch (_: Throwable) {
            runCatching { reader.skipValue() }
            defaultVal
        }
    }

    private val sectionCompletionRegistry = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun wasSectionSyncedCompletely(playlistId: String, contentType: StreamContentType): Boolean {
        return sectionCompletionRegistry["${playlistId.trim()}_${contentType.name}"] ?: true
    }

    private fun recordSectionCompletion(playlistId: String, contentType: StreamContentType, completedCleanly: Boolean) {
        if (playlistId.isNotBlank()) {
            sectionCompletionRegistry["${playlistId.trim()}_${contentType.name}"] = completedCleanly
        }
    }

    /**
     * Universal streaming collection reader:
     * Handles standard JSONArrays `[ {...}, {...} ]`, ID-keyed JSONObjects `{ "1": {...}, "2": {...} }`,
     * and nested wrapper JSONObjects `{ "status": true, "data": [ {...} ] }` or `{ "available_channels": { "1": {...} } }`.
     */
    private fun parseUniversalJsonCollection(
        reader: JsonReader,
        isCategoryMode: Boolean = false,
        parseSingleObject: (fallbackKey: String) -> Unit
    ): Boolean {
        val wrapperKeys = if (isCategoryMode) CATEGORY_WRAPPER_KEYS else STREAM_WRAPPER_KEYS
        return try {
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        try {
                            when (reader.peek()) {
                                JsonToken.BEGIN_OBJECT -> parseSingleObject("")
                                JsonToken.BEGIN_ARRAY -> parseUniversalJsonCollection(reader, isCategoryMode, parseSingleObject)
                                else -> reader.skipValue()
                            }
                        } catch (ioe: java.io.IOException) {
                            throw ioe
                        } catch (_: Throwable) {
                            runCatching { reader.skipValue() }
                        }
                    }
                    reader.endArray()
                    true
                }
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val propName = reader.nextName()
                        val propClean = propName.trim().lowercase(Locale.ROOT)
                        try {
                            when (reader.peek()) {
                                JsonToken.BEGIN_ARRAY -> {
                                    if (!isCategoryMode && propClean in IGNORED_META_KEYS) {
                                        reader.skipValue()
                                    } else {
                                        parseUniversalJsonCollection(reader, isCategoryMode, parseSingleObject)
                                    }
                                }
                                JsonToken.BEGIN_OBJECT -> {
                                    if (!isCategoryMode && propClean in IGNORED_META_KEYS) {
                                        reader.skipValue()
                                    } else if (propClean in wrapperKeys) {
                                        parseUniversalJsonCollection(reader, isCategoryMode, parseSingleObject)
                                    } else if (propClean == "user_info" || propClean == "server_info") {
                                        reader.skipValue()
                                    } else {
                                        parseSingleObject(propName.trim())
                                    }
                                }
                                else -> reader.skipValue()
                            }
                        } catch (ioe: java.io.IOException) {
                            throw ioe
                        } catch (_: Throwable) {
                            runCatching { reader.skipValue() }
                        }
                    }
                    reader.endObject()
                    true
                }
                else -> {
                    reader.skipValue()
                    true
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Uses LinkedHashMap to preserve the exact category order sent by any Xtream server variant.
     */
    private fun fetchCategoryMapStreaming(url: String, serverBaseUrl: String = ""): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>(256)
        val rememberedUa = IboPlayerProfileSpoofer.getRememberedHostProfile(serverBaseUrl.ifBlank { url })?.userAgent
        val uaList = if (!rememberedUa.isNullOrBlank()) {
            (listOf(rememberedUa) + IPTV_USER_AGENTS).distinct()
        } else IPTV_USER_AGENTS
        for ((uaIdx, ua) in uaList.take(5).withIndex()) {
            runCatching {
                val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                    builder = Request.Builder().url(url),
                    targetUrl = url,
                    serverBaseUrl = serverBaseUrl,
                    userAgent = ua,
                    useIdentityEncoding = true,
                    includeRefererHeader = (uaIdx % 2 == 0)
                ).build()
                xtreamHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    BufferedReader(InputStreamReader(wrapAutoDecompressStream(body.byteStream()), Charsets.UTF_8), 262144).use { br ->
                        if (!skipToFirstJsonToken(br)) return@use
                        val reader = JsonReader(br)
                        reader.isLenient = true
                        parseUniversalJsonCollection(reader, isCategoryMode = true) { fallbackKey ->
                            readSingleCategoryObject(reader, map, fallbackKey)
                        }
                    }
                }
            }
            if (map.isNotEmpty()) break
        }
        return map
    }

    private fun readSingleCategoryObject(
        reader: JsonReader,
        map: MutableMap<String, String>,
        fallbackKey: String = ""
    ) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        var id = fallbackKey
        var name = ""
        reader.beginObject()
        while (reader.hasNext()) {
            val k = reader.nextName().trim().lowercase(Locale.ROOT)
            when (k) {
                "category_id", "id", "cat_id", "cid" -> {
                    val v = safeReadString(reader)
                    if (v.isNotBlank()) id = v
                }
                "category_name", "name", "title", "cat_name", "group", "group_title" -> {
                    val v = safeReadString(reader)
                    if (v.isNotBlank()) name = v
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (id.isNotBlank() && name.isNotBlank() && !AdultContentFilter.isAdultOr18Plus(name, name)) {
            map[id] = name
        }
    }

    private fun isValidCategoryIdToken(v: String): Boolean {
        return v.isNotBlank() && v != "0" && !v.equals("null", ignoreCase = true) && !v.equals("false", ignoreCase = true)
    }

    private fun streamParseLiveChannels(
        url: String,
        liveCats: Map<String, String>,
        serverLabel: String,
        playlistId: String,
        baseUrl: String,
        uEnc: String,
        pEnc: String,
        serverDefaultLiveExt: String = "",
        onBatchProgress: (Int, List<TvChannel>?) -> Unit = { _, _ -> }
    ): List<TvChannel> {
        var bestList = ArrayList<TvChannel>()
        val groupPool = HashMap<String, String>(256)
        val rememberedUa = IboPlayerProfileSpoofer.getRememberedHostProfile(baseUrl.ifBlank { url })?.userAgent
        val uaList = if (!rememberedUa.isNullOrBlank()) {
            (listOf(rememberedUa) + IPTV_USER_AGENTS).distinct()
        } else IPTV_USER_AGENTS
        for ((uaIdx, ua) in uaList.take(5).withIndex()) {
            val list = ArrayList<TvChannel>(65536)
            var completedCleanly = false
            runCatching {
                val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                    builder = Request.Builder().url(url),
                    targetUrl = url,
                    serverBaseUrl = baseUrl,
                    userAgent = ua,
                    useIdentityEncoding = true,
                    includeRefererHeader = (uaIdx % 2 == 0)
                ).build()
                xtreamHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    BufferedReader(InputStreamReader(wrapAutoDecompressStream(body.byteStream()), Charsets.UTF_8), 524288).use { br ->
                        if (!skipToFirstJsonToken(br)) return@use
                        val reader = JsonReader(br)
                        reader.isLenient = true
                        var index = 1
                        completedCleanly = parseUniversalJsonCollection(reader, isCategoryMode = false) { fallbackKey ->
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                var streamId = fallbackKey.toIntOrNull() ?: 0
                                var hasExplicitStreamId = false
                                var num = index
                                var name = ""
                                var catId = ""
                                var categoryName = ""
                                var icon = ""
                                var directSource = ""
                                var ext = ""
                                while (reader.hasNext()) {
                                    val k = reader.nextName().trim().lowercase(Locale.ROOT)
                                    when (k) {
                                        "stream_id" -> {
                                            val v = safeReadInt(reader, 0)
                                            if (v > 0) {
                                                streamId = v
                                                hasExplicitStreamId = true
                                            }
                                        }
                                        "id", "channel_id", "cid" -> {
                                            val v = safeReadInt(reader, 0)
                                            if (v > 0 && !hasExplicitStreamId) streamId = v
                                        }
                                        "num", "number", "ch_num" -> {
                                            val v = safeReadInt(reader, index)
                                            if (v > 0) num = v
                                        }
                                        "name", "title", "stream_display_name", "channel_name", "o_name" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && name.isBlank()) name = v
                                        }
                                        "category_id", "cat_id", "category_ids" -> {
                                            val v = safeReadString(reader)
                                            if (isValidCategoryIdToken(v) && !isValidCategoryIdToken(catId)) catId = v
                                        }
                                        "category_name", "category", "group", "group_title" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && !v.equals("null", true)) categoryName = v
                                        }
                                        "stream_icon", "logo", "icon", "tvg_logo", "img" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank()) icon = v
                                        }
                                        "direct_source", "stream_url", "url", "play_url", "link", "file" -> {
                                            val v = safeReadString(reader)
                                            if (v.startsWith("http", ignoreCase = true) || v.startsWith("rtmp", ignoreCase = true) || v.startsWith("rtsp", ignoreCase = true)) {
                                                directSource = v
                                            }
                                        }
                                        "container_extension", "extension", "ext", "output" -> {
                                            val v = safeReadString(reader).lowercase(Locale.ROOT).removePrefix(".")
                                            if (v.isNotBlank() && v != "null") ext = v
                                        }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (streamId > 0 || directSource.isNotBlank()) {
                                    val rawGroup = categoryName.ifBlank {
                                        liveCats[catId] ?: "باقات $serverLabel"
                                    }
                                    val groupName = groupPool.getOrPut(rawGroup) { rawGroup }
                                    val finalName = name.ifBlank { "قناة $index" }
                                    if (!AdultContentFilter.isAdultOr18Plus(finalName, groupName)) {
                                        // التزام صارم بالصيغة التي حددها السيرفر للعنصر أو في allowed_output_formats
                                        val cleanExt = when {
                                            ext.isNotBlank() -> ext
                                            directSource.substringBefore("?").endsWith(".m3u8", ignoreCase = true) -> "m3u8"
                                            directSource.substringBefore("?").endsWith(".ts", ignoreCase = true) -> "ts"
                                            serverDefaultLiveExt.isNotBlank() -> serverDefaultLiveExt
                                            else -> "ts"
                                        }
                                        val streamUrl = directSource.takeIf { it.isNotBlank() }
                                            ?: "$baseUrl/live/$uEnc/$pEnc/$streamId.$cleanExt"
                                        list.add(
                                            TvChannel(
                                                id = 500_000 + index,
                                                number = if (num > 0) num else index,
                                                name = finalName,
                                                url = streamUrl,
                                                group = groupName,
                                                logo = icon.takeIf { it.startsWith("http") },
                                                contentType = StreamContentType.LIVE_TV,
                                                playlistId = playlistId,
                                                playlistName = serverLabel,
                                                streamId = streamId,
                                                streamBaseUrl = baseUrl,
                                                streamUsername = uEnc,
                                                streamPassword = pEnc,
                                                containerExtension = cleanExt
                                            )
                                        )
                                        if (list.size == 600 || list.size == 2500) {
                                            val earlySnap = ArrayList<TvChannel>(list)
                                            fastBucketSortByCategoryOrder(earlySnap, liveCats)
                                            onBatchProgress(list.size, earlySnap)
                                        } else if (list.size == 1 || list.size % 150 == 0) {
                                            onBatchProgress(list.size, null)
                                        }
                                        index++
                                    }
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                    }
                }
            }
            if (completedCleanly && list.isNotEmpty()) {
                bestList = list
                recordSectionCompletion(playlistId, StreamContentType.LIVE_TV, true)
                break
            }
            if (list.size > bestList.size) {
                bestList = list
                recordSectionCompletion(playlistId, StreamContentType.LIVE_TV, false)
            }
            // إذا انقطع التدفق في المنتصف (بسبب رمشة شبكة أو تبديل مسار VPN)، ننتظر استقرار المسار ونفرغ الاتصالات لإعادة السحب كاملاً 100%
            ChannelRepository.evictIdleApiConnections()
            runCatching { Thread.sleep(450L + (uaIdx * 250L)) }
        }
        return bestList
    }

    private fun streamParseVodMovies(
        url: String,
        vodCats: Map<String, String>,
        serverLabel: String,
        playlistId: String,
        baseUrl: String,
        uEnc: String,
        pEnc: String,
        onBatchProgress: (Int, List<TvChannel>?) -> Unit = { _, _ -> }
    ): List<TvChannel> {
        var bestList = ArrayList<TvChannel>()
        val groupPool = HashMap<String, String>(256)
        val rememberedUa = IboPlayerProfileSpoofer.getRememberedHostProfile(baseUrl.ifBlank { url })?.userAgent
        val uaList = if (!rememberedUa.isNullOrBlank()) {
            (listOf(rememberedUa) + IPTV_USER_AGENTS).distinct()
        } else IPTV_USER_AGENTS
        for ((uaIdx, ua) in uaList.take(5).withIndex()) {
            val list = ArrayList<TvChannel>(131072)
            var completedCleanly = false
            runCatching {
                val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                    builder = Request.Builder().url(url),
                    targetUrl = url,
                    serverBaseUrl = baseUrl,
                    userAgent = ua,
                    useIdentityEncoding = true,
                    includeRefererHeader = (uaIdx % 2 == 0)
                ).build()
                xtreamHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    BufferedReader(InputStreamReader(wrapAutoDecompressStream(body.byteStream()), Charsets.UTF_8), 524288).use { br ->
                        if (!skipToFirstJsonToken(br)) return@use
                        val reader = JsonReader(br)
                        reader.isLenient = true
                        var index = 1
                        completedCleanly = parseUniversalJsonCollection(reader, isCategoryMode = false) { fallbackKey ->
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                var streamId = fallbackKey.toIntOrNull() ?: 0
                                var hasExplicitStreamId = false
                                var name = ""
                                var catId = ""
                                var categoryName = ""
                                var icon = ""
                                var ext = "mp4"
                                var rating = ""
                                val plot = ""
                                var releaseYear = ""
                                var directSource = ""
                                while (reader.hasNext()) {
                                    val k = reader.nextName().trim().lowercase(Locale.ROOT)
                                    when (k) {
                                        "stream_id", "vod_id", "movie_id" -> {
                                            val v = safeReadInt(reader, 0)
                                            if (v > 0) {
                                                streamId = v
                                                hasExplicitStreamId = true
                                            }
                                        }
                                        "id" -> {
                                            val v = safeReadInt(reader, 0)
                                            if (v > 0 && !hasExplicitStreamId) streamId = v
                                        }
                                        "name", "title", "stream_display_name", "movie_name", "o_name" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && name.isBlank()) name = v
                                        }
                                        "category_id", "cat_id", "category_ids" -> {
                                            val v = safeReadString(reader)
                                            if (isValidCategoryIdToken(v) && !isValidCategoryIdToken(catId)) catId = v
                                        }
                                        "category_name", "category", "group", "group_title" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && !v.equals("null", true)) categoryName = v
                                        }
                                        "cover_big", "movie_image", "poster" -> {
                                            val v = safeReadString(reader)
                                            if (v.startsWith("http", ignoreCase = true)) icon = FluxBitmapCache.upgradeImageUrlToHighRes(v)
                                        }
                                        "stream_icon", "cover", "logo", "icon", "img" -> {
                                            val v = safeReadString(reader)
                                            if (v.startsWith("http", ignoreCase = true) && icon.isBlank()) icon = FluxBitmapCache.upgradeImageUrlToHighRes(v)
                                        }
                                        "container_extension", "target_container", "extension", "ext" -> {
                                            val v = safeReadString(reader).lowercase(Locale.ROOT).removePrefix(".")
                                            if (v.isNotBlank() && v != "null") ext = v
                                        }
                                        "rating", "rating_5based", "tmdb_rating" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && v != "0" && rating.isBlank()) rating = v
                                        }
                                        "plot", "description", "overview" -> {
                                            reader.skipValue()
                                        }
                                        "releasedate", "release_date", "year" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && releaseYear.isBlank()) releaseYear = v
                                        }
                                        "direct_source", "stream_url", "url", "play_url", "link", "file" -> {
                                            val v = safeReadString(reader)
                                            if (v.startsWith("http", ignoreCase = true)) directSource = v
                                        }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (streamId > 0 || directSource.isNotBlank()) {
                                    val rawGroup = categoryName.ifBlank {
                                        vodCats[catId] ?: "أفلام $serverLabel"
                                    }
                                    val groupName = groupPool.getOrPut(rawGroup) { rawGroup }
                                    val finalName = name.ifBlank { "فيلم $index" }
                                    if (!AdultContentFilter.isAdultOr18Plus(finalName, groupName)) {
                                        val cleanExt = ext.ifBlank { "mp4" }
                                        val streamUrl = directSource.takeIf { it.isNotBlank() }
                                            ?: "$baseUrl/movie/$uEnc/$pEnc/$streamId.$cleanExt"
                                        list.add(
                                            TvChannel(
                                                id = 700_000 + index,
                                                number = index,
                                                name = finalName,
                                                url = streamUrl,
                                                group = groupName,
                                                logo = icon.takeIf { it.startsWith("http") },
                                                contentType = StreamContentType.MOVIE,
                                                rating = rating,
                                                releaseYear = releaseYear,
                                                plot = plot,
                                                playlistId = playlistId,
                                                playlistName = serverLabel,
                                                streamId = streamId,
                                                streamBaseUrl = baseUrl,
                                                streamUsername = uEnc,
                                                streamPassword = pEnc,
                                                containerExtension = cleanExt
                                            )
                                        )
                                        if (list.size == 800 || list.size == 3000) {
                                            val earlyVodSnap = ArrayList<TvChannel>(list)
                                            fastBucketSortByCategoryOrder(earlyVodSnap, vodCats)
                                            onBatchProgress(list.size, earlyVodSnap)
                                        } else if (list.size == 1 || list.size % 200 == 0) {
                                            onBatchProgress(list.size, null)
                                        }
                                        index++
                                    }
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                    }
                }
            }
            if (completedCleanly && list.isNotEmpty()) {
                bestList = list
                recordSectionCompletion(playlistId, StreamContentType.MOVIE, true)
                break
            }
            if (list.size > bestList.size) {
                bestList = list
                recordSectionCompletion(playlistId, StreamContentType.MOVIE, false)
            }
            // إذا انقطع سحب الأفلام في المنتصف (مثال: عند 32 ألف فيلم من أصل 225 ألف)، نفرغ الاتصالات وننتظر استقرار المسار لإعادة السحب كاملاً 100%
            ChannelRepository.evictIdleApiConnections()
            runCatching { Thread.sleep(550L + (uaIdx * 300L)) }
        }
        return bestList
    }

    private fun streamParseSeriesItems(
        url: String,
        seriesCats: Map<String, String>,
        serverLabel: String,
        playlistId: String,
        apiBase: String,
        baseUrl: String,
        uEnc: String,
        pEnc: String,
        onBatchProgress: (Int, List<TvChannel>?) -> Unit = { _, _ -> }
    ): List<TvChannel> {
        var bestList = ArrayList<TvChannel>()
        val groupPool = HashMap<String, String>(256)
        val rememberedUa = IboPlayerProfileSpoofer.getRememberedHostProfile(baseUrl.ifBlank { url })?.userAgent
        val uaList = if (!rememberedUa.isNullOrBlank()) {
            (listOf(rememberedUa) + IPTV_USER_AGENTS).distinct()
        } else IPTV_USER_AGENTS
        for ((uaIdx, ua) in uaList.take(5).withIndex()) {
            val list = ArrayList<TvChannel>(65536)
            var completedCleanly = false
            runCatching {
                val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                    builder = Request.Builder().url(url),
                    targetUrl = url,
                    serverBaseUrl = baseUrl,
                    userAgent = ua,
                    useIdentityEncoding = true,
                    includeRefererHeader = (uaIdx % 2 == 0)
                ).build()
                xtreamHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    BufferedReader(InputStreamReader(wrapAutoDecompressStream(body.byteStream()), Charsets.UTF_8), 524288).use { br ->
                        if (!skipToFirstJsonToken(br)) return@use
                        val reader = JsonReader(br)
                        reader.isLenient = true
                        var index = 1
                        completedCleanly = parseUniversalJsonCollection(reader, isCategoryMode = false) { fallbackKey ->
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                var seriesId = fallbackKey.toIntOrNull() ?: 0
                                var hasExplicitSeriesId = false
                                var name = ""
                                var catId = ""
                                var categoryName = ""
                                var cover = ""
                                var rating = ""
                                val plot = ""
                                var releaseDate = ""
                                while (reader.hasNext()) {
                                    val k = reader.nextName().trim().lowercase(Locale.ROOT)
                                    when (k) {
                                        "series_id" -> {
                                            val v = safeReadInt(reader, 0)
                                            if (v > 0) {
                                                seriesId = v
                                                hasExplicitSeriesId = true
                                            }
                                        }
                                        "id", "sid" -> {
                                            val v = safeReadInt(reader, 0)
                                            if (v > 0 && !hasExplicitSeriesId) seriesId = v
                                        }
                                        "name", "title", "series_name", "o_name" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && name.isBlank()) name = v
                                        }
                                        "category_id", "cat_id", "category_ids" -> {
                                            val v = safeReadString(reader)
                                            if (isValidCategoryIdToken(v) && !isValidCategoryIdToken(catId)) catId = v
                                        }
                                        "category_name", "category", "group", "group_title" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && !v.equals("null", true)) categoryName = v
                                        }
                                        "cover_big", "poster" -> {
                                            val v = safeReadString(reader)
                                            if (v.startsWith("http", ignoreCase = true)) cover = FluxBitmapCache.upgradeImageUrlToHighRes(v)
                                        }
                                        "cover", "stream_icon", "logo", "img" -> {
                                            val v = safeReadString(reader)
                                            if (v.startsWith("http", ignoreCase = true) && cover.isBlank()) cover = FluxBitmapCache.upgradeImageUrlToHighRes(v)
                                        }
                                        "rating", "rating_5based" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && v != "0" && rating.isBlank()) rating = v
                                        }
                                        "plot", "description", "overview" -> {
                                            reader.skipValue()
                                        }
                                        "releasedate", "release_date", "year" -> {
                                            val v = safeReadString(reader)
                                            if (v.isNotBlank() && releaseDate.isBlank()) releaseDate = v
                                        }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (seriesId > 0) {
                                    val rawGroup = categoryName.ifBlank {
                                        seriesCats[catId] ?: "مسلسلات $serverLabel"
                                    }
                                    val groupName = groupPool.getOrPut(rawGroup) { rawGroup }
                                    val finalName = name.ifBlank { "مسلسل $index" }
                                    if (!AdultContentFilter.isAdultOr18Plus(finalName, groupName)) {
                                        val seriesInfoUrl = "$apiBase&action=get_series_info&series_id=$seriesId"
                                        list.add(
                                            TvChannel(
                                                id = 900_000 + index,
                                                number = index,
                                                name = finalName,
                                                url = seriesInfoUrl,
                                                group = groupName,
                                                logo = cover.takeIf { it.startsWith("http") },
                                                contentType = StreamContentType.SERIES,
                                                rating = rating,
                                                releaseYear = releaseDate,
                                                plot = plot,
                                                seriesId = seriesId,
                                                playlistId = playlistId,
                                                playlistName = serverLabel,
                                                streamBaseUrl = baseUrl,
                                                streamUsername = uEnc,
                                                streamPassword = pEnc
                                            )
                                        )
                                        if (list.size == 500 || list.size == 2000) {
                                            val earlySeriesSnap = ArrayList<TvChannel>(list)
                                            fastBucketSortByCategoryOrder(earlySeriesSnap, seriesCats)
                                            onBatchProgress(list.size, earlySeriesSnap)
                                        } else if (list.size == 1 || list.size % 150 == 0) {
                                            onBatchProgress(list.size, null)
                                        }
                                        index++
                                    }
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                    }
                }
            }
            if (completedCleanly && list.isNotEmpty()) {
                bestList = list
                recordSectionCompletion(playlistId, StreamContentType.SERIES, true)
                break
            }
            if (list.size > bestList.size) {
                bestList = list
                recordSectionCompletion(playlistId, StreamContentType.SERIES, false)
            }
            ChannelRepository.evictIdleApiConnections()
            runCatching { Thread.sleep(500L + (uaIdx * 250L)) }
        }
        return bestList
    }

    /**
     * Parses legacy Xtream panel_api.php responses which include both `categories` (`live` / `movie`)
     * and `available_channels` (which may contain both `live` and `movie` stream types).
     */
    private fun parsePanelApiFallback(
        panelUrl: String,
        serverLabel: String,
        playlistId: String,
        baseUrl: String,
        uEnc: String,
        pEnc: String
    ): Pair<List<TvChannel>, List<TvChannel>> {
        val cats = fetchCategoryMapStreaming(panelUrl, baseUrl)
        val rawItems = streamParseLiveChannels(
            url = panelUrl,
            liveCats = cats,
            serverLabel = serverLabel,
            playlistId = playlistId,
            baseUrl = baseUrl,
            uEnc = uEnc,
            pEnc = pEnc
        )
        val liveOut = ArrayList<TvChannel>()
        val movieOut = ArrayList<TvChannel>()
        for (item in rawItems) {
            val extLow = item.containerExtension.lowercase(Locale.ROOT)
            if (extLow in setOf("mp4", "mkv", "avi", "mov", "flv", "webm")) {
                val vodUrl = "$baseUrl/movie/$uEnc/$pEnc/${item.streamId}.$extLow"
                movieOut.add(item.copy(contentType = StreamContentType.MOVIE, url = vodUrl))
            } else {
                liveOut.add(item)
            }
        }
        return liveOut to movieOut
    }

    private val vodResolvedUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Resolves the exact VOD container_extension (e.g. mkv, mp4, avi, ts) or direct_source via get_vod_info
     * when an Xtream server omits or misreports container_extension in the bulk get_vod_streams list.
     */
    suspend fun resolveVodRealUrlIfNeeded(
        channel: TvChannel,
        matchingAccount: XtreamAccountConfig?
    ): String? = withContext(Dispatchers.IO) {
        if (channel.contentType != StreamContentType.MOVIE || channel.streamId <= 0) return@withContext null
        val cacheKey = "${channel.playlistId}_${channel.streamId}"
        vodResolvedUrlCache[cacheKey]?.let { return@withContext it }

        val rawBase = (matchingAccount?.serverUrl?.takeIf { it.isNotBlank() } ?: channel.streamBaseUrl).trim().removeSuffix("/")
        val baseUrl = normalizeServerUrl(rawBase)
        val rawUser = (matchingAccount?.username?.takeIf { it.isNotBlank() } ?: channel.streamUsername).trim()
        val rawPass = (matchingAccount?.password?.takeIf { it.isNotBlank() } ?: channel.streamPassword).trim()
        if (baseUrl.isBlank() || rawUser.isBlank() || rawPass.isBlank()) return@withContext null

        val uEnc = safeEncodeParam(rawUser)
        val pEnc = safeEncodeParam(rawPass)

        val infoUrl = "$baseUrl/player_api.php?username=$uEnc&password=$pEnc&action=get_vod_info&vod_id=${channel.streamId}"
        val rawJson = runCatching {
            withTimeout(4_000L) {
                fetchUrlTextFast(infoUrl)
            }
        }.getOrDefault("")
        val clean = extractJsonPayload(rawJson)
        if (!clean.startsWith("{")) return@withContext null

        return@withContext runCatching {
            val root = JSONObject(clean)
            val movieData = root.optJSONObject("movie_data")
                ?: root.optJSONObject("data")?.optJSONObject("movie_data")
                ?: root.optJSONObject("info")
                ?: root
            val direct = movieData.optString("direct_source", "").trim()
            if (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true)) {
                vodResolvedUrlCache[cacheKey] = direct
                return@runCatching direct
            }
            val realExt = movieData.optString("container_extension", "")
                .ifBlank { movieData.optString("target_container", "") }
                .ifBlank { root.optJSONObject("info")?.optString("container_extension", "").orEmpty() }
                .trim().lowercase(Locale.ROOT).removePrefix(".")
            if (realExt.isNotBlank()) {
                val built = "$baseUrl/movie/$uEnc/$pEnc/${channel.streamId}.$realExt"
                vodResolvedUrlCache[cacheKey] = built
                built
            } else null
        }.getOrNull()
    }

    private val seriesEpisodesMemoryCache = java.util.concurrent.ConcurrentHashMap<String, List<SeriesEpisode>>()

    private val seriesInfoFastClient by lazy {
        ChannelRepository.bootstrapHttpClient.newBuilder()
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(16, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun resolveEffectiveSeriesId(seriesChannel: TvChannel): Int {
        if (seriesChannel.seriesId > 0) return seriesChannel.seriesId
        val fromUrl = Regex("[?&]series_id=(\\d+)", RegexOption.IGNORE_CASE)
            .find(seriesChannel.url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        if (fromUrl > 0) return fromUrl
        if (seriesChannel.streamId > 0 && seriesChannel.url.contains("action=get_series_info", ignoreCase = true)) {
            return seriesChannel.streamId
        }
        return 0
    }

    fun getCachedSeriesEpisodesNow(seriesChannel: TvChannel): List<SeriesEpisode>? {
        if (seriesChannel.episodes.isNotEmpty()) return seriesChannel.episodes
        val effectiveSid = resolveEffectiveSeriesId(seriesChannel)
        if (effectiveSid <= 0) return null
        val cacheKey = "${seriesChannel.playlistId}_$effectiveSid"
        return seriesEpisodesMemoryCache[cacheKey]
    }

    private fun readSingleEpisodeStreaming(
        reader: JsonReader,
        defaultSeason: Int,
        defaultIndex: Int,
        baseUrl: String,
        user: String,
        pass: String,
        outList: MutableList<SeriesEpisode>
    ) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        var epId = ""
        var directUrl = ""
        var sNum = defaultSeason.coerceAtLeast(1)
        var epNum = defaultIndex.coerceAtLeast(1)
        var epTitle = ""
        var ext = "mp4"
        var dur = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName().trim().lowercase(Locale.ROOT)) {
                "id", "stream_id", "episode_id" -> {
                    val v = safeReadString(reader)
                    if (v.isNotBlank() && epId.isBlank()) epId = v
                }
                "direct_source", "url", "stream_url" -> {
                    val v = safeReadString(reader)
                    if (v.startsWith("http", ignoreCase = true)) directUrl = v
                }
                "season", "season_num" -> {
                    val v = safeReadInt(reader, defaultSeason)
                    if (v > 0) sNum = v
                }
                "episode_num", "num" -> {
                    val v = safeReadInt(reader, defaultIndex)
                    if (v > 0) epNum = v
                }
                "title", "name" -> {
                    val v = safeReadString(reader)
                    if (v.isNotBlank() && !v.equals("false", ignoreCase = true) && epTitle.isBlank()) epTitle = v
                }
                "container_extension", "ext" -> {
                    val v = safeReadString(reader).lowercase(Locale.ROOT).removePrefix(".")
                    if (v.isNotBlank()) ext = v
                }
                "duration" -> {
                    val v = safeReadString(reader)
                    if (v.isNotBlank() && !v.equals("false", ignoreCase = true)) dur = v
                }
                "info" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val ik = reader.nextName().trim().lowercase(Locale.ROOT)
                            if (ik == "duration") {
                                val dv = safeReadString(reader)
                                if (dv.isNotBlank() && !dv.equals("false", ignoreCase = true)) dur = dv
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (epId.isBlank() && !directUrl.startsWith("http", ignoreCase = true)) return
        val playUrl = directUrl.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: "$baseUrl/series/$user/$pass/$epId.$ext"
        outList.add(
            SeriesEpisode(
                id = epId.ifBlank { "${sNum}_$epNum" },
                seasonNumber = sNum,
                episodeNumber = epNum,
                title = epTitle.ifBlank { "الموسم $sNum - الحلقة $epNum" },
                url = playUrl,
                durationLabel = dur,
                containerExtension = ext.ifBlank { "mp4" }
            )
        )
    }

    private fun parseEpisodesNodeStreaming(
        reader: JsonReader,
        seasonNumber: Int,
        baseUrl: String,
        user: String,
        pass: String,
        outList: MutableList<SeriesEpisode>
    ) {
        when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                var idx = 1
                while (reader.hasNext()) {
                    when (reader.peek()) {
                        JsonToken.BEGIN_OBJECT -> readSingleEpisodeStreaming(reader, seasonNumber, idx++, baseUrl, user, pass, outList)
                        JsonToken.BEGIN_ARRAY -> parseEpisodesNodeStreaming(reader, if (seasonNumber <= 1) idx++ else seasonNumber, baseUrl, user, pass, outList)
                        else -> reader.skipValue()
                    }
                }
                reader.endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                var idx = 1
                while (reader.hasNext()) {
                    val key = reader.nextName().trim()
                    val inferredSeason = key.toIntOrNull() ?: seasonNumber
                    when (reader.peek()) {
                        JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> {
                            parseEpisodesNodeStreaming(reader, inferredSeason, baseUrl, user, pass, outList)
                        }
                        else -> reader.skipValue()
                    }
                    idx++
                }
                reader.endObject()
            }
            else -> reader.skipValue()
        }
    }

    private fun fetchAndParseSeriesInfoFast(
        infoUrl: String,
        baseUrl: String,
        user: String,
        pass: String
    ): List<SeriesEpisode> {
        for (ua in IPTV_USER_AGENTS.take(3)) {
            val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                builder = Request.Builder().url(infoUrl),
                targetUrl = infoUrl,
                serverBaseUrl = baseUrl,
                userAgent = ua,
                useIdentityEncoding = false
            ).build()
            for (client in listOf(seriesInfoFastClient, ChannelRepository.httpClient)) {
                try {
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body ?: return@use
                        val list = ArrayList<SeriesEpisode>(128)
                        BufferedReader(InputStreamReader(wrapAutoDecompressStream(body.byteStream()), Charsets.UTF_8), 65536).use { br ->
                            if (!skipToFirstJsonToken(br)) return@use
                            val reader = JsonReader(br)
                            reader.isLenient = true
                            when (reader.peek()) {
                                JsonToken.BEGIN_ARRAY -> {
                                    parseEpisodesNodeStreaming(reader, 1, baseUrl, user, pass, list)
                                }
                                JsonToken.BEGIN_OBJECT -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName().trim().lowercase(Locale.ROOT)) {
                                            "episodes" -> parseEpisodesNodeStreaming(reader, 1, baseUrl, user, pass, list)
                                            "data" -> {
                                                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                                    reader.beginObject()
                                                    while (reader.hasNext()) {
                                                        if (reader.nextName().trim().equals("episodes", ignoreCase = true)) {
                                                            parseEpisodesNodeStreaming(reader, 1, baseUrl, user, pass, list)
                                                        } else {
                                                            reader.skipValue()
                                                        }
                                                    }
                                                    reader.endObject()
                                                } else {
                                                    reader.skipValue()
                                                }
                                            }
                                            "seasons" -> {
                                                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                                    reader.beginArray()
                                                    var sIdx = 1
                                                    while (reader.hasNext()) {
                                                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                                            reader.beginObject()
                                                            var sNum = sIdx
                                                            while (reader.hasNext()) {
                                                                val sk = reader.nextName().trim().lowercase(Locale.ROOT)
                                                                when (sk) {
                                                                    "season_number", "season" -> {
                                                                        val v = safeReadInt(reader, sIdx)
                                                                        if (v > 0) sNum = v
                                                                    }
                                                                    "episodes" -> parseEpisodesNodeStreaming(reader, sNum, baseUrl, user, pass, list)
                                                                    else -> reader.skipValue()
                                                                }
                                                            }
                                                            reader.endObject()
                                                        } else {
                                                            reader.skipValue()
                                                        }
                                                        sIdx++
                                                    }
                                                    reader.endArray()
                                                } else {
                                                    reader.skipValue()
                                                }
                                            }
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        if (list.isNotEmpty()) {
                            return list.distinctBy { "${it.seasonNumber}_${it.episodeNumber}_${it.id}" }
                                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        return emptyList()
    }

    suspend fun fetchSeriesEpisodes(
        seriesChannel: TvChannel,
        xtreamLists: List<XtreamAccountConfig>
    ): List<SeriesEpisode> = withContext(Dispatchers.IO) {
        if (seriesChannel.episodes.isNotEmpty()) return@withContext seriesChannel.episodes
        val effectiveSid = resolveEffectiveSeriesId(seriesChannel)
        val cacheKey = "${seriesChannel.playlistId}_$effectiveSid"
        if (effectiveSid > 0) {
            seriesEpisodesMemoryCache[cacheKey]?.let { if (it.isNotEmpty()) return@withContext it }
        }

        val matchingAccount = xtreamLists.find { it.id == seriesChannel.playlistId }
            ?: xtreamLists.firstOrNull { it.isConfigured }
        val seriesUrlMatch = Regex("^(https?://[^/]+)/series/([^/]+)/([^/]+)/", RegexOption.IGNORE_CASE)
            .find(seriesChannel.url.trim())
        val urlBaseFallback = when {
            seriesChannel.url.contains("/player_api.php", ignoreCase = true) ->
                seriesChannel.url.substringBefore("/player_api.php").trim().removeSuffix("/")
            seriesUrlMatch != null ->
                seriesUrlMatch.groupValues[1].trim().removeSuffix("/")
            else -> ""
        }
        val urlUserFallback = Regex("[?&]username=([^&]+)", RegexOption.IGNORE_CASE)
            .find(seriesChannel.url)?.groupValues?.getOrNull(1)?.trim()
            ?: seriesUrlMatch?.groupValues?.getOrNull(2)?.trim()
            ?: ""
        val urlPassFallback = Regex("[?&]password=([^&]+)", RegexOption.IGNORE_CASE)
            .find(seriesChannel.url)?.groupValues?.getOrNull(1)?.trim()
            ?: seriesUrlMatch?.groupValues?.getOrNull(3)?.trim()
            ?: ""

        val baseUrl = seriesChannel.streamBaseUrl
            .ifBlank { urlBaseFallback }
            .ifBlank { matchingAccount?.let { normalizeServerUrl(it.serverUrl) }.orEmpty() }
        val rawUser = seriesChannel.streamUsername
            .ifBlank { urlUserFallback }
            .ifBlank { matchingAccount?.username?.trim().orEmpty() }
        val rawPass = seriesChannel.streamPassword
            .ifBlank { urlPassFallback }
            .ifBlank { matchingAccount?.password?.trim().orEmpty() }
        val user = safeEncodeParam(rawUser)
        val pass = safeEncodeParam(rawPass)

        if (baseUrl.isBlank() || user.isBlank() || pass.isBlank() || effectiveSid <= 0) {
            return@withContext emptyList()
        }

        val infoUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$effectiveSid"

        // [1] Primary DOM JSONObject Parser
        try {
            val raw = extractJsonPayload(fetchUrlTextFast(infoUrl).ifBlank { fetchUrlTextWithFallback(infoUrl) })
            if (raw.startsWith("{") || raw.startsWith("[")) {
                val result = mutableListOf<SeriesEpisode>()

                fun parseSingleEpisodeObj(ep: JSONObject, defaultSeason: Int, defaultIndex: Int) {
                    val epId = ep.optString("id", "")
                        .ifBlank { ep.optString("stream_id", "") }
                        .ifBlank { ep.optString("episode_id", "") }
                        .trim()
                    val directUrl = ep.optString("direct_source", "")
                        .ifBlank { ep.optString("url", "") }
                        .ifBlank { ep.optString("stream_url", "") }
                        .trim()
                    if (epId.isBlank() && !directUrl.startsWith("http", ignoreCase = true)) return

                    val sNum = ep.optInt("season", ep.optInt("season_num", defaultSeason)).coerceAtLeast(1)
                    val epNum = ep.optString("episode_num", "").trim().toIntOrNull()
                        ?: ep.optInt("episode_num", ep.optInt("num", defaultIndex)).coerceAtLeast(1)
                    val rawTitle = ep.optString("title", ep.optString("name", "")).trim()
                    val epTitle = if (rawTitle.isNotBlank() && !rawTitle.equals("false", ignoreCase = true) && !rawTitle.equals("null", ignoreCase = true)) {
                        rawTitle
                    } else {
                        "الموسم $sNum - الحلقة $epNum"
                    }
                    val ext = ep.optString("container_extension", ep.optString("ext", "mkv"))
                        .trim().lowercase(Locale.ROOT).removePrefix(".").ifBlank { "mkv" }
                    val dur = ep.optJSONObject("info")?.optString("duration", "")
                        ?: ep.optString("duration", "")
                    val playUrl = directUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                        ?: "$baseUrl/series/$user/$pass/$epId.$ext"

                    result.add(
                        SeriesEpisode(
                            id = epId.ifBlank { "${sNum}_$epNum" },
                            seasonNumber = sNum,
                            episodeNumber = epNum,
                            title = epTitle,
                            url = playUrl,
                            durationLabel = if (dur.equals("false", ignoreCase = true) || dur.equals("null", ignoreCase = true)) "" else dur,
                            containerExtension = ext
                        )
                    )
                }

                fun parseEpisodeContainer(container: Any?, seasonNumber: Int) {
                    when (container) {
                        is JSONArray -> {
                            for (i in 0 until container.length()) {
                                val item = container.opt(i)
                                when (item) {
                                    is JSONObject -> parseSingleEpisodeObj(item, seasonNumber, i + 1)
                                    is JSONArray -> parseEpisodeContainer(item, if (seasonNumber <= 1) i + 1 else seasonNumber)
                                }
                            }
                        }
                        is JSONObject -> {
                            val keyList = mutableListOf<String>()
                            val keysIter = container.keys()
                            while (keysIter.hasNext()) {
                                keyList.add(keysIter.next())
                            }
                            keyList.sortWith(compareBy({ it.trim().toIntOrNull() ?: Int.MAX_VALUE }, { it }))
                            var idx = 1
                            for (k in keyList) {
                                val child = container.opt(k)
                                val inferredSeason = k.trim().toIntOrNull() ?: seasonNumber
                                when (child) {
                                    is JSONArray -> {
                                        for (i in 0 until child.length()) {
                                            val epObj = child.optJSONObject(i)
                                            if (epObj != null) {
                                                parseSingleEpisodeObj(epObj, inferredSeason, i + 1)
                                            }
                                        }
                                    }
                                    is JSONObject -> {
                                        if (child.has("id") || child.has("stream_id") || child.has("episode_num") || child.has("container_extension") || child.has("direct_source")) {
                                            parseSingleEpisodeObj(child, seasonNumber, k.trim().toIntOrNull() ?: idx)
                                        } else {
                                            parseEpisodeContainer(child, inferredSeason)
                                        }
                                    }
                                }
                                idx++
                            }
                        }
                    }
                }

                if (raw.startsWith("[")) {
                    parseEpisodeContainer(JSONArray(raw), 1)
                } else {
                    val root = JSONObject(raw)
                    val dataRoot = root.optJSONObject("data") ?: root
                    val episodesNode = dataRoot.opt("episodes") ?: root.opt("episodes")
                    if (episodesNode != null && episodesNode != JSONObject.NULL) {
                        parseEpisodeContainer(episodesNode, 1)
                    }
                    if (result.isEmpty()) {
                        val seasonsArr = dataRoot.optJSONArray("seasons") ?: root.optJSONArray("seasons")
                        if (seasonsArr != null) {
                            for (sIdx in 0 until seasonsArr.length()) {
                                val sObj = seasonsArr.optJSONObject(sIdx) ?: continue
                                val sNum = sObj.optInt("season_number", sObj.optInt("season", sIdx + 1)).coerceAtLeast(1)
                                parseEpisodeContainer(sObj.opt("episodes"), sNum)
                            }
                        }
                    }
                }

                val finalSorted = result.distinctBy { "${it.seasonNumber}_${it.episodeNumber}_${it.id}" }
                    .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                if (finalSorted.isNotEmpty()) {
                    seriesEpisodesMemoryCache[cacheKey] = finalSorted
                    return@withContext finalSorted
                }
            }
        } catch (_: Throwable) {
        }

        // 2. Fallback to Streaming JsonReader parser
        val fastParsed = fetchAndParseSeriesInfoFast(infoUrl, baseUrl, user, pass)
        if (fastParsed.isNotEmpty()) {
            seriesEpisodesMemoryCache[cacheKey] = fastParsed
            return@withContext fastParsed
        }

        emptyList()
    }

    private fun readResponseBodyAutoDecompress(resp: okhttp3.Response): String {
        val body = resp.body ?: return ""
        return BufferedReader(InputStreamReader(wrapAutoDecompressStream(body.byteStream()), Charsets.UTF_8), 65536).use {
            it.readText().trim()
        }
    }

    private fun fetchUrlTextFast(url: String): String {
        val rememberedUa = IboPlayerProfileSpoofer.getRememberedHostProfile(url)?.userAgent
        val uaList = if (!rememberedUa.isNullOrBlank()) {
            (listOf(rememberedUa) + IPTV_USER_AGENTS).distinct()
        } else IPTV_USER_AGENTS
        for ((idx, ua) in uaList.take(4).withIndex()) {
            val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                builder = Request.Builder().url(url).header("Cache-Control", "no-cache, no-store, max-age=0"),
                targetUrl = url,
                userAgent = ua,
                useIdentityEncoding = true,
                includeRefererHeader = (idx % 2 == 0)
            ).build()
            val res = runCatching {
                fastProbeHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val txt = readResponseBodyAutoDecompress(resp)
                        if (!IboPlayerProfileSpoofer.isBlockedResponseBody(txt)) txt else ""
                    } else ""
                }
            }.getOrDefault("")
            if (res.isNotBlank()) return res
        }
        return ""
    }

    private fun fetchUrlTextWithFallback(url: String): String {
        val rememberedUa = IboPlayerProfileSpoofer.getRememberedHostProfile(url)?.userAgent
        val uaList = if (!rememberedUa.isNullOrBlank()) {
            (listOf(rememberedUa) + IPTV_USER_AGENTS).distinct()
        } else IPTV_USER_AGENTS
        for ((idx, ua) in uaList.take(5).withIndex()) {
            val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                builder = Request.Builder().url(url).header("Cache-Control", "no-cache"),
                targetUrl = url,
                userAgent = ua,
                useIdentityEncoding = true,
                includeRefererHeader = (idx % 2 == 0)
            ).build()
            try {
                val client = if (idx < 2) fastProbeHttpClient else xtreamHttpClient
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val txt = readResponseBodyAutoDecompress(resp)
                        if (txt.isNotBlank() && !IboPlayerProfileSpoofer.isBlockedResponseBody(txt)) return txt
                    }
                }
            } catch (_: Exception) {
            }
        }
        return ""
    }

    private fun fetchAndParseM3uStream(
        url: String,
        defaultGroup: String,
        playlistId: String,
        playlistName: String
    ): List<TvChannel> {
        for ((idx, ua) in IPTV_USER_AGENTS.take(5).withIndex()) {
            try {
                val req = IboPlayerProfileSpoofer.applyIboApiHeaders(
                    builder = Request.Builder().url(url),
                    targetUrl = url,
                    userAgent = ua,
                    useIdentityEncoding = (idx >= 3)
                ).build()
                xtreamHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    BufferedReader(InputStreamReader(wrapAutoDecompressStream(body.byteStream()), Charsets.UTF_8), 524288).use { br ->
                        val parsed = parseM3uBufferedReader(br, defaultGroup, playlistId, playlistName)
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    fun parseM3uPlaylist(
        m3u: String,
        defaultGroup: String,
        playlistId: String,
        playlistName: String
    ): List<TvChannel> {
        if (!m3u.contains("#EXTINF", ignoreCase = true)) return emptyList()
        return BufferedReader(StringReader(m3u), 131072).use { br ->
            parseM3uBufferedReader(br, defaultGroup, playlistId, playlistName)
        }
    }

    private val xtreamStreamPathRegex = Regex(
        "^(https?://[^/]+)/(?:(live|movie|series)/)?([^/]+)/([^/]+)/(\\d+)(?:\\.([a-zA-Z0-9]+))?$",
        RegexOption.IGNORE_CASE
    )

    private fun parseM3uBufferedReader(
        reader: BufferedReader,
        defaultGroup: String,
        playlistId: String,
        playlistName: String
    ): List<TvChannel> {
        val list = ArrayList<TvChannel>(16384)
        val groupPool = HashMap<String, String>(256)
        var currentName = ""
        var currentGroup = defaultGroup
        var currentLogo: String? = null
        var currentUa: String? = null
        var currentReferer: String? = null
        var currentOrigin: String? = null
        var currentCookie: String? = null
        var currentDrmScheme = ""
        var currentDrmKey = ""
        var counter = 1

        var rawLine: String?
        while (reader.readLine().also { rawLine = it } != null) {
            val line = rawLine!!.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                currentName = line.substringAfterLast(",", "قناة $counter").trim()
                val rawGrp = extractAttr(line, "group-title").ifBlank { defaultGroup }
                currentGroup = groupPool.getOrPut(rawGrp) { rawGrp }
                currentLogo = extractAttr(line, "tvg-logo").takeIf { it.startsWith("http") }
                currentUa = extractAttr(line, "user-agent").ifBlank { extractAttr(line, "http-user-agent") }.takeIf { it.isNotBlank() }
                currentReferer = extractAttr(line, "referer").ifBlank { extractAttr(line, "http-referrer") }.takeIf { it.isNotBlank() }
                currentOrigin = null
                currentCookie = null
                currentDrmScheme = ""
                currentDrmKey = ""
            } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                val grp = line.substringAfter(":").trim()
                if (grp.isNotBlank()) currentGroup = groupPool.getOrPut(grp) { grp }
            } else if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = line.substringAfter(":").trim()
                when {
                    opt.startsWith("http-user-agent=", ignoreCase = true) ->
                        currentUa = opt.substringAfter("=").trim().takeIf { it.isNotBlank() }
                    opt.startsWith("http-referrer=", ignoreCase = true) || opt.startsWith("http-referer=", ignoreCase = true) ->
                        currentReferer = opt.substringAfter("=").trim().takeIf { it.isNotBlank() }
                    opt.startsWith("http-origin=", ignoreCase = true) ->
                        currentOrigin = opt.substringAfter("=").trim().takeIf { it.isNotBlank() }
                    opt.startsWith("http-cookie=", ignoreCase = true) ->
                        currentCookie = opt.substringAfter("=").trim().takeIf { it.isNotBlank() }
                }
            } else if (line.startsWith("#KODIPROP:", ignoreCase = true)) {
                val prop = line.substringAfter(":").trim()
                when {
                    prop.startsWith("inputstream.adaptive.license_type=", ignoreCase = true) ->
                        currentDrmScheme = prop.substringAfter("=").trim().lowercase(Locale.ROOT)
                    prop.startsWith("inputstream.adaptive.license_key=", ignoreCase = true) ->
                        currentDrmKey = prop.substringAfter("=").trim()
                }
            } else if (!line.startsWith("#") &&
                (line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true) ||
                    line.startsWith("rtsp://", ignoreCase = true) ||
                    line.startsWith("rtmp://", ignoreCase = true) ||
                    line.startsWith("rtmps://", ignoreCase = true) ||
                    line.startsWith("udp://", ignoreCase = true) ||
                    line.startsWith("rtp://", ignoreCase = true) ||
                    line.startsWith("mms://", ignoreCase = true))
            ) {
                if (AdultContentFilter.isAdultOr18Plus(currentName, currentGroup)) {
                    continue
                }
                val cleanUrl = line.substringBefore("|").trim()
                if (line.contains("|")) {
                    val pipePart = line.substringAfter("|")
                    pipePart.split("&", "|").forEach { kv ->
                        val k = kv.substringBefore("=").trim().lowercase(Locale.ROOT)
                        val v = kv.substringAfter("=", "").trim()
                        if (v.isNotBlank()) {
                            when (k) {
                                "user-agent", "user_agent" -> currentUa = v
                                "referer", "referrer" -> currentReferer = v
                                "origin" -> currentOrigin = v
                                "cookie" -> currentCookie = v
                            }
                        }
                    }
                }
                val urlNoQuery = cleanUrl.substringBefore("?")
                val lowUrl = urlNoQuery.lowercase(Locale.ROOT)
                val lowGroup = currentGroup.lowercase(Locale.ROOT)
                val lowName = currentName.lowercase(Locale.ROOT)
                val isSeriesPattern = lowUrl.contains("/series/") ||
                    lowGroup.contains("مسلسل") || lowGroup.contains("series") || lowGroup.contains("série") ||
                    Regex("s\\d{1,2}\\s*[eex]\\s*\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(lowName) ||
                    lowName.contains("حلقة") || lowName.contains("موسم")
                val isMoviePattern = !isSeriesPattern && (
                    lowUrl.contains("/movie/") || lowUrl.contains("/vod/") ||
                        lowGroup.contains("افلام") || lowGroup.contains("أفلام") ||
                        lowGroup.contains("movie") || lowGroup.contains("film") || lowGroup.contains("vod") ||
                        lowUrl.endsWith(".mp4") || lowUrl.endsWith(".mkv") ||
                        lowUrl.endsWith(".avi") || lowUrl.endsWith(".mov") ||
                        lowUrl.endsWith(".webm") || lowUrl.endsWith(".flv")
                    )
                val type = when {
                    isSeriesPattern -> StreamContentType.SERIES
                    isMoviePattern -> StreamContentType.MOVIE
                    else -> StreamContentType.LIVE_TV
                }

                // Extract Xtream stream metadata if this M3U item follows Xtream URL structure
                val pathMatch = xtreamStreamPathRegex.find(urlNoQuery)
                val extractedBase = pathMatch?.groupValues?.getOrNull(1).orEmpty()
                val extractedUser = pathMatch?.groupValues?.getOrNull(3).orEmpty()
                val extractedPass = pathMatch?.groupValues?.getOrNull(4).orEmpty()
                val extractedId = pathMatch?.groupValues?.getOrNull(5)?.toIntOrNull() ?: 0
                val extractedExt = pathMatch?.groupValues?.getOrNull(6)?.lowercase(Locale.ROOT).orEmpty()

                val resolvedName = currentName.ifBlank { "قناة $counter" }
                list.add(
                    TvChannel(
                        id = 600_000 + counter,
                        number = counter,
                        name = resolvedName,
                        url = cleanUrl,
                        group = currentGroup,
                        logo = currentLogo,
                        userAgent = currentUa,
                        referer = currentReferer,
                        origin = currentOrigin,
                        cookie = currentCookie,
                        drmScheme = currentDrmScheme,
                        drmKey = currentDrmKey,
                        contentType = type,
                        playlistId = playlistId,
                        playlistName = playlistName,
                        streamId = extractedId,
                        streamBaseUrl = extractedBase,
                        streamUsername = extractedUser,
                        streamPassword = extractedPass,
                        containerExtension = extractedExt
                    )
                )
                counter++
            }
        }
        return list
    }

    private fun extractAttr(extInf: String, attr: String): String {
        val regex = Regex("$attr\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
        val m = regex.find(extInf) ?: return ""
        return (m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: m.groupValues.getOrNull(2).orEmpty()).trim()
    }
}
