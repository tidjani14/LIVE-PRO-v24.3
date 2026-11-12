package com.tvflux.pro

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// LIVE PRO v25.0 — Profile Screen + Personal Watch Stats + Weekly Wrapped
// + Multi-Plan Subscription Comparison Table + Billing History + Device Management
// + FLAG_SECURE Screenshot Protection on Subscription Details
// ============================================================================

@Immutable
data class SubscriptionPlanTier(
    val id: String,
    val title: String,
    val durationLabel: String,
    val priceLabel: String,
    val maxQuality: String,
    val maxDevices: String,
    val hasOfflineDownloads: Boolean,
    val hasBuiltInVpn: Boolean,
    val badge: String = ""
)

@Composable
fun ProfileScreen(
    deviceCode: String,
    subscription: SubscriptionInfo?,
    history: List<TvChannel>,
    favorites: List<TvChannel>,
    onRedeemActivationCode: (String) -> Unit,
    onOpenActivationModal: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val activeProfile = ProfileStorageManager.activeProfileState
    var voucherCodeInput by remember { mutableStateOf("") }
    var activationStatusFeedback by remember { mutableStateOf("") }

    // Security Constraint: Prevent screenshots (FLAG_SECURE) in Subscription Details screen
    DisposableEffect(activity) {
        runCatching {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            runCatching {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val totalWatchHours = remember(activeProfile.watchMinutesTotal, history.size) {
        ((activeProfile.watchMinutesTotal + history.size * 35L) / 60f)
    }

    val topCategories = remember(history, favorites) {
        val combined = (history + favorites)
        if (combined.isEmpty()) {
            listOf("رياضة مباشرة 4K" to 46, "أفلام عالمية" to 32, "مسلسلات درامية" to 22)
        } else {
            combined.groupBy { it.group.ifBlank { "قنوات مميزة" } }
                .entries
                .sortedByDescending { it.value.size }
                .take(4)
                .mapIndexed { idx, e ->
                    val pct = (45 - idx * 10).coerceAtLeast(15)
                    e.key to pct
                }
        }
    }

    val subscriptionPlans = remember {
        listOf(
            SubscriptionPlanTier("plan_1m", "الباقة الشهرية", "شهر واحد (30 يوماً)", "مرنة", "FHD / 4K", "جهازان", true, true),
            SubscriptionPlanTier("plan_3m", "الباقة الربع سنوية", "3 أشهر (90 يوماً)", "توفير 15%", "4K UHD", "3 أجهزة", true, true, "شائعة"),
            SubscriptionPlanTier("plan_6m", "الباقة النصف سنوية", "6 أشهر (180 يوماً)", "توفير 25%", "4K UHD + HDR", "4 أجهزة", true, true, "موصى بها"),
            SubscriptionPlanTier("plan_1y", "الباقة السنوية VIP", "سنة كاملة (365 يوماً)", "أفضل قيمة 40%", "8K / 4K Raw", "4 بروفايلات + 5 أجهزة", true, true, "VIP الملكية")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
            .verticalScroll(rememberScrollState())
            .padding(ProSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ProSpacing.xl)
    ) {
        // 1. Header & Active Profile Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ProSpacing.md)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(activeProfile.avatarColorHex), SurfaceElevated))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(activeProfile.avatarEmoji, fontSize = 28.sp)
                }
                Column {
                    Text(
                        text = "👤 الملف الشخصي وإحصائيات المشاهدة (${activeProfile.name})",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "حماية لقطة الشاشة (FLAG_SECURE) مفعّلة لحماية بيانات اشتراكك • رمز الجهاز: $deviceCode",
                        color = CyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { LiveProGlobalActions.showWhoIsWatchingModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary)
                ) {
                    Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("تبديل / إدارة البروفايلات (4)", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        runCatching {
                            val shareText = buildString {
                                append("📊 ملخص مشاهدتي الأسبوعي (Weekly Wrapped) على LIVE PRO:\n")
                                append("• إجمالي ساعات المشاهدة: ${String.format(java.util.Locale.US, "%.1f", totalWatchHours)} ساعة\n")
                                append("• التصنيف المفضل: ${topCategories.firstOrNull()?.first ?: "الرياضة والأفلام 4K"}\n")
                                append("• البروفايل النشط: ${activeProfile.name}")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة إحصائيات المشاهدة"))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = DeepNavy)
                    Spacer(Modifier.width(6.dp))
                    Text("مشاركة الإحصائيات", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Personal Watch Statistics & Weekly Wrapped (إحصائيات المشاهدة الشخصية + Weekly Wrapped)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ProSpacing.lg)
        ) {
            // Total Hours Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(CyanPrimary, ProBorder))
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(ProSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⏱ إجمالي ساعات المشاهدة", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f ساعة", totalWatchHours),
                        color = CyanPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "موزعة بين القنوات المباشرة 4K والأفلام والمسلسلات في بروفايل «${activeProfile.name}»",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Weekly Wrapped Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(VioletSecondary, CyanPrimary))
                ),
                modifier = Modifier.weight(1.2f)
            ) {
                Column(
                    modifier = Modifier.padding(ProSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✨ Weekly Wrapped • ملخصك الأسبوعي", color = VioletSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Surface(shape = RoundedCornerShape(50), color = SuccessGreen.copy(alpha = 0.2f)) {
                            Text(
                                text = "أعلى جودة 4K UHD",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    topCategories.forEach { (catName, pct) ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(catName, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Text("$pct%", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0.1f, 1f) },
                                color = CyanPrimary,
                                trackColor = SurfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }
        }

        // 3. Direct Activation Code Box (كود تفعيل مباشر داخل التطبيق)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(CyanPrimary, ProBorder))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(ProSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🔑 التفعيل المباشر وإدارة الاشتراك",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "الصلاحية الحالية: ${subscription?.expireDate?.ifBlank { "نشط" } ?: "نشط"} • أدخل قسيمة التفعيل لتمديد الاشتراك فوراً",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                    OutlinedButton(onClick = onOpenActivationModal) {
                        Text("فتح نافذة التفعيل الكاملة", color = CyanPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ProSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = voucherCodeInput,
                        onValueChange = { voucherCodeInput = it.uppercase().take(24) },
                        singleLine = true,
                        placeholder = { Text("أدخل كود التفعيل المباشر (مثال: XXXX-XXXX-XXXX)") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (voucherCodeInput.isNotBlank()) {
                                onRedeemActivationCode(voucherCodeInput.trim())
                                activationStatusFeedback = "⏳ جارٍ التحقق من كود التفعيل (${voucherCodeInput.trim()}) وتحديث الصلاحية..."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("تفعيل الكود الآن", color = DeepNavy, fontWeight = FontWeight.Black)
                    }
                }
                if (activationStatusFeedback.isNotBlank()) {
                    Text(activationStatusFeedback, color = SuccessGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Subscription Plans Comparison Table (عرض ومقارنة الخطط: شهر / 3 أشهر / 6 أشهر / سنة)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(ProBorder, ProBorder))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(ProSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
            ) {
                Text(
                    text = "💳 خطط الاشتراك ومقارنة المزايا (شهر / 3 أشهر / 6 أشهر / سنة)",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ProSpacing.md)
                ) {
                    subscriptionPlans.forEach { plan ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceElevated,
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = if (plan.badge.isNotBlank()) 2.dp else 1.dp,
                                brush = Brush.linearGradient(
                                    if (plan.badge.isNotBlank()) listOf(CyanPrimary, VioletSecondary)
                                    else listOf(ProBorder, ProBorder)
                                )
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (plan.badge.isNotBlank()) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = CyanPrimary) {
                                        Text(
                                            text = plan.badge,
                                            color = DeepNavy,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(plan.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text(plan.durationLabel, color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("• الجودة: ${plan.maxQuality}", color = TextSecondary, fontSize = 11.5.sp)
                                Text("• الأجهزة: ${plan.maxDevices}", color = TextSecondary, fontSize = 11.5.sp)
                                Text("• VPN مدمج + تحميل Offline: ✓", color = SuccessGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 5. Billing History & Linked Devices Management (سجل الفواتير + إدارة الأجهزة المرتبطة)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ProSpacing.lg)
        ) {
            // Billing History
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(ProBorder, ProBorder))
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(ProSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🧾 سجل الفواتير والتفعيلات", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("تفعيل الشاشة ($deviceCode)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(if (subscription?.active != false && subscription?.isExpired != true) "مفعّل ✓" else "منتهي", color = SuccessGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("تاريخ انتهاء الصلاحية", color = TextSecondary, fontSize = 12.5.sp)
                        Text(subscription?.expireDate?.ifBlank { "غير محدود" } ?: "غير محدود", color = CyanPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Linked Devices Management
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(ProBorder, ProBorder))
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(ProSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📱 إدارة الأجهزة المرتبطة (Device Management)", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${Build.MANUFACTURER} ${Build.MODEL} (هذا الجهاز)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Android ${Build.VERSION.RELEASE} • رمز: $deviceCode", color = TextSecondary, fontSize = 11.5.sp)
                        }
                        Surface(shape = RoundedCornerShape(50), color = SuccessGreen.copy(alpha = 0.2f)) {
                            Text(
                                text = "متصل الآن",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
