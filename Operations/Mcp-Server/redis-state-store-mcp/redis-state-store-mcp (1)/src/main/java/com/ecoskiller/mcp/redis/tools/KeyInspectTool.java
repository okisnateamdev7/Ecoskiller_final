package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tool: key_inspect
 *
 * Inspects keys matching a pattern or retrieves key values with security masking.
 *
 * Actions:
 *   - inspect: scan and retrieve info for keys matching a pattern
 *   - get: retrieve value for a key with security masking for sensitive data (e.g. OTP)
 */
public class KeyInspectTool extends BaseTool {

    @Override public String getName() { return "key_inspect"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",     strProp("inspect|get"))
                .put("pattern",    strProp("Glob pattern for key matching (required for 'inspect')"))
                .put("tenant_id",  strProp("Ecoskiller tenant ID (required for 'get')"))
                .put("key_suffix", strProp("Key suffix (required for 'get')"));
        return schema(getName(),
                "Redis key inspector — scan and examine key types, TTLs, and values.",
                props, "action");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action = args.getString("action");

        RedisClient r = redis();
        try {
            return switch (action) {
                case "inspect" -> {
                    String pattern = args.getString("pattern");
                    String raw = r.keys(pattern);
                    if (raw.startsWith("[") && raw.endsWith("]")) {
                        raw = raw.substring(1, raw.length() - 1);
                    }
                    String[] keys = raw.trim().isEmpty() ? new String[0] : raw.split(",");
                    JSONArray result = new JSONArray();
                    for (String k : keys) {
                        k = k.trim();
                        if (k.isEmpty()) continue;
                        String type = r.type(k);
                        String ttlStr = r.ttl(k);
                        long ttl = Long.parseLong(ttlStr.replace(":", "").replace("+", "").trim());
                        JSONObject item = new JSONObject()
                                .put("key", k)
                                .put("type", type.replace("+", "").trim())
                                .put("ttl", ttl);
                        result.put(item);
                    }
                    yield jsonResponse(new JSONObject().put("keys", result));
                }
                case "get" -> {
                    String tenantId = args.getString("tenant_id");
                    String suffix   = args.getString("key_suffix");
                    String key      = "tenant:" + tenantId + ":" + suffix;

                    if (suffix.contains("otp") || key.contains("otp")) {
                        yield textResponse("Sensitive key masked. [otp_store]");
                    }

                    String val = r.get(key);
                    if (val == null || "$-1".equals(val)) {
                        yield textResponse("Key not found.");
                    }
                    yield textResponse(val);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
