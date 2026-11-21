package com.guardian360.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.guardian360.common.SecurityUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GuardianFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // --- USER PROFILE ---

    fun observeUserAccount(uid: String): Flow<UserAccount?> {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("users").document(uid)
            .snapshots()
            .map { snap -> snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    }

    suspend fun upsertUserAccount(account: UserAccount): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(account.copy(userId = uid).toCreateMap()).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("displayName", account.displayName.take(80).ifBlank { "Guardian User" })
                    account.email?.let { put("email", it.take(120)) }
                    put("role", account.role)
                    account.familyId?.let { put("familyId", it.take(128)) }
                    account.mobileNumber?.let { put("mobileNumber", it.take(32)) }
                    account.dateOfBirth?.let { put("dateOfBirth", it.take(24)) }
                    account.username?.let { put("username", it.take(48)) }
                    account.deviceName?.let { put("deviceName", it.take(80)) }
                    account.avatarUri?.let { put("avatarUri", it.take(500)) }
                    account.pinHash?.let { put("pinHash", it.take(128)) }
                    put("biometricEnabled", account.biometricEnabled)
                    put("twoFactorEnabled", account.twoFactorEnabled)
                    put("sessionVersion", account.sessionVersion.coerceAtLeast(1L))
                    put("languageCode", account.languageCode)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementSessionVersion(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "sessionVersion" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateLanguageCode(languageCode: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val safe = if (languageCode in listOf("en", "hi", "as")) languageCode else "en"
            db.collection("users").document(uid).update(
                mapOf("languageCode" to safe, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- FAMILY ---

    fun observeMyFamilies(uid: String): Flow<List<Family>> {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { it.toObjects(Family::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    }

    suspend fun ensureParentFamily(displayName: String): Result<String> {
        return try {
            val uid = requireUserId()
            val existingQuery = db.collection("families")
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get()
                .await()
            val existingFam = existingQuery.documents.firstOrNull {
                it.getString("ownerUid") == uid
            }
            if (existingFam != null) {
                return Result.success(existingFam.id)
            }
            val familyId = "fam_${uid.take(40)}"
            val family = Family(
                ownerUid = uid,
                familyName = "${displayName.substringBefore(" ").ifBlank { "My" }} Family",
                memberUids = listOf(uid),
                childUids = emptyList()
            )
            db.collection("families").document(familyId).set(family.toCreateMap()).await()
            Result.success(familyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "families")
            Result.failure(e)
        }
    }

    suspend fun updateFamilyName(familyId: String, newFamilyName: String): Result<Unit> {
        val path = "families/$familyId"
        return try {
            requireUserId()
            val cleaned = newFamilyName.trim().take(80).ifBlank { "Guardian Family" }
            db.collection("families").document(familyId).update(
                mapOf(
                    "familyName" to cleaned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(
                familyId = familyId,
                childUid = null,
                action = "FAMILY_NAME_UPDATED",
                targetPath = path,
                details = "Updated family name to $cleaned"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- PAIRING SESSIONS ---

    fun observeParentPairingSessions(parentUid: String): Flow<List<PairingSession>> {
        if (parentUid.isBlank() || auth.currentUser?.uid != parentUid) return emptyFlow()
        return db.collection("pairing_sessions")
            .whereEqualTo("parentUid", parentUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(PairingSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    fun observePairingSession(sessionId: String): Flow<PairingSession?> {
        if (sessionId.isBlank() || auth.currentUser == null) return emptyFlow()
        return db.collection("pairing_sessions").document(sessionId)
            .snapshots()
            .map { it.toObject(PairingSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    }

    suspend fun createPairingSession(familyId: String, pairingCode: String, ttlMinutes: Long = 15L): Result<String> {
        val cleanCode = SecurityUtils.normalizePairingCode(pairingCode)
        val sessionId = SecurityUtils.pairingSessionDocId(cleanCode)
        return try {
            val uid = requireUserId()
            val session = PairingSession(
                familyId = familyId,
                parentUid = uid,
                pairingCode = null,
                pairingCodeHash = SecurityUtils.sha256Hex(cleanCode),
                pairingCodeHint = SecurityUtils.maskPairingCode(cleanCode),
                status = "PENDING",
                expiresAtEpochMs = System.currentTimeMillis() + ttlMinutes * 60_000L,
                attemptCount = 0L,
                consumed = false
            )
            db.collection("pairing_sessions").document(sessionId).set(session.toCreateMap()).await()
            logAudit(
                familyId = familyId,
                childUid = null,
                action = "PAIRING_CREATED",
                targetPath = "pairing_sessions/$sessionId",
                details = "Created hashed pairing session (${session.pairingCodeHint})"
            )
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "pairing_sessions/$sessionId")
            Result.failure(e)
        }
    }

    suspend fun requestChildPairing(
        pairingCode: String,
        childName: String,
        deviceName: String,
        deviceModel: String
    ): Result<String> {
        val normalized = SecurityUtils.normalizePairingCode(pairingCode)
        val sessionId = SecurityUtils.pairingSessionDocId(normalized)
        return try {
            val uid = requireUserId()
            require(normalized.length in 6..16) { "Invalid pairing code format." }
            val docRef = db.collection("pairing_sessions").document(sessionId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("Pairing code not found or already expired."))
            }
            val session = snap.toObject(PairingSession::class.java)
                ?: return Result.failure(IllegalStateException("Malformed pairing session."))
            val expectedHash = SecurityUtils.sha256Hex(normalized)
            if (session.pairingCodeHash.isNotBlank() && session.pairingCodeHash != expectedHash) {
                return Result.failure(IllegalArgumentException("Pairing code verification failed."))
            }
            if (session.consumed || session.status != "PENDING") {
                return Result.failure(IllegalStateException("This one-time pairing code has already been used."))
            }
            if (session.expiresAtEpochMs < System.currentTimeMillis()) {
                return Result.failure(IllegalStateException("This pairing code has expired. Ask your parent for a new QR/code."))
            }
            if (session.attemptCount >= 5L) {
                return Result.failure(IllegalStateException("Rate limit exceeded for this pairing session."))
            }
            docRef.update(
                mapOf(
                    "status" to "REQUESTED",
                    "childUid" to uid,
                    "childName" to childName.take(80).ifBlank { "Child" },
                    "childDeviceName" to deviceName.take(80).ifBlank { "Android Device" },
                    "deviceModel" to deviceModel.take(80),
                    "attemptCount" to (session.attemptCount + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return Result.failure(IllegalArgumentException("Pairing code not found, expired, or already claimed."))
            }
            handleFirestoreError(e, OperationType.UPDATE, "pairing_sessions/$sessionId")
            Result.failure(e)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "pairing_sessions/$sessionId")
            Result.failure(e)
        }
    }

    suspend fun approvePairingSession(session: PairingSession): Result<Unit> {
        val childUid = session.childUid ?: return Result.failure(IllegalStateException("Missing child UID"))
        val sessionId = session.id.ifBlank { SecurityUtils.pairingSessionDocId(session.pairingCode ?: "") }
        return try {
            val parentUid = requireUserId()
            check(session.parentUid == parentUid) { "Only the issuing parent can approve pairing." }
            val familyRef = db.collection("families").document(session.familyId)
            val familySnap = familyRef.get().await()
            val currentMembers = (familySnap.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: listOf(parentUid)
            val newMembers = (currentMembers + parentUid + childUid).distinct().take(12)

            val batch = db.batch()
            batch.update(
                db.collection("pairing_sessions").document(sessionId),
                mapOf(
                    "status" to "APPROVED",
                    "consumed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                familyRef,
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(childUid),
                    "childUids" to FieldValue.arrayUnion(childUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val device = SupervisedDevice(
                parentUid = parentUid,
                childUid = childUid,
                memberUids = newMembers,
                childName = session.childName ?: "Child",
                deviceName = session.childDeviceName ?: "Android Device",
                deviceModel = session.deviceModel,
                managementLevel = "STANDARD_SUPERVISION",
                supervisionStatus = "PAIRED_ACTIVE",
                deviceOnline = true,
                batteryLevel = 100L,
                lastSeenEpochMs = System.currentTimeMillis(),
                lastPolicySyncEpochMs = System.currentTimeMillis()
            )
            batch.set(familyRef.collection("devices").document(childUid), device.toCreateMap())

            val defaultPolicy = SupervisionPolicy(
                parentUid = parentUid,
                childUid = childUid,
                memberUids = newMembers
            )
            batch.set(familyRef.collection("policies").document(childUid), defaultPolicy.toCreateMap())

            val evt = SecurityEvent(
                parentUid = parentUid,
                childUid = childUid,
                memberUids = newMembers,
                eventType = "DEVICE_PAIRED",
                severity = "INFO",
                title = "Child Device Paired",
                details = "${device.childName} (${device.deviceName}) verified and enrolled."
            )
            batch.set(familyRef.collection("security_events").document("evt_${UUID.randomUUID().toString().take(12)}"), evt.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "pairing_sessions/$sessionId")
            Result.failure(e)
        }
    }

    suspend fun rejectPairingSession(sessionId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("pairing_sessions").document(sessionId).update(
                mapOf(
                    "status" to "REJECTED",
                    "consumed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "pairing_sessions/$sessionId")
            Result.failure(e)
        }
    }

    // --- DEVICES & TELEMETRY ---

    fun observeDevices(familyId: String, uid: String): Flow<List<SupervisedDevice>> {
        if (familyId.isBlank() || uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families").document(familyId).collection("devices")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { it.toObjects(SupervisedDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    }

    suspend fun updateDeviceLockAndStrict(
        familyId: String,
        childUid: String,
        locked: Boolean,
        strictSupervision: Boolean,
        policyVersion: Long
    ): Result<Unit> {
        val path = "families/$familyId/devices/$childUid"
        return try {
            requireUserId()
            db.collection("families").document(familyId)
                .collection("devices").document(childUid)
                .update(
                    mapOf(
                        "deviceLocked" to locked,
                        "strictSupervision" to strictSupervision,
                        "policyVersion" to policyVersion,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncChildDeviceTelemetry(
        familyId: String,
        childUid: String,
        deviceName: String,
        deviceModel: String,
        supervisionStatus: String,
        online: Boolean,
        batteryLevel: Int,
        dailyMinutes: Long,
        weeklyMinutes: Long
    ): Result<Unit> {
        val path = "families/$familyId/devices/$childUid"
        return try {
            requireUserId()
            // Note: managementLevel is strictly excluded from child updates to match zero-trust Firestore rules.
            db.collection("families").document(familyId)
                .collection("devices").document(childUid)
                .update(
                    mapOf(
                        "deviceName" to deviceName.take(80),
                        "deviceModel" to deviceModel.take(80),
                        "supervisionStatus" to supervisionStatus.take(64),
                        "deviceOnline" to online,
                        "batteryLevel" to batteryLevel.toLong().coerceIn(-1L, 100L),
                        "lastSeenEpochMs" to System.currentTimeMillis(),
                        "lastPolicySyncEpochMs" to System.currentTimeMillis(),
                        "dailyScreenTimeMinutes" to dailyMinutes.coerceAtLeast(0L),
                        "weeklyScreenTimeMinutes" to weeklyMinutes.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeChildDevice(familyId: String, childUid: String): Result<Unit> {
        val path = "families/$familyId/devices/$childUid"
        return try {
            requireUserId()
            val famRef = db.collection("families").document(familyId)
            famRef.collection("devices").document(childUid).delete().await()
            runCatching { famRef.collection("policies").document(childUid).delete().await() }
            famRef.update(
                mapOf(
                    "childUids" to FieldValue.arrayRemove(childUid),
                    "memberUids" to FieldValue.arrayRemove(childUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- POLICIES ---

    fun observePolicies(familyId: String, uid: String): Flow<List<SupervisionPolicy>> {
        if (familyId.isBlank() || uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families").document(familyId).collection("policies")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { it.toObjects(SupervisionPolicy::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    }

    suspend fun savePolicy(familyId: String, policy: SupervisionPolicy): Result<Unit> {
        val path = "families/$familyId/policies/${policy.childUid}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("families").document(familyId)
                .collection("policies").document(policy.childUid)
            val snap = docRef.get().await()
            val nextVersion = policy.policyVersion + 1L
            if (!snap.exists()) {
                docRef.set(
                    policy.copy(
                        parentUid = uid,
                        policyVersion = nextVersion,
                        lastActionState = "PENDING"
                    ).toCreateMap()
                ).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("policyVersion", nextVersion)
                    put("dailyLimitMinutes", policy.dailyLimitMinutes.coerceIn(0L, 1440L))
                    put("bedtimeStart", policy.bedtimeStart.take(10))
                    put("bedtimeEnd", policy.bedtimeEnd.take(10))
                    put("schoolTimeStart", policy.schoolTimeStart.take(10))
                    put("schoolTimeEnd", policy.schoolTimeEnd.take(10))
                    put("downtimeEnabled", policy.downtimeEnabled)
                    put("emergencyBypassEnabled", policy.emergencyBypassEnabled)
                    put("bonusMinutesToday", policy.bonusMinutesToday.coerceAtLeast(0L))
                    put("maxAgeRating", policy.maxAgeRating)
                    put("youtubeMode", policy.youtubeMode)
                    put("youtubeDailyLimitMinutes", policy.youtubeDailyLimitMinutes.coerceAtLeast(0L))
                    put("whatsappDailyLimitMinutes", policy.whatsappDailyLimitMinutes.coerceAtLeast(0L))
                    put("whatsappMetaAiWebRestricted", policy.whatsappMetaAiWebRestricted)
                    put("safeSearchEnabled", policy.safeSearchEnabled)
                    put("explicitContentFilterEnabled", policy.explicitContentFilterEnabled)
                    put("dnsFilteringEnabled", policy.dnsFilteringEnabled)
                    put("playStoreApprovalRequired", policy.playStoreApprovalRequired)
                    put("iapRestricted", policy.iapRestricted)
                    put("paidAppsRestricted", policy.paidAppsRestricted)
                    put("unknownSourcesBlocked", policy.unknownSourcesBlocked)
                    put("uninstallBlocked", policy.uninstallBlocked)
                    put("usbDebuggingBlocked", policy.usbDebuggingBlocked)
                    put("blockedCategories", policy.blockedCategories.distinct().take(30))
                    put("blockedPackages", policy.blockedPackages.distinct().take(150))
                    put("allowedPackages", policy.allowedPackages.distinct().take(150))
                    put("blockedDomains", policy.blockedDomains.distinct().take(150))
                    put("allowedDomains", policy.allowedDomains.distinct().take(150))
                    put("homeLat", policy.homeLat)
                    put("homeLng", policy.homeLng)
                    put("homeRadiusMeters", policy.homeRadiusMeters.coerceAtLeast(20L))
                    put("schoolLat", policy.schoolLat)
                    put("schoolLng", policy.schoolLng)
                    put("schoolRadiusMeters", policy.schoolRadiusMeters.coerceAtLeast(20L))
                    put("locationSharingEnabled", policy.locationSharingEnabled)
                    put("retentionDays", policy.retentionDays.coerceIn(1L, 365L))
                    put("lastActionState", policy.lastActionState)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgePolicyState(familyId: String, childUid: String, state: String): Result<Unit> {
        val path = "families/$familyId/policies/$childUid"
        return try {
            requireUserId()
            db.collection("families").document(familyId)
                .collection("policies").document(childUid)
                .update(mapOf("lastActionState" to state, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- APPROVALS ---

    fun observeApprovals(familyId: String, uid: String): Flow<List<ApprovalRequest>> {
        if (familyId.isBlank() || uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families").document(familyId).collection("approvals")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ApprovalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun submitApprovalRequest(familyId: String, request: ApprovalRequest): Result<Unit> {
        val reqId = "req_${UUID.randomUUID().toString().take(12)}"
        val path = "families/$familyId/approvals/$reqId"
        return try {
            val uid = requireUserId()
            db.collection("families").document(familyId).collection("approvals").document(reqId)
                .set(request.copy(childUid = uid, status = "PENDING").toCreateMap()).await()

            val notifId = "notif_${UUID.randomUUID().toString().take(12)}"
            val notif = FamilyNotification(
                parentUid = request.parentUid,
                childUid = uid,
                memberUids = request.memberUids,
                recipientUid = request.parentUid,
                category = request.requestType,
                title = "Approval Requested by ${request.childName}",
                body = request.targetLabel ?: request.targetDomain ?: "${request.requestedMinutes ?: 15} min extra time"
            )
            db.collection("families").document(familyId).collection("notifications").document(notifId)
                .set(notif.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun decideApprovalRequest(
        familyId: String,
        request: ApprovalRequest,
        approved: Boolean,
        durationMinutes: Long? = null
    ): Result<Unit> {
        val path = "families/$familyId/approvals/${request.id}"
        return try {
            val uid = requireUserId()
            val status = if (approved) "APPROVED" else "REJECTED"
            val expiresAt = if (approved && durationMinutes != null && durationMinutes > 0) {
                System.currentTimeMillis() + durationMinutes * 60_000L
            } else {
                System.currentTimeMillis() + 3600_000L
            }
            db.collection("families").document(familyId)
                .collection("approvals").document(request.id)
                .update(
                    mapOf(
                        "status" to status,
                        "decidedByUid" to uid,
                        "expiresAtEpochMs" to expiresAt,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            val notifId = "notif_${UUID.randomUUID().toString().take(12)}"
            val notif = FamilyNotification(
                parentUid = request.parentUid,
                childUid = request.childUid,
                memberUids = request.memberUids,
                recipientUid = request.childUid,
                category = "APPROVAL_DECISION",
                title = "Request $status",
                body = "Parent $status ${request.targetLabel ?: request.requestType}."
            )
            db.collection("families").document(familyId).collection("notifications").document(notifId)
                .set(notif.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- SECURITY EVENTS & NOTIFICATIONS ---

    fun observeSecurityEvents(familyId: String, uid: String): Flow<List<SecurityEvent>> {
        if (familyId.isBlank() || uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families").document(familyId).collection("security_events")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(SecurityEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun logSecurityEvent(familyId: String, event: SecurityEvent): Result<Unit> {
        val eventId = "evt_${UUID.randomUUID().toString().take(12)}"
        return try {
            requireUserId()
            db.collection("families").document(familyId)
                .collection("security_events").document(eventId)
                .set(event.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "families/$familyId/security_events/$eventId")
            Result.failure(e)
        }
    }

    suspend fun acknowledgeSecurityEvent(familyId: String, eventId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("families").document(familyId)
                .collection("security_events").document(eventId)
                .update(mapOf("acknowledged" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/security_events/$eventId")
            Result.failure(e)
        }
    }

    fun observeNotifications(familyId: String, uid: String): Flow<List<FamilyNotification>> {
        if (familyId.isBlank() || uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families").document(familyId).collection("notifications")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FamilyNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun markNotificationRead(familyId: String, notificationId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("families").document(familyId)
                .collection("notifications").document(notificationId)
                .update(mapOf("readFlag" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/notifications/$notificationId")
            Result.failure(e)
        }
    }

    // --- LOCATIONS ---

    fun observeLocations(familyId: String, uid: String): Flow<List<LocationUpdate>> {
        if (familyId.isBlank() || uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families").document(familyId).collection("locations")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(LocationUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.capturedAtEpochMs }
            }
    }

    suspend fun publishLocation(familyId: String, location: LocationUpdate): Result<Unit> {
        val locId = "loc_${UUID.randomUUID().toString().take(12)}"
        return try {
            val uid = requireUserId()
            db.collection("families").document(familyId)
                .collection("locations").document(locId)
                .set(location.copy(childUid = uid).toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "families/$familyId/locations/$locId")
            Result.failure(e)
        }
    }

    // --- APP USAGE & INVENTORY ---

    fun observeAppUsage(familyId: String, uid: String): Flow<List<AppUsageEntry>> {
        if (familyId.isBlank() || uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families").document(familyId).collection("app_usage")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppUsageEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.minutesToday }
            }
    }

    suspend fun syncAppUsageBatch(familyId: String, entries: List<AppUsageEntry>): Result<Unit> {
        return try {
            val uid = requireUserId()
            if (entries.isEmpty()) return Result.success(Unit)
            val usageCollection = db.collection("families").document(familyId).collection("app_usage")
            val existingIds = usageCollection
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
                .documents
                .map { it.id }
                .toSet()

            val batch = db.batch()
            entries.take(60).forEach { entry ->
                val docId = "${entry.childUid}_${entry.packageName}".replace(".", "_").take(120)
                val ref = usageCollection.document(docId)
                if (docId in existingIds) {
                    val updates = buildMap<String, Any> {
                        put("appLabel", entry.appLabel.take(120).ifBlank { entry.packageName })
                        put("category", entry.category.take(64))
                        entry.ageRating?.let { put("ageRating", it.take(20)) }
                        put("minutesToday", entry.minutesToday.coerceAtLeast(0L))
                        put("minutesWeek", entry.minutesWeek.coerceAtLeast(0L))
                        put("blocked", entry.blocked)
                        put("exceptionAllowed", entry.exceptionAllowed)
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.update(ref, updates)
                } else {
                    batch.set(ref, entry.toCreateMap())
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "families/$familyId/app_usage")
            Result.failure(e)
        }
    }

    // --- AUDIT LOGS, POLICY VERSIONS & HEARTBEATS ---

    fun observeAuditLogs(familyId: String, parentUid: String): Flow<List<AuditLogEntry>> {
        if (familyId.isBlank() || parentUid.isBlank() || auth.currentUser?.uid != parentUid) return emptyFlow()
        return db.collection("families").document(familyId).collection("audit_logs")
            .whereEqualTo("parentUid", parentUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLogEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun logAudit(
        familyId: String,
        childUid: String?,
        action: String,
        targetPath: String,
        details: String
    ): Result<Unit> {
        val logId = "audit_${UUID.randomUUID().toString().take(12)}"
        return try {
            val uid = requireUserId()
            val entry = AuditLogEntry(
                parentUid = uid,
                actorUid = uid,
                childUid = childUid,
                action = action,
                targetPath = targetPath,
                details = details
            )
            db.collection("families").document(familyId)
                .collection("audit_logs").document(logId)
                .set(entry.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePolicyVersions(familyId: String, uid: String): Flow<List<PolicyVersionRecord>> {
        if (familyId.isBlank() || uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("families").document(familyId).collection("policy_versions")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(PolicyVersionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.policyVersion }
            }
    }

    suspend fun recordPolicyVersion(
        familyId: String,
        childUid: String,
        memberUids: List<String>,
        policyVersion: Long,
        executionStatus: String,
        summary: String
    ): Result<Unit> {
        val docId = "pv_${childUid.take(16)}_${policyVersion}_${UUID.randomUUID().toString().take(6)}"
        return try {
            val uid = requireUserId()
            val rec = PolicyVersionRecord(
                parentUid = uid,
                childUid = childUid,
                memberUids = memberUids,
                policyVersion = policyVersion,
                executionStatus = executionStatus,
                summary = summary
            )
            db.collection("families").document(familyId)
                .collection("policy_versions").document(docId)
                .set(rec.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordHeartbeat(
        familyId: String,
        parentUid: String,
        memberUids: List<String>,
        online: Boolean,
        batteryLevel: Int
    ): Result<Unit> {
        val docId = "hb_${UUID.randomUUID().toString().take(14)}"
        return try {
            val uid = requireUserId()
            val hb = DeviceHeartbeat(
                parentUid = parentUid,
                childUid = uid,
                memberUids = memberUids,
                online = online,
                batteryLevel = batteryLevel.toLong().coerceIn(-1L, 100L),
                recordedAtEpochMs = System.currentTimeMillis()
            )
            db.collection("families").document(familyId)
                .collection("device_heartbeats").document(docId)
                .set(hb.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
