package com.tvflux.pro

import android.content.Context
import android.util.Base64
import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ============================================================================
// LIVE PRO v25.0 — Multi-Profile System (Up to 4 Profiles like Netflix)
// + Family/Kids Mode (+18 Filter) + PIN Lock + Custom Lists + Encrypted Vault
// ============================================================================

@Immutable
data class UserProfile(
    val id: String,
    val name: String,
    val avatarEmoji: String = "🎬",
    val avatarColorHex: Long = 0xFF22D3EE,
    val pinCode: String = "",
    val isKidsMode: Boolean = false,
    val isFamilyMode: Boolean = false,
    val watchlistKeys: Set<String> = emptySet(),
    val favoriteKeys: Set<String> = emptySet(),
    val watchMinutesTotal: Long = 140L,
    val userRatings: Map<String, Int> = emptyMap()
)

@Immutable
data class CustomUserList(
    val id: String,
    val title: String,
    val description: String = "",
    val channelKeys: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

object SecureAccountVault {
    private const val PREF_NAME = "livepro_encrypted_vault_v25"

    private fun deriveAesKey(context: Context): SecretKeySpec {
        val seed = "LIVEPRO_V25_AES_GCM_${context.packageName}_VAULT_KEY"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    fun putEncryptedString(context: Context, key: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveAesKey(context))
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            val b64 = Base64.encodeToString(combined, Base64.NO_WRAP)
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, b64)
                .apply()
        }
    }

    fun getDecryptedString(context: Context, key: String, defaultVal: String = ""): String {
        return runCatching {
            val b64 = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(key, null) ?: return defaultVal
            val combined = Base64.decode(b64, Base64.NO_WRAP)
            val ivSize = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val encrypted = combined.copyOfRange(1 + ivSize, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveAesKey(context), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault(defaultVal)
    }
}

object ProfileStorageManager {
    private const val PREFS_NAME = "livepro_multi_profiles_v25"
    private var appContext: Context? = null

    private val defaultProfiles = listOf(
        UserProfile(
            id = "prof_main",
            name = "الرئيسي",
            avatarEmoji = "👑",
            avatarColorHex = 0xFF22D3EE,
            isKidsMode = false,
            isFamilyMode = false,
            watchMinutesTotal = 860L
        ),
        UserProfile(
            id = "prof_family",
            name = "العائلة",
            avatarEmoji = "👨‍👩‍👧‍👦",
            avatarColorHex = 0xFFA855F7,
            isKidsMode = false,
            isFamilyMode = true,
            watchMinutesTotal = 420L
        ),
        UserProfile(
            id = "prof_kids",
            name = "الأطفال",
            avatarEmoji = "🧸",
            avatarColorHex = 0xFF10B981,
            pinCode = "",
            isKidsMode = true,
            isFamilyMode = true,
            watchMinutesTotal = 290L
        )
    )

    var profilesState by mutableStateOf(defaultProfiles)
        private set

    var activeProfileIdState by mutableStateOf("prof_main")
        private set

    val activeProfileState: UserProfile
        get() = profilesState.find { it.id == activeProfileIdState } ?: profilesState.first()

    var customListsState by mutableStateOf(
        listOf(
            CustomUserList(
                id = "list_friday",
                title = "أفلام السهرة والجمعة 🍿",
                description = "أفلام مختارة لمشاهدة نهاية الأسبوع"
            ),
            CustomUserList(
                id = "list_matches",
                title = "قنوات المباريات الكبرى ⚽",
                description = "قنوات الرياضة المفضلة بجودة 4K/FHD"
            )
        )
    )
        private set

    var recentSearchesState by mutableStateOf(
        listOf("beIN Sports 4K", "SSC", "أفلام 2025", "مسلسلات عربية", "وثائقي")
    )
        private set

    var cachedWatchlistChannels by mutableStateOf<List<TvChannel>>(emptyList())
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        runCatching {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeId = sp.getString("active_profile_id", "prof_main") ?: "prof_main"
            val profilesJson = sp.getString("profiles_json", null)
            if (!profilesJson.isNullOrBlank()) {
                val arr = JSONArray(profilesJson)
                val parsed = ArrayList<UserProfile>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val wlSet = mutableSetOf<String>()
                    val wlArr = o.optJSONArray("watchlistKeys")
                    if (wlArr != null) {
                        for (j in 0 until wlArr.length()) wlSet.add(wlArr.optString(j))
                    }
                    val favSet = mutableSetOf<String>()
                    val favArr = o.optJSONArray("favoriteKeys")
                    if (favArr != null) {
                        for (j in 0 until favArr.length()) favSet.add(favArr.optString(j))
                    }
                    val ratingsMap = mutableMapOf<String, Int>()
                    val rObj = o.optJSONObject("userRatings")
                    if (rObj != null) {
                        val keys = rObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            ratingsMap[k] = rObj.optInt(k, 5)
                        }
                    }
                    parsed.add(
                        UserProfile(
                            id = o.optString("id", "prof_$i"),
                            name = o.optString("name", "مستخدم ${i + 1}"),
                            avatarEmoji = o.optString("avatarEmoji", "🎬"),
                            avatarColorHex = o.optLong("avatarColorHex", 0xFF22D3EE),
                            pinCode = o.optString("pinCode", ""),
                            isKidsMode = o.optBoolean("isKidsMode", false),
                            isFamilyMode = o.optBoolean("isFamilyMode", false),
                            watchlistKeys = wlSet,
                            favoriteKeys = favSet,
                            watchMinutesTotal = o.optLong("watchMinutesTotal", 120L),
                            userRatings = ratingsMap
                        )
                    )
                }
                if (parsed.isNotEmpty()) {
                    profilesState = parsed.take(4)
                }
            }
            activeProfileIdState = if (profilesState.any { it.id == activeId }) activeId else profilesState.first().id

            // Custom Lists
            val listsJson = sp.getString("custom_lists_json", null)
            if (!listsJson.isNullOrBlank()) {
                val lArr = JSONArray(listsJson)
                val parsedLists = ArrayList<CustomUserList>()
                for (i in 0 until lArr.length()) {
                    val o = lArr.getJSONObject(i)
                    val keysList = ArrayList<String>()
                    val kArr = o.optJSONArray("channelKeys")
                    if (kArr != null) {
                        for (j in 0 until kArr.length()) keysList.add(kArr.optString(j))
                    }
                    parsedLists.add(
                        CustomUserList(
                            id = o.optString("id", "list_$i"),
                            title = o.optString("title", "قائمة مخصصة"),
                            description = o.optString("description", ""),
                            channelKeys = keysList,
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (parsedLists.isNotEmpty()) {
                    customListsState = parsedLists
                }
            }

            // Recent Searches (max 10)
            val searchesJson = sp.getString("recent_searches_json", null)
            if (!searchesJson.isNullOrBlank()) {
                val sArr = JSONArray(searchesJson)
                val sList = ArrayList<String>()
                for (i in 0 until sArr.length()) {
                    val q = sArr.optString(i).trim()
                    if (q.isNotBlank()) sList.add(q)
                }
                recentSearchesState = sList.take(10)
            }
        }
    }

    private fun saveToPrefs() {
        val ctx = appContext ?: return
        runCatching {
            val arr = JSONArray()
            for (p in profilesState.take(4)) {
                val o = JSONObject()
                o.put("id", p.id)
                o.put("name", p.name)
                o.put("avatarEmoji", p.avatarEmoji)
                o.put("avatarColorHex", p.avatarColorHex)
                o.put("pinCode", p.pinCode)
                o.put("isKidsMode", p.isKidsMode)
                o.put("isFamilyMode", p.isFamilyMode)
                o.put("watchMinutesTotal", p.watchMinutesTotal)
                val wlArr = JSONArray()
                p.watchlistKeys.forEach { wlArr.put(it) }
                o.put("watchlistKeys", wlArr)
                val favArr = JSONArray()
                p.favoriteKeys.forEach { favArr.put(it) }
                o.put("favoriteKeys", favArr)
                val rObj = JSONObject()
                p.userRatings.forEach { (k, v) -> rObj.put(k, v) }
                o.put("userRatings", rObj)
                arr.put(o)
            }

            val lArr = JSONArray()
            for (l in customListsState) {
                val o = JSONObject()
                o.put("id", l.id)
                o.put("title", l.title)
                o.put("description", l.description)
                o.put("createdAt", l.createdAt)
                val kArr = JSONArray()
                l.channelKeys.forEach { kArr.put(it) }
                o.put("channelKeys", kArr)
                lArr.put(o)
            }

            val sArr = JSONArray()
            recentSearchesState.take(10).forEach { sArr.put(it) }

            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("active_profile_id", activeProfileIdState)
                .putString("profiles_json", arr.toString())
                .putString("custom_lists_json", lArr.toString())
                .putString("recent_searches_json", sArr.toString())
                .apply()
        }
    }

    fun selectProfile(profileId: String) {
        if (profilesState.any { it.id == profileId }) {
            activeProfileIdState = profileId
            saveToPrefs()
        }
    }

    fun addOrUpdateProfile(profile: UserProfile) {
        val existingIdx = profilesState.indexOfFirst { it.id == profile.id }
        profilesState = if (existingIdx >= 0) {
            profilesState.toMutableList().also { it[existingIdx] = profile }
        } else if (profilesState.size < 4) {
            profilesState + profile
        } else {
            profilesState
        }
        saveToPrefs()
    }

    fun deleteProfile(profileId: String) {
        if (profilesState.size <= 1) return
        profilesState = profilesState.filterNot { it.id == profileId }
        if (activeProfileIdState == profileId) {
            activeProfileIdState = profilesState.first().id
        }
        saveToPrefs()
    }

    fun toggleWatchlist(channel: TvChannel) {
        val current = activeProfileState
        val key = channel.stableKey
        val updatedKeys = if (key in current.watchlistKeys) {
            current.watchlistKeys - key
        } else {
            current.watchlistKeys + key
        }
        if (key !in current.watchlistKeys && cachedWatchlistChannels.none { it.stableKey == key }) {
            cachedWatchlistChannels = listOf(channel) + cachedWatchlistChannels
        }
        addOrUpdateProfile(current.copy(watchlistKeys = updatedKeys))
    }

    fun rateContent(channelKey: String, stars: Int) {
        val current = activeProfileState
        val updatedRatings = current.userRatings.toMutableMap().apply {
            put(channelKey, stars.coerceIn(1, 5))
        }
        addOrUpdateProfile(current.copy(userRatings = updatedRatings))
    }

    fun addWatchMinutes(minutes: Long) {
        if (minutes <= 0L) return
        val current = activeProfileState
        addOrUpdateProfile(current.copy(watchMinutesTotal = current.watchMinutesTotal + minutes))
    }

    fun recordRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        recentSearchesState = (listOf(clean) + recentSearchesState.filterNot { it.equals(clean, ignoreCase = true) }).take(10)
        saveToPrefs()
    }

    fun clearRecentSearches() {
        recentSearchesState = emptyList()
        saveToPrefs()
    }

    fun createCustomList(title: String, description: String = "") {
        val clean = title.trim()
        if (clean.isBlank()) return
        val newList = CustomUserList(
            id = "custom_${System.currentTimeMillis()}",
            title = clean,
            description = description.trim().ifBlank { "قائمة مخصصة للمشاهدة" }
        )
        customListsState = customListsState + newList
        saveToPrefs()
    }

    fun deleteCustomList(listId: String) {
        customListsState = customListsState.filterNot { it.id == listId }
        saveToPrefs()
    }

    fun toggleChannelInCustomList(listId: String, channelKey: String) {
        customListsState = customListsState.map { list ->
            if (list.id != listId) list
            else {
                val updated = if (channelKey in list.channelKeys) {
                    list.channelKeys - channelKey
                } else {
                    list.channelKeys + channelKey
                }
                list.copy(channelKeys = updated)
            }
        }
        saveToPrefs()
    }
}

