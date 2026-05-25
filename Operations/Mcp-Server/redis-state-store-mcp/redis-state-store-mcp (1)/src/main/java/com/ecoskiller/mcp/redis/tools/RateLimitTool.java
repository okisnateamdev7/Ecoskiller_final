package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: rate_limit
 *
 * Sliding-window rate counters (INCR + EXPIRE).
 *
 * Actions:
 *   - increment: increment the rate limit counter for a user and endpoint,
 *                and check if it exceeds the limit.
 */
public class RateLimitTool extends BaseTool {

    @Override public String getName() { return "rate_limit"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",      strProp("increment"))
                .put("tenant_id",   strProp("Ecoskiller tenant ID"))
                .put("user_id",     strProp("User ID"))
                .put("endpoint",    strProp("Endpoint name (e.g. otp_request)"))
                .put("limit",       intProp("Max attempts allowed in window"))
                .put("window_secs", intProp("Time window in seconds"));
        return schema(getName(),
                "Sliding-window rate limiter — increment and check attempts against a limit.",
                props, "action", "tenant_id", "user_id", "endpoint", "limit", "window_secs");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action    = args.getString("action");
        String tenantId  = args.getString("tenant_id");
        String userId    = args.getString("user_id");
        String endpoint  = args.getString("endpoint");
        int    limit     = args.getInt("limit");
        int    windowSecs = args.getInt("window_secs");

        long currentWindow = System.currentTimeMillis() / 1000 / windowSecs;
        String key = KeyBuilder.rateLimitCounter(tenantId, userId, endpoint, String.valueOf(currentWindow));

        RedisClient r = redis();
        try {
            return switch (action) {
                case "increment" -> {
                    String incrRes = r.incr(key);
                    long val = Long.parseLong(incrRes.replace(":", "").trim());
                    if (val == 1) {
                        r.expire(key, windowSecs);
                    }
                    boolean allowed = val <= limit;
                    JSONObject info = new JSONObject()
                            .put("allowed", allowed)
                            .put("current", val)
                            .put("limit", limit)
                            .put("window_secs", windowSecs)
                            .put("key", key);
                    yield jsonResponse(info);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
