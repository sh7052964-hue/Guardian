"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.deleteAccountData = exports.revokeDevice = exports.submitSecurityEvent = exports.submitDeviceHeartbeat = exports.rejectRequest = exports.approveRequest = exports.updateScreenTimePolicy = exports.updateAppPolicy = exports.approvePairing = exports.claimPairingSession = exports.createPairingSession = void 0;
const https_1 = require("firebase-functions/v2/https");
const logger = __importStar(require("firebase-functions/logger"));
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-admin/firestore");
const validation_1 = require("./validation");
if (!admin.apps.length) {
    admin.initializeApp();
}
const FIRESTORE_DATABASE_ID = process.env.FIRESTORE_DATABASE_ID ||
    "ai-studio-android-guardian-f84579d4-c1bb-4789-935a-1688dbc2791b";
const db = (0, firestore_1.getFirestore)(FIRESTORE_DATABASE_ID);
const REGION = "us-central1";
const PAIRING_TTL_MS = 15 * 60 * 1000;
function requireAuthUid(request) {
    const uid = request.auth?.uid;
    if (!uid) {
        throw new https_1.HttpsError("unauthenticated", "Authentication is required.");
    }
    return uid;
}
async function getUserRole(uid) {
    const snap = await db.collection("users").doc(uid).get();
    if (!snap.exists) {
        throw new https_1.HttpsError("failed-precondition", "User profile not found.");
    }
    return snap.get("role") ?? "UNSET";
}
async function requireParentAndFamily(callerUid, familyId) {
    const role = await getUserRole(callerUid);
    const famSnap = await db.collection("families").doc(familyId).get();
    if (!famSnap.exists) {
        throw new https_1.HttpsError("not-found", "Family not found.");
    }
    const ownerUid = famSnap.get("ownerUid");
    try {
        (0, validation_1.assertParentOwnership)(callerUid, role, ownerUid);
    }
    catch (err) {
        throw new https_1.HttpsError("permission-denied", err.message);
    }
    return famSnap;
}
async function enforceRateLimit(bucketId, windowMs, maxRequests) {
    const ref = db.collection("_server_rate_limits").doc(bucketId);
    const nowMs = Date.now();
    await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const prev = snap.exists
            ? {
                windowStartMs: snap.get("windowStartMs"),
                count: snap.get("count"),
            }
            : undefined;
        const result = (0, validation_1.checkSlidingWindowRateLimit)(prev, nowMs, windowMs, maxRequests);
        if (!result.allowed) {
            throw new https_1.HttpsError("resource-exhausted", "Too many requests. Please wait and retry.");
        }
        tx.set(ref, {
            ...result.nextState,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
    });
}
async function writeAuditLog(params) {
    const now = admin.firestore.Timestamp.now();
    await db
        .collection("families")
        .doc(params.familyId)
        .collection("audit_logs")
        .add({
        parentUid: params.parentUid,
        actorUid: params.actorUid,
        childUid: params.childUid ?? "",
        action: params.action,
        targetPath: params.targetPath,
        details: params.details.slice(0, 400),
        createdAt: now,
        updatedAt: now,
    });
}
async function tryDispatchFcm(params) {
    try {
        const tokenSnap = await db.collection("user_push_tokens").doc(params.targetUid).get();
        const token = tokenSnap.exists ? tokenSnap.get("fcmToken") : undefined;
        if (!token)
            return;
        const payload = (0, validation_1.buildFcmNotificationPayload)({
            token,
            title: params.title,
            body: params.body,
            category: params.category,
            familyId: params.familyId,
            childUid: params.childUid,
            silentSync: params.silentSync,
        });
        await admin.messaging().send(payload);
    }
    catch (err) {
        logger.warn("FCM dispatch skipped or failed", {
            targetUid: params.targetUid,
            error: err.message,
        });
    }
}
exports.createPairingSession = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const callerUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const familyId = (0, validation_1.validateStringBounded)(data.familyId, "familyId", 1, 128);
    await requireParentAndFamily(callerUid, familyId);
    await enforceRateLimit(`pairing_create_${callerUid}`, 60_000, 10);
    const code = (0, validation_1.generatePairingCode)(8);
    const sessionId = (0, validation_1.pairingSessionDocId)(code);
    const nowMs = Date.now();
    const expiresAtEpochMs = nowMs + PAIRING_TTL_MS;
    const nowTs = admin.firestore.Timestamp.now();
    await db.collection("pairing_sessions").doc(sessionId).set({
        familyId,
        parentUid: callerUid,
        pairingCodeHash: (0, validation_1.sha256Hex)(code),
        pairingCodeHint: (0, validation_1.maskPairingCode)(code),
        status: "PENDING",
        attemptCount: 0,
        consumed: false,
        expiresAtEpochMs,
        createdAt: nowTs,
        updatedAt: nowTs,
    });
    await writeAuditLog({
        familyId,
        parentUid: callerUid,
        actorUid: callerUid,
        action: "PAIRING_SESSION_CREATED",
        targetPath: `pairing_sessions/${sessionId}`,
        details: `Created hashed pairing session (${(0, validation_1.maskPairingCode)(code)})`,
    });
    return {
        sessionId,
        pairingCode: code,
        pairingCodeHint: (0, validation_1.maskPairingCode)(code),
        expiresAtEpochMs,
    };
});
exports.claimPairingSession = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const childUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const rawCode = (0, validation_1.validateStringBounded)(data.pairingCode, "pairingCode", 4, 24);
    const childName = (0, validation_1.validateStringBounded)(data.childName, "childName", 1, 80);
    const childDeviceName = (0, validation_1.validateStringBounded)(data.childDeviceName, "childDeviceName", 1, 80);
    const deviceModel = (0, validation_1.validateStringBounded)(data.deviceModel ?? "Android Device", "deviceModel", 1, 80);
    await enforceRateLimit(`pairing_claim_${childUid}`, 60_000, 8);
    const normalized = (0, validation_1.normalizeCode)(rawCode);
    const sessionId = (0, validation_1.pairingSessionDocId)(normalized);
    const codeHash = (0, validation_1.sha256Hex)(normalized);
    const sessionRef = db.collection("pairing_sessions").doc(sessionId);
    let parentUid = "";
    let familyId = "";
    await db.runTransaction(async (tx) => {
        const snap = await tx.get(sessionRef);
        if (!snap.exists) {
            throw new https_1.HttpsError("not-found", "Pairing session not found.");
        }
        const status = snap.get("status");
        const consumed = snap.get("consumed");
        const expiresAt = snap.get("expiresAtEpochMs");
        const storedHash = snap.get("pairingCodeHash");
        const attemptCount = snap.get("attemptCount") ?? 0;
        if (consumed || status !== "PENDING") {
            throw new https_1.HttpsError("failed-precondition", "Pairing session is no longer available.");
        }
        if (Date.now() > expiresAt) {
            tx.update(sessionRef, {
                status: "EXPIRED",
                consumed: true,
                updatedAt: admin.firestore.Timestamp.now(),
            });
            throw new https_1.HttpsError("deadline-exceeded", "Pairing code has expired.");
        }
        if (attemptCount >= 10) {
            throw new https_1.HttpsError("resource-exhausted", "Maximum pairing attempts exceeded.");
        }
        if (storedHash && storedHash !== codeHash) {
            tx.update(sessionRef, {
                attemptCount: attemptCount + 1,
                updatedAt: admin.firestore.Timestamp.now(),
            });
            throw new https_1.HttpsError("permission-denied", "Pairing verification failed.");
        }
        parentUid = snap.get("parentUid");
        familyId = snap.get("familyId");
        tx.update(sessionRef, {
            status: "REQUESTED",
            childUid,
            childName,
            childDeviceName,
            deviceModel,
            attemptCount: attemptCount + 1,
            updatedAt: admin.firestore.Timestamp.now(),
        });
    });
    await tryDispatchFcm({
        targetUid: parentUid,
        title: "Child Pairing Request",
        body: `${childName} (${childDeviceName}) requested device supervision pairing.`,
        category: "PAIRING_REQUEST",
        familyId,
        childUid,
    });
    return { sessionId, familyId, status: "REQUESTED" };
});
exports.approvePairing = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const parentUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const sessionId = (0, validation_1.validateStringBounded)(data.sessionId, "sessionId", 1, 128);
    const approve = data.approve !== false;
    const sessionRef = db.collection("pairing_sessions").doc(sessionId);
    const nowTs = admin.firestore.Timestamp.now();
    let familyId = "";
    let childUid = "";
    let childName = "";
    let childDeviceName = "";
    let deviceModel = "";
    await db.runTransaction(async (tx) => {
        const snap = await tx.get(sessionRef);
        if (!snap.exists) {
            throw new https_1.HttpsError("not-found", "Pairing session not found.");
        }
        if (snap.get("parentUid") !== parentUid) {
            throw new https_1.HttpsError("permission-denied", "Only the owning parent can verify pairing.");
        }
        if (snap.get("consumed") === true || snap.get("status") !== "REQUESTED") {
            throw new https_1.HttpsError("failed-precondition", "Pairing session is not pending approval.");
        }
        familyId = snap.get("familyId");
        childUid = snap.get("childUid");
        childName = snap.get("childName") || "Child";
        childDeviceName = snap.get("childDeviceName") || "Android Device";
        deviceModel = snap.get("deviceModel") || "Android";
        if (!approve) {
            tx.update(sessionRef, {
                status: "REJECTED",
                consumed: true,
                updatedAt: nowTs,
            });
            return;
        }
        const famRef = db.collection("families").doc(familyId);
        const famSnap = await tx.get(famRef);
        if (!famSnap.exists) {
            throw new https_1.HttpsError("not-found", "Family not found.");
        }
        const members = Array.from(new Set([...(famSnap.get("memberUids") ?? []), parentUid, childUid]));
        const children = Array.from(new Set([...(famSnap.get("childUids") ?? []), childUid]));
        tx.update(sessionRef, {
            status: "APPROVED",
            consumed: true,
            updatedAt: nowTs,
        });
        tx.update(famRef, {
            memberUids: members,
            childUids: children,
            updatedAt: nowTs,
        });
        const devRef = famRef.collection("devices").doc(childUid);
        tx.set(devRef, {
            parentUid,
            childUid,
            memberUids: members,
            childName,
            deviceName: childDeviceName,
            deviceModel,
            managementLevel: "STANDARD_SUPERVISION",
            supervisionStatus: "ACTIVE",
            deviceOnline: true,
            batteryLevel: 100,
            dailyScreenTimeMinutes: 0,
            weeklyScreenTimeMinutes: 0,
            deviceLocked: false,
            strictSupervision: false,
            policyVersion: 1,
            lastSeenEpochMs: Date.now(),
            lastPolicySyncEpochMs: Date.now(),
            createdAt: nowTs,
            updatedAt: nowTs,
        });
        const policyRef = famRef.collection("policies").doc(childUid);
        tx.set(policyRef, {
            parentUid,
            childUid,
            memberUids: members,
            policyVersion: 1,
            dailyLimitMinutes: 120,
            bedtimeStart: "21:30",
            bedtimeEnd: "06:30",
            schoolTimeStart: "08:00",
            schoolTimeEnd: "14:00",
            downtimeEnabled: true,
            emergencyBypassEnabled: false,
            bonusMinutesToday: 0,
            maxAgeRating: "AGE_12",
            youtubeMode: "SHORTS_RESTRICTION",
            youtubeDailyLimitMinutes: 45,
            whatsappDailyLimitMinutes: 60,
            whatsappMetaAiWebRestricted: true,
            safeSearchEnabled: true,
            explicitContentFilterEnabled: true,
            dnsFilteringEnabled: true,
            playStoreApprovalRequired: true,
            iapRestricted: true,
            paidAppsRestricted: true,
            unknownSourcesBlocked: true,
            uninstallBlocked: true,
            usbDebuggingBlocked: true,
            blockedCategories: ["DATING", "ADULT_CONTENT", "GAMBLING"],
            blockedPackages: [],
            allowedPackages: [],
            blockedDomains: ["youtube.com/shorts", "m.youtube.com/shorts"],
            allowedDomains: ["khanacademy.org", "wikipedia.org"],
            locationSharingEnabled: true,
            retentionDays: 30,
            lastActionState: "PENDING",
            createdAt: nowTs,
            updatedAt: nowTs,
        });
    });
    if (familyId) {
        await writeAuditLog({
            familyId,
            parentUid,
            actorUid: parentUid,
            childUid,
            action: approve ? "PAIRING_APPROVED" : "PAIRING_REJECTED",
            targetPath: `pairing_sessions/${sessionId}`,
            details: `${approve ? "Approved" : "Rejected"} child ${childName} (${childDeviceName})`,
        });
    }
    return {
        sessionId,
        familyId,
        childUid,
        status: approve ? "APPROVED" : "REJECTED",
    };
});
exports.updateAppPolicy = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const parentUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const familyId = (0, validation_1.validateStringBounded)(data.familyId, "familyId", 1, 128);
    const childUid = (0, validation_1.validateStringBounded)(data.childUid, "childUid", 1, 128);
    await requireParentAndFamily(parentUid, familyId);
    const policyRef = db.collection("families").doc(familyId).collection("policies").doc(childUid);
    const devRef = db.collection("families").doc(familyId).collection("devices").doc(childUid);
    const nowTs = admin.firestore.Timestamp.now();
    let nextVersion = 1;
    await db.runTransaction(async (tx) => {
        const snap = await tx.get(policyRef);
        if (!snap.exists) {
            throw new https_1.HttpsError("not-found", "Child policy not found.");
        }
        const currentVersion = snap.get("policyVersion") ?? 1;
        nextVersion = currentVersion + 1;
        const members = snap.get("memberUids") ?? [parentUid, childUid];
        const blockedPackages = data.blockedPackages !== undefined
            ? (0, validation_1.sanitizeStringArray)(data.blockedPackages, 150, 160)
            : (snap.get("blockedPackages") ?? []);
        const allowedPackages = data.allowedPackages !== undefined
            ? (0, validation_1.sanitizeStringArray)(data.allowedPackages, 150, 160)
            : (snap.get("allowedPackages") ?? []);
        const blockedCategories = data.blockedCategories !== undefined
            ? (0, validation_1.sanitizeStringArray)(data.blockedCategories, 30, 64)
            : (snap.get("blockedCategories") ?? []);
        tx.update(policyRef, {
            blockedPackages,
            allowedPackages,
            blockedCategories,
            policyVersion: nextVersion,
            lastActionState: "PENDING",
            updatedAt: nowTs,
        });
        tx.update(devRef, { policyVersion: nextVersion, updatedAt: nowTs });
        tx.set(db.collection("families").doc(familyId).collection("policy_versions").doc(`v_${childUid}_${nextVersion}`), {
            parentUid,
            childUid,
            memberUids: members,
            policyVersion: nextVersion,
            executionStatus: "PENDING",
            summary: `App policy updated (${blockedPackages.length} blocked apps, ${blockedCategories.length} categories)`,
            createdAt: nowTs,
            updatedAt: nowTs,
        });
    });
    await writeAuditLog({
        familyId,
        parentUid,
        actorUid: parentUid,
        childUid,
        action: "APP_POLICY_UPDATED",
        targetPath: `families/${familyId}/policies/${childUid}`,
        details: `Updated app policy to v${nextVersion}`,
    });
    await tryDispatchFcm({
        targetUid: childUid,
        title: "Policy Sync",
        body: "Updated app policy available.",
        category: "POLICY_SYNC",
        familyId,
        childUid,
        silentSync: true,
    });
    return { policyVersion: nextVersion, status: "PENDING" };
});
exports.updateScreenTimePolicy = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const parentUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const familyId = (0, validation_1.validateStringBounded)(data.familyId, "familyId", 1, 128);
    const childUid = (0, validation_1.validateStringBounded)(data.childUid, "childUid", 1, 128);
    const dailyLimitMinutes = (0, validation_1.validateIntBounded)(data.dailyLimitMinutes, "dailyLimitMinutes", 0, 1440);
    const bedtimeStart = (0, validation_1.validateStringBounded)(data.bedtimeStart ?? "21:30", "bedtimeStart", 4, 10);
    const bedtimeEnd = (0, validation_1.validateStringBounded)(data.bedtimeEnd ?? "06:30", "bedtimeEnd", 4, 10);
    const deviceLocked = typeof data.deviceLocked === "boolean" ? data.deviceLocked : false;
    await requireParentAndFamily(parentUid, familyId);
    const policyRef = db.collection("families").doc(familyId).collection("policies").doc(childUid);
    const devRef = db.collection("families").doc(familyId).collection("devices").doc(childUid);
    const nowTs = admin.firestore.Timestamp.now();
    let nextVersion = 1;
    await db.runTransaction(async (tx) => {
        const snap = await tx.get(policyRef);
        if (!snap.exists) {
            throw new https_1.HttpsError("not-found", "Child policy not found.");
        }
        nextVersion = (snap.get("policyVersion") ?? 1) + 1;
        const members = snap.get("memberUids") ?? [parentUid, childUid];
        tx.update(policyRef, {
            dailyLimitMinutes,
            bedtimeStart,
            bedtimeEnd,
            downtimeEnabled: Boolean(data.downtimeEnabled ?? true),
            policyVersion: nextVersion,
            lastActionState: "PENDING",
            updatedAt: nowTs,
        });
        tx.update(devRef, {
            deviceLocked,
            policyVersion: nextVersion,
            updatedAt: nowTs,
        });
        tx.set(db.collection("families").doc(familyId).collection("policy_versions").doc(`v_${childUid}_${nextVersion}`), {
            parentUid,
            childUid,
            memberUids: members,
            policyVersion: nextVersion,
            executionStatus: "PENDING",
            summary: `Screen time limit ${dailyLimitMinutes}m, bedtime ${bedtimeStart}-${bedtimeEnd}, lock=${deviceLocked}`,
            createdAt: nowTs,
            updatedAt: nowTs,
        });
    });
    await writeAuditLog({
        familyId,
        parentUid,
        actorUid: parentUid,
        childUid,
        action: "SCREEN_TIME_POLICY_UPDATED",
        targetPath: `families/${familyId}/policies/${childUid}`,
        details: `Limit=${dailyLimitMinutes}m, Lock=${deviceLocked}, v${nextVersion}`,
    });
    await tryDispatchFcm({
        targetUid: childUid,
        title: "Screen Time Updated",
        body: "Syncing screen time schedule.",
        category: "POLICY_SYNC",
        familyId,
        childUid,
        silentSync: true,
    });
    return { policyVersion: nextVersion };
});
async function handleApprovalDecision(request, approve) {
    const parentUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const familyId = (0, validation_1.validateStringBounded)(data.familyId, "familyId", 1, 128);
    const requestId = (0, validation_1.validateStringBounded)(data.requestId, "requestId", 1, 128);
    await requireParentAndFamily(parentUid, familyId);
    const reqRef = db.collection("families").doc(familyId).collection("approvals").doc(requestId);
    const nowTs = admin.firestore.Timestamp.now();
    let childUid = "";
    let requestType = "";
    let targetLabel = "";
    await db.runTransaction(async (tx) => {
        const snap = await tx.get(reqRef);
        if (!snap.exists) {
            throw new https_1.HttpsError("not-found", "Approval request not found.");
        }
        if (snap.get("status") !== "PENDING") {
            throw new https_1.HttpsError("failed-precondition", "Approval request is already resolved.");
        }
        childUid = snap.get("childUid");
        requestType = snap.get("requestType");
        targetLabel = snap.get("targetLabel") || "Request";
        const targetPackage = snap.get("targetPackage") || "";
        const targetDomain = snap.get("targetDomain") || "";
        const requestedMinutes = snap.get("requestedMinutes") || 0;
        tx.update(reqRef, {
            status: approve ? "APPROVED" : "REJECTED",
            decidedByUid: parentUid,
            expiresAtEpochMs: approve ? Date.now() + 24 * 60 * 60 * 1000 : Date.now(),
            updatedAt: nowTs,
        });
        if (approve) {
            const policyRef = db.collection("families").doc(familyId).collection("policies").doc(childUid);
            const policySnap = await tx.get(policyRef);
            if (policySnap.exists) {
                const currentBonus = policySnap.get("bonusMinutesToday") ?? 0;
                const allowedPackages = [...(policySnap.get("allowedPackages") ?? [])];
                const allowedDomains = [...(policySnap.get("allowedDomains") ?? [])];
                const nextVersion = (policySnap.get("policyVersion") ?? 1) + 1;
                if (targetPackage && !allowedPackages.includes(targetPackage)) {
                    allowedPackages.push(targetPackage);
                }
                if (targetDomain && !allowedDomains.includes(targetDomain)) {
                    allowedDomains.push(targetDomain);
                }
                tx.update(policyRef, {
                    bonusMinutesToday: requestType === "EXTRA_TIME" ? currentBonus + requestedMinutes : currentBonus,
                    allowedPackages,
                    allowedDomains,
                    policyVersion: nextVersion,
                    lastActionState: "PENDING",
                    updatedAt: nowTs,
                });
            }
        }
    });
    await writeAuditLog({
        familyId,
        parentUid,
        actorUid: parentUid,
        childUid,
        action: approve ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED",
        targetPath: `families/${familyId}/approvals/${requestId}`,
        details: `${approve ? "Approved" : "Rejected"} ${requestType}: ${targetLabel}`,
    });
    await tryDispatchFcm({
        targetUid: childUid,
        title: approve ? "Request Approved" : "Request Declined",
        body: `Your parent ${approve ? "approved" : "declined"} ${targetLabel}.`,
        category: "APPROVAL_DECISION",
        familyId,
        childUid,
    });
    return { requestId, status: approve ? "APPROVED" : "REJECTED" };
}
exports.approveRequest = (0, https_1.onCall)({ region: REGION }, (req) => handleApprovalDecision(req, true));
exports.rejectRequest = (0, https_1.onCall)({ region: REGION }, (req) => handleApprovalDecision(req, false));
exports.submitDeviceHeartbeat = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const childUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const familyId = (0, validation_1.validateStringBounded)(data.familyId, "familyId", 1, 128);
    const batteryLevel = (0, validation_1.validateIntBounded)(data.batteryLevel, "batteryLevel", -1, 100);
    const dailyScreenTimeMinutes = (0, validation_1.validateIntBounded)(data.dailyScreenTimeMinutes ?? 0, "dailyScreenTimeMinutes", 0, 1440);
    const weeklyScreenTimeMinutes = (0, validation_1.validateIntBounded)(data.weeklyScreenTimeMinutes ?? 0, "weeklyScreenTimeMinutes", 0, 10080);
    const supervisionStatus = (0, validation_1.validateStringBounded)(data.supervisionStatus ?? "ACTIVE", "supervisionStatus", 1, 64);
    await enforceRateLimit(`hb_${childUid}`, 30_000, 12);
    const devRef = db.collection("families").doc(familyId).collection("devices").doc(childUid);
    const devSnap = await devRef.get();
    if (!devSnap.exists) {
        throw new https_1.HttpsError("permission-denied", "Device is not paired to this family.");
    }
    const nowTs = admin.firestore.Timestamp.now();
    const nowMs = Date.now();
    const parentUid = devSnap.get("parentUid");
    const members = devSnap.get("memberUids") ?? [parentUid, childUid];
    // Note: managementLevel is intentionally NEVER updated from child heartbeat.
    await devRef.update({
        deviceOnline: true,
        batteryLevel,
        supervisionStatus,
        dailyScreenTimeMinutes,
        weeklyScreenTimeMinutes,
        lastSeenEpochMs: nowMs,
        updatedAt: nowTs,
    });
    await db
        .collection("families")
        .doc(familyId)
        .collection("device_heartbeats")
        .add({
        parentUid,
        childUid,
        memberUids: members,
        online: true,
        batteryLevel,
        recordedAtEpochMs: nowMs,
        createdAt: nowTs,
        updatedAt: nowTs,
    });
    return { ok: true, recordedAtEpochMs: nowMs };
});
exports.submitSecurityEvent = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const callerUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const familyId = (0, validation_1.validateStringBounded)(data.familyId, "familyId", 1, 128);
    const eventType = (0, validation_1.validateStringBounded)(data.eventType, "eventType", 1, 64);
    const severity = (0, validation_1.validateStringBounded)(data.severity ?? "WARNING", "severity", 4, 16);
    const title = (0, validation_1.validateStringBounded)(data.title, "title", 1, 120);
    const details = (0, validation_1.validateStringBounded)(data.details ?? "", "details", 0, 400);
    if (!["INFO", "WARNING", "CRITICAL"].includes(severity)) {
        throw new https_1.HttpsError("invalid-argument", "Invalid severity level.");
    }
    await enforceRateLimit(`sec_evt_${callerUid}`, 60_000, 20);
    const devSnap = await db.collection("families").doc(familyId).collection("devices").doc(callerUid).get();
    if (!devSnap.exists) {
        throw new https_1.HttpsError("permission-denied", "Only paired child devices may submit device security events.");
    }
    const parentUid = devSnap.get("parentUid");
    const members = devSnap.get("memberUids") ?? [parentUid, callerUid];
    const nowTs = admin.firestore.Timestamp.now();
    const eventRef = await db.collection("families").doc(familyId).collection("security_events").add({
        parentUid,
        childUid: callerUid,
        memberUids: members,
        eventType,
        severity,
        title,
        details,
        packageName: typeof data.packageName === "string" ? data.packageName.slice(0, 160) : "",
        acknowledged: false,
        createdAt: nowTs,
        updatedAt: nowTs,
    });
    if (severity === "CRITICAL" || severity === "WARNING") {
        await tryDispatchFcm({
            targetUid: parentUid,
            title,
            body: details || "Security event reported on child device.",
            category: "SECURITY_EVENT",
            familyId,
            childUid: callerUid,
        });
    }
    return { eventId: eventRef.id };
});
exports.revokeDevice = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const parentUid = requireAuthUid(request);
    const data = (request.data ?? {});
    const familyId = (0, validation_1.validateStringBounded)(data.familyId, "familyId", 1, 128);
    const childUid = (0, validation_1.validateStringBounded)(data.childUid, "childUid", 1, 128);
    const famSnap = await requireParentAndFamily(parentUid, familyId);
    const famRef = famSnap.ref;
    const nowTs = admin.firestore.Timestamp.now();
    await db.runTransaction(async (tx) => {
        const members = (famSnap.get("memberUids") ?? []).filter((u) => u !== childUid);
        const children = (famSnap.get("childUids") ?? []).filter((u) => u !== childUid);
        tx.delete(famRef.collection("devices").doc(childUid));
        tx.delete(famRef.collection("policies").doc(childUid));
        tx.update(famRef, {
            memberUids: members,
            childUids: children,
            updatedAt: nowTs,
        });
    });
    await writeAuditLog({
        familyId,
        parentUid,
        actorUid: parentUid,
        childUid,
        action: "DEVICE_REVOKED",
        targetPath: `families/${familyId}/devices/${childUid}`,
        details: "Removed paired child device and revoked active supervision policy.",
    });
    return { revoked: true, childUid };
});
exports.deleteAccountData = (0, https_1.onCall)({ region: REGION }, async (request) => {
    const uid = requireAuthUid(request);
    const userRef = db.collection("users").doc(uid);
    const userSnap = await userRef.get();
    const familyId = userSnap.exists ? userSnap.get("familyId") : undefined;
    if (familyId) {
        const famRef = db.collection("families").doc(familyId);
        const famSnap = await famRef.get();
        if (famSnap.exists && famSnap.get("ownerUid") === uid) {
            await db.recursiveDelete(famRef);
        }
    }
    await userRef.delete();
    return { deleted: true, uid };
});
//# sourceMappingURL=index.js.map