@Composable
fun WhoIsWatchingModal(
    onDismiss: () -> Unit,
    onOpenFullProfileScreen: () -> Unit = {
        onDismiss()
        LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.PROFILE
    }
) {
    val profiles = ProfileStorageManager.profilesState
    val activeProfile = ProfileStorageManager.activeProfileState
    var pendingPinProfile by remember { mutableStateOf<UserProfile?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf("🎬") }
    var newIsKids by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }

    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstCardFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy.copy(alpha = 0.96f))
                .padding(ProSpacing.xxl),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ProSpacing.xl),
                modifier = Modifier.widthIn(max = 820.dp)
            ) {
                Text(
                    text = "من يشاهد الآن؟",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "اختر البروفايل الخاص بك للحصول على مفضلتك وسجل المشاهدة والاقتراحات الخاصة بك (حتى 4 بروفايلات)",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(ProSpacing.xl),
                    contentPadding = PaddingValues(horizontal = ProSpacing.lg, vertical = ProSpacing.md)
                ) {
                    itemsIndexed(profiles, key = { _, p -> p.id }) { idx, prof ->
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (focused) 1.10f else 1f,
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            label = "profCardScale"
                        )
                        val isSelected = prof.id == activeProfile.id
                        val cardModifier = if (idx == 0) Modifier.focusRequester(firstCardFocus) else Modifier

                        val onSelect = {
                            if (prof.pinCode.isNotBlank()) {
                                pendingPinProfile = prof
                                pinInput = ""
                                pinError = false
                            } else {
                                ProfileStorageManager.selectProfile(prof.id)
                                onDismiss()
                            }
                        }

                        Column(
                            modifier = cardModifier
                                .width(150.dp)
                                .scale(scale)
                                .onKeyEvent { ev ->
                                    if (isDpadSelectKeyEvent(ev)) {
                                        onSelect()
                                        true
                                    } else false
                                }
                                .focusable(interactionSource = interaction)
                                .clickable(interactionSource = interaction, indication = null) { onSelect() },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(116.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(prof.avatarColorHex).copy(alpha = 0.85f),
                                                SurfaceElevated
                                            )
                                        )
                                    )
                                    .border(
                                        width = if (focused || isSelected) 3.dp else 1.5.dp,
                                        color = if (focused) Color.White else if (isSelected) CyanPrimary else ProBorder,
                                        shape = RoundedCornerShape(22.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = prof.avatarEmoji, fontSize = 46.sp)
                                if (prof.pinCode.isNotBlank()) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "محمي برمز PIN",
                                        tint = WarningAmber,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(18.dp)
                                    )
                                }
                                if (prof.isKidsMode) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SuccessGreen,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 6.dp)
                                    ) {
                                        Text(
                                            text = "KIDS آمن",
                                            color = DeepNavy,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = prof.name,
                                color = if (focused || isSelected) TextPrimary else TextSecondary,
                                fontSize = 16.sp,
                                fontWeight = if (focused || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Add Profile Card (if < 4)
                    if (profiles.size < 4) {
                        item(key = "add_new_profile_card") {
                            val addInt = remember { MutableInteractionSource() }
                            val addFocused by addInt.collectIsFocusedAsState()
                            val addScale by animateFloatAsState(
                                targetValue = if (addFocused) 1.10f else 1f,
                                animationSpec = tween(200),
                                label = "addProfScale"
                            )
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .scale(addScale)
                                    .onKeyEvent { ev ->
                                        if (isDpadSelectKeyEvent(ev)) {
                                            showCreateDialog = true
                                            true
                                        } else false
                                    }
                                    .focusable(interactionSource = addInt)
                                    .clickable(interactionSource = addInt, indication = null) { showCreateDialog = true },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(116.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(SurfaceElevated)
                                        .border(
                                            width = if (addFocused) 3.dp else 1.5.dp,
                                            color = if (addFocused) CyanPrimary else ProBorder,
                                            shape = RoundedCornerShape(22.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "إضافة بروفايل",
                                        tint = if (addFocused) CyanPrimary else TextSecondary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                                Text(
                                    text = "إضافة بروفايل",
                                    color = if (addFocused) CyanPrimary else TextSecondary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // PIN Verification Box if needed
                if (pendingPinProfile != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceDark,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(listOf(CyanPrimary, VioletSecondary))
                        ),
                        modifier = Modifier.widthIn(max = 380.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(ProSpacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ProSpacing.sm)
                        ) {
                            Text(
                                text = "أدخل رمز PIN لبروفايل «${pendingPinProfile?.name}»",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    pinInput = it.take(6)
                                    pinError = false
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                label = { Text("رمز PIN") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (pinError) {
                                Text(
                                    text = "رمز PIN غير صحيح، حاول مرة أخرى.",
                                    color = ErrorRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(ProSpacing.md)) {
                                Button(
                                    onClick = {
                                        val target = pendingPinProfile
                                        if (target != null && pinInput == target.pinCode) {
                                            ProfileStorageManager.selectProfile(target.id)
                                            pendingPinProfile = null
                                            onDismiss()
                                        } else {
                                            pinError = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                                ) {
                                    Text("دخول", color = DeepNavy, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { pendingPinProfile = null }) {
                                    Text("إلغاء", color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Create New Profile Dialog Box
                if (showCreateDialog) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceDark,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(listOf(CyanPrimary, VioletSecondary))
                        ),
                        modifier = Modifier.widthIn(max = 440.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(ProSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(ProSpacing.sm)
                        ) {
                            Text(
                                text = "إنشاء بروفايل جديد (${profiles.size}/4)",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it.take(18) },
                                singleLine = true,
                                label = { Text("اسم البروفايل") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الأفاتار:", color = TextSecondary, fontSize = 13.sp)
                                listOf("🎬", "⚽", "🍿", "🧸", "🚀", "🌟").forEach { em ->
                                    Surface(
                                        shape = CircleShape,
                                        color = if (newEmoji == em) CyanPrimary.copy(0.3f) else SurfaceElevated,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable { newEmoji = em }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(em, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("وضع الأطفال والعائلة (فلترة +18)", color = TextPrimary, fontSize = 13.sp)
                                Switch(checked = newIsKids, onCheckedChange = { newIsKids = it })
                            }
                            OutlinedTextField(
                                value = newPin,
                                onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(4) },
                                singleLine = true,
                                label = { Text("رمز PIN اختياري (4 أرقام)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(ProSpacing.md)) {
                                Button(
                                    onClick = {
                                        if (newName.isNotBlank()) {
                                            val created = UserProfile(
                                                id = "prof_${System.currentTimeMillis()}",
                                                name = newName.trim(),
                                                avatarEmoji = newEmoji,
                                                avatarColorHex = if (newIsKids) 0xFF10B981 else 0xFF38BDF8,
                                                pinCode = newPin,
                                                isKidsMode = newIsKids,
                                                isFamilyMode = newIsKids
                                            )
                                            ProfileStorageManager.addOrUpdateProfile(created)
                                            ProfileStorageManager.selectProfile(created.id)
                                            showCreateDialog = false
                                            newName = ""
                                            newPin = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                                ) {
                                    Text("حفظ البروفايل", color = DeepNavy, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { showCreateDialog = false }) {
                                    Text("إلغاء", color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Bottom Actions: Manage Profiles & Stats / Close
                Row(horizontalArrangement = Arrangement.spacedBy(ProSpacing.md)) {
                    Button(
                        onClick = onOpenFullProfileScreen,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated)
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إحصائيات المشاهدة والاشتراك", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text("إغلاق والعودة", color = TextSecondary)
                    }
                }
            }
        }
    }
}
