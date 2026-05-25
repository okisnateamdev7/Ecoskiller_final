package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: leaderboard
 *
 * Live Sorted Set leaderboards for candidate rankings.
 *
 * Actions:
 *   - update: ZADD score member
 *   - rank: ZREVRANK member (returns 0-indexed rank, highest score is 0)
 *   - score: ZSCORE member
 *   - get_top: ZRANGE key start end WITHSCORES
 */
public class LeaderboardTool extends BaseTool {

    @Override public String getName() { return "leaderboard"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",       strProp("update|rank|score|get_top"))
                .put("tenant_id",    strProp("Ecoskiller tenant ID"))
                .put("job_id",       strProp("Job ID"))
                .put("candidate_id", strProp("Candidate ID (required for update/rank/score)"))
                .put("score",        numProp("Candidate score (required for 'update')"))
                .put("start",        intProp("Start index for 'get_top' (default: 0)"))
                .put("end",          intProp("End index for 'get_top' (default: 9)"));
        return schema(getName(),
                "Candidate leaderboard management — update score, check rank, score, or retrieve top candidates.",
                props, "action", "tenant_id", "job_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action     = args.getString("action");
        String tenantId   = args.getString("tenant_id");
        String jobId      = args.getString("job_id");
        String key        = KeyBuilder.rankings(tenantId, jobId);

        RedisClient r = redis();
        try {
            return switch (action) {
                case "update" -> {
                    String cid = args.getString("candidate_id");
                    double score = args.getDouble("score");
                    r.zadd(key, score, cid);
                    yield textResponse("Leaderboard updated for job=" + jobId + " candidate=" + cid + " score=" + score);
                }
                case "rank" -> {
                    String cid = args.getString("candidate_id");
                    String res = r.zrevrank(key, cid);
                    if (res == null || "$-1".equals(res) || res.contains("ERR") || res.isEmpty()) {
                        yield textResponse("Candidate not ranked.");
                    }
                    long rank = Long.parseLong(res.replace(":", "").trim());
                    yield textResponse("Candidate rank: " + rank + " (0-indexed)");
                }
                case "score" -> {
                    String cid = args.getString("candidate_id");
                    String res = r.zscore(key, cid);
                    if (res == null || "$-1".equals(res) || res.isEmpty()) {
                        yield textResponse("Candidate score not found.");
                    }
                    yield textResponse("Candidate score: " + res);
                }
                case "get_top" -> {
                    int start = args.has("start") ? args.getInt("start") : 0;
                    int end = args.has("end") ? args.getInt("end") : 9;
                    String list = r.zrange(key, start, end, true);
                    yield textResponse("Top candidates: " + list);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
