package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import org.json.JSONObject;

import java.time.Instant;

/**
 * Tool: backup_status
 *
 * Check state/trigger Redis backups (RDB persistence info).
 *
 * Actions:
 *   - query / check_rpo: retrieve the last successful save timestamp (LASTSAVE)
 *   - trigger: start an asynchronous background save (BGSAVE)
 */
public class BackupStatusTool extends BaseTool {

    @Override public String getName() { return "backup_status"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action", strProp("query|trigger|check_rpo"));
        return schema(getName(),
                "Query Redis backup status, check RPO, or trigger a new background RDB save.",
                props, "action");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action = args.getString("action");

        RedisClient r = redis();
        try {
            return switch (action) {
                case "query", "check_rpo" -> {
                    String lastsaveRes = r.lastsave();
                    long timestamp = Long.parseLong(lastsaveRes.replace(":", "").trim());
                    String timeStr = Instant.ofEpochSecond(timestamp).toString();
                    long secondsSinceLastSave = (System.currentTimeMillis() / 1000) - timestamp;
                    JSONObject res = new JSONObject()
                            .put("last_save_timestamp", timestamp)
                            .put("last_save_time", timeStr)
                            .put("seconds_since_last_save", secondsSinceLastSave);
                    yield jsonResponse(res);
                }
                case "trigger" -> {
                    String bgsaveRes = r.bgsave();
                    yield textResponse("Background save triggered. Response: " + bgsaveRes);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
