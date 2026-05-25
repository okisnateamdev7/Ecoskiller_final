package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: gd_session_state
 *
 * Manages the GD Orchestrator's deterministic session state machine.
 * States: WAITING → INTRO → SPEAKING:{cid} → OPEN_DISCUSSION → CLOSING → COMPLETED
 *
 * Supports GET, SET, and transition operations.
 * Keys: tenant:{tid}:gd:{sid}:state, :current_speaker, :participants, :result
 */
public class GdSessionStateTool extends BaseTool {

    @Override public String getName() { return "gd_session_state"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",       strProp("get|set|set_speaker|add_participant|remove_participant|get_participants|set_result|get_result"))
                .put("tenant_id",    strProp("Ecoskiller tenant ID"))
                .put("session_id",   strProp("GD session ID"))
                .put("state",        strProp("New GD state (for 'set' action): WAITING|INTRO|SPEAKING:{cid}|OPEN_DISCUSSION|CLOSING|COMPLETED"))
                .put("candidate_id", strProp("Candidate ID (for set_speaker, add/remove_participant)"))
                .put("result_json",  strProp("Serialized session result JSON (for set_result)"));
        return schema(getName(),
                "GD Orchestrator state machine — get/set GD session state, speaker, participants, and result.",
                props, "action", "tenant_id", "session_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action    = args.getString("action");
        String tenantId  = args.getString("tenant_id");
        String sessionId = args.getString("session_id");

        RedisClient r = redis();
        try {
            return switch (action) {
                case "get" -> {
                    String state = r.get(KeyBuilder.gdState(tenantId, sessionId));
                    yield textResponse("GD session state: " + (state != null ? state : "NOT_FOUND"));
                }
                case "set" -> {
                    String newState = args.getString("state");
                    r.set(KeyBuilder.gdState(tenantId, sessionId), newState);
                    yield textResponse("GD state set to: " + newState);
                }
                case "set_speaker" -> {
                    String cid = args.getString("candidate_id");
                    r.setex(KeyBuilder.gdCurrentSpeaker(tenantId, sessionId), 90, cid);
                    yield textResponse("Current speaker set to: " + cid + " (TTL=90s)");
                }
                case "add_participant" -> {
                    String cid = args.getString("candidate_id");
                    r.sadd(KeyBuilder.gdParticipants(tenantId, sessionId), cid);
                    yield textResponse("Participant added: " + cid);
                }
                case "remove_participant" -> {
                    String cid = args.getString("candidate_id");
                    r.srem(KeyBuilder.gdParticipants(tenantId, sessionId), cid);
                    yield textResponse("Participant removed: " + cid);
                }
                case "get_participants" -> {
                    String members = r.smembers(KeyBuilder.gdParticipants(tenantId, sessionId));
                    yield textResponse("Participants: " + members);
                }
                case "set_result" -> {
                    String json = args.getString("result_json");
                    r.setex(KeyBuilder.gdResult(tenantId, sessionId), 86400, json); // TTL=24h
                    // Also set idempotency key
                    r.setNxPx(KeyBuilder.gdIdempotency(tenantId, sessionId), "1", 86400000L);
                    yield textResponse("GD result stored with 24h TTL and idempotency key set.");
                }
                case "get_result" -> {
                    String res = r.get(KeyBuilder.gdResult(tenantId, sessionId));
                    yield textResponse(res != null ? res : "No result stored");
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
