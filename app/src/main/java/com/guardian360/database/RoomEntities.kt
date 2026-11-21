package com.guardian360.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local offline policy cache. Never stores raw authentication secrets.
 */
@Entity(tableName = "cached_policies")
data class CachedPolicyEntity(
    @PrimaryKey val childUid: String,
    val familyId: String,
    val policyVersion: Long,
    val dailyLimitMinutes: Long,
    val downtimeEnabled: Boolean,
    val bedtimeStart: String,
    val bedtimeEnd: String,
    val schoolTimeStart: String,
    val schoolTimeEnd: String,
    val maxAgeRating: String,
    val youtubeMode: String,
    val dnsFilteringEnabled: Boolean,
    val blockedCategoriesCsv: String,
    val blockedPackagesCsv: String,
    val allowedPackagesCsv: String,
    val blockedDomainsCsv: String,
    val allowedDomainsCsv: String,
    val lastActionState: String,
    val syncedAtEpochMs: Long
)

@Entity(tableName = "cached_app_inventory")
data class CachedAppInventoryEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val category: String,
    val gameSubcategory: String?,
    val ageRating: String,
    val isSystemApp: Boolean,
    val isBlocked: Boolean,
    val minutesToday: Long,
    val minutesWeek: Long,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "pending_requests")
data class PendingRequestEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val familyId: String,
    val childUid: String,
    val requestType: String,
    val targetPackage: String?,
    val targetDomain: String?,
    val requestedMinutes: Long?,
    val reason: String,
    val createdAtEpochMs: Long
)

@Entity(tableName = "pending_security_events")
data class PendingSecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val familyId: String,
    val childUid: String,
    val eventType: String,
    val severity: String,
    val title: String,
    val details: String,
    val createdAtEpochMs: Long
)

@Entity(tableName = "usage_summaries")
data class UsageSummaryEntity(
    @PrimaryKey val dateKey: String,
    val childUid: String,
    val totalMinutes: Long,
    val topPackage: String,
    val recordedAtEpochMs: Long
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,
    val targetPath: String,
    val payloadSummary: String,
    val retryCount: Int = 0,
    val status: String = "QUEUED",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
