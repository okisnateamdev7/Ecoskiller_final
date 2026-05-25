package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: dojo_match
 *
 * Dojo match engine state and submission attempt counter.
 *
 * Actions:
 *   - set_state / init: set initial match state JSON and reset attempt counter to 0
 *   - get: retrieve the current match state JSON
 *   - increment_attempt / incr_attempts: increment the submission attempt counter
 *   - get_attempts: get the submission attempt count
 */
public class DojoMatchTool extends BaseTool {

    @Override public String getName() { return "dojo_match"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",     strProp("set_state|init|get|increment_attempt|incr_attempts|get_attempts"))
                .put("tenant_id",  strProp("Ecoskiller tenant ID"))
                .put("match_id",   strProp("Dojo match ID"))
                .put("state_json", strProp("Dojo match state JSON"));
        return schema(getName(),
                "Dojo Match Engine state and attempt counter management.",
                props, "action", "tenant_id", "match_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action   = args.getString("action");
        String tenantId = args.getString("tenant_id");
        String matchId  = args.getString("match_id");
        String stateKey = KeyBuilder.dojoMatchState(tenantId, matchId);
        String attemptsKey = KeyBuilder.dojoMatchAttempts(tenantId, matchId);

        RedisClient r = redis();
        try {
            return switch (action) {
                case "set_state", "init" -> {
                    String stateJson = args.getString("state_json");
                    r.set(stateKey, stateJson);
                    r.set(attemptsKey, "0");
                    yield textResponse("Dojo match initialized: " + matchId);
                }
                case "get" -> {
                    String state = r.get(stateKey);
                    if (state == null || "$-1".equals(state)) {
                        yield textResponse("Match state not found.");
                    }
                    yield textResponse(state);
                }
                case "increment_attempt", "incr_attempts" -> {
                    String attempts = r.incr(attemptsKey);
                    long val = Long.parseLong(attempts.replace(":", "").trim());
                    yield textResponse("Submission attempts: " + val);
                }
                case "get_attempts" -> {
                    String attempts = r.get(attemptsKey);
                    yield textResponse("Submission attempts: " + (attempts != null ? attempts : "0"));
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
