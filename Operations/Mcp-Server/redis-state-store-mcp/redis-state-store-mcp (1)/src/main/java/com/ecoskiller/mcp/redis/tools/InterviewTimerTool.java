package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: interview_timer
 *
 * Interview question and session timers.
 *
 * Actions:
 *   - start: SETEX timer key with ttl_seconds; initialize question index to 0
 *   - remaining: read remaining time using PTTL (millisecond precision)
 *   - next_question: increment question index
 *   - get_status: get timer status and current question index
 */
public class InterviewTimerTool extends BaseTool {

    @Override public String getName() { return "interview_timer"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",       strProp("start|remaining|next_question|get_status"))
                .put("tenant_id",    strProp("Ecoskiller tenant ID"))
                .put("interview_id", strProp("Interview session ID"))
                .put("ttl_seconds",  intProp("Timer TTL in seconds (required for 'start')"));
        return schema(getName(),
                "Interview session timer and question index management.",
                props, "action", "tenant_id", "interview_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action      = args.getString("action");
        String tenantId    = args.getString("tenant_id");
        String interviewId = args.getString("interview_id");
        String timerKey    = KeyBuilder.interviewTimer(tenantId, interviewId);
        String questionKey = KeyBuilder.interviewQuestion(tenantId, interviewId);

        RedisClient r = redis();
        try {
            return switch (action) {
                case "start" -> {
                    int ttl = args.getInt("ttl_seconds");
                    r.setex(timerKey, ttl, "ACTIVE");
                    r.set(questionKey, "0");
                    yield textResponse("Interview timer started with TTL=" + ttl + "s. Question index set to 0.");
                }
                case "remaining" -> {
                    String pttlRes = r.pttl(timerKey);
                    long remainingMs = Long.parseLong(pttlRes.replace(":", "").trim());
                    if (remainingMs == -2) yield textResponse("Interview timer not active or expired.");
                    yield textResponse("Remaining time: " + remainingMs + " ms");
                }
                case "next_question" -> {
                    String next = r.incr(questionKey);
                    long idx = Long.parseLong(next.replace(":", "").trim());
                    yield textResponse("Advanced to question: " + idx);
                }
                case "get_status" -> {
                    String qIdx = r.get(questionKey);
                    String ttlStr = r.ttl(timerKey);
                    long remaining = Long.parseLong(ttlStr.replace(":", "").trim());
                    JSONObject info = new JSONObject()
                            .put("interview_id", interviewId)
                            .put("current_question", qIdx != null ? qIdx : "0")
                            .put("remaining_seconds", remaining);
                    yield jsonResponse(info);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
