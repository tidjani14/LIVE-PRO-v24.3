package com.tvflux.pro.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "channels",
    indices = [
        // 1. فهرس فريد لمنع تكرار القناة داخل نفس القائمة وتسريع عملية REPLACE
        Index(value = ["playlistId", "streamId"], unique = true, name = "idx_channels_pid_stream_unique"),
        // 2. فهرس حرج لحذف القنوات الملغاة بالزمن (pruneByTimestamp) في O(log N)
        Index(value = ["playlistId", "contentType", "lastSyncedAt"], name = "idx_channels_pid_type_synced"),
        // 3. فهرس العرض الأبجدي والبحث السريع حسب النوع
        Index(value = ["playlistId", "contentType", "name"], name = "idx_channels_pid_type_name"),
        // Fast local first paint: filter playlist/type and order by channel number.
        Index(value = ["playlistId", "contentType", "number"], name = "idx_channels_pid_type_number"),
        // 4. فهرس الفلترة حسب الباقة/القسم
        Index(value = ["playlistId", "categoryId"], name = "idx_channels_pid_cat")
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val playlistId: String,
    val streamId: Int,
    val name: String,
    val url: String,
    val logo: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val contentType: String, // "LIVE" | "MOVIE" | "SERIES"
    val number: Int = 0,
    val epgChannelId: String = "",
    val rating: String = "",
    val added: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val origin: String = "",
    val cookie: String = "",
    val lastSyncedAt: Long,
    val catalogHash: String = ""
) {
    companion object {
        const val TYPE_LIVE = "LIVE"
        const val TYPE_MOVIE = "MOVIE"
        const val TYPE_SERIES = "SERIES"
    }
}
