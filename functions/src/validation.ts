import * as crypto from "node:crypto";

export type UserRole = "PARENT" | "CHILD" | "UNSET";

export type ManagementLevel =
  | "STANDARD_SUPERVISION"
  | "FULLY_MANAGED"
  | "ENROLLMENT_PENDING"
  | "ENROLLMENT_FAILED"
  | "DEVICE_OFFLINE"
  | "MANAGEMENT_LOST";

export interface RateLimitState {
  windowStartMs: number;
  count: number;
}

const PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function normalizeCode(raw: string): string {
  return raw.trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
}

export function sha256Hex(input: string): string {
  return crypto.createHash("sha256").update(input, "utf8").digest("hex");
}

export function generatePairingCode(length = 8): string {
  const bytes = crypto.randomBytes(length);
  let result = "";
  for (let i = 0; i < length; i++) {
    result += PAIRING_ALPHABET[bytes[i] % PAIRING_ALPHABET.length];
  }
  return result;
}

export function maskPairingCode(code: string): string {
  const clean = normalizeCode(code);
  if (clean.length <= 4) {
    return "••••";
  }
  return `${clean.slice(0, 2)}••••${clean.slice(-2)}`;
}

export function pairingSessionDocId(code: string): string {
  const digest = sha256Hex(normalizeCode(code));
  return `pair_${digest.slice(0, 24)}`;
}

export function validateStringBounded(
  value: unknown,
  field: string,
  minLen: number,
  maxLen: number
): string {
  if (typeof value !== "string") {
    throw new Error(`${field} must be a string.`);
  }
  const trimmed = value.trim();
  if (trimmed.length < minLen || trimmed.length > maxLen) {
    throw new Error(`${field} length must be between ${minLen} and ${maxLen}.`);
  }
  return trimmed;
}

export function validateIntBounded(
  value: unknown,
  field: string,
  min: number,
  max: number
): number {
  if (typeof value !== "number" || !Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${field} must be an integer between ${min} and ${max}.`);
  }
  return value;
}

export function sanitizeStringArray(
  value: unknown,
  maxItems: number,
  maxItemLen = 160
): string[] {
  if (!Array.isArray(value)) return [];
  const seen = new Set<string>();
  const out: string[] = [];
  for (const item of value) {
    if (typeof item !== "string") continue;
    const trimmed = item.trim();
    if (!trimmed || trimmed.length > maxItemLen) continue;
    if (!seen.has(trimmed)) {
      seen.add(trimmed);
      out.push(trimmed);
      if (out.length >= maxItems) break;
    }
  }
  return out;
}

export function checkSlidingWindowRateLimit(
  prev: RateLimitState | undefined,
  nowMs: number,
  windowMs: number,
  maxRequests: number
): { allowed: boolean; nextState: RateLimitState } {
  if (!prev || nowMs - prev.windowStartMs >= windowMs) {
    return {
      allowed: true,
      nextState: { windowStartMs: nowMs, count: 1 },
    };
  }
  if (prev.count >= maxRequests) {
    return {
      allowed: false,
      nextState: prev,
    };
  }
  return {
    allowed: true,
    nextState: { windowStartMs: prev.windowStartMs, count: prev.count + 1 },
  };
}

export function assertParentOwnership(
  callerUid: string,
  callerRole: UserRole | undefined,
  familyOwnerUid: string
): void {
  if (callerRole !== "PARENT") {
    throw new Error("PERMISSION_DENIED: Parent role is required.");
  }
  if (callerUid !== familyOwnerUid) {
    throw new Error("PERMISSION_DENIED: Caller does not own target family.");
  }
}

export function resolveEffectiveAppDecision(params: {
  packageName: string;
  category: string;
  blockedPackages: string[];
  allowedPackages: string[];
  blockedCategories: string[];
}): "ALLOWED" | "BLOCKED" {
  // Explicit app override takes precedence over category-level block,
  // and explicit package block overrides category allow.
  if (params.blockedPackages.includes(params.packageName)) {
    return "BLOCKED";
  }
  if (params.allowedPackages.includes(params.packageName)) {
    return "ALLOWED";
  }
  if (params.blockedCategories.includes(params.category)) {
    return "BLOCKED";
  }
  return "ALLOWED";
}

export interface FcmTokenMessage {
  token: string;
  notification?: {
    title: string;
    body: string;
  };
  data: Record<string, string>;
  android: {
    priority: "high";
    notification?: {
      channelId: string;
    };
  };
}

export function buildFcmNotificationPayload(params: {
  token: string;
  title: string;
  body: string;
  category: string;
  familyId: string;
  childUid?: string;
  silentSync?: boolean;
}): FcmTokenMessage {
  if (params.silentSync) {
    return {
      token: params.token,
      data: {
        category: params.category,
        familyId: params.familyId,
        childUid: params.childUid ?? "",
        syncRequired: "true",
      },
      android: {
        priority: "high",
      },
    };
  }
  return {
    token: params.token,
    notification: {
      title: params.title,
      body: params.body,
    },
    data: {
      category: params.category,
      familyId: params.familyId,
      childUid: params.childUid ?? "",
    },
    android: {
      priority: "high",
      notification: {
        channelId: "guardian_alerts",
      },
    },
  };
}
