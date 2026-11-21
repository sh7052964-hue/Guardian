package com.guardian360.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedPolicyEntity::class,
        CachedAppInventoryEntity::class,
        PendingRequestEntity::class,
        PendingSecurityEventEntity::class,
        UsageSummaryEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GuardianRoomDatabase : RoomDatabase() {
    abstract fun cachedPolicyDao(): CachedPolicyDao
    abstract fun cachedAppInventoryDao(): CachedAppInventoryDao
    abstract fun pendingRequestDao(): PendingRequestDao
    abstract fun pendingSecurityEventDao(): PendingSecurityEventDao
    abstract fun usageSummaryDao(): UsageSummaryDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: GuardianRoomDatabase? = null

        fun getInstance(context: Context): GuardianRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GuardianRoomDatabase::class.java,
                    "guardian360_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
