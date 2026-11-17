package com.tvflux.pro.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tvflux.pro.data.db.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE playlistId = :pid LIMIT 1")
    suspend fun getPlaylist(pid: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY serverName ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query(
        """
        UPDATE playlists
        SET lastFullSyncAt = :ts,
            lastEtag = :etag,
            catalogHash = :hash
        WHERE playlistId = :pid
        """
    )
    suspend fun markSynced(pid: String, ts: Long, etag: String, hash: String)

    @Query(
        """
        UPDATE playlists
        SET liveCount = :live,
            movieCount = :movie,
            seriesCount = :series
        WHERE playlistId = :pid
        """
    )
    suspend fun updateCounts(pid: String, live: Int, movie: Int, series: Int)

    @Query("UPDATE playlists SET lastEtag = :etag WHERE playlistId = :pid")
    suspend fun updateEtag(pid: String, etag: String)
}
