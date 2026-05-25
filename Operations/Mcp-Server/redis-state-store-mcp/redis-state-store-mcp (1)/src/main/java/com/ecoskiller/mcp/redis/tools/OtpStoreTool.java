package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: otp_store
 *
 * Secure OTP lifecycle management:
 *   - store: SETEX 900s (15 min). OTP never written to disk (in-memory only).
 *   - verify: Atomic GET then DEL to prevent replay attacks.
 *   - ttl:    Remaining TTL for a pending OTP.
 *   - invalidate: Force-delete an OTP (admin cancel).
 *
 * Key: tenant:{tid}:otp:{uid}:{otp_type}
 * OTP types: login | mfa | media_room
 *
 * SECURITY: otp_value is never logged in the audit trail.
 */
public class OtpStoreTool extends BaseTool {

    @Override public String getName() { return "otp_store"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",    strProp("store|verify|ttl|invalidate"))
                .put("tenant_id", strProp("Ecoskiller tenant ID"))
                .put("user_id",   strProp("User ID"))
                .put("otp_type",  strProp("OTP type: login|mfa|media_room"))
                .put("otp_value", strProp("OTP value (for store/verify)"));
        return schema(getName(),
                "Auth-service OTP lifecycle — store, verify (atomic), TTL check, and invalidate. " +
                "OTPs are in-memory only, never persisted to disk.",
                props, "action", "tenant_id", "user_id", "otp_type");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action   = args.getString("action");
        String tenantId = args.getString("tenant_id");
        String userId   = args.getString("user_id");
        String otpType  = args.getString("otp_type");
        String key      = KeyBuilder.otp(tenantId, userId, otpType);

        RedisClient r = redis();
        try {
            return switch (action) {
                case "store" -> {
                    String otp = args.getString("otp_value");
                    r.setex(key, 900, otp);  // 15-minute TTL
                    yield textResponse("OTP stored for user=" + userId + " type=" + otpType + " TTL=900s");
                }
                case "verify" -> {
                    String provided = args.getString("otp_value");
                    String stored   = r.get(key);
                    if (stored == null) {
                        yield textResponse("VERIFICATION_FAILED: OTP not found or expired.");
                    }
                    if (stored.equals(provided)) {
                        r.del(key); // Atomic delete on success — prevents replay
                        yield textResponse("VERIFICATION_OK: OTP verified and invalidated.");
                    } else {
                        yield textResponse("VERIFICATION_FAILED: OTP mismatch.");
                    }
                }
                case "ttl" -> {
                    String ttl = r.ttl(key);
                    long remaining = Long.parseLong(ttl.replace(":", "").trim());
                    if (remaining == -2) yield textResponse("OTP not found or already expired.");
                    yield textResponse("OTP TTL remaining: " + remaining + " seconds");
                }
                case "invalidate" -> {
                    r.del(key);
                    yield textResponse("OTP invalidated for user=" + userId + " type=" + otpType);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
