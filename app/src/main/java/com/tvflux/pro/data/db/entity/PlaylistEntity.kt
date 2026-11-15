package com.tvflux.pro.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val playlistId: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val serverName: String,
    val lastFullSyncAt: Long = 0L,
    val lastEtag: String = "",
    val catalogHash: String = "",
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0
)
