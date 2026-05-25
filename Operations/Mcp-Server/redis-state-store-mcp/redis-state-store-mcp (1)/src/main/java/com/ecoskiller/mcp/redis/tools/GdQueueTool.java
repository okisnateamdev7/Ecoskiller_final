package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: gd_queue
 *
 * Raise-hand queue management for GD sessions using Redis LIST (FIFO).
 *
 * Actions:
 *   - raise: LPUSH candidate_id onto the queue
 *   - peek: LRANGE key -1 -1 (view the next candidate to speak without popping)
 *   - grant: RPOP key (remove and return the next candidate to speak)
 *   - list: LRANGE key 0 -1 (view entire queue)
 */
public class GdQueueTool extends BaseTool {

    @Override public String getName() { return "gd_queue"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",       strProp("raise|peek|grant|list"))
                .put("tenant_id",    strProp("Ecoskiller tenant ID"))
                .put("session_id",   strProp("GD session ID"))
                .put("candidate_id", strProp("Candidate ID (required for 'raise')"));
        return schema(getName(),
                "GD raise-hand queue management — raise hand, peek next candidate, grant speaking rights, or list all.",
                props, "action", "tenant_id", "session_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action    = args.getString("action");
        String tenantId  = args.getString("tenant_id");
        String sessionId = args.getString("session_id");
        String key       = KeyBuilder.gdQueue(tenantId, sessionId);

        RedisClient r = redis();
        try {
            return switch (action) {
                case "raise" -> {
                    String cid = args.getString("candidate_id");
                    r.lpush(key, cid);
                    yield textResponse("Candidate raised hand: " + cid);
                }
                case "peek" -> {
                    String val = r.lrange(key, -1, -1);
                    if ("[]".equals(val) || val == null || val.isEmpty()) {
                        yield textResponse("Queue is empty.");
                    }
                    yield textResponse("Next candidate (peek): " + val);
                }
                case "grant" -> {
                    String cid = r.rpop(key);
                    if (cid == null || "$-1".equals(cid)) {
                        yield textResponse("Queue is empty.");
                    }
                    yield textResponse("Granted speaking rights: " + cid);
                }
                case "list" -> {
                    String val = r.lrange(key, 0, -1);
                    yield textResponse("Queue candidates: " + val);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
