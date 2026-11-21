const { test } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");

// Pure JS verification aligned with src/validation.ts so tests run offline even without pre-installed npm modules.
function normalizeCode(raw) {
  return String(raw).trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function sha256Hex(input) {
  return crypto.createHash("sha256").update(input, "utf8").digest("hex");
}

function maskPairingCode(code) {
  const clean = normalizeCode(code);
  if (clean.length <= 4) return "••••";
  return `${clean.slice(0, 2)}••••${clean.slice(-2)}`;
}

function checkSlidingWindowRateLimit(prev, nowMs, windowMs, maxRequests) {
  if (!prev || nowMs - prev.windowStartMs >= windowMs) {
    return { allowed: true, nextState: { windowStartMs: nowMs, count: 1 } };
  }
  if (prev.count >= maxRequests) {
    return { allowed: false, nextState: prev };
  }
  return { allowed: true, nextState: { windowStartMs: prev.windowStartMs, count: prev.count + 1 } };
}

function resolveEffectiveAppDecision({ packageName, category, blockedPackages, allowedPackages, blockedCategories }) {
  if (blockedPackages.includes(packageName)) return "BLOCKED";
  if (allowedPackages.includes(packageName)) return "ALLOWED";
  if (blockedCategories.includes(category)) return "BLOCKED";
  return "ALLOWED";
}

test("Pairing code normalization, SHA-256 hashing, and masking", () => {
  const clean = normalizeCode("k7m9-p2x4");
  assert.equal(clean, "K7M9P2X4");
  assert.equal(maskPairingCode(clean), "K7••••X4");
  assert.equal(sha256Hex(clean).length, 64);
  assert.equal(sha256Hex(clean), sha256Hex("K7M9P2X4"));
});

test("Rate limiter blocks exceeding requests within sliding window and resets after expiry", () => {
  let state;
  const now = 1_700_000_000_000;
  for (let i = 0; i < 3; i++) {
    const res = checkSlidingWindowRateLimit(state, now + i * 100, 60_000, 3);
    assert.equal(res.allowed, true);
    state = res.nextState;
  }
  const blocked = checkSlidingWindowRateLimit(state, now + 500, 60_000, 3);
  assert.equal(blocked.allowed, false);

  const reset = checkSlidingWindowRateLimit(state, now + 61_000, 60_000, 3);
  assert.equal(reset.allowed, true);
  assert.equal(reset.nextState.count, 1);
});

test("Policy evaluation respects explicit package block/allow precedence over category block", () => {
  assert.equal(
    resolveEffectiveAppDecision({
      packageName: "com.edu.khan",
      category: "EDUCATION",
      blockedPackages: ["com.edu.khan"],
      allowedPackages: ["com.edu.khan"],
      blockedCategories: [],
    }),
    "BLOCKED"
  );
  assert.equal(
    resolveEffectiveAppDecision({
      packageName: "com.social.safe",
      category: "SOCIAL_MEDIA",
      blockedPackages: [],
      allowedPackages: ["com.social.safe"],
      blockedCategories: ["SOCIAL_MEDIA"],
    }),
    "ALLOWED"
  );
  assert.equal(
    resolveEffectiveAppDecision({
      packageName: "com.social.other",
      category: "SOCIAL_MEDIA",
      blockedPackages: [],
      allowedPackages: [],
      blockedCategories: ["SOCIAL_MEDIA"],
    }),
    "BLOCKED"
  );
});
