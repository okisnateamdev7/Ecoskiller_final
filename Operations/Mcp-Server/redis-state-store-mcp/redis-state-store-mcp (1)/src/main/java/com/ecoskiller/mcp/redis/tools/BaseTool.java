package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import org.json.JSONArray;
import org.json.JSONObject;

public abstract class BaseTool implements McpTool {

    protected RedisClient redis() throws Exception {
        RedisClient client = new RedisClient();
        client.connect();
        return client;
    }

    protected JSONObject strProp(String description) {
        return new JSONObject().put("type", "string").put("description", description);
    }

    protected JSONObject intProp(String description) {
        return new JSONObject().put("type", "integer").put("description", description);
    }

    protected JSONObject numProp(String description) {
        return new JSONObject().put("type", "number").put("description", description);
    }

    protected JSONObject schema(String name, String description, JSONObject properties, String... required) {
        JSONObject inputSchema = new JSONObject()
                .put("type", "object")
                .put("properties", properties);
        if (required.length > 0) {
            inputSchema.put("required", required);
        }
        return new JSONObject()
                .put("name", name)
                .put("description", description)
                .put("inputSchema", inputSchema);
    }

    protected JSONObject textResponse(String text) {
        return new JSONObject()
                .put("content", new JSONArray().put(
                        new JSONObject().put("type", "text").put("text", text)
                ));
    }

    protected JSONObject jsonResponse(JSONObject data) {
        return textResponse(data.toString());
    }
}
