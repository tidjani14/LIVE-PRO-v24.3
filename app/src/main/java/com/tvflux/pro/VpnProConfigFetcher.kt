package com.tvflux.pro

import androidx.compose.foundation.border

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
 * سيرفر VPN PRO (خارجي، منفصل ومعزول تماماً 100% عن التطبيق):
 * - Endpoint: GET http://{VPN_PRO_SERVER_IP}:{VPN_PRO_SERVER_PORT}/api/get_config/{device_id}
 * - فك التشفير:
 *   • PBKDF2WithHmacSHA256(device_id, "vpn_fixed_salt", 100_000, 256 bits)
 *   • AES-256-GCM
 *   • Payload = Base64(nonce[12] + ciphertext + tag[16])
 */
object VpnProConfigFetcher {
    private const val SALT = "vpn_fixed_salt"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val CACHED_VPN_PRO_FILE = "player_vip_vpn_pro_v26.conf"

    var isVpnProSubscribed by mutableStateOf(false)
    var isRechecking by mutableStateOf(false)
    var lastStatusMessage by mutableStateOf("")
    var activeEndpoint by mutableStateOf("")

    @Volatile
    private var memoryCachedConfig: String? = null

    private val vpnProHttpClient: OkHttpClient by lazy {
        ChannelRepository.bootstrapHttpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * يفك تشفير إعدادات WireGuard المشفرة بـ AES-256-GCM ومفتاح مشتق عبر PBKDF2WithHmacSHA256.
     */
    fun decryptConfig(encryptedBase64: String, deviceId: String): String? {
        return try {
            val cleanB64 = encryptedBase64.trim()
            val cleanDevice = deviceId.trim()
            if (cleanB64.isEmpty() || cleanDevice.isEmpty()) return null

            val rawBytes = Base64.decode(cleanB64, Base64.DEFAULT)
            if (rawBytes.size <= GCM_NONCE_BYTES + 16) return null

            val nonce = rawBytes.copyOfRange(0, GCM_NONCE_BYTES)
            val cipherAndTag = rawBytes.copyOfRange(GCM_NONCE_BYTES, rawBytes.size)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keySpec = PBEKeySpec(
                cleanDevice.toCharArray(),
                SALT.toByteArray(Charsets.UTF_8),
                PBKDF2_ITERATIONS,
                KEY_LENGTH_BITS
            )
            val keyBytes = factory.generateSecret(keySpec).encoded
            val aesKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(cipherAndTag)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * طلب واحد صامت في الخلفية: GET /api/get_config/{device_id}
     * - status == "success" -> فك التشفير، حفظ الإعدادات، وتفعيل حالة المشترك (الحالة A)
     * - status == "error"   -> غير مشترك أو منتهي؛ إخفاء كامل (الحالة B)
     * - فشل شبكي / 5xx      -> إخفاء كامل مع إعادة محاولة صامتة كل 30 ثانية (الحالة C)
     */
    suspend fun fetchAndVerifyVpnProStatus(
        context: Context,
        deviceId: String = StorageManager.resolveHardwareDeviceId(context),
        forceNetwork: Boolean = true
    ): VpnProFetchOutcome = withContext(Dispatchers.IO) {
        val cleanId = deviceId.trim()
        if (cleanId.isEmpty()) {
            isVpnProSubscribed = false
            return@withContext VpnProFetchOutcome(VpnProState.NOT_SUBSCRIBED)
        }

        if (!forceNetwork && isVpnProSubscribed && !memoryCachedConfig.isNullOrBlank()) {
            return@withContext VpnProFetchOutcome(VpnProState.SUBSCRIBED, memoryCachedConfig, lastStatusMessage)
        }

        try {
            val url = ChannelRepository.getVpnProEndpointUrl(cleanId)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PLAYER-VIP/26.0")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache, no-store, max-age=0")
                .get()
                .build()

            vpnProHttpClient.newCall(request).execute().use { response ->
                if (response.code >= 500) {
                    return@withContext VpnProFetchOutcome(VpnProState.NETWORK_ERROR, null, "HTTP ${response.code}")
                }
                val body = response.body?.string()?.trim().orEmpty()
                if (!body.startsWith("{")) {
                    return@withContext VpnProFetchOutcome(VpnProState.NETWORK_ERROR, null, "Invalid response format")
                }

                val json = JSONObject(body)
                val status = json.optString("status", "").trim().lowercase()
                val msg = json.optString("message", "").trim()

                when (status) {
                    "success" -> {
                        val encData = if (json.isNull("encrypted_data")) "" else json.optString("encrypted_data", "").trim()
                        val decrypted = decryptConfig(encData, cleanId)
                        val sanitized = sanitizeVpnProConfig(decrypted.orEmpty())
                        if (!sanitized.isNullOrBlank()) {
                            memoryCachedConfig = sanitized
                            activeEndpoint = extractEndpoint(sanitized).orEmpty()
                            runCatching {
                                File(context.filesDir, CACHED_VPN_PRO_FILE).writeText(sanitized, Charsets.UTF_8)
                            }
                            isVpnProSubscribed = true
                            lastStatusMessage = msg
                            VpnProFetchOutcome(VpnProState.SUBSCRIBED, sanitized, msg)
                        } else {
                            clearCachedConfig(context)
                            isVpnProSubscribed = false
                            lastStatusMessage = msg
                            VpnProFetchOutcome(VpnProState.NOT_SUBSCRIBED, null, msg)
                        }
                    }
                    "error" -> {
                        clearCachedConfig(context)
                        isVpnProSubscribed = false
                        lastStatusMessage = msg
                        VpnProFetchOutcome(VpnProState.NOT_SUBSCRIBED, null, msg)
                    }
                    else -> {
                        VpnProFetchOutcome(VpnProState.NETWORK_ERROR, null, msg)
                    }
                }
            }
        } catch (t: Throwable) {
            VpnProFetchOutcome(VpnProState.NETWORK_ERROR, null, t.message.orEmpty())
        }
    }

    suspend fun getOrFetchConfig(context: Context, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh && isVpnProSubscribed) {
            val ram = memoryCachedConfig
            if (!ram.isNullOrBlank()) return@withContext ram
            val diskFile = File(context.filesDir, CACHED_VPN_PRO_FILE)
            if (diskFile.exists()) {
                val diskCfg = runCatching { diskFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
                val validDisk = sanitizeVpnProConfig(diskCfg)
                if (!validDisk.isNullOrBlank()) {
                    memoryCachedConfig = validDisk
                    activeEndpoint = extractEndpoint(validDisk).orEmpty()
                    return@withContext validDisk
                }
            }
        }
        val outcome = fetchAndVerifyVpnProStatus(
            context = context,
            deviceId = StorageManager.resolveHardwareDeviceId(context),
            forceNetwork = true
        )
        if (outcome.state == VpnProState.SUBSCRIBED) outcome.configText else null
    }

    fun clearCachedConfig(context: Context) {
        memoryCachedConfig = null
        activeEndpoint = ""
        runCatching {
            File(context.filesDir, CACHED_VPN_PRO_FILE).delete()
        }
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

    fun isVpnProConfig(configText: String): Boolean {
        if (configText.isBlank()) return false
        val ep = extractEndpoint(configText).orEmpty()
        return ep.startsWith(ChannelRepository.VPN_PRO_SERVER_IP) ||
            ep.endsWith(":51820") ||
            configText.contains("10.8.0.")
    }

    fun sanitizeVpnProConfig(raw: String): String? {
        if (raw.isBlank() || !raw.contains("[Interface]", ignoreCase = true) || !raw.contains("[Peer]", ignoreCase = true)) {
            return null
        }
        val allowedInterfaceKeys = setOf("privatekey", "address", "dns", "mtu", "listenport")
        val allowedPeerKeys = setOf("publickey", "presharedkey", "allowedips", "endpoint", "persistentkeepalive")

        var currentSection = ""
        var hasPrivateKey = false
        var hasPublicKey = false
        var hasEndpoint = false
        var hasAddress = false
        var hasDns = false
        var hasAllowedIps = false
        var hasKeepalive = false

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
                val keyOrig = trimmed.substringBefore("=").trim()
                val key = keyOrig.lowercase()
                val value = trimmed.substringAfter("=").trim()
                if (value.isEmpty()) continue

                if (currentSection == "interface" && key in allowedInterfaceKeys) {
                    when (key) {
                        "privatekey" -> {
                            hasPrivateKey = true
                            interfaceLines.add("PrivateKey = $value")
                        }
                        "address" -> {
                            hasAddress = true
                            interfaceLines.add("Address = $value")
                        }
                        "dns" -> {
                            hasDns = true
                            interfaceLines.add("DNS = $value")
                        }
                        "mtu" -> interfaceLines.add("MTU = $value")
                        "listenport" -> interfaceLines.add("ListenPort = $value")
                    }
                } else if (currentSection == "peer" && key in allowedPeerKeys) {
                    when (key) {
                        "publickey" -> {
                            hasPublicKey = true
                            peerLines.add("PublicKey = $value")
                        }
                        "presharedkey" -> peerLines.add("PresharedKey = $value")
                        "endpoint" -> {
                            hasEndpoint = true
                            peerLines.add("Endpoint = $value")
                        }
                        "allowedips" -> {
                            hasAllowedIps = true
                            peerLines.add("AllowedIPs = $value")
                        }
                        "persistentkeepalive" -> {
                            hasKeepalive = true
                            peerLines.add("PersistentKeepalive = $value")
                        }
                    }
                }
            }
        }

        if (!hasPrivateKey || !hasPublicKey) return null
        if (!hasAddress) interfaceLines.add("Address = 10.8.0.2/32")
        if (!hasDns) interfaceLines.add("DNS = 1.1.1.1")
        if (!hasEndpoint) peerLines.add("Endpoint = ${ChannelRepository.VPN_PRO_SERVER_IP}:51820")
        if (!hasAllowedIps) peerLines.add("AllowedIPs = 0.0.0.0/0")
        if (!hasKeepalive) peerLines.add("PersistentKeepalive = 25")

        return buildString {
            appendLine("[Interface]")
            interfaceLines.forEach { appendLine(it) }
            appendLine("[Peer]")
            peerLines.forEachIndexed { idx, l ->
                if (idx == peerLines.lastIndex) append(l) else appendLine(l)
            }
        }
    }
}
