package com.guardian360.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedPolicyDao {
    @Query("SELECT * FROM cached_policies WHERE childUid = :childUid LIMIT 1")
    fun observePolicy(childUid: String): Flow<CachedPolicyEntity?>

    @Query("SELECT * FROM cached_policies")
    fun observeAllPolicies(): Flow<List<CachedPolicyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolicy(entity: CachedPolicyEntity)
}

@Dao
interface CachedAppInventoryDao {
    @Query("SELECT * FROM cached_app_inventory ORDER BY minutesToday DESC, appLabel ASC")
    fun observeInventory(): Flow<List<CachedAppInventoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedAppInventoryEntity>)

    @Query("DELETE FROM cached_app_inventory")
    suspend fun clearAll()
}

@Dao
interface PendingRequestDao {
    @Query("SELECT * FROM pending_requests ORDER BY createdAtEpochMs DESC")
    fun observePendingRequests(): Flow<List<PendingRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingRequestEntity)

    @Query("DELETE FROM pending_requests WHERE localId = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PendingSecurityEventDao {
    @Query("SELECT * FROM pending_security_events ORDER BY createdAtEpochMs DESC")
    fun observePendingEvents(): Flow<List<PendingSecurityEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingSecurityEventEntity)

    @Query("DELETE FROM pending_security_events WHERE localId = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface UsageSummaryDao {
    @Query("SELECT * FROM usage_summaries ORDER BY dateKey DESC LIMIT 30")
    fun observeRecentSummaries(): Flow<List<UsageSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: UsageSummaryEntity)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAtEpochMs ASC")
    fun observeQueue(): Flow<List<SyncQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, status = :status WHERE id = :id")
    suspend fun markRetry(id: Long, status: String)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)
}
