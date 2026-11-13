package com.tvflux.pro

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.util.Locale

private val SharedAvatarBgBrush = Brush.linearGradient(
    listOf(Color(0xFF14141D), Color(0xFF0A0A0F))
)

/**
 * PLAYER VIP v27.0 — Stateless Coil Image Helper (Zero custom Bitmap memory retention):
 * Provides URL upgrading and Coil disk cache utilities for SettingsScreen compatibility.
 */
object FluxBitmapCache {
    private val tmdbLowResRegex = Regex("/t/p/(?:w92|w154|w185|w200|w300|w342|w400|w500)/", RegexOption.IGNORE_CASE)

    @Volatile
    var maxDiskCacheBytes: Long = ThumbnailCacheLimitOption.MB_128.maxBytes

    fun upgradeImageUrlToHighRes(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.contains("image.tmdb.org/t/p/", ignoreCase = true)) {
            tmdbLowResRegex.replace(trimmed, "/t/p/w780/")
        } else {
            trimmed
        }
    }

    fun getCacheSizeMb(context: Context): String {
        val coilCacheDir = File(context.cacheDir, "image_cache")
        val bytes = if (coilCacheDir.exists()) {
            coilCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun enforceDiskQuota(context: Context, limitBytes: Long = maxDiskCacheBytes) {
        runCatching {
            val coilCacheDir = File(context.cacheDir, "image_cache")
            if (!coilCacheDir.exists()) return
            val files = coilCacheDir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= limitBytes) return
            for (f in files.sortedBy { it.lastModified() }) {
                val len = f.length()
                if (f.delete()) {
                    total -= len
                    if (total <= (limitBytes * 85L) / 100L) break
                }
            }
        }
    }

    fun clear(context: Context) {
        runCatching {
            Coil.imageLoader(context).memoryCache?.clear()
            File(context.cacheDir, "image_cache").deleteRecursively()
            File(context.cacheDir, "flux_thumbs").deleteRecursively()
            File(context.cacheDir, "flux_thumbs_uhd_v24").deleteRecursively()
        }
    }
}

@Composable
fun BrandLogoBadge(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0A1324))
            .border(1.5.dp, BeinHighlightPurple.copy(alpha = 0.85f), shape),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(APP_LOGO_URL)
                .crossfade(true)
                .build(),
            contentDescription = APP_BRAND_NAME,
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.app_logo),
            placeholder = painterResource(id = R.drawable.app_logo),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ChannelAvatar(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    contentScale: ContentScale = ContentScale.Fit,
    innerPadding: Dp = 3.dp,
    maxDimensionPx: Int = 520,
    isPoster: Boolean = false
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(SharedAvatarBgBrush)
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}
