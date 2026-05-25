package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import org.json.JSONObject;

/**
 * Tool: keyspace_notification
 *
 * Configures or checks Redis Keyspace Notifications.
 *
 * Actions:
 *   - configure: set notify-keyspace-events to Ex
 *   - check: get current notify-keyspace-events configuration
 */
public class KeyspaceNotificationTool extends BaseTool {

    @Override public String getName() { return "keyspace_notification"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action", strProp("configure|check"));
        return schema(getName(),
                "Configures or checks Redis keyspace notifications for expired keys.",
                props, "action");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action = args.optString("action", "check");

        RedisClient r = redis();
        try {
            return switch (action) {
                case "configure" -> {
                    String res = r.sendCommand("CONFIG", "SET", "notify-keyspace-events", "Ex");
                    yield textResponse("Keyspace notifications configured (Ex). Result: " + res);
                }
                case "check" -> {
                    String res = r.sendCommand("CONFIG", "GET", "notify-keyspace-events");
                    yield textResponse("Keyspace notification config: " + res);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
