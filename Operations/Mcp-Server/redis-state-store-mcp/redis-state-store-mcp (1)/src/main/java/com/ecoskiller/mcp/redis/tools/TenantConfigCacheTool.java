package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: tenant_config_cache
 *
 * Tenant-specific configuration caching for microservices.
 *
 * Actions:
 *   - set / cache: store config JSON with TTL
 *   - get: retrieve cached config JSON
 */
public class TenantConfigCacheTool extends BaseTool {

    @Override public String getName() { return "tenant_config_cache"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",       strProp("set|cache|get"))
                .put("tenant_id",    strProp("Ecoskiller tenant ID"))
                .put("service",      strProp("Service name"))
                .put("service_name", strProp("Service name"))
                .put("config_json",  strProp("Configuration JSON string"))
                .put("ttl_seconds",  intProp("TTL in seconds (default: 300)"));
        return schema(getName(),
                "Tenant config cache management — cache configurations or retrieve them.",
                props, "action", "tenant_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action   = args.getString("action");
        String tenantId = args.getString("tenant_id");
        String service  = args.has("service_name") ? args.getString("service_name") : args.getString("service");
        String key      = KeyBuilder.tenantConfig(tenantId, service);

        RedisClient r = redis();
        try {
            return switch (action) {
                case "set", "cache" -> {
                    String configJson = args.getString("config_json");
                    int ttl = args.has("ttl_seconds") ? args.getInt("ttl_seconds") : 300;
                    r.setex(key, ttl, configJson);
                    yield textResponse("Tenant configuration cached for service=" + service + " with TTL=" + ttl + "s");
                }
                case "get" -> {
                    String val = r.get(key);
                    if (val == null || "$-1".equals(val)) {
                        yield textResponse("No cached configuration found.");
                    }
                    yield textResponse(val);
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
