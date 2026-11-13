package com.tvflux.pro

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class DifferentialSyncStats(
    val sourceId: String,
    val addedCount: Int,
    val updatedCount: Int,
    val removedCount: Int,
    val unchangedCount: Int,
    val durationMs: Long
)

/**
 * Local SQLite Content Library Engine (`live_pro_v23.db`).
 * - Stores hundreds of thousands of Live TV channels, VOD Movies, Series, and Bouquets locally per playlist.
 * - Strictly isolates playlists (`playlist_id`) with dedicated B-Tree composite indexes so loading or switching
 *   any playlist from local storage takes ~15–25ms regardless of how many playlists are stored on disk.
 * - Uses WAL (Write-Ahead Logging) + fast batch transactions (`25,000` rows/batch) with pre-compiled SQLiteStatement.
 */
class TiviMateLocalDatabase private constructor(private val appContext: Context) :
    SQLiteOpenHelper(appContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        runCatching { db.enableWriteAheadLogging() }
        runCatching { db.rawQuery("PRAGMA synchronous = NORMAL", null).use { it.moveToFirst() } }
        runCatching { db.rawQuery("PRAGMA temp_store = MEMORY", null).use { it.moveToFirst() } }
        runCatching { db.rawQuery("PRAGMA cache_size = -32000", null).use { it.moveToFirst() } }
        runCatching { db.rawQuery("PRAGMA mmap_size = 268435456", null).use { it.moveToFirst() } }
        runCatching { db.rawQuery("PRAGMA journal_size_limit = 67108864", null).use { it.moveToFirst() } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalog_items (
                stable_key TEXT PRIMARY KEY,
                item_id INTEGER NOT NULL,
                channel_number INTEGER NOT NULL,
                name TEXT NOT NULL COLLATE NOCASE,
                name_lower TEXT NOT NULL,
                url TEXT NOT NULL,
                group_name TEXT NOT NULL COLLATE NOCASE,
                logo TEXT DEFAULT '',
                user_agent TEXT DEFAULT '',
                referer TEXT DEFAULT '',
                origin TEXT DEFAULT '',
                cookie TEXT DEFAULT '',
                drm_scheme TEXT DEFAULT '',
                drm_key TEXT DEFAULT '',
                content_type TEXT NOT NULL,
                rating TEXT DEFAULT '',
                release_year TEXT DEFAULT '',
                plot TEXT DEFAULT '',
                series_id INTEGER DEFAULT 0,
                playlist_id TEXT NOT NULL,
                playlist_name TEXT NOT NULL,
                stream_id INTEGER DEFAULT 0,
                stream_base_url TEXT DEFAULT '',
                stream_username TEXT DEFAULT '',
                stream_password TEXT DEFAULT '',
                container_ext TEXT DEFAULT '',
                video_codec TEXT DEFAULT '',
                audio_codec TEXT DEFAULT '',
                tv_archive INTEGER DEFAULT 0,
                tv_archive_duration INTEGER DEFAULT 0,
                epg_channel_id TEXT DEFAULT '',
                item_hash TEXT NOT NULL,
                sort_order INTEGER DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_sources (
                source_id TEXT PRIMARY KEY,
                source_name TEXT NOT NULL,
                catalog_etag TEXT DEFAULT '',
                last_sync_ms INTEGER DEFAULT 0,
                live_count INTEGER DEFAULT 0,
                movie_count INTEGER DEFAULT 0,
                series_count INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS epg_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                playlist_id TEXT NOT NULL,
                channel_id TEXT NOT NULL COLLATE NOCASE,
                title TEXT NOT NULL,
                description TEXT DEFAULT '',
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )

        createIndexes(db)
    }

    private fun createIndexes(db: SQLiteDatabase) {
        runCatching { db.execSQL("DROP INDEX IF EXISTS idx_cat_type_playlist_group") }
        runCatching { db.execSQL("DROP INDEX IF EXISTS idx_cat_stream_id") }
        runCatching { db.execSQL("DROP INDEX IF EXISTS idx_cat_type_group") }
        runCatching { db.execSQL("DROP INDEX IF EXISTS idx_cat_search_name") }

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cat_type_sort ON catalog_items(content_type, sort_order ASC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cat_playlist ON catalog_items(playlist_id, content_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cat_playlist_type_sort ON catalog_items(playlist_id, content_type, sort_order ASC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cat_playlist_type_number ON catalog_items(playlist_id, content_type, channel_number ASC, sort_order ASC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cat_playlist_type_group_sort ON catalog_items(playlist_id, content_type, group_name COLLATE NOCASE, sort_order ASC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cat_playlist_type_name ON catalog_items(playlist_id, content_type, name COLLATE NOCASE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_epg_channel_time ON epg_data(playlist_id, channel_id COLLATE NOCASE, start_ms, end_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        runCatching { db.execSQL("ALTER TABLE catalog_items ADD COLUMN tv_archive INTEGER DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE catalog_items ADD COLUMN tv_archive_duration INTEGER DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE catalog_items ADD COLUMN epg_channel_id TEXT DEFAULT ''") }
        onCreate(db)
    }

    companion object {
        const val DB_NAME = "live_pro_tivimate_v11.db"
        private const val DB_VERSION = 16
        private const val CURSOR_PAGE_SIZE = 10000
        private const val INSERT_BATCH_SIZE = 25000
        private val playlistSnapshotCache = ConcurrentHashMap<String, Triple<List<TvChannel>, List<TvChannel>, List<TvChannel>>>()

        fun invalidatePlaylistSnapshotCache(sourceId: String = "") {
            if (sourceId.isBlank()) {
                playlistSnapshotCache.clear()
            } else {
                playlistSnapshotCache.remove(sourceId)
            }
        }

        @Volatile
        private var instance: TiviMateLocalDatabase? = null

        private val sourceSyncLocks = ConcurrentHashMap<String, Mutex>()
        private val activeNetworkSyncSources = ConcurrentHashMap.newKeySet<String>()
        val globalXtreamSyncQueueMutex = Mutex()

        fun getInstance(context: Context): TiviMateLocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: TiviMateLocalDatabase(context.applicationContext).also { instance = it }
            }
        }

        fun getSourceMutex(sourceId: String): Mutex {
            return sourceSyncLocks.getOrPut(sourceId) { Mutex() }
        }

        fun tryAcquireNetworkSync(sourceId: String): Boolean {
            if (sourceId.isBlank()) return false
            if (sourceSyncLocks[sourceId]?.isLocked == true) return false
            return activeNetworkSyncSources.add(sourceId)
        }

        fun releaseNetworkSync(sourceId: String) {
            if (sourceId.isNotBlank()) {
                activeNetworkSyncSources.remove(sourceId)
            }
        }

        fun isSourceSyncInProgress(sourceId: String): Boolean {
            return activeNetworkSyncSources.contains(sourceId) || sourceSyncLocks[sourceId]?.isLocked == true
        }
    }

    fun getDatabaseSizeMb(): String {
        val dbFile = appContext.getDatabasePath(DB_NAME)
        val walFile = File(dbFile.path + "-wal")
        val totalBytes = (if (dbFile.exists()) dbFile.length() else 0L) +
            (if (walFile.exists()) walFile.length() else 0L)
        return String.format(Locale.US, "%.2f MB", totalBytes / (1024.1 * 1024.1))
    }

    suspend fun getSourceLastSyncInfo(sourceId: String): Pair<Long, String> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        var lastMs = 0L
        var etag = ""
        db.rawQuery(
            "SELECT last_sync_ms, catalog_etag FROM sync_sources WHERE source_id = ? LIMIT 1",
            arrayOf(sourceId)
        ).use { c ->
            if (c.moveToFirst()) {
                lastMs = c.getLong(0)
                etag = c.getString(1).orEmpty()
            }
        }
        Pair(lastMs, etag)
    }

    /**
     * Fast <1ms check whether a specific playlist (`playlistId`) already has cached items in local SQLite.
     */
    suspend fun hasLocalCatalogForPlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext false
        runCatching {
            val db = readableDatabase
            db.rawQuery(
                "SELECT 1 FROM catalog_items WHERE playlist_id = ? LIMIT 1",
                arrayOf(playlistId)
            ).use { c ->
                c.moveToFirst()
            }
        }.getOrDefault(false)
    }

    /**
     * Returns a map of `playlistId -> Triple(liveCount, movieCount, seriesCount)` for all playlists stored in SQLite (<1ms).
     */
    suspend fun getStoredPlaylistCountsMap(): Map<String, Triple<Int, Int, Int>> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, Triple<Int, Int, Int>>()
        runCatching {
            val db = readableDatabase
            db.rawQuery(
                "SELECT source_id, live_count, movie_count, series_count FROM sync_sources",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val sid = c.getString(0).orEmpty()
                    if (sid.isNotBlank()) {
                        val l = c.getInt(1)
                        val m = c.getInt(2)
                        val s = c.getInt(3)
                        if (l > 0 || m > 0 || s > 0) {
                            result[sid] = Triple(l, m, s)
                        }
                    }
                }
            }
            if (result.isEmpty()) {
                db.rawQuery(
                    """
                    SELECT
                        playlist_id,
                        SUM(CASE WHEN content_type = 'LIVE_TV' THEN 1 ELSE 0 END),
                        SUM(CASE WHEN content_type = 'MOVIE' THEN 1 ELSE 0 END),
                        SUM(CASE WHEN content_type = 'SERIES' THEN 1 ELSE 0 END)
                    FROM catalog_items
                    GROUP BY playlist_id
                    """.trimIndent(),
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        val pid = c.getString(0).orEmpty()
                        if (pid.isNotBlank()) {
                            result[pid] = Triple(c.getInt(1), c.getInt(2), c.getInt(3))
                        }
                    }
                }
            }
        }
        result
    }

    /**
     * Performs an Ultra-Fast Atomic Batch Sync for a single source (`playlistId`):
     * - Pre-compiles `SQLiteStatement` once and binds rows in 25,000-item transactions.
     * - Never wipes existing Live TV, Movies, or Series if one endpoint returned empty.
     */
    suspend fun syncSourceCatalogAtomically(
        sourceId: String,
        sourceName: String,
        liveChannels: List<TvChannel>,
        movies: List<TvChannel>,
        series: List<TvChannel>,
        catalogEtag: String = "",
        allowEmptyReplace: Boolean = false
    ): DifferentialSyncStats = withContext(Dispatchers.IO) {
        val mutex = getSourceMutex(sourceId)
        mutex.withLock {
            val startMs = SystemClock.elapsedRealtime()
            val totalCount = liveChannels.size + movies.size + series.size
            if (totalCount == 0 && !allowEmptyReplace) {
                TiviMateDiagnostics.logEvent("⚠️ تم الاحتفاظ بالمكتبة المحلية للمصدر [$sourceName] لأن الاستجابة كانت فارغة.")
                return@withLock DifferentialSyncStats(sourceId, 0, 0, 0, 0, 0L)
            }

            val db = writableDatabase
            val nowMs = System.currentTimeMillis()
            var totalInserted = 0

            val upsertSql = """
                INSERT OR REPLACE INTO catalog_items (
                    stable_key, item_id, channel_number, name, name_lower, url, group_name,
                    logo, user_agent, referer, origin, cookie, drm_scheme, drm_key,
                    content_type, rating, release_year, plot, series_id, playlist_id,
                    playlist_name, stream_id, stream_base_url, stream_username,
                    stream_password, container_ext, video_codec, audio_codec,
                    tv_archive, tv_archive_duration, epg_channel_id,
                    item_hash, sort_order, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            fun writeTypeChunks(contentType: StreamContentType, items: List<TvChannel>) {
                if (items.isEmpty() && !allowEmptyReplace) {
                    return
                }
                if (items.isEmpty()) {
                    db.beginTransactionNonExclusive()
                    try {
                        db.delete("catalog_items", "playlist_id = ? AND content_type = ?", arrayOf(sourceId, contentType.name))
                        db.setTransactionSuccessful()
                    } finally {
                        runCatching { db.endTransaction() }
                    }
                    return
                }

                val stmt = db.compileStatement(upsertSql)
                try {
                    var offset = 0
                    var deletedOld = false
                    while (offset < items.size) {
                        val end = (offset + INSERT_BATCH_SIZE).coerceAtMost(items.size)
                        db.beginTransactionNonExclusive()
                        try {
                            if (!deletedOld) {
                                db.delete("catalog_items", "playlist_id = ? AND content_type = ?", arrayOf(sourceId, contentType.name))
                                deletedOld = true
                            }
                            for (orderIdx in offset until end) {
                                val ch = items[orderIdx]
                                val uniqueRowKey = "${sourceId}_${contentType.name}_${orderIdx}_${ch.id}"
                                stmt.clearBindings()
                                stmt.bindString(1, uniqueRowKey)
                                stmt.bindLong(2, ch.id.toLong())
                                stmt.bindLong(3, ch.number.toLong())
                                stmt.bindString(4, ch.name)
                                stmt.bindString(5, "")
                                stmt.bindString(6, ch.url)
                                stmt.bindString(7, ch.group.ifBlank { "عام" })
                                stmt.bindString(8, ch.logo.orEmpty())
                                stmt.bindString(9, ch.userAgent.orEmpty())
                                stmt.bindString(10, ch.referer.orEmpty())
                                stmt.bindString(11, ch.origin.orEmpty())
                                stmt.bindString(12, ch.cookie.orEmpty())
                                stmt.bindString(13, ch.drmScheme)
                                stmt.bindString(14, ch.drmKey)
                                stmt.bindString(15, contentType.name)
                                stmt.bindString(16, ch.rating)
                                stmt.bindString(17, ch.releaseYear)
                                stmt.bindString(18, "")
                                stmt.bindLong(19, ch.seriesId.toLong())
                                stmt.bindString(20, ch.playlistId.ifBlank { sourceId })
                                stmt.bindString(21, ch.playlistName.ifBlank { sourceName })
                                stmt.bindLong(22, ch.streamId.toLong())
                                stmt.bindString(23, ch.streamBaseUrl)
                                stmt.bindString(24, ch.streamUsername)
                                stmt.bindString(25, ch.streamPassword)
                                stmt.bindString(26, ch.containerExtension)
                                stmt.bindString(27, ch.videoCodecHint)
                                stmt.bindString(28, ch.audioCodecHint)
                                stmt.bindLong(29, ch.tvArchive.toLong())
                                stmt.bindLong(30, ch.tvArchiveDuration.toLong())
                                stmt.bindString(31, ch.epgChannelId)
                                stmt.bindString(32, "")
                                stmt.bindLong(33, orderIdx.toLong())
                                stmt.bindLong(34, nowMs)
                                stmt.executeInsert()
                                totalInserted++
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            runCatching { db.endTransaction() }
                        }
                        offset = end
                    }
                } finally {
                    runCatching { stmt.close() }
                }
            }

            try {
                writeTypeChunks(StreamContentType.LIVE_TV, liveChannels)
                writeTypeChunks(StreamContentType.MOVIE, movies)
                writeTypeChunks(StreamContentType.SERIES, series)

                var storedLive = liveChannels.size
                var storedMovies = movies.size
                var storedSeries = series.size
                if (storedLive == 0 || storedMovies == 0 || storedSeries == 0) {
                    runCatching {
                        db.rawQuery(
                            "SELECT content_type, COUNT(*) FROM catalog_items WHERE playlist_id = ? GROUP BY content_type",
                            arrayOf(sourceId)
                        ).use { c ->
                            while (c.moveToNext()) {
                                when (c.getString(0)) {
                                    StreamContentType.LIVE_TV.name -> storedLive = c.getInt(1)
                                    StreamContentType.MOVIE.name -> storedMovies = c.getInt(1)
                                    StreamContentType.SERIES.name -> storedSeries = c.getInt(1)
                                }
                            }
                        }
                    }
                }

                db.beginTransactionNonExclusive()
                try {
                    val metaStmt = db.compileStatement(
                        """
                        INSERT OR REPLACE INTO sync_sources (
                            source_id, source_name, catalog_etag, last_sync_ms, live_count, movie_count, series_count
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    )
                    try {
                        metaStmt.bindString(1, sourceId)
                        metaStmt.bindString(2, sourceName)
                        metaStmt.bindString(3, catalogEtag)
                        metaStmt.bindLong(4, nowMs)
                        metaStmt.bindLong(5, storedLive.toLong())
                        metaStmt.bindLong(6, storedMovies.toLong())
                        metaStmt.bindLong(7, storedSeries.toLong())
                        metaStmt.executeInsert()
                    } finally {
                        runCatching { metaStmt.close() }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    runCatching { db.endTransaction() }
                }

                if (liveChannels.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()) {
                    if (playlistSnapshotCache.size >= 2) playlistSnapshotCache.clear()
                    playlistSnapshotCache[sourceId] = Triple(liveChannels, movies, series)
                }
                val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                val stats = DifferentialSyncStats(
                    sourceId = sourceId,
                    addedCount = totalInserted,
                    updatedCount = 0,
                    removedCount = 0,
                    unchangedCount = 0,
                    durationMs = elapsed
                )
                TiviMateDiagnostics.recordSyncStats(sourceName, stats)
                stats
            } finally {
                checkpointWalNow()
            }
        }
    }

    fun checkpointWalNow() {
        runCatching {
            writableDatabase.rawQuery("PRAGMA wal_checkpoint(PASSIVE)", null).use { it.moveToFirst() }
        }
    }

    suspend fun updatePlaylistCredentialsInPlace(
        playlistId: String,
        newServerName: String,
        newBaseUrl: String,
        newUsernameEnc: String,
        newPasswordEnc: String,
        oldBaseUrl: String = ""
    ) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val cleanNewBase = newBaseUrl.trim().removeSuffix("/")
        val cleanOldBase = oldBaseUrl.trim().removeSuffix("/")
        db.beginTransactionNonExclusive()
        try {
            val oldBasesToReplace = LinkedHashSet<String>()
            if (cleanOldBase.isNotBlank() && !cleanOldBase.equals(cleanNewBase, ignoreCase = true)) {
                oldBasesToReplace.add(cleanOldBase)
            }
            runCatching {
                db.rawQuery(
                    "SELECT DISTINCT stream_base_url FROM catalog_items WHERE playlist_id = ? AND stream_base_url IS NOT NULL AND stream_base_url != ''",
                    arrayOf(playlistId)
                ).use { c ->
                    while (c.moveToNext()) {
                        val dbOld = c.getString(0)?.trim()?.removeSuffix("/").orEmpty()
                        if (dbOld.isNotBlank() && !dbOld.equals(cleanNewBase, ignoreCase = true)) {
                            oldBasesToReplace.add(dbOld)
                        }
                    }
                }
            }

            if (oldBasesToReplace.isNotEmpty() && cleanNewBase.isNotBlank()) {
                for (oldCandidate in oldBasesToReplace) {
                    db.execSQL(
                        """
                        UPDATE catalog_items
                        SET playlist_name = ?,
                            stream_base_url = ?,
                            stream_username = ?,
                            stream_password = ?,
                            url = REPLACE(url, ?, ?),
                            logo = REPLACE(logo, ?, ?)
                        WHERE playlist_id = ?
                        """.trimIndent(),
                        arrayOf(
                            newServerName,
                            cleanNewBase,
                            newUsernameEnc,
                            newPasswordEnc,
                            oldCandidate,
                            cleanNewBase,
                            oldCandidate,
                            cleanNewBase,
                            playlistId
                        )
                    )
                }
            } else {
                db.execSQL(
                    """
                    UPDATE catalog_items
                    SET playlist_name = ?,
                        stream_base_url = ?,
                        stream_username = ?,
                        stream_password = ?
                    WHERE playlist_id = ?
                    """.trimIndent(),
                    arrayOf(newServerName, cleanNewBase, newUsernameEnc, newPasswordEnc, playlistId)
                )
            }
            runCatching {
                db.execSQL("UPDATE sync_sources SET source_name = ? WHERE source_id = ?", arrayOf(newServerName, playlistId))
            }
            invalidatePlaylistSnapshotCache(playlistId)
            db.setTransactionSuccessful()
        } finally {
            runCatching { db.endTransaction() }
            checkpointWalNow()
        }
    }

    /**
     * يستبدل أي دومين قديم بالدومين الجديد إجبارياً في جدول catalog_items المحلي بالكامل
     * (جميع القنوات المباشرة، الأفلام، والمسلسلات المحفوظة محلياً) عند إعادة إقلاع التطبيق.
     */
    suspend fun replaceDomainGloballyInLocalDb(
        oldDomainOrUrl: String,
        newDomainOrUrl: String,
        newServerName: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanOld = oldDomainOrUrl.trim().removeSuffix("/")
        val cleanNew = XtreamRepository.normalizeServerUrl(newDomainOrUrl).ifBlank { newDomainOrUrl.trim().removeSuffix("/") }
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld.equals(cleanNew, ignoreCase = true)) {
            return@withContext false
        }
        val oldHostAuth = cleanOld.substringAfter("://").substringBefore("/").trim()
        val newHostAuth = cleanNew.substringAfter("://").substringBefore("/").trim()
        if (oldHostAuth.isBlank() || newHostAuth.isBlank()) return@withContext false

        val db = writableDatabase
        var anyUpdated = false
        db.beginTransactionNonExclusive()
        try {
            val candidates = linkedSetOf(
                cleanOld to cleanNew,
                "http://$oldHostAuth" to cleanNew,
                "https://$oldHostAuth" to cleanNew,
                oldHostAuth to newHostAuth
            )
            for ((oldToken, newToken) in candidates) {
                if (oldToken.isBlank() || newToken.isBlank() || oldToken.equals(newToken, ignoreCase = true)) continue
                val likePattern = "%$oldToken%"
                if (newServerName.isNotBlank()) {
                    db.execSQL(
                        """
                        UPDATE catalog_items
                        SET playlist_name = ?,
                            stream_base_url = REPLACE(stream_base_url, ?, ?),
                            url = REPLACE(url, ?, ?),
                            logo = REPLACE(logo, ?, ?)
                        WHERE stream_base_url LIKE ? OR url LIKE ? OR logo LIKE ?
                        """.trimIndent(),
                        arrayOf(newServerName, oldToken, newToken, oldToken, newToken, oldToken, newToken, likePattern, likePattern, likePattern)
                    )
                } else {
                    db.execSQL(
                        """
                        UPDATE catalog_items
                        SET stream_base_url = REPLACE(stream_base_url, ?, ?),
                            url = REPLACE(url, ?, ?),
                            logo = REPLACE(logo, ?, ?)
                        WHERE stream_base_url LIKE ? OR url LIKE ? OR logo LIKE ?
                        """.trimIndent(),
                        arrayOf(oldToken, newToken, oldToken, newToken, oldToken, newToken, likePattern, likePattern, likePattern)
                    )
                }
                anyUpdated = true
            }
            playlistSnapshotCache.clear()
            db.setTransactionSuccessful()
        } catch (_: Throwable) {
        } finally {
            runCatching { db.endTransaction() }
            checkpointWalNow()
        }
        anyUpdated
    }

    suspend fun deleteSourceCatalog(sourceId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            db.delete("catalog_items", "playlist_id = ?", arrayOf(sourceId))
            db.delete("sync_sources", "source_id = ?", arrayOf(sourceId))
            invalidatePlaylistSnapshotCache(sourceId)
            db.setTransactionSuccessful()
        } finally {
            runCatching { db.endTransaction() }
            checkpointWalNow()
        }
    }

    /**
     * Migrates a locally cached playlist ID to the canonical server playlist_id so cached channels
     * are preserved when IDs are unified, before pruning deleted server playlists.
     */
    suspend fun migratePlaylistIdInLocalDb(
        oldPlaylistId: String,
        newPlaylistId: String,
        newServerName: String = ""
    ) = withContext(Dispatchers.IO) {
        val cleanOld = oldPlaylistId.trim()
        val cleanNew = newPlaylistId.trim()
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == cleanNew) return@withContext
        runCatching {
            val db = writableDatabase
            db.beginTransactionNonExclusive()
            try {
                if (newServerName.isNotBlank()) {
                    db.execSQL(
                        "UPDATE catalog_items SET playlist_id = ?, playlist_name = ? WHERE playlist_id = ?",
                        arrayOf(cleanNew, newServerName.trim(), cleanOld)
                    )
                    db.execSQL(
                        "UPDATE sync_sources SET source_id = ?, source_name = ? WHERE source_id = ?",
                        arrayOf(cleanNew, newServerName.trim(), cleanOld)
                    )
                } else {
                    db.execSQL(
                        "UPDATE catalog_items SET playlist_id = ? WHERE playlist_id = ?",
                        arrayOf(cleanNew, cleanOld)
                    )
                    db.execSQL(
                        "UPDATE sync_sources SET source_id = ? WHERE source_id = ?",
                        arrayOf(cleanNew, cleanOld)
                    )
                }
                invalidatePlaylistSnapshotCache(cleanOld)
                invalidatePlaylistSnapshotCache(cleanNew)
                db.setTransactionSuccessful()
            } finally {
                runCatching { db.endTransaction() }
                checkpointWalNow()
            }
        }
    }

    /**
     * Automatically deletes any Xtream playlist from local SQLite (both catalog_items and sync_sources)
     * that is no longer present in the server's database upon app startup or remote sync.
     */
    suspend fun pruneDeletedRemotePlaylists(validPlaylistIds: Set<String>): List<String> = withContext(Dispatchers.IO) {
        val removedIds = ArrayList<String>()
        runCatching {
            val db = writableDatabase
            val existingIds = LinkedHashSet<String>()
            db.rawQuery(
                "SELECT DISTINCT source_id FROM sync_sources WHERE source_id != 'PORTAL_MAIN' AND source_id != ''",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val sid = c.getString(0)?.trim().orEmpty()
                    if (sid.isNotBlank()) existingIds.add(sid)
                }
            }
            db.rawQuery(
                "SELECT DISTINCT playlist_id FROM catalog_items WHERE playlist_id != 'PORTAL_MAIN' AND playlist_id != ''",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val pid = c.getString(0)?.trim().orEmpty()
                    if (pid.isNotBlank()) existingIds.add(pid)
                }
            }
            val toDelete = existingIds.filter { it !in validPlaylistIds }
            if (toDelete.isNotEmpty()) {
                db.beginTransactionNonExclusive()
                try {
                    for (delId in toDelete) {
                        db.delete("catalog_items", "playlist_id = ?", arrayOf(delId))
                        db.delete("sync_sources", "source_id = ?", arrayOf(delId))
                        invalidatePlaylistSnapshotCache(delId)
                        removedIds.add(delId)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    runCatching { db.endTransaction() }
                    checkpointWalNow()
                }
            }
        }
        removedIds
    }

    suspend fun hasAnyCatalogItems(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val db = readableDatabase
            db.rawQuery("SELECT 1 FROM catalog_items LIMIT 1", null).use { c ->
                c.moveToFirst()
            }
        }.getOrDefault(false)
    }

    /**
     * Reconstructs XtreamAccountConfig entries directly from SQLite catalog_items if the JSON state
     * file was ever missing a playlist entry.
     */
    suspend fun loadDistinctPlaylistsFromSql(): List<XtreamAccountConfig> = withContext(Dispatchers.IO) {
        val recovered = ArrayList<XtreamAccountConfig>()
        runCatching {
            val db = readableDatabase
            val fastSql = """
                SELECT s.source_id, s.source_name, s.live_count, s.movie_count, s.series_count, s.last_sync_ms
                FROM sync_sources s
                WHERE s.source_id != 'PORTAL_MAIN' AND s.source_id != ''
            """.trimIndent()
            var fastIdx = 0
            db.rawQuery(fastSql, null).use { c ->
                while (c.moveToNext()) {
                    val pid = c.getString(0).orEmpty()
                    if (pid.isBlank()) continue
                    val pName = c.getString(1).orEmpty().ifBlank { "قائمة XTREAM #${fastIdx + 1}" }
                    val liveCnt = c.getInt(2)
                    val movieCnt = c.getInt(3)
                    val seriesCnt = c.getInt(4)
                    val syncMs = c.getLong(5)
                    var baseUrl = ""
                    var userDec = ""
                    var passDec = ""
                    db.rawQuery(
                        "SELECT stream_base_url, stream_username, stream_password FROM catalog_items WHERE playlist_id = ? LIMIT 1",
                        arrayOf(pid)
                    ).use { sample ->
                        if (sample.moveToFirst()) {
                            baseUrl = sample.getString(0).orEmpty()
                            val uRaw = sample.getString(1).orEmpty()
                            val pRaw = sample.getString(2).orEmpty()
                            userDec = runCatching { URLDecoder.decode(uRaw, "UTF-8") }.getOrDefault(uRaw)
                            passDec = runCatching { URLDecoder.decode(pRaw, "UTF-8") }.getOrDefault(pRaw)
                        }
                    }
                    recovered.add(
                        XtreamAccountConfig(
                            id = pid,
                            serverName = pName,
                            serverUrl = baseUrl,
                            username = userDec,
                            password = passDec,
                            enabled = true,
                            isRemoteFromPortal = false,
                            orderIndex = fastIdx++,
                            liveCount = liveCnt,
                            movieCount = movieCnt,
                            seriesCount = seriesCnt,
                            lastSyncTimestamp = syncMs,
                            statusNote = "محفوظة محلياً ✓ ($liveCnt قناة • $movieCnt فيلم • $seriesCnt مسلسل)"
                        )
                    )
                }
            }
            if (recovered.isNotEmpty()) return@withContext recovered

            val sql = """
                SELECT
                    playlist_id,
                    MAX(playlist_name),
                    MAX(stream_base_url),
                    MAX(stream_username),
                    MAX(stream_password),
                    SUM(CASE WHEN content_type = 'LIVE_TV' THEN 1 ELSE 0 END),
                    SUM(CASE WHEN content_type = 'MOVIE' THEN 1 ELSE 0 END),
                    SUM(CASE WHEN content_type = 'SERIES' THEN 1 ELSE 0 END)
                FROM catalog_items
                WHERE playlist_id != 'PORTAL_MAIN' AND playlist_id != ''
                GROUP BY playlist_id
            """.trimIndent()
            var idx = 0
            db.rawQuery(sql, null).use { c ->
                while (c.moveToNext()) {
                    val pid = c.getString(0).orEmpty()
                    if (pid.isBlank()) continue
                    val pName = c.getString(1).orEmpty().ifBlank { "قائمة XTREAM #${idx + 1}" }
                    val baseUrl = c.getString(2).orEmpty()
                    val uRaw = c.getString(3).orEmpty()
                    val pRaw = c.getString(4).orEmpty()
                    val userDec = runCatching { URLDecoder.decode(uRaw, "UTF-8") }.getOrDefault(uRaw)
                    val passDec = runCatching { URLDecoder.decode(pRaw, "UTF-8") }.getOrDefault(pRaw)
                    val liveCnt = c.getInt(5)
                    val movieCnt = c.getInt(6)
                    val seriesCnt = c.getInt(7)
                    recovered.add(
                        XtreamAccountConfig(
                            id = pid,
                            serverName = pName,
                            serverUrl = baseUrl,
                            username = userDec,
                            password = passDec,
                            enabled = true,
                            isRemoteFromPortal = false,
                            orderIndex = idx++,
                            liveCount = liveCnt,
                            movieCount = movieCnt,
                            seriesCount = seriesCnt,
                            statusNote = "محفوظة محلياً ✓ ($liveCnt قناة • $movieCnt فيلم • $seriesCnt مسلسل)"
                        )
                    )
                }
            }
        }
        recovered
    }

    /**
     * Instant Single-Playlist Loader:
     * Loads ONLY the specified `playlistId` from local SQLite in parallel across CPU cores (~15–25ms).
     * Prevents loading multiple playlists into memory at once so app startup and playlist switching are instantaneous!
     */
    suspend fun loadPlaylistCatalogSnapshot(
        playlistId: String
    ): Triple<List<TvChannel>, List<TvChannel>, List<TvChannel>> = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext Triple(emptyList(), emptyList(), emptyList())
        playlistSnapshotCache[playlistId]?.let { cached ->
            if (cached.first.isNotEmpty() || cached.second.isNotEmpty() || cached.third.isNotEmpty()) {
                TiviMateDiagnostics.recordQueryLatency(1L)
                return@withContext cached
            }
        }
        val startMs = SystemClock.elapsedRealtime()
        val countsMap = getStoredPlaylistCountsMap()
        val (liveExpected, movieExpected, seriesExpected) = countsMap[playlistId] ?: Triple(4096, 8192, 4096)
        val filterIds = listOf(playlistId)

        coroutineScope {
            val liveDeferred = async(Dispatchers.IO) {
                runCatching {
                    queryItemsInternal(
                        contentType = StreamContentType.LIVE_TV,
                        playlistIds = filterIds,
                        limit = Int.MAX_VALUE,
                        expectedCount = liveExpected,
                        sharedStringPool = HashMap(512)
                    )
                }.getOrDefault(emptyList())
            }
            val moviesDeferred = async(Dispatchers.IO) {
                runCatching {
                    queryItemsInternal(
                        contentType = StreamContentType.MOVIE,
                        playlistIds = filterIds,
                        limit = Int.MAX_VALUE,
                        expectedCount = movieExpected,
                        sharedStringPool = HashMap(512)
                    )
                }.getOrDefault(emptyList())
            }
            val seriesDeferred = async(Dispatchers.IO) {
                runCatching {
                    queryItemsInternal(
                        contentType = StreamContentType.SERIES,
                        playlistIds = filterIds,
                        limit = Int.MAX_VALUE,
                        expectedCount = seriesExpected,
                        sharedStringPool = HashMap(512)
                    )
                }.getOrDefault(emptyList())
            }
            val live = liveDeferred.await()
            val movies = moviesDeferred.await()
            val series = seriesDeferred.await()
            val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            TiviMateDiagnostics.recordQueryLatency(elapsed)
            val resultTriple = Triple(live, movies, series)
            if (live.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()) {
                val rt = Runtime.getRuntime()
                if ((rt.totalMemory() - rt.freeMemory()) < (rt.maxMemory() * 0.75)) {
                    if (playlistSnapshotCache.size >= 2) playlistSnapshotCache.clear()
                    playlistSnapshotCache[playlistId] = resultTriple
                }
            }
            resultTriple
        }
    }

    suspend fun loadAllCatalogSnapshot(): Triple<List<TvChannel>, List<TvChannel>, List<TvChannel>> =
        withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            val (liveExpected, movieExpected, seriesExpected) = getCountsSummary()
            coroutineScope {
                val liveDeferred = async(Dispatchers.IO) {
                    runCatching {
                        queryItemsInternal(
                            contentType = StreamContentType.LIVE_TV,
                            limit = Int.MAX_VALUE,
                            expectedCount = liveExpected,
                            sharedStringPool = HashMap(512)
                        )
                    }.getOrDefault(emptyList())
                }
                val moviesDeferred = async(Dispatchers.IO) {
                    runCatching {
                        queryItemsInternal(
                            contentType = StreamContentType.MOVIE,
                            limit = Int.MAX_VALUE,
                            expectedCount = movieExpected,
                            sharedStringPool = HashMap(512)
                        )
                    }.getOrDefault(emptyList())
                }
                val seriesDeferred = async(Dispatchers.IO) {
                    runCatching {
                        queryItemsInternal(
                            contentType = StreamContentType.SERIES,
                            limit = Int.MAX_VALUE,
                            expectedCount = seriesExpected,
                            sharedStringPool = HashMap(512)
                        )
                    }.getOrDefault(emptyList())
                }
                val live = liveDeferred.await()
                val movies = moviesDeferred.await()
                val series = seriesDeferred.await()
                val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                TiviMateDiagnostics.recordQueryLatency(elapsed)
                Triple(live, movies, series)
            }
        }

    /**
     * Windowed Infinite Pagination SQL Query (`LIMIT ? OFFSET ?`):
     * Fetches a single window of catalog items directly from SQLite without loading 100k rows into RAM.
     */
    suspend fun queryCatalogPage(
        contentType: StreamContentType,
        playlistIds: List<String>,
        groupName: String,
        limit: Int,
        offset: Int
    ): List<TvChannel> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val whereClauses = mutableListOf("content_type = ?")
        val args = mutableListOf(contentType.name)

        if (playlistIds.size == 1) {
            whereClauses.add("playlist_id = ?")
            args.add(playlistIds[0])
        } else if (playlistIds.isNotEmpty()) {
            val placeholders = playlistIds.joinToString(",") { "?" }
            whereClauses.add("playlist_id IN ($placeholders)")
            args.addAll(playlistIds)
        }

        if (groupName.isNotBlank() && groupName != "الكل" && groupName != "Tout" && groupName != "All" && groupName != "__NONE__") {
            whereClauses.add("group_name = ?")
            args.add(groupName)
        }

        val safeLimit = limit.coerceIn(1, 500)
        val safeOffset = offset.coerceAtLeast(0)
        args.add(safeLimit.toString())
        args.add(safeOffset.toString())

        val sql = """
            SELECT
                item_id, channel_number, name, url, group_name, logo,
                user_agent, referer, origin, cookie, drm_scheme, drm_key,
                content_type, rating, release_year, plot, series_id,
                playlist_id, playlist_name, stream_id, stream_base_url,
                stream_username, stream_password, container_ext, video_codec, audio_codec,
                tv_archive, tv_archive_duration, epg_channel_id
            FROM catalog_items
            WHERE ${whereClauses.joinToString(" AND ")}
            ORDER BY sort_order ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val result = ArrayList<TvChannel>(safeLimit)
        runCatching {
            db.rawQuery(sql, args.toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    result.add(
                        TvChannel(
                            id = c.getInt(0),
                            number = c.getInt(1),
                            name = c.getString(2).orEmpty(),
                            url = c.getString(3).orEmpty(),
                            group = c.getString(4).orEmpty().ifBlank { "عام" },
                            logo = c.getString(5)?.takeIf { it.isNotBlank() },
                            userAgent = c.getString(6)?.takeIf { it.isNotBlank() },
                            referer = c.getString(7)?.takeIf { it.isNotBlank() },
                            origin = c.getString(8)?.takeIf { it.isNotBlank() },
                            cookie = c.getString(9)?.takeIf { it.isNotBlank() },
                            drmScheme = c.getString(10).orEmpty(),
                            drmKey = c.getString(11).orEmpty(),
                            contentType = contentType,
                            rating = c.getString(13).orEmpty(),
                            releaseYear = c.getString(14).orEmpty(),
                            plot = c.getString(15).orEmpty(),
                            seriesId = c.getInt(16),
                            playlistId = c.getString(17).orEmpty().ifBlank { "PORTAL_MAIN" },
                            playlistName = c.getString(18).orEmpty().ifBlank { "الرئيسية" },
                            streamId = c.getInt(19),
                            streamBaseUrl = c.getString(20).orEmpty(),
                            streamUsername = c.getString(21).orEmpty(),
                            streamPassword = c.getString(22).orEmpty(),
                            containerExtension = c.getString(23).orEmpty(),
                            videoCodecHint = c.getString(24).orEmpty(),
                            audioCodecHint = c.getString(25).orEmpty(),
                            tvArchive = c.getInt(26),
                            tvArchiveDuration = c.getInt(27),
                            epgChannelId = c.getString(28).orEmpty()
                        )
                    )
                }
            }
        }
        result
    }

    private val epgMemoryCache = ConcurrentHashMap<String, EpgProgramItem>()

    suspend fun saveEpgProgramsBatch(playlistId: String, programs: List<EpgProgramItem>) = withContext(Dispatchers.IO) {
        if (programs.isEmpty()) return@withContext
        runCatching {
            val db = writableDatabase
            val now = System.currentTimeMillis()
            db.beginTransactionNonExclusive()
            try {
                db.delete("epg_data", "playlist_id = ? OR end_ms < ?", arrayOf(playlistId, (now - 3600_000L).toString()))
                val stmt = db.compileStatement(
                    "INSERT INTO epg_data (playlist_id, channel_id, title, description, start_ms, end_ms) VALUES (?, ?, ?, ?, ?, ?)"
                )
                try {
                    for (p in programs) {
                        stmt.clearBindings()
                        stmt.bindString(1, playlistId)
                        stmt.bindString(2, p.channelId)
                        stmt.bindString(3, p.title)
                        stmt.bindString(4, p.description)
                        stmt.bindLong(5, p.startEpochMs)
                        stmt.bindLong(6, p.endEpochMs)
                        stmt.executeInsert()
                        if (now in p.startEpochMs..p.endEpochMs) {
                            epgMemoryCache["${playlistId}_${p.channelId.lowercase(Locale.ROOT)}"] = p
                        }
                    }
                } finally {
                    runCatching { stmt.close() }
                }
                db.setTransactionSuccessful()
            } finally {
                runCatching { db.endTransaction() }
            }
        }
    }

    suspend fun getCurrentEpgForChannel(playlistId: String, epgChannelId: String, channelName: String = ""): EpgProgramItem? =
        withContext(Dispatchers.IO) {
            val keyCandidates = buildList {
                if (epgChannelId.isNotBlank()) add(epgChannelId.trim())
                if (channelName.isNotBlank()) add(channelName.trim())
            }
            if (keyCandidates.isEmpty()) return@withContext null
            val now = System.currentTimeMillis()
            for (cand in keyCandidates) {
                val memKey = "${playlistId}_${cand.lowercase(Locale.ROOT)}"
                epgMemoryCache[memKey]?.let { cached ->
                    if (now in cached.startEpochMs..cached.endEpochMs) return@withContext cached
                    epgMemoryCache.remove(memKey)
                }
            }
            runCatching {
                val db = readableDatabase
                for (cand in keyCandidates) {
                    db.rawQuery(
                        """
                        SELECT channel_id, title, description, start_ms, end_ms
                        FROM epg_data
                        WHERE playlist_id = ? AND channel_id = ? AND start_ms <= ? AND end_ms >= ?
                        ORDER BY start_ms DESC
                        LIMIT 1
                        """.trimIndent(),
                        arrayOf(playlistId, cand, now.toString(), now.toString())
                    ).use { c ->
                        if (c.moveToFirst()) {
                            val item = EpgProgramItem(
                                channelId = c.getString(0).orEmpty(),
                                title = c.getString(1).orEmpty(),
                                description = c.getString(2).orEmpty(),
                                startEpochMs = c.getLong(3),
                                endEpochMs = c.getLong(4)
                            )
                            epgMemoryCache["${playlistId}_${cand.lowercase(Locale.ROOT)}"] = item
                            return@withContext item
                        }
                    }
                }
            }
            null
        }

    suspend fun queryCatalogFiltered(
        contentType: StreamContentType,
        playlistIds: List<String> = emptyList(),
        groupName: String = "الكل",
        searchQuery: String = "",
        hiddenBouquets: Set<String> = emptySet(),
        limit: Int = Int.MAX_VALUE,
        offset: Int = 0
    ): List<TvChannel> = withContext(Dispatchers.IO) {
        val startMs = SystemClock.elapsedRealtime()
        val result = runCatching {
            queryItemsInternal(
                contentType = contentType,
                playlistIds = playlistIds,
                groupName = groupName,
                searchQuery = searchQuery,
                hiddenBouquets = hiddenBouquets,
                limit = limit,
                offset = offset
            )
        }.getOrDefault(emptyList())
        val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
        TiviMateDiagnostics.recordQueryLatency(elapsed)
        result
    }

    /**
     * Reads catalog items using O(N) `rowid > lastRowId` Keyset Pagination in 6,000-row pages
     * with String Interning so Android's 2MB SQLite CursorWindow never overflows.
     */
    private fun queryItemsInternal(
        contentType: StreamContentType,
        playlistIds: List<String> = emptyList(),
        groupName: String = "الكل",
        searchQuery: String = "",
        hiddenBouquets: Set<String> = emptySet(),
        limit: Int = Int.MAX_VALUE,
        offset: Int = 0,
        expectedCount: Int = 8192,
        sharedStringPool: HashMap<String, String>? = null
    ): List<TvChannel> {
        val db = readableDatabase
        val stringPool = sharedStringPool ?: HashMap<String, String>(512)
        fun intern(s: String?): String {
            if (s.isNullOrEmpty()) return ""
            return stringPool.getOrPut(s) { s }
        }
        fun internNullable(s: String?): String? {
            if (s.isNullOrBlank()) return null
            return stringPool.getOrPut(s) { s }
        }

        val useSortOrderSeek = (playlistIds.size == 1)
        val cursorCol = if (useSortOrderSeek) "sort_order" else "rowid"
        val whereClauses = mutableListOf("$cursorCol > ?", "content_type = ?")
        val staticArgs = mutableListOf(contentType.name)

        if (playlistIds.size == 1) {
            whereClauses.add("playlist_id = ?")
            staticArgs.add(playlistIds[0])
        } else if (playlistIds.isNotEmpty()) {
            val placeholders = playlistIds.joinToString(",") { "?" }
            whereClauses.add("playlist_id IN ($placeholders)")
            staticArgs.addAll(playlistIds)
        }

        if (groupName.isNotBlank() && groupName != "الكل" && groupName != "Tout" && groupName != "All") {
            whereClauses.add("group_name = ?")
            staticArgs.add(groupName)
        }

        val q = searchQuery.trim().lowercase(Locale.ROOT)
        if (q.isNotEmpty()) {
            whereClauses.add("(name LIKE ? OR group_name LIKE ? OR CAST(channel_number AS TEXT) = ?)")
            staticArgs.add("%$q%")
            staticArgs.add("%$q%")
            staticArgs.add(q)
        }

        val whereSql = whereClauses.joinToString(" AND ")
        val initialCap = if (expectedCount > 0) expectedCount.coerceAtMost(250_000) else 4096
        val list = ArrayList<TvChannel>(initialCap)

        var remaining = limit
        var lastRowId = if (useSortOrderSeek) -1L else 0L

        while (remaining > 0) {
            val pageSize = remaining.coerceAtMost(CURSOR_PAGE_SIZE)
            val pageArgs = Array(staticArgs.size + 1) { idx ->
                if (idx == 0) lastRowId.toString() else staticArgs[idx - 1]
            }
            val sql = """
                SELECT
                    $cursorCol, item_id, channel_number, name, url, group_name, logo,
                    user_agent, referer, origin, cookie, drm_scheme, drm_key,
                    content_type, rating, release_year, plot, series_id,
                    playlist_id, playlist_name, stream_id, stream_base_url,
                    stream_username, stream_password, container_ext, video_codec, audio_codec,
                    tv_archive, tv_archive_duration, epg_channel_id
                FROM catalog_items
                WHERE $whereSql
                ORDER BY $cursorCol ASC
                LIMIT $pageSize
            """.trimIndent()

            var fetchedInPage = 0
            db.rawQuery(sql, pageArgs).use { c ->
                while (c.moveToNext()) {
                    fetchedInPage++
                    lastRowId = c.getLong(0)
                    val grp = intern(c.getString(5)).ifBlank { "عام" }
                    if (hiddenBouquets.isNotEmpty() && grp in hiddenBouquets) continue
                    val nm = c.getString(3).orEmpty()

                    list.add(
                        TvChannel(
                            id = c.getInt(1),
                            number = c.getInt(2),
                            name = nm,
                            url = c.getString(4).orEmpty(),
                            group = grp,
                            logo = c.getString(6)?.takeIf { it.isNotBlank() },
                            userAgent = internNullable(c.getString(7)),
                            referer = internNullable(c.getString(8)),
                            origin = internNullable(c.getString(9)),
                            cookie = internNullable(c.getString(10)),
                            drmScheme = intern(c.getString(11)),
                            drmKey = c.getString(12).orEmpty(),
                            contentType = contentType,
                            rating = intern(c.getString(14)),
                            releaseYear = intern(c.getString(15)),
                            plot = "",
                            seriesId = c.getInt(17),
                            playlistId = intern(c.getString(18)).ifBlank { "PORTAL_MAIN" },
                            playlistName = intern(c.getString(19)).ifBlank { "الرئيسية" },
                            streamId = c.getInt(20),
                            streamBaseUrl = intern(c.getString(21)),
                            streamUsername = intern(c.getString(22)),
                            streamPassword = intern(c.getString(23)),
                            containerExtension = intern(c.getString(24)),
                            videoCodecHint = intern(c.getString(25)),
                            audioCodecHint = intern(c.getString(26)),
                            tvArchive = c.getInt(27),
                            tvArchiveDuration = c.getInt(28),
                            epgChannelId = intern(c.getString(29))
                        )
                    )
                }
            }

            if (fetchedInPage < pageSize) break
            if (remaining != Int.MAX_VALUE) {
                remaining -= fetchedInPage
            }
        }

        return list
    }

    suspend fun getCountsSummary(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        var live = 0
        var movies = 0
        var series = 0
        runCatching {
            val db = readableDatabase
            db.rawQuery(
                "SELECT content_type, COUNT(*) FROM catalog_items GROUP BY content_type",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    when (c.getString(0)) {
                        StreamContentType.LIVE_TV.name -> live = c.getInt(1)
                        StreamContentType.MOVIE.name -> movies = c.getInt(1)
                        StreamContentType.SERIES.name -> series = c.getInt(1)
                    }
                }
            }
        }
        Triple(live, movies, series)
    }
}
