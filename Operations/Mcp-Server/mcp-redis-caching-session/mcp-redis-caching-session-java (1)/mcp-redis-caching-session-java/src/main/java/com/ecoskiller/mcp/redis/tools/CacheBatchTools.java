package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisConfig;
import com.ecoskiller.mcp.redis.security.AuditLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// ─────────────────────────────────────────────────────────────────────────────
// cache_delete
// ─────────────────────────────────────────────────────────────────────────────

class CacheDeleteTool extends BaseTool {

    CacheDeleteTool(RedisConfig cfg, AuditLogger audit) { super(cfg, audit); }
    @Override public String name() { return "cache_delete"; }

    @Override
    public ObjectNode schema() {
        ObjectNode props = json.createObjectNode();
        props.set("api_key",   apiKeyProp());
        props.set("namespace", strProp("Cache namespace"));
        props.set("key",       strProp("Cache key to delete (invalidate)"));
        return buildSchema(name(),
                "Invalidate (delete) a cached entry. Call this when the underlying data in PostgreSQL changes " +
                "to ensure subsequent reads fetch fresh data.",
                props, "namespace", "key");
    }

    @Override
    public ToolResult execute(JsonNode args) throws Exception {
        String fqk = key(requireString(args, "namespace"), requireString(args, "key"));
        try (Jedis j = conn()) {
            long deleted = j.del(fqk);
            ObjectNode r = json.createObjectNode();
            r.put("deleted", deleted > 0);
            r.put("count",   deleted);
            r.put("key",     fqk);
            return ToolResult.ok(json.writeValueAsString(r));
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// cache_mget — batch GET for cache warming
// ─────────────────────────────────────────────────────────────────────────────

class CacheMGetTool extends BaseTool {

    private static final int MAX_KEYS = 100;

    CacheMGetTool(RedisConfig cfg, AuditLogger audit) { super(cfg, audit); }
    @Override public String name() { return "cache_mget"; }

    @Override
    public ObjectNode schema() {
        ObjectNode props = json.createObjectNode();
        props.set("api_key",   apiKeyProp());
        props.set("namespace", strProp("Cache namespace (applied to all keys)"));

        ObjectNode keysSchema = json.createObjectNode();
        keysSchema.put("type", "array");
        keysSchema.putObject("items").put("type", "string");
        keysSchema.put("description", "Array of cache keys to retrieve (max 100)");
        props.set("keys", keysSchema);

        return buildSchema(name(),
                "Batch retrieve multiple cache values in a single round-trip. " +
                "Use for cache warming or pre-fetching multiple candidate profiles at once.",
                props, "namespace", "keys");
    }

    @Override
    public ToolResult execute(JsonNode args) throws Exception {
        String   ns   = requireString(args, "namespace");
        JsonNode keysNode = args.path("keys");
        if (!keysNode.isArray() || keysNode.size() == 0) {
            return ToolResult.error("'keys' must be a non-empty array");
        }
        if (keysNode.size() > MAX_KEYS) {
            return ToolResult.error("Max " + MAX_KEYS + " keys per batch");
        }

        List<String> fqKeys = new ArrayList<>();
        for (JsonNode k : keysNode) fqKeys.add(key(ns, k.asText()));

        try (Jedis j = conn()) {
            List<String> values = j.mget(fqKeys.toArray(new String[0]));

            ArrayNode results = json.createArrayNode();
            for (int i = 0; i < fqKeys.size(); i++) {
                ObjectNode entry = json.createObjectNode();
                entry.put("key",   fqKeys.get(i));
                entry.put("hit",   values.get(i) != null);
                entry.put("value", values.get(i));
                results.add(entry);
            }

            long hits   = values.stream().filter(v -> v != null).count();
            ObjectNode r = json.createObjectNode();
            r.put("total",  fqKeys.size());
            r.put("hits",   hits);
            r.put("misses", fqKeys.size() - hits);
            r.set("entries", results);
            return ToolResult.ok(json.writeValueAsString(r));
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// cache_flush_namespace — delete all keys in a namespace (SCAN + DEL)
// ─────────────────────────────────────────────────────────────────────────────

class CacheFlushNamespaceTool extends BaseTool {

    CacheFlushNamespaceTool(RedisConfig cfg, AuditLogger audit) { super(cfg, audit); }
    @Override public String name() { return "cache_flush_namespace"; }

    @Override
    public ObjectNode schema() {
        ObjectNode props = json.createObjectNode();
        props.set("api_key",   apiKeyProp());
        props.set("namespace", strProp("Cache namespace to flush (all keys under this namespace will be deleted)"));
        props.set("confirm",   strProp("Must be 'CONFIRM' to proceed (safety guard)"));
        return buildSchema(name(),
                "Delete all cached keys in a namespace. Use after a bulk data update to force cache refresh. " +
                "Requires confirm=CONFIRM to prevent accidental flushes.",
                props, "namespace", "confirm");
    }

    @Override
    public ToolResult execute(JsonNode args) throws Exception {
        String ns      = requireString(args, "namespace");
        String confirm = requireString(args, "confirm");

        if (!"CONFIRM".equals(confirm)) {
            return ToolResult.error("Safety: set confirm=CONFIRM to flush a namespace");
        }

        // Validate namespace — must not be wildcard or empty
        validateKey(ns, "namespace");

        String pattern = cfg.keyPrefix + ":" + ns + ":*";
        long   deleted = 0;

        try (Jedis j = conn()) {
            // Use SCAN to avoid blocking Redis with KEYS on large keyspaces
            String cursor = "0";
            do {
                redis.clients.jedis.resps.ScanResult<String> result = j.scan(cursor,
                        new redis.clients.jedis.params.ScanParams().match(pattern).count(100));
                cursor = result.getCursor();
                List<String> batch = result.getResult();
                if (!batch.isEmpty()) {
                    deleted += j.del(batch.toArray(new String[0]));
                }
            } while (!cursor.equals("0"));
        }

        ObjectNode r = json.createObjectNode();
        r.put("namespace", ns);
        r.put("deleted",   deleted);
        r.put("pattern",   pattern);
        return ToolResult.ok(json.writeValueAsString(r));
    }
}
