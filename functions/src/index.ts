import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { getFirestore } from "firebase-admin/firestore";
import {
  UserRole,
  assertParentOwnership,
  buildFcmNotificationPayload,
  checkSlidingWindowRateLimit,
  generatePairingCode,
  maskPairingCode,
  normalizeCode,
  pairingSessionDocId,
  sanitizeStringArray,
  sha256Hex,
  validateIntBounded,
  validateStringBounded,
} from "./validation";

if (!admin.apps.length) {
  admin.initializeApp();
}

const FIRESTORE_DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-guardian-f84579d4-c1bb-4789-935a-1688dbc2791b";
const db = getFirestore(FIRESTORE_DATABASE_ID);
const REGION = "us-central1";
const PAIRING_TTL_MS = 15 * 60 * 1000;

function requireAuthUid(request: CallableRequest<unknown>): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  return uid;
}

async function getUserRole(uid: string): Promise<UserRole> {
  const snap = await db.collection("users").doc(uid).get();
  if (!snap.exists) {
    throw new HttpsError("failed-precondition", "User profile not found.");
  }
  return (snap.get("role") as UserRole) ?? "UNSET";
}

async function requireParentAndFamily(
  callerUid: string,
  familyId: string
): Promise<admin.firestore.DocumentSnapshot> {
  const role = await getUserRole(callerUid);
  const famSnap = await db.collection("families").doc(familyId).get();
  if (!famSnap.exists) {
    throw new HttpsError("not-found", "Family not found.");
  }
  const ownerUid = famSnap.get("ownerUid") as string;
  try {
    assertParentOwnership(callerUid, role, ownerUid);
  } catch (err) {
    throw new HttpsError("permission-denied", (err as Error).message);
  }
  return famSnap;
}

async function enforceRateLimit(
  bucketId: string,
  windowMs: number,
  maxRequests: number
): Promise<void> {
  const ref = db.collection("_server_rate_limits").doc(bucketId);
  const nowMs = Date.now();
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const prev = snap.exists
      ? {
          windowStartMs: snap.get("windowStartMs") as number,
          count: snap.get("count") as number,
        }
      : undefined;
    const result = checkSlidingWindowRateLimit(prev, nowMs, windowMs, maxRequests);
    if (!result.allowed) {
      throw new HttpsError("resource-exhausted", "Too many requests. Please wait and retry.");
    }
    tx.set(ref, {
      ...result.nextState,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });
}

