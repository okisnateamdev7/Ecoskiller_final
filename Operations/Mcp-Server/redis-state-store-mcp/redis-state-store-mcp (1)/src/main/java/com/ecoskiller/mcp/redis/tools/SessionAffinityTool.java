package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import org.json.JSONObject;

/**
 * Tool: session_affinity
 *
 * Cross-pod session context for stateless microservices.
 *
 * Actions:
 *   - store: store session affinity context JSON (TTL=3600s)
 *   - retrieve: retrieve session affinity context JSON
 */
public class SessionAffinityTool extends BaseTool {

    @Override public String getName() { return "session_affinity"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",       strProp("store|retrieve"))
                .put("tenant_id",    strProp("Ecoskiller tenant ID"))
                .put("service",      strProp("Service name (e.g. gd)"))
                .put("session_id",   strProp("Session ID"))
                .put("context_json", strProp("Context JSON payload (required for 'store')"));
        return schema(getName(),
                "Cross-pod session context management for stateless microservices.",
                props, "action", "tenant_id", "service", "session_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action    = args.getString("action");
        String tenantId  = args.getString("tenant_id");
        String service   = args.getString("service");
        String sessionId = args.getString("session_id");
        String key       = "tenant:" + tenantId + ":session:" + sessionId + ":" + service + ":affinity";

        RedisClient r = redis();
        try {
            return switch (action) {
                case "store" -> {
                    String json = args.getString("context_json");
                    r.setex(key, 3600, json);
                    yield textResponse("Session affinity stored for key: " + key);
                }
                case "retrieve" -> {
                    String json = r.get(key);
                    if (json == null || "$-1".equals(json)) {
                        yield textResponse("No session affinity context found.");
                    }
                    yield textResponse(json);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
