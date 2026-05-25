package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: pubsub_broadcast
 *
 * Real-time event broadcasting via Redis Pub/Sub.
 *
 * Actions/Channel Types:
 *   - publish_session / channel_type=session: publish message to session events channel
 *   - publish_tenant / channel_type=tenant: publish message to tenant broadcast channel
 */
public class PubSubBroadcastTool extends BaseTool {

    @Override public String getName() { return "pubsub_broadcast"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",       strProp("publish_session|publish_tenant"))
                .put("channel_type", strProp("session|tenant"))
                .put("session_id",   strProp("Session ID"))
                .put("tenant_id",    strProp("Tenant ID"))
                .put("message",      strProp("Raw message string"))
                .put("event_type",   strProp("Event type (e.g. MUTE_ALL)"))
                .put("payload_json", strProp("Payload JSON string"));
        return schema(getName(),
                "Real-time event broadcasting using Redis Pub/Sub.",
                props);
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action      = args.optString("action", "");
        String channelType = args.optString("channel_type", "");
        String message     = args.optString("message", "");

        if (message.isEmpty() && args.has("event_type")) {
            message = new JSONObject()
                    .put("event", args.getString("event_type"))
                    .put("payload", new JSONObject(args.optString("payload_json", "{}")))
                    .toString();
        }

        RedisClient r = redis();
        try {
            if ("publish_session".equals(action) || "session".equals(channelType)) {
                String sessionId = args.getString("session_id");
                String channel = KeyBuilder.sessionEventsChannel(sessionId);
                String res = r.publish(channel, message);
                long receivers = Long.parseLong(res.replace(":", "").trim());
                return textResponse("Published message to session=" + sessionId + ". Receivers: " + receivers);
            } else if ("publish_tenant".equals(action) || "tenant".equals(channelType)) {
                String tenantId = args.getString("tenant_id");
                String channel = KeyBuilder.tenantBroadcastChannel(tenantId);
                String res = r.publish(channel, message);
                long receivers = Long.parseLong(res.replace(":", "").trim());
                return textResponse("Published message to tenant=" + tenantId + ". Receivers: " + receivers);
            } else {
                return textResponse("Unknown Pub/Sub broadcast channel_type or action: action=" + action + ", channelType=" + channelType);
            }
        } finally { r.close(); }
    }
}
