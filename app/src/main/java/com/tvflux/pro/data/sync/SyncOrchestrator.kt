package com.tvflux.pro.data.sync

import android.os.SystemClock
import com.tvflux.pro.ChannelRepository
import com.tvflux.pro.CloudflareDohManager
import com.tvflux.pro.CloudflareWarpVpnService
import com.tvflux.pro.data.db.AppDatabase
import com.tvflux.pro.data.db.entity.ChannelEntity
import com.tvflux.pro.data.db.entity.EpisodeEntity
import com.tvflux.pro.data.db.entity.PlaylistEntity
import com.tvflux.pro.data.remote.NetworkModule
import com.tvflux.pro.data.remote.XtreamApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request

sealed class SyncEvent {
    data class Phase(val playlistId: String, val phaseName: String) : SyncEvent()
    data class LiveReady(val playlistId: String, val count: Int, val elapsedMs: Long) : SyncEvent()
    data class VodProgress(val playlistId: String, val loadedCount: Int) : SyncEvent()
    data class SeriesProgress(val playlistId: String, val loadedCount: Int) : SyncEvent()
    data class AllDone(
        val playlistId: String,
        val liveCount: Int,
        val movieCount: Int,
        val seriesCount: Int,
        val totalElapsedMs: Long
    ) : SyncEvent()
    data class Cancelled(val playlistId: String) : SyncEvent()
    data class Error(val playlistId: String, val message: String) : SyncEvent()
}

