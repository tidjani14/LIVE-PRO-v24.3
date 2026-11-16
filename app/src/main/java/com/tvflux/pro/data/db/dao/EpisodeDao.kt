package com.tvflux.pro.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tvflux.pro.data.db.entity.EpisodeEntity

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(episodes: List<EpisodeEntity>)

    @Query(
        """
        SELECT * FROM series_episodes
        WHERE playlistId = :pid AND seriesId = :seriesId
        ORDER BY season ASC, episode ASC
        """
    )
    suspend fun getBySeries(pid: String, seriesId: Int): List<EpisodeEntity>

    @Query(
        """
        SELECT MAX(lastFetchedAt) FROM series_episodes
        WHERE playlistId = :pid AND seriesId = :seriesId
        """
    )
    suspend fun getLastFetchedAt(pid: String, seriesId: Int): Long?

    // [F5] التحقق من صلاحية الكاش المحلي لحلقات المسلسل (TTL = 1 ساعة)
    suspend fun isFresh(pid: String, seriesId: Int, ttlMs: Long = 3_600_000L): Boolean {
        val lastFetched = getLastFetchedAt(pid, seriesId) ?: return false
        return (System.currentTimeMillis() - lastFetched) < ttlMs
    }

    @Query("DELETE FROM series_episodes WHERE playlistId = :pid AND seriesId = :seriesId")
    suspend fun clearSeries(pid: String, seriesId: Int)
}
