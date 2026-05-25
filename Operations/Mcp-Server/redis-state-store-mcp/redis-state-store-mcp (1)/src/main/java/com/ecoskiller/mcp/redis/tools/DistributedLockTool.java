package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Tool: distributed_lock
 *
 * Atomic distributed locking using SET key value NX PX {ttl_ms}.
 * Prevents double-booking, concurrent billing cycle starts, and
 * race conditions in dojo match initialization.
 *
 * Lock types:
 *   slot    → tenant:{tid}:lock:slot:{slot_id}     TTL default=10s
 *   billing → tenant:{tid}:lock:billing:{cycle_id} TTL default=60s
 *
 * The caller receives a lock_token on acquire; the same token is required
 * to release, preventing accidental release by a different caller.
 */
public class DistributedLockTool extends BaseTool {

    @Override public String getName() { return "distributed_lock"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",     strProp("acquire|release|status"))
                .put("lock_type",  strProp("slot|billing"))
                .put("tenant_id",  strProp("Ecoskiller tenant ID"))
                .put("lock_id",    strProp("Slot ID or billing cycle ID"))
                .put("lock_token", strProp("Token returned on acquire — required for release"))
                .put("ttl_ms",     intProp("Lock TTL in milliseconds (default: 10000 for slot, 60000 for billing)"));
        return schema(getName(),
                "Atomic distributed lock using SET NX PX — prevents double-booking and concurrent billing.",
                props, "action", "lock_type", "tenant_id", "lock_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action   = args.getString("action");
        String lockType = args.getString("lock_type");
        String tenantId = args.getString("tenant_id");
        String lockId   = args.getString("lock_id");

        String key = switch (lockType) {
            case "slot"    -> KeyBuilder.slotLock(tenantId, lockId);
            case "billing" -> KeyBuilder.billingLock(tenantId, lockId);
            default        -> throw new IllegalArgumentException("lock_type must be 'slot' or 'billing'");
        };

        long defaultTtl = lockType.equals("billing") ? 60000L : 10000L;
        long ttlMs = args.has("ttl_ms") ? args.getLong("ttl_ms") : defaultTtl;

        RedisClient r = redis();
        try {
            return switch (action) {
                case "acquire" -> {
                    String token = UUID.randomUUID().toString();
                    String res   = r.setNxPx(key, token, ttlMs);
                    if ("+OK".equals(res)) {
                        JSONObject info = new JSONObject()
                                .put("acquired", true)
                                .put("lock_token", token)
                                .put("key", key)
                                .put("ttl_ms", ttlMs);
                        yield jsonResponse(info);
                    } else {
                        JSONObject info = new JSONObject()
                                .put("acquired", false)
                                .put("reason", "Lock already held — resource is locked by another caller");
                        yield jsonResponse(info);
                    }
                }
                case "release" -> {
                    String token   = args.getString("lock_token");
                    String current = r.get(key);
                    if (current == null) {
                        yield textResponse("Lock not found — may have expired or was already released.");
                    }
                    if (current.equals(token)) {
                        r.del(key);
                        yield textResponse("Lock released successfully for key: " + key);
                    } else {
                        yield textResponse("RELEASE_DENIED: token mismatch — you do not own this lock.");
                    }
                }
                case "status" -> {
                    String ttl = r.ttl(key);
                    long remaining = Long.parseLong(ttl.replace(":", "").trim());
                    if (remaining == -2) yield textResponse("Lock is FREE (not held)");
                    yield textResponse("Lock is HELD — TTL remaining: " + remaining + "s");
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
