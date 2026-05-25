package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import org.json.JSONObject;

/**
 * Tool: health_check
 *
 * Checks connection health, latency, and operational statistics of the Redis state store.
 *
 * Actions/Levels:
 *   - ping / level=basic: run PING command
 *   - stats: get DB size and basic info
 */
public class HealthCheckTool extends BaseTool {

    @Override public String getName() { return "health_check"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action", strProp("ping|stats"))
                .put("level",  strProp("basic|full"));
        return schema(getName(),
                "Perform health check or stats query against the Redis instance.",
                props);
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action = args.optString("action", "");
        String level  = args.optString("level", "");

        RedisClient r = redis();
        try {
            if ("basic".equals(level) || "ping".equals(action) || (action.isEmpty() && level.isEmpty())) {
                String res = r.ping();
                return textResponse(res);
            } else if ("stats".equals(action) || "full".equals(level)) {
                String size = r.dbsize();
                String info = r.info("memory");
                JSONObject stats = new JSONObject()
                        .put("status", "UP")
                        .put("dbsize", size)
                        .put("memory_info", info);
                return jsonResponse(stats);
            } else {
                return textResponse("Unknown health check action/level: action=" + action + ", level=" + level);
            }
        } finally { r.close(); }
    }
}
