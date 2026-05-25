package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import org.json.JSONObject;

/**
 * Tool: tenant_key_flush
 *
 * Flushes all keys matching a specific tenant or session scope (with dry_run and confirm protection).
 *
 * Parameters:
 *   - scope: "session" or "tenant" (default: "tenant")
 *   - tenant_id: Ecoskiller tenant ID
 *   - session_id: GD session ID (required for scope=session)
 *   - service: Service name (required for scope=session, e.g. "gd")
 *   - dry_run: "true" or "false" (default: "true" - preview only)
 *   - confirm: "true" or "false" (default: "false" - required if dry_run="false")
 */
public class TenantKeyFlushTool extends BaseTool {

    @Override public String getName() { return "tenant_key_flush"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",     strProp("flush"))
                .put("scope",      strProp("session|tenant (default: tenant)"))
                .put("tenant_id",  strProp("Tenant ID to flush keys for"))
                .put("session_id", strProp("Session ID (required for scope=session)"))
                .put("service",    strProp("Service name (required for scope=session)"))
                .put("dry_run",    strProp("true|false (default: true)"))
                .put("confirm",    strProp("true|false (default: false)"));
        return schema(getName(),
                "Tenant-specific key flush — deletes keys scoped to a tenant or session.",
                props, "tenant_id");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String tenantId  = args.getString("tenant_id");
        String scope     = args.optString("scope", "tenant");
        String sessionId = args.optString("session_id", "");
        String service   = args.optString("service", "");

        // Determine pattern to delete
        String pattern;
        if ("session".equalsIgnoreCase(scope)) {
            if (!service.isEmpty()) {
                pattern = "tenant:" + tenantId + ":" + service + ":" + sessionId + ":*";
            } else {
                pattern = "tenant:" + tenantId + ":*:" + sessionId + ":*";
            }
        } else {
            pattern = "tenant:" + tenantId + ":*";
        }

        // Check dry_run and confirm parameters (handle both JSON string and boolean)
        boolean dryRun = true;
        if (args.has("dry_run")) {
            Object dr = args.get("dry_run");
            if (dr instanceof Boolean) {
                dryRun = (Boolean) dr;
            } else {
                dryRun = !"false".equalsIgnoreCase(dr.toString());
            }
        }

        boolean confirm = false;
        if (args.has("confirm")) {
            Object cf = args.get("confirm");
            if (cf instanceof Boolean) {
                confirm = (Boolean) cf;
            } else {
                confirm = "true".equalsIgnoreCase(cf.toString());
            }
        }

        if (dryRun) {
            return textResponse("DRY RUN: would flush keys matching " + pattern + ". No keys deleted.");
        }

        if (!confirm) {
            return textResponse("ABORTED: dry_run=false requires confirm=true to proceed.");
        }

        RedisClient r = redis();
        try {
            String raw = r.keys(pattern);
            if (raw.startsWith("[") && raw.endsWith("]")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            String[] keys = raw.trim().isEmpty() ? new String[0] : raw.split(",");
            int deletedCount = 0;
            for (String k : keys) {
                k = k.trim();
                if (k.isEmpty()) continue;
                r.del(k);
                deletedCount++;
            }
            return textResponse("Flush completed. Deleted " + deletedCount + " keys matching " + pattern);
        } finally { r.close(); }
    }
}
