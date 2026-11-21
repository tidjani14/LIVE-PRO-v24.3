package com.tvflux.pro.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "series_episodes",
    indices = [
        // فهرس مركب لاسترجاع حلقات المسلسل مرتبة حسب الموسم والحلقة فوراً
        Index(
            value = ["playlistId", "seriesId", "season", "episode"],
            unique = true,
            name = "idx_episodes_pid_series_season_ep"
        )
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val playlistId: String,
    val seriesId: Int,
    val episodeId: Int,
    val title: String,
    val season: Int,
    val episode: Int,
    val containerExtension: String = "mp4",
    val streamUrl: String,
    val duration: String = "",
    val rating: String = "",
    val plot: String = "",
    val lastFetchedAt: Long
)
