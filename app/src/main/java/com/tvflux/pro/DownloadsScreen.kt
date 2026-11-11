package com.tvflux.pro

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloadsScreen(
    sampleMovies: List<TvChannel> = emptyList(),
    allCatalogItems: List<TvChannel> = sampleMovies,
    onPlayOfflineItem: (TvChannel) -> Unit = {},
    onPlayDownloadedItem: (OfflineDownloadItem) -> Unit = { item ->
        val fileUri = if (item.localFilePath.isNotBlank()) "file://${item.localFilePath}" else item.streamUrl
        onPlayOfflineItem(
            TvChannel(
                id = item.id.hashCode(),
                number = 1,
                name = item.title,
                group = item.subtitle,
                logo = item.posterUrl,
                url = fileUri,
                contentType = if (item.isSeriesEpisode) StreamContentType.SERIES else StreamContentType.MOVIE
            )
        )
    },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OfflineDownloadsManager.init(context)
    }
    val downloads = OfflineDownloadsManager.downloadsState
    val storageInfo = remember(downloads.size, downloads.sumOf { it.downloadedBytes }) {
        OfflineDownloadsManager.getDeviceStorageSummary()
    }
    var selectedQuality by remember { mutableStateOf(OfflineDownloadsManager.defaultDownloadQuality) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(horizontal = ProSpacing.lg, vertical = ProSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "التحميلات والمشاهدة بدون إنترنت",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "تحميل حقيقي عبر OkHttp مع دعم الاستئناف التلقائي (Range)",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (downloads.any { it.status == DownloadItemStatus.COMPLETED }) {
                    Focusable translucentBtn@{
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, ProBorder),
                            modifier = Modifier.clickable { OfflineDownloadsManager.clearCompletedDownloads() }
                        ) {
                            Text(
                                text = "مسح المكتمل",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, ProBorder),
                    modifier = Modifier.clickable { onBack() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Text(text = "رجوع", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Storage Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, ProBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(ProSpacing.md),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مساحة التخزين (${OfflineDownloadsManager.formatBytes(storageInfo.usedByAppBytes)} مستخدمة للتحميلات)",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "المتاح: ${OfflineDownloadsManager.formatBytes(storageInfo.freeBytes)}",
                        color = CyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(storageInfo.usedFraction)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(CyanPrimary, VioletSecondary)))
                    )
                }
            }
        }

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, ProBorder),
                    modifier = Modifier.widthIn(max = 460.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(ProSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "قائمة التحميلات فارغة",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "افتح تفاصيل أي فيلم أو حلقات أي مسلسل واضغط زر التحميل (⬇️) لحفظه ومشاهدته بدون إنترنت.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(downloads, key = { _, item -> item.id }) { _, item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, ProBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceElevated)
                            ) {
                                ChannelAvatar(
                                    name = item.title,
                                    logoUrl = item.posterUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 10.dp,
                                    contentScale = ContentScale.Crop,
                                    isPoster = true
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.subtitle} • ${OfflineDownloadsManager.formatBytes(item.downloadedBytes)}" +
                                        if (item.totalBytes > 0L) " / ${OfflineDownloadsManager.formatBytes(item.totalBytes)}" else "",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(SurfaceElevated)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(item.progress.coerceIn(0.02f, 1f))
                                                .fillMaxHeight()
                                                .background(
                                                    if (item.status == DownloadItemStatus.COMPLETED) SuccessGreen else CyanPrimary
                                                )
                                        )
                                    }
                                    Text(
                                        text = "${(item.progress * 100).toInt()}% • ${item.status.labelAr}",
                                        color = if (item.status == DownloadItemStatus.COMPLETED) SuccessGreen else CyanPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (item.status == DownloadItemStatus.COMPLETED) {
                                    IconButton(
                                        onClick = { onPlayDownloadedItem(item) },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(CyanPrimary)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل", tint = DeepNavy)
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (item.status == DownloadItemStatus.DOWNLOADING) {
                                                OfflineDownloadsManager.pauseDownload(item.id)
                                            } else {
                                                OfflineDownloadsManager.resumeDownload(item.id)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceElevated)
                                    ) {
                                        Icon(
                                            imageVector = if (item.status == DownloadItemStatus.DOWNLOADING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "إيقاف/استئناف",
                                            tint = CyanPrimary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { OfflineDownloadsManager.deleteDownload(item.id) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(ErrorRed.copy(alpha = 0.16f))
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Focusable(content: @Composable () -> Unit) {
    content()
}


@Composable
fun DownloadQualityPickerDialog(
    channelTitle: String,
    onSelectQuality: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val qualities = listOf(
        "UHD 4K" to "جودة فائقة الوضوح 2160p",
        "FHD 1080p" to "جودة عالية الوضوح 1080p (موصى بها)",
        "HD 720p" to "جودة متوسطة وتوفير بيانات 720p",
        "SD 480p" to "حجم صغير للتحمل السريع 480p"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                text = "اختر جودة التحميل — $channelTitle",
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { (qCode, qDesc) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, ProBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectQuality(qCode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = qCode,
                                    color = CyanPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = qDesc,
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = CyanPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