class SyncOrchestrator(
    private val db: AppDatabase,
    private val api: XtreamApi = XtreamApi(NetworkModule.syncClient),
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    @Volatile
    var currentJob: Job? = null
        private set

    private val _events = MutableSharedFlow<SyncEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    // [F6] إلغاء فوري لأي مزامنة جارية
    fun cancelSync() {
        val active = currentJob
        currentJob = null
        active?.cancel()
    }

    fun startSync(p: PlaylistEntity, force: Boolean = false): Job {
        cancelSync()
        val job = externalScope.launch(Dispatchers.IO) {
            try {
                db.playlistDao().upsert(p)
                if (!force && !needsFullSync(p)) {
                    val liveCnt = db.channelDao().countByType(p.playlistId, ChannelEntity.TYPE_LIVE)
                    val movCnt = db.channelDao().countByType(p.playlistId, ChannelEntity.TYPE_MOVIE)
                    val serCnt = db.channelDao().countByType(p.playlistId, ChannelEntity.TYPE_SERIES)
                    _events.tryEmit(SyncEvent.AllDone(p.playlistId, liveCnt, movCnt, serCnt, 0L))
                    return@launch
                }
                fullSync(p)
            } catch (ce: CancellationException) {
                _events.tryEmit(SyncEvent.Cancelled(p.playlistId))
                throw ce
            } catch (t: Throwable) {
                // إذا حظر سيرفر Xtream عنوان IP مركز بيانات WireGuard (ASN Block)، ننتقل تلقائياً لوضع التوافق المباشر مع تشفير DoH ونعيد المحاولة فوراً
                if (CloudflareWarpVpnService.isTunnelRunning && !CloudflareWarpVpnService.isDohOnlyMode) {
                    runCatching {
                        CloudflareWarpVpnService.isDohOnlyMode = true
                        CloudflareWarpVpnService.dohShieldFallbackActive = true
                        CloudflareDohManager.dohEnabled = true
                        CloudflareDohManager.clearDnsCache()
                        ChannelRepository.evictIdleApiConnections()
                        fullSync(p)
                        return@launch
                    }
                }
                _events.tryEmit(SyncEvent.Error(p.playlistId, t.message ?: "Sync failed"))
            } finally {
                CloudflareWarpVpnService.isXtreamCatalogSyncActive = false
            }
        }
        currentJob = job
        return job
    }

    /**
     * [F7] فحص ETag / If-None-Match حقيقي مع السيرفر:
     * - إذا أعاد السيرفر 304 Not Modified -> return false (صفر سحب).
     * - إذا أعاد 200 OK مع ETag جديد -> يحفظ الـ ETag ويرجع true.
     * - إذا لم يدعم السيرفر ETag -> يعتمد على TTL (24 ساعة) ووجود قنوات محلية.
     */
    suspend fun needsFullSync(p: PlaylistEntity): Boolean = withContext(Dispatchers.IO) {
        val stored = db.playlistDao().getPlaylist(p.playlistId) ?: return@withContext true
        val existingLive = db.channelDao().countByType(p.playlistId, ChannelEntity.TYPE_LIVE)
        if (existingLive == 0 || stored.lastFullSyncAt <= 0L) {
            return@withContext true
        }

        val probeUrl = api.buildUrl(stored, "get_live_categories")
        val reqBuilder = Request.Builder()
            .url(probeUrl)
            .header("User-Agent", "IBOPlayerPro/3.9 (Android TV; Linux)")
        if (stored.lastEtag.isNotBlank()) {
            reqBuilder.header("If-None-Match", stored.lastEtag)
        }

        return@withContext try {
            NetworkModule.syncClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code == 304) {
                    return@use false
                }
                if (resp.isSuccessful) {
                    val newEtag = resp.header("ETag")?.trim().orEmpty()
                    if (newEtag.isNotBlank()) {
                        if (newEtag == stored.lastEtag) {
                            return@use false
                        }
                        db.playlistDao().updateEtag(p.playlistId, newEtag)
                        return@use true
                    }
                }
                val ageMs = System.currentTimeMillis() - stored.lastFullSyncAt
                ageMs > TWENTY_FOUR_HOURS_MS
            }
        } catch (_: Throwable) {
            val ageMs = System.currentTimeMillis() - stored.lastFullSyncAt
            ageMs > TWENTY_FOUR_HOURS_MS
        }
    }

    /**
     * [F2 & F3] المزامنة الكاملة ثنائية المراحل:
     * - Phase A: سحب القنوات الحية فقط + join() كامل + pruneByTimestamp + إطلاق LiveReady.
     * - Phase B: سحب VOD + Series بالتوازي مع Semaphore(2) بعد اكتمال Phase A حصراً.
     */
    suspend fun fullSync(p: PlaylistEntity) = withContext(Dispatchers.IO) {
        val overallStartRealtime = SystemClock.elapsedRealtime()
        val syncStartTimestamp = System.currentTimeMillis()
        CloudflareWarpVpnService.isXtreamCatalogSyncActive = true
        CloudflareDohManager.dohEnabled = true

        // ════════════════════════════════════════════════════════════════════
        // Phase A: LIVE TV ONLY (أولوية قصوى بدون أي منافسة على SQLite أو الشبكة)
        // ════════════════════════════════════════════════════════════════════
        _events.tryEmit(SyncEvent.Phase(p.playlistId, "PHASE_A_LIVE"))
        val phaseAStartRealtime = SystemClock.elapsedRealtime()

        var finalLiveCount = 0
        val phaseAJob = externalScope.launch(Dispatchers.IO) {
            val liveCats = runCatching { api.getLiveCategories(p) }.getOrDefault(emptyMap())
            api.streamLive(
                p = p,
                cats = liveCats,
                syncStart = syncStartTimestamp,
                onBatch = { batch ->
                    ensureActive()
                    db.channelDao().upsertBatch(batch)
                }
            )
            ensureActive()
            db.channelDao().pruneByTimestamp(
                pid = p.playlistId,
                type = ChannelEntity.TYPE_LIVE,
                syncStart = syncStartTimestamp
            )
            finalLiveCount = db.channelDao().countByType(p.playlistId, ChannelEntity.TYPE_LIVE)
            val existingPl = db.playlistDao().getPlaylist(p.playlistId)
            db.playlistDao().updateCounts(
                pid = p.playlistId,
                live = finalLiveCount,
                movie = existingPl?.movieCount ?: 0,
                series = existingPl?.seriesCount ?: 0
            )
        }

        // انتظار اكتمال Phase A بالكامل قبل إطلاق LiveReady أو بدء Phase B
        phaseAJob.join()
        ensureActive()

        val liveElapsedMs = (SystemClock.elapsedRealtime() - phaseAStartRealtime).coerceAtLeast(1L)
        _events.tryEmit(SyncEvent.LiveReady(p.playlistId, finalLiveCount, liveElapsedMs))

        // ════════════════════════════════════════════════════════════════════
        // Phase B: SEQUENTIAL — Movies first, then Series (no parallel network pull)
        // ════════════════════════════════════════════════════════════════════
        _events.tryEmit(SyncEvent.Phase(p.playlistId, "PHASE_B_VOD_THEN_SERIES"))

        val finalMovieCount = run {
            val vodCats = runCatching { api.getVodCategories(p) }.getOrDefault(emptyMap())
            var loadedVod = 0
            api.streamVod(
                p = p,
                cats = vodCats,
                syncStart = syncStartTimestamp,
                onBatch = { batch ->
                    ensureActive()
                    db.channelDao().upsertBatch(batch)
                    loadedVod += batch.size
                    _events.tryEmit(SyncEvent.VodProgress(p.playlistId, loadedVod))
                }
            )
            ensureActive()
            db.channelDao().pruneByTimestamp(
                pid = p.playlistId,
                type = ChannelEntity.TYPE_MOVIE,
                syncStart = syncStartTimestamp
            )
            db.channelDao().countByType(p.playlistId, ChannelEntity.TYPE_MOVIE)
        }

        val finalSeriesCount = run {
            val seriesCats = runCatching { api.getSeriesCategories(p) }.getOrDefault(emptyMap())
            var loadedSeries = 0
            api.streamSeries(
                p = p,
                cats = seriesCats,
                syncStart = syncStartTimestamp,
                onBatch = { batch ->
                    ensureActive()
                    db.channelDao().upsertBatch(batch)
                    loadedSeries += batch.size
                    _events.tryEmit(SyncEvent.SeriesProgress(p.playlistId, loadedSeries))
                }
            )
            ensureActive()
            db.channelDao().pruneByTimestamp(
                pid = p.playlistId,
                type = ChannelEntity.TYPE_SERIES,
                syncStart = syncStartTimestamp
            )
            db.channelDao().countByType(p.playlistId, ChannelEntity.TYPE_SERIES)
        }

        val latestPl = db.playlistDao().getPlaylist(p.playlistId)
        val etagNow = latestPl?.lastEtag.orEmpty()
        val catalogHashNow = "${finalLiveCount}_${finalMovieCount}_${finalSeriesCount}_$syncStartTimestamp"

        db.playlistDao().updateCounts(
            pid = p.playlistId,
            live = finalLiveCount,
            movie = finalMovieCount,
            series = finalSeriesCount
        )
        db.playlistDao().markSynced(
            pid = p.playlistId,
            ts = syncStartTimestamp,
            etag = etagNow,
            hash = catalogHashNow
        )

        val totalElapsedMs = (SystemClock.elapsedRealtime() - overallStartRealtime).coerceAtLeast(1L)
        _events.tryEmit(
            SyncEvent.AllDone(
                playlistId = p.playlistId,
                liveCount = finalLiveCount,
                movieCount = finalMovieCount,
                seriesCount = finalSeriesCount,
                totalElapsedMs = totalElapsedMs
            )
        )
    }

    /**
     * [F5] جلب حلقات المسلسل بشكل كسول (Lazy) عند فتح المسلسل فقط مع احترام TTL = 1 ساعة
     */
    suspend fun fetchSeriesEpisodesLazy(
        p: PlaylistEntity,
        seriesId: Int,
        ttlMs: Long = ONE_HOUR_MS
    ): List<EpisodeEntity> = withContext(Dispatchers.IO) {
        val episodeDao = db.episodeDao()
        if (episodeDao.isFresh(p.playlistId, seriesId, ttlMs)) {
            val cached = episodeDao.getBySeries(p.playlistId, seriesId)
            if (cached.isNotEmpty()) return@withContext cached
        }

        val remoteInfo = api.getSeriesInfo(p, seriesId)
        if (remoteInfo.episodes.isNotEmpty()) {
            episodeDao.clearSeries(p.playlistId, seriesId)
            for (chunk in remoteInfo.episodes.chunked(500)) {
                episodeDao.upsertBatch(chunk)
            }
        }
        episodeDao.getBySeries(p.playlistId, seriesId)
    }

    companion object {
        private const val ONE_HOUR_MS = 3_600_000L
        private const val TWENTY_FOUR_HOURS_MS = 24 * 3_600_000L
    }
}
