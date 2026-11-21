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
exports.normalizeCode = normalizeCode;
exports.sha256Hex = sha256Hex;
exports.generatePairingCode = generatePairingCode;
exports.maskPairingCode = maskPairingCode;
exports.pairingSessionDocId = pairingSessionDocId;
exports.validateStringBounded = validateStringBounded;
exports.validateIntBounded = validateIntBounded;
exports.sanitizeStringArray = sanitizeStringArray;
exports.checkSlidingWindowRateLimit = checkSlidingWindowRateLimit;
exports.assertParentOwnership = assertParentOwnership;
exports.resolveEffectiveAppDecision = resolveEffectiveAppDecision;
exports.buildFcmNotificationPayload = buildFcmNotificationPayload;
const crypto = __importStar(require("node:crypto"));
const PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
function normalizeCode(raw) {
    return raw.trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
}
function sha256Hex(input) {
    return crypto.createHash("sha256").update(input, "utf8").digest("hex");
}
function generatePairingCode(length = 8) {
    const bytes = crypto.randomBytes(length);
    let result = "";
    for (let i = 0; i < length; i++) {
        result += PAIRING_ALPHABET[bytes[i] % PAIRING_ALPHABET.length];
    }
    return result;
}
function maskPairingCode(code) {
    const clean = normalizeCode(code);
    if (clean.length <= 4) {
        return "••••";
    }
    return `${clean.slice(0, 2)}••••${clean.slice(-2)}`;
}
function pairingSessionDocId(code) {
    const digest = sha256Hex(normalizeCode(code));
    return `pair_${digest.slice(0, 24)}`;
}
function validateStringBounded(value, field, minLen, maxLen) {
    if (typeof value !== "string") {
        throw new Error(`${field} must be a string.`);
    }
    const trimmed = value.trim();
    if (trimmed.length < minLen || trimmed.length > maxLen) {
        throw new Error(`${field} length must be between ${minLen} and ${maxLen}.`);
    }
    return trimmed;
}
function validateIntBounded(value, field, min, max) {
    if (typeof value !== "number" || !Number.isInteger(value) || value < min || value > max) {
        throw new Error(`${field} must be an integer between ${min} and ${max}.`);
    }
    return value;
}
function sanitizeStringArray(value, maxItems, maxItemLen = 160) {
    if (!Array.isArray(value))
        return [];
    const seen = new Set();
    const out = [];
    for (const item of value) {
        if (typeof item !== "string")
            continue;
        const trimmed = item.trim();
        if (!trimmed || trimmed.length > maxItemLen)
            continue;
        if (!seen.has(trimmed)) {
            seen.add(trimmed);
            out.push(trimmed);
            if (out.length >= maxItems)
                break;
        }
    }
    return out;
}
function checkSlidingWindowRateLimit(prev, nowMs, windowMs, maxRequests) {
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
function assertParentOwnership(callerUid, callerRole, familyOwnerUid) {
    if (callerRole !== "PARENT") {
        throw new Error("PERMISSION_DENIED: Parent role is required.");
    }
    if (callerUid !== familyOwnerUid) {
        throw new Error("PERMISSION_DENIED: Caller does not own target family.");
    }
}
function resolveEffectiveAppDecision(params) {
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
function buildFcmNotificationPayload(params) {
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
//# sourceMappingURL=validation.js.map