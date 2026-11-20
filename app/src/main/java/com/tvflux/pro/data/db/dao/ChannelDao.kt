package com.tvflux.pro.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tvflux.pro.data.db.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    // إدراج دفعة من 500 عنصر مع استبدال السجل المطابق لـ (playlistId, streamId)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(items: List<ChannelEntity>)

    @Query(
        """
        SELECT * FROM channels
        WHERE playlistId = :pid AND contentType = 'LIVE'
        ORDER BY number ASC, id ASC
        """
    )
    fun observeLive(pid: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT * FROM channels
        WHERE playlistId = :pid AND contentType = 'MOVIE'
        ORDER BY id ASC
        """
    )
    fun observeMovies(pid: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT * FROM channels
        WHERE playlistId = :pid AND contentType = 'SERIES'
        ORDER BY id ASC
        """
    )
    fun observeSeries(pid: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM channels
        WHERE playlistId = :pid AND contentType = :type
        """
    )
    suspend fun countByType(pid: String, type: String): Int

    // [F2] حذف العناصر القديمة التي لم ترد في المزامنة الحالية عبر الطابع الزمني (O(1) RAM)
    @Query(
        """
        DELETE FROM channels
        WHERE playlistId = :pid
          AND contentType = :type
          AND lastSyncedAt < :syncStart
        """
    )
    suspend fun pruneByTimestamp(pid: String, type: String, syncStart: Long): Int

    @Query("DELETE FROM channels WHERE playlistId = :pid")
    suspend fun clearPlaylist(pid: String)

    @Query(
        """
        SELECT * FROM channels
        WHERE playlistId = :pid
          AND contentType = :type
          AND (name LIKE '%' || :q || '%' OR categoryName LIKE '%' || :q || '%')
        ORDER BY number ASC, name ASC
        LIMIT 500
        """
    )
    fun search(pid: String, type: String, q: String): Flow<List<ChannelEntity>>
}
