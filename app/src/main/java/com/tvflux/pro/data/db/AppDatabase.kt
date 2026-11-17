package com.tvflux.pro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tvflux.pro.data.db.dao.ChannelDao
import com.tvflux.pro.data.db.dao.EpisodeDao
import com.tvflux.pro.data.db.dao.PlaylistDao
import com.tvflux.pro.data.db.entity.ChannelEntity
import com.tvflux.pro.data.db.entity.EpisodeEntity
import com.tvflux.pro.data.db.entity.PlaylistEntity

@Database(
    entities = [
        ChannelEntity::class,
        PlaylistEntity::class,
        EpisodeEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        private const val DB_NAME = "player_vip_v27_lightning_catalog.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration stubs للأجيال القادمة
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // جاهز لترقيات المخطط المستقبلية بدون مسح بيانات المستخدم
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    // تفعيل وضع WRITE_AHEAD_LOGGING لقراءة متزامنة أثناء الكتابة في الخلفية
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
