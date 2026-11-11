package com.tvflux.pro

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// LIVE PRO v25.0 — My List (Watchlist) + Favorites + Custom Lists + Cloud Sync UX
// ============================================================================

@Composable
fun MyListScreen(
    deviceCode: String,
    favorites: List<TvChannel>,
    allCatalogItems: List<TvChannel>,
    onPlayChannel: (TvChannel) -> Unit,
    onOpenDetails: (TvChannel) -> Unit,
    onToggleFavorite: (TvChannel) -> Unit,
    onTriggerCloudSync: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeProfile = ProfileStorageManager.activeProfileState
    val customLists = ProfileStorageManager.customListsState
    var selectedSection by remember { mutableStateOf(0) } // 0 = My List, 1 = Favorites, 2 = Custom Lists
    var selectedCustomListId by remember(customLists) {
        mutableStateOf(customLists.firstOrNull()?.id.orEmpty())
    }
    var showCreateCustomListModal by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var newListDesc by remember { mutableStateOf("") }
    var cloudSyncToast by remember { mutableStateOf("") }

    val watchlistItems = remember(activeProfile.watchlistKeys, allCatalogItems, favorites) {
        val pool = (ProfileStorageManager.cachedWatchlistChannels + favorites + allCatalogItems).distinctBy { it.stableKey }
        pool.filter { it.stableKey in activeProfile.watchlistKeys }
    }

    val activeCustomList = customLists.find { it.id == selectedCustomListId } ?: customLists.firstOrNull()
    val customListItems = remember(activeCustomList, allCatalogItems, favorites) {
        if (activeCustomList == null) emptyList()
        else {
            val pool = (favorites + allCatalogItems).distinctBy { it.stableKey }
            pool.filter { it.stableKey in activeCustomList.channelKeys }.ifEmpty {
                favorites.take(8)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(ProSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ProSpacing.lg)
    ) {
        // Header + Cloud Sync via Screen Code
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
                Column {
                    Text(
                        text = "⭐ قائمتي والمفضلة المتقدمة (${activeProfile.name})",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "قائمة المشاهدة لاحقاً (Watchlist) • المفضلة الدائمة • قوائم مخصصة («أفلام الجمعة»)",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showCreateCustomListModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("قائمة مخصصة جديدة", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        onTriggerCloudSync()
                        cloudSyncToast = "☁️ تمت المزامنة السحابية عبر رمز الشاشة ($deviceCode) بنجاح!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = DeepNavy)
                    Spacer(Modifier.width(6.dp))
                    Text("مزامنة سحابية ($deviceCode)", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (cloudSyncToast.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SuccessGreen.copy(alpha = 0.18f),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(SuccessGreen, SuccessGreen))
                )
            ) {
                Text(
                    text = cloudSyncToast,
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Section Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val tabs = listOf(
                0 to "📌 قائمة المشاهدة لاحقاً (${watchlistItems.size})",
                1 to "❤️ المفضلة الدائمة (${favorites.size})",
                2 to "📂 القوائم المخصصة (${customLists.size})"
            )
            tabs.forEach { (idx, title) ->
                val selected = selectedSection == idx
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) CyanPrimary else SurfaceDark,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(CyanPrimary, ProBorder))
                    ),
                    modifier = Modifier.clickable { selectedSection = idx }
                ) {
                    Text(
                        text = title,
                        color = if (selected) DeepNavy else TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }

        if (selectedSection == 2) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(customLists) { _, cList ->
                    val isSel = cList.id == activeCustomList?.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSel) VioletSecondary.copy(alpha = 0.28f) else SurfaceDark,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = if (isSel) 2.dp else 1.dp,
                            brush = Brush.linearGradient(
                                if (isSel) listOf(VioletSecondary, CyanPrimary) else listOf(ProBorder, ProBorder)
                            )
                        ),
                        modifier = Modifier.clickable { selectedCustomListId = cList.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column {
                                Text(cList.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(cList.description, color = TextSecondary, fontSize = 11.sp)
                            }
                            if (customLists.size > 1) {
                                IconButton(
                                    onClick = { ProfileStorageManager.deleteCustomList(cList.id) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "حذف القائمة", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        val displayedItems = when (selectedSection) {
            0 -> watchlistItems.ifEmpty { favorites }
            1 -> favorites
            else -> customListItems
        }

        if (displayedItems.isEmpty()) {
            ProEmptyState(
                title = "القائمة فارغة حالياً",
                subtitle = "اضغط على زر (+) الموجود على أي بطاقة فيلم أو مسلسل أو قناة لإضافته فوراً إلى قائمتك.",
                actionText = "استعراض المحتوى",
                onActionClick = onBack,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if ((LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION || LocalConfiguration.current.screenWidthDp >= 720) 180.dp else 110.dp),
                verticalArrangement = Arrangement.spacedBy(ProSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ProSpacing.md),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(displayedItems, key = { idx, ch -> "${idx}_${ch.stableKey}" }) { _, ch ->
                    val poster = ch.toPosterItem()
                    Box(contentAlignment = Alignment.Center) {
                        ProPosterCard(
                            title = poster.title,
                            imageUrl = poster.imageUrl,
                            badge = poster.badge,
                            subtitle = poster.subtitle,
                            isLive = poster.isLive,
                            rating = poster.rating,
                            inWatchlist = ch.stableKey in activeProfile.watchlistKeys,
                            onQuickAddWatchlist = { ProfileStorageManager.toggleWatchlist(ch) },
                            onClick = {
                                if (ch.contentType == StreamContentType.LIVE_TV) onPlayChannel(ch)
                                else onOpenDetails(ch)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateCustomListModal) {
        AlertDialog(
            onDismissRequest = { showCreateCustomListModal = false },
            containerColor = SurfaceDark,
            title = {
                Text("إنشاء قائمة مخصصة جديدة", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it.take(28) },
                        singleLine = true,
                        label = { Text("اسم القائمة (مثلاً: أفلام الجمعة 🍿)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newListDesc,
                        onValueChange = { newListDesc = it.take(50) },
                        singleLine = true,
                        label = { Text("وصف مختصر (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newListName.isNotBlank()) {
                            ProfileStorageManager.createCustomList(newListName, newListDesc)
                            newListName = ""
                            newListDesc = ""
                            selectedSection = 2
                            showCreateCustomListModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text("إنشاء القائمة", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCustomListModal = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }
}
