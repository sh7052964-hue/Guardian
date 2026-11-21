package com.guardian360.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val role: String = "UNSET",
    val familyId: String? = null,
    val mobileNumber: String? = null,
    val dateOfBirth: String? = null,
    val username: String? = null,
    val deviceName: String? = null,
    val avatarUri: String? = null,
    val pinHash: String? = null,
    val biometricEnabled: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    val sessionVersion: Long = 1L,
    val languageCode: String = "en",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(80).ifBlank { "Guardian User" },
        "email" to email?.take(120),
        "role" to role,
        "familyId" to familyId?.take(128),
        "mobileNumber" to mobileNumber?.take(32),
        "dateOfBirth" to dateOfBirth?.take(24),
        "username" to username?.take(48),
        "deviceName" to deviceName?.take(80),
        "avatarUri" to avatarUri?.take(500),
        "pinHash" to pinHash?.take(128),
        "biometricEnabled" to biometricEnabled,
        "twoFactorEnabled" to twoFactorEnabled,
        "sessionVersion" to sessionVersion.coerceAtLeast(1L),
        "languageCode" to if (languageCode in listOf("en", "hi", "as")) languageCode else "en",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class Family(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val familyName: String = "",
    val memberUids: List<String> = emptyList(),
    val childUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "ownerUid" to ownerUid,
        "familyName" to familyName.take(80).ifBlank { "Family Safety" },
        "memberUids" to memberUids.distinct().take(12),
        "childUids" to childUids.distinct().take(10),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PairingSession(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val parentUid: String = "",
    val pairingCode: String? = null,
    val pairingCodeHash: String = "",
    val pairingCodeHint: String = "",
    val status: String = "PENDING",
    val childUid: String? = null,
    val childName: String? = null,
    val childDeviceName: String? = null,
    val deviceModel: String? = null,
    val expiresAtEpochMs: Long = 0L,
    val attemptCount: Long = 0L,
    val consumed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "familyId" to familyId,
        "parentUid" to parentUid,
        "pairingCode" to pairingCode?.take(24),
        "pairingCodeHash" to pairingCodeHash.take(128).ifBlank { "unhashed" },
        "pairingCodeHint" to pairingCodeHint.take(24).ifBlank { "••••" },
        "status" to status,
        "childUid" to childUid,
        "childName" to childName,
        "childDeviceName" to childDeviceName,
        "deviceModel" to deviceModel,
        "expiresAtEpochMs" to expiresAtEpochMs,
        "attemptCount" to attemptCount,
        "consumed" to consumed,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class SupervisedDevice(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val childName: String = "",
    val childEmail: String? = null,
    val dateOfBirth: String? = null,
    val deviceName: String = "",
    val deviceModel: String? = null,
    val managementLevel: String = "STANDARD_SUPERVISION",
    val supervisionStatus: String = "ACTIVE",
    val deviceOnline: Boolean = true,
    val batteryLevel: Long = -1L,
    val lastSeenEpochMs: Long = 0L,
    val lastPolicySyncEpochMs: Long = 0L,
    val dailyScreenTimeMinutes: Long = 0L,
    val weeklyScreenTimeMinutes: Long = 0L,
    val deviceLocked: Boolean = false,
    val strictSupervision: Boolean = false,
    val policyVersion: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "childName" to childName.take(80).ifBlank { "Child" },
        "childEmail" to childEmail?.take(120),
        "dateOfBirth" to dateOfBirth?.take(24),
        "deviceName" to deviceName.take(80).ifBlank { "Android Device" },
        "deviceModel" to deviceModel?.take(80),
        "managementLevel" to managementLevel,
        "supervisionStatus" to supervisionStatus.take(64),
        "deviceOnline" to deviceOnline,
        "batteryLevel" to batteryLevel.coerceIn(-1L, 100L),
        "lastSeenEpochMs" to lastSeenEpochMs,
        "lastPolicySyncEpochMs" to lastPolicySyncEpochMs,
        "dailyScreenTimeMinutes" to dailyScreenTimeMinutes.coerceAtLeast(0L),
        "weeklyScreenTimeMinutes" to weeklyScreenTimeMinutes.coerceAtLeast(0L),
        "deviceLocked" to deviceLocked,
        "strictSupervision" to strictSupervision,
        "policyVersion" to policyVersion.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class SupervisionPolicy(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val policyVersion: Long = 1L,
    val dailyLimitMinutes: Long = 120L,
    val bedtimeStart: String = "21:30",
    val bedtimeEnd: String = "07:00",
    val schoolTimeStart: String = "08:30",
    val schoolTimeEnd: String = "14:30",
    val downtimeEnabled: Boolean = true,
    val emergencyBypassEnabled: Boolean = true,
    val bonusMinutesToday: Long = 0L,
    val maxAgeRating: String = "AGE_12",
    val youtubeMode: String = "SHORTS_RESTRICTION",
    val youtubeDailyLimitMinutes: Long = 30L,
    val whatsappDailyLimitMinutes: Long = 30L,
    val whatsappMetaAiWebRestricted: Boolean = true,
    val safeSearchEnabled: Boolean = true,
    val explicitContentFilterEnabled: Boolean = true,
    val dnsFilteringEnabled: Boolean = true,
    val playStoreApprovalRequired: Boolean = true,
    val iapRestricted: Boolean = true,
    val paidAppsRestricted: Boolean = true,
    val unknownSourcesBlocked: Boolean = true,
    val uninstallBlocked: Boolean = true,
    val usbDebuggingBlocked: Boolean = true,
    val blockedCategories: List<String> = listOf("DATING", "ADULT_CONTENT"),
    val blockedPackages: List<String> = emptyList(),
    val allowedPackages: List<String> = emptyList(),
    val blockedDomains: List<String> = listOf("shorts.youtube.com", "ai.meta.com", "meta.ai"),
    val allowedDomains: List<String> = listOf("khanacademy.org", "wikipedia.org"),
    val homeLat: Double = 0.0,
    val homeLng: Double = 0.0,
    val homeRadiusMeters: Long = 150L,
    val schoolLat: Double = 0.0,
    val schoolLng: Double = 0.0,
    val schoolRadiusMeters: Long = 200L,
    val locationSharingEnabled: Boolean = true,
    val retentionDays: Long = 30L,
    val lastActionState: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "policyVersion" to policyVersion.coerceAtLeast(1L),
        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(0L, 1440L),
        "bedtimeStart" to bedtimeStart.take(10),
        "bedtimeEnd" to bedtimeEnd.take(10),
        "schoolTimeStart" to schoolTimeStart.take(10),
        "schoolTimeEnd" to schoolTimeEnd.take(10),
        "downtimeEnabled" to downtimeEnabled,
        "emergencyBypassEnabled" to emergencyBypassEnabled,
        "bonusMinutesToday" to bonusMinutesToday.coerceAtLeast(0L),
        "maxAgeRating" to maxAgeRating,
        "youtubeMode" to youtubeMode,
        "youtubeDailyLimitMinutes" to youtubeDailyLimitMinutes.coerceAtLeast(0L),
        "whatsappDailyLimitMinutes" to whatsappDailyLimitMinutes.coerceAtLeast(0L),
        "whatsappMetaAiWebRestricted" to whatsappMetaAiWebRestricted,
        "safeSearchEnabled" to safeSearchEnabled,
        "explicitContentFilterEnabled" to explicitContentFilterEnabled,
        "dnsFilteringEnabled" to dnsFilteringEnabled,
        "playStoreApprovalRequired" to playStoreApprovalRequired,
        "iapRestricted" to iapRestricted,
        "paidAppsRestricted" to paidAppsRestricted,
        "unknownSourcesBlocked" to unknownSourcesBlocked,
        "uninstallBlocked" to uninstallBlocked,
        "usbDebuggingBlocked" to usbDebuggingBlocked,
        "blockedCategories" to blockedCategories.distinct().take(30),
        "blockedPackages" to blockedPackages.distinct().take(150),
        "allowedPackages" to allowedPackages.distinct().take(150),
        "blockedDomains" to blockedDomains.distinct().take(150),
        "allowedDomains" to allowedDomains.distinct().take(150),
        "homeLat" to homeLat,
        "homeLng" to homeLng,
        "homeRadiusMeters" to homeRadiusMeters.coerceAtLeast(20L),
        "schoolLat" to schoolLat,
        "schoolLng" to schoolLng,
        "schoolRadiusMeters" to schoolRadiusMeters.coerceAtLeast(20L),
        "locationSharingEnabled" to locationSharingEnabled,
        "retentionDays" to retentionDays.coerceIn(1L, 365L),
        "lastActionState" to lastActionState,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class ApprovalRequest(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val childName: String = "",
    val requestType: String = "EXTRA_TIME",
    val targetPackage: String? = null,
    val targetLabel: String? = null,
    val targetDomain: String? = null,
    val requestedMinutes: Long? = null,
    val reason: String? = null,
    val status: String = "PENDING",
    val expiresAtEpochMs: Long? = null,
    val decidedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "childName" to childName.take(80).ifBlank { "Child" },
        "requestType" to requestType,
        "targetPackage" to targetPackage?.take(160),
        "targetLabel" to targetLabel?.take(120),
        "targetDomain" to targetDomain?.take(200),
        "requestedMinutes" to requestedMinutes?.coerceIn(0L, 1440L),
        "reason" to reason?.take(300),
        "status" to "PENDING",
        "expiresAtEpochMs" to expiresAtEpochMs,
        "decidedByUid" to decidedByUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class SecurityEvent(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val eventType: String = "",
    val severity: String = "INFO",
    val title: String = "",
    val details: String? = null,
    val packageName: String? = null,
    val acknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "eventType" to eventType.take(64).ifBlank { "POLICY_EVENT" },
        "severity" to severity,
        "title" to title.take(120).ifBlank { "Security Event" },
        "details" to details?.take(400),
        "packageName" to packageName?.take(160),
        "acknowledged" to acknowledged,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FamilyNotification(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val recipientUid: String = "",
    val category: String = "",
    val title: String = "",
    val body: String = "",
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "recipientUid" to recipientUid,
        "category" to category.take(64).ifBlank { "SYSTEM" },
        "title" to title.take(120).ifBlank { "Guardian360 Alert" },
        "body" to body.take(300).ifBlank { "Update received." },
        "readFlag" to readFlag,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class LocationUpdate(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double? = null,
    val placeLabel: String? = null,
    val eventKind: String = "UPDATE",
    val sharingActive: Boolean = true,
    val capturedAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "latitude" to latitude.coerceIn(-90.0, 90.0),
        "longitude" to longitude.coerceIn(-180.0, 180.0),
        "accuracyMeters" to accuracyMeters,
        "placeLabel" to placeLabel?.take(80),
        "eventKind" to eventKind,
        "sharingActive" to sharingActive,
        "capturedAtEpochMs" to capturedAtEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class AppUsageEntry(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val packageName: String = "",
    val appLabel: String = "",
    val category: String = "UNKNOWN",
    val ageRating: String? = null,
    val minutesToday: Long = 0L,
    val minutesWeek: Long = 0L,
    val blocked: Boolean = false,
    val exceptionAllowed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "packageName" to packageName.take(160),
        "appLabel" to appLabel.take(120).ifBlank { packageName },
        "category" to category.take(64),
        "ageRating" to ageRating?.take(20),
        "minutesToday" to minutesToday.coerceAtLeast(0L),
        "minutesWeek" to minutesWeek.coerceAtLeast(0L),
        "blocked" to blocked,
        "exceptionAllowed" to exceptionAllowed,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class AuditLogEntry(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val actorUid: String = "",
    val childUid: String? = null,
    val action: String = "",
    val targetPath: String = "",
    val details: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "actorUid" to actorUid,
        "childUid" to childUid?.take(128),
        "action" to action.take(80).ifBlank { "ACTION" },
        "targetPath" to targetPath.take(200).ifBlank { "families" },
        "details" to details?.take(400),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class DeviceHeartbeat(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val online: Boolean = true,
    val batteryLevel: Long = -1L,
    val recordedAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "online" to online,
        "batteryLevel" to batteryLevel.coerceIn(-1L, 100L),
        "recordedAtEpochMs" to recordedAtEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PolicyVersionRecord(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val memberUids: List<String> = emptyList(),
    val policyVersion: Long = 1L,
    val executionStatus: String = "PENDING",
    val summary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "parentUid" to parentUid,
        "childUid" to childUid,
        "memberUids" to memberUids.distinct().take(12),
        "policyVersion" to policyVersion.coerceAtLeast(1L),
        "executionStatus" to executionStatus.take(64).ifBlank { "PENDING" },
        "summary" to summary?.take(300),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}
