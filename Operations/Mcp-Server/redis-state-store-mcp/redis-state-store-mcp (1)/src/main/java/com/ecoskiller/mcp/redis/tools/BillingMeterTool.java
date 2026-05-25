package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisClient;
import com.ecoskiller.mcp.redis.model.KeyBuilder;
import org.json.JSONObject;

/**
 * Tool: billing_meter
 *
 * Usage metering counters for billing purposes.
 *
 * Actions:
 *   - increment: increment a billing metric for a tenant by a given value/amount
 *   - get: retrieve current usage count for a metric
 */
public class BillingMeterTool extends BaseTool {

    @Override public String getName() { return "billing_meter"; }

    @Override
    public JSONObject getSchema() {
        JSONObject props = new JSONObject()
                .put("action",      strProp("increment|get"))
                .put("tenant_id",   strProp("Ecoskiller tenant ID"))
                .put("metric_name", strProp("Name of the metric (e.g. api_calls)"))
                .put("value",       intProp("Amount to increment by"))
                .put("amount",      intProp("Amount to increment by"));
        return schema(getName(),
                "Tenant billing usage metering — track and query usage counters.",
                props, "action", "tenant_id", "metric_name");
    }

    @Override
    public JSONObject execute(JSONObject args) throws Exception {
        String action     = args.getString("action");
        String tenantId   = args.getString("tenant_id");
        String metricName = args.getString("metric_name");
        String key        = KeyBuilder.billingMeter(tenantId, metricName);

        RedisClient r = redis();
        try {
            return switch (action) {
                case "increment" -> {
                    long val = 1;
                    if (args.has("value")) {
                        val = args.getLong("value");
                    } else if (args.has("amount")) {
                        val = args.getLong("amount");
                    }
                    String res = r.sendCommand("INCRBY", key, String.valueOf(val));
                    long current = Long.parseLong(res.replace(":", "").trim());
                    yield textResponse("Metric " + metricName + " incremented. Current value: " + current);
                }
                case "get" -> {
                    String val = r.get(key);
                    yield textResponse("Usage count for " + metricName + ": " + (val != null ? val : "0"));
                }
                default -> textResponse("Unknown action: " + action);
            };
        } finally { r.close(); }
    }
}
