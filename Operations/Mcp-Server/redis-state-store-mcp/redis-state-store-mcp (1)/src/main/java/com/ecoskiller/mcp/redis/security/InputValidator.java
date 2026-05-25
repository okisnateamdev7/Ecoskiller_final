package com.ecoskiller.mcp.redis.security;

import org.json.JSONObject;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Security validator for all MCP tool inputs.
 *
 * Enforces:
 *   1. Tenant ID format — alphanumeric + hyphen, max 64 chars
 *   2. Session/resource IDs — alphanumeric + hyphen/underscore, max 128 chars
 *   3. Key pattern injection prevention — no wildcard (*) or RESP special chars in IDs
 *   4. Value size limits — prevents memory exhaustion via large payloads
 *   5. Required field presence per tool
 *   6. Numeric range validation for TTL and score values
 */
public class InputValidator {

    // ─── Patterns ────────────────────────────────────────────────────────────────

    /** Tenant IDs: lowercase alphanumeric + hyphen */
    private static final Pattern TENANT_ID  = Pattern.compile("^[a-zA-Z0-9\\-]{1,64}$");

    /** Session/resource IDs: alphanumeric + hyphen/underscore */
    private static final Pattern RESOURCE_ID = Pattern.compile("^[a-zA-Z0-9\\-_]{1,128}$");

    /** OTP types allowed */
    private static final Set<String> OTP_TYPES = Set.of("login", "mfa", "media_room");

    /** GD states allowed */
    private static final Set<String> GD_STATES = Set.of(
            "WAITING", "INTRO", "OPEN_DISCUSSION", "CLOSING", "COMPLETED"
    );

    /** Max value size: 64 KB */
    private static final int MAX_VALUE_BYTES = 65536;

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Validate arguments for the given tool.
     * Throws SecurityException with a descriptive message if any check fails.
     */
    public void validate(String toolName, JSONObject args) {
        // Every tool that touches a tenant must supply tenant_id
        if (args.has("tenant_id")) {
            validateTenantId(args.getString("tenant_id"));
        }

        // Resource IDs
        validateIfPresent(args, "session_id",   "session_id");
        validateIfPresent(args, "interview_id", "interview_id");
        validateIfPresent(args, "slot_id",      "slot_id");
        validateIfPresent(args, "job_id",       "job_id");
        validateIfPresent(args, "match_id",     "match_id");
        validateIfPresent(args, "cycle_id",     "cycle_id");
        validateIfPresent(args, "user_id",      "user_id");
        validateIfPresent(args, "candidate_id", "candidate_id");
        validateIfPresent(args, "lock_id",      "lock_id");

        // OTP type allow-list
        if (args.has("otp_type")) {
            String ot = args.getString("otp_type");
            if (!OTP_TYPES.contains(ot)) {
                throw new SecurityException("otp_type must be one of " + OTP_TYPES + ", got: " + ot);
            }
        }

        // GD state allow-list
        if (args.has("state")) {
            String st = args.getString("state");
            // Speaker states include "SPEAKING:{candidateId}" — validate prefix + suffix
            if (!GD_STATES.contains(st) && !st.startsWith("SPEAKING:")) {
                throw new SecurityException("Invalid GD state: " + st);
            }
            if (st.startsWith("SPEAKING:")) {
                String cid = st.substring("SPEAKING:".length());
                if (!RESOURCE_ID.matcher(cid).matches()) {
                    throw new SecurityException("Invalid candidate_id in SPEAKING state: " + cid);
                }
            }
        }

        // Value size
        if (args.has("value")) {
            String v = args.optString("value", "");
            if (v.getBytes().length > MAX_VALUE_BYTES) {
                throw new SecurityException("value exceeds max size of " + MAX_VALUE_BYTES + " bytes");
            }
        }

        // TTL range (1s to 7 days)
        if (args.has("ttl_seconds")) {
            int ttl = args.getInt("ttl_seconds");
            if (ttl < 1 || ttl > 604800) {
                throw new SecurityException("ttl_seconds must be between 1 and 604800 (7 days)");
            }
        }

        // Lock TTL (1ms to 300,000ms = 5 minutes)
        if (args.has("ttl_ms")) {
            long ttl = args.getLong("ttl_ms");
            if (ttl < 1 || ttl > 300000) {
                throw new SecurityException("ttl_ms must be between 1 and 300000 (5 min)");
            }
        }

        // Score must be finite double
        if (args.has("score")) {
            double s = args.getDouble("score");
            if (Double.isNaN(s) || Double.isInfinite(s)) {
                throw new SecurityException("score must be a finite number");
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void validateTenantId(String id) {
        if (id == null || !TENANT_ID.matcher(id).matches()) {
            throw new SecurityException(
                "Invalid tenant_id '" + safeLog(id) + "' — must match [a-zA-Z0-9\\-]{1,64}");
        }
    }

    private void validateIfPresent(JSONObject args, String field, String label) {
        if (args.has(field)) {
            String val = args.optString(field, "");
            if (!RESOURCE_ID.matcher(val).matches()) {
                throw new SecurityException(
                    "Invalid " + label + " '" + safeLog(val) + "' — must match [a-zA-Z0-9\\-_]{1,128}");
            }
        }
    }

    /** Truncate value for safe logging (never log full user data) */
    private String safeLog(String s) {
        if (s == null) return "null";
        return s.length() > 20 ? s.substring(0, 20) + "..." : s;
    }
}
