const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PARENT_UID = "parent_alice_01";
const CHILD_UID = "child_bob_02";
const STRANGER_UID = "stranger_eve_99";
const FAMILY_ID = "fam_parent_alice_01";
const SESSION_ID = "pair_K7M9P2X4";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(PARENT_UID).get());
  await assertFails(unauth.collection("families").where("memberUids", "array-contains", PARENT_UID).get());
  await assertFails(unauth.collection("pairing_sessions").doc(SESSION_ID).get());
  await assertFails(
    unauth.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      displayName: "Unauth",
      role: "PARENT",
      sessionVersion: 1,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("UserAccount: owner create/update succeeds; cross-user, list, and ghost fields fail", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    parentDb.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      displayName: "Alice Guardian",
      email: "alice@example.com",
      role: "PARENT",
      familyId: FAMILY_ID,
      sessionVersion: 1,
      biometricEnabled: true,
      twoFactorEnabled: false,
      languageCode: "en",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    parentDb.collection("users").doc(PARENT_UID).update({
      languageCode: "hi",
      sessionVersion: 2,
      updatedAt: pastDate(500),
    })
  );

  // Cross-user read/write blocked
  await assertFails(strangerDb.collection("users").doc(PARENT_UID).get());
  await assertFails(strangerDb.collection("users").doc(PARENT_UID).update({ displayName: "Hack", updatedAt: pastDate(500) }));
  // Collection list blocked
  await assertFails(parentDb.collection("users").get());
  // Ghost field shadow write blocked
  await assertFails(
    parentDb.collection("users").doc(PARENT_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
});

test("Family & PairingSession: one-time pairing lifecycle, replay protection, and member isolation", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    parentDb.collection("families").doc(FAMILY_ID).set({
      ownerUid: PARENT_UID,
      familyName: "Alice Family",
      memberUids: [PARENT_UID],
      childUids: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Filtered family query matches Kotlin repository
  await assertSucceeds(
    parentDb.collection("families").where("memberUids", "array-contains", PARENT_UID).get()
  );
  // Unfiltered list fails
  await assertFails(parentDb.collection("families").get());

  // Parent creates pairing session
  await assertSucceeds(
    parentDb.collection("pairing_sessions").doc(SESSION_ID).set({
      familyId: FAMILY_ID,
      parentUid: PARENT_UID,
      pairingCode: "K7M9P2X4",
      status: "PENDING",
      expiresAtEpochMs: Date.now() + 900000,
      attemptCount: 0,
      consumed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Child fetches exact session and requests pairing
  await assertSucceeds(childDb.collection("pairing_sessions").doc(SESSION_ID).get());
  await assertSucceeds(
    childDb.collection("pairing_sessions").doc(SESSION_ID).update({
      status: "REQUESTED",
      childUid: CHILD_UID,
      childName: "Rohan",
      childDeviceName: "Pixel Kid",
      deviceModel: "Pixel 8a",
      attemptCount: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Stranger cannot list pairing_sessions
  await assertFails(strangerDb.collection("pairing_sessions").get());
  // Stranger cannot approve pairing session
  await assertFails(
    strangerDb.collection("pairing_sessions").doc(SESSION_ID).update({
      status: "APPROVED",
      consumed: true,
      updatedAt: pastDate(500),
    })
  );

  // Parent approves pairing session (terminal) and adds child to family
  await assertSucceeds(
    parentDb.collection("pairing_sessions").doc(SESSION_ID).update({
      status: "APPROVED",
      consumed: true,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    parentDb.collection("families").doc(FAMILY_ID).update({
      memberUids: [PARENT_UID, CHILD_UID],
      childUids: [CHILD_UID],
      updatedAt: pastDate(500),
    })
  );

  // Replay protection: consumed session cannot be re-requested
  await assertFails(
    childDb.collection("pairing_sessions").doc(SESSION_ID).update({
      status: "REQUESTED",
      childUid: CHILD_UID,
      childName: "Rohan",
      childDeviceName: "Pixel Kid",
      deviceModel: "Pixel 8a",
      attemptCount: 2,
      updatedAt: pastDate(200),
    })
  );
});

test("Devices, Policies, Approvals, SecurityEvents, Notifications, Locations, AppUsage enforce member isolation & parity", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const created = pastDate(15000);
  const members = [PARENT_UID, CHILD_UID];

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("families").doc(FAMILY_ID).set({
      ownerUid: PARENT_UID,
      familyName: "Alice Family",
      memberUids: members,
      childUids: [CHILD_UID],
      createdAt: created,
      updatedAt: created,
    });
  });

  // Device create & child telemetry update
  const devRef = parentDb.collection("families").doc(FAMILY_ID).collection("devices").doc(CHILD_UID);
  await assertSucceeds(
    devRef.set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      childName: "Rohan",
      deviceName: "Rohan Phone",
      managementLevel: "STANDARD_SUPERVISION",
      supervisionStatus: "ACTIVE",
      deviceOnline: true,
      batteryLevel: 84,
      deviceLocked: false,
      strictSupervision: false,
      policyVersion: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Child updates telemetry only
  await assertSucceeds(
    childDb.collection("families").doc(FAMILY_ID).collection("devices").doc(CHILD_UID).update({
      batteryLevel: 80,
      dailyScreenTimeMinutes: 45,
      updatedAt: pastDate(500),
    })
  );
  // Child CANNOT lock/unlock their own device
  await assertFails(
    childDb.collection("families").doc(FAMILY_ID).collection("devices").doc(CHILD_UID).update({
      deviceLocked: true,
      updatedAt: pastDate(500),
    })
  );

  // Policy: Parent creates & updates; Child can only ack lastActionState
  const policyRef = parentDb.collection("families").doc(FAMILY_ID).collection("policies").doc(CHILD_UID);
  await assertSucceeds(
    policyRef.set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      policyVersion: 1,
      dailyLimitMinutes: 120,
      maxAgeRating: "AGE_12",
      youtubeMode: "SHORTS_RESTRICTION",
      lastActionState: "PENDING",
      blockedCategories: ["DATING", "ADULT_CONTENT"],
      blockedDomains: ["shorts.youtube.com"],
      allowedDomains: ["khanacademy.org"],
      homeLat: 26.1445,
      homeLng: 91.7362,
      homeRadiusMeters: 150,
      locationSharingEnabled: true,
      retentionDays: 30,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    childDb.collection("families").doc(FAMILY_ID).collection("policies").doc(CHILD_UID).update({
      lastActionState: "APPLIED",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    childDb.collection("families").doc(FAMILY_ID).collection("policies").doc(CHILD_UID).update({
      dailyLimitMinutes: 999,
      updatedAt: pastDate(400),
    })
  );

  // Approval: Child creates PENDING; Parent approves (terminal); Child cannot self-approve
  const reqRef = childDb.collection("families").doc(FAMILY_ID).collection("approvals").doc("req_1");
  await assertSucceeds(
    reqRef.set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      childName: "Rohan",
      requestType: "EXTRA_TIME",
      requestedMinutes: 15,
      reason: "Finish science video",
      status: "PENDING",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    reqRef.update({
      status: "APPROVED",
      decidedByUid: CHILD_UID,
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    parentDb.collection("families").doc(FAMILY_ID).collection("approvals").doc("req_1").update({
      status: "APPROVED",
      decidedByUid: PARENT_UID,
      expiresAtEpochMs: Date.now() + 900000,
      updatedAt: pastDate(300),
    })
  );
  // Terminal state locking
  await assertFails(
    parentDb.collection("families").doc(FAMILY_ID).collection("approvals").doc("req_1").update({
      status: "REJECTED",
      decidedByUid: PARENT_UID,
      updatedAt: pastDate(200),
    })
  );

  // SecurityEvent, Notification, LocationUpdate, AppUsageEntry
  await assertSucceeds(
    childDb.collection("families").doc(FAMILY_ID).collection("security_events").doc("evt_1").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      eventType: "PERMISSION_CHANGED",
      severity: "WARNING",
      title: "Usage Access Revoked",
      details: "Usage stats permission was disabled on device",
      acknowledged: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    parentDb.collection("families").doc(FAMILY_ID).collection("notifications").doc("notif_1").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      recipientUid: PARENT_UID,
      category: "NEW_APP_INSTALLED",
      title: "New App Detected",
      body: "Rohan installed Calculator",
      readFlag: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    childDb.collection("families").doc(FAMILY_ID).collection("locations").doc("loc_1").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      latitude: 26.1445,
      longitude: 91.7362,
      accuracyMeters: 12.5,
      placeLabel: "Home",
      eventKind: "ARRIVAL",
      sharingActive: true,
      capturedAtEpochMs: Date.now() - 1000,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    childDb.collection("families").doc(FAMILY_ID).collection("app_usage").doc("pkg_youtube").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      packageName: "com.google.android.youtube",
      appLabel: "YouTube",
      category: "VIDEO_STREAMING",
      ageRating: "AGE_12",
      minutesToday: 24,
      minutesWeek: 110,
      blocked: false,
      exceptionAllowed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Filtered member queries succeed; stranger queries fail
  await assertSucceeds(
    parentDb.collection("families").doc(FAMILY_ID).collection("devices").where("memberUids", "array-contains", PARENT_UID).get()
  );
  await assertFails(
    strangerDb.collection("families").doc(FAMILY_ID).collection("devices").get()
  );
  await assertFails(
    strangerDb.collection("families").doc(FAMILY_ID).collection("devices").where("memberUids", "array-contains", PARENT_UID).get()
  );
  await assertFails(
    strangerDb.collection("families").doc(FAMILY_ID).collection("policies").doc(CHILD_UID).get()
  );

  // Child CANNOT self-escalate managementLevel
  await assertFails(
    childDb.collection("families").doc(FAMILY_ID).collection("devices").doc(CHILD_UID).update({
      managementLevel: "FULLY_MANAGED",
      updatedAt: pastDate(200),
    })
  );

  // Audit logs: Parent creates & reads; Child cannot write/read; immutable (no update/delete)
  const auditRef = parentDb.collection("families").doc(FAMILY_ID).collection("audit_logs").doc("audit_1");
  await assertSucceeds(
    auditRef.set({
      parentUid: PARENT_UID,
      actorUid: PARENT_UID,
      childUid: CHILD_UID,
      action: "POLICY_UPDATED",
      targetPath: `families/${FAMILY_ID}/policies/${CHILD_UID}`,
      details: "Updated daily screen time limit to 120m",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(auditRef.get());
  await assertFails(childDb.collection("families").doc(FAMILY_ID).collection("audit_logs").doc("audit_1").get());
  await assertFails(
    childDb.collection("families").doc(FAMILY_ID).collection("audit_logs").doc("audit_2").set({
      parentUid: PARENT_UID,
      actorUid: CHILD_UID,
      action: "SPOOF",
      targetPath: "families/x",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(auditRef.update({ action: "TAMPERED", updatedAt: pastDate(100) }));
  await assertFails(auditRef.delete());

  // Device heartbeats: Child creates; members read; Parent cannot spoof as child; immutable
  const hbRef = childDb.collection("families").doc(FAMILY_ID).collection("device_heartbeats").doc("hb_1");
  await assertSucceeds(
    hbRef.set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      online: true,
      batteryLevel: 82,
      recordedAtEpochMs: Date.now() - 500,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(parentDb.collection("families").doc(FAMILY_ID).collection("device_heartbeats").doc("hb_1").get());
  await assertFails(hbRef.update({ batteryLevel: 99, updatedAt: pastDate(100) }));
  await assertFails(hbRef.delete());

  // Policy versions: Parent creates; members read; Child cannot create; immutable
  const pvRef = parentDb.collection("families").doc(FAMILY_ID).collection("policy_versions").doc("pv_1");
  await assertSucceeds(
    pvRef.set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      policyVersion: 2,
      executionStatus: "PENDING",
      summary: "Daily limit 120m, Shorts restricted",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(childDb.collection("families").doc(FAMILY_ID).collection("policy_versions").doc("pv_1").get());
  await assertFails(
    childDb.collection("families").doc(FAMILY_ID).collection("policy_versions").doc("pv_2").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      memberUids: members,
      policyVersion: 3,
      executionStatus: "APPLIED",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(pvRef.update({ executionStatus: "APPLIED", updatedAt: pastDate(100) }));
  await assertFails(pvRef.delete());
});

test("Hashed PairingSession: create & claim without plaintext pairingCode", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const created = pastDate(10000);
  const hashedSessionId = "pair_9f86d081884c7d659a2feaa0";

  await assertSucceeds(
    parentDb.collection("families").doc(FAMILY_ID).set({
      ownerUid: PARENT_UID,
      familyName: "Alice Family",
      memberUids: [PARENT_UID],
      childUids: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    parentDb.collection("pairing_sessions").doc(hashedSessionId).set({
      familyId: FAMILY_ID,
      parentUid: PARENT_UID,
      pairingCodeHash: "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
      pairingCodeHint: "K7••••X4",
      status: "PENDING",
      expiresAtEpochMs: Date.now() + 900000,
      attemptCount: 0,
      consumed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    childDb.collection("pairing_sessions").doc(hashedSessionId).update({
      status: "REQUESTED",
      childUid: CHILD_UID,
      childName: "Rohan",
      childDeviceName: "Redmi Note 13",
      deviceModel: "2312DRA50I",
      attemptCount: 1,
      updatedAt: pastDate(500),
    })
  );
});