async function writeAuditLog(params: {
  familyId: string;
  parentUid: string;
  actorUid: string;
  childUid?: string;
  action: string;
  targetPath: string;
  details: string;
}): Promise<void> {
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

async function tryDispatchFcm(params: {
  targetUid: string;
  title: string;
  body: string;
  category: string;
  familyId: string;
  childUid?: string;
  silentSync?: boolean;
}): Promise<void> {
  try {
    const tokenSnap = await db.collection("user_push_tokens").doc(params.targetUid).get();
    const token = tokenSnap.exists ? (tokenSnap.get("fcmToken") as string | undefined) : undefined;
    if (!token) return;
    const payload = buildFcmNotificationPayload({
      token,
      title: params.title,
      body: params.body,
      category: params.category,
      familyId: params.familyId,
      childUid: params.childUid,
      silentSync: params.silentSync,
    });
    await admin.messaging().send(payload);
  } catch (err) {
    logger.warn("FCM dispatch skipped or failed", {
      targetUid: params.targetUid,
      error: (err as Error).message,
    });
  }
}

export const createPairingSession = onCall({ region: REGION }, async (request) => {
  const callerUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const familyId = validateStringBounded(data.familyId, "familyId", 1, 128);

  await requireParentAndFamily(callerUid, familyId);
  await enforceRateLimit(`pairing_create_${callerUid}`, 60_000, 10);

  const code = generatePairingCode(8);
  const sessionId = pairingSessionDocId(code);
  const nowMs = Date.now();
  const expiresAtEpochMs = nowMs + PAIRING_TTL_MS;
  const nowTs = admin.firestore.Timestamp.now();

  await db.collection("pairing_sessions").doc(sessionId).set({
    familyId,
    parentUid: callerUid,
    pairingCodeHash: sha256Hex(code),
    pairingCodeHint: maskPairingCode(code),
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
    details: `Created hashed pairing session (${maskPairingCode(code)})`,
  });

  return {
    sessionId,
    pairingCode: code,
    pairingCodeHint: maskPairingCode(code),
    expiresAtEpochMs,
  };
});

export const claimPairingSession = onCall({ region: REGION }, async (request) => {
  const childUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const rawCode = validateStringBounded(data.pairingCode, "pairingCode", 4, 24);
  const childName = validateStringBounded(data.childName, "childName", 1, 80);
  const childDeviceName = validateStringBounded(data.childDeviceName, "childDeviceName", 1, 80);
  const deviceModel = validateStringBounded(data.deviceModel ?? "Android Device", "deviceModel", 1, 80);

  await enforceRateLimit(`pairing_claim_${childUid}`, 60_000, 8);

  const normalized = normalizeCode(rawCode);
  const sessionId = pairingSessionDocId(normalized);
  const codeHash = sha256Hex(normalized);
  const sessionRef = db.collection("pairing_sessions").doc(sessionId);

  let parentUid = "";
  let familyId = "";

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(sessionRef);
    if (!snap.exists) {
      throw new HttpsError("not-found", "Pairing session not found.");
    }
    const status = snap.get("status") as string;
    const consumed = snap.get("consumed") as boolean;
    const expiresAt = snap.get("expiresAtEpochMs") as number;
    const storedHash = snap.get("pairingCodeHash") as string | undefined;
    const attemptCount = (snap.get("attemptCount") as number) ?? 0;

    if (consumed || status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Pairing session is no longer available.");
    }
    if (Date.now() > expiresAt) {
      tx.update(sessionRef, {
        status: "EXPIRED",
        consumed: true,
        updatedAt: admin.firestore.Timestamp.now(),
      });
      throw new HttpsError("deadline-exceeded", "Pairing code has expired.");
    }
    if (attemptCount >= 10) {
      throw new HttpsError("resource-exhausted", "Maximum pairing attempts exceeded.");
    }
    if (storedHash && storedHash !== codeHash) {
      tx.update(sessionRef, {
        attemptCount: attemptCount + 1,
        updatedAt: admin.firestore.Timestamp.now(),
      });
      throw new HttpsError("permission-denied", "Pairing verification failed.");
    }

    parentUid = snap.get("parentUid") as string;
    familyId = snap.get("familyId") as string;

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

export const approvePairing = onCall({ region: REGION }, async (request) => {
  const parentUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const sessionId = validateStringBounded(data.sessionId, "sessionId", 1, 128);
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
      throw new HttpsError("not-found", "Pairing session not found.");
    }
    if (snap.get("parentUid") !== parentUid) {
      throw new HttpsError("permission-denied", "Only the owning parent can verify pairing.");
    }
    if (snap.get("consumed") === true || snap.get("status") !== "REQUESTED") {
      throw new HttpsError("failed-precondition", "Pairing session is not pending approval.");
    }
    familyId = snap.get("familyId") as string;
    childUid = snap.get("childUid") as string;
    childName = (snap.get("childName") as string) || "Child";
    childDeviceName = (snap.get("childDeviceName") as string) || "Android Device";
    deviceModel = (snap.get("deviceModel") as string) || "Android";

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
      throw new HttpsError("not-found", "Family not found.");
    }
    const members = Array.from(new Set([...((famSnap.get("memberUids") as string[]) ?? []), parentUid, childUid]));
    const children = Array.from(new Set([...((famSnap.get("childUids") as string[]) ?? []), childUid]));

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

export const updateAppPolicy = onCall({ region: REGION }, async (request) => {
  const parentUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const familyId = validateStringBounded(data.familyId, "familyId", 1, 128);
  const childUid = validateStringBounded(data.childUid, "childUid", 1, 128);

  await requireParentAndFamily(parentUid, familyId);

  const policyRef = db.collection("families").doc(familyId).collection("policies").doc(childUid);
  const devRef = db.collection("families").doc(familyId).collection("devices").doc(childUid);
  const nowTs = admin.firestore.Timestamp.now();

  let nextVersion = 1;
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(policyRef);
    if (!snap.exists) {
      throw new HttpsError("not-found", "Child policy not found.");
    }
    const currentVersion = (snap.get("policyVersion") as number) ?? 1;
    nextVersion = currentVersion + 1;
    const members = (snap.get("memberUids") as string[]) ?? [parentUid, childUid];

    const blockedPackages =
      data.blockedPackages !== undefined
        ? sanitizeStringArray(data.blockedPackages, 150, 160)
        : ((snap.get("blockedPackages") as string[]) ?? []);
    const allowedPackages =
      data.allowedPackages !== undefined
        ? sanitizeStringArray(data.allowedPackages, 150, 160)
        : ((snap.get("allowedPackages") as string[]) ?? []);
    const blockedCategories =
      data.blockedCategories !== undefined
        ? sanitizeStringArray(data.blockedCategories, 30, 64)
        : ((snap.get("blockedCategories") as string[]) ?? []);

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

export const updateScreenTimePolicy = onCall({ region: REGION }, async (request) => {
  const parentUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const familyId = validateStringBounded(data.familyId, "familyId", 1, 128);
  const childUid = validateStringBounded(data.childUid, "childUid", 1, 128);
  const dailyLimitMinutes = validateIntBounded(data.dailyLimitMinutes, "dailyLimitMinutes", 0, 1440);
  const bedtimeStart = validateStringBounded(data.bedtimeStart ?? "21:30", "bedtimeStart", 4, 10);
  const bedtimeEnd = validateStringBounded(data.bedtimeEnd ?? "06:30", "bedtimeEnd", 4, 10);
  const deviceLocked = typeof data.deviceLocked === "boolean" ? data.deviceLocked : false;

  await requireParentAndFamily(parentUid, familyId);

  const policyRef = db.collection("families").doc(familyId).collection("policies").doc(childUid);
  const devRef = db.collection("families").doc(familyId).collection("devices").doc(childUid);
  const nowTs = admin.firestore.Timestamp.now();

  let nextVersion = 1;
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(policyRef);
    if (!snap.exists) {
      throw new HttpsError("not-found", "Child policy not found.");
    }
    nextVersion = ((snap.get("policyVersion") as number) ?? 1) + 1;
    const members = (snap.get("memberUids") as string[]) ?? [parentUid, childUid];

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

async function handleApprovalDecision(
  request: CallableRequest<unknown>,
  approve: boolean
) {
  const parentUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const familyId = validateStringBounded(data.familyId, "familyId", 1, 128);
  const requestId = validateStringBounded(data.requestId, "requestId", 1, 128);

  await requireParentAndFamily(parentUid, familyId);

  const reqRef = db.collection("families").doc(familyId).collection("approvals").doc(requestId);
  const nowTs = admin.firestore.Timestamp.now();

  let childUid = "";
  let requestType = "";
  let targetLabel = "";

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(reqRef);
    if (!snap.exists) {
      throw new HttpsError("not-found", "Approval request not found.");
    }
    if (snap.get("status") !== "PENDING") {
      throw new HttpsError("failed-precondition", "Approval request is already resolved.");
    }
    childUid = snap.get("childUid") as string;
    requestType = snap.get("requestType") as string;
    targetLabel = (snap.get("targetLabel") as string) || "Request";
    const targetPackage = (snap.get("targetPackage") as string) || "";
    const targetDomain = (snap.get("targetDomain") as string) || "";
    const requestedMinutes = (snap.get("requestedMinutes") as number) || 0;

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
        const currentBonus = (policySnap.get("bonusMinutesToday") as number) ?? 0;
        const allowedPackages = [...((policySnap.get("allowedPackages") as string[]) ?? [])];
        const allowedDomains = [...((policySnap.get("allowedDomains") as string[]) ?? [])];
        const nextVersion = ((policySnap.get("policyVersion") as number) ?? 1) + 1;

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

export const approveRequest = onCall({ region: REGION }, (req) => handleApprovalDecision(req, true));
export const rejectRequest = onCall({ region: REGION }, (req) => handleApprovalDecision(req, false));

export const submitDeviceHeartbeat = onCall({ region: REGION }, async (request) => {
  const childUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const familyId = validateStringBounded(data.familyId, "familyId", 1, 128);
  const batteryLevel = validateIntBounded(data.batteryLevel, "batteryLevel", -1, 100);
  const dailyScreenTimeMinutes = validateIntBounded(
    data.dailyScreenTimeMinutes ?? 0,
    "dailyScreenTimeMinutes",
    0,
    1440
  );
  const weeklyScreenTimeMinutes = validateIntBounded(
    data.weeklyScreenTimeMinutes ?? 0,
    "weeklyScreenTimeMinutes",
    0,
    10080
  );
  const supervisionStatus = validateStringBounded(
    data.supervisionStatus ?? "ACTIVE",
    "supervisionStatus",
    1,
    64
  );

  await enforceRateLimit(`hb_${childUid}`, 30_000, 12);

  const devRef = db.collection("families").doc(familyId).collection("devices").doc(childUid);
  const devSnap = await devRef.get();
  if (!devSnap.exists) {
    throw new HttpsError("permission-denied", "Device is not paired to this family.");
  }

  const nowTs = admin.firestore.Timestamp.now();
  const nowMs = Date.now();
  const parentUid = devSnap.get("parentUid") as string;
  const members = (devSnap.get("memberUids") as string[]) ?? [parentUid, childUid];

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

export const submitSecurityEvent = onCall({ region: REGION }, async (request) => {
  const callerUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const familyId = validateStringBounded(data.familyId, "familyId", 1, 128);
  const eventType = validateStringBounded(data.eventType, "eventType", 1, 64);
  const severity = validateStringBounded(data.severity ?? "WARNING", "severity", 4, 16);
  const title = validateStringBounded(data.title, "title", 1, 120);
  const details = validateStringBounded(data.details ?? "", "details", 0, 400);

  if (!["INFO", "WARNING", "CRITICAL"].includes(severity)) {
    throw new HttpsError("invalid-argument", "Invalid severity level.");
  }

  await enforceRateLimit(`sec_evt_${callerUid}`, 60_000, 20);

  const devSnap = await db.collection("families").doc(familyId).collection("devices").doc(callerUid).get();
  if (!devSnap.exists) {
    throw new HttpsError("permission-denied", "Only paired child devices may submit device security events.");
  }

  const parentUid = devSnap.get("parentUid") as string;
  const members = (devSnap.get("memberUids") as string[]) ?? [parentUid, callerUid];
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

export const revokeDevice = onCall({ region: REGION }, async (request) => {
  const parentUid = requireAuthUid(request);
  const data = (request.data ?? {}) as Record<string, unknown>;
  const familyId = validateStringBounded(data.familyId, "familyId", 1, 128);
  const childUid = validateStringBounded(data.childUid, "childUid", 1, 128);

  const famSnap = await requireParentAndFamily(parentUid, familyId);
  const famRef = famSnap.ref;
  const nowTs = admin.firestore.Timestamp.now();

  await db.runTransaction(async (tx) => {
    const members = ((famSnap.get("memberUids") as string[]) ?? []).filter((u) => u !== childUid);
    const children = ((famSnap.get("childUids") as string[]) ?? []).filter((u) => u !== childUid);
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

export const deleteAccountData = onCall({ region: REGION }, async (request) => {
  const uid = requireAuthUid(request);
  const userRef = db.collection("users").doc(uid);
  const userSnap = await userRef.get();
  const familyId = userSnap.exists ? (userSnap.get("familyId") as string | undefined) : undefined;

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
