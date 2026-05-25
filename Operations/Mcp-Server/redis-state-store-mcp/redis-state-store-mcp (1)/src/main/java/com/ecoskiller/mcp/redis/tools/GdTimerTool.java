package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: gd_timer
 *
 * TTL-based phase timers with keyspace notification support.
 *
 * Actions:
 *   - start: SETEX a timer key with given ttl_seconds
 *   - remaining: get remaining TTL
 *   - cancel: delete the timer key
 */
public class GdTimerTool extends BaseTool {

    @Override public String getName() { return "gd_timer"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",      strProp("start|remaining|cancel"))
                .put("tenant_id",   strProp("Ecoskiller tenant ID"))
                .put("session_id",  strProp("GD session ID"))
                .put("ttl_seconds", intProp("TTL in seconds (required for 'start')"));
        return schema(getName(),
                "GD phase timer management — start, check remaining TTL, or cancel.",
                props, "action", "tenant_id", "session_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action    = args.getString("action");
        String tenantId  = args.getString("tenant_id");
        String sessionId = args.getString("session_id");
        String key       = KeyBuilder.gdTimer(tenantId, sessionId);

        RedisClient r = redis();
        try {
            return switch (action) {
                case "start" -> {
                    int ttl = args.getInt("ttl_seconds");
                    r.setex(key, ttl, "ACTIVE");
                    yield textResponse("GD timer started for session=" + sessionId + " with TTL=" + ttl + "s");
                }
                case "remaining" -> {
                    String ttlStr = r.ttl(key);
                    long remaining = Long.parseLong(ttlStr.replace(":", "").trim());
                    if (remaining == -2) yield textResponse("GD timer not active or expired.");
                    yield textResponse("GD timer remaining: " + remaining + " seconds");
                }
                case "cancel" -> {
                    r.del(key);
                    yield textResponse("GD timer cancelled for session=" + sessionId);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
