package com.tvflux.pro

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// ============================================================================
// LIVE PRO v25.0 — Real OkHttp Offline Downloads Manager (Range Resume + DIRECTORY_MOVIES)
// ============================================================================

enum class DownloadItemStatus(val labelAr: String) {
    QUEUED("في الانتظار"),
    DOWNLOADING("جارٍ التحميل"),
    PAUSED("متوقف مؤقتاً"),
    COMPLETED("مكتمل"),
    FAILED("فشل التحميل")
}

data class OfflineDownloadItem(
    val id: String,
    val stableKey: String,
    val title: String,
    val subtitle: String,
    val posterUrl: String,
    val streamUrl: String,
    val localFilePath: String,
    val qualityLabel: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val progress: Float,
    val status: DownloadItemStatus,
    val isSeriesEpisode: Boolean = false,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

typealias DownloadTask = OfflineDownloadItem

data class StorageSpaceInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedByAppBytes: Long
) {
    val usedDeviceBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedDeviceBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

object OfflineDownloadsManager {
    private const val PREF_NAME = "livepro_real_downloads_v25"
    private const val KEY_ITEMS = "downloads_json_v25"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var appContext: Context? = null

    private val downloadHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val downloadsState: SnapshotStateList<DownloadTask> = mutableStateListOf()
    val downloadTasks: SnapshotStateList<DownloadTask> get() = downloadsState

    var wifiOnlyDownloads by mutableStateOf(false)
    var defaultDownloadQuality by mutableStateOf("FHD 1080p")

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            loadFromPrefs()
        }
    }

    fun resolveMoviesDirectory(context: Context): File {
        val extDir = runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        }.getOrNull()
        val dir = extDir ?: File(context.filesDir, "offline_movies")
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        return dir
    }

    private fun sanitizeFileName(raw: String, ext: String = "mp4"): String {
        val safe = raw.replace(Regex("[^a-zA-Z0-9_\\-\u0600-\u06FF.]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(64)
            .ifBlank { "vod_${System.currentTimeMillis()}" }
        val cleanExt = ext.lowercase(Locale.ROOT).trimStart('.').ifBlank { "mp4" }
        return if (safe.endsWith(".$cleanExt", ignoreCase = true)) safe else "$safe.$cleanExt"
    }

    private fun loadFromPrefs() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        wifiOnlyDownloads = prefs.getBoolean("wifi_only", false)
        defaultDownloadQuality = prefs.getString("default_quality", "FHD 1080p") ?: "FHD 1080p"
        val raw = prefs.getString(KEY_ITEMS, null)
        downloadsState.clear()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val filePath = o.optString("localFilePath")
                    val f = if (filePath.isNotBlank()) File(filePath) else null
                    val actualDiskBytes = if (f != null && f.exists()) f.length() else o.optLong("downloadedBytes", 0L)
                    val totalB = o.optLong("totalBytes", 0L)
                    val savedStatus = runCatching {
                        DownloadItemStatus.valueOf(o.optString("status", "PAUSED"))
                    }.getOrDefault(DownloadItemStatus.PAUSED)
                    val resolvedStatus = when {
                        savedStatus == DownloadItemStatus.COMPLETED && f != null && f.exists() && f.length() > 0L ->
                            DownloadItemStatus.COMPLETED
                        savedStatus == DownloadItemStatus.DOWNLOADING || savedStatus == DownloadItemStatus.QUEUED ->
                            DownloadItemStatus.PAUSED
                        else -> savedStatus
                    }
                    val calcProgress = when {
                        resolvedStatus == DownloadItemStatus.COMPLETED -> 1f
                        totalB > 0L -> (actualDiskBytes.toFloat() / totalB.toFloat()).coerceIn(0f, 0.99f)
                        else -> o.optDouble("progress", 0.0).toFloat()
                    }
                    downloadsState.add(
                        OfflineDownloadItem(
                            id = o.optString("id"),
                            stableKey = o.optString("stableKey"),
                            title = o.optString("title"),
                            subtitle = o.optString("subtitle"),
                            posterUrl = o.optString("posterUrl"),
                            streamUrl = o.optString("streamUrl"),
                            localFilePath = filePath,
                            qualityLabel = o.optString("qualityLabel", "FHD 1080p"),
                            totalBytes = totalB,
                            downloadedBytes = actualDiskBytes,
                            progress = calcProgress,
                            status = resolvedStatus,
                            isSeriesEpisode = o.optBoolean("isSeriesEpisode", false),
                            seasonNumber = o.optInt("seasonNumber", 1),
                            episodeNumber = o.optInt("episodeNumber", 1),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
    }

    private fun saveToPrefs() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        downloadsState.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("stableKey", item.stableKey)
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("posterUrl", item.posterUrl)
                    put("streamUrl", item.streamUrl)
                    put("localFilePath", item.localFilePath)
                    put("qualityLabel", item.qualityLabel)
                    put("totalBytes", item.totalBytes)
                    put("downloadedBytes", item.downloadedBytes)
                    put("progress", item.progress.toDouble())
                    put("status", item.status.name)
                    put("isSeriesEpisode", item.isSeriesEpisode)
                    put("seasonNumber", item.seasonNumber)
                    put("episodeNumber", item.episodeNumber)
                    put("createdAt", item.createdAt)
                }
            )
        }
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, arr.toString())
            .putBoolean("wifi_only", wifiOnlyDownloads)
            .putString("default_quality", defaultDownloadQuality)
            .apply()
    }

    fun findDownloadForChannelOrUrl(stableKey: String, streamUrl: String): OfflineDownloadItem? {
        val cleanUrl = streamUrl.trim()
        return downloadsState.firstOrNull {
            (stableKey.isNotBlank() && (it.stableKey == stableKey || it.id == "dl_${stableKey.hashCode()}")) ||
                (cleanUrl.isNotBlank() && it.streamUrl.trim() == cleanUrl)
        }
    }

    fun getDownloadedLocalUriIfAvailable(context: Context, stableKey: String, streamUrl: String): String? {
        init(context)
        if (streamUrl.startsWith("file://")) return streamUrl
        val item = findDownloadForChannelOrUrl(stableKey, streamUrl)
        if (item != null && item.localFilePath.isNotBlank()) {
            val file = File(item.localFilePath)
            if (file.exists() && file.length() >= 64 * 1024L &&
                (item.status == DownloadItemStatus.COMPLETED || (item.totalBytes > 0L && file.length() >= item.totalBytes))
            ) {
                return "file://${file.absolutePath}"
            }
        }
        return null
    }

    fun enqueueDownload(
        context: Context,
        channel: TvChannel,
        qualityLabel: String = defaultDownloadQuality,
        episode: SeriesEpisode? = null
    ) {
        init(context)
        val targetStreamUrl = (episode?.url?.takeIf { it.isNotBlank() } ?: channel.url).trim()
        if (targetStreamUrl.isBlank()) {
            Toast.makeText(context, "⚠️ رابط التحميل غير متوفر لهذا العنصر", Toast.LENGTH_SHORT).show()
            return
        }
        val itemKey = if (episode != null) {
            "${channel.stableKey}_S${episode.seasonNumber}_E${episode.episodeNumber}"
        } else {
            channel.stableKey
        }
        val id = "dl_${itemKey.hashCode()}"
        val existingIdx = downloadsState.indexOfFirst { it.id == id || it.streamUrl.trim() == targetStreamUrl }
        if (existingIdx >= 0) {
            val existing = downloadsState[existingIdx]
            if (existing.status == DownloadItemStatus.COMPLETED && File(existing.localFilePath).exists()) {
                Toast.makeText(context, "✅ هذا المحتوى محمّل مسبقاً وجاهز للتشغيل بدون إنترنت", Toast.LENGTH_SHORT).show()
                return
            }
            resumeDownload(existing.id)
            Toast.makeText(context, "▶️ تم استئناف التحميل: ${existing.title}", Toast.LENGTH_SHORT).show()
            return
        }

        val ext = (episode?.containerExtension?.takeIf { it.isNotBlank() }
            ?: channel.containerExtension.takeIf { it.isNotBlank() }
            ?: targetStreamUrl.substringBefore("?").substringAfterLast('.', "mp4"))
            .take(5)
        val dir = resolveMoviesDirectory(context)
        val epTag = if (episode != null) "S${episode.seasonNumber}E${episode.episodeNumber}" else "movie"
        val fileName = sanitizeFileName("${channel.name}_${epTag}_${id.takeLast(6)}", ext)
        val targetFile = File(dir, fileName)

        val displayTitle = if (episode != null) {
            "${channel.name} - م${episode.seasonNumber} ح${episode.episodeNumber}: ${episode.title.ifBlank { "حلقة ${episode.episodeNumber}" }}"
        } else {
            ProVisualSanitizer.sanitizeTitle(channel.name, "فيلم")
        }
        val displaySub = if (episode != null) {
            "الموسم ${episode.seasonNumber} • الحلقة ${episode.episodeNumber}"
        } else {
            channel.group.ifBlank { "أفلام" }
        }

        val newItem = OfflineDownloadItem(
            id = id,
            stableKey = itemKey,
            title = displayTitle,
            subtitle = displaySub,
            posterUrl = channel.logo.orEmpty(),
            streamUrl = targetStreamUrl,
            localFilePath = targetFile.absolutePath,
            qualityLabel = qualityLabel,
            totalBytes = 0L,
            downloadedBytes = if (targetFile.exists()) targetFile.length() else 0L,
            progress = 0f,
            status = DownloadItemStatus.DOWNLOADING,
            isSeriesEpisode = (episode != null || channel.contentType == StreamContentType.SERIES),
            seasonNumber = episode?.seasonNumber ?: 1,
            episodeNumber = episode?.episodeNumber ?: 1
        )
        downloadsState.add(0, newItem)
        saveToPrefs()
        Toast.makeText(context, "⬇️ بدأ التحميل الحقيقي: $displayTitle", Toast.LENGTH_SHORT).show()
        startOrResumeWorker(id)
    }

    private fun startOrResumeWorker(id: String) {
        activeJobs[id]?.cancel()
        val job = scope.launch {
            val idx0 = downloadsState.indexOfFirst { it.id == id }
            if (idx0 < 0) return@launch
            val item0 = downloadsState[idx0]
            val targetUrl = item0.streamUrl.substringBefore("|").trim()
            if (targetUrl.isBlank()) {
                updateItemStatus(id, DownloadItemStatus.FAILED)
                return@launch
            }

            val ctx = appContext ?: return@launch
            val destFile = if (item0.localFilePath.isNotBlank()) {
                File(item0.localFilePath)
            } else {
                File(resolveMoviesDirectory(ctx), sanitizeFileName(item0.title, "mp4"))
            }
            destFile.parentFile?.mkdirs()

            val existingBytes = if (destFile.exists()) destFile.length() else 0L
            updateItemOnMain(id) {
                it.copy(
                    localFilePath = destFile.absolutePath,
                    downloadedBytes = existingBytes,
                    status = DownloadItemStatus.DOWNLOADING
                )
            }

            runCatching {
                val reqBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "IBOPlayer/3.9 (Android; Linux; Android 13)")
                    .header("Accept", "*/*")
                if (existingBytes > 0L) {
                    reqBuilder.header("Range", "bytes=$existingBytes-")
                }

                downloadHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val code = response.code
                    if (!response.isSuccessful && code != 206) {
                        if (code == 416 && existingBytes > 0L) {
                            updateItemOnMain(id) {
                                it.copy(
                                    totalBytes = existingBytes,
                                    downloadedBytes = existingBytes,
                                    progress = 1f,
                                    status = DownloadItemStatus.COMPLETED
                                )
                            }
                            return@use
                        }
                        updateItemStatus(id, DownloadItemStatus.FAILED)
                        return@use
                    }

                    val body = response.body ?: run {
                        updateItemStatus(id, DownloadItemStatus.FAILED)
                        return@use
                    }

                    val isPartial = (code == 206)
                    val startOffset = if (isPartial) existingBytes else 0L
                    val contentLength = body.contentLength()
                    val totalBytes = when {
                        contentLength > 0L -> startOffset + contentLength
                        item0.totalBytes > 0L -> item0.totalBytes
                        else -> -1L
                    }

                    RandomAccessFile(destFile, "rw").use { raf ->
                        if (isPartial) {
                            raf.seek(startOffset)
                        } else {
                            raf.setLength(0L)
                            raf.seek(0L)
                        }

                        val buffer = ByteArray(64 * 1024)
                        var bytesReadTotal = startOffset
                        var lastUiEmitMs = 0L
                        val input = body.byteStream()

                        while (true) {
                            val currentItem = downloadsState.find { it.id == id }
                            if (currentItem == null || currentItem.status != DownloadItemStatus.DOWNLOADING) {
                                break
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            bytesReadTotal += read

                            val now = System.currentTimeMillis()
                            if (now - lastUiEmitMs >= 300L) {
                                lastUiEmitMs = now
                                val realProgress = if (totalBytes > 0L) {
                                    (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 0.99f)
                                } else {
                                    0.5f
                                }
                                updateItemOnMain(id, persist = false) {
                                    it.copy(
                                        downloadedBytes = bytesReadTotal,
                                        totalBytes = if (totalBytes > 0L) totalBytes else bytesReadTotal,
                                        progress = realProgress
                                    )
                                }
                            }
                        }

                        val finalCheck = downloadsState.find { it.id == id }
                        if (finalCheck != null && finalCheck.status == DownloadItemStatus.DOWNLOADING) {
                            val finalSize = destFile.length()
                            updateItemOnMain(id, persist = true) {
                                it.copy(
                                    downloadedBytes = finalSize,
                                    totalBytes = if (totalBytes > 0L) totalBytes else finalSize,
                                    progress = 1f,
                                    status = DownloadItemStatus.COMPLETED
                                )
                            }
                        } else {
                            saveToPrefs()
                        }
                    }
                }
            }.onFailure {
                val cur = downloadsState.find { it.id == id }
                if (cur != null && cur.status == DownloadItemStatus.DOWNLOADING) {
                    updateItemStatus(id, DownloadItemStatus.PAUSED)
                }
            }
        }
        activeJobs[id] = job
    }

    private suspend fun updateItemOnMain(
        id: String,
        persist: Boolean = true,
        transform: (OfflineDownloadItem) -> OfflineDownloadItem
    ) {
        withContext(Dispatchers.Main) {
            val idx = downloadsState.indexOfFirst { it.id == id }
            if (idx >= 0) {
                downloadsState[idx] = transform(downloadsState[idx])
                if (persist) saveToPrefs()
            }
        }
    }

    private suspend fun updateItemStatus(id: String, status: DownloadItemStatus) {
        updateItemOnMain(id, persist = true) { it.copy(status = status) }
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        val idx = downloadsState.indexOfFirst { it.id == id }
        if (idx >= 0) {
            downloadsState[idx] = downloadsState[idx].copy(status = DownloadItemStatus.PAUSED)
            saveToPrefs()
        }
    }

    fun resumeDownload(id: String) {
        val idx = downloadsState.indexOfFirst { it.id == id }
        if (idx >= 0) {
            downloadsState[idx] = downloadsState[idx].copy(status = DownloadItemStatus.DOWNLOADING)
            saveToPrefs()
            startOrResumeWorker(id)
        }
    }

    fun deleteDownload(id: String) {
        activeJobs[id]?.cancel()
        val item = downloadsState.find { it.id == id }
        if (item != null && item.localFilePath.isNotBlank()) {
            runCatching { File(item.localFilePath).delete() }
        }
        downloadsState.removeAll { it.id == id }
        saveToPrefs()
    }

    fun clearCompletedDownloads() {
        val completed = downloadsState.filter { it.status == DownloadItemStatus.COMPLETED }
        completed.forEach { item ->
            if (item.localFilePath.isNotBlank()) {
                runCatching { File(item.localFilePath).delete() }
            }
        }
        downloadsState.removeAll { it.status == DownloadItemStatus.COMPLETED }
        saveToPrefs()
    }

    fun getDeviceStorageSummary(): StorageSpaceInfo {
        return runCatching {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val total = stat.blockCountLong * blockSize
            val free = stat.availableBlocksLong * blockSize
            val appUsed = downloadsState.sumOf { it.downloadedBytes }
            StorageSpaceInfo(totalBytes = total, freeBytes = free, usedByAppBytes = appUsed)
        }.getOrDefault(
            StorageSpaceInfo(
                totalBytes = 64L * 1024L * 1024L * 1024L,
                freeBytes = 28L * 1024L * 1024L * 1024L,
                usedByAppBytes = downloadsState.sumOf { it.downloadedBytes }
            )
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        "SD 480p" to "حجم صغير للتحميل السريع 480p"
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
